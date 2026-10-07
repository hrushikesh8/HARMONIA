"""FastAPI application factory for Harmonia."""

from __future__ import annotations

from contextlib import asynccontextmanager
from typing import TYPE_CHECKING

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from harmonia import __version__
from harmonia.api.routers import (
    albums_router,
    artists_router,
    artwork_router,
    duplicates_router,
    files_router,
    health_router,
    search_router,
    stream_router,
    tracks_router,
)
from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.core.exceptions import HarmoniaError

if TYPE_CHECKING:
    from collections.abc import AsyncIterator, Callable

    from sqlalchemy.orm import Session


def create_app(
    config: HarmoniaConfig | None = None,
    session_factory: Callable[[], Session] | None = None,
    analysis_service: AudioAnalysisService | None = None,
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

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        # Startup: Recover stale jobs from previous unexpected terminations and start workers
        if effective_analysis_service:
            effective_analysis_service.recover_stale_jobs(
                timeout_seconds=app_config.analysis.timeout_seconds
            )
            effective_analysis_service.start_worker()

        yield

        # Shutdown: Gracefully stop worker threads and dispose engine
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

    return app
