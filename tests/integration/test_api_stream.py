"""Integration tests for Harmonia secure audio streaming endpoint.

Verifies:
- Valid file UUID streaming
- Nonexistent UUID handling (404)
- Path traversal rejection / no path parameter acceptance
- Full audio stream (200 OK, Content-Length, Accept-Ranges)
- Partial audio stream HTTP Range requests (206 Partial Content, Content-Range)
- Invalid HTTP Range handling (416 Range Not Satisfiable)
- Missing file on disk handling (404)
- Strict concealment of filesystem paths in headers and response bodies
"""

from pathlib import Path
from uuid import uuid4

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
def streaming_client(tmp_path: Path):
    db_path = tmp_path / "stream_test.db"
    config = HarmoniaConfig()
    config.database.path = db_path

    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    # Create dummy audio payload (1000 bytes)
    dummy_payload = bytes(i % 256 for i in range(1000))
    audio_file = tmp_path / "test_track.flac"
    audio_file.write_bytes(dummy_payload)

    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Brian Eno")
        artist_repo.add(artist)

        track = Track(
            title="Music for Airports 1/1",
            artist_id=artist.id,
            duration_seconds=1003.0,
        )
        track_repo.add(track)

        tf = TrackFile(
            track_id=track.id,
            file_path=str(audio_file),
            file_size=len(dummy_payload),
            format=AudioFormat.FLAC,
            file_hash="dummy_flac_hash_stream",
            codec="flac",
            bitrate=850,
            sample_rate=44100,
            channels=2,
            channel_layout="stereo",
            is_lossless=True,
            quality_score=95,
        )
        track_repo.add_file(tf)
        session.commit()
        tf_id = tf.id

    app = create_app(config=config, session_factory=session_factory)
    client = TestClient(app)
    return client, tf_id, dummy_payload, str(audio_file)


def test_stream_full_content(streaming_client) -> None:
    """GET /api/v1/stream/{file_id} without Range returns 200 OK and entire content."""
    client, file_id, dummy_payload, disk_path = streaming_client

    res = client.get(f"/api/v1/stream/{file_id}")
    assert res.status_code == 200
    assert res.headers["accept-ranges"] == "bytes"
    assert res.headers["content-length"] == str(len(dummy_payload))
    assert res.headers["content-type"] in ("audio/flac", "audio/x-flac")
    assert res.content == dummy_payload

    # Security check: verify disk path is NOT present anywhere in headers
    for _header_name, header_val in res.headers.items():
        assert disk_path not in header_val


def test_stream_range_partial_content(streaming_client) -> None:
    """GET /api/v1/stream/{file_id} with Range returns 206 Partial Content."""
    client, file_id, dummy_payload, _disk_path = streaming_client

    # Request bytes 0-99 (100 bytes)
    res = client.get(
        f"/api/v1/stream/{file_id}",
        headers={"Range": "bytes=0-99"},
    )
    assert res.status_code == 206
    assert res.headers["accept-ranges"] == "bytes"
    assert res.headers["content-range"] == f"bytes 0-99/{len(dummy_payload)}"
    assert res.headers["content-length"] == "100"
    assert res.content == dummy_payload[0:100]

    # Request bytes 500- (from 500 to end: 500 bytes)
    res2 = client.get(
        f"/api/v1/stream/{file_id}",
        headers={"Range": "bytes=500-"},
    )
    assert res2.status_code == 206
    assert res2.headers["content-range"] == f"bytes 500-999/{len(dummy_payload)}"
    assert res2.headers["content-length"] == "500"
    assert res2.content == dummy_payload[500:]

    # Request suffix bytes -100 (last 100 bytes: 900-999)
    res3 = client.get(
        f"/api/v1/stream/{file_id}",
        headers={"Range": "bytes=-100"},
    )
    assert res3.status_code == 206
    assert res3.headers["content-range"] == f"bytes 900-999/{len(dummy_payload)}"
    assert res3.headers["content-length"] == "100"
    assert res3.content == dummy_payload[900:1000]


def test_stream_invalid_range(streaming_client) -> None:
    """Invalid or unsatisfiable Range returns 416 Range Not Satisfiable."""
    client, file_id, dummy_payload, _ = streaming_client

    # Range start beyond file size
    res = client.get(
        f"/api/v1/stream/{file_id}",
        headers={"Range": "bytes=2000-3000"},
    )
    assert res.status_code == 416
    assert res.headers["content-range"] == f"bytes */{len(dummy_payload)}"

    # Inverted range start > end
    res2 = client.get(
        f"/api/v1/stream/{file_id}",
        headers={"Range": "bytes=500-200"},
    )
    assert res2.status_code == 416


def test_stream_nonexistent_file_uuid(streaming_client) -> None:
    """Requesting nonexistent file UUID returns 404."""
    client, _, _, _ = streaming_client

    fake_id = uuid4()
    res = client.get(f"/api/v1/stream/{fake_id}")
    assert res.status_code == 404
    data = res.json()
    assert "not found" in data["detail"].lower()


def test_stream_missing_physical_file(streaming_client, tmp_path: Path) -> None:
    """TrackFile registered in DB but deleted on disk returns 404 without leaking path."""
    client, file_id, _, disk_path = streaming_client

    # Delete file from disk
    Path(disk_path).unlink()

    res = client.get(f"/api/v1/stream/{file_id}")
    assert res.status_code == 404
    data = res.json()
    assert disk_path not in data["detail"]
    assert "audio file missing" in data["detail"].lower()


def test_stream_no_path_parameter_accepted(streaming_client) -> None:
    """API router does not accept arbitrary file paths or path traversal."""
    client, _, _, _ = streaming_client

    # Any non-UUID path segment fails routing / validation
    res = client.get("/api/v1/stream/..%2F..%2Fetc%2Fpasswd")
    assert res.status_code in (404, 422)

    res2 = client.get("/api/v1/stream/C:\\Windows\\System32\\cmd.exe")
    assert res2.status_code in (404, 422)
