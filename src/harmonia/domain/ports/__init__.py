"""Domain ports (repository interfaces).

These are Python Protocols — structural subtyping contracts that
infrastructure implementations must satisfy.  No ABCs, no inheritance.
"""

from harmonia.domain.ports.track_repository import TrackRepository
from harmonia.domain.ports.album_repository import AlbumRepository
from harmonia.domain.ports.artist_repository import ArtistRepository

__all__ = ["TrackRepository", "AlbumRepository", "ArtistRepository"]
