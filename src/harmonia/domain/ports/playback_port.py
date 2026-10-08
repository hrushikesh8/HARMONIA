"""Playback port protocol definition."""

from __future__ import annotations

from typing import Protocol
from uuid import UUID

from harmonia.domain.entities.playback import PlaybackState, QueueItem
from harmonia.domain.values.playback import RepeatMode


class PlaybackPort(Protocol):
    """Abstract playback port for controlling audio transport and queue state.

    Decoupled from audio frameworks, web presentation, or specific hardware.
    """

    def play(self) -> None:
        """Begin or resume playback of the active track."""
        ...

    def pause(self) -> None:
        """Pause audio playback at current position."""
        ...

    def resume(self) -> None:
        """Resume playback from paused position."""
        ...

    def stop(self) -> None:
        """Stop playback and reset position to 0.0s."""
        ...

    def seek(self, position_seconds: float) -> None:
        """Seek playback to specific offset in seconds."""
        ...

    def next(self) -> None:
        """Advance to the next track in the queue."""
        ...

    def previous(self) -> None:
        """Return to previous track or beginning of current track."""
        ...

    def set_volume(self, volume: float) -> None:
        """Set output volume level, clamped to [0.0, 1.0]."""
        ...

    def set_queue(self, track_ids: list[UUID], play_index: int = 0) -> None:
        """Replace the active context queue and start playback."""
        ...

    def insert_play_next(self, track_id: UUID) -> None:
        """Insert track into manual queue immediately after current track."""
        ...

    def append_up_next(self, track_id: UUID) -> None:
        """Append track to the end of the manual Up Next queue."""
        ...

    def remove_from_queue(self, index: int) -> None:
        """Remove a track at a specific index from the queue."""
        ...

    def clear_queue(self) -> None:
        """Clear all queued items and stop playback."""
        ...

    def toggle_shuffle(self) -> bool:
        """Toggle shuffle mode and return new enabled state."""
        ...

    def set_repeat_mode(self, mode: RepeatMode) -> None:
        """Set queue repeat mode."""
        ...

    def get_state(self) -> PlaybackState:
        """Return the current factual playback state."""
        ...

    def get_queue(self) -> list[QueueItem]:
        """Return the complete queue list in active playback order."""
        ...
