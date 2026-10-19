"""SQLAlchemy persistence layer."""

from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.audio_analysis_repo import (
    SqliteAudioAnalysisRepository,
)
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)
from harmonia.infrastructure.persistence.favorite_repo import (
    SqliteFavoriteRepository,
)
from harmonia.infrastructure.persistence.listening_event_repo import (
    SqliteListeningEventRepository,
)
from harmonia.infrastructure.persistence.metadata_field_repo import (
    SqliteMetadataFieldRepository,
)
from harmonia.infrastructure.persistence.models import (
    AlbumModel,
    ArtistModel,
    AudioAnalysisModel,
    DuplicateCandidateModel,
    FavoriteModel,
    ListeningEventModel,
    MetadataFieldModel,
    PlaybackQueueSnapshotModel,
    PlaylistItemModel,
    PlaylistModel,
    RatingModel,
    TagModel,
    TrackFileModel,
    TrackModel,
)
from harmonia.infrastructure.persistence.playback_snapshot_repo import (
    SqlitePlaybackSnapshotRepository,
)
from harmonia.infrastructure.persistence.playlist_repo import (
    SqlitePlaylistRepository,
)
from harmonia.infrastructure.persistence.rating_repo import (
    SqliteRatingRepository,
)
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

__all__ = [
    "AlbumModel",
    "ArtistModel",
    "AudioAnalysisModel",
    "DuplicateCandidateModel",
    "FavoriteModel",
    "ListeningEventModel",
    "MetadataFieldModel",
    "PlaybackQueueSnapshotModel",
    "PlaylistItemModel",
    "PlaylistModel",
    "RatingModel",
    "SqliteAlbumRepository",
    "SqliteArtistRepository",
    "SqliteAudioAnalysisRepository",
    "SqliteDuplicateCandidateRepository",
    "SqliteFavoriteRepository",
    "SqliteListeningEventRepository",
    "SqliteMetadataFieldRepository",
    "SqlitePlaybackSnapshotRepository",
    "SqlitePlaylistRepository",
    "SqliteRatingRepository",
    "SqliteSearchRepository",
    "SqliteTrackRepository",
    "TagModel",
    "TrackFileModel",
    "TrackModel",
]
