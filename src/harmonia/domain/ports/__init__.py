"""Domain ports (repository and service interfaces).

These are Python Protocols — structural subtyping contracts that
infrastructure implementations must satisfy.  No ABCs, no inheritance.
"""

from harmonia.domain.ports.album_repository import AlbumRepository
from harmonia.domain.ports.artist_repository import ArtistRepository
from harmonia.domain.ports.audio_analysis_repository import AudioAnalysisRepository
from harmonia.domain.ports.audio_analyzer import (
    AudioAnalyzer,
    SignalAnalysisResult,
    TechnicalAudioProperties,
)
from harmonia.domain.ports.duplicate_candidate_repository import DuplicateCandidateRepository
from harmonia.domain.ports.listening_event_repository import ListeningEventRepository
from harmonia.domain.ports.metadata_field_repository import MetadataFieldRepository
from harmonia.domain.ports.metadata_provider import MetadataProvider
from harmonia.domain.ports.playback_port import PlaybackPort
from harmonia.domain.ports.playback_snapshot_repository import PlaybackSnapshotRepository
from harmonia.domain.ports.search_repository import SearchRepository
from harmonia.domain.ports.track_repository import TrackRepository

__all__ = [
    "AlbumRepository",
    "ArtistRepository",
    "AudioAnalysisRepository",
    "AudioAnalyzer",
    "DuplicateCandidateRepository",
    "ListeningEventRepository",
    "MetadataFieldRepository",
    "MetadataProvider",
    "PlaybackPort",
    "PlaybackSnapshotRepository",
    "SearchRepository",
    "SignalAnalysisResult",
    "TechnicalAudioProperties",
    "TrackRepository",
]
