"""API router modules."""

from harmonia.api.routers.albums import router as albums_router
from harmonia.api.routers.artists import router as artists_router
from harmonia.api.routers.artwork import router as artwork_router
from harmonia.api.routers.duplicates import router as duplicates_router
from harmonia.api.routers.files import router as files_router
from harmonia.api.routers.health import router as health_router
from harmonia.api.routers.search import router as search_router
from harmonia.api.routers.stream import router as stream_router
from harmonia.api.routers.tracks import router as tracks_router

__all__ = [
    "albums_router",
    "artists_router",
    "artwork_router",
    "duplicates_router",
    "files_router",
    "health_router",
    "search_router",
    "stream_router",
    "tracks_router",
]
