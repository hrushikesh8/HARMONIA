"""Port definition for external MetadataProvider."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.metadata_field import MetadataFieldRecord


class MetadataProvider(Protocol):
    """Protocol for external metadata providers (e.g. MusicBrainz)."""

    @property
    def name(self) -> str:
        """Provider identifier (e.g., 'musicbrainz')."""
        ...

    def lookup_track(
        self,
        entity_id: UUID,
        artist_name: str | None,
        album_title: str | None,
        track_title: str | None,
        duration_seconds: float | None = None,
    ) -> list[MetadataFieldRecord]:
        """Fetch metadata for a track and return field records."""
        ...
