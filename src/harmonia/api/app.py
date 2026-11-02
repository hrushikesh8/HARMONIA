"""FastAPI application factory for Harmonia."""

from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path
from typing import TYPE_CHECKING

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles
from starlette.datastructures import Headers, URL
from starlette.middleware.trustedhost import TrustedHostMiddleware
from starlette.responses import PlainTextResponse, RedirectResponse, Response
from starlette.types import Receive, Scope, Send

class LoopbackTrustedHostMiddleware(TrustedHostMiddleware):
    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if self.allow_any or scope["type"] not in ("http", "websocket"):
            await self.app(scope, receive, send)
            return

        headers = Headers(scope=scope)
        raw_host = headers.get("host", "")
        if raw_host.startswith("["):
            bracket_idx = raw_host.find("]")
            host = raw_host[:bracket_idx + 1] if bracket_idx != -1 else raw_host.split(":")[0]
        else:
            host = raw_host.split(":")[0]

        is_valid_host = False
        found_www_redirect = False
        for pattern in self.allowed_hosts:
            if host == pattern or (pattern.startswith("*") and host.endswith(pattern[1:])):
                is_valid_host = True
                break
            elif "www." + host == pattern:
                found_www_redirect = True

        if is_valid_host:
            await self.app(scope, receive, send)
        else:
            if found_www_redirect and self.www_redirect:
                url = URL(scope=scope)
                redirect_url = url.replace(netloc="www." + url.netloc)
                response: Response = RedirectResponse(url=str(redirect_url))
            else:
                response = PlainTextResponse("Invalid host header", status_code=400)
            await response(scope, receive, send)


from harmonia import __version__
from harmonia.api.routers import (
    albums_router,
    artists_router,
    artwork_router,
    duplicates_router,
    favorites_router,
    files_router,
    health_router,
    listening_events_router,
    playback_router,
    playlists_router,
    queue_router,
    ratings_router,
    search_router,
    stream_router,
    tracks_router,
)
from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.application.playback_service import PlaybackService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.core.exceptions import EntityNotFoundError, HarmoniaError, ValidationError
from harmonia.infrastructure.audio.backend import DesktopAudioBackend
from harmonia.infrastructure.persistence.listening_event_repo import (
    SqliteListeningEventRepository,
)
from harmonia.infrastructure.persistence.playback_snapshot_repo import (
    SqlitePlaybackSnapshotRepository,
)
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Callable

    from sqlalchemy.orm import Session


def create_app(
    config: HarmoniaConfig | None = None,
    session_factory: Callable[[], Session] | None = None,
    analysis_service: AudioAnalysisService | None = None,
    audio_backend: DesktopAudioBackend | None = None,
) -> FastAPI:
    """Create and configure a FastAPI application instance."""
    app_config = config or HarmoniaConfig.load()
    app_config.ensure_directories()

    # Configure database engine & session factory if not provided
    effective_session_factory = session_factory
    engine = None
    if effective_session_factory is None:
        engine = create_database_engine(app_config.database.path)
        init_database(engine)
        effective_session_factory = create_session_factory(engine)

    # Configure audio analysis service
    effective_analysis_service = analysis_service
    if effective_analysis_service is None:
        effective_analysis_service = AudioAnalysisService(
            session_factory=effective_session_factory,
            max_workers=app_config.analysis.max_workers,
        )

    # Configure core playback service with state snapshot restoration
    playback_session = effective_session_factory()
    playback_service = PlaybackService(
        track_repo=SqliteTrackRepository(playback_session),
        listening_event_repo=SqliteListeningEventRepository(playback_session),
        snapshot_repo=SqlitePlaybackSnapshotRepository(playback_session),
        audio_backend=audio_backend,
        session=playback_session,
    )
    playback_service.restore_snapshot()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        # Startup: Recover stale jobs from previous unexpected terminations and start workers
        if effective_analysis_service:
            effective_analysis_service.recover_stale_jobs(
                timeout_seconds=app_config.analysis.timeout_seconds
            )
            effective_analysis_service.start_worker()

        yield

        # Shutdown: Gracefully stop workers, playback, and close db resources
        playback_service.stop()
        playback_session.close()

        if effective_analysis_service:
            effective_analysis_service.stop_worker()
        if engine:
            engine.dispose()

    app = FastAPI(
        title="Harmonia API",
        description="Deterministic music infrastructure and audio intelligence REST API.",
        version=__version__,
        lifespan=lifespan,
    )

    # Store state on app
    app.state.config = app_config
    app.state.session_factory = effective_session_factory
    app.state.analysis_service = effective_analysis_service
    app.state.playback_service = playback_service

    # Local CORS policy strictly allowing loopback clients
    app.add_middleware(
        CORSMiddleware,
        allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1|\[::1\])(:\d+)?$",
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Host-header validation for supported loopback deployment hosts
    allowed_hosts = [
        "localhost",
        "127.0.0.1",
        "[::1]",
        "*.localhost",
        "testserver",
    ]
    if app_config.api.host and app_config.api.host not in allowed_hosts:
        allowed_hosts.append(app_config.api.host)

    app.add_middleware(
        LoopbackTrustedHostMiddleware,
        allowed_hosts=allowed_hosts,
    )

    # Custom exception handling for Harmonia domain errors
    @app.exception_handler(EntityNotFoundError)
    async def entity_not_found_handler(request: Request, exc: EntityNotFoundError) -> JSONResponse:
        return JSONResponse(
            status_code=404,
            content={"detail": str(exc)},
        )

    @app.exception_handler(ValidationError)
    async def validation_error_handler(request: Request, exc: ValidationError) -> JSONResponse:
        return JSONResponse(
            status_code=422,
            content={"detail": str(exc)},
        )

    @app.exception_handler(HarmoniaError)
    async def harmonia_error_handler(request: Request, exc: HarmoniaError) -> JSONResponse:
        return JSONResponse(
            status_code=400,
            content={"detail": str(exc)},
        )

    # Mount routers under /api/v1
    app.include_router(health_router, prefix="/api/v1")
    app.include_router(tracks_router, prefix="/api/v1")
    app.include_router(files_router, prefix="/api/v1")
    app.include_router(albums_router, prefix="/api/v1")
    app.include_router(artists_router, prefix="/api/v1")
    app.include_router(search_router, prefix="/api/v1")
    app.include_router(artwork_router, prefix="/api/v1")
    app.include_router(duplicates_router, prefix="/api/v1")
    app.include_router(stream_router, prefix="/api/v1")
    app.include_router(playback_router, prefix="/api/v1")
    app.include_router(queue_router, prefix="/api/v1")
    app.include_router(listening_events_router, prefix="/api/v1")
    app.include_router(playlists_router, prefix="/api/v1")
    app.include_router(favorites_router, prefix="/api/v1")
    app.include_router(ratings_router, prefix="/api/v1")

    # Static assets and UI serving
    static_dir = Path(__file__).parent / "static"
    if static_dir.exists():
        app.mount("/static", StaticFiles(directory=static_dir), name="static")

        @app.get("/", include_in_schema=False)
        @app.get("/ui", include_in_schema=False)
        async def serve_ui() -> FileResponse:
            return FileResponse(static_dir / "index.html", media_type="text/html")

    return app
