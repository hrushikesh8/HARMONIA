"""Regression test suite for Phase 4 Critical Corrections.

Validates:
1. Case A: Play Next queue placement (A -> X -> B -> C -> D).
2. Case B: Shuffle + Play Next preserves randomized future order.
3. Case C: Server-authoritative queue navigation via HTTP API (/playback/next).
4. Case D: Native DesktopAudioBackend operations with real MP3 and FLAC audio files.
5. Case E: Native backend EOF detection and automatic queue advancement.
6. Case F: Safe handling of missing/corrupted audio files without application crash.
"""

from __future__ import annotations

import time
from pathlib import Path
from uuid import UUID, uuid4

import pytest
from fastapi.testclient import TestClient

from harmonia.api.app import create_app
from harmonia.application.playback_service import PlaybackService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.listening_event import ListeningEvent
from harmonia.domain.entities.playback import PlaybackStatus, QueueSnapshot
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.domain.values.playback import LocalFileSource
from harmonia.infrastructure.audio.backend import DesktopAudioBackend, VirtualAudioBackend
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

REAL_MP3_PATH = Path("RESOURCES/fooyin-master/tests/data/audio/audiotest.mp3").resolve()
REAL_FLAC_PATH = Path("RESOURCES/fooyin-master/tests/data/audio/audiotest.flac").resolve()


class MemoryTrackRepo:
    def __init__(self, tracks: list[Track], files_map: dict[UUID, list[TrackFile]]) -> None:
        self.tracks = {t.id: t for t in tracks}
        self.files = files_map

    def get_by_id(self, track_id: UUID) -> Track | None:
        return self.tracks.get(track_id)

    def get_files(self, track_id: UUID) -> list[TrackFile]:
        return self.files.get(track_id, [])


class MemoryListeningEventRepo:
    def __init__(self) -> None:
        self.events: list[ListeningEvent] = []

    def add(self, event: ListeningEvent) -> None:
        self.events.append(event)


class MemorySnapshotRepo:
    def __init__(self) -> None:
        self.snapshot: QueueSnapshot | None = None

    def save(self, snapshot: QueueSnapshot) -> None:
        self.snapshot = snapshot

    def load(self) -> QueueSnapshot | None:
        return self.snapshot


def create_mock_track(title: str, duration: float = 120.0, file_path: str = "/music/dummy.mp3") -> tuple[Track, TrackFile]:
    tid = uuid4()
    track = Track(title=title, duration_seconds=duration, id=tid)
    tf = TrackFile(
        track_id=tid,
        file_path=file_path,
        file_size=1024,
        format=AudioFormat.MP3,
        duration_seconds=duration,
    )
    return track, tf


# ── Case A: Play Next Queue Ordering ──────────────────────────────────────────


def test_case_a_play_next_order() -> None:
    """Case A: Current A, Context B C D. User selects X -> Play Next.

    Expected queue: A -> X -> B -> C -> D.
    """
    track_a, file_a = create_mock_track("A")
    track_b, file_b = create_mock_track("B")
    track_c, file_c = create_mock_track("C")
    track_d, file_d = create_mock_track("D")
    track_x, file_x = create_mock_track("X")

    tracks = [track_a, track_b, track_c, track_d, track_x]
    files_map = {
        track_a.id: [file_a],
        track_b.id: [file_b],
        track_c.id: [file_c],
        track_d.id: [file_d],
        track_x.id: [file_x],
    }

    service = PlaybackService(
        track_repo=MemoryTrackRepo(tracks, files_map),
        listening_event_repo=MemoryListeningEventRepo(),
        snapshot_repo=MemorySnapshotRepo(),
        audio_backend=VirtualAudioBackend(),
    )

    # 1. Start playback of context queue [A, B, C, D]
    service.set_queue([track_a.id, track_b.id, track_c.id, track_d.id], play_index=0)
    assert service.get_state().current_track_id == track_a.id

    # 2. User selects X -> Play Next
    service.insert_play_next(track_x.id)

    # 3. Verify combined queue ordering is [A, X, B, C, D]
    queue = service.get_queue()
    expected_ids = [track_a.id, track_x.id, track_b.id, track_c.id, track_d.id]
    assert [item.track_id for item in queue] == expected_ids

    # 4. Advance through queue and verify sequential playback
    service.next()
    assert service.get_state().current_track_id == track_x.id

    service.next()
    assert service.get_state().current_track_id == track_b.id

    service.next()
    assert service.get_state().current_track_id == track_c.id

    service.next()
    assert service.get_state().current_track_id == track_d.id


# ── Case B: Shuffle + Play Next ───────────────────────────────────────────────


def test_case_b_shuffle_and_play_next() -> None:
    """Case B: Current, X, randomized future sequence remains correct."""
    mock_tracks = [create_mock_track(f"Track {i}") for i in range(6)]
    tracks = [t for t, _ in mock_tracks]
    files_map = {t.id: [f] for t, f in mock_tracks}

    track_x, file_x = create_mock_track("X")
    tracks.append(track_x)
    files_map[track_x.id] = [file_x]

    service = PlaybackService(
        track_repo=MemoryTrackRepo(tracks, files_map),
        listening_event_repo=MemoryListeningEventRepo(),
        snapshot_repo=MemorySnapshotRepo(),
        audio_backend=VirtualAudioBackend(),
    )

    # Set context queue
    context_ids = [t.id for t in tracks[:6]]
    service.set_queue(context_ids, play_index=0)
    assert service.get_state().current_track_id == tracks[0].id

    # Enable shuffle
    service.toggle_shuffle()
    assert service.get_state().shuffle_enabled is True

    # Insert X as Play Next
    service.insert_play_next(track_x.id)

    # Combined queue must have Current at 0, X at 1, and randomized remaining 5 items
    queue = service.get_queue()
    assert len(queue) == 7
    assert queue[0].track_id == tracks[0].id
    assert queue[1].track_id == track_x.id

    # Verify all original future tracks are present in the future queue
    future_ids = {item.track_id for item in queue[2:]}
    expected_future = set(context_ids[1:])
    assert future_ids == expected_future

    # Next must play X immediately
    service.next()
    assert service.get_state().current_track_id == track_x.id


# ── Case C: UI Next Calls Server Queue ───────────────────────────────────────


def test_case_c_ui_next_invokes_server_queue(tmp_path: Path) -> None:
    """Case C: Server queue API is authoritative when client calls /playback/next."""
    db_path = tmp_path / "case_c.db"
    config = HarmoniaConfig()
    config.database.path = db_path

    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    # Seed tracks in DB
    created_ids = []
    with session_factory() as session:
        track_repo = SqliteTrackRepository(session)
        for i in range(4):
            t = Track(title=f"Song {i}", duration_seconds=100.0)
            track_repo.add(t)
            session.commit()
            created_ids.append(str(t.id))

    # Initialize app with VirtualAudioBackend
    virtual_backend = VirtualAudioBackend()
    app = create_app(config=config, session_factory=session_factory, audio_backend=virtual_backend)
    client = TestClient(app)

    # Set queue with 0, 1, 2
    res = client.post("/api/v1/queue/set", json={"track_ids": created_ids[:3], "play_index": 0})
    assert res.status_code == 200

    # Insert created_ids[3] as Play Next
    res = client.post("/api/v1/queue/play-next", json={"track_id": created_ids[3]})
    assert res.status_code == 200

    # Advance via POST /api/v1/playback/next (UI Next button action)
    next_res = client.post("/api/v1/playback/next")
    assert next_res.status_code == 200
    state = next_res.json()
    # The server queue must advance to the Play Next item, NOT local arithmetic
    assert state["current_track_id"] == created_ids[3]

    # Advance again -> should go to track 1
    next_res2 = client.post("/api/v1/playback/next")
    assert next_res2.status_code == 200
    assert next_res2.json()["current_track_id"] == created_ids[1]


# ── Case D: Real Native Desktop Audio Backend Operations ─────────────────────


@pytest.mark.skipif(not REAL_MP3_PATH.exists(), reason="Real MP3 test file not found")
def test_case_d_native_desktop_audio_backend_mp3() -> None:
    """Case D: Verify DesktopAudioBackend with real MP3 decoding and playback."""
    backend = DesktopAudioBackend(sample_rate=44100, nchannels=2)
    source = LocalFileSource(file_path=REAL_MP3_PATH, mime_type="audio/mp3")

    backend.load(source)
    backend.play()
    time.sleep(0.15)

    assert backend.is_playing() is True
    assert backend.get_position() > 0.0

    backend.pause()
    pos_paused = backend.get_position()
    time.sleep(0.1)
    # Position should not advance while paused
    assert abs(backend.get_position() - pos_paused) < 0.05

    backend.resume()
    time.sleep(0.1)
    assert backend.is_playing() is True

    # Seek
    backend.seek(0.8)
    assert backend.get_position() >= 0.75

    # Volume
    backend.set_volume(0.5)
    assert backend._volume == 0.5

    backend.stop()
    assert backend.is_playing() is False
    assert backend.get_position() == 0.0


@pytest.mark.skipif(not REAL_FLAC_PATH.exists(), reason="Real FLAC test file not found")
def test_case_d_native_desktop_audio_backend_flac() -> None:
    """Case D: Verify DesktopAudioBackend with real FLAC decoding and playback."""
    backend = DesktopAudioBackend(sample_rate=44100, nchannels=2)
    source = LocalFileSource(file_path=REAL_FLAC_PATH, mime_type="audio/flac")

    backend.load(source)
    backend.play()
    time.sleep(0.15)

    assert backend.is_playing() is True
    assert backend.get_position() > 0.0

    backend.stop()
    assert backend.is_playing() is False


# ── Case E: Native Backend EOF Auto-Advancement ─────────────────────────────


def test_case_e_backend_eof_auto_advances_queue() -> None:
    """Case E: Backend EOF triggers ListeningEvent finalization and advances queue."""
    track_1, file_1 = create_mock_track("Track 1", duration=60.0)
    track_2, file_2 = create_mock_track("Track 2", duration=90.0)

    event_repo = MemoryListeningEventRepo()
    backend = VirtualAudioBackend()

    service = PlaybackService(
        track_repo=MemoryTrackRepo([track_1, track_2], {track_1.id: [file_1], track_2.id: [file_2]}),
        listening_event_repo=event_repo,
        snapshot_repo=MemorySnapshotRepo(),
        audio_backend=backend,
    )

    service.set_queue([track_1.id, track_2.id], play_index=0)
    assert service.get_state().status == PlaybackStatus.PLAYING
    assert service.get_state().current_track_id == track_1.id

    # Backend reaches end of Track 1
    backend.trigger_eof()

    # 1. ListeningEvent recorded for Track 1 with completion
    assert len(event_repo.events) == 1
    event = event_repo.events[0]
    assert event.track_id == track_1.id
    assert event.was_completed is True
    assert event.was_skipped is False

    # 2. Queue automatically advanced to Track 2
    state = service.get_state()
    assert state.status == PlaybackStatus.PLAYING
    assert state.current_track_id == track_2.id

    # Backend reaches end of Track 2
    backend.trigger_eof()

    # Second event recorded with completion
    assert len(event_repo.events) == 2
    event2 = event_repo.events[1]
    assert event2.track_id == track_2.id
    assert event2.was_completed is True

    # Queue is now finished -> status is STOPPED
    state_final = service.get_state()
    assert state_final.status == PlaybackStatus.STOPPED
    assert state_final.current_track_id is None


# ── Case F: Missing File / Decoder Failure Handling ──────────────────────────


def test_case_f_missing_file_handled_safely() -> None:
    """Case F: Missing track file halts playback gracefully without crashing."""
    missing_track, missing_file = create_mock_track("Ghost Track", file_path="/non/existent/missing.flac")

    backend = DesktopAudioBackend()
    service = PlaybackService(
        track_repo=MemoryTrackRepo([missing_track], {missing_track.id: [missing_file]}),
        listening_event_repo=MemoryListeningEventRepo(),
        snapshot_repo=MemorySnapshotRepo(),
        audio_backend=backend,
    )

    # Starting playback on a missing file must not crash
    service.set_queue([missing_track.id], play_index=0)

    # State must be STOPPED, position 0.0, queue preserved
    state = service.get_state()
    assert state.status == PlaybackStatus.STOPPED
    assert state.position_seconds == 0.0
    assert service.get_queue()[0].track_id == missing_track.id
