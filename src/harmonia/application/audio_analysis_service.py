"""Application service for audio analysis orchestration and worker execution."""

from __future__ import annotations

import logging
import os
import queue
import threading
import time
from collections.abc import Callable
from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING
from uuid import UUID

from harmonia.core.exceptions import AudioAnalysisError
from harmonia.domain.entities.audio_analysis import AnalysisStatus, AudioAnalysis
from harmonia.domain.ports.audio_analyzer import AudioAnalyzer
from harmonia.infrastructure.audio.ffmpeg_analyzer import FFmpegAudioAnalyzer
from harmonia.infrastructure.persistence.audio_analysis_repo import (
    SqliteAudioAnalysisRepository,
)
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

logger = logging.getLogger("harmonia.analysis")


class AudioAnalysisService:
    """Coordinates audio analysis, bounded worker threads, and persistence."""

    def __init__(
        self,
        session_factory: Callable[[], Session],
        analyzer: AudioAnalyzer | None = None,
        max_workers: int | None = None,
        queue_maxsize: int = 1000,
    ) -> None:
        self.session_factory = session_factory
        self.analyzer = analyzer or FFmpegAudioAnalyzer()

        cpu_count = os.cpu_count() or 4
        self.max_workers = max_workers or min(4, max(1, cpu_count - 1))
        self._queue: queue.Queue[tuple[UUID, bool]] = queue.Queue(maxsize=queue_maxsize)
        self._shutdown_event = threading.Event()
        self._worker_threads: list[threading.Thread] = []
        self._lock = threading.Lock()

    # ── Query API ─────────────────────────────────────────────────────────────

    def get_analysis_for_file(self, track_file_id: UUID) -> AudioAnalysis | None:
        """Get the current AudioAnalysis record for a physical TrackFile."""
        with self.session_factory() as session:
            repo = SqliteAudioAnalysisRepository(session)
            return repo.get_by_track_file_id(track_file_id)

    def get_analyses_for_track(self, track_id: UUID) -> list[AudioAnalysis]:
        """Get all AudioAnalysis records for all TrackFiles of a logical Track."""
        with self.session_factory() as session:
            repo = SqliteAudioAnalysisRepository(session)
            return repo.get_by_track_id(track_id)

    # ── Synchronous Execution ─────────────────────────────────────────────────

    def analyze_file(self, track_file_id: UUID, force: bool = False) -> AudioAnalysis:
        """Analyze a TrackFile synchronously and return the resulting AudioAnalysis."""
        # 1. Fetch TrackFile and existing analysis in a short transaction
        with self.session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            analysis_repo = SqliteAudioAnalysisRepository(session)

            tf = track_repo.get_file_by_id(track_file_id)
            if not tf:
                raise AudioAnalysisError(str(track_file_id), f"TrackFile '{track_file_id}' not found")

            file_hash = tf.file_hash or ""
            file_path = Path(tf.file_path)

            existing = analysis_repo.get_by_track_file_id(track_file_id)
            if (
                existing
                and not force
                and existing.is_fresh(
                    current_file_hash=file_hash,
                    current_analyzer_version=getattr(self.analyzer, "ANALYZER_VERSION", "1.0.0"),
                    current_config_version=getattr(self.analyzer, "CONFIG_VERSION", "1.0.0"),
                )
            ):
                return existing

            # Create or update to RUNNING
            if existing:
                analysis = existing
                analysis.status = AnalysisStatus.RUNNING
                analysis.file_hash = file_hash
                analysis.started_at = datetime.now(UTC)
                analysis.error_message = None
            else:
                analysis = AudioAnalysis(
                    track_file_id=track_file_id,
                    file_hash=file_hash,
                    status=AnalysisStatus.RUNNING,
                    started_at=datetime.now(UTC),
                )
            analysis_repo.save(analysis)
            session.commit()

        # 2. Execute analysis OUTSIDE database transaction
        start_time = time.monotonic()
        try:
            # Run signal analysis
            signal_res = self.analyzer.analyze_signal(file_path)

            # Optionally probe technical layout if TrackFile doesn't have it
            tech_props = None
            try:
                tech_props = self.analyzer.probe_technical(file_path)
            except Exception as e:
                logger.debug(f"Optional tech probe skipped for {file_path}: {e}")

            elapsed = round(time.monotonic() - start_time, 3)

            # 3. Persist success in short transaction
            with self.session_factory() as session:
                analysis_repo = SqliteAudioAnalysisRepository(session)
                track_repo = SqliteTrackRepository(session)

                # Update TrackFile technical properties if missing
                if tech_props:
                    curr_tf = track_repo.get_file_by_id(track_file_id)
                    if curr_tf:
                        if not curr_tf.channel_layout and tech_props.channel_layout:
                            curr_tf.channel_layout = tech_props.channel_layout
                        if not curr_tf.encoder and tech_props.encoder:
                            curr_tf.encoder = tech_props.encoder
                        track_repo.update_file(curr_tf)

                analysis.status = AnalysisStatus.COMPLETED
                analysis.integrated_lufs = signal_res.integrated_lufs
                analysis.loudness_range_lu = signal_res.loudness_range_lu
                analysis.true_peak_dbtp = signal_res.true_peak_dbtp
                analysis.sample_peak_dbfs = signal_res.sample_peak_dbfs
                analysis.peak_to_loudness_db = signal_res.peak_to_loudness_db
                analysis.clipping_samples = signal_res.clipping_samples
                analysis.is_clipped = signal_res.is_clipped
                analysis.duration_analyzed = signal_res.duration_analyzed
                analysis.silence_start_sec = signal_res.silence_start_sec
                analysis.silence_end_sec = signal_res.silence_end_sec
                analysis.analyzer_name = signal_res.analyzer_name
                analysis.analyzer_version = signal_res.analyzer_version
                analysis.config_version = signal_res.config_version
                analysis.completed_at = datetime.now(UTC)
                analysis.execution_time_sec = elapsed
                analysis.error_message = None

                saved = analysis_repo.save(analysis)
                session.commit()
                return saved

        except Exception as exc:
            elapsed = round(time.monotonic() - start_time, 3)
            logger.error(f"Analysis failed for file {track_file_id}: {exc}")

            with self.session_factory() as session:
                analysis_repo = SqliteAudioAnalysisRepository(session)
                analysis.status = AnalysisStatus.FAILED
                analysis.completed_at = datetime.now(UTC)
                analysis.execution_time_sec = elapsed
                analysis.error_message = str(exc)
                saved = analysis_repo.save(analysis)
                session.commit()
                return saved

    # ── Asynchronous Queue & Bounded Worker ───────────────────────────────────

    def enqueue_file(self, track_file_id: UUID, force: bool = False) -> AudioAnalysis:
        """Enqueue a TrackFile for asynchronous background analysis."""
        with self.session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            analysis_repo = SqliteAudioAnalysisRepository(session)

            tf = track_repo.get_file_by_id(track_file_id)
            if not tf:
                raise AudioAnalysisError(str(track_file_id), f"TrackFile '{track_file_id}' not found")

            existing = analysis_repo.get_by_track_file_id(track_file_id)
            if (
                existing
                and not force
                and existing.is_fresh(
                    current_file_hash=tf.file_hash or "",
                    current_analyzer_version=getattr(self.analyzer, "ANALYZER_VERSION", "1.0.0"),
                    current_config_version=getattr(self.analyzer, "CONFIG_VERSION", "1.0.0"),
                )
            ):
                return existing

            if existing:
                analysis = existing
                analysis.status = AnalysisStatus.PENDING
                analysis.error_message = None
            else:
                analysis = AudioAnalysis(
                    track_file_id=track_file_id,
                    file_hash=tf.file_hash or "",
                    status=AnalysisStatus.PENDING,
                )
            saved = analysis_repo.save(analysis)
            session.commit()

        try:
            self._queue.put_nowait((track_file_id, force))
        except queue.Full:
            logger.warning("Audio analysis queue is full; task marked PENDING for next worker sweep")

        return saved

    def start_worker(self) -> None:
        """Start background worker threads."""
        with self._lock:
            if self._worker_threads:
                return
            self._shutdown_event.clear()
            for i in range(self.max_workers):
                t = threading.Thread(
                    target=self._worker_loop,
                    name=f"harmonia-analysis-worker-{i}",
                    daemon=True,
                )
                t.start()
                self._worker_threads.append(t)
            logger.info(f"Started {self.max_workers} audio analysis worker threads")

    def stop_worker(self, timeout: float = 5.0) -> None:
        """Signal worker threads to stop and wait for termination."""
        with self._lock:
            if not self._worker_threads:
                return
            self._shutdown_event.set()
            for t in self._worker_threads:
                t.join(timeout=timeout)
            self._worker_threads.clear()
            logger.info("Stopped audio analysis worker threads")

    def drain(self) -> None:
        """Wait until all queued items are processed by workers."""
        self._queue.join()

    def _worker_loop(self) -> None:
        while not self._shutdown_event.is_set():
            try:
                item = self._queue.get(timeout=0.5)
            except queue.Empty:
                continue

            track_file_id, force = item
            try:
                self.analyze_file(track_file_id, force=force)
            except Exception as e:
                logger.error(f"Worker exception analyzing {track_file_id}: {e}")
            finally:
                self._queue.task_done()

    # ── Crash Recovery ────────────────────────────────────────────────────────

    def recover_stale_jobs(self, timeout_seconds: float = 600.0) -> int:
        """Find abandoned jobs in RUNNING status and reset them to PENDING."""
        with self.session_factory() as session:
            repo = SqliteAudioAnalysisRepository(session)
            stale_jobs = repo.get_stale_running(timeout_seconds=timeout_seconds)
            recovered_count = len(stale_jobs)

            for job in stale_jobs:
                logger.warning(
                    f"Recovering abandoned analysis job {job.id} for file {job.track_file_id} "
                    f"(started_at: {job.started_at})"
                )
                job.status = AnalysisStatus.PENDING
                job.error_message = "Recovered from interrupted or crashed analysis; queued for retry"
                repo.save(job)

            if recovered_count > 0:
                session.commit()

            return recovered_count
