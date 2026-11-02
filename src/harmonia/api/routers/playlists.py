"""FastAPI router for user playlist management and item curation."""

from __future__ import annotations

import math
from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query, Response

from harmonia.api.dependencies import get_personalization_service, verify_api_token
from harmonia.api.schemas.common import PaginatedResponse
from harmonia.api.schemas.personalization import (
    PlaylistCreateRequest,
    PlaylistDetailResponse,
    PlaylistItemCreateRequest,
    PlaylistItemMoveRequest,
    PlaylistItemResponse,
    PlaylistSummaryResponse,
    PlaylistUpdateRequest,
)
from harmonia.application.personalization_dto import (
    PlaylistCreate,
    PlaylistItemCreate,
    PlaylistUpdate,
)

if TYPE_CHECKING:
    from harmonia.application.personalization_service import PersonalizationService

router = APIRouter(prefix="/playlists", tags=["Playlists"], dependencies=[Depends(verify_api_token)])


@router.post("", response_model=PlaylistDetailResponse, status_code=201)
def create_playlist(
    req: PlaylistCreateRequest,
    service: PersonalizationService = Depends(get_personalization_service),
) -> PlaylistDetailResponse:
    """Create a new user playlist."""
    dto = service.create_playlist(PlaylistCreate(name=req.name, description=req.description))
    return _to_detail_response(dto)


@router.get("", response_model=PaginatedResponse[PlaylistSummaryResponse])
def list_playlists(
    page: int = Query(default=1, ge=1, description="Page number (1-indexed)"),
    page_size: int = Query(default=50, ge=1, le=200, description="Items per page"),
    service: PersonalizationService = Depends(get_personalization_service),
) -> PaginatedResponse[PlaylistSummaryResponse]:
    """Retrieve a paginated list of playlists ordered by updated_at descending."""
    offset = (page - 1) * page_size
    items_dto = service.list_playlists(limit=page_size, offset=offset)
    total = service._playlist_repo.count()
    total_pages = max(1, math.ceil(total / page_size))

    results = [
        PlaylistSummaryResponse(
            id=p.id,
            name=p.name,
            description=p.description,
            created_at=p.created_at,
            updated_at=p.updated_at,
            track_count=p.track_count,
        )
        for p in items_dto
    ]

    return PaginatedResponse[PlaylistSummaryResponse](
        items=results,
        total=total,
        page=page,
        page_size=page_size,
        total_pages=total_pages,
    )


@router.get("/{playlist_id}", response_model=PlaylistDetailResponse)
def get_playlist(
    playlist_id: UUID,
    service: PersonalizationService = Depends(get_personalization_service),
) -> PlaylistDetailResponse:
    """Retrieve full playlist details including ordered items."""
    dto = service.get_playlist(playlist_id)
    if dto is None:
        raise HTTPException(status_code=404, detail=f"Playlist '{playlist_id}' not found.")
    return _to_detail_response(dto)


@router.put("/{playlist_id}", response_model=PlaylistDetailResponse)
def update_playlist(
    playlist_id: UUID,
    req: PlaylistUpdateRequest,
    service: PersonalizationService = Depends(get_personalization_service),
) -> PlaylistDetailResponse:
    """Update an existing playlist's name and/or description.

    Contract:
    - Omitted description: existing description is preserved.
    - Non-empty description string: description is updated.
    - Explicit null, empty string, or clear_description=True: description is cleared to None.
    """
    clear_desc = req.clear_description or (
        "description" in req.model_fields_set and req.description is None
    )
    dto = service.update_playlist(
        playlist_id,
        PlaylistUpdate(
            name=req.name,
            description=req.description if not clear_desc else None,
            clear_description=clear_desc,
        ),
    )
    return _to_detail_response(dto)


@router.delete("/{playlist_id}", status_code=204)
def delete_playlist(
    playlist_id: UUID,
    service: PersonalizationService = Depends(get_personalization_service),
) -> Response:
    """Delete a playlist and all associated item entries."""
    deleted = service.delete_playlist(playlist_id)
    if not deleted:
        raise HTTPException(status_code=404, detail=f"Playlist '{playlist_id}' not found.")
    return Response(status_code=204)


@router.post("/{playlist_id}/items", response_model=PlaylistItemResponse, status_code=201)
def add_playlist_item(
    playlist_id: UUID,
    req: PlaylistItemCreateRequest,
    service: PersonalizationService = Depends(get_personalization_service),
) -> PlaylistItemResponse:
    """Add a track item to a playlist at an optional position (appends by default)."""
    item = service.add_playlist_item(
        playlist_id,
        PlaylistItemCreate(track_id=req.track_id, position=req.position),
    )
    return PlaylistItemResponse(
        id=item.id,
        playlist_id=item.playlist_id,
        track_id=item.track_id,
        position=item.position,
        position_key=item.position_key,
        added_at=item.added_at,
        track_title=item.track_title,
        track_artist=item.track_artist,
        duration_seconds=item.duration_seconds,
    )


@router.post("/{playlist_id}/items/{item_id}/move", response_model=PlaylistDetailResponse)
def move_playlist_item(
    playlist_id: UUID,
    item_id: UUID,
    req: PlaylistItemMoveRequest,
    service: PersonalizationService = Depends(get_personalization_service),
) -> PlaylistDetailResponse:
    """Move an item to a new position, collision-safely adjusting intervening items."""
    service.move_playlist_item(playlist_id, item_id, req.new_position)
    detail = service.get_playlist(playlist_id)
    if detail is None:
        raise HTTPException(status_code=404, detail=f"Playlist '{playlist_id}' not found.")
    return _to_detail_response(detail)


@router.delete("/{playlist_id}/items/{item_id}", status_code=204)
def remove_playlist_item(
    playlist_id: UUID,
    item_id: UUID,
    service: PersonalizationService = Depends(get_personalization_service),
) -> Response:
    """Remove a specific item from a playlist."""
    removed = service.remove_playlist_item(playlist_id, item_id)
    if not removed:
        raise HTTPException(
            status_code=404,
            detail=f"Playlist item '{item_id}' not found in playlist '{playlist_id}'.",
        )
    return Response(status_code=204)


def _to_detail_response(dto) -> PlaylistDetailResponse:
    return PlaylistDetailResponse(
        id=dto.id,
        name=dto.name,
        description=dto.description,
        created_at=dto.created_at,
        updated_at=dto.updated_at,
        track_count=dto.track_count,
        total_duration_seconds=dto.total_duration_seconds,
        items=[
            PlaylistItemResponse(
                id=item.id,
                playlist_id=item.playlist_id,
                track_id=item.track_id,
                position=item.position,
                position_key=item.position_key,
                added_at=item.added_at,
                track_title=item.track_title,
                track_artist=item.track_artist,
                duration_seconds=item.duration_seconds,
            )
            for item in dto.items
        ],
    )
