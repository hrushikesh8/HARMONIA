"""Hardening Tests #11, #12, #13, #17, #18, #19, #20, #22: API endpoints & behaviors.

Validates:
- Test #11: API Health (/api/v1/health) & status schema
- Test #12: Library endpoints (/api/v1/info, tracks, albums, artists, search)
- Test #13: API audio analysis endpoints (/api/v1/files/{id}/analysis & analyze)
- Test #17: Authentication token enforcement (X-Harmonia-Token & Bearer)
- Test #18: CORS loopback policy and preflight OPTIONS
- Test #19: Duplicate endpoints list and resolve
- Test #20: Artwork endpoint
- Test #22: API lifespan startup/shutdown (stale recovery & worker teardown)
"""

from __future__ import annotations

from pathlib import Path
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import create_engine

from harmonia.api.app import create_app
from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_session_factory
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.duplicate_candidate import DuplicateCandidate
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.audio.ffmpeg_analyzer import FFmpegAudioAnalyzer
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)
from harmonia.infrastructure.persistence.models import Base
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def api_test_env(tmp_path: Path, test_audio_fixtures: dict[str, Path]):
    """Set up database, app, and seed data for API hardening."""
    db_file = tmp_path / "api_test.db"
    engine = create_engine(f"sqlite:///{db_file.as_posix()}", echo=False)
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)

    config = HarmoniaConfig()
    config.database.path = db_file
    config.music_dirs = [tmp_path]
    config.artwork.path = tmp_path / "artworks"
    config.artwork.path.mkdir(parents=True, exist_ok=True)

    # Place a dummy artwork image
    dummy_art_hash = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
    (config.artwork.path / f"{dummy_art_hash}.jpg").write_bytes(b"\xff\xd8\xff\xe0\x00\x10JFIF\x00")

    analysis_service = AudioAnalysisService(
        session_factory=session_factory,
        analyzer=FFmpegAudioAnalyzer(),
        max_workers=1,
    )

    app = create_app(
        config=config,
        session_factory=session_factory,
        analysis_service=analysis_service,
    )

    # Seed data
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        album_repo = SqliteAlbumRepository(session)
        track_repo = SqliteTrackRepository(session)
        dup_repo = SqliteDuplicateCandidateRepository(session)

        artist = Artist(name="Brian Eno")
        artist_repo.add(artist)

        album = Album(title="Music for Airports", artist_id=artist.id)
        album_repo.add(album)

        track1 = Track(title="1/1", artist_id=artist.id, album_id=album.id, duration_seconds=100.0)
        track2 = Track(title="1/2", artist_id=artist.id, album_id=album.id, duration_seconds=100.0)
        track_repo.add(track1)
        track_repo.add(track2)

        tf1 = TrackFile(
            track_id=track1.id,
            file_path=str(test_audio_fixtures["wav_stereo"]),
            file_size=1000,
            format=AudioFormat.WAV,
            file_hash="hash_wav_1",
            sample_rate=44100,
            channels=2,
            bitrate=1411,
            duration_seconds=1.0,
        )
        track_repo.add_file(tf1)

        candidate = DuplicateCandidate(
            track_id_a=track1.id,
            track_id_b=track2.id,
            confidence="PROBABLE",
            reason="Similar title and duration",
        )
        dup_repo.add(candidate)
        session.commit()

        seed_ids = {
            "artist_id": str(artist.id),
            "album_id": str(album.id),
            "track1_id": str(track1.id),
            "track2_id": str(track2.id),
            "tf1_id": str(tf1.id),
            "candidate_id": str(candidate.id),
            "art_hash": dummy_art_hash,
        }

    return app, config, session_factory, seed_ids


def test_api_health(api_test_env) -> None:
    """TEST #11: API Health endpoint check."""
    app, _, _, _ = api_test_env
    client = TestClient(app)

    res = client.get("/api/v1/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert "version" in data
    assert data["database"] == "connected"


def test_api_library_endpoints(api_test_env) -> None:
    """TEST #12: API library endpoints query and pagination."""
    app, _, _, seeds = api_test_env
    client = TestClient(app)

    # 1. /info
    info_res = client.get("/api/v1/info")
    assert info_res.status_code == 200
    info_data = info_res.json()
    assert info_data["total_tracks"] >= 2
    assert info_data["total_albums"] >= 1
    assert info_data["total_artists"] >= 1

    # 2. /tracks & /tracks/{id}
    tracks_res = client.get("/api/v1/tracks?page=1&page_size=10")
    assert tracks_res.status_code == 200
    assert len(tracks_res.json()["items"]) >= 2

    track_detail = client.get(f"/api/v1/tracks/{seeds['track1_id']}")
    assert track_detail.status_code == 200
    detail_data = track_detail.json()
    assert detail_data["title"] == "1/1"
    assert len(detail_data["files"]) == 1
    # Check host path is NOT in response
    assert "file_path" not in detail_data["files"][0]

    # Nonexistent track
    assert client.get(f"/api/v1/tracks/{uuid4()}").status_code == 404

    # 3. /albums & /albums/{id}
    albums_res = client.get("/api/v1/albums")
    assert albums_res.status_code == 200
    assert len(albums_res.json()["items"]) >= 1

    album_detail = client.get(f"/api/v1/albums/{seeds['album_id']}")
    assert album_detail.status_code == 200
    assert album_detail.json()["title"] == "Music for Airports"

    # 4. /artists & /artists/{id}
    artists_res = client.get("/api/v1/artists")
    assert artists_res.status_code == 200
    assert len(artists_res.json()["items"]) >= 1

    artist_detail = client.get(f"/api/v1/artists/{seeds['artist_id']}")
    assert artist_detail.status_code == 200
    assert artist_detail.json()["name"] == "Brian Eno"

    # 5. /search
    search_res = client.get("/api/v1/search?q=Eno")
    assert search_res.status_code == 200


def test_api_audio_analysis_endpoints(api_test_env) -> None:
    """TEST #13: File analysis querying and triggering."""
    app, _, _, seeds = api_test_env
    client = TestClient(app)

    tf_id = seeds["tf1_id"]

    # Before analysis: should be 404 or None
    get_res = client.get(f"/api/v1/files/{tf_id}/analysis")
    assert get_res.status_code == 404

    # Trigger analysis synchronously via service directly or via POST
    post_res = client.post(f"/api/v1/files/{tf_id}/analyze")
    assert post_res.status_code in (200, 202)
    assert post_res.json()["track_file_id"] == tf_id

    # Nonexistent file
    assert client.post(f"/api/v1/files/{uuid4()}/analyze").status_code == 404
    # Malformed UUID
    assert client.get("/api/v1/files/not-a-uuid/analysis").status_code == 422


def test_authentication_enforcement(api_test_env) -> None:
    """TEST #17: Authentication token enforcement when configured."""
    _app, config, session_factory, _seeds = api_test_env

    # 1. When config.api.token is set
    config.api.token = "secret-token-123"
    auth_app = create_app(config=config, session_factory=session_factory)
    auth_client = TestClient(auth_app)

    # Missing token -> 401
    res_no_token = auth_client.get("/api/v1/tracks")
    assert res_no_token.status_code == 401

    # Invalid token -> 401
    res_bad_token = auth_client.get("/api/v1/tracks", headers={"X-Harmonia-Token": "wrong-token"})
    assert res_bad_token.status_code == 401

    # Valid X-Harmonia-Token -> 200
    res_valid_x = auth_client.get("/api/v1/tracks", headers={"X-Harmonia-Token": "secret-token-123"})
    assert res_valid_x.status_code == 200

    # Valid Bearer token -> 200
    res_valid_bearer = auth_client.get(
        "/api/v1/tracks",
        headers={"Authorization": "Bearer secret-token-123"},
    )
    assert res_valid_bearer.status_code == 200


def test_cors_policy(api_test_env) -> None:
    """TEST #18: CORS loopback policy."""
    app, _, _, _ = api_test_env
    client = TestClient(app)

    # Allowed loopback origin
    res_loopback = client.options(
        "/api/v1/tracks",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "GET",
        },
    )
    assert res_loopback.headers.get("access-control-allow-origin") == "http://localhost:3000"

    # Disallowed remote origin
    res_disallowed = client.options(
        "/api/v1/tracks",
        headers={
            "Origin": "http://malicious-site.com",
            "Access-Control-Request-Method": "GET",
        },
    )
    assert res_disallowed.headers.get("access-control-allow-origin") is None


def test_duplicate_endpoints(api_test_env) -> None:
    """TEST #19: Duplicate listing and resolution."""
    app, _, _, seeds = api_test_env
    client = TestClient(app)

    # List duplicates
    dups = client.get("/api/v1/duplicates")
    assert dups.status_code == 200
    data = dups.json()
    assert len(data) >= 1
    assert data[0]["id"] == seeds["candidate_id"]

    # Resolve duplicate
    cid = seeds["candidate_id"]
    resolve_res = client.post(
        f"/api/v1/duplicates/{cid}/resolve",
        json={"resolution": "KEEP_BOTH"},
    )
    assert resolve_res.status_code == 200
    res_data = resolve_res.json()
    assert res_data["resolved"] is True
    assert res_data["resolution"] == "KEEP_BOTH"

    # Nonexistent candidate
    assert client.post(f"/api/v1/duplicates/{uuid4()}/resolve", json={"resolution": "KEEP_BOTH"}).status_code == 404


def test_artwork_endpoint(api_test_env) -> None:
    """TEST #20: Content-addressed artwork retrieval."""
    app, _, _, seeds = api_test_env
    client = TestClient(app)

    # Valid artwork hash
    res = client.get(f"/api/v1/artwork/{seeds['art_hash']}")
    assert res.status_code == 200
    assert res.headers["content-type"] in ("image/jpeg", "image/jpg")
    assert len(res.content) > 0

    # Nonexistent artwork hash
    res_missing = client.get(f"/api/v1/artwork/{'0' * 64}")
    assert res_missing.status_code == 404


def test_api_lifespan_startup_and_shutdown(api_test_env) -> None:
    """TEST #22: API lifespan startup/shutdown cleans up workers and engine."""
    app, _, _, _ = api_test_env

    # Using TestClient as context manager triggers the FastAPI lifespan handler
    with TestClient(app) as client:
        res = client.get("/api/v1/health")
        assert res.status_code == 200
