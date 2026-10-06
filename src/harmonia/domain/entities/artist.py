"""Artist domain entity."""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4


@dataclass
class Artist:
    """A musical artist or band.

    Represents a unique artist identity in the library. An artist can
    have multiple albums and tracks. The ``sort_name`` enables proper
    alphabetical ordering (e.g., "The Beatles" → "Beatles, The").
    """

    name: str
    id: UUID = field(default_factory=uuid4)
    sort_name: str | None = None
    mbid: str | None = None  # MusicBrainz artist ID
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    updated_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    def __post_init__(self) -> None:
        self.name = _normalize_text(self.name)
        if self.sort_name is not None:
            self.sort_name = _normalize_text(self.sort_name)
        if self.sort_name is None:
            self.sort_name = _compute_sort_name(self.name)

    @property
    def display_name(self) -> str:
        """The name as it should be displayed to the user."""
        return self.name

    def __str__(self) -> str:
        return self.name


def _normalize_text(text: str) -> str:
    """Unicode NFC normalization and whitespace cleanup."""
    return unicodedata.normalize("NFC", text).strip()


def _compute_sort_name(name: str) -> str:
    """Generate a sort name from an artist name.

    Handles common prefixes: "The Beatles" → "Beatles, The".
    """
    prefixes = ("The ", "A ", "An ")
    for prefix in prefixes:
        if name.startswith(prefix):
            return f"{name[len(prefix):]}, {prefix.strip()}"
    return name
