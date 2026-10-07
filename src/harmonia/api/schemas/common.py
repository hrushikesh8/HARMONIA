"""Common schemas for Harmonia HTTP API."""

from __future__ import annotations

from typing import Generic, TypeVar

from pydantic import BaseModel, Field

T = TypeVar("T")


class PaginatedResponse(BaseModel, Generic[T]):
    """Standard pagination envelope."""

    items: list[T] = Field(default_factory=list)
    total: int
    page: int
    page_size: int
    total_pages: int


class HealthResponse(BaseModel):
    """System health and database liveness status."""

    status: str = "ok"
    version: str
    database: str = "connected"


class LibraryInfoResponse(BaseModel):
    """Factual high-level library statistics."""

    total_tracks: int
    total_albums: int
    total_artists: int
    total_files: int
