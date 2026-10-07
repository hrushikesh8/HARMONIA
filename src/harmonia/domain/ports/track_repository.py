"""Track repository port — interface for track persistence."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.tag import Tag
    from harmonia.domain.entities.track import Track
    from harmonia.domain.entities.track_file import TrackFile


class TrackRepository(Protocol):
    """Port for persisting and querying tracks."""

    def add(self, track: Track) -> None:
        """Persist a new track."""
        ...

    def get_by_id(self, track_id: UUID) -> Track | None:
        """Retrieve a track by its ID."""
        ...

    def get_all(
        self,
        limit: int = 100,
        offset: int = 0,
        artist_id: UUID | None = None,
        album_id: UUID | None = None,
        genre: str | None = None,
    ) -> list[Track]:
        """Retrieve all tracks with pagination and optional filters."""
        ...

    def get_by_album(self, album_id: UUID) -> list[Track]:
        """Retrieve all tracks in an album, ordered by disc/track number."""
        ...

    def get_by_artist(self, artist_id: UUID) -> list[Track]:
        """Retrieve all tracks by an artist."""
        ...

    def update(self, track: Track) -> None:
        """Update an existing track's metadata."""
        ...

    def delete(self, track_id: UUID) -> None:
        """Remove a track and its associated files/tags."""
        ...

    def count(
        self,
        artist_id: UUID | None = None,
        album_id: UUID | None = None,
        genre: str | None = None,
    ) -> int:
        """Return the total number of tracks in the library, with optional filters."""
        ...

    def count_files(self) -> int:
        """Return the total number of track files in the library."""
        ...

    # ── Track files ───────────────────────────────────────────────────────

    def add_file(self, track_file: TrackFile) -> None:
        """Associate a physical file with a track."""
        ...

    def get_files(self, track_id: UUID) -> list[TrackFile]:
        """Get all physical files for a track."""
        ...

    def get_file_by_id(self, file_id: UUID) -> TrackFile | None:
        """Look up a track file by its unique ID."""
        ...

    def get_file_by_path(self, file_path: str) -> TrackFile | None:
        """Look up a track file by its filesystem path."""
        ...

    def file_exists(self, file_path: str) -> bool:
        """Check whether a file path already exists in the library."""
        ...

    def get_file_by_hash(self, file_hash: str) -> TrackFile | None:
        """Look up a track file by its SHA-256 hash."""
        ...

    def update_file(self, track_file: TrackFile) -> None:
        """Update properties of an existing track file."""
        ...

    # ── Tags ──────────────────────────────────────────────────────────────

    def add_tag(self, tag: Tag) -> None:
        """Add a tag to a track."""
        ...

    def get_tags(self, track_id: UUID) -> list[Tag]:
        """Get all tags for a track."""
        ...

    def delete_tags(self, track_id: UUID, source: str | None = None) -> None:
        """Delete tags for a track, optionally filtered by source."""
        ...
