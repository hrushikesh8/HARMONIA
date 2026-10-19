"""Rating domain entity."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID

from harmonia.core.exceptions import ValidationError


@dataclass
class Rating:
    """A user star rating (1-5) for a track."""

    track_id: UUID
    rating: int
    client_updated_at: int = field(
        default_factory=lambda: int(datetime.now(UTC).timestamp() * 1000)
    )

    def __post_init__(self) -> None:
        if not (1 <= self.rating <= 5):
            raise ValidationError(
                "rating", f"Rating must be an integer between 1 and 5, got {self.rating}"
            )
