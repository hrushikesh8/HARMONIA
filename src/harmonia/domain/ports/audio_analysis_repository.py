"""Port protocol for AudioAnalysis persistence."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.audio_analysis import AudioAnalysis


class AudioAnalysisRepository(Protocol):
    """Repository interface for persisting and querying AudioAnalysis entities."""

    def save(self, analysis: AudioAnalysis) -> AudioAnalysis:
        """Save or update an AudioAnalysis record."""
        ...

    def get_by_id(self, analysis_id: UUID) -> AudioAnalysis | None:
        """Retrieve an AudioAnalysis record by its unique ID."""
        ...

    def get_by_track_file_id(self, track_file_id: UUID) -> AudioAnalysis | None:
        """Retrieve the AudioAnalysis record for a specific TrackFile."""
        ...

    def get_by_track_id(self, track_id: UUID) -> list[AudioAnalysis]:
        """Retrieve all AudioAnalysis records for all TrackFiles of a Track."""
        ...

    def get_stale_running(self, timeout_seconds: float = 600.0) -> list[AudioAnalysis]:
        """Find jobs stuck in RUNNING status older than timeout_seconds."""
        ...

    def delete(self, analysis_id: UUID) -> bool:
        """Delete an AudioAnalysis record by ID."""
        ...
