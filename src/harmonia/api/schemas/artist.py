"""Artist API schemas."""

from __future__ import annotations

from uuid import UUID

from pydantic import BaseModel, Field

from harmonia.api.schemas.album import AlbumResponse


class ArtistResponse(BaseModel):
    """Artist summary."""

    id: UUID
    name: str
    sort_name: str | None = None
    album_count: int = 0
    track_count: int = 0


class ArtistDetailResponse(ArtistResponse):
    """Detailed artist information with albums."""

    mbid: str | None = None
    albums: list[AlbumResponse] = Field(default_factory=list)
