"""FastAPI dependency injection providers."""

from __future__ import annotations

from collections.abc import Callable, Generator
from typing import TYPE_CHECKING

from fastapi import Depends, Header, HTTPException, Request

from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.application.library_service import LibraryService
from harmonia.application.search_service import SearchService
from harmonia.application.streaming_service import StreamingService
from harmonia.core.config import HarmoniaConfig

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


def get_config(request: Request) -> HarmoniaConfig:
    """Retrieve HarmoniaConfig attached to application state."""
    return getattr(request.app.state, "config", HarmoniaConfig.load())


def get_session_factory(request: Request) -> Callable[[], Session]:
    """Retrieve SQLAlchemy session factory attached to application state."""
    factory = getattr(request.app.state, "session_factory", None)
    if factory is None:
        raise RuntimeError("Application session factory not initialized on app.state")
    return factory


def verify_api_token(
    request: Request,
    config: HarmoniaConfig = Depends(get_config),
    x_harmonia_token: str | None = Header(default=None, alias="X-Harmonia-Token"),
    authorization: str | None = Header(default=None),
) -> None:
    """Verify local API token if one is configured."""
    expected_token = config.api.token
    if not expected_token:
        # No token configured; local-first loopback access trusted
        return

    provided = x_harmonia_token
    if not provided and authorization:
        parts = authorization.split(" ", 1)
        if len(parts) == 2 and parts[0].lower() == "bearer":
            provided = parts[1]

    if not provided or provided != expected_token:
        raise HTTPException(status_code=401, detail="Unauthorized: Invalid or missing API token.")


def get_library_service(
    session_factory: Callable[[], Session] = Depends(get_session_factory),
) -> Generator[LibraryService, None, None]:
    """Provide LibraryService instance with scoped session."""
    with session_factory() as session:
        yield LibraryService(session)


def get_search_service(
    session_factory: Callable[[], Session] = Depends(get_session_factory),
) -> Generator[SearchService, None, None]:
    """Provide SearchService instance with scoped session."""
    with session_factory() as session:
        yield SearchService(session)


def get_analysis_service(
    request: Request,
    session_factory: Callable[[], Session] = Depends(get_session_factory),
) -> AudioAnalysisService:
    """Provide shared or scoped AudioAnalysisService instance."""
    existing = getattr(request.app.state, "analysis_service", None)
    if existing is not None:
        return existing
    return AudioAnalysisService(session_factory=session_factory)


def get_streaming_service(
    session_factory: Callable[[], Session] = Depends(get_session_factory),
) -> StreamingService:
    """Provide StreamingService instance."""
    return StreamingService(session_factory=session_factory)
