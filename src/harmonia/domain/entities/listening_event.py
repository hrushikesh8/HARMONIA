"""ListeningEvent domain entity — factual telemetry record."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4


@dataclass(frozen=True)
class ListeningEvent:
    """An immutable factual record of an audio playback session.

    Contains dual pointers:
    - track_id: Logical musical work (for aggregate analytics, history, and AI taste profiling).
    - track_file_id: Specific physical container played (for audiophile and technical fidelity audits).

    Does NOT contain subjective mood, inference, or user preferences.
    """

    track_id: UUID
    id: UUID = field(default_factory=uuid4)
    track_file_id: UUID | None = None
    timestamp_utc: datetime = field(default_factory=lambda: datetime.now(UTC))
    started_at_sec: float = 0.0
    ended_at_sec: float = 0.0
    duration_listened_seconds: float = 0.0
    track_duration_seconds: float = 0.0
    was_completed: bool = False
    was_skipped: bool = False
    playback_source: str = "queue"
    device_id: UUID | None = None  # None in Phase 4; populated in Phase 5 LocalSync

    @classmethod
    def create(
        cls,
        track_id: UUID,
        track_file_id: UUID | None,
        started_at_sec: float,
        ended_at_sec: float,
        duration_listened_seconds: float,
        track_duration_seconds: float,
        playback_source: str = "queue",
        device_id: UUID | None = None,
        timestamp_utc: datetime | None = None,
        event_id: UUID | None = None,
    ) -> ListeningEvent:
        """Create a ListeningEvent computing completion and skip facts."""
        duration_listened = max(0.0, float(duration_listened_seconds))
        track_dur = max(0.0, float(track_duration_seconds))

        # Completion rule: listened >= 80% of track length OR at least 4 minutes (240s)
        was_completed = False
        if track_dur > 0.0:
            was_completed = duration_listened >= (0.8 * track_dur) or duration_listened >= 240.0

        # Skip rule: abandoned before 30 seconds of active playback
        was_skipped = duration_listened < 30.0 and not was_completed

        return cls(
            id=event_id or uuid4(),
            track_id=track_id,
            track_file_id=track_file_id,
            timestamp_utc=timestamp_utc or datetime.now(UTC),
            started_at_sec=max(0.0, float(started_at_sec)),
            ended_at_sec=max(0.0, float(ended_at_sec)),
            duration_listened_seconds=duration_listened,
            track_duration_seconds=track_dur,
            was_completed=was_completed,
            was_skipped=was_skipped,
            playback_source=playback_source,
            device_id=device_id,
        )
