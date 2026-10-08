"""FastAPI router for listening events and factual telemetry."""

from __future__ import annotations

import math
from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, Query

from harmonia.api.dependencies import get_listening_event_repo, verify_api_token
from harmonia.api.schemas.common import PaginatedResponse
from harmonia.api.schemas.playback import ListeningEventResponse, ListeningStatsResponse

if TYPE_CHECKING:
    from harmonia.infrastructure.persistence.listening_event_repo import (
        SqliteListeningEventRepository,
    )

router = APIRouter(prefix="/listening-events", tags=["Listening History"], dependencies=[Depends(verify_api_token)])


@router.get("", response_model=PaginatedResponse[ListeningEventResponse])
def list_listening_events(
    page: int = Query(default=1, ge=1),
    page_size: int = Query(default=50, ge=1, le=200),
    track_id: UUID | None = Query(default=None),
    repo: SqliteListeningEventRepository = Depends(get_listening_event_repo),
) -> PaginatedResponse[ListeningEventResponse]:
    """List factual listening events with pagination."""
    offset = (page - 1) * page_size
    total = repo.count_events(track_id=track_id)
    events = repo.list_events(limit=page_size, offset=offset, track_id=track_id)

    items = [
        ListeningEventResponse(
            id=e.id,
            track_id=e.track_id,
            track_file_id=e.track_file_id,
            timestamp_utc=e.timestamp_utc,
            started_at_sec=e.started_at_sec,
            ended_at_sec=e.ended_at_sec,
            duration_listened_seconds=e.duration_listened_seconds,
            track_duration_seconds=e.track_duration_seconds,
            was_completed=e.was_completed,
            was_skipped=e.was_skipped,
            playback_source=e.playback_source,
            device_id=e.device_id,
        )
        for e in events
    ]

    total_pages = math.ceil(total / page_size) if total > 0 else 1
    return PaginatedResponse(
        items=items,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages,
    )


@router.get("/stats", response_model=ListeningStatsResponse)
def get_listening_stats(
    track_id: UUID | None = Query(default=None),
    repo: SqliteListeningEventRepository = Depends(get_listening_event_repo),
) -> ListeningStatsResponse:
    """Return factual metrics for playback history."""
    total = repo.count_events(track_id=track_id)
    events = repo.list_events(limit=10000, offset=0, track_id=track_id)
    completed = sum(1 for e in events if e.was_completed)
    rate = (completed / total) if total > 0 else 0.0

    return ListeningStatsResponse(
        total_events=total,
        total_completed=completed,
        completion_rate=round(rate, 4),
    )
