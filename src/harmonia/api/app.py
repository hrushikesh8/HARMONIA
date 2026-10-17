"""FastAPI application factory for Harmonia."""

from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path
from typing import TYPE_CHECKING

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from harmonia import __version__
from harmonia.api.routers import (
    albums_router,
    artists_router,
    artwork_router,
    duplicates_router,
    files_router,
    health_router,
    listening_events_router,
    playback_router,
    queue_router,
    search_router,
    stream_router,
    tracks_router,
)
from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.application.playback_service import PlaybackService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.core.exceptions import HarmoniaError
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
        allow_origin_regex=r"^https?://(localhost|127\.0\.0\.1)(:\d+)?$",
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    # Custom exception handling for Harmonia domain errors
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

    # Static assets and UI serving
    static_dir = Path(__file__).parent / "static"
    if static_dir.exists():
        app.mount("/static", StaticFiles(directory=static_dir), name="static")

        @app.get("/", include_in_schema=False)
        @app.get("/ui", include_in_schema=False)
        async def serve_ui() -> FileResponse:
            return FileResponse(static_dir / "index.html", media_type="text/html")

    return app
