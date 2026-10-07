"""MetadataFieldRecord domain entity — field-level provenance record."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4


@dataclass
class MetadataFieldRecord:
    """One field value from one source for one entity.

    Tracks field-level provenance across providers (file, path, musicbrainz, user)
    with priority-based conflict resolution.
    """

    entity_type: str  # "track", "album", "artist"
    entity_id: UUID
    field_name: str  # "title", "artist_name", "year", "genre", etc.
    provider: str  # "file", "path", "musicbrainz", "user"
    raw_value: str  # The original value exactly as received
    normalized_value: str  # After NFC, whitespace cleanup
    priority: int  # User=100, MusicBrainz=50, File=30, Path=10
    id: UUID = field(default_factory=uuid4)
    is_active: bool = False  # True = winning value for this field
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    def __post_init__(self) -> None:
        self.entity_type = self.entity_type.lower().strip()
        self.field_name = self.field_name.lower().strip()
        self.provider = self.provider.lower().strip()
