"""Integration tests for Phase 3 repositories."""

from datetime import UTC, datetime, timedelta

from sqlalchemy.orm import Session

from harmonia.domain.entities.audio_analysis import AnalysisStatus, AudioAnalysis
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.audio_analysis_repo import (
    SqliteAudioAnalysisRepository,
)
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


def test_audio_analysis_repository_crud(session: Session) -> None:
    track_repo = SqliteTrackRepository(session)
    analysis_repo = SqliteAudioAnalysisRepository(session)

    # 1. Create a track with a track file
    track = Track(title="Starman")
    track_repo.add(track)

    track_file = TrackFile(
        track_id=track.id,
        file_path="/music/starman.flac",
        file_size=25000000,
        format=AudioFormat.FLAC,
        file_hash="starman_hash_123",
        channel_layout="stereo",
        encoder="Lavf62.1.103",
    )
    track_repo.add_file(track_file)

    # 2. Create AudioAnalysis
    analysis = AudioAnalysis(
        track_file_id=track_file.id,
        file_hash=track_file.file_hash,
        status=AnalysisStatus.COMPLETED,
        integrated_lufs=-14.2,
        loudness_range_lu=7.8,
        true_peak_dbtp=-0.3,
        sample_peak_dbfs=-0.5,
        peak_to_loudness_db=13.9,
        clipping_samples=0,
        is_clipped=False,
        duration_analyzed=256.4,
        silence_start_sec=0.12,
        silence_end_sec=0.45,
    )

    saved = analysis_repo.save(analysis)
    assert saved.id == analysis.id

    # 3. Retrieve by ID
    fetched_by_id = analysis_repo.get_by_id(analysis.id)
    assert fetched_by_id is not None
    assert fetched_by_id.integrated_lufs == -14.2
    assert fetched_by_id.loudness_range_lu == 7.8
    assert fetched_by_id.status == AnalysisStatus.COMPLETED

    # 4. Retrieve by track_file_id
    fetched_by_file = analysis_repo.get_by_track_file_id(track_file.id)
    assert fetched_by_file is not None
    assert fetched_by_file.id == analysis.id

    # 5. Retrieve by track_id
    by_track = analysis_repo.get_by_track_id(track.id)
    assert len(by_track) == 1
    assert by_track[0].id == analysis.id

    # 6. Update existing record
    analysis.status = AnalysisStatus.COMPLETED
    analysis.integrated_lufs = -13.8
    analysis_repo.save(analysis)
    updated = analysis_repo.get_by_id(analysis.id)
    assert updated is not None
    assert updated.integrated_lufs == -13.8

    # 7. Delete
    deleted = analysis_repo.delete(analysis.id)
    assert deleted is True
    assert analysis_repo.get_by_id(analysis.id) is None


def test_audio_analysis_repository_stale_running(session: Session) -> None:
    track_repo = SqliteTrackRepository(session)
    analysis_repo = SqliteAudioAnalysisRepository(session)

    track = Track(title="Life on Mars")
    track_repo.add(track)

    file_1 = TrackFile(
        track_id=track.id,
        file_path="/music/life_on_mars.flac",
        file_size=20000000,
        format=AudioFormat.FLAC,
        file_hash="hash_mars_1",
    )
    file_2 = TrackFile(
        track_id=track.id,
        file_path="/music/life_on_mars_alt.flac",
        file_size=21000000,
        format=AudioFormat.FLAC,
        file_hash="hash_mars_2",
    )
    track_repo.add_file(file_1)
    track_repo.add_file(file_2)

    # Job 1: Running recently (within 5 minutes)
    job_recent = AudioAnalysis(
        track_file_id=file_1.id,
        file_hash=file_1.file_hash,
        status=AnalysisStatus.RUNNING,
        started_at=datetime.now(UTC) - timedelta(minutes=2),
    )
    # Job 2: Running a long time ago (e.g. 20 minutes ago)
    job_stale = AudioAnalysis(
        track_file_id=file_2.id,
        file_hash=file_2.file_hash,
        status=AnalysisStatus.RUNNING,
        started_at=datetime.now(UTC) - timedelta(minutes=20),
    )

    analysis_repo.save(job_recent)
    analysis_repo.save(job_stale)

    # Query stale running with 10 min threshold (600s)
    stale_jobs = analysis_repo.get_stale_running(timeout_seconds=600.0)
    assert len(stale_jobs) == 1
    assert stale_jobs[0].id == job_stale.id
