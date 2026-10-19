"""Rating repository port — interface for rating persistence."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.rating import Rating


class RatingRepository(Protocol):
    """Port for persisting and querying track ratings."""

    def set_rating(self, rating: Rating) -> None:
        """Set or update a track rating (idempotent)."""
        ...

    def get_by_track_id(self, track_id: UUID) -> Rating | None:
        """Retrieve a rating record by track ID."""
        ...

    def remove(self, track_id: UUID) -> bool:
        """Remove a rating by track ID. Returns True if removed, False if absent."""
        ...

    def list_all(
        self,
        limit: int = 100,
        offset: int = 0,
        min_rating: int | None = None,
    ) -> list[Rating]:
        """List ratings ordered by client_updated_at descending with optional filter."""
        ...

    def count(self, min_rating: int | None = None) -> int:
        """Return total number of rated tracks."""
        ...
