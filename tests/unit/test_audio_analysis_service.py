"""Unit tests for AudioAnalysisService."""

from datetime import UTC, datetime, timedelta
from pathlib import Path
from unittest.mock import MagicMock

import pytest
from sqlalchemy.orm import Session

from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.domain.entities.audio_analysis import AnalysisStatus, AudioAnalysis
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.ports.audio_analyzer import (
    SignalAnalysisResult,
    TechnicalAudioProperties,
)
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.audio_analysis_repo import (
    SqliteAudioAnalysisRepository,
)
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def mock_analyzer() -> MagicMock:
    analyzer = MagicMock()
    analyzer.ANALYZER_NAME = "ffmpeg_ebur128"
    analyzer.ANALYZER_VERSION = "1.0.0"
    analyzer.CONFIG_VERSION = "1.0.0"
    analyzer.probe_technical.return_value = TechnicalAudioProperties(
        codec="flac",
        sample_rate=44100,
        channels=2,
        channel_layout="stereo",
        encoder="Lavf62.1.103",
    )
    analyzer.analyze_signal.return_value = SignalAnalysisResult(
        integrated_lufs=-14.2,
        loudness_range_lu=8.0,
        true_peak_dbtp=-0.5,
        sample_peak_dbfs=-0.8,
        peak_to_loudness_db=13.7,
        clipping_samples=0,
        is_clipped=False,
        duration_analyzed=200.0,
        silence_start_sec=0.1,
        silence_end_sec=0.2,
    )
    return analyzer


def test_analyze_file_success(session: Session, mock_analyzer: MagicMock, tmp_path: Path) -> None:
    track_repo = SqliteTrackRepository(session)
    track = Track(title="Heroes")
    track_repo.add(track)

    song_file = tmp_path / "heroes.flac"
    song_file.write_bytes(b"dummy flac content")

    tf = TrackFile(
        track_id=track.id,
        file_path=str(song_file),
        file_size=1000,
        format=AudioFormat.FLAC,
        file_hash="hash_heroes_1",
    )
    track_repo.add_file(tf)
    session.commit()

    service = AudioAnalysisService(
        session_factory=lambda: session,
        analyzer=mock_analyzer,
    )

    result = service.analyze_file(tf.id)
    assert result.status == AnalysisStatus.COMPLETED
    assert result.integrated_lufs == -14.2
    assert result.loudness_range_lu == 8.0
    assert result.true_peak_dbtp == -0.5
    assert result.peak_to_loudness_db == 13.7
    assert result.is_clipped is False
    assert mock_analyzer.analyze_signal.call_count == 1

    # Verify TrackFile was enriched with technical layout
    updated_tf = track_repo.get_file_by_id(tf.id)
    assert updated_tf is not None
    assert updated_tf.channel_layout == "stereo"
    assert updated_tf.encoder == "Lavf62.1.103"


def test_analyze_file_idempotency_and_freshness(session: Session, mock_analyzer: MagicMock, tmp_path: Path) -> None:
    track_repo = SqliteTrackRepository(session)
    track = Track(title="Starman")
    track_repo.add(track)

    song_file = tmp_path / "starman.flac"
    song_file.write_bytes(b"flac data")

    tf = TrackFile(
        track_id=track.id,
        file_path=str(song_file),
        file_size=1000,
        format=AudioFormat.FLAC,
        file_hash="hash_starman_1",
    )
    track_repo.add_file(tf)
    session.commit()

    service = AudioAnalysisService(
        session_factory=lambda: session,
        analyzer=mock_analyzer,
    )

    # First call: executes analysis
    first = service.analyze_file(tf.id)
    assert mock_analyzer.analyze_signal.call_count == 1

    # Second call without force: returns existing fresh analysis without re-analyzing
    second = service.analyze_file(tf.id)
    assert second.id == first.id
    assert mock_analyzer.analyze_signal.call_count == 1  # Not incremented!

    # Third call with force=True: re-analyzes
    third = service.analyze_file(tf.id, force=True)
    assert third.id == first.id
    assert mock_analyzer.analyze_signal.call_count == 2


def test_analyze_file_failure_handling(session: Session, mock_analyzer: MagicMock, tmp_path: Path) -> None:
    track_repo = SqliteTrackRepository(session)
    track = Track(title="Corrupt Song")
    track_repo.add(track)

    song_file = tmp_path / "corrupt.flac"
    song_file.write_bytes(b"bad content")

    tf = TrackFile(
        track_id=track.id,
        file_path=str(song_file),
        file_size=100,
        format=AudioFormat.FLAC,
        file_hash="hash_corrupt",
    )
    track_repo.add_file(tf)
    session.commit()

    mock_analyzer.analyze_signal.side_effect = RuntimeError("FFmpeg decoding error")

    service = AudioAnalysisService(
        session_factory=lambda: session,
        analyzer=mock_analyzer,
    )

    result = service.analyze_file(tf.id)
    assert result.status == AnalysisStatus.FAILED
    assert result.error_message is not None
    assert "FFmpeg decoding error" in result.error_message


def test_recover_stale_jobs(session: Session, mock_analyzer: MagicMock) -> None:
    track_repo = SqliteTrackRepository(session)
    analysis_repo = SqliteAudioAnalysisRepository(session)

    track = Track(title="Abandoned Track")
    track_repo.add(track)
    tf = TrackFile(
        track_id=track.id,
        file_path="/dummy/path.flac",
        file_size=500,
        format=AudioFormat.FLAC,
        file_hash="hash_stale",
    )
    track_repo.add_file(tf)

    stale_job = AudioAnalysis(
        track_file_id=tf.id,
        file_hash=tf.file_hash,
        status=AnalysisStatus.RUNNING,
        started_at=datetime.now(UTC) - timedelta(minutes=30),
    )
    analysis_repo.save(stale_job)
    session.commit()

    service = AudioAnalysisService(
        session_factory=lambda: session,
        analyzer=mock_analyzer,
    )

    recovered = service.recover_stale_jobs(timeout_seconds=600.0)
    assert recovered == 1

    updated = analysis_repo.get_by_id(stale_job.id)
    assert updated is not None
    assert updated.status == AnalysisStatus.PENDING
    assert "queued for retry" in (updated.error_message or "")


def test_worker_queue_execution(mock_analyzer: MagicMock, tmp_path: Path) -> None:
    from harmonia.core.database import create_database_engine, create_session_factory, init_database

    db_path = tmp_path / "worker_test.db"
    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    with session_factory() as session:
        track_repo = SqliteTrackRepository(session)
        track = Track(title="Async Song")
        track_repo.add(track)

        song_file = tmp_path / "async.flac"
        song_file.write_bytes(b"sound")

        tf = TrackFile(
            track_id=track.id,
            file_path=str(song_file),
            file_size=500,
            format=AudioFormat.FLAC,
            file_hash="hash_async",
        )
        track_repo.add_file(tf)
        session.commit()

    service = AudioAnalysisService(
        session_factory=session_factory,
        analyzer=mock_analyzer,
        max_workers=1,
    )

    enqueued = service.enqueue_file(tf.id)
    assert enqueued.status == AnalysisStatus.PENDING

    # Start worker, let it process the queued job
    service.start_worker()
    import time
    for _ in range(50):
        res = service.get_analysis_for_file(tf.id)
        if res and res.status == AnalysisStatus.COMPLETED:
            break
        time.sleep(0.05)

    service.stop_worker()
    engine.dispose()

    final_res = service.get_analysis_for_file(tf.id)
    assert final_res is not None
    assert final_res.status == AnalysisStatus.COMPLETED
    assert final_res.integrated_lufs == -14.2
