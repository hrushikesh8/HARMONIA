"""Full-text search API endpoint."""

from __future__ import annotations

from typing import TYPE_CHECKING

from fastapi import APIRouter, Depends, Query

from harmonia.api.dependencies import get_search_service, verify_api_token
from harmonia.api.schemas.search import SearchItemResponse, SearchResponse

if TYPE_CHECKING:
    from harmonia.application.search_service import SearchService

router = APIRouter(prefix="/search", tags=["Search"], dependencies=[Depends(verify_api_token)])


@router.get("", response_model=SearchResponse)
def search_tracks(
    q: str = Query(..., min_length=1, description="Search query string"),
    limit: int = Query(default=50, ge=1, le=200, description="Maximum results to return"),
    search_service: SearchService = Depends(get_search_service),
) -> SearchResponse:
    """Perform fast local full-text search across track titles, artists, albums, and genres."""
    dto = search_service.search(query=q, limit=limit)
    items = [
        SearchItemResponse(
            track_id=item.track_id,
            title=item.title,
            artist_name=item.artist_name,
            album_title=item.album_title,
            duration_display=item.duration_display,
            genre=item.genre,
            year=item.year,
            format=item.format,
            quality_score=item.quality_score,
        )
        for item in dto.items
    ]
    return SearchResponse(
        query=dto.query,
        total_matches=dto.total_matches,
        items=items,
    )
