"""Playback and queue domain entities."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4

from harmonia.domain.values.playback import PlaybackStatus, RepeatMode


@dataclass(frozen=True)
class QueueItem:
    """An item in the playback queue.

    Preserves both logical Track and physical TrackFile identity where known.
    """

    track_id: UUID
    id: UUID = field(default_factory=uuid4)
    track_file_id: UUID | None = None
    title: str = ""
    artist_name: str = ""
    duration_seconds: float = 0.0
    is_manual: bool = False  # True if explicitly queued via 'Up Next' or 'Play Next'


@dataclass
class PlaybackState:
    """Current factual state of the audio engine and queue."""

    status: PlaybackStatus = PlaybackStatus.IDLE
    current_track_id: UUID | None = None
    current_track_file_id: UUID | None = None
    position_seconds: float = 0.0
    duration_seconds: float = 0.0
    volume: float = 1.0
    repeat_mode: RepeatMode = RepeatMode.OFF
    shuffle_enabled: bool = False
    current_queue_index: int = -1
    active_session_id: UUID | None = None

    def __post_init__(self) -> None:
        self.volume = max(0.0, min(1.0, float(self.volume)))
        self.position_seconds = max(0.0, float(self.position_seconds))
        self.duration_seconds = max(0.0, float(self.duration_seconds))


@dataclass
class QueueSnapshot:
    """Persistent snapshot of the active playback state and queue order."""

    current_track_id: UUID | None
    current_track_file_id: UUID | None
    position_seconds: float
    volume: float
    repeat_mode: RepeatMode
    shuffle_enabled: bool
    queue_items: list[QueueItem]
    current_queue_index: int
    updated_at: datetime = field(default_factory=lambda: datetime.now(UTC))
