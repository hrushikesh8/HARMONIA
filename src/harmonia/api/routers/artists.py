"""Artist query endpoints."""

from __future__ import annotations

import math
from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query

from harmonia.api.dependencies import get_library_service, verify_api_token
from harmonia.api.schemas.album import AlbumResponse
from harmonia.api.schemas.artist import ArtistDetailResponse, ArtistResponse
from harmonia.api.schemas.common import PaginatedResponse

if TYPE_CHECKING:
    from harmonia.application.library_service import LibraryService

router = APIRouter(prefix="/artists", tags=["Artists"], dependencies=[Depends(verify_api_token)])


@router.get("", response_model=PaginatedResponse[ArtistResponse])
def list_artists(
    page: int = Query(default=1, ge=1, description="Page number (1-indexed)"),
    page_size: int = Query(default=50, ge=1, le=200, description="Items per page"),
    library_service: LibraryService = Depends(get_library_service),
) -> PaginatedResponse[ArtistResponse]:
    """Retrieve a paginated list of artists."""
    offset = (page - 1) * page_size
    items_dto = library_service.list_artists(limit=page_size, offset=offset)
    total = library_service.count_artists()
    total_pages = max(1, math.ceil(total / page_size))

    results = [
        ArtistResponse(
            id=a.id,
            name=a.name,
            sort_name=a.sort_name,
            album_count=a.album_count,
            track_count=a.track_count,
        )
        for a in items_dto
    ]

    return PaginatedResponse[ArtistResponse](
        items=results,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages,
    )


@router.get("/{artist_id}", response_model=ArtistDetailResponse)
def get_artist(
    artist_id: UUID,
    library_service: LibraryService = Depends(get_library_service),
) -> ArtistDetailResponse:
    """Retrieve artist detail and associated albums."""
    artist = library_service.get_artist(artist_id)
    if not artist:
        raise HTTPException(status_code=404, detail=f"Artist '{artist_id}' not found.")

    albums_response = [
        AlbumResponse(
            id=alb.id,
            title=alb.title,
            artist_name=alb.artist_name,
            year=alb.year,
            track_count=alb.track_count,
        )
        for alb in artist.albums
    ]

    return ArtistDetailResponse(
        id=artist.id,
        name=artist.name,
        sort_name=artist.sort_name,
        album_count=artist.album_count,
        track_count=artist.track_count,
        mbid=artist.mbid,
        albums=albums_response,
    )
