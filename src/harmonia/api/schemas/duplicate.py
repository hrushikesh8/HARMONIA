"""Duplicate candidate API schemas."""

from __future__ import annotations

from datetime import datetime
from uuid import UUID

from pydantic import BaseModel


class DuplicateCandidateResponse(BaseModel):
    """Duplicate candidate pair."""

    id: UUID
    track_id_a: UUID
    track_id_b: UUID
    confidence: str
    reason: str
    resolved: bool = False
    resolution: str | None = None
    created_at: datetime


class ResolveDuplicateRequest(BaseModel):
    """Payload to resolve a duplicate candidate."""

    resolution: str  # e.g., "confirmed_duplicate", "distinct_tracks"
