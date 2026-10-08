"""Unit tests for Phase 4 playback domain entities, value objects, and events."""

from __future__ import annotations

from pathlib import Path
from uuid import uuid4

from harmonia.domain.entities.listening_event import ListeningEvent
from harmonia.domain.entities.playback import PlaybackState, QueueItem, QueueSnapshot
from harmonia.domain.values.playback import (
    LocalFileSource,
    PlatformUriSource,
    PlaybackStatus,
    RepeatMode,
    StreamUriSource,
)


def test_playback_status_and_repeat_mode() -> None:
    assert PlaybackStatus.IDLE == "idle"
    assert PlaybackStatus.PLAYING == "playing"
    assert RepeatMode.OFF == "off"
    assert RepeatMode.ALL == "all"
    assert RepeatMode.ONE == "one"


def test_playback_sources() -> None:
    local = LocalFileSource(file_path=Path("/music/song.flac"), mime_type="audio/flac")
    assert local.file_path == Path("/music/song.flac")
    assert local.mime_type == "audio/flac"

    stream = StreamUriSource(stream_url="http://127.0.0.1:8000/api/v1/tracks/123/stream")
    assert stream.stream_url == "http://127.0.0.1:8000/api/v1/tracks/123/stream"
    assert stream.mime_type == "audio/octet-stream"

    platform = PlatformUriSource(content_uri="content://media/external/audio/123")
    assert platform.content_uri == "content://media/external/audio/123"


def test_queue_item_creation() -> None:
    track_id = uuid4()
    file_id = uuid4()
    item = QueueItem(
        track_id=track_id,
        track_file_id=file_id,
        title="Paranoid Android",
        artist_name="Radiohead",
        duration_seconds=387.0,
        is_manual=True,
    )
    assert item.track_id == track_id
    assert item.track_file_id == file_id
    assert item.title == "Paranoid Android"
    assert item.duration_seconds == 387.0
    assert item.is_manual is True
    assert item.id is not None


def test_playback_state_clamping() -> None:
    state = PlaybackState(
        status=PlaybackStatus.PLAYING,
        position_seconds=-10.0,
        volume=1.5,
    )
    assert state.position_seconds == 0.0
    assert state.volume == 1.0

    state_low = PlaybackState(volume=-0.5)
    assert state_low.volume == 0.0


def test_listening_event_factual_completion_and_skipping() -> None:
    track_id = uuid4()
    file_id = uuid4()

    # Completed track: listened 85% of 200s
    evt_completed = ListeningEvent.create(
        track_id=track_id,
        track_file_id=file_id,
        started_at_sec=0.0,
        ended_at_sec=170.0,
        duration_listened_seconds=170.0,
        track_duration_seconds=200.0,
    )
    assert evt_completed.was_completed is True
    assert evt_completed.was_skipped is False
    assert evt_completed.track_id == track_id
    assert evt_completed.track_file_id == file_id

    # Skipped track: skipped after 12 seconds
    evt_skipped = ListeningEvent.create(
        track_id=track_id,
        track_file_id=file_id,
        started_at_sec=0.0,
        ended_at_sec=12.0,
        duration_listened_seconds=12.0,
        track_duration_seconds=200.0,
    )
    assert evt_skipped.was_completed is False
    assert evt_skipped.was_skipped is True

    # Long track completion: listened 245s of 600s (>240s threshold)
    evt_long = ListeningEvent.create(
        track_id=track_id,
        track_file_id=file_id,
        started_at_sec=0.0,
        ended_at_sec=245.0,
        duration_listened_seconds=245.0,
        track_duration_seconds=600.0,
    )
    assert evt_long.was_completed is True
    assert evt_long.was_skipped is False

    # Partial listen (neither completed nor skipped): listened 60s of 200s
    evt_partial = ListeningEvent.create(
        track_id=track_id,
        track_file_id=file_id,
        started_at_sec=0.0,
        ended_at_sec=60.0,
        duration_listened_seconds=60.0,
        track_duration_seconds=200.0,
    )
    assert evt_partial.was_completed is False
    assert evt_partial.was_skipped is False


def test_queue_snapshot_creation() -> None:
    track_id = uuid4()
    items = [QueueItem(track_id=track_id, title="Song A")]
    snapshot = QueueSnapshot(
        current_track_id=track_id,
        current_track_file_id=None,
        position_seconds=42.5,
        volume=0.8,
        repeat_mode=RepeatMode.ALL,
        shuffle_enabled=True,
        queue_items=items,
        current_queue_index=0,
    )
    assert snapshot.current_track_id == track_id
    assert snapshot.position_seconds == 42.5
    assert snapshot.volume == 0.8
    assert snapshot.repeat_mode == RepeatMode.ALL
    assert snapshot.shuffle_enabled is True
    assert len(snapshot.queue_items) == 1
