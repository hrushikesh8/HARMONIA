"""Data transfer objects for the personalization application service.

DTOs carry data between layers and across API boundaries for playlists,
playlist items, favorites, and ratings without exposing SQLAlchemy models.
"""

from __future__ import annotations

import unicodedata
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

# ── Playlist DTOs ─────────────────────────────────────────────────────────────


class PlaylistCreate(BaseModel):
    """Payload for creating a new playlist."""

    model_config = ConfigDict(frozen=True)

    name: str = Field(..., min_length=1, max_length=200, description="Playlist name (1-200 chars).")
    description: str | None = Field(default=None, description="Optional playlist description.")

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str) -> str:
        normalized = unicodedata.normalize("NFC", v).strip()
        if not normalized:
            raise ValueError("Playlist name cannot be empty or whitespace only.")
        if len(normalized) > 200:
            raise ValueError("Playlist name must not exceed 200 characters.")
        return normalized

    @field_validator("description")
    @classmethod
    def validate_description(cls, v: str | None) -> str | None:
        if v is None:
            return None
        normalized = unicodedata.normalize("NFC", v).strip()
        return normalized if normalized else None


class PlaylistUpdate(BaseModel):
    """Payload for updating an existing playlist."""

    model_config = ConfigDict(frozen=True)

    name: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = None
    clear_description: bool = Field(default=False, description="Explicitly clear the existing description.")

    @field_validator("name")
    @classmethod
    def validate_name(cls, v: str | None) -> str | None:
        if v is None:
            return None
        normalized = unicodedata.normalize("NFC", v).strip()
        if not normalized:
            raise ValueError("Playlist name cannot be empty or whitespace only.")
        if len(normalized) > 200:
            raise ValueError("Playlist name must not exceed 200 characters.")
        return normalized

    @field_validator("description")
    @classmethod
    def validate_description(cls, v: str | None) -> str | None:
        if v is None:
            return None
        normalized = unicodedata.normalize("NFC", v).strip()
        return normalized if normalized else None

    @model_validator(mode="after")
    def validate_description_clearing(self) -> PlaylistUpdate:
        if self.description is not None and self.clear_description:
            raise ValueError("Cannot provide both description and clear_description=True.")
        return self


class PlaylistItemCreate(BaseModel):
    """Payload for adding an item to a playlist."""

    model_config = ConfigDict(frozen=True)

    track_id: UUID = Field(..., description="Referenced catalog track ID.")
    position: int | None = Field(default=None, ge=0, description="Optional target position (0-indexed).")


class PlaylistItemSummary(BaseModel):
    """Summary of an item in a playlist."""

    model_config = ConfigDict(frozen=True)

    id: UUID
    playlist_id: UUID
    track_id: UUID
    position: int
    position_key: str
    added_at: datetime
    track_title: str | None = None
    track_artist: str | None = None
    duration_seconds: float | None = None


class PlaylistSummary(BaseModel):
    """Lightweight playlist information for list views."""

    model_config = ConfigDict(frozen=True)

    id: UUID
    name: str
    description: str | None = None
    created_at: datetime
    updated_at: datetime
    track_count: int = 0


class PlaylistDetail(PlaylistSummary):
    """Full playlist detail with items and total duration."""

    total_duration_seconds: float = 0.0
    items: list[PlaylistItemSummary] = Field(default_factory=list)


# ── Favorite DTOs ─────────────────────────────────────────────────────────────


class FavoriteSummary(BaseModel):
    """Summary of a user favorite track."""

    model_config = ConfigDict(frozen=True)

    track_id: UUID
    client_updated_at: int
    track_title: str | None = None
    track_artist: str | None = None


# ── Rating DTOs ───────────────────────────────────────────────────────────────


class RatingSummary(BaseModel):
    """Summary of a user star rating for a track."""

    model_config = ConfigDict(frozen=True)

    track_id: UUID
    rating: int = Field(..., ge=1, le=5)
    client_updated_at: int
    track_title: str | None = None
    track_artist: str | None = None
