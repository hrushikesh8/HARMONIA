"""API request and response schemas."""

from harmonia.api.schemas.album import AlbumDetailResponse, AlbumResponse
from harmonia.api.schemas.artist import ArtistDetailResponse, ArtistResponse
from harmonia.api.schemas.common import (
    HealthResponse,
    LibraryInfoResponse,
    PaginatedResponse,
)
from harmonia.api.schemas.duplicate import (
    DuplicateCandidateResponse,
    ResolveDuplicateRequest,
)
from harmonia.api.schemas.file import (
    AudioAnalysisResponse,
    TrackAnalysisMultiFileResponse,
)
from harmonia.api.schemas.playback import (
    ListeningEventResponse,
    ListeningStatsResponse,
    PlaybackStateResponse,
    QueueItemResponse,
    QueueTrackRequest,
    RepeatRequest,
    SeekRequest,
    SetQueueRequest,
    ShuffleRequest,
    VolumeRequest,
)
from harmonia.api.schemas.search import SearchItemResponse, SearchResponse
from harmonia.api.schemas.track import (
    TagResponse,
    TrackDetailResponse,
    TrackFileResponse,
    TrackResponse,
)

__all__ = [
    "AlbumDetailResponse",
    "AlbumResponse",
    "ArtistDetailResponse",
    "ArtistResponse",
    "AudioAnalysisResponse",
    "DuplicateCandidateResponse",
    "HealthResponse",
    "LibraryInfoResponse",
    "ListeningEventResponse",
    "ListeningStatsResponse",
    "PaginatedResponse",
    "PlaybackStateResponse",
    "QueueItemResponse",
    "QueueTrackRequest",
    "RepeatRequest",
    "ResolveDuplicateRequest",
    "SearchItemResponse",
    "SearchResponse",
    "SeekRequest",
    "SetQueueRequest",
    "ShuffleRequest",
    "TagResponse",
    "TrackAnalysisMultiFileResponse",
    "TrackDetailResponse",
    "TrackFileResponse",
    "TrackResponse",
    "VolumeRequest",
]
