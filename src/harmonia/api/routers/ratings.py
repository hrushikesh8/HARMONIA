"""FastAPI router for user track ratings."""

from __future__ import annotations

import math
from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Response

from harmonia.api.dependencies import get_personalization_service, verify_api_token
from harmonia.api.schemas.common import PaginatedResponse
from harmonia.api.schemas.personalization import RatingResponse, RatingSetRequest

if TYPE_CHECKING:
    from harmonia.application.personalization_service import PersonalizationService

router = APIRouter(prefix="/ratings", tags=["Ratings"], dependencies=[Depends(verify_api_token)])


@router.put("/{track_id}", response_model=RatingResponse)
def set_rating(
    track_id: UUID,
    req: RatingSetRequest,
    service: PersonalizationService = Depends(get_personalization_service),
) -> RatingResponse:
    """Set or update a track rating (1-5 stars)."""
    dto = service.set_rating(track_id, req.rating)
    return RatingResponse(
        track_id=dto.track_id,
        rating=dto.rating,
        client_updated_at=dto.client_updated_at,
        track_title=dto.track_title,
        track_artist=dto.track_artist,
        is_rated=True,
    )


@router.get("/{track_id}", response_model=RatingResponse)
def get_rating(
    track_id: UUID,
    service: PersonalizationService = Depends(get_personalization_service),
) -> RatingResponse:
    """Retrieve a track's rating evaluation, with clear representation if unrated."""
    track = service._track_repo.get_by_id(track_id)
    if track is None:
        raise HTTPException(status_code=404, detail=f"Track '{track_id}' not found.")

    dto = service.get_rating(track_id)
    if dto is None:
        track_artist = None
        if track.artist_id and service._artist_repo:
            artist = service._artist_repo.get_by_id(track.artist_id)
            if artist:
                track_artist = artist.name

        return RatingResponse(
            track_id=track_id,
            rating=None,
            client_updated_at=None,
            track_title=track.title,
            track_artist=track_artist,
            is_rated=False,
        )

    return RatingResponse(
        track_id=dto.track_id,
        rating=dto.rating,
        client_updated_at=dto.client_updated_at,
        track_title=dto.track_title,
        track_artist=dto.track_artist,
        is_rated=True,
    )


@router.delete("/{track_id}", status_code=204)
def remove_rating(
    track_id: UUID,
    service: PersonalizationService = Depends(get_personalization_service),
) -> Response:
    """Remove a track's rating evaluation."""
    track = service._track_repo.get_by_id(track_id)
    if track is None:
        raise HTTPException(status_code=404, detail=f"Track '{track_id}' not found.")
    service.remove_rating(track_id)
    return Response(status_code=204)


@router.get("", response_model=PaginatedResponse[RatingResponse])
def list_ratings(
    page: int = Query(default=1, ge=1, description="Page number (1-indexed)"),
    page_size: int = Query(default=50, ge=1, le=200, description="Items per page"),
    min_rating: int | None = Query(default=None, ge=1, le=5, description="Filter by minimum star rating (1-5)"),
    service: PersonalizationService = Depends(get_personalization_service),
) -> PaginatedResponse[RatingResponse]:
    """Retrieve a paginated list of rated tracks with optional min_rating filter."""
    offset = (page - 1) * page_size
    items_dto = service.list_ratings(limit=page_size, offset=offset, min_rating=min_rating)
    total = service._rating_repo.count(min_rating=min_rating)
    total_pages = max(1, math.ceil(total / page_size))

    results = [
        RatingResponse(
            track_id=r.track_id,
            rating=r.rating,
            client_updated_at=r.client_updated_at,
            track_title=r.track_title,
            track_artist=r.track_artist,
            is_rated=True,
        )
        for r in items_dto
    ]

    return PaginatedResponse[RatingResponse](
        items=results,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages,
    )
