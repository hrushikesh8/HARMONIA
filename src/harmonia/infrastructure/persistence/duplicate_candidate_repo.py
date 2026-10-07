"""SQLite repository implementation for duplicate candidates."""

from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import func, select

from harmonia.domain.entities.duplicate_candidate import DuplicateCandidate
from harmonia.infrastructure.persistence.models import DuplicateCandidateModel

if TYPE_CHECKING:
    from uuid import UUID

    from sqlalchemy.orm import Session


class SqliteDuplicateCandidateRepository:
    """SQLite implementation of the DuplicateCandidateRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, candidate: DuplicateCandidate) -> None:
        """Store a new duplicate candidate."""
        model = _candidate_to_model(candidate)
        self._session.add(model)
        self._session.flush()

    def get_by_id(self, candidate_id: UUID) -> DuplicateCandidate | None:
        """Retrieve a duplicate candidate by ID."""
        model = self._session.get(DuplicateCandidateModel, candidate_id)
        return _model_to_candidate(model) if model else None

    def list_unresolved(
        self, limit: int = 50, offset: int = 0
    ) -> list[DuplicateCandidate]:
        """List unresolved duplicate candidates."""
        stmt = (
            select(DuplicateCandidateModel)
            .where(DuplicateCandidateModel.resolved == False)  # noqa: E712
            .order_by(DuplicateCandidateModel.created_at.desc())
            .limit(limit)
            .offset(offset)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_candidate(m) for m in models]

    def count_unresolved(self) -> int:
        """Count unresolved duplicate candidates."""
        stmt = select(func.count(DuplicateCandidateModel.id)).where(
            DuplicateCandidateModel.resolved == False  # noqa: E712
        )
        return self._session.scalar(stmt) or 0

    def update(self, candidate: DuplicateCandidate) -> None:
        """Update resolution state of a candidate."""
        model = self._session.get(DuplicateCandidateModel, candidate.id)
        if model is not None:
            model.resolved = candidate.resolved
            model.resolution = candidate.resolution
            model.resolved_at = candidate.resolved_at
            self._session.flush()


def _candidate_to_model(candidate: DuplicateCandidate) -> DuplicateCandidateModel:
    return DuplicateCandidateModel(
        id=candidate.id,
        track_id_a=candidate.track_id_a,
        track_id_b=candidate.track_id_b,
        confidence=candidate.confidence,
        reason=candidate.reason,
        resolved=candidate.resolved,
        resolution=candidate.resolution,
        created_at=candidate.created_at,
        resolved_at=candidate.resolved_at,
    )


def _model_to_candidate(model: DuplicateCandidateModel) -> DuplicateCandidate:
    return DuplicateCandidate(
        id=model.id,
        track_id_a=model.track_id_a,
        track_id_b=model.track_id_b,
        confidence=model.confidence,
        reason=model.reason,
        resolved=model.resolved,
        resolution=model.resolution,
        created_at=model.created_at,
        resolved_at=model.resolved_at,
    )
