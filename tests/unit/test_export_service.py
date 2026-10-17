"""Unit tests for CatalogExportService."""

from __future__ import annotations

import json
from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import uuid4

import pytest

from harmonia.application.export_service import CatalogExportService
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
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from pathlib import Path

    from sqlalchemy.orm import Session


def test_export_empty_library(session: Session) -> None:
    service = CatalogExportService(session)
    bundle = service.build_bundle()

    assert bundle.manifest.schema_version == "1.0.0"
    assert bundle.manifest.entity_counts["tracks"] == 0
    assert bundle.manifest.entity_counts["albums"] == 0
    assert bundle.manifest.entity_counts["artists"] == 0
    assert len(bundle.data.tracks) == 0
    assert len(bundle.checksum) == 64


def test_export_populated_library(session: Session) -> None:
    artist_repo = SqliteArtistRepository(session)
    album_repo = SqliteAlbumRepository(session)
    track_repo = SqliteTrackRepository(session)
    meta_repo = SqliteMetadataFieldRepository(session)
    le_repo = SqliteListeningEventRepository(session)

    # 1. Artist & Album
    artist = Artist(name="Massive Attack", sort_name="Massive Attack")
    artist_repo.add(artist)

    album = Album(title="Mezzanine", artist_id=artist.id, year=1998, art_hash="hash_art_123")
    album_repo.add(album)

    # 2. Track & TrackFile & Tag
    track = Track(title="Teardrop", artist_id=artist.id, album_id=album.id, track_number=3, duration_seconds=330.0)
    track_repo.add(track)

    tf = TrackFile(
        track_id=track.id,
        file_path="/media/music/teardrop.flac",
        file_size=35000000,
        format=AudioFormat.FLAC,
        codec="flac",
        bitrate=900,
        sample_rate=44100,
        channels=2,
        is_lossless=True,
    )
    track_repo.add_file(tf)

    tag = Tag(track_id=track.id, name="mood", value="chill", source=TagSource.USER)
    track_repo.add_tag(tag)

    # 3. Provenance & Listening event
    meta = MetadataFieldRecord(
        id=uuid4(),
        entity_type="track",
        entity_id=track.id,
        field_name="genre",
        provider="user",
        raw_value="Trip Hop",
        normalized_value="Trip Hop",
        is_active=True,
        priority=100,
    )
    meta_repo.add(meta)

    le = ListeningEvent(
        track_id=track.id,
        track_file_id=tf.id,
        timestamp_utc=datetime.now(UTC),
        duration_listened_seconds=330.0,
        track_duration_seconds=330.0,
        was_completed=True,
    )
    le_repo.add(le)
    session.commit()

    # Export
    service = CatalogExportService(session)
    bundle = service.build_bundle(include_telemetry=True)

    assert bundle.manifest.entity_counts["artists"] == 1
    assert bundle.manifest.entity_counts["albums"] == 1
    assert bundle.manifest.entity_counts["tracks"] == 1
    assert bundle.manifest.entity_counts["track_files"] == 1
    assert bundle.manifest.entity_counts["tags"] == 1
    assert bundle.manifest.entity_counts["metadata_fields"] == 1
    assert bundle.manifest.entity_counts["listening_events"] == 1

    assert bundle.data.artists[0].id == artist.id
    assert bundle.data.albums[0].art_hash == "hash_art_123"
    assert bundle.data.tracks[0].title == "Teardrop"
    assert bundle.data.tags[0].name == "mood"
    assert bundle.data.listening_events[0].was_completed is True


def test_export_without_telemetry(session: Session) -> None:
    track_repo = SqliteTrackRepository(session)
    le_repo = SqliteListeningEventRepository(session)

    track = Track(title="Angel", duration_seconds=379.0)
    track_repo.add(track)

    le = ListeningEvent(
        track_id=track.id,
        timestamp_utc=datetime.now(UTC),
        duration_listened_seconds=100.0,
        track_duration_seconds=379.0,
    )
    le_repo.add(le)
    session.commit()

    service = CatalogExportService(session)
    bundle = service.build_bundle(include_telemetry=False)

    assert len(bundle.data.tracks) == 1
    assert len(bundle.data.listening_events) == 0
    assert bundle.manifest.entity_counts["listening_events"] == 0


def test_export_to_file_and_overwrite_protection(session: Session, tmp_path: Path) -> None:
    track_repo = SqliteTrackRepository(session)
    track_repo.add(Track(title="Karmacoma"))
    session.commit()

    service = CatalogExportService(session)
    export_file = tmp_path / "exports" / "catalog.json"

    # 1. Normal export creates file
    saved_path = service.export_to_file(export_file)
    assert saved_path == export_file
    assert export_file.exists()

    # Verify JSON content
    data = json.loads(export_file.read_text(encoding="utf-8"))
    assert "manifest" in data
    assert "data" in data
    assert "checksum" in data

    # 2. Existing file without force raises FileExistsError
    with pytest.raises(FileExistsError) as exc_info:
        service.export_to_file(export_file, force=False)
    assert "already exists" in str(exc_info.value)

    # 3. Existing file with force overwrites cleanly
    saved_path_forced = service.export_to_file(export_file, force=True)
    assert saved_path_forced.exists()


def test_export_never_serializes_absolute_paths(session: Session) -> None:
    track_repo = SqliteTrackRepository(session)
    track1 = Track(title="Dissolved Girl")
    track2 = Track(title="Exchange")
    track_repo.add(track1)
    track_repo.add(track2)

    # POSIX absolute path
    tf1 = TrackFile(
        track_id=track1.id,
        file_path="/var/music/massive_attack/06_dissolved_girl.flac",
        file_size=25000000,
        format=AudioFormat.FLAC,
    )
    # Windows absolute path with drive letter and backslashes
    tf2 = TrackFile(
        track_id=track2.id,
        file_path=r"D:\Audio\Library\Massive Attack\09_exchange.mp3",
        file_size=8000000,
        format=AudioFormat.MP3,
    )
    track_repo.add_file(tf1)
    track_repo.add_file(tf2)
    session.commit()

    service = CatalogExportService(session)
    bundle = service.build_bundle()

    assert len(bundle.data.track_files) == 2
    paths = {tf.file_path for tf in bundle.data.track_files}
    assert "06_dissolved_girl.flac" in paths
    assert "09_exchange.mp3" in paths
    for p in paths:
        assert p is not None
        assert "/" not in p
        assert "\\" not in p
        assert ":" not in p


def test_extract_portable_filename_os_independence() -> None:
    from harmonia.application.export_service import extract_portable_filename

    # Windows paths
    assert extract_portable_filename(r"C:\Music\Albums\track.flac") == "track.flac"
    assert extract_portable_filename(r"\\Server\Share\Music\track.mp3") == "track.mp3"
    assert extract_portable_filename(r"D:\track.opus") == "track.opus"

    # POSIX paths
    assert extract_portable_filename("/home/user/music/track.flac") == "track.flac"
    assert extract_portable_filename("/media/external/song.ogg") == "song.ogg"

    # Relative and mixed paths
    assert extract_portable_filename("albums/rock/track.flac") == "track.flac"
    assert extract_portable_filename(r"albums\rock\track.flac") == "track.flac"
    assert extract_portable_filename("../../track.flac") == "track.flac"
    assert extract_portable_filename(r"..\..\track.flac") == "track.flac"

    # Edge cases
    assert extract_portable_filename(None) is None
    assert extract_portable_filename("") is None
    assert extract_portable_filename("..") is None
    assert extract_portable_filename("/") is None
    assert extract_portable_filename("\\") is None
    assert extract_portable_filename("C:") is None
    assert extract_portable_filename("C:\\") is None
    assert extract_portable_filename("C:track.flac") == "track.flac"
