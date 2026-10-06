"""Integration tests for SQLite repository implementations."""

from __future__ import annotations

from sqlalchemy.orm import Session

from harmonia.core.types import TagSource
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.tag import Tag
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


def test_artist_repository_crud(session: Session) -> None:
    repo = SqliteArtistRepository(session)
    assert repo.count() == 0

    artist = Artist(name="Radiohead", sort_name="Radiohead")
    repo.add(artist)
    session.commit()

    assert repo.count() == 1
    fetched = repo.get_by_id(artist.id)
    assert fetched is not None
    assert fetched.name == "Radiohead"

    # Case-insensitive lookup
    found = repo.find_by_name("radiohead")
    assert found is not None
    assert found.id == artist.id

    # List all
    all_artists = repo.get_all()
    assert len(all_artists) == 1


def test_album_repository_crud(session: Session) -> None:
    artist_repo = SqliteArtistRepository(session)
    album_repo = SqliteAlbumRepository(session)

    artist = Artist(name="Daft Punk")
    artist_repo.add(artist)

    album = Album(
        title="Discovery",
        artist_id=artist.id,
        year=2001,
        total_tracks=14,
    )
    album_repo.add(album)
    session.commit()

    assert album_repo.count() == 1
    fetched = album_repo.get_by_id(album.id)
    assert fetched is not None
    assert fetched.title == "Discovery"

    # Find by title and artist
    found = album_repo.find_by_title_and_artist("discovery", artist.id)
    assert found is not None
    assert found.id == album.id

    # Get by artist
    by_artist = album_repo.get_by_artist(artist.id)
    assert len(by_artist) == 1
    assert by_artist[0].id == album.id


def test_track_repository_crud_with_files_and_tags(session: Session) -> None:
    track_repo = SqliteTrackRepository(session)

    track = Track(
        title="One More Time",
        track_number=1,
        duration_seconds=320.0,
        year=2001,
        genre="Electronic",
    )
    track_repo.add(track)

    tf = TrackFile(
        track_id=track.id,
        file_path="/music/daft_punk/01_one_more_time.mp3",
        file_size=8000000,
        format=AudioFormat.MP3,
        codec="mp3",
        bitrate=320,
        sample_rate=44100,
        channels=2,
    )
    track_repo.add_file(tf)

    tag = Tag(
        track_id=track.id,
        name="genre",
        value="Electronic",
        source=TagSource.FILE,
    )
    track_repo.add_tag(tag)
    session.commit()

    assert track_repo.count() == 1
    fetched_track = track_repo.get_by_id(track.id)
    assert fetched_track is not None
    assert fetched_track.title == "One More Time"

    # Check files
    assert track_repo.file_exists("/music/daft_punk/01_one_more_time.mp3") is True
    assert track_repo.file_exists("/music/nonexistent.mp3") is False

    files = track_repo.get_files(track.id)
    assert len(files) == 1
    assert files[0].file_path == "/music/daft_punk/01_one_more_time.mp3"

    file_by_path = track_repo.get_file_by_path("/music/daft_punk/01_one_more_time.mp3")
    assert file_by_path is not None
    assert file_by_path.id == tf.id

    # Check tags
    tags = track_repo.get_tags(track.id)
    assert len(tags) == 1
    assert tags[0].name == "genre"
    assert tags[0].value == "Electronic"

    # Delete track cascades
    track_repo.delete(track.id)
    session.commit()

    assert track_repo.count() == 0
    assert track_repo.get_files(track.id) == []
    assert track_repo.get_tags(track.id) == []
