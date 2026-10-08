"""Integration tests for Phase 4 playback, queue, and listening events API endpoints."""

from __future__ import annotations

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from harmonia.api.app import create_app
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def client_and_tracks(tmp_path: Path):
    db_path = tmp_path / "playback_api_test.db"
    config = HarmoniaConfig()
    config.database.path = db_path

    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    # Create dummy tracks
    created_tracks = []
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Daft Punk")
        artist_repo.add(artist)

        for i in range(1, 4):
            audio_file = tmp_path / f"track_{i}.flac"
            audio_file.write_bytes(b"dummy audio payload")

            t = Track(title=f"Song {i}", artist_id=artist.id, duration_seconds=120.0 * i)
            track_repo.add(t)

            tf = TrackFile(
                track_id=t.id,
                file_path=str(audio_file),
                file_size=1000,
                format=AudioFormat.FLAC,
                duration_seconds=120.0 * i,
                quality_score=95,
            )
            track_repo.add_file(tf)
            created_tracks.append(t)
        session.commit()

    app = create_app(config=config, session_factory=session_factory)
    client = TestClient(app)
    return client, created_tracks


def test_api_playback_and_queue_flow(client_and_tracks) -> None:
    client, tracks = client_and_tracks

    # 1. Initially queue is empty and state is IDLE
    state_res = client.get("/api/v1/playback/state")
    assert state_res.status_code == 200
    assert state_res.json()["status"] in {"idle", "stopped", "paused"}

    # 2. Set queue with track 0 and track 1
    t0_id = str(tracks[0].id)
    t1_id = str(tracks[1].id)
    set_res = client.post(
        "/api/v1/queue/set",
        json={"track_ids": [t0_id, t1_id], "play_index": 0},
    )
    assert set_res.status_code == 200
    queue_items = set_res.json()
    assert len(queue_items) == 2
    assert queue_items[0]["track_id"] == t0_id

    # 3. Verify state is PLAYING with track 0
    state_res = client.get("/api/v1/playback/state")
    assert state_res.status_code == 200
    state_data = state_res.json()
    assert state_data["status"] == "playing"
    assert state_data["current_track_id"] == t0_id

    # 4. Pause and Resume
    pause_res = client.post("/api/v1/playback/pause")
    assert pause_res.status_code == 200
    assert pause_res.json()["status"] == "paused"

    resume_res = client.post("/api/v1/playback/resume")
    assert resume_res.status_code == 200
    assert resume_res.json()["status"] == "playing"

    # 5. Seek
    seek_res = client.post("/api/v1/playback/seek", json={"position_seconds": 45.0})
    assert seek_res.status_code == 200
    assert seek_res.json()["position_seconds"] == 45.0

    # 6. Volume
    vol_res = client.post("/api/v1/playback/volume", json={"volume": 0.75})
    assert vol_res.status_code == 200
    assert vol_res.json()["volume"] == 0.75

    # 7. Repeat mode
    repeat_res = client.post("/api/v1/playback/repeat", json={"mode": "all"})
    assert repeat_res.status_code == 200
    assert repeat_res.json()["repeat_mode"] == "all"

    # 8. Shuffle mode toggle
    shuf_res = client.post("/api/v1/playback/shuffle", json={"enabled": True})
    assert shuf_res.status_code == 200
    assert shuf_res.json()["shuffle_enabled"] is True

    # 9. Play Next insertion with track 2
    t2_id = str(tracks[2].id)
    pn_res = client.post("/api/v1/queue/play-next", json={"track_id": t2_id})
    assert pn_res.status_code == 200
    assert any(it["track_id"] == t2_id and it["is_manual"] is True for it in pn_res.json())

    # 10. Advance to next: should play manual item (track 2)
    next_res = client.post("/api/v1/playback/next")
    assert next_res.status_code == 200
    assert next_res.json()["current_track_id"] == t2_id

    # 11. Advance again: should resume context queue with track 1
    next_res2 = client.post("/api/v1/playback/next")
    assert next_res2.status_code == 200
    assert next_res2.json()["current_track_id"] == t1_id

    # 12. Stop playback
    stop_res = client.post("/api/v1/playback/stop")
    assert stop_res.status_code == 200
    assert stop_res.json()["status"] == "stopped"

    # 13. Verify Listening Events were recorded
    events_res = client.get("/api/v1/listening-events")
    assert events_res.status_code == 200
    events_data = events_res.json()
    assert events_data["total"] >= 2
    assert len(events_data["items"]) >= 2

    # 14. Verify Listening Stats
    stats_res = client.get("/api/v1/listening-events/stats")
    assert stats_res.status_code == 200
    assert stats_res.json()["total_events"] >= 2

    # 15. Clear queue
    clear_res = client.delete("/api/v1/queue")
    assert clear_res.status_code == 204

    q_empty_res = client.get("/api/v1/queue")
    assert q_empty_res.status_code == 200
    assert len(q_empty_res.json()) == 0
