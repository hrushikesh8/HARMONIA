"""Playback queue snapshot repository port protocol."""

from __future__ import annotations

from typing import Protocol

from harmonia.domain.entities.playback import QueueSnapshot


class PlaybackSnapshotRepository(Protocol):
    """Repository port for saving and loading the persistent queue snapshot."""

    def save(self, snapshot: QueueSnapshot) -> None:
        """Persist or update the singleton playback snapshot."""
        ...

    def load(self) -> QueueSnapshot | None:
        """Load the active playback snapshot if one exists."""
        ...

    def clear(self) -> None:
        """Clear the stored snapshot."""
        ...
