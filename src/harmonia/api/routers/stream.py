"""Secure byte-range audio streaming endpoint."""

from __future__ import annotations

from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, Header, Response

from harmonia.api.dependencies import get_streaming_service, verify_api_token

if TYPE_CHECKING:
    from harmonia.application.streaming_service import StreamingService

router = APIRouter(prefix="/stream", tags=["Streaming"], dependencies=[Depends(verify_api_token)])


@router.get("/{file_id}")
def stream_audio(
    file_id: UUID,
    range_: str | None = Header(default=None, alias="Range"),
    streaming_service: StreamingService = Depends(get_streaming_service),
) -> Response:
    """Stream an audio file using HTTP/1.1 Range requests. Physical disk paths are strictly internal."""
    return streaming_service.get_stream(file_id=file_id, range_header=range_)
