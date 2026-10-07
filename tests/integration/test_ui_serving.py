"""Integration tests for Harmonia UI static asset serving and response contracts."""

from __future__ import annotations

from typing import TYPE_CHECKING
from unittest.mock import MagicMock

from fastapi.testclient import TestClient

from harmonia.api.app import create_app
from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from pathlib import Path


def test_ui_index_and_static_serving(tmp_path: Path) -> None:
    """Verify that root, /ui, and static assets (CSS, JS) are served correctly."""
    db_path = tmp_path / "ui_test.db"
    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    config = HarmoniaConfig()
    config.database.path = db_path
    config.music_dirs = [tmp_path]

    analysis_service = MagicMock(spec=AudioAnalysisService)
    app = create_app(
        config=config,
        session_factory=session_factory,
        analysis_service=analysis_service,
    )

    client = TestClient(app)

    # 1. Root / endpoint serves index.html
    root_resp = client.get("/")
    assert root_resp.status_code == 200
    assert "text/html" in root_resp.headers["content-type"]
    assert "HARMONIA" in root_resp.text
    assert "AUDIO INTELLIGENCE" in root_resp.text
    assert "playPauseBtn" in root_resp.text

    # 2. /ui endpoint serves index.html
    ui_resp = client.get("/ui")
    assert ui_resp.status_code == 200
    assert "text/html" in ui_resp.headers["content-type"]
    assert "Harmonia" in ui_resp.text

    # 3. /static/styles.css is served with correct mime
    css_resp = client.get("/static/styles.css")
    assert css_resp.status_code == 200
    assert "text/css" in css_resp.headers["content-type"]
    assert "--bg-deep" in css_resp.text

    # 4. /static/app.js is served with javascript mime
    js_resp = client.get("/static/app.js")
    assert js_resp.status_code == 200
    assert any(t in js_resp.headers["content-type"] for t in ("javascript", "text/plain"))
    assert "loadLibrary" in js_resp.text

    # 5. /static/favicon.svg is served with image/svg+xml mime
    fav_resp = client.get("/static/favicon.svg")
    assert fav_resp.status_code == 200
    assert "svg" in fav_resp.headers["content-type"]
    assert "Harmonia Favicon" in fav_resp.text

    # 6. /static/brand/harmonia-mark.svg is served
    brand_resp = client.get("/static/brand/harmonia-mark.svg")
    assert brand_resp.status_code == 200
    assert "svg" in brand_resp.headers["content-type"]
    assert "Harmonia Symbol" in brand_resp.text


def test_track_api_includes_ui_fields(tmp_path: Path) -> None:
    """Verify TrackResponse and TrackDetail DTOs expose file_id, album_id, and art_url."""
    db_path = tmp_path / "ui_api_test.db"
    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    config = HarmoniaConfig()
    config.database.path = db_path
    config.music_dirs = [tmp_path]

    # Seed an artist, album with artwork, and track with track_file
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        album_repo = SqliteAlbumRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Daft Punk")
        artist_repo.add(artist)

        art_hash = "a" * 64
        album = Album(
            title="Random Access Memories",
            artist_id=artist.id,
            art_hash=art_hash,
        )
        album_repo.add(album)

        track = Track(
            title="Get Lucky",
            artist_id=artist.id,
            album_id=album.id,
            duration_seconds=248.0,
        )
        track_repo.add(track)

        audio_file = tmp_path / "song.flac"
        audio_file.write_bytes(b"dummy audio data")

        track_file = TrackFile(
            track_id=track.id,
            file_path=str(audio_file),
            format=AudioFormat.FLAC,
            bitrate=1411,
            sample_rate=44100,
            channels=2,
            duration_seconds=248.0,
            file_size=audio_file.stat().st_size,
            file_hash="b" * 64,
        )
        track_repo.add_file(track_file)
        session.commit()

        track_id = track.id
        file_id = track_file.id
        album_id = album.id

    analysis_service = MagicMock(spec=AudioAnalysisService)
    app = create_app(
        config=config,
        session_factory=session_factory,
        analysis_service=analysis_service,
    )
    client = TestClient(app)

    # List tracks
    list_resp = client.get("/api/v1/tracks")
    assert list_resp.status_code == 200
    tracks = list_resp.json()["items"]
    assert len(tracks) == 1
    t0 = tracks[0]
    assert t0["id"] == str(track_id)
    assert t0["file_id"] == str(file_id)
    assert t0["album_id"] == str(album_id)
    assert t0["art_url"] == f"/api/v1/artwork/{art_hash}"
    assert t0["format"] == "flac"
    assert t0["is_lossless"] is False
    assert t0["analysis_status"] is None

    # Get track details
    detail_resp = client.get(f"/api/v1/tracks/{track_id}")
    assert detail_resp.status_code == 200
    detail = detail_resp.json()
    assert detail["id"] == str(track_id)
    assert detail["file_id"] == str(file_id)
    assert detail["album_id"] == str(album_id)
    assert detail["art_url"] == f"/api/v1/artwork/{art_hash}"
    assert detail["format"] == "flac"
    assert detail["is_lossless"] is False
    assert detail["analysis_status"] is None
    # Verify no local filesystem path leaked in public detail
    assert str(tmp_path) not in detail_resp.text


def test_cli_ui_command() -> None:
    """Verify typer CLI includes harmonia ui."""
    from typer.testing import CliRunner

    from harmonia.cli.app import app

    runner = CliRunner()
    result = runner.invoke(app, ["ui", "--help"])
    assert result.exit_code == 0
    assert "Launch the Harmonia desktop web UI" in result.output
