"""Personalization schemas for Harmonia HTTP API: playlists, favorites, and ratings."""

from __future__ import annotations

import unicodedata
from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

# ── Playlist Schemas ──────────────────────────────────────────────────────────


class PlaylistCreateRequest(BaseModel):
    """Payload for creating a new playlist."""

    model_config = ConfigDict(frozen=True)

    name: str = Field(..., min_length=1, max_length=200, description="Playlist name (1-200 characters).")
    description: str | None = Field(default=None, max_length=1000, description="Optional playlist description.")

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


class PlaylistUpdateRequest(BaseModel):
    """Payload for updating an existing playlist."""

    model_config = ConfigDict(frozen=True)

    name: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=1000)
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
    def validate_description_clearing(self) -> PlaylistUpdateRequest:
        if self.description is not None and self.clear_description:
            raise ValueError("Cannot provide both description and clear_description=True.")
        return self


class PlaylistItemCreateRequest(BaseModel):
    """Payload for adding an item to a playlist."""

    model_config = ConfigDict(frozen=True)

    track_id: UUID = Field(..., description="Referenced catalog track ID.")
    position: int | None = Field(default=None, ge=0, description="Target 0-indexed position. Appends if omitted.")


class PlaylistItemMoveRequest(BaseModel):
    """Payload for moving an item within a playlist."""

    model_config = ConfigDict(frozen=True)

    new_position: int = Field(..., ge=0, description="Target 0-indexed position.")


class PlaylistItemResponse(BaseModel):
    """Summary of an item inside a playlist."""

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


class PlaylistSummaryResponse(BaseModel):
    """Lightweight playlist summary for list representations."""

    model_config = ConfigDict(frozen=True)

    id: UUID
    name: str
    description: str | None = None
    created_at: datetime
    updated_at: datetime
    track_count: int = 0


class PlaylistDetailResponse(PlaylistSummaryResponse):
    """Full playlist detail with items and total duration."""

    total_duration_seconds: float = 0.0
    items: list[PlaylistItemResponse] = Field(default_factory=list)


# ── Favorite Schemas ──────────────────────────────────────────────────────────


class FavoriteResponse(BaseModel):
    """Representation of a user favorite track."""

    model_config = ConfigDict(frozen=True)

    track_id: UUID
    client_updated_at: int
    track_title: str | None = None
    track_artist: str | None = None


# ── Rating Schemas ────────────────────────────────────────────────────────────


class RatingSetRequest(BaseModel):
    """Payload for rating a track."""

    model_config = ConfigDict(frozen=True)

    rating: int = Field(..., ge=1, le=5, description="1-5 star rating.")


class RatingResponse(BaseModel):
    """Representation of a track rating evaluation."""

    model_config = ConfigDict(frozen=True)

    track_id: UUID
    rating: int | None = Field(default=None, ge=1, le=5)
    client_updated_at: int | None = None
    track_title: str | None = None
    track_artist: str | None = None
    is_rated: bool = False
