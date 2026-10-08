"""FastAPI router for playback transport controls and state."""

from __future__ import annotations

from fastapi import APIRouter, Depends

from harmonia.api.dependencies import get_playback_service, verify_api_token
from harmonia.api.schemas.playback import (
    PlaybackStateResponse,
    RepeatRequest,
    SeekRequest,
    ShuffleRequest,
    VolumeRequest,
)
from harmonia.application.playback_service import PlaybackService
from harmonia.domain.values.playback import RepeatMode

router = APIRouter(prefix="/playback", tags=["Playback"], dependencies=[Depends(verify_api_token)])


@router.post("/play", response_model=PlaybackStateResponse)
def play(service: PlaybackService = Depends(get_playback_service)) -> PlaybackStateResponse:
    """Start or resume playback."""
    service.play()
    return _to_response(service)


@router.post("/pause", response_model=PlaybackStateResponse)
def pause(service: PlaybackService = Depends(get_playback_service)) -> PlaybackStateResponse:
    """Pause playback at current position."""
    service.pause()
    return _to_response(service)


@router.post("/resume", response_model=PlaybackStateResponse)
def resume(service: PlaybackService = Depends(get_playback_service)) -> PlaybackStateResponse:
    """Resume playback from paused position."""
    service.resume()
    return _to_response(service)


@router.post("/stop", response_model=PlaybackStateResponse)
def stop(service: PlaybackService = Depends(get_playback_service)) -> PlaybackStateResponse:
    """Stop playback and reset position to 0.0s."""
    service.stop()
    return _to_response(service)


@router.post("/seek", response_model=PlaybackStateResponse)
def seek(
    req: SeekRequest,
    service: PlaybackService = Depends(get_playback_service),
) -> PlaybackStateResponse:
    """Seek playback to target offset in seconds."""
    service.seek(req.position_seconds)
    return _to_response(service)


@router.post("/next", response_model=PlaybackStateResponse)
def next_track(service: PlaybackService = Depends(get_playback_service)) -> PlaybackStateResponse:
    """Advance to the next track in the queue."""
    service.next()
    return _to_response(service)


@router.post("/previous", response_model=PlaybackStateResponse)
def previous_track(
    service: PlaybackService = Depends(get_playback_service),
) -> PlaybackStateResponse:
    """Return to previous track or beginning of current track."""
    service.previous()
    return _to_response(service)


@router.post("/volume", response_model=PlaybackStateResponse)
def set_volume(
    req: VolumeRequest,
    service: PlaybackService = Depends(get_playback_service),
) -> PlaybackStateResponse:
    """Adjust audio gain level between 0.0 and 1.0."""
    service.set_volume(req.volume)
    return _to_response(service)


@router.post("/repeat", response_model=PlaybackStateResponse)
def set_repeat(
    req: RepeatRequest,
    service: PlaybackService = Depends(get_playback_service),
) -> PlaybackStateResponse:
    """Set queue repeat mode ('off', 'all', 'one')."""
    mode = RepeatMode(req.mode.lower()) if req.mode.lower() in RepeatMode._value2member_map_ else RepeatMode.OFF
    service.set_repeat_mode(mode)
    return _to_response(service)


@router.post("/shuffle", response_model=PlaybackStateResponse)
def toggle_shuffle(
    req: ShuffleRequest | None = None,
    service: PlaybackService = Depends(get_playback_service),
) -> PlaybackStateResponse:
    """Toggle or set queue shuffle mode."""
    state = service.get_state()
    if req is not None and req.enabled is not None:
        if state.shuffle_enabled != req.enabled:
            service.toggle_shuffle()
    else:
        service.toggle_shuffle()
    return _to_response(service)


@router.get("/state", response_model=PlaybackStateResponse)
def get_state(service: PlaybackService = Depends(get_playback_service)) -> PlaybackStateResponse:
    """Inspect current factual playback state."""
    return _to_response(service)


def _to_response(service: PlaybackService) -> PlaybackStateResponse:
    state = service.get_state()
    return PlaybackStateResponse(
        status=state.status.value,
        current_track_id=state.current_track_id,
        current_track_file_id=state.current_track_file_id,
        position_seconds=state.position_seconds,
        duration_seconds=state.duration_seconds,
        volume=state.volume,
        repeat_mode=state.repeat_mode.value,
        shuffle_enabled=state.shuffle_enabled,
        current_queue_index=state.current_queue_index,
    )
