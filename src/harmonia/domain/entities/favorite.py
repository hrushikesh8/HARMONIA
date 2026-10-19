"""Favorite domain entity."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID


@dataclass
class Favorite:
    """A user favorite evaluation for a track."""

    track_id: UUID
    client_updated_at: int = field(
        default_factory=lambda: int(datetime.now(UTC).timestamp() * 1000)
    )
