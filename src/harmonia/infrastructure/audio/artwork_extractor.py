"""Artwork extraction from audio files and content-addressed storage."""

from __future__ import annotations

import base64
import hashlib
from dataclasses import dataclass
from typing import TYPE_CHECKING

import mutagen
import mutagen.id3
from mutagen.flac import Picture

from harmonia.core.logging import get_logger

if TYPE_CHECKING:
    from pathlib import Path

logger = get_logger(__name__)


@dataclass
class ExtractedArtwork:
    """Extracted album/track artwork information."""

    art_hash: str  # SHA-256 hex digest of image bytes
    relative_path: str  # Content-addressed path relative to library data root
    full_path: Path  # Absolute destination path on disk
    mime_type: str  # MIME type (e.g. 'image/jpeg', 'image/png')
    size_bytes: int  # Size of raw image in bytes


def detect_image_extension(data: bytes, fallback_mime: str = "") -> str:
    """Determine image file extension from magic bytes."""
    if data.startswith(b"\xff\xd8\xff"):
        return ".jpg"
    elif data.startswith(b"\x89PNG\r\n\x1a\n"):
        return ".png"
    elif data.startswith(b"GIF87a") or data.startswith(b"GIF89a"):
        return ".gif"
    elif data.startswith(b"RIFF") and b"WEBP" in data[:16]:
        return ".webp"
    if "png" in fallback_mime.lower():
        return ".png"
    return ".jpg"


def extract_raw_image(file_path: Path) -> tuple[bytes, str] | None:
    """Extract raw image bytes and MIME type from an audio file using mutagen."""
    try:
        audio = mutagen.File(str(file_path))
    except Exception as e:
        logger.debug("Failed to read audio file %s for artwork: %e", file_path, e)
        return None

    if audio is None:
        return None

    # 1. FLAC pictures
    if hasattr(audio, "pictures") and audio.pictures:
        pic = audio.pictures[0]
        return pic.data, getattr(pic, "mime", "image/jpeg")

    # 2. ID3 APIC frames
    if hasattr(audio, "tags") and audio.tags:
        for tag in audio.tags.values():
            if isinstance(tag, mutagen.id3.APIC):
                return tag.data, getattr(tag, "mime", "image/jpeg")
        for key in audio.tags:
            if str(key).startswith("APIC"):
                frame = audio.tags[key]
                return frame.data, getattr(frame, "mime", "image/jpeg")

    # 3. MP4 / M4A covr atom
    if hasattr(audio, "tags") and audio.tags and "covr" in audio.tags:
        covers = audio.tags["covr"]
        if covers:
            cov = covers[0]
            mime = "image/png" if getattr(cov, "imageformat", None) == 14 else "image/jpeg"
            return bytes(cov), mime

    # 4. Vorbis comment METADATA_BLOCK_PICTURE
    if hasattr(audio, "tags") and audio.tags and "metadata_block_picture" in audio.tags:
        blocks = audio.tags["metadata_block_picture"]
        if blocks:
            try:
                pic = Picture(base64.b64decode(blocks[0]))
                return pic.data, getattr(pic, "mime", "image/jpeg")
            except Exception:
                pass

    return None


class ArtworkExtractor:
    """Extracts, hashes, and stores cover art in content-addressed storage."""

    def __init__(self, artwork_dir: Path, data_dir: Path | None = None) -> None:
        self._artwork_dir = artwork_dir
        self._data_dir = data_dir or artwork_dir.parent

    def extract_from_file(self, file_path: Path) -> ExtractedArtwork | None:
        """Extract artwork from audio file and store in content-addressed directory."""
        extracted = extract_raw_image(file_path)
        if extracted is None:
            return None

        image_bytes, mime = extracted
        if not image_bytes:
            return None

        art_hash = hashlib.sha256(image_bytes).hexdigest()
        ext = detect_image_extension(image_bytes, fallback_mime=mime)

        # Content-addressed path: artwork/{hash[:2]}/{hash}{ext}
        subdir = self._artwork_dir / art_hash[:2]
        dest_path = subdir / f"{art_hash}{ext}"

        if not dest_path.exists():
            subdir.mkdir(parents=True, exist_ok=True)
            dest_path.write_bytes(image_bytes)
            logger.debug("Saved extracted artwork: %s", dest_path)
        else:
            logger.debug("Reusing existing artwork: %s", dest_path)

        try:
            rel_path = dest_path.relative_to(self._data_dir).as_posix()
        except ValueError:
            rel_path = dest_path.as_posix()

        return ExtractedArtwork(
            art_hash=art_hash,
            relative_path=rel_path,
            full_path=dest_path,
            mime_type=mime,
            size_bytes=len(image_bytes),
        )
