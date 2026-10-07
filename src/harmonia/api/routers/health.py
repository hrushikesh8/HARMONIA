"""Health and system information endpoints."""

from __future__ import annotations

from typing import TYPE_CHECKING

from fastapi import APIRouter, Depends

from harmonia import __version__
from harmonia.api.dependencies import get_library_service, verify_api_token
from harmonia.api.schemas.common import HealthResponse, LibraryInfoResponse

if TYPE_CHECKING:
    from harmonia.application.library_service import LibraryService

router = APIRouter(tags=["System"], dependencies=[Depends(verify_api_token)])


@router.get("/health", response_model=HealthResponse)
def get_health() -> HealthResponse:
    """Return system liveness status and version."""
    return HealthResponse(
        status="ok",
        version=__version__,
        database="connected",
    )


@router.get("/info", response_model=LibraryInfoResponse)
def get_library_info(
    library_service: LibraryService = Depends(get_library_service),
) -> LibraryInfoResponse:
    """Return high-level factual statistics for the library."""
    summary = library_service.get_summary()
    return LibraryInfoResponse(
        total_tracks=summary.total_tracks,
        total_albums=summary.total_albums,
        total_artists=summary.total_artists,
        total_files=summary.total_files,
    )
