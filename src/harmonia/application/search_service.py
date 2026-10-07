"""Search service — application use-case for full-text search."""

from __future__ import annotations

from typing import TYPE_CHECKING

from harmonia.application.dto import SearchResult, SearchResultItem
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

    from harmonia.domain.ports.album_repository import AlbumRepository
    from harmonia.domain.ports.artist_repository import ArtistRepository
    from harmonia.domain.ports.search_repository import SearchRepository
    from harmonia.domain.ports.track_repository import TrackRepository


class SearchService:
    """Application service for searching the Harmonia music library."""

    def __init__(
        self,
        session: Session,
        search_repo: SearchRepository | None = None,
        track_repo: TrackRepository | None = None,
        artist_repo: ArtistRepository | None = None,
        album_repo: AlbumRepository | None = None,
    ) -> None:
        self._session = session
        self._search_repo = search_repo or SqliteSearchRepository(session)
        self._track_repo = track_repo or SqliteTrackRepository(session)
        self._artist_repo = artist_repo or SqliteArtistRepository(session)
        self._album_repo = album_repo or SqliteAlbumRepository(session)

    def search(self, query: str, limit: int = 50, offset: int = 0) -> SearchResult:
        """Search the library by title, artist, album, or genre."""
        matched_ids = self._search_repo.search(query, limit=limit, offset=offset)
        items: list[SearchResultItem] = []

        for track_id in matched_ids:
            track = self._track_repo.get_by_id(track_id)
            if track is None:
                continue

            artist_name = None
            if track.artist_id:
                artist = self._artist_repo.get_by_id(track.artist_id)
                if artist:
                    artist_name = artist.name

            album_title = None
            if track.album_id:
                album = self._album_repo.get_by_id(track.album_id)
                if album:
                    album_title = album.title

            files = self._track_repo.get_files(track.id)
            best_file = files[0] if files else None

            items.append(
                SearchResultItem(
                    track_id=track.id,
                    title=track.title,
                    artist_name=artist_name,
                    album_title=album_title,
                    duration_display=track.duration_display,
                    genre=track.genre,
                    year=track.year,
                    format=best_file.format.value.upper() if best_file else None,
                    quality_score=best_file.quality_score if best_file else None,
                )
            )

        return SearchResult(query=query, total_matches=len(items), items=items)

    def reindex(self) -> int:
        """Rebuild the entire search index from relational database tables."""
        count = self._search_repo.rebuild_index()
        self._session.commit()
        return count
