"""AudioAnalysis domain entity and analysis status."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from enum import StrEnum
from uuid import UUID, uuid4


class AnalysisStatus(StrEnum):
    """Lifecycle status of audio analysis."""

    PENDING = "PENDING"
    RUNNING = "RUNNING"
    COMPLETED = "COMPLETED"
    FAILED = "FAILED"


@dataclass
class AudioAnalysis:
    """Objective derived signal measurements for a physical TrackFile.

    This entity stores factual, reproducible audio measurements produced
    by local signal analysis (FFmpeg ebur128, silence detection, etc.).
    It never encodes subjective quality judgments.
    """

    track_file_id: UUID
    file_hash: str
    id: UUID = field(default_factory=uuid4)
    status: AnalysisStatus = AnalysisStatus.PENDING

    # ── Loudness & Peaks (ITU-R BS.1770-4 / EBU R128) ───────────────────────
    integrated_lufs: float | None = None
    loudness_range_lu: float | None = None
    true_peak_dbtp: float | None = None
    sample_peak_dbfs: float | None = None

    # ── Dynamics & Crest Factor ─────────────────────────────────────────────
    peak_to_loudness_db: float | None = None  # Crest factor: TP - LUFS
    clipping_samples: int | None = None
    is_clipped: bool = False

    # ── Temporal Boundaries ─────────────────────────────────────────────────
    duration_analyzed: float | None = None
    silence_start_sec: float | None = None
    silence_end_sec: float | None = None

    # ── Reproducibility & Provenance ────────────────────────────────────────
    analyzer_name: str = "ffmpeg_ebur128"
    analyzer_version: str = "1.0.0"
    config_version: str = "1.0.0"
    error_message: str | None = None

    # ── Timestamps & Recovery ───────────────────────────────────────────────
    started_at: datetime | None = None
    completed_at: datetime | None = None
    execution_time_sec: float | None = None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    updated_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    def is_fresh(
        self,
        current_file_hash: str,
        current_analyzer_version: str = "1.0.0",
        current_config_version: str = "1.0.0",
    ) -> bool:
        """Check if this analysis result is fresh and valid for the given file."""
        return (
            self.status == AnalysisStatus.COMPLETED
            and self.file_hash == current_file_hash
            and self.analyzer_version == current_analyzer_version
            and self.config_version == current_config_version
        )
