"""Integration tests for SQLite FTS5 search repository."""

from uuid import uuid4

from sqlalchemy.orm import Session

from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.search_repo import (
    SqliteSearchRepository,
    sanitize_fts_query,
)
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


def test_sanitize_fts_query() -> None:
    assert sanitize_fts_query("The Beatles") == '"The"* "Beatles"*'
    assert sanitize_fts_query("AC/DC") == '"AC"* "DC"*'
    assert sanitize_fts_query("   ") == ""
    assert sanitize_fts_query('special "quotes" AND OR') == '"special"* "quotes"* "AND"* "OR"*'


def test_search_index_and_query(session: Session) -> None:
    search_repo = SqliteSearchRepository(session)
    track_id1 = uuid4()
    track_id2 = uuid4()

    search_repo.index_track(
        track_id=track_id1,
        title="Comfortably Numb",
        artist_name="Pink Floyd",
        album_title="The Wall",
        genre="Progressive Rock",
    )
    search_repo.index_track(
        track_id=track_id2,
        title="Paranoid Android",
        artist_name="Radiohead",
        album_title="OK Computer",
        genre="Alternative Rock",
    )

    # Search by title
    res_title = search_repo.search("Comfortably")
    assert len(res_title) == 1
    assert res_title[0] == track_id1

    # Search by artist
    res_artist = search_repo.search("Radiohead")
    assert len(res_artist) == 1
    assert res_artist[0] == track_id2

    # Search by album
    res_album = search_repo.search("OK Computer")
    assert len(res_album) == 1
    assert res_album[0] == track_id2

    # Search by genre
    res_genre = search_repo.search("Progressive")
    assert len(res_genre) == 1
    assert res_genre[0] == track_id1

    # Prefix search
    res_prefix = search_repo.search("Pink")
    assert len(res_prefix) == 1
    assert res_prefix[0] == track_id1


def test_search_diacritics(session: Session) -> None:
    search_repo = SqliteSearchRepository(session)
    track_id = uuid4()
    search_repo.index_track(
        track_id=track_id,
        title="Halo",
        artist_name="Beyoncé",
        album_title="I Am... Sasha Fierce",
        genre="Pop",
    )

    # Search without accent
    results = search_repo.search("Beyonce")
    assert len(results) == 1
    assert results[0] == track_id


def test_search_remove_and_update(session: Session) -> None:
    search_repo = SqliteSearchRepository(session)
    track_id = uuid4()
    search_repo.index_track(
        track_id=track_id,
        title="Original Title",
        artist_name="Artist",
        album_title="Album",
        genre="Rock",
    )
    assert len(search_repo.search("Original")) == 1

    # Update
    search_repo.index_track(
        track_id=track_id,
        title="Updated Title",
        artist_name="Artist",
        album_title="Album",
        genre="Rock",
    )
    assert len(search_repo.search("Original")) == 0
    assert len(search_repo.search("Updated")) == 1

    # Remove
    search_repo.remove_track(track_id)
    assert len(search_repo.search("Updated")) == 0


def test_rebuild_index(session: Session) -> None:
    artist_repo = SqliteArtistRepository(session)
    album_repo = SqliteAlbumRepository(session)
    track_repo = SqliteTrackRepository(session)
    search_repo = SqliteSearchRepository(session)

    artist = Artist(name="Led Zeppelin")
    artist_repo.add(artist)

    album = Album(title="Led Zeppelin IV", artist_id=artist.id)
    album_repo.add(album)

    track = Track(
        title="Stairway to Heaven",
        artist_id=artist.id,
        album_id=album.id,
        genre="Classic Rock",
    )
    track_repo.add(track)

    search_repo.clear()
    assert len(search_repo.search("Stairway")) == 0

    indexed_count = search_repo.rebuild_index()
    assert indexed_count == 1
    results = search_repo.search("Stairway")
    assert len(results) == 1
    assert results[0] == track.id
