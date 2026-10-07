"""Track and TrackFile API schemas with strict path concealment."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class TagResponse(BaseModel):
    """Tag information."""

    name: str
    value: str
    source: str


class TrackFileResponse(BaseModel):
    """Physical audio file details with host filesystem path redacted."""

    id: UUID
    format: str
    codec: str | None = None
    bitrate: int | None = None
    sample_rate: int | None = None
    bit_depth: int | None = None
    channels: int | None = None
    channel_layout: str | None = None
    encoder: str | None = None
    duration_seconds: float | None = None
    is_lossless: bool = False
    quality_score: int | None = None
    file_size_bytes: int
    file_size_display: str
    stream_url: str


class TrackResponse(BaseModel):
    """Logical track information."""

    id: UUID
    title: str
    artist_name: str | None = None
    album_title: str | None = None
    duration_display: str = "-"
    genre: str | None = None
    year: int | None = None
    album_id: UUID | None = None
    file_id: UUID | None = None
    art_url: str | None = None
    format: str | None = None
    is_lossless: bool = False
    analysis_status: str | None = None


class TrackDetailResponse(TrackResponse):
    """Full logical track detail including file representations and tags."""

    artist_id: UUID | None = None
    album_id: UUID | None = None
    track_number: int | None = None
    disc_number: int | None = None
    duration_seconds: float | None = None
    mbid: str | None = None
    created_at: datetime | None = None
    files: list[TrackFileResponse] = Field(default_factory=list)
    tags: list[TagResponse] = Field(default_factory=list)
