"""FastAPI router for user track favorites."""

from __future__ import annotations

import math
from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Response

from harmonia.api.dependencies import get_personalization_service, verify_api_token
from harmonia.api.schemas.common import PaginatedResponse
from harmonia.api.schemas.personalization import FavoriteResponse

if TYPE_CHECKING:
    from harmonia.application.personalization_service import PersonalizationService

router = APIRouter(prefix="/favorites", tags=["Favorites"], dependencies=[Depends(verify_api_token)])


@router.post("/{track_id}", response_model=FavoriteResponse, status_code=201)
def add_favorite(
    track_id: UUID,
    service: PersonalizationService = Depends(get_personalization_service),
) -> FavoriteResponse:
    """Mark a track as a user favorite (idempotent)."""
    fav = service.add_favorite(track_id)
    return FavoriteResponse(
        track_id=fav.track_id,
        client_updated_at=fav.client_updated_at,
        track_title=fav.track_title,
        track_artist=fav.track_artist,
    )


@router.delete("/{track_id}", status_code=204)
def remove_favorite(
    track_id: UUID,
    service: PersonalizationService = Depends(get_personalization_service),
) -> Response:
    """Remove a track favorite."""
    track = service._track_repo.get_by_id(track_id)
    if track is None:
        raise HTTPException(status_code=404, detail=f"Track '{track_id}' not found.")
    service.remove_favorite(track_id)
    return Response(status_code=204)


@router.get("", response_model=PaginatedResponse[FavoriteResponse])
def list_favorites(
    page: int = Query(default=1, ge=1, description="Page number (1-indexed)"),
    page_size: int = Query(default=50, ge=1, le=200, description="Items per page"),
    service: PersonalizationService = Depends(get_personalization_service),
) -> PaginatedResponse[FavoriteResponse]:
    """Retrieve a paginated list of favorites ordered by client_updated_at descending."""
    offset = (page - 1) * page_size
    items_dto = service.list_favorites(limit=page_size, offset=offset)
    total = service._favorite_repo.count()
    total_pages = max(1, math.ceil(total / page_size))

    results = [
        FavoriteResponse(
            track_id=f.track_id,
            client_updated_at=f.client_updated_at,
            track_title=f.track_title,
            track_artist=f.track_artist,
        )
        for f in items_dto
    ]

    return PaginatedResponse[FavoriteResponse](
        items=results,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages,
    )
