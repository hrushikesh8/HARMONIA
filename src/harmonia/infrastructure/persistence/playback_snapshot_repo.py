"""SQLite implementation of PlaybackSnapshotRepository."""

from __future__ import annotations

import json
from typing import TYPE_CHECKING
from uuid import UUID

from harmonia.domain.entities.playback import QueueItem, QueueSnapshot
from harmonia.domain.values.playback import RepeatMode
from harmonia.infrastructure.persistence.models import PlaybackQueueSnapshotModel

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


class SqlitePlaybackSnapshotRepository:
    """SQLite repository for storing the singleton playback queue snapshot."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def save(self, snapshot: QueueSnapshot) -> None:
        """Persist or update the singleton snapshot."""
        model = self.session.get(PlaybackQueueSnapshotModel, 1)
        queue_data = [
            {
                "id": str(item.id),
                "track_id": str(item.track_id),
                "track_file_id": str(item.track_file_id) if item.track_file_id else None,
                "title": item.title,
                "artist_name": item.artist_name,
                "duration_seconds": item.duration_seconds,
                "is_manual": item.is_manual,
            }
            for item in snapshot.queue_items
        ]
        queue_json = json.dumps(queue_data)

        if model is None:
            model = PlaybackQueueSnapshotModel(
                id=1,
                current_track_id=snapshot.current_track_id,
                current_track_file_id=snapshot.current_track_file_id,
                position_seconds=snapshot.position_seconds,
                volume=snapshot.volume,
                repeat_mode=snapshot.repeat_mode.value,
                shuffle_enabled=snapshot.shuffle_enabled,
                queue_json=queue_json,
                current_queue_index=snapshot.current_queue_index,
            )
            self.session.add(model)
        else:
            model.current_track_id = snapshot.current_track_id
            model.current_track_file_id = snapshot.current_track_file_id
            model.position_seconds = snapshot.position_seconds
            model.volume = snapshot.volume
            model.repeat_mode = snapshot.repeat_mode.value
            model.shuffle_enabled = snapshot.shuffle_enabled
            model.queue_json = queue_json
            model.current_queue_index = snapshot.current_queue_index

        self.session.flush()

    def load(self) -> QueueSnapshot | None:
        """Load the active playback snapshot if one exists."""
        model = self.session.get(PlaybackQueueSnapshotModel, 1)
        if model is None:
            return None

        try:
            raw_items = json.loads(model.queue_json)
            queue_items: list[QueueItem] = []
            for d in raw_items:
                queue_items.append(
                    QueueItem(
                        id=UUID(d["id"]),
                        track_id=UUID(d["track_id"]),
                        track_file_id=UUID(d["track_file_id"]) if d.get("track_file_id") else None,
                        title=d.get("title", ""),
                        artist_name=d.get("artist_name", ""),
                        duration_seconds=float(d.get("duration_seconds", 0.0)),
                        is_manual=bool(d.get("is_manual", False)),
                    )
                )

            repeat_mode = (
                RepeatMode(model.repeat_mode)
                if model.repeat_mode in RepeatMode._value2member_map_
                else RepeatMode.OFF
            )

            return QueueSnapshot(
                current_track_id=(
                    model.current_track_id
                    if model.current_track_id is None or isinstance(model.current_track_id, UUID)
                    else UUID(str(model.current_track_id))
                ),
                current_track_file_id=(
                    model.current_track_file_id
                    if model.current_track_file_id is None or isinstance(model.current_track_file_id, UUID)
                    else UUID(str(model.current_track_file_id))
                ),
                position_seconds=model.position_seconds,
                volume=model.volume,
                repeat_mode=repeat_mode,
                shuffle_enabled=model.shuffle_enabled,
                queue_items=queue_items,
                current_queue_index=model.current_queue_index,
                updated_at=model.updated_at,
            )
        except Exception:
            return None

    def clear(self) -> None:
        """Clear the stored snapshot."""
        model = self.session.get(PlaybackQueueSnapshotModel, 1)
        if model is not None:
            self.session.delete(model)
            self.session.flush()
