"""Artist repository port — interface for artist persistence."""

from __future__ import annotations

from typing import Protocol
from uuid import UUID

from harmonia.domain.entities.artist import Artist


class ArtistRepository(Protocol):
    """Port for persisting and querying artists."""

    def add(self, artist: Artist) -> None:
        """Persist a new artist."""
        ...

    def get_by_id(self, artist_id: UUID) -> Artist | None:
        """Retrieve an artist by ID."""
        ...

    def get_all(self, limit: int = 100, offset: int = 0) -> list[Artist]:
        """Retrieve all artists with pagination."""
        ...

    def find_by_name(self, name: str) -> Artist | None:
        """Find an artist by exact name (case-insensitive).

        Used during import to avoid creating duplicate artist entries.
        """
        ...

    def update(self, artist: Artist) -> None:
        """Update an existing artist."""
        ...

    def delete(self, artist_id: UUID) -> None:
        """Remove an artist."""
        ...

    def count(self) -> int:
        """Return the total number of artists."""
        ...
