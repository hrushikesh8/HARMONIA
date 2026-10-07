"""Shared types, type aliases, and enumerations used across Harmonia."""

from __future__ import annotations

import enum
from uuid import UUID

# ── Type aliases ──────────────────────────────────────────────────────────────

EntityId = UUID
"""Primary key type for all domain entities."""


# ── Enumerations ──────────────────────────────────────────────────────────────


class TagSource(enum.StrEnum):
    """Origin of a tag value."""

    FILE = "file"
    """Extracted from embedded file metadata (ID3, Vorbis, etc.)."""

    USER = "user"
    """Manually applied by the user."""

    PROVIDER = "provider"
    """Fetched from an external metadata provider (MusicBrainz, etc.)."""


class PlaylistSource(enum.StrEnum):
    """How a playlist was created."""

    MANUAL = "manual"
    AI = "ai"
    IMPORT = "import"


class PlaylistItemSource(enum.StrEnum):
    """How a track was added to a playlist."""

    MANUAL = "manual"
    AI = "ai"


class FavoriteEntityType(enum.StrEnum):
    """Entity types that can be favorited."""

    TRACK = "track"
    ALBUM = "album"
    ARTIST = "artist"


class MetadataProviderName(enum.StrEnum):
    """Known metadata providers."""

    FILE = "file"
    PATH = "path"
    MUSICBRAINZ = "musicbrainz"
    DISCOGS = "discogs"
    MANUAL = "user"
    USER = "user"


class MetadataPriority:
    """Standard priority levels for metadata provenance resolution."""

    USER: int = 100
    MUSICBRAINZ: int = 50
    FILE: int = 30
    PATH: int = 10


class DuplicateConfidence(enum.StrEnum):
    """Confidence level of duplicate match."""

    CERTAIN = "certain"
    PROBABLE = "probable"
    UNCERTAIN = "uncertain"


class DuplicateResolution(enum.StrEnum):
    """Resolution of a duplicate candidate."""

    MERGED = "merged"
    DISTINCT = "distinct"


class ImportStatus(enum.StrEnum):
    """Status of an import operation."""

    PENDING = "pending"
    SCANNING = "scanning"
    IMPORTING = "importing"
    COMPLETE = "complete"
    FAILED = "failed"
