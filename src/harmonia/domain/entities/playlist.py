"""Playlist and PlaylistItem domain entities."""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4


@dataclass
class PlaylistItem:
    """A track entry within a playlist.

    Maintains integer position and a deterministic position key.
    """

    playlist_id: UUID
    track_id: UUID
    position: int
    id: UUID = field(default_factory=uuid4)
    position_key: str = ""
    added_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    def __post_init__(self) -> None:
        if not self.position_key:
            self.position_key = f"{self.position:08d}"


@dataclass
class Playlist:
    """A user-curated collection of tracks."""

    name: str
    id: UUID = field(default_factory=uuid4)
    description: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    updated_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    items: list[PlaylistItem] = field(default_factory=list)

    def __post_init__(self) -> None:
        self.name = unicodedata.normalize("NFC", self.name).strip()
        if self.description is not None:
            self.description = unicodedata.normalize("NFC", self.description).strip()

    @property
    def track_count(self) -> int:
        """Total number of items currently in the playlist."""
        return len(self.items)
