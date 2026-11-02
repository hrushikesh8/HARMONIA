"""FastAPI dependency injection providers."""

from __future__ import annotations

import re
import urllib.parse
from collections.abc import Callable, Generator
from typing import TYPE_CHECKING

from fastapi import Depends, Header, HTTPException, Request

from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.application.library_service import LibraryService
from harmonia.application.personalization_service import PersonalizationService
from harmonia.application.playback_service import PlaybackService
from harmonia.application.search_service import SearchService
from harmonia.application.streaming_service import StreamingService
from harmonia.core.config import HarmoniaConfig
from harmonia.infrastructure.persistence.listening_event_repo import (
    SqliteListeningEventRepository,
)

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

LOOPBACK_ORIGIN_REGEX = re.compile(
    r"^https?://(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$",
    re.IGNORECASE,
)


def is_trusted_loopback_origin(origin: str) -> bool:
    """Check if the provided origin matches trusted loopback hosts."""
    if not origin:
        return False
    return bool(LOOPBACK_ORIGIN_REGEX.match(origin.strip()))


def validate_cross_origin_mutation(request: Request) -> None:
    """Validate Origin and Referer headers for state-changing HTTP methods.

    Protects state-changing methods (POST, PUT, DELETE, PATCH) against CSRF
    and unauthorized cross-origin requests originating from browser contexts.

    Rules:
    1. If Origin header is present:
       Browsers attach Origin on all cross-origin POST/PUT/DELETE requests (including
       simple form-style POSTs, text/plain POSTs, and fetch/sendBeacon calls).
       Opaque origins ('null') and untrusted origins are strictly rejected with 403 Forbidden.
    2. If Origin header is absent:
       Check the Referer header. If Referer is present, its scheme and host must match
       trusted loopback; otherwise rejected with 403 Forbidden.
    3. If both Origin and Referer headers are absent:
       Allowed. Legitimate local non-browser clients (Harmonia CLI commands, native desktop
       wrappers, background workers, curl, and python integration tests) do not send Origin
       or Referer headers. Web browsers running on the host cannot suppress or omit Origin
       on cross-origin mutation requests per W3C Fetch specification.
    """
    if request.method not in ("POST", "PUT", "DELETE", "PATCH"):
        return

    origin = request.headers.get("origin")
    referer = request.headers.get("referer")

    if origin is not None:
        if origin == "null" or not is_trusted_loopback_origin(origin):
            raise HTTPException(
                status_code=403,
                detail="Forbidden: Untrusted cross-origin request rejected.",
            )
        return

    if referer is not None:
        try:
            parsed = urllib.parse.urlsplit(referer)
            if parsed.scheme and parsed.netloc:
                referer_origin = f"{parsed.scheme}://{parsed.netloc}"
            else:
                referer_origin = referer
        except Exception:
            raise HTTPException(
                status_code=403,
                detail="Forbidden: Malformed cross-origin referer header.",
            ) from None

        if not is_trusted_loopback_origin(referer_origin):
            raise HTTPException(
                status_code=403,
                detail="Forbidden: Untrusted cross-origin referer rejected.",
            )
        return

    # Neither Origin nor Referer present: safe non-browser desktop client.
    return


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
    """Verify local API token if one is configured, and protect state-changing requests."""
    # 1. API Token enforcement (when configured)
    expected_token = config.api.token
    if expected_token:
        provided = x_harmonia_token
        if not provided and authorization:
            parts = authorization.split(" ", 1)
            if len(parts) == 2 and parts[0].lower() == "bearer":
                provided = parts[1]

        if not provided or provided != expected_token:
            raise HTTPException(status_code=401, detail="Unauthorized: Invalid or missing API token.")

    # 2. State-changing cross-origin protection
    validate_cross_origin_mutation(request)



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


def get_playback_service(request: Request) -> PlaybackService:
    """Retrieve shared PlaybackService attached to application state."""
    service = getattr(request.app.state, "playback_service", None)
    if service is None:
        raise RuntimeError("PlaybackService not initialized on app.state")
    return service


def get_listening_event_repo(
    session_factory: Callable[[], Session] = Depends(get_session_factory),
) -> Generator[SqliteListeningEventRepository, None, None]:
    """Provide SqliteListeningEventRepository with scoped session."""
    with session_factory() as session:
        yield SqliteListeningEventRepository(session)


def get_personalization_service(
    session_factory: Callable[[], Session] = Depends(get_session_factory),
) -> Generator[PersonalizationService, None, None]:
    """Provide PersonalizationService instance with scoped session."""
    with session_factory() as session:
        yield PersonalizationService(session, autocommit=True)

