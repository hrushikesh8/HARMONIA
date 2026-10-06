"""Domain entities — pure Python data objects representing the music domain."""

from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.entities.tag import Tag

__all__ = ["Artist", "Album", "Track", "TrackFile", "Tag"]
