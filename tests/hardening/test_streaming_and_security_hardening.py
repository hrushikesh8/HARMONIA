"""Hardening Tests #14, #15, #16, #28: HTTP Range Streaming and Security.

Validates:
- Test #14: HTTP Range Streaming (200 full, 206 partial, 416 invalid, exact byte comparison)
- Test #15: Streaming security (no arbitrary filesystem paths accepted, only valid UUIDs)
- Test #16: Zero host path leakage in JSON responses, error payloads, and headers
- Test #28: Security regression suite (path traversal, malicious headers, malformed IDs)
"""

from __future__ import annotations

import re
from pathlib import Path
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine

from harmonia.api.app import create_app
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_session_factory
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.models import Base
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def streaming_env(tmp_path: Path, test_audio_fixtures: dict[str, Path]):
    """Set up database and seed real audio file for byte streaming tests."""
    db_file = tmp_path / "streaming_test.db"
    engine = create_engine(f"sqlite:///{db_file.as_posix()}", echo=False)
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)

    config = HarmoniaConfig()
    config.database.path = db_file
    config.music_dirs = [tmp_path]
    config.artwork.path = tmp_path / "artwork"
    config.artwork.path.mkdir(parents=True, exist_ok=True)

    app = create_app(config=config, session_factory=session_factory)

    wav_file = test_audio_fixtures["wav_stereo"]
    file_bytes = wav_file.read_bytes()
    file_len = len(file_bytes)

    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Brian Eno")
        artist_repo.add(artist)

        track = Track(title="1/1", artist_id=artist.id, duration_seconds=1.0)
        track_repo.add(track)

        tf = TrackFile(
            track_id=track.id,
            file_path=str(wav_file),
            file_size=file_len,
            format=AudioFormat.WAV,
            file_hash="hash_streaming_wav",
            sample_rate=44100,
            channels=2,
            bitrate=1411,
            duration_seconds=1.0,
        )
        track_repo.add_file(tf)
        session.commit()

        file_id = tf.id

    return app, file_id, file_bytes, file_len, config


def test_http_range_streaming_byte_accuracy(streaming_env) -> None:
    """TEST #14: HTTP Range Streaming exact byte validation across all range patterns."""
    app, file_id, file_bytes, total_size, _ = streaming_env
    client = TestClient(app)

    stream_url = f"/api/v1/stream/{file_id}"

    # 1. No Range header -> 200 OK, full file
    res_full = client.get(stream_url)
    assert res_full.status_code == 200
    assert res_full.headers["accept-ranges"] == "bytes"
    assert int(res_full.headers["content-length"]) == total_size
    assert res_full.content == file_bytes

    # 2. Valid start-end range: bytes=0-499 (first 500 bytes)
    res_part = client.get(stream_url, headers={"Range": "bytes=0-499"})
    assert res_part.status_code == 206
    assert res_part.headers["content-range"] == f"bytes 0-499/{total_size}"
    assert int(res_part.headers["content-length"]) == 500
    assert res_part.content == file_bytes[0:500]

    # 3. Start-only range: bytes=500- (from byte 500 to end)
    res_start_only = client.get(stream_url, headers={"Range": "bytes=500-"})
    assert res_start_only.status_code == 206
    assert res_start_only.headers["content-range"] == f"bytes 500-{total_size - 1}/{total_size}"
    assert int(res_start_only.headers["content-length"]) == total_size - 500
    assert res_start_only.content == file_bytes[500:]

    # 4. End-only (suffix) range: bytes=-200 (last 200 bytes)
    res_suffix = client.get(stream_url, headers={"Range": "bytes=-200"})
    assert res_suffix.status_code == 206
    assert res_suffix.headers["content-range"] == f"bytes {total_size - 200}-{total_size - 1}/{total_size}"
    assert int(res_suffix.headers["content-length"]) == 200
    assert res_suffix.content == file_bytes[-200:]

    # 5. Exact final byte: bytes=(total-1)-(total-1)
    last_idx = total_size - 1
    res_last = client.get(stream_url, headers={"Range": f"bytes={last_idx}-{last_idx}"})
    assert res_last.status_code == 206
    assert res_last.headers["content-range"] == f"bytes {last_idx}-{last_idx}/{total_size}"
    assert int(res_last.headers["content-length"]) == 1
    assert res_last.content == file_bytes[last_idx : last_idx + 1]

    # 6. Unsatisfiable range: past EOF -> 416
    res_past = client.get(stream_url, headers={"Range": f"bytes={total_size + 100}-{total_size + 200}"})
    assert res_past.status_code == 416
    assert res_past.headers["content-range"] == f"bytes */{total_size}"

    # 7. Inverted range: start > end -> 416
    res_inverted = client.get(stream_url, headers={"Range": "bytes=300-100"})
    assert res_inverted.status_code == 416
    assert res_inverted.headers["content-range"] == f"bytes */{total_size}"

    # 8. Malformed range header -> 416
    res_malformed = client.get(stream_url, headers={"Range": "bytes=invalid"})
    assert res_malformed.status_code == 416


def test_streaming_security_and_path_traversal(streaming_env) -> None:
    """TEST #15 & #28: Streaming security against path traversal and arbitrary path injection."""
    app, _file_id, _, _, _ = streaming_env
    client = TestClient(app)

    # 1. Path traversal attempts through the identifier
    traversal_paths = [
        "/api/v1/stream/..%2f..%2fetc%2fpasswd",
        "/api/v1/stream/../../Windows/System32/drivers/etc/hosts",
        "/api/v1/stream/C:\\Windows\\System32\\calc.exe",
        "/api/v1/stream/....//....//something",
        "/api/v1/stream/some-file.mp3",
    ]
    for bad_path in traversal_paths:
        res = client.get(bad_path)
        # All invalid UUIDs are rejected with 422 or 404, never 200
        assert res.status_code in (404, 422), f"Path {bad_path} must be rejected"

    # 2. Nonexistent UUID
    assert client.get(f"/api/v1/stream/{uuid4()}").status_code == 404


def test_zero_host_path_leakage(streaming_env) -> None:
    """TEST #16: Zero host filesystem path leakage in responses, headers, and error messages."""
    app, file_id, _, _, _config = streaming_env
    client = TestClient(app)

    # Known sensitive strings that should NEVER appear in API responses
    # Drive letter pattern (e.g., C:\, D:\) or workspace paths
    drive_pattern = re.compile(r"[A-Za-z]:\\[^\"\s<>]+")

    endpoints_to_check = [
        "/api/v1/health",
        "/api/v1/info",
        "/api/v1/tracks",
        f"/api/v1/tracks/{file_id}",  # will 404 (track_id != file_id)
        f"/api/v1/stream/{file_id}",
        f"/api/v1/files/{file_id}/analysis",
        "/api/v1/search?q=test",
        "/api/v1/duplicates",
        "/api/v1/nonexistent-endpoint",
    ]

    for ep in endpoints_to_check:
        res = client.get(ep)
        body_text = res.text

        # 1. Check response body for drive patterns
        matches = drive_pattern.findall(body_text)
        assert not matches, f"Host drive path leaked in {ep}: {matches}"

        # 2. Check headers
        for h_name, h_val in res.headers.items():
            h_matches = drive_pattern.findall(h_val)
            assert not h_matches, f"Host drive path leaked in header {h_name} of {ep}: {h_matches}"

        # 3. Check for repository root or temp paths in body
        assert "Downloads\\H\\PROJECTS" not in body_text, f"Project path leaked in {ep}"
        assert "harmonia.db" not in body_text, f"Database path leaked in {ep}"
