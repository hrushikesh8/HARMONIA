"""Library service — queries and manages the music library.

This is the primary read-path service for library operations.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from harmonia.application.dto import (
    AlbumDetail,
    AlbumSummary,
    ArtistDetail,
    ArtistSummary,
    LibrarySummary,
    TagInfo,
    TrackDetail,
    TrackFileInfo,
    TrackSummary,
)
from harmonia.core.logging import get_logger
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from uuid import UUID

    from sqlalchemy.orm import Session

logger = get_logger(__name__)


class LibraryService:
    """Provides read access to the music library."""

    def __init__(self, session: Session) -> None:
        self._session = session
        self._track_repo = SqliteTrackRepository(session)
        self._album_repo = SqliteAlbumRepository(session)
        self._artist_repo = SqliteArtistRepository(session)

    def get_summary(self) -> LibrarySummary:
        """Get high-level library statistics."""
        return LibrarySummary(
            total_tracks=self._track_repo.count(),
            total_albums=self._album_repo.count(),
            total_artists=self._artist_repo.count(),
            total_files=self._track_repo.count_files(),
        )

    # ── Tracks ────────────────────────────────────────────────────────────

    def list_tracks(
        self,
        limit: int = 100,
        offset: int = 0,
        artist_id: UUID | None = None,
        album_id: UUID | None = None,
        genre: str | None = None,
    ) -> list[TrackSummary]:
        """List tracks with pagination."""
        tracks = self._track_repo.get_all(
            limit=limit,
            offset=offset,
            artist_id=artist_id,
            album_id=album_id,
            genre=genre,
        )
        return [self._to_track_summary(t) for t in tracks]

    def count_tracks(
        self,
        artist_id: UUID | None = None,
        album_id: UUID | None = None,
        genre: str | None = None,
    ) -> int:
        """Count tracks with optional filters."""
        return self._track_repo.count(
            artist_id=artist_id, album_id=album_id, genre=genre
        )

    def get_track(self, track_id: UUID) -> TrackDetail | None:
        """Get full details for a track."""
        track = self._track_repo.get_by_id(track_id)
        if track is None:
            return None

        # Resolve names.
        artist_name = None
        if track.artist_id:
            artist = self._artist_repo.get_by_id(track.artist_id)
            artist_name = artist.name if artist else None

        album_title = None
        if track.album_id:
            album = self._album_repo.get_by_id(track.album_id)
            album_title = album.title if album else None

        # Get files.
        files = self._track_repo.get_files(track.id)
        file_infos = [
            TrackFileInfo(
                id=f.id,
                file_path=f.file_path,
                format=f.format.value,
                codec=f.codec,
                bitrate=f.bitrate,
                sample_rate=f.sample_rate,
                bit_depth=f.bit_depth,
                channels=f.channels,
                channel_layout=getattr(f, "channel_layout", None),
                encoder=getattr(f, "encoder", None),
                duration_seconds=getattr(f, "duration_seconds", None),
                file_size_bytes=getattr(f, "file_size", 0),
                is_lossless=f.is_lossless,
                quality_score=f.quality_score,
                file_size_display=f.file_size_display,
            )
            for f in files
        ]

        # Get tags.
        tags = self._track_repo.get_tags(track.id)
        tag_infos = [
            TagInfo(name=t.name, value=t.value, source=t.source.value)
            for t in tags
        ]

        return TrackDetail(
            id=track.id,
            title=track.title,
            artist_name=artist_name,
            album_title=album_title,
            artist_id=track.artist_id,
            album_id=track.album_id,
            track_number=track.track_number,
            disc_number=track.disc_number,
            duration_seconds=track.duration_seconds,
            duration_display=track.duration_display,
            year=track.year,
            genre=track.genre,
            mbid=track.mbid,
            created_at=track.created_at,
            files=file_infos,
            tags=tag_infos,
        )

    # ── Artists ───────────────────────────────────────────────────────────

    def list_artists(self, limit: int = 100, offset: int = 0) -> list[ArtistSummary]:
        """List artists with pagination."""
        artists = self._artist_repo.get_all(limit=limit, offset=offset)
        return [
            ArtistSummary(
                id=a.id,
                name=a.name,
                sort_name=a.sort_name,
            )
            for a in artists
        ]

    def count_artists(self) -> int:
        """Count total artists in library."""
        return self._artist_repo.count()

    def get_artist(self, artist_id: UUID) -> ArtistDetail | None:
        """Get full details for an artist including albums."""
        artist = self._artist_repo.get_by_id(artist_id)
        if artist is None:
            return None

        albums = self._album_repo.get_by_artist(artist_id)
        album_summaries = [
            AlbumSummary(
                id=alb.id,
                title=alb.title,
                artist_name=artist.name,
                year=alb.year,
            )
            for alb in albums
        ]

        return ArtistDetail(
            id=artist.id,
            name=artist.name,
            sort_name=artist.sort_name,
            mbid=artist.mbid,
            albums=album_summaries,
            album_count=len(album_summaries),
        )

    # ── Albums ────────────────────────────────────────────────────────────

    def list_albums(
        self, limit: int = 100, offset: int = 0, artist_id: UUID | None = None
    ) -> list[AlbumSummary]:
        """List albums with pagination."""
        albums = self._album_repo.get_all(limit=limit, offset=offset, artist_id=artist_id)
        summaries = []
        for album in albums:
            artist_name = None
            if album.artist_id:
                artist = self._artist_repo.get_by_id(album.artist_id)
                artist_name = artist.name if artist else None
            summaries.append(
                AlbumSummary(
                    id=album.id,
                    title=album.title,
                    artist_name=artist_name,
                    year=album.year,
                )
            )
        return summaries

    def count_albums(self, artist_id: UUID | None = None) -> int:
        """Count albums with optional artist filter."""
        return self._album_repo.count(artist_id=artist_id)

    def get_album(self, album_id: UUID) -> AlbumDetail | None:
        """Get full details for an album including track listing."""
        album = self._album_repo.get_by_id(album_id)
        if album is None:
            return None

        artist_name = None
        if album.artist_id:
            artist = self._artist_repo.get_by_id(album.artist_id)
            artist_name = artist.name if artist else None

        tracks = self._track_repo.get_by_album(album_id)
        track_summaries = [self._to_track_summary(t) for t in tracks]

        return AlbumDetail(
            id=album.id,
            title=album.title,
            artist_name=artist_name,
            artist_id=album.artist_id,
            year=album.year,
            mbid=album.mbid,
            total_tracks=album.total_tracks,
            total_discs=album.total_discs,
            art_path=album.art_path,
            tracks=track_summaries,
            track_count=len(track_summaries),
        )

    # ── Internal helpers ──────────────────────────────────────────────────

    def _to_track_summary(self, track: object) -> TrackSummary:
        """Convert a Track domain entity to a TrackSummary DTO."""
        from harmonia.domain.entities.track import Track

        t = track
        assert isinstance(t, Track)

        artist_name = None
        if t.artist_id:
            artist = self._artist_repo.get_by_id(t.artist_id)
            artist_name = artist.name if artist else None

        album_title = None
        if t.album_id:
            album = self._album_repo.get_by_id(t.album_id)
            album_title = album.title if album else None

        return TrackSummary(
            id=t.id,
            title=t.title,
            artist_name=artist_name,
            album_title=album_title,
            duration_display=t.duration_display,
            genre=t.genre,
            year=t.year,
        )
