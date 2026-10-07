"""Unit tests for SearchService."""

from unittest.mock import MagicMock
from uuid import uuid4

from harmonia.application.search_service import SearchService
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat


def test_search_service_flow() -> None:
    session = MagicMock()
    search_repo = MagicMock()
    track_repo = MagicMock()
    artist_repo = MagicMock()
    album_repo = MagicMock()

    service = SearchService(
        session=session,
        search_repo=search_repo,
        track_repo=track_repo,
        artist_repo=artist_repo,
        album_repo=album_repo,
    )

    track_id = uuid4()
    artist_id = uuid4()
    album_id = uuid4()

    search_repo.search.return_value = [track_id]
    track_repo.get_by_id.return_value = Track(
        id=track_id,
        title="Heroes",
        artist_id=artist_id,
        album_id=album_id,
        duration_seconds=367.0,
        genre="Art Rock",
        year=1977,
    )
    artist_repo.get_by_id.return_value = Artist(id=artist_id, name="David Bowie")
    album_repo.get_by_id.return_value = Album(id=album_id, title='"Heroes"')
    track_repo.get_files.return_value = [
        TrackFile(
            track_id=track_id,
            file_path="/music/heroes.flac",
            file_size=20000000,
            format=AudioFormat.FLAC,
            quality_score=95,
        )
    ]

    result = service.search("heroes")
    assert result.query == "heroes"
    assert result.total_matches == 1
    assert len(result.items) == 1

    item = result.items[0]
    assert item.track_id == track_id
    assert item.title == "Heroes"
    assert item.artist_name == "David Bowie"
    assert item.album_title == '"Heroes"'
    assert item.format == "FLAC"
    assert item.quality_score == 95


def test_search_service_reindex() -> None:
    session = MagicMock()
    search_repo = MagicMock()
    search_repo.rebuild_index.return_value = 42

    service = SearchService(session=session, search_repo=search_repo)
    count = service.reindex()

    assert count == 42
    search_repo.rebuild_index.assert_called_once()
    session.commit.assert_called_once()
