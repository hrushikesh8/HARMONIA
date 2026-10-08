"""Domain entities — pure Python data objects representing the music domain."""

from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.audio_analysis import AnalysisStatus, AudioAnalysis
from harmonia.domain.entities.duplicate_candidate import DuplicateCandidate
from harmonia.domain.entities.listening_event import ListeningEvent
from harmonia.domain.entities.metadata_field import MetadataFieldRecord
from harmonia.domain.entities.playback import PlaybackState, QueueItem, QueueSnapshot
from harmonia.domain.entities.tag import Tag
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile

__all__ = [
    "Album",
    "AnalysisStatus",
    "Artist",
    "AudioAnalysis",
    "DuplicateCandidate",
    "ListeningEvent",
    "MetadataFieldRecord",
    "PlaybackState",
    "QueueItem",
    "QueueSnapshot",
    "Tag",
    "Track",
    "TrackFile",
]
