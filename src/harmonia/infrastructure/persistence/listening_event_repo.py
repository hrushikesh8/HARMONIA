"""SQLite implementation of ListeningEventRepository."""

from __future__ import annotations

from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import desc, func, select

from harmonia.domain.entities.listening_event import ListeningEvent
from harmonia.infrastructure.persistence.models import ListeningEventModel

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


class SqliteListeningEventRepository:
    """SQLite repository for listening event telemetry."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def add(self, event: ListeningEvent) -> None:
        """Persist a new immutable listening event."""
        model = ListeningEventModel(
            id=event.id,
            track_id=event.track_id,
            track_file_id=event.track_file_id,
            timestamp_utc=event.timestamp_utc,
            started_at_sec=event.started_at_sec,
            ended_at_sec=event.ended_at_sec,
            duration_listened_seconds=event.duration_listened_seconds,
            track_duration_seconds=event.track_duration_seconds,
            was_completed=event.was_completed,
            was_skipped=event.was_skipped,
            playback_source=event.playback_source,
            device_id=event.device_id,
        )
        self.session.add(model)
        self.session.flush()

    def get_by_id(self, event_id: UUID) -> ListeningEvent | None:
        """Retrieve a listening event by its primary identifier."""
        model = self.session.get(ListeningEventModel, event_id)
        if model is None:
            return None
        return self._to_entity(model)

    def list_events(
        self,
        limit: int = 50,
        offset: int = 0,
        track_id: UUID | None = None,
    ) -> list[ListeningEvent]:
        """List listening events with pagination, optionally filtered by track."""
        stmt = select(ListeningEventModel).order_by(desc(ListeningEventModel.timestamp_utc))
        if track_id is not None:
            stmt = stmt.where(ListeningEventModel.track_id == track_id)
        stmt = stmt.limit(limit).offset(offset)
        models = self.session.scalars(stmt).all()
        return [self._to_entity(m) for m in models]

    def count_events(self, track_id: UUID | None = None) -> int:
        """Count total events, optionally filtered by track."""
        stmt = select(func.count(ListeningEventModel.id))
        if track_id is not None:
            stmt = stmt.where(ListeningEventModel.track_id == track_id)
        return self.session.scalar(stmt) or 0

    def get_track_play_count(self, track_id: UUID) -> int:
        """Return total plays (completed or listened >= 30s) for a track."""
        stmt = (
            select(func.count(ListeningEventModel.id))
            .where(ListeningEventModel.track_id == track_id)
            .where((ListeningEventModel.was_completed.is_(True)) | (ListeningEventModel.was_skipped.is_(False)))
        )
        return self.session.scalar(stmt) or 0

    @staticmethod
    def _to_entity(model: ListeningEventModel) -> ListeningEvent:
        return ListeningEvent(
            id=model.id if isinstance(model.id, UUID) else UUID(str(model.id)),
            track_id=model.track_id if isinstance(model.track_id, UUID) else UUID(str(model.track_id)),
            track_file_id=(
                model.track_file_id
                if model.track_file_id is None or isinstance(model.track_file_id, UUID)
                else UUID(str(model.track_file_id))
            ),
            timestamp_utc=model.timestamp_utc,
            started_at_sec=model.started_at_sec,
            ended_at_sec=model.ended_at_sec,
            duration_listened_seconds=model.duration_listened_seconds,
            track_duration_seconds=model.track_duration_seconds,
            was_completed=model.was_completed,
            was_skipped=model.was_skipped,
            playback_source=model.playback_source,
            device_id=(
                model.device_id
                if model.device_id is None or isinstance(model.device_id, UUID)
                else UUID(str(model.device_id))
            ),
        )
