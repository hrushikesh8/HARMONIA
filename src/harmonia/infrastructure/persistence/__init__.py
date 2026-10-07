"""SQLAlchemy persistence layer."""

from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.audio_analysis_repo import (
    SqliteAudioAnalysisRepository,
)
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)
from harmonia.infrastructure.persistence.metadata_field_repo import (
    SqliteMetadataFieldRepository,
)
from harmonia.infrastructure.persistence.models import (
    AlbumModel,
    ArtistModel,
    AudioAnalysisModel,
    DuplicateCandidateModel,
    MetadataFieldModel,
    TagModel,
    TrackFileModel,
    TrackModel,
)
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

__all__ = [
    "AlbumModel",
    "ArtistModel",
    "AudioAnalysisModel",
    "DuplicateCandidateModel",
    "MetadataFieldModel",
    "SqliteAlbumRepository",
    "SqliteArtistRepository",
    "SqliteAudioAnalysisRepository",
    "SqliteDuplicateCandidateRepository",
    "SqliteMetadataFieldRepository",
    "SqliteSearchRepository",
    "SqliteTrackRepository",
    "TagModel",
    "TrackFileModel",
    "TrackModel",
]
