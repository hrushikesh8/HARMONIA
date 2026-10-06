"""Integration tests for ImportService and LibraryService pipeline."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import patch
from uuid import UUID

from sqlalchemy.orm import Session

from harmonia.application.import_service import ImportService
from harmonia.application.library_service import LibraryService
from harmonia.core.events import ImportCompleted, TrackImported, event_bus
from harmonia.infrastructure.audio.mutagen_reader import ExtractedMetadata


def test_import_and_query_pipeline(session: Session, temp_dir: Path) -> None:
    # 1. Create simulated music directory structure
    music_dir = temp_dir / "Queen" / "A Night at the Opera"
    music_dir.mkdir(parents=True)
    f1 = music_dir / "01 - Bohemian Rhapsody.flac"
    f2 = music_dir / "02 - Youre My Best Friend.flac"
    f1.write_bytes(b"FLAC dummy content 1")
    f2.write_bytes(b"FLAC dummy content 2")

    # Mock metadata extraction for these files
    def fake_extract(path: Path) -> ExtractedMetadata:
        if "Bohemian" in path.name:
            return ExtractedMetadata(
                title="Bohemian Rhapsody",
                artist="Queen",
                album="A Night at the Opera",
                track_number=1,
                year=1975,
                genre="Rock",
                duration_seconds=354.0,
                sample_rate=44100,
                bit_depth=16,
                channels=2,
                is_lossless=True,
                codec="flac",
            )
        else:
            return ExtractedMetadata(
                title="You're My Best Friend",
                artist="Queen",
                album="A Night at the Opera",
                track_number=2,
                year=1975,
                genre="Rock",
                duration_seconds=172.0,
                sample_rate=44100,
                bit_depth=16,
                channels=2,
                is_lossless=True,
                codec="flac",
            )

    events_received: list[str] = []

    def on_track_imported(event: TrackImported) -> None:
        events_received.append(f"track:{Path(event.file_path).name}")

    def on_import_completed(event: ImportCompleted) -> None:
        events_received.append(f"completed:{event.total_imported}")

    event_bus.subscribe(TrackImported, on_track_imported)
    event_bus.subscribe(ImportCompleted, on_import_completed)

    try:
        # 2. Run ImportService
        with patch("harmonia.application.import_service.extract_metadata", side_effect=fake_extract):
            importer = ImportService(
                session=session,
                supported_formats=[".flac"],
                compute_hashes=True,
            )
            result = importer.import_directory(temp_dir)

            assert result.total_scanned == 2
            assert result.imported == 2
            assert result.skipped == 0
            assert result.failed == 0

            # 3. Idempotency test — running again should skip already imported files
            result2 = importer.import_directory(temp_dir)
            assert result2.total_scanned == 2
            assert result2.imported == 0
            assert result2.skipped == 2
            assert result2.failed == 0

        # Verify events
        assert "track:01 - Bohemian Rhapsody.flac" in events_received
        assert "completed:2" in events_received

        # 4. Query via LibraryService
        lib = LibraryService(session)
        summary = lib.get_summary()
        assert summary.total_tracks == 2
        assert summary.total_albums == 1
        assert summary.total_artists == 1

        # Check track list
        tracks = lib.list_tracks()
        assert len(tracks) == 2
        titles = [t.title for t in tracks]
        assert "Bohemian Rhapsody" in titles
        assert "You're My Best Friend" in titles

        # Find Bohemian Rhapsody id
        boh_summary = next(t for t in tracks if t.title == "Bohemian Rhapsody")
        boh_detail = lib.get_track(boh_summary.id)
        assert boh_detail is not None
        assert boh_detail.artist_name == "Queen"
        assert boh_detail.album_title == "A Night at the Opera"
        assert boh_detail.year == 1975
        assert boh_detail.duration_display == "5:54"
        assert len(boh_detail.files) == 1
        assert boh_detail.files[0].is_lossless is True
        assert boh_detail.files[0].quality_score == 65
        assert len(boh_detail.tags) >= 1

        # Check album
        albums = lib.list_albums()
        assert len(albums) == 1
        assert albums[0].title == "A Night at the Opera"
        assert albums[0].artist_name == "Queen"

        album_detail = lib.get_album(albums[0].id)
        assert album_detail is not None
        assert album_detail.track_count == 2
        assert len(album_detail.tracks) == 2

        # Check artist
        artists = lib.list_artists()
        assert len(artists) == 1
        assert artists[0].name == "Queen"

        artist_detail = lib.get_artist(artists[0].id)
        assert artist_detail is not None
        assert artist_detail.album_count == 1
        assert artist_detail.albums[0].title == "A Night at the Opera"

    finally:
        event_bus.unsubscribe(TrackImported, on_track_imported)
        event_bus.unsubscribe(ImportCompleted, on_import_completed)
