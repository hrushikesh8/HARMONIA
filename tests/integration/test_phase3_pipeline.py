"""End-to-end integration test for Phase 3 pipeline:

TrackFile -> Analysis (mocked / real) -> Database -> API -> Response
"""

from pathlib import Path
from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient

from harmonia.api.app import create_app
from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.ports.audio_analyzer import (
    SignalAnalysisResult,
    TechnicalAudioProperties,
)
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def phase3_env(tmp_path: Path):
    db_path = tmp_path / "pipeline.db"
    config = HarmoniaConfig()
    config.database.path = db_path

    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    # Create dummy audio file
    dummy_file = tmp_path / "song.flac"
    dummy_file.write_bytes(b"FLAC" + b"\x00" * 200)

    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Brian Eno")
        artist_repo.add(artist)

        track = Track(title="An Ending (Ascent)", artist_id=artist.id, duration_seconds=266.0)
        track_repo.add(track)

        tf = TrackFile(
            track_id=track.id,
            file_path=str(dummy_file),
            file_size=len(dummy_file.read_bytes()),
            format=AudioFormat.FLAC,
            file_hash="hash_ascent_123",
            codec="flac",
            bitrate=700,
            sample_rate=44100,
            channels=2,
            is_lossless=True,
            quality_score=90,
        )
        track_repo.add_file(tf)
        session.commit()
        track_id = track.id
        file_id = tf.id

    mock_analyzer = MagicMock()
    mock_analyzer.analyze_signal.return_value = SignalAnalysisResult(
        integrated_lufs=-18.4,
        loudness_range_lu=5.6,
        true_peak_dbtp=-1.2,
        sample_peak_dbfs=-1.5,
        peak_to_loudness_db=17.2,
        clipping_samples=0,
        is_clipped=False,
        duration_analyzed=266.0,
        silence_start_sec=0.2,
        silence_end_sec=0.5,
        analyzer_name="Harmonia Objective Audio Analyzer",
        analyzer_version="0.3.0",
        config_version="1.0",
    )
    mock_analyzer.probe_technical.return_value = TechnicalAudioProperties(
        channel_layout="stereo",
        encoder="Lavf62.1.103",
    )

    analysis_service = AudioAnalysisService(
        session_factory=session_factory,
        analyzer=mock_analyzer,
        max_workers=1,
    )
    analysis_service.start_worker()

    app = create_app(
        config=config,
        session_factory=session_factory,
        analysis_service=analysis_service,
    )
    client = TestClient(app)

    try:
        yield client, analysis_service, track_id, file_id, str(dummy_file)
    finally:
        analysis_service.stop_worker()


def test_phase3_full_pipeline(phase3_env) -> None:
    """Verify TrackFile -> Enqueue analysis -> Complete job -> API retrieval."""
    client, analysis_service, track_id, file_id, disk_path = phase3_env

    # 1. Check initial analysis status (not analyzed yet)
    res_before = client.get(f"/api/v1/files/{file_id}/analysis")
    assert res_before.status_code == 404

    # 2. Enqueue analysis via API
    post_res = client.post(f"/api/v1/files/{file_id}/analyze")
    assert post_res.status_code in (200, 202)
    post_data = post_res.json()
    assert post_data["track_file_id"] == str(file_id)
    assert post_data["status"].lower() in ("pending", "running", "completed")

    # 3. Synchronously drain worker queue
    analysis_service.drain()

    # 4. Query canonical file analysis endpoint
    res_after = client.get(f"/api/v1/files/{file_id}/analysis")
    assert res_after.status_code == 200
    file_analysis = res_after.json()
    assert file_analysis["track_file_id"] == str(file_id)
    assert file_analysis["status"].lower() == "completed"
    assert file_analysis["integrated_lufs"] == -18.4
    assert file_analysis["loudness_range_lu"] == 5.6
    assert file_analysis["true_peak_dbtp"] == -1.2
    assert file_analysis["clipping_samples"] == 0
    assert file_analysis["is_clipped"] is False
    assert file_analysis["analyzer_version"] == "0.3.0"

    # Verify no filesystem path leakage anywhere in analysis JSON
    assert disk_path not in str(file_analysis)

    # 5. Query multi-file track-level analysis endpoint
    res_track = client.get(f"/api/v1/tracks/{track_id}/analysis")
    assert res_track.status_code == 200
    track_analysis = res_track.json()
    assert track_analysis["track_id"] == str(track_id)
    assert len(track_analysis["analyses"]) == 1
    assert track_analysis["analyses"][0]["track_file_id"] == str(file_id)
    assert track_analysis["analyses"][0]["integrated_lufs"] == -18.4

    # 6. Verify stream works for this file
    stream_res = client.get(f"/api/v1/stream/{file_id}")
    assert stream_res.status_code == 200
    assert disk_path not in str(stream_res.headers)
