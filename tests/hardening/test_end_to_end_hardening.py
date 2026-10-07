"""Hardening Tests #21 & #27: CLI Execution and Real End-to-End Pipeline.

Validates:
- Test #21: CLI invocation (harmonia --help, analyze --help, serve --help, harmonia analyze <file>)
- Test #27: The complete end-to-end pipeline without mocks:
    real audio file on disk
        ↓
    TrackFile & Track
        ↓
    AudioAnalysisService
        ↓
    real FFprobe (codec, channels, duration)
        ↓
    real FFmpeg (ebur128, volumedetect, silencedetect)
        ↓
    AudioAnalysis SQLite persistence
        ↓
    FastAPI /api/v1/files/{id}/analysis
        ↓
    HTTP client retrieves analysis
        ↓
    HTTP Range Streaming /api/v1/stream/{id}
        ↓
    Byte-for-byte match with source file
"""

from __future__ import annotations

from pathlib import Path
from uuid import uuid4

from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from typer.testing import CliRunner

from harmonia.api.app import create_app
from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.cli.app import app as cli_app
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_session_factory
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.audio.ffmpeg_analyzer import FFmpegAudioAnalyzer
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.models import Base
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

runner = CliRunner()


def test_cli_help_and_execution(tmp_path: Path, test_audio_fixtures: dict[str, Path]) -> None:
    """TEST #21: CLI --help options and analyze command execution."""
    # 1. harmonia --help
    res_main = runner.invoke(cli_app, ["--help"])
    assert res_main.exit_code == 0
    assert "Harmonia" in res_main.output
    assert "analyze" in res_main.output
    assert "serve" in res_main.output

    # 2. harmonia analyze --help
    res_analyze_help = runner.invoke(cli_app, ["analyze", "--help"])
    assert res_analyze_help.exit_code == 0
    assert "signal analysis" in res_analyze_help.output.lower() or "ebur128" in res_analyze_help.output.lower()

    # 3. harmonia serve --help
    res_serve_help = runner.invoke(cli_app, ["serve", "--help"])
    assert res_serve_help.exit_code == 0
    assert "host" in res_serve_help.output.lower()
    assert "port" in res_serve_help.output.lower()

    # 4. Set up a database with a registered TrackFile to test `harmonia analyze`
    db_file = tmp_path / "cli_test.db"
    engine = create_engine(f"sqlite:///{db_file.as_posix()}", echo=False)
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)

    real_audio_path = test_audio_fixtures["wav_stereo"]

    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Brian Eno")
        artist_repo.add(artist)

        track = Track(title="1/1", artist_id=artist.id, duration_seconds=1.0)
        track_repo.add(track)

        tf = TrackFile(
            track_id=track.id,
            file_path=str(real_audio_path),
            file_size=real_audio_path.stat().st_size,
            format=AudioFormat.WAV,
            file_hash="hash_cli_test",
            sample_rate=44100,
            channels=2,
            bitrate=1411,
            duration_seconds=1.0,
        )
        track_repo.add_file(tf)
        session.commit()
        tf_id = str(tf.id)

    # 5. Execute `harmonia analyze <UUID> --db <path>`
    res_run = runner.invoke(cli_app, ["analyze", tf_id, "--db", str(db_file)])
    assert res_run.exit_code == 0
    assert "COMPLETED" in res_run.output
    assert "LUFS" in res_run.output

    # 6. Execute `harmonia analyze <invalid-file>` -> exit code 1
    res_invalid = runner.invoke(cli_app, ["analyze", str(uuid4()), "--db", str(db_file)])
    assert res_invalid.exit_code != 0


def test_real_end_to_end_pipeline(tmp_path: Path, test_audio_fixtures: dict[str, Path]) -> None:
    """TEST #27: Real end-to-end pipeline without mocks.

    From physical audio file -> DB -> AudioAnalysisService -> real FFprobe/FFmpeg ->
    FastAPI endpoints -> API client retrieval -> HTTP byte range streaming.
    """
    db_file = tmp_path / "e2e.db"
    engine = create_engine(f"sqlite:///{db_file.as_posix()}", echo=False)
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)

    config = HarmoniaConfig()
    config.database.path = db_file
    config.music_dirs = [tmp_path]
    config.artwork.path = tmp_path / "artwork"
    config.artwork.path.mkdir(parents=True, exist_ok=True)

    # 1. Physical audio file on disk
    audio_path = test_audio_fixtures["wav_stereo"]
    source_bytes = audio_path.read_bytes()
    source_len = len(source_bytes)

    # 2. Ingest Track & TrackFile
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Brian Eno")
        artist_repo.add(artist)

        track = Track(title="1/1", artist_id=artist.id, duration_seconds=1.0)
        track_repo.add(track)

        tf = TrackFile(
            track_id=track.id,
            file_path=str(audio_path),
            file_size=source_len,
            format=AudioFormat.WAV,
            file_hash="hash_e2e_wav",
            sample_rate=44100,
            channels=2,
            bitrate=1411,
            duration_seconds=1.0,
        )
        track_repo.add_file(tf)
        session.commit()
        tf_id = tf.id

    # 3. Initialize AudioAnalysisService with real FFmpegAudioAnalyzer (no mocks)
    analysis_service = AudioAnalysisService(
        session_factory=session_factory,
        analyzer=FFmpegAudioAnalyzer(),
    )

    # 4. Create FastAPI app
    app = create_app(
        config=config,
        session_factory=session_factory,
        analysis_service=analysis_service,
    )
    client = TestClient(app)

    # 5. Trigger analysis via API: POST /api/v1/files/{file_id}/analyze?sync=true
    post_res = client.post(f"/api/v1/files/{tf_id}/analyze?sync=true")
    assert post_res.status_code == 200
    analysis_data = post_res.json()
    assert analysis_data["status"] == "COMPLETED"
    assert analysis_data["track_file_id"] == str(tf_id)
    assert analysis_data["integrated_lufs"] is not None
    assert analysis_data["true_peak_dbtp"] is not None

    # 6. Retrieve analysis via API: GET /api/v1/files/{file_id}/analysis
    get_res = client.get(f"/api/v1/files/{tf_id}/analysis")
    assert get_res.status_code == 200
    get_data = get_res.json()
    assert get_data["id"] == analysis_data["id"]
    assert get_data["integrated_lufs"] == analysis_data["integrated_lufs"]

    # 7. Stream via HTTP Range: GET /api/v1/stream/{file_id}
    # Test full stream
    stream_res = client.get(f"/api/v1/stream/{tf_id}")
    assert stream_res.status_code == 200
    assert stream_res.headers["content-length"] == str(source_len)
    assert stream_res.content == source_bytes

    # Test partial byte range stream (first 256 bytes)
    partial_res = client.get(f"/api/v1/stream/{tf_id}", headers={"Range": "bytes=0-255"})
    assert partial_res.status_code == 206
    assert partial_res.headers["content-range"] == f"bytes 0-255/{source_len}"
    assert partial_res.headers["content-length"] == "256"
    assert partial_res.content == source_bytes[:256]
