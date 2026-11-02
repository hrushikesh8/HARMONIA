"""API router modules."""

from harmonia.api.routers.albums import router as albums_router
from harmonia.api.routers.artists import router as artists_router
from harmonia.api.routers.artwork import router as artwork_router
from harmonia.api.routers.duplicates import router as duplicates_router
from harmonia.api.routers.favorites import router as favorites_router
from harmonia.api.routers.files import router as files_router
from harmonia.api.routers.health import router as health_router
from harmonia.api.routers.listening_events import router as listening_events_router
from harmonia.api.routers.playback import router as playback_router
from harmonia.api.routers.playlists import router as playlists_router
from harmonia.api.routers.queue import router as queue_router
from harmonia.api.routers.ratings import router as ratings_router
from harmonia.api.routers.search import router as search_router
from harmonia.api.routers.stream import router as stream_router
from harmonia.api.routers.tracks import router as tracks_router

__all__ = [
    "albums_router",
    "artists_router",
    "artwork_router",
    "duplicates_router",
    "favorites_router",
    "files_router",
    "health_router",
    "listening_events_router",
    "playback_router",
    "playlists_router",
    "queue_router",
    "ratings_router",
    "search_router",
    "stream_router",
    "tracks_router",
]
