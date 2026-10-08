"""ListeningEvent repository port protocol."""

from __future__ import annotations

from typing import Protocol
from uuid import UUID

from harmonia.domain.entities.listening_event import ListeningEvent


class ListeningEventRepository(Protocol):
    """Repository port for persisting and querying listening history telemetry."""

    def add(self, event: ListeningEvent) -> None:
        """Persist a new immutable listening event."""
        ...

    def get_by_id(self, event_id: UUID) -> ListeningEvent | None:
        """Retrieve a listening event by its primary identifier."""
        ...

    def list_events(
        self,
        limit: int = 50,
        offset: int = 0,
        track_id: UUID | None = None,
    ) -> list[ListeningEvent]:
        """List listening events with pagination, optionally filtered by track."""
        ...

    def count_events(self, track_id: UUID | None = None) -> int:
        """Count total events, optionally filtered by track."""
        ...

    def get_track_play_count(self, track_id: UUID) -> int:
        """Return the total number of times a track has completed or passed 30s."""
        ...
