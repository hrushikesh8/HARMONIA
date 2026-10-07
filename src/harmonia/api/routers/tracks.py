"""Track query and track-level analysis endpoints."""

from __future__ import annotations

import math
from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query

from harmonia.api.dependencies import (
    get_analysis_service,
    get_library_service,
    verify_api_token,
)
from harmonia.api.schemas.common import PaginatedResponse
from harmonia.api.schemas.file import (
    AudioAnalysisResponse,
    TrackAnalysisMultiFileResponse,
)
from harmonia.api.schemas.track import (
    TagResponse,
    TrackDetailResponse,
    TrackFileResponse,
    TrackResponse,
)

if TYPE_CHECKING:
    from harmonia.application.audio_analysis_service import AudioAnalysisService
    from harmonia.application.library_service import LibraryService

router = APIRouter(prefix="/tracks", tags=["Tracks"], dependencies=[Depends(verify_api_token)])


@router.get("", response_model=PaginatedResponse[TrackResponse])
def list_tracks(
    page: int = Query(default=1, ge=1, description="Page number (1-indexed)"),
    page_size: int = Query(default=50, ge=1, le=200, description="Items per page"),
    artist_id: UUID | None = Query(default=None, description="Filter by artist ID"),
    album_id: UUID | None = Query(default=None, description="Filter by album ID"),
    genre: str | None = Query(default=None, description="Filter by genre"),
    library_service: LibraryService = Depends(get_library_service),
) -> PaginatedResponse[TrackResponse]:
    """Retrieve a paginated list of tracks with optional filters."""
    offset = (page - 1) * page_size
    items_dto = library_service.list_tracks(
        limit=page_size,
        offset=offset,
        artist_id=artist_id,
        album_id=album_id,
        genre=genre,
    )

    total = library_service.count_tracks(artist_id=artist_id, album_id=album_id, genre=genre)
    total_pages = max(1, math.ceil(total / page_size))

    results = [
        TrackResponse(
            id=t.id,
            title=t.title,
            artist_name=t.artist_name,
            album_title=t.album_title,
            duration_display=t.duration_display,
            genre=t.genre,
            year=t.year,
        )
        for t in items_dto
    ]

    return PaginatedResponse[TrackResponse](
        items=results,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages,
    )


@router.get("/{track_id}", response_model=TrackDetailResponse)
def get_track(
    track_id: UUID,
    library_service: LibraryService = Depends(get_library_service),
) -> TrackDetailResponse:
    """Retrieve full detail for a track. Filesystem paths are strictly redacted."""
    t = library_service.get_track(track_id)
    if not t:
        raise HTTPException(status_code=404, detail=f"Track '{track_id}' not found.")

    file_responses: list[TrackFileResponse] = []
    for f in t.files:
        file_responses.append(
            TrackFileResponse(
                id=f.id,
                format=f.format,
                codec=f.codec,
                bitrate=f.bitrate,
                sample_rate=f.sample_rate,
                bit_depth=f.bit_depth,
                channels=f.channels,
                channel_layout=getattr(f, "channel_layout", None),
                encoder=getattr(f, "encoder", None),
                duration_seconds=getattr(f, "duration_seconds", None),
                is_lossless=f.is_lossless,
                quality_score=f.quality_score,
                file_size_bytes=getattr(f, "file_size_bytes", 0),
                file_size_display=f.file_size_display,
                stream_url=f"/api/v1/stream/{f.id}",
            )
        )

    tag_responses = [
        TagResponse(name=tag.name, value=tag.value, source=tag.source)
        for tag in t.tags
    ]

    return TrackDetailResponse(
        id=t.id,
        title=t.title,
        artist_name=t.artist_name,
        album_title=t.album_title,
        duration_display=t.duration_display,
        genre=t.genre,
        year=t.year,
        artist_id=t.artist_id,
        album_id=t.album_id,
        track_number=t.track_number,
        disc_number=t.disc_number,
        duration_seconds=t.duration_seconds,
        mbid=t.mbid,
        created_at=t.created_at,
        files=file_responses,
        tags=tag_responses,
    )


@router.get("/{track_id}/analysis", response_model=TrackAnalysisMultiFileResponse)
def get_track_analysis(
    track_id: UUID,
    library_service: LibraryService = Depends(get_library_service),
    analysis_service: AudioAnalysisService = Depends(get_analysis_service),
) -> TrackAnalysisMultiFileResponse:
    """Retrieve objective audio analysis for all files associated with a track."""
    t = library_service.get_track(track_id)
    if not t:
        raise HTTPException(status_code=404, detail=f"Track '{track_id}' not found.")

    analyses_domain = analysis_service.get_analyses_for_track(track_id)
    responses = [
        AudioAnalysisResponse(
            id=a.id,
            track_file_id=a.track_file_id,
            status=a.status.value,
            integrated_lufs=a.integrated_lufs,
            loudness_range_lu=a.loudness_range_lu,
            true_peak_dbtp=a.true_peak_dbtp,
            sample_peak_dbfs=a.sample_peak_dbfs,
            peak_to_loudness_db=a.peak_to_loudness_db,
            clipping_samples=a.clipping_samples,
            is_clipped=a.is_clipped,
            duration_analyzed=a.duration_analyzed,
            silence_start_sec=a.silence_start_sec,
            silence_end_sec=a.silence_end_sec,
            analyzer_name=a.analyzer_name,
            analyzer_version=a.analyzer_version,
            config_version=a.config_version,
            error_message=a.error_message,
            started_at=a.started_at,
            completed_at=a.completed_at,
            execution_time_sec=a.execution_time_sec,
        )
        for a in analyses_domain
    ]

    return TrackAnalysisMultiFileResponse(
        track_id=track_id,
        analyses=responses,
    )
