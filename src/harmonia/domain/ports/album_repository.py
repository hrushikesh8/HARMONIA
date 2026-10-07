"""Album repository port — interface for album persistence."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.album import Album


class AlbumRepository(Protocol):
    """Port for persisting and querying albums."""

    def add(self, album: Album) -> None:
        """Persist a new album."""
        ...

    def get_by_id(self, album_id: UUID) -> Album | None:
        """Retrieve an album by its ID."""
        ...

    def get_all(
        self, limit: int = 100, offset: int = 0, artist_id: UUID | None = None
    ) -> list[Album]:
        """Retrieve all albums with pagination and optional artist filter."""
        ...

    def get_by_artist(self, artist_id: UUID) -> list[Album]:
        """Retrieve all albums by an artist."""
        ...

    def find_by_title_and_artist(
        self, title: str, artist_id: UUID | None
    ) -> Album | None:
        """Find an album by exact title and artist.

        Used during import to avoid creating duplicate album entries.
        """
        ...

    def update(self, album: Album) -> None:
        """Update an existing album."""
        ...

    def delete(self, album_id: UUID) -> None:
        """Remove an album."""
        ...

    def count(self, artist_id: UUID | None = None) -> int:
        """Return the total number of albums, optionally filtered by artist."""
        ...
