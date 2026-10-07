"""Search API schemas."""

from __future__ import annotations

from uuid import UUID

from pydantic import BaseModel, Field


class SearchItemResponse(BaseModel):
    """A search result item."""

    track_id: UUID
    title: str
    artist_name: str | None = None
    album_title: str | None = None
    duration_display: str = "-"
    genre: str | None = None
    year: int | None = None
    format: str | None = None
    quality_score: int | None = None


class SearchResponse(BaseModel):
    """Full-text search response."""

    query: str
    total_matches: int
    items: list[SearchItemResponse] = Field(default_factory=list)
