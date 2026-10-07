"""Album API schemas."""

from __future__ import annotations

from uuid import UUID

from pydantic import BaseModel, Field

from harmonia.api.schemas.track import TrackResponse


class AlbumResponse(BaseModel):
    """Album summary."""

    id: UUID
    title: str
    artist_name: str | None = None
    year: int | None = None
    track_count: int = 0
    art_url: str | None = None


class AlbumDetailResponse(AlbumResponse):
    """Detailed album information with track list."""

    artist_id: UUID | None = None
    mbid: str | None = None
    total_tracks: int | None = None
    total_discs: int | None = None
    art_hash: str | None = None
    tracks: list[TrackResponse] = Field(default_factory=list)
