"""Per-file canonical audio analysis endpoints."""

from __future__ import annotations

from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query

from harmonia.api.dependencies import get_analysis_service, verify_api_token
from harmonia.api.schemas.file import AudioAnalysisResponse
from harmonia.core.exceptions import AudioAnalysisError

if TYPE_CHECKING:
    from harmonia.application.audio_analysis_service import AudioAnalysisService

router = APIRouter(prefix="/files", tags=["Files"], dependencies=[Depends(verify_api_token)])


@router.get("/{file_id}/analysis", response_model=AudioAnalysisResponse)
def get_file_analysis(
    file_id: UUID,
    analysis_service: AudioAnalysisService = Depends(get_analysis_service),
) -> AudioAnalysisResponse:
    """Canonical per-file audio analysis endpoint."""
    analysis = analysis_service.get_analysis_for_file(file_id)
    if not analysis:
        raise HTTPException(
            status_code=404,
            detail=f"No audio analysis found for file '{file_id}'. Trigger via POST /api/v1/files/{file_id}/analyze",
        )

    return AudioAnalysisResponse(
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
        error_message=analysis.error_message,
        started_at=analysis.started_at,
        completed_at=analysis.completed_at,
        execution_time_sec=analysis.execution_time_sec,
    )


@router.post("/{file_id}/analyze", response_model=AudioAnalysisResponse)
def trigger_file_analysis(
    file_id: UUID,
    force: bool = Query(default=False, description="Force re-analysis even if existing results are fresh"),
    sync: bool = Query(default=False, description="Run synchronously and wait for results instead of enqueuing"),
    analysis_service: AudioAnalysisService = Depends(get_analysis_service),
) -> AudioAnalysisResponse:
    """Trigger or queue audio analysis for a specific TrackFile."""
    try:
        if sync:
            analysis = analysis_service.analyze_file(file_id, force=force)
        else:
            analysis = analysis_service.enqueue_file(file_id, force=force)

        return AudioAnalysisResponse(
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
            error_message=analysis.error_message,
            started_at=analysis.started_at,
            completed_at=analysis.completed_at,
            execution_time_sec=analysis.execution_time_sec,
        )
    except AudioAnalysisError as err:
        if "not found" in str(err).lower():
            raise HTTPException(status_code=404, detail=f"TrackFile '{file_id}' not found.") from err
        raise HTTPException(status_code=400, detail=f"Failed to analyze file: {err}") from err
    except Exception as err:
        raise HTTPException(status_code=400, detail=f"Failed to analyze file: {err}") from err
