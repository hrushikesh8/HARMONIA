"""Integration tests for Harmonia FastAPI REST API endpoints."""

from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from harmonia.api.app import create_app
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.audio_analysis import AnalysisStatus, AudioAnalysis
from harmonia.domain.entities.duplicate_candidate import DuplicateCandidate
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.audio_analysis_repo import (
    SqliteAudioAnalysisRepository,
)
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def api_client(tmp_path: Path) -> TestClient:
    db_path = tmp_path / "api_test.db"
    art_dir = tmp_path / "artwork"
    art_dir.mkdir(parents=True, exist_ok=True)

    config = HarmoniaConfig()
    config.database.path = db_path
    config.artwork.path = art_dir

    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    # Seed test data
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        album_repo = SqliteAlbumRepository(session)
        track_repo = SqliteTrackRepository(session)
        search_repo = SqliteSearchRepository(session)
        analysis_repo = SqliteAudioAnalysisRepository(session)
        dup_repo = SqliteDuplicateCandidateRepository(session)

        # Artist & Album
        artist = Artist(name="David Bowie", sort_name="Bowie, David")
        artist_repo.add(artist)

        album = Album(title="Heroes", artist_id=artist.id, year=1977, total_tracks=1)
        album.art_path = "bowie_art_hash_123"
        album_repo.add(album)

        # Dummy artwork image
        art_file = art_dir / "bowie_art_hash_123.jpg"
        art_file.write_bytes(b"\xff\xd8\xff\xe0dummy_jpeg_bytes")

        # Track & Files
        track = Track(
            title="Heroes",
            artist_id=artist.id,
            album_id=album.id,
            track_number=3,
            disc_number=1,
            genre="Art Rock",
            year=1977,
            duration_seconds=367.0,
        )
        track_repo.add(track)

        # File on disk
        song_file = tmp_path / "heroes.flac"
        song_file.write_bytes(b"flac_sample_audio_stream_data_here")

        tf = TrackFile(
            track_id=track.id,
            file_path=str(song_file),
            file_size=len(song_file.read_bytes()),
            format=AudioFormat.FLAC,
            file_hash="hash_heroes_flac",
            codec="flac",
            bitrate=900,
            sample_rate=44100,
            channels=2,
            channel_layout="stereo",
            encoder="Lavf62.1.103",
            is_lossless=True,
            quality_score=95,
        )
        track_repo.add_file(tf)

        # AudioAnalysis
        analysis = AudioAnalysis(
            track_file_id=tf.id,
            file_hash=tf.file_hash,
            status=AnalysisStatus.COMPLETED,
            integrated_lufs=-14.2,
            loudness_range_lu=8.5,
            true_peak_dbtp=-0.4,
            sample_peak_dbfs=-0.6,
            peak_to_loudness_db=13.8,
            clipping_samples=0,
            is_clipped=False,
            duration_analyzed=367.0,
            silence_start_sec=0.1,
            silence_end_sec=0.3,
        )
        analysis_repo.save(analysis)

        # FTS Index
        search_repo.index_track(
            track_id=track.id,
            title=track.title,
            artist_name=artist.name,
            album_title=album.title,
            genre=track.genre,
        )

        track_b = Track(
            title="Heroes (Single Version)",
            artist_id=artist.id,
            album_id=album.id,
            track_number=4,
            disc_number=1,
            genre="Art Rock",
            year=1977,
            duration_seconds=365.0,
        )
        track_repo.add(track_b)

        # Duplicate Candidate
        dup = DuplicateCandidate(
            track_id_a=track.id,
            track_id_b=track_b.id,
            confidence="probable",
            reason="Exact title match with duration delta < 2s",
        )
        dup_repo.add(dup)

        session.commit()

    app = create_app(config=config, session_factory=session_factory)
    client = TestClient(app)
    return client


def test_api_health(api_client: TestClient) -> None:
    res = api_client.get("/api/v1/health")
    assert res.status_code == 200
    data = res.json()
    assert data["status"] == "ok"
    assert data["database"] == "connected"
    assert "version" in data


def test_api_info(api_client: TestClient) -> None:
    res = api_client.get("/api/v1/info")
    assert res.status_code == 200
    data = res.json()
    assert data["total_tracks"] >= 1
    assert data["total_albums"] >= 1
    assert data["total_artists"] >= 1
    assert data["total_files"] >= 1


def test_api_tracks_list_and_pagination(api_client: TestClient) -> None:
    res = api_client.get("/api/v1/tracks?page=1&page_size=10")
    assert res.status_code == 200
    data = res.json()
    assert data["total"] >= 1
    assert data["page"] == 1
    assert len(data["items"]) >= 1
    assert data["items"][0]["title"] == "Heroes"


def test_api_track_detail_path_concealment(api_client: TestClient) -> None:
    # First get track id
    tracks = api_client.get("/api/v1/tracks").json()["items"]
    track_id = tracks[0]["id"]

    res = api_client.get(f"/api/v1/tracks/{track_id}")
    assert res.status_code == 200
    data = res.json()
    assert data["id"] == track_id
    assert data["title"] == "Heroes"
    assert len(data["files"]) >= 1

    file_info = data["files"][0]
    # CRITICAL: Verify file_path is completely concealed and omitted
    assert "file_path" not in file_info
    assert "stream_url" in file_info
    assert file_info["stream_url"] == f"/api/v1/stream/{file_info['id']}"
    assert file_info["channel_layout"] == "stereo"
    assert file_info["encoder"] == "Lavf62.1.103"


def test_api_track_analysis_multi_file(api_client: TestClient) -> None:
    tracks = api_client.get("/api/v1/tracks").json()["items"]
    track_id = tracks[0]["id"]

    res = api_client.get(f"/api/v1/tracks/{track_id}/analysis")
    assert res.status_code == 200
    data = res.json()
    assert data["track_id"] == track_id
    assert len(data["analyses"]) >= 1
    analysis = data["analyses"][0]
    assert analysis["status"] == "COMPLETED"
    assert analysis["integrated_lufs"] == -14.2
    assert analysis["true_peak_dbtp"] == -0.4
    assert analysis["peak_to_loudness_db"] == 13.8


def test_api_file_analysis_canonical_endpoint(api_client: TestClient) -> None:
    track = api_client.get("/api/v1/tracks").json()["items"][0]
    track_detail = api_client.get(f"/api/v1/tracks/{track['id']}").json()
    file_id = track_detail["files"][0]["id"]

    res = api_client.get(f"/api/v1/files/{file_id}/analysis")
    assert res.status_code == 200
    data = res.json()
    assert data["track_file_id"] == file_id
    assert data["status"] == "COMPLETED"
    assert data["integrated_lufs"] == -14.2
    assert data["loudness_range_lu"] == 8.5
    assert data["is_clipped"] is False


def test_api_albums(api_client: TestClient) -> None:
    res = api_client.get("/api/v1/albums")
    assert res.status_code == 200
    data = res.json()
    assert data["total"] >= 1
    album_id = data["items"][0]["id"]

    detail_res = api_client.get(f"/api/v1/albums/{album_id}")
    assert detail_res.status_code == 200
    detail_data = detail_res.json()
    assert detail_data["title"] == "Heroes"
    assert len(detail_data["tracks"]) >= 1
    assert detail_data["art_url"] == "/api/v1/artwork/bowie_art_hash_123"


def test_api_artists(api_client: TestClient) -> None:
    res = api_client.get("/api/v1/artists")
    assert res.status_code == 200
    data = res.json()
    assert data["total"] >= 1
    artist_id = data["items"][0]["id"]

    detail_res = api_client.get(f"/api/v1/artists/{artist_id}")
    assert detail_res.status_code == 200
    detail_data = detail_res.json()
    assert detail_data["name"] == "David Bowie"
    assert len(detail_data["albums"]) >= 1


def test_api_search(api_client: TestClient) -> None:
    res = api_client.get("/api/v1/search?q=heroes")
    assert res.status_code == 200
    data = res.json()
    assert data["query"] == "heroes"
    assert data["total_matches"] >= 1
    assert data["items"][0]["title"] == "Heroes"


def test_api_artwork(api_client: TestClient) -> None:
    res = api_client.get("/api/v1/artwork/bowie_art_hash_123")
    assert res.status_code == 200
    assert res.headers["content-type"] == "image/jpeg"
    assert res.headers["cache-control"] == "public, max-age=31536000, immutable"
    assert res.content == b"\xff\xd8\xff\xe0dummy_jpeg_bytes"

    # Nonexistent artwork
    res_404 = api_client.get("/api/v1/artwork/nonexistent_hash")
    assert res_404.status_code == 404


def test_api_duplicates_workflow(api_client: TestClient) -> None:
    res = api_client.get("/api/v1/duplicates")
    assert res.status_code == 200
    data = res.json()
    assert len(data) >= 1
    candidate_id = data[0]["id"]

    resolve_res = api_client.post(
        f"/api/v1/duplicates/{candidate_id}/resolve",
        json={"resolution": "distinct_tracks"},
    )
    assert resolve_res.status_code == 200
    resolved_data = resolve_res.json()
    assert resolved_data["resolved"] is True
    assert resolved_data["resolution"] == "distinct_tracks"


def test_api_token_auth(tmp_path: Path) -> None:
    config = HarmoniaConfig()
    config.database.path = tmp_path / "auth_test.db"
    config.api.token = "secret123"

    engine = create_database_engine(config.database.path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    app = create_app(config=config, session_factory=session_factory)
    client = TestClient(app)

    # 1. Request without token -> 401 Unauthorized
    res_no_token = client.get("/api/v1/health")
    assert res_no_token.status_code == 401

    # 2. Request with invalid token -> 401 Unauthorized
    res_bad_token = client.get("/api/v1/health", headers={"X-Harmonia-Token": "wrong"})
    assert res_bad_token.status_code == 401

    # 3. Request with valid header token -> 200 OK
    res_valid_header = client.get("/api/v1/health", headers={"X-Harmonia-Token": "secret123"})
    assert res_valid_header.status_code == 200

    # 4. Request with valid Bearer token -> 200 OK
    res_valid_bearer = client.get("/api/v1/health", headers={"Authorization": "Bearer secret123"})
    assert res_valid_bearer.status_code == 200

    engine.dispose()
