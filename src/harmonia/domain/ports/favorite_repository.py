"""Favorite repository port — interface for favorite persistence."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.favorite import Favorite


class FavoriteRepository(Protocol):
    """Port for persisting and querying track favorites."""

    def add(self, favorite: Favorite) -> None:
        """Add or update a track favorite (idempotent)."""
        ...

    def get_by_track_id(self, track_id: UUID) -> Favorite | None:
        """Retrieve a favorite record by track ID."""
        ...

    def is_favorite(self, track_id: UUID) -> bool:
        """Check if a track is favorited."""
        ...

    def remove(self, track_id: UUID) -> bool:
        """Remove a favorite by track ID. Returns True if removed, False if absent."""
        ...

    def list_all(self, limit: int = 100, offset: int = 0) -> list[Favorite]:
        """List favorites ordered by client_updated_at descending."""
        ...

    def count(self) -> int:
        """Return total number of favorited tracks."""
        ...
