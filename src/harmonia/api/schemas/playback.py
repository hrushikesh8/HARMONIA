"""API schemas for playback, queue, and listening events."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class SeekRequest(BaseModel):
    """Seek request payload."""

    position_seconds: float = Field(ge=0.0, description="Target playback position in seconds.")


class VolumeRequest(BaseModel):
    """Volume adjustment request payload."""

    volume: float = Field(ge=0.0, le=1.0, description="Gain level between 0.0 and 1.0.")


class RepeatRequest(BaseModel):
    """Repeat mode request payload."""

    mode: str = Field(description="Repeat mode: 'off', 'all', or 'one'.")


class ShuffleRequest(BaseModel):
    """Shuffle mode request payload."""

    enabled: bool | None = Field(default=None, description="Explicit shuffle flag; toggles if omitted.")


class SetQueueRequest(BaseModel):
    """Queue replacement payload."""

    track_ids: list[UUID] = Field(description="Ordered list of track UUIDs.")
    play_index: int = Field(default=0, ge=0, description="Initial index to start playback.")


class QueueTrackRequest(BaseModel):
    """Single track queue insertion payload."""

    track_id: UUID = Field(description="Target track UUID.")


class QueueItemResponse(BaseModel):
    """Queue item representation."""

    id: UUID
    track_id: UUID
    track_file_id: UUID | None = None
    title: str = ""
    artist_name: str = ""
    duration_seconds: float = 0.0
    is_manual: bool = False


class PlaybackStateResponse(BaseModel):
    """Factual state of the playback engine."""

    status: str
    current_track_id: UUID | None = None
    current_track_file_id: UUID | None = None
    position_seconds: float = 0.0
    duration_seconds: float = 0.0
    volume: float = 1.0
    repeat_mode: str = "off"
    shuffle_enabled: bool = False
    current_queue_index: int = -1


class ListeningEventResponse(BaseModel):
    """Factual listening event history item."""

    id: UUID
    track_id: UUID
    track_file_id: UUID | None = None
    timestamp_utc: datetime
    started_at_sec: float
    ended_at_sec: float
    duration_listened_seconds: float
    track_duration_seconds: float
    was_completed: bool
    was_skipped: bool
    playback_source: str = "queue"
    device_id: UUID | None = None


class ListeningStatsResponse(BaseModel):
    """Summary metrics of listening history."""

    total_events: int
    total_completed: int
    completion_rate: float
