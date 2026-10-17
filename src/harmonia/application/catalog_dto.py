"""Data transfer objects and versioned schemas for catalog portability.

Defines the canonical, versioned CatalogBundle interchange contract, entity DTOs,
and deterministic SHA-256 payload integrity calculation.
"""

from __future__ import annotations

import hashlib
import json
from datetime import datetime
from typing import Any
from uuid import UUID

from pydantic import BaseModel, Field

CURRENT_CATALOG_SCHEMA_VERSION = "1.0.0"
SUPPORTED_CATALOG_SCHEMA_MAJOR_VERSIONS = {"1"}


class ArtistBundleItem(BaseModel):
    """Portable representation of an Artist entity."""

    id: UUID
    name: str
    sort_name: str | None = None
    mbid: str | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None


class AlbumBundleItem(BaseModel):
    """Portable representation of an Album entity."""

    id: UUID
    title: str
    artist_id: UUID | None = None
    year: int | None = None
    total_tracks: int | None = None
    total_discs: int | None = None
    mbid: str | None = None
    art_hash: str | None = None  # Content-addressed artwork digest; host paths excluded
    created_at: datetime | None = None
    updated_at: datetime | None = None


class TrackBundleItem(BaseModel):
    """Portable representation of a Track entity."""

    id: UUID
    title: str
    artist_id: UUID | None = None
    album_id: UUID | None = None
    track_number: int | None = None
    disc_number: int | None = None
    duration_seconds: float | None = None
    year: int | None = None
    genre: str | None = None
    mbid: str | None = None
    lyrics: str | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None


class TrackFileBundleItem(BaseModel):
    """Portable representation of a TrackFile container and technical audio properties."""

    id: UUID
    track_id: UUID
    file_hash: str | None = None  # SHA-256 audio stream hash
    file_size: int
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
    file_path: str | None = None  # Portable filename hint (basename only); host directories strictly excluded
    added_at: datetime | None = None
    verified_at: datetime | None = None


class TagBundleItem(BaseModel):
    """Portable representation of a Tag entity (including user tags)."""

    id: UUID
    track_id: UUID
    name: str
    value: str
    source: str
    created_at: datetime | None = None


class MetadataFieldBundleItem(BaseModel):
    """Portable provenance record for entity metadata fields."""

    id: UUID
    entity_type: str
    entity_id: UUID
    field_name: str
    provider: str
    raw_value: str
    normalized_value: str
    is_active: bool = False
    priority: int = 0
    created_at: datetime | None = None


class ListeningEventBundleItem(BaseModel):
    """Portable user listening history telemetry event."""

    id: UUID
    track_id: UUID
    track_file_id: UUID | None = None
    timestamp_utc: datetime
    started_at_sec: float = 0.0
    ended_at_sec: float = 0.0
    duration_listened_seconds: float = 0.0
    track_duration_seconds: float = 0.0
    was_completed: bool = False
    was_skipped: bool = False
    playback_source: str = "queue"
    device_id: UUID | None = None
    created_at: datetime | None = None


class CatalogData(BaseModel):
    """Structured payload holding all portable entities."""

    artists: list[ArtistBundleItem] = Field(default_factory=list)
    albums: list[AlbumBundleItem] = Field(default_factory=list)
    tracks: list[TrackBundleItem] = Field(default_factory=list)
    track_files: list[TrackFileBundleItem] = Field(default_factory=list)
    tags: list[TagBundleItem] = Field(default_factory=list)
    metadata_fields: list[MetadataFieldBundleItem] = Field(default_factory=list)
    listening_events: list[ListeningEventBundleItem] = Field(default_factory=list)


class CatalogManifest(BaseModel):
    """Metadata describing the bundle envelope and generation context."""

    schema_version: str = CURRENT_CATALOG_SCHEMA_VERSION
    harmonia_version: str
    exported_at: datetime
    source_system: str = "harmonia-desktop"
    entity_counts: dict[str, int] = Field(default_factory=dict)


class CatalogBundle(BaseModel):
    """The canonical, portable catalog interchange bundle."""

    manifest: CatalogManifest
    data: CatalogData
    checksum: str  # SHA-256 of canonically formatted data payload


class CatalogImportResult(BaseModel):
    """Summary of operations performed during a catalog import."""

    dry_run: bool = False
    artists_created: int = 0
    artists_updated: int = 0
    artists_skipped: int = 0
    albums_created: int = 0
    albums_updated: int = 0
    albums_skipped: int = 0
    tracks_created: int = 0
    tracks_updated: int = 0
    tracks_skipped: int = 0
    track_files_created: int = 0
    track_files_updated: int = 0
    track_files_skipped: int = 0
    tags_created: int = 0
    tags_updated: int = 0
    tags_skipped: int = 0
    metadata_fields_created: int = 0
    metadata_fields_updated: int = 0
    metadata_fields_skipped: int = 0
    listening_events_created: int = 0
    listening_events_skipped: int = 0
    warnings: list[str] = Field(default_factory=list)

    @property
    def total_created(self) -> int:
        return (
            self.artists_created
            + self.albums_created
            + self.tracks_created
            + self.track_files_created
            + self.tags_created
            + self.metadata_fields_created
            + self.listening_events_created
        )

    @property
    def total_skipped(self) -> int:
        return (
            self.artists_skipped
            + self.albums_skipped
            + self.tracks_skipped
            + self.track_files_skipped
            + self.tags_skipped
            + self.metadata_fields_skipped
            + self.listening_events_skipped
        )

    @property
    def total_updated(self) -> int:
        return (
            self.artists_updated
            + self.albums_updated
            + self.tracks_updated
            + self.track_files_updated
            + self.tags_updated
            + self.metadata_fields_updated
        )


def compute_data_checksum(data: CatalogData | dict[str, Any]) -> str:
    """Compute the deterministic SHA-256 hex digest of the catalog data payload.

    Serializes the payload dictionary with sorted keys, no extraneous whitespace,
    and UTF-8 encoding.
    """
    data_dict = data.model_dump(mode="json") if isinstance(data, CatalogData) else data

    canonical_json = json.dumps(
        data_dict,
        sort_keys=True,
        separators=(",", ":"),
        ensure_ascii=False,
    )
    return hashlib.sha256(canonical_json.encode("utf-8")).hexdigest()
