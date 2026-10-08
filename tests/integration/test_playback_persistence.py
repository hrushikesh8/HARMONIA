"""Integration tests for ListeningEvent and PlaybackSnapshot SQLite repositories."""

from __future__ import annotations

from pathlib import Path
from uuid import uuid4

import pytest

from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.listening_event import ListeningEvent
from harmonia.domain.entities.playback import QueueItem, QueueSnapshot
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.domain.values.playback import RepeatMode
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.listening_event_repo import SqliteListeningEventRepository
from harmonia.infrastructure.persistence.playback_snapshot_repo import (
    SqlitePlaybackSnapshotRepository,
)
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def session_factory(tmp_path: Path):
    db_path = tmp_path / "test_persistence.db"
    engine = create_database_engine(db_path)
    init_database(engine)
    return create_session_factory(engine)


def test_listening_event_round_trip(session_factory, tmp_path: Path) -> None:
    track_file_path = tmp_path / "audio.flac"
    track_file_path.write_bytes(b"dummy audio")

    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)
        event_repo = SqliteListeningEventRepository(session)

        artist = Artist(name="Radiohead")
        artist_repo.add(artist)

        track = Track(title="Karma Police", artist_id=artist.id, duration_seconds=261.0)
        track_repo.add(track)

        tf = TrackFile(
            track_id=track.id,
            file_path=track_file_path,
            file_size=1000,
            format=AudioFormat.FLAC,
            duration_seconds=261.0,
        )
        track_repo.add_file(tf)

        # 1. Add completed event
        event1 = ListeningEvent.create(
            track_id=track.id,
            track_file_id=tf.id,
            started_at_sec=0.0,
            ended_at_sec=260.0,
            duration_listened_seconds=260.0,
            track_duration_seconds=261.0,
            playback_source="album",
        )
        event_repo.add(event1)

        # 2. Add skipped event
        event2 = ListeningEvent.create(
            track_id=track.id,
            track_file_id=tf.id,
            started_at_sec=0.0,
            ended_at_sec=15.0,
            duration_listened_seconds=15.0,
            track_duration_seconds=261.0,
            playback_source="queue",
        )
        event_repo.add(event2)
        session.commit()

    with session_factory() as session:
        event_repo = SqliteListeningEventRepository(session)
        assert event_repo.count_events(track_id=track.id) == 2

        fetched = event_repo.get_by_id(event1.id)
        assert fetched is not None
        assert fetched.track_id == track.id
        assert fetched.track_file_id == tf.id
        assert fetched.was_completed is True
        assert fetched.was_skipped is False
        assert fetched.playback_source == "album"
        assert fetched.device_id is None

        # Play count only counts completed or non-skipped (>30s)
        assert event_repo.get_track_play_count(track.id) == 1

        all_events = event_repo.list_events(limit=10)
        assert len(all_events) == 2


def test_playback_snapshot_round_trip(session_factory) -> None:
    t1_id = uuid4()
    t2_id = uuid4()

    items = [
        QueueItem(track_id=t1_id, title="Track 1", duration_seconds=180.0, is_manual=True),
        QueueItem(track_id=t2_id, title="Track 2", duration_seconds=200.0, is_manual=False),
    ]

    snapshot = QueueSnapshot(
        current_track_id=t1_id,
        current_track_file_id=None,
        position_seconds=55.0,
        volume=0.85,
        repeat_mode=RepeatMode.ALL,
        shuffle_enabled=True,
        queue_items=items,
        current_queue_index=0,
    )

    with session_factory() as session:
        snapshot_repo = SqlitePlaybackSnapshotRepository(session)
        assert snapshot_repo.load() is None
        snapshot_repo.save(snapshot)
        session.commit()

    with session_factory() as session:
        snapshot_repo = SqlitePlaybackSnapshotRepository(session)
        loaded = snapshot_repo.load()
        assert loaded is not None
        assert loaded.current_track_id == t1_id
        assert loaded.position_seconds == 55.0
        assert loaded.volume == 0.85
        assert loaded.repeat_mode == RepeatMode.ALL
        assert loaded.shuffle_enabled is True
        assert loaded.current_queue_index == 0
        assert len(loaded.queue_items) == 2
        assert loaded.queue_items[0].track_id == t1_id
        assert loaded.queue_items[0].is_manual is True
        assert loaded.queue_items[1].track_id == t2_id
        assert loaded.queue_items[1].is_manual is False

        # Test updating existing snapshot
        loaded.position_seconds = 95.0
        loaded.current_queue_index = 1
        snapshot_repo.save(loaded)
        session.commit()

    with session_factory() as session:
        snapshot_repo = SqlitePlaybackSnapshotRepository(session)
        updated = snapshot_repo.load()
        assert updated is not None
        assert updated.position_seconds == 95.0
        assert updated.current_queue_index == 1

        snapshot_repo.clear()
        session.commit()
        assert snapshot_repo.load() is None
