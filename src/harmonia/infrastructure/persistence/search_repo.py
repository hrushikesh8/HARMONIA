"""SQLite FTS5 full-text search repository implementation."""

from __future__ import annotations

import re
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import text

from harmonia.core.logging import get_logger

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

logger = get_logger(__name__)


def sanitize_fts_query(raw_query: str) -> str:
    """Sanitize and format user query for SQLite FTS5 MATCH syntax.

    Supports prefix matching on individual terms while preventing FTS syntax errors.
    """
    cleaned = raw_query.strip()
    if not cleaned:
        return ""

    # Split into words, strip FTS syntax characters
    tokens = re.findall(r"\w+", cleaned)
    if not tokens:
        return ""

    # Format each token as a prefix match, e.g. "term"*
    return " ".join(f'"{t}"*' for t in tokens)


class SqliteSearchRepository:
    """SQLite FTS5 implementation of the SearchRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def index_track(
        self,
        track_id: UUID,
        title: str,
        artist_name: str | None,
        album_title: str | None,
        genre: str | None,
    ) -> None:
        """Add or update a track in the FTS index."""
        track_id_str = str(track_id)
        # Remove any existing entry first
        self.remove_track(track_id)

        stmt = text(
            "INSERT INTO tracks_fts (track_id, title, artist_name, album_title, genre) "
            "VALUES (:track_id, :title, :artist_name, :album_title, :genre)"
        )
        self._session.execute(
            stmt,
            {
                "track_id": track_id_str,
                "title": title or "",
                "artist_name": artist_name or "",
                "album_title": album_title or "",
                "genre": genre or "",
            },
        )
        self._session.flush()

    def remove_track(self, track_id: UUID) -> None:
        """Remove a track from the FTS index."""
        stmt = text("DELETE FROM tracks_fts WHERE track_id = :track_id")
        self._session.execute(stmt, {"track_id": str(track_id)})
        self._session.flush()

    def search(self, query: str, limit: int = 50, offset: int = 0) -> list[UUID]:
        """Search tracks matching the query, returning track UUIDs in rank order."""
        fts_query = sanitize_fts_query(query)
        if not fts_query:
            return []

        stmt = text(
            "SELECT track_id FROM tracks_fts WHERE tracks_fts MATCH :query "
            "ORDER BY rank LIMIT :limit OFFSET :offset"
        )
        try:
            rows = self._session.execute(
                stmt, {"query": fts_query, "limit": limit, "offset": offset}
            ).fetchall()
            return [UUID(r[0]) for r in rows]
        except Exception as e:
            logger.warning("FTS search query failed for %r: %s", query, e)
            return []

    def clear(self) -> None:
        """Clear all entries in the search index."""
        self._session.execute(text("DELETE FROM tracks_fts"))
        self._session.flush()

    def rebuild_index(self) -> int:
        """Rebuild the entire search index from tracks, artists, and albums."""
        self.clear()
        stmt = text(
            "INSERT INTO tracks_fts (track_id, title, artist_name, album_title, genre) "
            "SELECT t.id, t.title, ar.name, al.title, t.genre "
            "FROM tracks t "
            "LEFT JOIN artists ar ON t.artist_id = ar.id "
            "LEFT JOIN albums al ON t.album_id = al.id"
        )
        result = self._session.execute(stmt)
        self._session.flush()
        return result.rowcount or 0
