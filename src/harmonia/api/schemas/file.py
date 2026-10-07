"""Audio analysis schemas for per-file and track-level endpoints."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel, Field


class AudioAnalysisResponse(BaseModel):
    """Objective derived signal measurements for a physical TrackFile."""

    id: UUID
    track_file_id: UUID
    status: str
    integrated_lufs: float | None = None
    loudness_range_lu: float | None = None
    true_peak_dbtp: float | None = None
    sample_peak_dbfs: float | None = None
    peak_to_loudness_db: float | None = None
    clipping_samples: int | None = None
    is_clipped: bool = False
    duration_analyzed: float | None = None
    silence_start_sec: float | None = None
    silence_end_sec: float | None = None
    analyzer_name: str
    analyzer_version: str
    config_version: str
    error_message: str | None = None
    started_at: datetime | None = None
    completed_at: datetime | None = None
    execution_time_sec: float | None = None


class TrackAnalysisMultiFileResponse(BaseModel):
    """Explicit collection of analysis results for all files belonging to a Track."""

    track_id: UUID
    analyses: list[AudioAnalysisResponse] = Field(default_factory=list)
