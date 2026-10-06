"""Album domain entity."""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4


@dataclass
class Album:
    """A music album or release.

    An album belongs to an artist (the album artist) and contains tracks.
    Compilations and various-artist albums may have ``artist_id = None``.
    """

    title: str
    id: UUID = field(default_factory=uuid4)
    artist_id: UUID | None = None
    year: int | None = None
    total_tracks: int | None = None
    total_discs: int | None = None
    mbid: str | None = None  # MusicBrainz release/release-group ID
    art_path: str | None = None  # Relative path to artwork file
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    updated_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    def __post_init__(self) -> None:
        self.title = unicodedata.normalize("NFC", self.title).strip()
        if self.year is not None and not (1000 <= self.year <= 9999):
            self.year = None  # Silently discard obviously invalid years.

    @property
    def display_title(self) -> str:
        """Album title with optional year."""
        if self.year:
            return f"{self.title} ({self.year})"
        return self.title

    def __str__(self) -> str:
        return self.display_title
