"""DuplicateCandidate domain entity — records potential duplicate tracks for review."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4


@dataclass
class DuplicateCandidate:
    """Records a probable-duplicate relationship between two Tracks.

    In Harmonia Phase 2, Tier B candidate pairs create separate tracks and a
    DuplicateCandidate entry for user review, ensuring zero false merges.
    """

    track_id_a: UUID  # The existing Track
    track_id_b: UUID  # The candidate / newly created Track
    confidence: str  # "probable"
    reason: str  # Human-readable explanation
    id: UUID = field(default_factory=uuid4)
    resolved: bool = False  # True after user reviews
    resolution: str | None = None  # "merged", "distinct", None
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    resolved_at: datetime | None = None

    def mark_resolved(self, resolution: str) -> None:
        """Mark this candidate as resolved."""
        self.resolved = True
        self.resolution = resolution
        self.resolved_at = datetime.now(UTC)
