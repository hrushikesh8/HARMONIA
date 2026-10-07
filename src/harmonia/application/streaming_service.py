"""Secure audio streaming service with HTTP byte-range support."""

from __future__ import annotations

import re
from collections.abc import Callable, Generator
from pathlib import Path
from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import HTTPException, Response
from fastapi.responses import StreamingResponse

from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

MIME_TYPES: dict[str, str] = {
    ".flac": "audio/flac",
    ".mp3": "audio/mpeg",
    ".m4a": "audio/mp4",
    ".aac": "audio/aac",
    ".ogg": "audio/ogg",
    ".opus": "audio/opus",
    ".wav": "audio/wav",
    ".alac": "audio/mp4",
    ".aiff": "audio/aiff",
    ".wma": "audio/x-ms-wma",
}

DEFAULT_CHUNK_SIZE = 65536  # 64 KB


def file_chunk_generator(
    file_path: Path, start: int, length: int, chunk_size: int = DEFAULT_CHUNK_SIZE
) -> Generator[bytes, None, None]:
    """Yield chunks of a file within a byte range [start, start + length)."""
    with open(file_path, "rb") as f:
        f.seek(start)
        remaining = length
        while remaining > 0:
            bytes_to_read = min(chunk_size, remaining)
            data = f.read(bytes_to_read)
            if not data:
                break
            remaining -= len(data)
            yield data


class StreamingService:
    """Provides secure, validated byte-range streaming for audio playback."""

    def __init__(self, session_factory: Callable[[], Session]) -> None:
        self.session_factory = session_factory

    def get_stream(self, file_id: UUID, range_header: str | None = None) -> Response:
        """Resolve TrackFile by file_id, validate file on disk, and return streaming response."""
        # 1. DB Lookup to find registered TrackFile
        with self.session_factory() as session:
            repo = SqliteTrackRepository(session)
            tf = repo.get_file_by_id(file_id)

        if not tf:
            raise HTTPException(status_code=404, detail="Audio file not found in library.")

        # 2. Internal filesystem resolution and validation
        resolved_path = Path(tf.file_path).resolve()
        if not resolved_path.exists() or not resolved_path.is_file():
            raise HTTPException(status_code=404, detail="Audio file missing from storage.")

        total_size = resolved_path.stat().st_size
        mime_type = MIME_TYPES.get(resolved_path.suffix.lower(), "application/octet-stream")

        # 3. Handle Range request
        if range_header:
            return self._handle_range_request(
                resolved_path, range_header, total_size, mime_type
            )

        # 4. Full file stream (200 OK)
        return StreamingResponse(
            file_chunk_generator(resolved_path, 0, total_size),
            status_code=200,
            media_type=mime_type,
            headers={
                "Accept-Ranges": "bytes",
                "Content-Length": str(total_size),
            },
        )

    def _handle_range_request(
        self,
        file_path: Path,
        range_header: str,
        total_size: int,
        mime_type: str,
    ) -> Response:
        """Parse HTTP Range header and build 206 Partial Content or 416 Range Not Satisfiable."""
        match = re.match(r"^bytes=(\d*)-(\d*)$", range_header.strip())
        if not match:
            # Malformed range header
            return Response(
                status_code=416,
                headers={"Content-Range": f"bytes */{total_size}"},
            )

        start_str, end_str = match.groups()

        if not start_str and not end_str:
            return Response(
                status_code=416,
                headers={"Content-Range": f"bytes */{total_size}"},
            )

        if not start_str:
            # Suffix range: bytes=-500 (last 500 bytes)
            suffix_len = int(end_str)
            if suffix_len <= 0:
                return Response(
                    status_code=416,
                    headers={"Content-Range": f"bytes */{total_size}"},
                )
            start = max(0, total_size - suffix_len)
            end = total_size - 1
        elif not end_str:
            # Open range: bytes=500- (from byte 500 to end)
            start = int(start_str)
            end = total_size - 1
        else:
            # Explicit range: bytes=500-999
            start = int(start_str)
            end = int(end_str)

        # Validation: start must be <= end and within total_size
        if start < 0 or start >= total_size or end < start or end >= total_size:
            return Response(
                status_code=416,
                headers={"Content-Range": f"bytes */{total_size}"},
            )

        content_length = end - start + 1
        return StreamingResponse(
            file_chunk_generator(file_path, start, content_length),
            status_code=206,
            media_type=mime_type,
            headers={
                "Accept-Ranges": "bytes",
                "Content-Range": f"bytes {start}-{end}/{total_size}",
                "Content-Length": str(content_length),
            },
        )
