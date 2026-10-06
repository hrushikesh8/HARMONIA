"""Track domain entity."""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4


@dataclass
class Track:
    """A logical music track (recording).

    A Track is a unique piece of music. It may have multiple physical files
    (TrackFile) — e.g., a FLAC version and an MP3 version of the same song.

    The Track entity holds canonical metadata — the resolved, authoritative
    values that may come from file tags, external providers, or user overrides.
    """

    title: str
    id: UUID = field(default_factory=uuid4)
    artist_id: UUID | None = None
    album_id: UUID | None = None
    track_number: int | None = None
    disc_number: int | None = None
    duration_seconds: float | None = None
    year: int | None = None
    genre: str | None = None
    mbid: str | None = None  # MusicBrainz recording ID
    lyrics: str | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    updated_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    def __post_init__(self) -> None:
        self.title = unicodedata.normalize("NFC", self.title).strip()
        if self.genre is not None:
            self.genre = unicodedata.normalize("NFC", self.genre).strip()
        if self.year is not None and not (1000 <= self.year <= 9999):
            self.year = None

    @property
    def duration_display(self) -> str:
        """Human-readable duration (e.g., '3:45')."""
        if self.duration_seconds is None:
            return "-"
        total = int(self.duration_seconds)
        minutes, seconds = divmod(total, 60)
        return f"{minutes}:{seconds:02d}"

    @property
    def track_position(self) -> str:
        """Display string for track position (e.g., '1-03' for disc 1, track 3)."""
        if self.disc_number and self.disc_number > 1:
            return f"{self.disc_number}-{self.track_number or '?':>02}"
        return f"{self.track_number or '?':>02}"

    def __str__(self) -> str:
        return f"{self.track_position} {self.title}"
