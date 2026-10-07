"""Duplicate candidate review and resolution endpoints."""

from __future__ import annotations

from typing import TYPE_CHECKING
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Query

from harmonia.api.dependencies import get_session_factory, verify_api_token
from harmonia.api.schemas.duplicate import (
    DuplicateCandidateResponse,
    ResolveDuplicateRequest,
)
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)

if TYPE_CHECKING:
    from collections.abc import Callable

    from sqlalchemy.orm import Session

router = APIRouter(prefix="/duplicates", tags=["Duplicates"], dependencies=[Depends(verify_api_token)])


@router.get("", response_model=list[DuplicateCandidateResponse])
def list_duplicates(
    limit: int = Query(default=50, ge=1, le=200, description="Maximum candidates to return"),
    session_factory: Callable[[], Session] = Depends(get_session_factory),
) -> list[DuplicateCandidateResponse]:
    """Retrieve unresolved duplicate track candidates."""
    with session_factory() as session:
        repo = SqliteDuplicateCandidateRepository(session)
        candidates = repo.list_unresolved(limit=limit)

        return [
            DuplicateCandidateResponse(
                id=c.id,
                track_id_a=c.track_id_a,
                track_id_b=c.track_id_b,
                confidence=c.confidence,
                reason=c.reason,
                resolved=c.resolved,
                resolution=c.resolution,
                created_at=c.created_at,
            )
            for c in candidates
        ]


@router.post("/{candidate_id}/resolve", response_model=DuplicateCandidateResponse)
def resolve_duplicate(
    candidate_id: UUID,
    payload: ResolveDuplicateRequest,
    session_factory: Callable[[], Session] = Depends(get_session_factory),
) -> DuplicateCandidateResponse:
    """Mark a duplicate candidate as resolved with a specific resolution."""
    with session_factory() as session:
        repo = SqliteDuplicateCandidateRepository(session)
        candidate = repo.get_by_id(candidate_id)
        if not candidate:
            raise HTTPException(status_code=404, detail=f"Duplicate candidate '{candidate_id}' not found.")

        candidate.mark_resolved(payload.resolution)
        repo.update(candidate)
        session.commit()

        return DuplicateCandidateResponse(
            id=candidate.id,
            track_id_a=candidate.track_id_a,
            track_id_b=candidate.track_id_b,
            confidence=candidate.confidence,
            reason=candidate.reason,
            resolved=candidate.resolved,
            resolution=candidate.resolution,
            created_at=candidate.created_at,
        )
