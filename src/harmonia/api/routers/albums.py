"""Album query endpoints."""

from __future__ import annotations

import math
from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query

from harmonia.api.dependencies import get_library_service, verify_api_token
from harmonia.api.schemas.album import AlbumDetailResponse, AlbumResponse
from harmonia.api.schemas.common import PaginatedResponse
from harmonia.api.schemas.track import TrackResponse

if TYPE_CHECKING:
    from harmonia.application.library_service import LibraryService

router = APIRouter(prefix="/albums", tags=["Albums"], dependencies=[Depends(verify_api_token)])


@router.get("", response_model=PaginatedResponse[AlbumResponse])
def list_albums(
    page: int = Query(default=1, ge=1, description="Page number (1-indexed)"),
    page_size: int = Query(default=50, ge=1, le=200, description="Items per page"),
    artist_id: UUID | None = Query(default=None, description="Filter by artist ID"),
    library_service: LibraryService = Depends(get_library_service),
) -> PaginatedResponse[AlbumResponse]:
    """Retrieve a paginated list of albums."""
    offset = (page - 1) * page_size
    items_dto = library_service.list_albums(limit=page_size, offset=offset, artist_id=artist_id)
    total = library_service.count_albums(artist_id=artist_id)
    total_pages = max(1, math.ceil(total / page_size))

    results = [
        AlbumResponse(
            id=a.id,
            title=a.title,
            artist_name=a.artist_name,
            year=a.year,
            track_count=a.track_count,
        )
        for a in items_dto
    ]

    return PaginatedResponse[AlbumResponse](
        items=results,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages,
    )


@router.get("/{album_id}", response_model=AlbumDetailResponse)
def get_album(
    album_id: UUID,
    library_service: LibraryService = Depends(get_library_service),
) -> AlbumDetailResponse:
    """Retrieve album detail and track listing."""
    album = library_service.get_album(album_id)
    if not album:
        raise HTTPException(status_code=404, detail=f"Album '{album_id}' not found.")

    tracks_response = [
        TrackResponse(
            id=t.id,
            title=t.title,
            artist_name=t.artist_name,
            album_title=t.album_title,
            duration_display=t.duration_display,
            genre=t.genre,
            year=t.year,
        )
        for t in album.tracks
    ]

    art_url = f"/api/v1/artwork/{album.art_path}" if album.art_path else None

    return AlbumDetailResponse(
        id=album.id,
        title=album.title,
        artist_name=album.artist_name,
        year=album.year,
        track_count=album.track_count,
        artist_id=album.artist_id,
        mbid=album.mbid,
        total_tracks=album.total_tracks,
        total_discs=album.total_discs,
        art_hash=album.art_path,
        art_url=art_url,
        tracks=tracks_response,
    )
