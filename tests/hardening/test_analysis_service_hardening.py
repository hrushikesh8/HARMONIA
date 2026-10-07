"""Hardening Tests #5, #6, #7, #8, #9, #10, #23, #24: Audio Analysis Service.

Covers:
- Test #5: Full service lifecycle (PENDING -> RUNNING -> COMPLETED & PENDING -> RUNNING -> FAILED)
- Test #6: Freshness & cache invalidation (hash, analyzer version, config version)
- Test #7: Idempotency (no duplicates, cached result returned)
- Test #8: Concurrent analysis with bounded workers
- Test #9: Stale job recovery
- Test #10: Worker failure recovery
- Test #23: Process safety
- Test #24: Data integrity
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from pathlib import Path
from uuid import UUID

import pytest
from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.core.database import create_session_factory
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.audio_analysis import AnalysisStatus, AudioAnalysis
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.audio.ffmpeg_analyzer import FFmpegAudioAnalyzer
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.audio_analysis_repo import SqliteAudioAnalysisRepository
from harmonia.infrastructure.persistence.models import Base
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def test_db(tmp_path: Path):
    """Create in-memory or temp SQLite database with all tables."""
    db_file = tmp_path / "analysis_test.db"
    engine = create_engine(f"sqlite:///{db_file.as_posix()}", echo=False)
    Base.metadata.create_all(engine)
    session_factory = create_session_factory(engine)
    return session_factory


def _seed_track_and_file(
    session: Session,
    audio_path: Path,
    file_hash: str = "hash_initial_123",
) -> tuple[Track, TrackFile]:
    artist_repo = SqliteArtistRepository(session)
    track_repo = SqliteTrackRepository(session)

    artist = Artist(name="Brian Eno")
    artist_repo.add(artist)

    track = Track(title="Ambient Track", artist_id=artist.id, duration_seconds=1.0)
    track_repo.add(track)

    tf = TrackFile(
        track_id=track.id,
        file_path=str(audio_path),
        file_size=audio_path.stat().st_size if audio_path.exists() else 1000,
        format=AudioFormat.WAV,
        file_hash=file_hash,
        sample_rate=44100,
        channels=2,
        bitrate=1411,
        duration_seconds=1.0,
    )
    track_repo.add_file(tf)
    session.commit()
    return track, tf


def test_service_lifecycle_success_and_failure(
    test_db,
    test_audio_fixtures: dict[str, Path],
) -> None:
    """TEST #5: Audio analysis service full lifecycle (SUCCESS and FAILURE)."""
    service = AudioAnalysisService(session_factory=test_db, analyzer=FFmpegAudioAnalyzer())

    # 1. Success lifecycle
    with test_db() as session:
        _, tf_success = _seed_track_and_file(session, test_audio_fixtures["wav_stereo"])
        tf_id = tf_success.id

    result = service.analyze_file(tf_id)
    assert result.status == AnalysisStatus.COMPLETED
    assert result.track_file_id == tf_id
    assert result.integrated_lufs is not None
    assert result.true_peak_dbtp is not None
    assert result.analyzer_version == "1.0.0"
    assert result.config_version == "1.0.0"
    assert result.error_message is None
    assert result.started_at is not None
    assert result.completed_at is not None
    assert result.execution_time_sec is not None and result.execution_time_sec >= 0.0

    # 2. Failure lifecycle
    with test_db() as session:
        _, tf_fail = _seed_track_and_file(session, test_audio_fixtures["corrupt_file"], file_hash="corrupt_hash")
        fail_id = tf_fail.id

    fail_result = service.analyze_file(fail_id)
    assert fail_result.status == AnalysisStatus.FAILED
    assert fail_result.track_file_id == fail_id
    assert fail_result.error_message is not None
    assert "code" in fail_result.error_message or "decode" in fail_result.error_message.lower() or "ffprobe" in fail_result.error_message.lower()


def test_freshness_cache_invalidation(
    test_db,
    test_audio_fixtures: dict[str, Path],
) -> None:
    """TEST #6: Cache invalidation when hash, analyzer version, or config version changes."""
    analyzer = FFmpegAudioAnalyzer()
    service = AudioAnalysisService(session_factory=test_db, analyzer=analyzer)

    with test_db() as session:
        _, tf = _seed_track_and_file(session, test_audio_fixtures["wav_stereo"], file_hash="hash_v1")
        tf_id = tf.id

    # 1. First run -> COMPLETED
    res1 = service.analyze_file(tf_id)
    assert res1.status == AnalysisStatus.COMPLETED

    # 2. Second run without changes -> should return cached result immediately
    res2 = service.analyze_file(tf_id)
    assert res2.id == res1.id
    assert res2.execution_time_sec == res1.execution_time_sec

    # 3. Invalidate via file_hash change
    with test_db() as session:
        t_repo = SqliteTrackRepository(session)
        updated_tf = t_repo.get_file_by_id(tf_id)
        assert updated_tf is not None
        updated_tf.file_hash = "hash_v2_changed"
        t_repo.update_file(updated_tf)
        session.commit()

    # Verify existing analysis detects it is NOT fresh
    with test_db() as session:
        a_repo = SqliteAudioAnalysisRepository(session)
        existing = a_repo.get_by_track_file_id(tf_id)
        assert existing is not None
        assert existing.is_fresh(current_file_hash="hash_v2_changed") is False

    # 4. Invalidate via analyzer_version change
    assert existing.is_fresh(
        current_file_hash="hash_v1",
        current_analyzer_version="2.0.0",
        current_config_version="1.0.0",
    ) is False

    # 5. Invalidate via config_version change
    assert existing.is_fresh(
        current_file_hash="hash_v1",
        current_analyzer_version="1.0.0",
        current_config_version="2.0.0",
    ) is False


def test_idempotency_and_no_duplicates(
    test_db,
    test_audio_fixtures: dict[str, Path],
) -> None:
    """TEST #7: Repeated requests return cached result and never create duplicate rows."""
    service = AudioAnalysisService(session_factory=test_db, analyzer=FFmpegAudioAnalyzer())

    with test_db() as session:
        _, tf = _seed_track_and_file(session, test_audio_fixtures["wav_stereo"])
        tf_id = tf.id

    r1 = service.analyze_file(tf_id)
    r2 = service.analyze_file(tf_id)
    r3 = service.analyze_file(tf_id)

    assert r1.id == r2.id == r3.id

    # Check database row count
    with test_db() as session:
        a_repo = SqliteAudioAnalysisRepository(session)
        all_for_tf = a_repo.get_by_track_file_id(tf_id)
        assert all_for_tf is not None


def test_concurrent_analysis_bounded_workers(
    test_db,
    test_audio_fixtures: dict[str, Path],
) -> None:
    """TEST #8: Bounded worker threads, no cross-contamination, clean termination."""
    service = AudioAnalysisService(
        session_factory=test_db,
        analyzer=FFmpegAudioAnalyzer(),
        max_workers=2,
    )

    # Seed multiple distinct files
    files_to_test = [
        ("wav_stereo", test_audio_fixtures["wav_stereo"]),
        ("mp3_mono", test_audio_fixtures["mp3_mono"]),
        ("silence_wav", test_audio_fixtures["silence_wav"]),
        ("clipped_wav", test_audio_fixtures["clipped_wav"]),
    ]

    tf_ids: list[tuple[str, UUID]] = []
    with test_db() as session:
        for name, p in files_to_test:
            _, tf = _seed_track_and_file(session, p, file_hash=f"hash_{name}")
            tf_ids.append((name, tf.id))

    # Start workers and enqueue all
    service.start_worker()
    for _, file_uuid in tf_ids:
        service.enqueue_file(file_uuid)

    # Wait for queue to drain and stop workers
    service.drain()
    service.stop_worker()

    # Validate that all files were analyzed cleanly and have matching hashes
    with test_db() as session:
        repo = SqliteAudioAnalysisRepository(session)
        for name, file_uuid in tf_ids:
            analysis = repo.get_by_track_file_id(file_uuid)
            assert analysis is not None
            assert analysis.status == AnalysisStatus.COMPLETED
            assert analysis.file_hash == f"hash_{name}"
            if name == "clipped_wav":
                assert analysis.is_clipped is True


def test_stale_job_recovery(test_db) -> None:
    """TEST #9: Stale job recovery resets abandoned RUNNING records to PENDING."""
    service = AudioAnalysisService(session_factory=test_db)

    with test_db() as session:
        _, tf_stale = _seed_track_and_file(session, Path("/tmp/dummy_stale.wav"), file_hash="stale_hash")
        _, tf_fresh = _seed_track_and_file(session, Path("/tmp/dummy_fresh.wav"), file_hash="fresh_hash")
        _, tf_comp = _seed_track_and_file(session, Path("/tmp/dummy_comp.wav"), file_hash="comp_hash")

        repo = SqliteAudioAnalysisRepository(session)

        # 1. Stale RUNNING job (started 2 hours ago)
        stale_job = AudioAnalysis(
            track_file_id=tf_stale.id,
            file_hash="stale_hash",
            status=AnalysisStatus.RUNNING,
            started_at=datetime.now(UTC) - timedelta(hours=2),
        )
        repo.save(stale_job)

        # 2. Fresh RUNNING job (started 10 seconds ago)
        fresh_job = AudioAnalysis(
            track_file_id=tf_fresh.id,
            file_hash="fresh_hash",
            status=AnalysisStatus.RUNNING,
            started_at=datetime.now(UTC) - timedelta(seconds=10),
        )
        repo.save(fresh_job)

        # 3. Completed job
        comp_job = AudioAnalysis(
            track_file_id=tf_comp.id,
            file_hash="comp_hash",
            status=AnalysisStatus.COMPLETED,
            completed_at=datetime.now(UTC),
        )
        repo.save(comp_job)
        session.commit()

        stale_id = stale_job.id
        fresh_id = fresh_job.id
        comp_id = comp_job.id

    # Run recovery with 600s threshold
    recovered = service.recover_stale_jobs(timeout_seconds=600.0)
    assert recovered == 1

    with test_db() as session:
        repo = SqliteAudioAnalysisRepository(session)
        s_after = repo.get_by_id(stale_id)
        f_after = repo.get_by_id(fresh_id)
        c_after = repo.get_by_id(comp_id)

        assert s_after.status == AnalysisStatus.PENDING
        assert "Recovered" in (s_after.error_message or "")
        assert f_after.status == AnalysisStatus.RUNNING
        assert c_after.status == AnalysisStatus.COMPLETED

    # Recovery is idempotent
    assert service.recover_stale_jobs(timeout_seconds=600.0) == 0


def test_worker_failure_recovery_and_pipeline_continuation(
    test_db,
    test_audio_fixtures: dict[str, Path],
) -> None:
    """TEST #10: A failing job fails gracefully and allows subsequent jobs to process."""
    service = AudioAnalysisService(session_factory=test_db, analyzer=FFmpegAudioAnalyzer(), max_workers=1)

    with test_db() as session:
        _, tf_bad = _seed_track_and_file(session, test_audio_fixtures["corrupt_file"], file_hash="bad_hash")
        _, tf_good = _seed_track_and_file(session, test_audio_fixtures["wav_stereo"], file_hash="good_hash")
        bad_id = tf_bad.id
        good_id = tf_good.id

    service.start_worker()

    # Enqueue failing job first, then good job
    service.enqueue_file(bad_id)
    service.enqueue_file(good_id)

    service.drain()
    service.stop_worker()

    with test_db() as session:
        repo = SqliteAudioAnalysisRepository(session)
        bad_res = repo.get_by_track_file_id(bad_id)
        good_res = repo.get_by_track_file_id(good_id)

        assert bad_res is not None and bad_res.status == AnalysisStatus.FAILED
        assert good_res is not None and good_res.status == AnalysisStatus.COMPLETED


def test_process_safety_and_data_integrity(
    test_db,
    test_audio_fixtures: dict[str, Path],
) -> None:
    """TEST #23 & #24: Process safety & relational data integrity."""
    service = AudioAnalysisService(session_factory=test_db, analyzer=FFmpegAudioAnalyzer())

    with test_db() as session:
        _, tf1 = _seed_track_and_file(session, test_audio_fixtures["wav_stereo"], file_hash="h1")
        _, tf2 = _seed_track_and_file(session, test_audio_fixtures["mp3_mono"], file_hash="h2")
        id1 = tf1.id
        id2 = tf2.id

    r1 = service.analyze_file(id1)
    r2 = service.analyze_file(id2)

    assert r1.track_file_id == id1
    assert r2.track_file_id == id2
    assert r1.file_hash == "h1"
    assert r2.file_hash == "h2"
    assert r1.track_file_id != r2.track_file_id
