"""FastAPI router for queue management."""

from __future__ import annotations

from fastapi import APIRouter, Depends, Response

from harmonia.api.dependencies import get_playback_service, verify_api_token
from harmonia.api.schemas.playback import (
    QueueItemResponse,
    QueueTrackRequest,
    SetQueueRequest,
)
from harmonia.application.playback_service import PlaybackService

router = APIRouter(prefix="/queue", tags=["Queue"], dependencies=[Depends(verify_api_token)])


@router.get("", response_model=list[QueueItemResponse])
def get_queue(service: PlaybackService = Depends(get_playback_service)) -> list[QueueItemResponse]:
    """Inspect current queue items in playback sequence."""
    items = service.get_queue()
    return [
        QueueItemResponse(
            id=item.id,
            track_id=item.track_id,
            track_file_id=item.track_file_id,
            title=item.title,
            artist_name=item.artist_name,
            duration_seconds=item.duration_seconds,
            is_manual=item.is_manual,
        )
        for item in items
    ]


@router.post("/set", response_model=list[QueueItemResponse])
def set_queue(
    req: SetQueueRequest,
    service: PlaybackService = Depends(get_playback_service),
) -> list[QueueItemResponse]:
    """Replace active context queue and begin playback."""
    service.set_queue(req.track_ids, play_index=req.play_index)
    return get_queue(service)


@router.post("/play-next", response_model=list[QueueItemResponse])
def insert_play_next(
    req: QueueTrackRequest,
    service: PlaybackService = Depends(get_playback_service),
) -> list[QueueItemResponse]:
    """Insert a track to play immediately after current track (Auxio contiguous logic)."""
    service.insert_play_next(req.track_id)
    return get_queue(service)


@router.post("/append", response_model=list[QueueItemResponse])
def append_up_next(
    req: QueueTrackRequest,
    service: PlaybackService = Depends(get_playback_service),
) -> list[QueueItemResponse]:
    """Append a track to the end of manual Up Next queue."""
    service.append_up_next(req.track_id)
    return get_queue(service)


@router.delete("/{index}", response_model=list[QueueItemResponse])
def remove_from_queue(
    index: int,
    service: PlaybackService = Depends(get_playback_service),
) -> list[QueueItemResponse]:
    """Remove a track at a specific index from the queue."""
    service.remove_from_queue(index)
    return get_queue(service)


@router.delete("", status_code=204)
def clear_queue(service: PlaybackService = Depends(get_playback_service)) -> Response:
    """Clear all queued items and stop playback."""
    service.clear_queue()
    return Response(status_code=204)
