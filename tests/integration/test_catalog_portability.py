"""Integration tests for catalog export and import round-trip portability."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import uuid4

from harmonia.application.catalog_import_service import CatalogImportService
from harmonia.application.export_service import CatalogExportService
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.core.types import TagSource
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.listening_event import ListeningEvent
from harmonia.domain.entities.metadata_field import MetadataFieldRecord
from harmonia.domain.entities.tag import Tag
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.listening_event_repo import SqliteListeningEventRepository
from harmonia.infrastructure.persistence.metadata_field_repo import SqliteMetadataFieldRepository
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from pathlib import Path


def test_catalog_export_import_round_trip(tmp_path: Path) -> None:
    # ── 1. Setup Source Database ──────────────────────────────────────────────
    db1_path = tmp_path / "source_library.db"
    engine1 = create_database_engine(db1_path)
    init_database(engine1)
    session_factory1 = create_session_factory(engine1)

    # Populate Source Database
    with session_factory1() as session1:
        artist_repo = SqliteArtistRepository(session1)
        album_repo = SqliteAlbumRepository(session1)
        track_repo = SqliteTrackRepository(session1)
        meta_repo = SqliteMetadataFieldRepository(session1)
        le_repo = SqliteListeningEventRepository(session1)

        artist1 = Artist(name="Radiohead", sort_name="Radiohead", mbid="a74b1b7f-71a5-4011-9441-d0b5e4122711")
        artist_repo.add(artist1)

        album1 = Album(
            title="OK Computer",
            artist_id=artist1.id,
            year=1997,
            total_tracks=12,
            total_discs=1,
            mbid="92911b3d-71b5-4122-8341-c0b5e4122722",
            art_path="/host/abs/path/art/cover.jpg",
            art_hash="11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff",
        )
        album_repo.add(album1)

        track1 = Track(
            title="Paranoid Android",
            artist_id=artist1.id,
            album_id=album1.id,
            track_number=2,
            disc_number=1,
            duration_seconds=383.0,
            year=1997,
            genre="Alternative Rock",
            mbid="55667788-71b5-4122-8341-c0b5e4122733",
            lyrics="Please could you stop the noise, I'm trying to get some rest...",
        )
        track_repo.add(track1)

        tf1 = TrackFile(
            track_id=track1.id,
            file_path="/media/music/radiohead/02_paranoid_android.flac",
            file_hash="aabbccddeeff11223344556677889900aabbccddeeff11223344556677889900",
            file_size=45000000,
            format=AudioFormat.FLAC,
            codec="flac",
            bitrate=950,
            sample_rate=44100,
            bit_depth=16,
            channels=2,
            is_lossless=True,
            quality_score=95,
        )
        track_repo.add_file(tf1)

        tag1 = Tag(track_id=track1.id, name="favorite", value="true", source=TagSource.USER)
        track_repo.add_tag(tag1)

        meta1 = MetadataFieldRecord(
            id=uuid4(),
            entity_type="track",
            entity_id=track1.id,
            field_name="genre",
            provider="user",
            raw_value="Art Rock",
            normalized_value="Art Rock",
            is_active=True,
            priority=100,
        )
        meta_repo.add(meta1)

        le1 = ListeningEvent(
            track_id=track1.id,
            track_file_id=tf1.id,
            timestamp_utc=datetime.now(UTC),
            duration_listened_seconds=383.0,
            track_duration_seconds=383.0,
            was_completed=True,
            playback_source="queue",
        )
        le_repo.add(le1)
        session1.commit()

    # ── 2. Export Database 1 to JSON Bundle ────────────────────────────────────
    export_bundle_path = tmp_path / "radiohead_catalog_export.json"
    with session_factory1() as session1:
        export_service = CatalogExportService(session1)
        saved_file = export_service.export_to_file(export_bundle_path)
        assert saved_file.exists()

    # Inspect JSON directly to verify machine paths exclusion
    raw_json = json.loads(export_bundle_path.read_text(encoding="utf-8"))
    assert raw_json["manifest"]["schema_version"] == "1.0.0"
    assert raw_json["manifest"]["entity_counts"]["tracks"] == 1
    # Confirm local machine art_path is not embedded in album bundle
    assert "art_path" not in raw_json["data"]["albums"][0]
    assert raw_json["data"]["albums"][0]["art_hash"] == album1.art_hash
    # Confirm local machine absolute directory is stripped from track_files
    assert raw_json["data"]["track_files"][0]["file_path"] == "02_paranoid_android.flac"
    assert "/" not in raw_json["data"]["track_files"][0]["file_path"]
    assert "\\" not in raw_json["data"]["track_files"][0]["file_path"]

    # ── 3. Setup Target Database ──────────────────────────────────────────────
    db2_path = tmp_path / "target_restored_library.db"
    engine2 = create_database_engine(db2_path)
    init_database(engine2)
    session_factory2 = create_session_factory(engine2)

    # ── 4. Import Bundle into Target Database ─────────────────────────────────
    with session_factory2() as session2:
        import_service = CatalogImportService(session2)
        result = import_service.import_bundle(export_bundle_path)

        assert result.dry_run is False
        assert result.artists_created == 1
        assert result.albums_created == 1
        assert result.tracks_created == 1
        assert result.track_files_created == 1
        assert result.tags_created == 1
        assert result.metadata_fields_created == 1
        assert result.listening_events_created == 1

    # ── 5. Verify Parity on Target Database ───────────────────────────────────
    with session_factory2() as session2:
        artist_repo2 = SqliteArtistRepository(session2)
        album_repo2 = SqliteAlbumRepository(session2)
        track_repo2 = SqliteTrackRepository(session2)
        le_repo2 = SqliteListeningEventRepository(session2)
        meta_repo2 = SqliteMetadataFieldRepository(session2)
        search_repo2 = SqliteSearchRepository(session2)

        # Artist
        restored_artist = artist_repo2.get_by_id(artist1.id)
        assert restored_artist is not None
        assert restored_artist.name == "Radiohead"
        assert restored_artist.mbid == "a74b1b7f-71a5-4011-9441-d0b5e4122711"

        # Album
        restored_album = album_repo2.get_by_id(album1.id)
        assert restored_album is not None
        assert restored_album.title == "OK Computer"
        assert restored_album.artist_id == artist1.id
        assert restored_album.art_hash == album1.art_hash

        # Track
        restored_track = track_repo2.get_by_id(track1.id)
        assert restored_track is not None
        assert restored_track.title == "Paranoid Android"
        assert restored_track.artist_id == artist1.id
        assert restored_track.album_id == album1.id
        assert restored_track.track_number == 2
        assert restored_track.duration_seconds == 383.0
        assert restored_track.lyrics == track1.lyrics

        # TrackFile
        restored_files = track_repo2.get_files(track1.id)
        assert len(restored_files) == 1
        assert restored_files[0].id == tf1.id
        assert restored_files[0].file_hash == tf1.file_hash
        assert restored_files[0].format == AudioFormat.FLAC
        assert restored_files[0].bitrate == 950
        assert "unlinked" in restored_files[0].file_path
        assert restored_files[0].verified_at is None

        # Tag
        restored_tags = track_repo2.get_tags(track1.id)
        assert len(restored_tags) == 1
        assert restored_tags[0].name == "favorite"
        assert restored_tags[0].value == "true"
        assert restored_tags[0].source == TagSource.USER

        # MetadataField Provenance
        restored_meta = meta_repo2.get_by_entity("track", track1.id)
        assert len(restored_meta) == 1
        assert restored_meta[0].raw_value == "Art Rock"
        assert restored_meta[0].provider == "user"

        # ListeningEvent
        restored_events = le_repo2.list_events(track_id=track1.id)
        assert len(restored_events) == 1
        assert restored_events[0].duration_listened_seconds == 383.0
        assert restored_events[0].was_completed is True

        # Search index test
        search_results = search_repo2.search("Paranoid")
        assert len(search_results) == 1
        assert search_results[0] == track1.id
