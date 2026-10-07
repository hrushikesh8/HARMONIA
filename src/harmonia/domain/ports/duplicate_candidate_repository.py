"""Port definition for DuplicateCandidateRepository."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.duplicate_candidate import DuplicateCandidate


class DuplicateCandidateRepository(Protocol):
    """Repository port for DuplicateCandidate records."""

    def add(self, candidate: DuplicateCandidate) -> None:
        """Store a new duplicate candidate."""
        ...

    def get_by_id(self, candidate_id: UUID) -> DuplicateCandidate | None:
        """Retrieve a duplicate candidate by ID."""
        ...

    def list_unresolved(
        self, limit: int = 50, offset: int = 0
    ) -> list[DuplicateCandidate]:
        """List unresolved duplicate candidates for review."""
        ...

    def count_unresolved(self) -> int:
        """Count unresolved duplicate candidates."""
        ...

    def update(self, candidate: DuplicateCandidate) -> None:
        """Update resolution state of a duplicate candidate."""
        ...
