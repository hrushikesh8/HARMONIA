"""SQLite implementation of AudioAnalysisRepository."""

from __future__ import annotations

from datetime import UTC, datetime, timedelta
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import select

from harmonia.domain.entities.audio_analysis import AnalysisStatus, AudioAnalysis
from harmonia.infrastructure.persistence.models import AudioAnalysisModel, TrackFileModel

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


class SqliteAudioAnalysisRepository:
    """SQLite implementation of AudioAnalysisRepository using SQLAlchemy."""

    def __init__(self, session: Session) -> None:
        self.session = session

    def save(self, analysis: AudioAnalysis) -> AudioAnalysis:
        """Insert or update an AudioAnalysis entity."""
        stmt = select(AudioAnalysisModel).where(AudioAnalysisModel.id == analysis.id)
        existing = self.session.scalar(stmt)

        if existing:
            existing.status = analysis.status.value
            existing.integrated_lufs = analysis.integrated_lufs
            existing.loudness_range_lu = analysis.loudness_range_lu
            existing.true_peak_dbtp = analysis.true_peak_dbtp
            existing.sample_peak_dbfs = analysis.sample_peak_dbfs
            existing.peak_to_loudness_db = analysis.peak_to_loudness_db
            existing.clipping_samples = analysis.clipping_samples
            existing.is_clipped = analysis.is_clipped
            existing.duration_analyzed = analysis.duration_analyzed
            existing.silence_start_sec = analysis.silence_start_sec
            existing.silence_end_sec = analysis.silence_end_sec
            existing.analyzer_name = analysis.analyzer_name
            existing.analyzer_version = analysis.analyzer_version
            existing.config_version = analysis.config_version
            existing.file_hash = analysis.file_hash
            existing.error_message = analysis.error_message
            existing.started_at = analysis.started_at
            existing.completed_at = analysis.completed_at
            existing.execution_time_sec = analysis.execution_time_sec
            existing.updated_at = datetime.now(UTC)
        else:
            model = AudioAnalysisModel(
                id=analysis.id,
                track_file_id=analysis.track_file_id,
                status=analysis.status.value,
                integrated_lufs=analysis.integrated_lufs,
                loudness_range_lu=analysis.loudness_range_lu,
                true_peak_dbtp=analysis.true_peak_dbtp,
                sample_peak_dbfs=analysis.sample_peak_dbfs,
                peak_to_loudness_db=analysis.peak_to_loudness_db,
                clipping_samples=analysis.clipping_samples,
                is_clipped=analysis.is_clipped,
                duration_analyzed=analysis.duration_analyzed,
                silence_start_sec=analysis.silence_start_sec,
                silence_end_sec=analysis.silence_end_sec,
                analyzer_name=analysis.analyzer_name,
                analyzer_version=analysis.analyzer_version,
                config_version=analysis.config_version,
                file_hash=analysis.file_hash,
                error_message=analysis.error_message,
                started_at=analysis.started_at,
                completed_at=analysis.completed_at,
                execution_time_sec=analysis.execution_time_sec,
                created_at=analysis.created_at,
                updated_at=analysis.updated_at,
            )
            self.session.add(model)

        self.session.flush()
        return analysis

    def get_by_id(self, analysis_id: UUID) -> AudioAnalysis | None:
        """Find an analysis record by its ID."""
        stmt = select(AudioAnalysisModel).where(AudioAnalysisModel.id == analysis_id)
        model = self.session.scalar(stmt)
        return self._to_entity(model) if model else None

    def get_by_track_file_id(self, track_file_id: UUID) -> AudioAnalysis | None:
        """Find an analysis record by track_file_id."""
        stmt = select(AudioAnalysisModel).where(AudioAnalysisModel.track_file_id == track_file_id)
        model = self.session.scalar(stmt)
        return self._to_entity(model) if model else None

    def get_by_track_id(self, track_id: UUID) -> list[AudioAnalysis]:
        """Find all analysis records for all files belonging to a track."""
        stmt = (
            select(AudioAnalysisModel)
            .join(TrackFileModel, AudioAnalysisModel.track_file_id == TrackFileModel.id)
            .where(TrackFileModel.track_id == track_id)
        )
        models = self.session.scalars(stmt).all()
        return [self._to_entity(m) for m in models]

    def get_stale_running(self, timeout_seconds: float = 600.0) -> list[AudioAnalysis]:
        """Find all jobs in RUNNING status whose started_at is older than timeout_seconds."""
        threshold = datetime.now(UTC) - timedelta(seconds=timeout_seconds)
        stmt = (
            select(AudioAnalysisModel)
            .where(
                AudioAnalysisModel.status == AnalysisStatus.RUNNING.value,
                (AudioAnalysisModel.started_at == None)  # noqa: E711
                | (AudioAnalysisModel.started_at <= threshold),
            )
        )
        models = self.session.scalars(stmt).all()
        return [self._to_entity(m) for m in models]

    def delete(self, analysis_id: UUID) -> bool:
        """Delete an analysis record by ID."""
        stmt = select(AudioAnalysisModel).where(AudioAnalysisModel.id == analysis_id)
        model = self.session.scalar(stmt)
        if not model:
            return False
        self.session.delete(model)
        self.session.flush()
        return True

    @staticmethod
    def _to_entity(model: AudioAnalysisModel) -> AudioAnalysis:
        # Normalize UUIDs if SQLite returned str
        m_id = UUID(str(model.id)) if isinstance(model.id, str) else model.id
        tf_id = (
            UUID(str(model.track_file_id))
            if isinstance(model.track_file_id, str)
            else model.track_file_id
        )

        return AudioAnalysis(
            id=m_id,
            track_file_id=tf_id,
            status=AnalysisStatus(model.status),
            integrated_lufs=model.integrated_lufs,
            loudness_range_lu=model.loudness_range_lu,
            true_peak_dbtp=model.true_peak_dbtp,
            sample_peak_dbfs=model.sample_peak_dbfs,
            peak_to_loudness_db=model.peak_to_loudness_db,
            clipping_samples=model.clipping_samples,
            is_clipped=model.is_clipped,
            duration_analyzed=model.duration_analyzed,
            silence_start_sec=model.silence_start_sec,
            silence_end_sec=model.silence_end_sec,
            analyzer_name=model.analyzer_name,
            analyzer_version=model.analyzer_version,
            config_version=model.config_version,
            file_hash=model.file_hash,
            error_message=model.error_message,
            started_at=model.started_at,
            completed_at=model.completed_at,
            execution_time_sec=model.execution_time_sec,
            created_at=model.created_at,
            updated_at=model.updated_at,
        )
