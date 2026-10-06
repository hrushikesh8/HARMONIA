"""Shared types, type aliases, and enumerations used across Harmonia."""

from __future__ import annotations

import enum
from uuid import UUID

# ── Type aliases ──────────────────────────────────────────────────────────────

EntityId = UUID
"""Primary key type for all domain entities."""


# ── Enumerations ──────────────────────────────────────────────────────────────


class TagSource(str, enum.Enum):
    """Origin of a tag value."""

    FILE = "file"
    """Extracted from embedded file metadata (ID3, Vorbis, etc.)."""

    USER = "user"
    """Manually applied by the user."""

    PROVIDER = "provider"
    """Fetched from an external metadata provider (MusicBrainz, etc.)."""


class PlaylistSource(str, enum.Enum):
    """How a playlist was created."""

    MANUAL = "manual"
    AI = "ai"
    IMPORT = "import"


class PlaylistItemSource(str, enum.Enum):
    """How a track was added to a playlist."""

    MANUAL = "manual"
    AI = "ai"


class FavoriteEntityType(str, enum.Enum):
    """Entity types that can be favorited."""

    TRACK = "track"
    ALBUM = "album"
    ARTIST = "artist"


class MetadataProviderName(str, enum.Enum):
    """Known metadata providers."""

    FILE = "file"
    MUSICBRAINZ = "musicbrainz"
    DISCOGS = "discogs"
    MANUAL = "manual"


class ImportStatus(str, enum.Enum):
    """Status of an import operation."""

    PENDING = "pending"
    SCANNING = "scanning"
    IMPORTING = "importing"
    COMPLETE = "complete"
    FAILED = "failed"
