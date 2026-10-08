"""Unit tests for PlaybackService, queue logic, and ListeningEvent session tracking."""

from __future__ import annotations

import time
from uuid import UUID, uuid4

from harmonia.application.playback_service import PlaybackService
from harmonia.domain.entities.listening_event import ListeningEvent
from harmonia.domain.entities.playback import QueueSnapshot
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.domain.values.playback import PlaybackStatus, RepeatMode
from harmonia.infrastructure.audio.backend import VirtualAudioBackend


class MockTrackRepo:
    def __init__(self, tracks: list[Track], files: dict[UUID, list[TrackFile]] | None = None) -> None:
        self.tracks = {t.id: t for t in tracks}
        self.files = files or {}

    def get_by_id(self, track_id: UUID) -> Track | None:
        return self.tracks.get(track_id)

    def get_files(self, track_id: UUID) -> list[TrackFile]:
        return self.files.get(track_id, [])


class MockListeningEventRepo:
    def __init__(self) -> None:
        self.events: list[ListeningEvent] = []

    def add(self, event: ListeningEvent) -> None:
        self.events.append(event)

    def get_by_id(self, event_id: UUID) -> ListeningEvent | None:
        for e in self.events:
            if e.id == event_id:
                return e
        return None

    def list_events(self, limit: int = 50, offset: int = 0, track_id: UUID | None = None) -> list[ListeningEvent]:
        filtered = self.events if track_id is None else [e for e in self.events if e.track_id == track_id]
        return filtered[offset : offset + limit]

    def count_events(self, track_id: UUID | None = None) -> int:
        return len(self.list_events(track_id=track_id))

    def get_track_play_count(self, track_id: UUID) -> int:
        return sum(1 for e in self.events if e.track_id == track_id and (e.was_completed or not e.was_skipped))


class MockSnapshotRepo:
    def __init__(self) -> None:
        self.snapshot: QueueSnapshot | None = None

    def save(self, snapshot: QueueSnapshot) -> None:
        self.snapshot = snapshot

    def load(self) -> QueueSnapshot | None:
        return self.snapshot

    def clear(self) -> None:
        self.snapshot = None


def create_test_tracks(count: int = 5) -> tuple[list[Track], dict[UUID, list[TrackFile]]]:
    tracks = []
    files_map = {}
    for i in range(1, count + 1):
        tid = uuid4()
        t = Track(id=tid, title=f"Track {i}", duration_seconds=100.0 * i)
        tf = TrackFile(
            track_id=tid,
            file_path=f"/music/track_{i}.flac",
            file_size=1024 * i,
            format=AudioFormat.FLAC,
            duration_seconds=100.0 * i,
            quality_score=90,
        )
        tracks.append(t)
        files_map[tid] = [tf]
    return tracks, files_map


def test_playback_service_basic_transport() -> None:
    tracks, files_map = create_test_tracks(3)
    track_repo = MockTrackRepo(tracks, files_map)
    event_repo = MockListeningEventRepo()
    snapshot_repo = MockSnapshotRepo()
    backend = VirtualAudioBackend()

    service = PlaybackService(
        track_repo=track_repo,
        listening_event_repo=event_repo,
        snapshot_repo=snapshot_repo,
        audio_backend=backend,
    )

    # 1. Set queue and start
    service.set_queue([t.id for t in tracks], play_index=0)
    state = service.get_state()
    assert state.status == PlaybackStatus.PLAYING
    assert state.current_track_id == tracks[0].id
    assert state.duration_seconds == 100.0
    assert backend.is_playing() is True

    # 2. Pause
    service.pause()
    assert service.get_state().status == PlaybackStatus.PAUSED
    assert backend.is_playing() is False

    # 3. Resume
    service.resume()
    assert service.get_state().status == PlaybackStatus.PLAYING
    assert backend.is_playing() is True

    # 4. Seek
    service.seek(50.0)
    assert backend.get_position() == 50.0
    assert service.get_state().position_seconds == 50.0

    # 5. Volume
    service.set_volume(0.65)
    assert service.get_state().volume == 0.65

    # 6. Stop
    service.stop()
    assert service.get_state().status == PlaybackStatus.STOPPED
    assert backend.is_playing() is False


def test_dual_layer_queue_up_next_priority() -> None:
    tracks, files_map = create_test_tracks(5)
    track_repo = MockTrackRepo(tracks, files_map)
    event_repo = MockListeningEventRepo()
    snapshot_repo = MockSnapshotRepo()

    service = PlaybackService(
        track_repo=track_repo,
        listening_event_repo=event_repo,
        snapshot_repo=snapshot_repo,
    )

    # Context queue has tracks 0, 1, 2
    service.set_queue([tracks[0].id, tracks[1].id, tracks[2].id], play_index=0)
    assert service.get_state().current_track_id == tracks[0].id

    # User explicitly enqueues track 4 as 'Up Next'
    service.append_up_next(tracks[4].id)

    # Next should play the manual 'Up Next' item first!
    service.next()
    assert service.get_state().current_track_id == tracks[4].id

    # Subsequent next should resume context queue at track 1
    service.next()
    assert service.get_state().current_track_id == tracks[1].id


def test_auxio_contiguous_play_next_during_shuffle() -> None:
    tracks, files_map = create_test_tracks(6)
    track_repo = MockTrackRepo(tracks, files_map)
    event_repo = MockListeningEventRepo()
    snapshot_repo = MockSnapshotRepo()

    service = PlaybackService(
        track_repo=track_repo,
        listening_event_repo=event_repo,
        snapshot_repo=snapshot_repo,
    )

    service.set_queue([t.id for t in tracks[:4]], play_index=0)
    service.toggle_shuffle()
    assert service.get_state().shuffle_enabled is True

    # User inserts track 5 with Play Next
    service.insert_play_next(tracks[5].id)

    # Next must immediately play track 5 without scrambling the rest
    service.next()
    assert service.get_state().current_track_id == tracks[5].id


def test_repeat_modes() -> None:
    tracks, files_map = create_test_tracks(2)
    track_repo = MockTrackRepo(tracks, files_map)
    event_repo = MockListeningEventRepo()
    snapshot_repo = MockSnapshotRepo()

    service = PlaybackService(
        track_repo=track_repo,
        listening_event_repo=event_repo,
        snapshot_repo=snapshot_repo,
    )

    # 1. Repeat ONE
    service.set_queue([tracks[0].id, tracks[1].id], play_index=0)
    service.set_repeat_mode(RepeatMode.ONE)
    service.seek(20.0)
    service.next()
    assert service.get_state().current_track_id == tracks[0].id

    # 2. Repeat ALL
    service.set_repeat_mode(RepeatMode.ALL)
    service.next()
    assert service.get_state().current_track_id == tracks[1].id
    service.next()  # Loops back to track 0
    assert service.get_state().current_track_id == tracks[0].id

    # 3. Repeat OFF
    service.set_repeat_mode(RepeatMode.OFF)
    service.next()  # Track 1
    assert service.get_state().current_track_id == tracks[1].id
    service.next()  # End of queue -> Stopped
    assert service.get_state().status == PlaybackStatus.STOPPED


def test_listening_event_session_deduplication_and_completion() -> None:
    tracks, files_map = create_test_tracks(2)
    tracks[0].duration_seconds = 10.0  # 10s track for fast testing
    files_map[tracks[0].id][0].duration_seconds = 10.0

    track_repo = MockTrackRepo(tracks, files_map)
    event_repo = MockListeningEventRepo()
    snapshot_repo = MockSnapshotRepo()
    backend = VirtualAudioBackend()

    service = PlaybackService(
        track_repo=track_repo,
        listening_event_repo=event_repo,
        snapshot_repo=snapshot_repo,
        audio_backend=backend,
    )

    service.set_queue([tracks[0].id, tracks[1].id], play_index=0)

    # Play for 0.1s, pause, resume, seek: must NOT create multiple events!
    time.sleep(0.05)
    service.pause()
    assert len(event_repo.events) == 0

    service.resume()
    service.seek(5.0)
    assert len(event_repo.events) == 0

    # Advance to next: concludes track 0 session and emits EXACTLY ONE event
    service.next()
    assert len(event_repo.events) == 1
    event = event_repo.events[0]
    assert event.track_id == tracks[0].id
    assert event.track_file_id == files_map[tracks[0].id][0].id
    assert event.duration_listened_seconds > 0.0


def test_snapshot_restoration() -> None:
    tracks, files_map = create_test_tracks(3)
    track_repo = MockTrackRepo(tracks, files_map)
    event_repo = MockListeningEventRepo()
    snapshot_repo = MockSnapshotRepo()

    service = PlaybackService(
        track_repo=track_repo,
        listening_event_repo=event_repo,
        snapshot_repo=snapshot_repo,
    )

    service.set_queue([t.id for t in tracks], play_index=1)
    service.seek(33.0)
    service.set_volume(0.7)
    service.set_repeat_mode(RepeatMode.ALL)

    # Verify snapshot was saved
    assert snapshot_repo.snapshot is not None
    assert snapshot_repo.snapshot.position_seconds == 33.0
    assert snapshot_repo.snapshot.volume == 0.7

    # Create fresh service instance and restore
    new_service = PlaybackService(
        track_repo=track_repo,
        listening_event_repo=event_repo,
        snapshot_repo=snapshot_repo,
    )
    restored = new_service.restore_snapshot()
    assert restored is True

    state = new_service.get_state()
    assert state.status == PlaybackStatus.PAUSED
    assert state.current_track_id == tracks[1].id
    assert state.position_seconds == 33.0
    assert state.volume == 0.7
    assert state.repeat_mode == RepeatMode.ALL
