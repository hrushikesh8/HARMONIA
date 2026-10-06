"""Data transfer objects for the application layer.

DTOs carry data between layers and across the API boundary.
They are simple Pydantic models — validated, serializable, and
independent of domain entities.
"""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


# ── Library summaries ─────────────────────────────────────────────────────────


class LibrarySummary(BaseModel):
    """High-level library statistics."""

    total_tracks: int
    total_albums: int
    total_artists: int
    total_files: int = 0


# ── Track DTOs ────────────────────────────────────────────────────────────────


class TrackSummary(BaseModel):
    """Lightweight track info for list views."""

    id: UUID
    title: str
    artist_name: str | None = None
    album_title: str | None = None
    duration_display: str = "-"
    genre: str | None = None
    year: int | None = None


class TrackDetail(TrackSummary):
    """Full track information."""

    artist_id: UUID | None = None
    album_id: UUID | None = None
    track_number: int | None = None
    disc_number: int | None = None
    duration_seconds: float | None = None
    mbid: str | None = None
    created_at: datetime | None = None
    files: list[TrackFileInfo] = Field(default_factory=list)
    tags: list[TagInfo] = Field(default_factory=list)


class TrackFileInfo(BaseModel):
    """Track file information."""

    id: UUID
    file_path: str
    format: str
    codec: str | None = None
    bitrate: int | None = None
    sample_rate: int | None = None
    bit_depth: int | None = None
    channels: int | None = None
    is_lossless: bool = False
    quality_score: int | None = None
    file_size_display: str = ""


class TagInfo(BaseModel):
    """Tag information."""

    name: str
    value: str
    source: str


# ── Import DTOs ───────────────────────────────────────────────────────────────


class ImportResult(BaseModel):
    """Result of an import operation."""

    total_scanned: int = 0
    imported: int = 0
    skipped: int = 0
    failed: int = 0
    errors: list[ImportError_] = Field(default_factory=list)


class ImportError_(BaseModel):
    """Details about a failed import."""

    file_path: str
    reason: str


# ── Artist / Album DTOs ──────────────────────────────────────────────────────


class ArtistSummary(BaseModel):
    """Lightweight artist info."""

    id: UUID
    name: str
    sort_name: str | None = None
    album_count: int = 0
    track_count: int = 0


class AlbumSummary(BaseModel):
    """Lightweight album info."""

    id: UUID
    title: str
    artist_name: str | None = None
    year: int | None = None
    track_count: int = 0


class AlbumDetail(AlbumSummary):
    """Detailed album information with track listing."""

    artist_id: UUID | None = None
    mbid: str | None = None
    total_tracks: int | None = None
    total_discs: int | None = None
    art_path: str | None = None
    tracks: list[TrackSummary] = Field(default_factory=list)


class ArtistDetail(ArtistSummary):
    """Detailed artist information with albums."""

    mbid: str | None = None
    albums: list[AlbumSummary] = Field(default_factory=list)
