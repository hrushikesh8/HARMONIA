"""Port definition for SearchRepository."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID


class SearchRepository(Protocol):
    """Repository port for full-text search operations."""

    def index_track(
        self,
        track_id: UUID,
        title: str,
        artist_name: str | None,
        album_title: str | None,
        genre: str | None,
    ) -> None:
        """Add or update a track in the search index."""
        ...

    def remove_track(self, track_id: UUID) -> None:
        """Remove a track from the search index."""
        ...

    def search(self, query: str, limit: int = 50, offset: int = 0) -> list[UUID]:
        """Search tracks matching the query, returning track IDs in rank order."""
        ...

    def clear(self) -> None:
        """Clear the search index."""
        ...
