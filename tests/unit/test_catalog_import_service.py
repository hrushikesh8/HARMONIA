"""Unit tests for CatalogImportService and safe import semantics."""

from __future__ import annotations

from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING
from unittest.mock import patch
from uuid import uuid4

import pytest
from sqlalchemy import select

from harmonia import __version__
from harmonia.application.catalog_dto import (
    AlbumBundleItem,
    ArtistBundleItem,
    CatalogBundle,
    CatalogData,
    CatalogManifest,
    ListeningEventBundleItem,
    MetadataFieldBundleItem,
    TagBundleItem,
    TrackBundleItem,
    TrackFileBundleItem,
    compute_data_checksum,
)
from harmonia.application.catalog_import_service import (
    CatalogImportService,
    sanitize_unlinked_filename,
)
from harmonia.application.import_service import ImportService
from harmonia.core.exceptions import (
    CatalogConflictError,
    CatalogIntegrityError,
    CatalogValidationError,
    CatalogVersionError,
)
from harmonia.domain.values.playback import LocalFileSource
from harmonia.infrastructure.audio.backend import DesktopAudioBackend
from harmonia.infrastructure.audio.mutagen_reader import ExtractedMetadata, compute_file_hash
from harmonia.infrastructure.persistence.models import (
    AlbumModel,
    ArtistModel,
    ListeningEventModel,
    MetadataFieldModel,
    TrackFileModel,
    TrackModel,
)

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


def _make_sample_bundle(
    artist_name: str = "Portishead",
    album_title: str = "Dummy",
    track_title: str = "Mysterons",
    schema_version: str = "1.0.0",
) -> CatalogBundle:
    artist_id = uuid4()
    album_id = uuid4()
    track_id = uuid4()
    tf_id = uuid4()
    tag_id = uuid4()
    le_id = uuid4()

    artists = [ArtistBundleItem(id=artist_id, name=artist_name, sort_name=artist_name)]
    albums = [AlbumBundleItem(id=album_id, title=album_title, artist_id=artist_id, year=1994)]
    tracks = [
        TrackBundleItem(
            id=track_id,
            title=track_title,
            artist_id=artist_id,
            album_id=album_id,
            track_number=1,
            duration_seconds=306.0,
            genre="Trip Hop",
        )
    ]
    track_files = [
        TrackFileBundleItem(
            id=tf_id,
            track_id=track_id,
            file_hash="c" * 64,
            file_size=20000000,
            format="FLAC",
            file_path="/media/music/portishead/01_mysterons.flac",
        )
    ]
    tags = [TagBundleItem(id=tag_id, track_id=track_id, name="mood", value="dark", source="user")]
    listening_events = [
        ListeningEventBundleItem(
            id=le_id,
            track_id=track_id,
            timestamp_utc=datetime.now(UTC),
            duration_listened_seconds=306.0,
            was_completed=True,
        )
    ]

    data = CatalogData(
        artists=artists,
        albums=albums,
        tracks=tracks,
        track_files=track_files,
        tags=tags,
        listening_events=listening_events,
    )
    manifest = CatalogManifest(
        schema_version=schema_version,
        harmonia_version=__version__,
        exported_at=datetime.now(UTC),
        source_system="harmonia-desktop",
        entity_counts={"artists": 1, "albums": 1, "tracks": 1},
    )
    checksum = compute_data_checksum(data)
    return CatalogBundle(manifest=manifest, data=data, checksum=checksum)


def test_import_bundle_success(session: Session) -> None:
    bundle = _make_sample_bundle()
    service = CatalogImportService(session)

    result = service.import_bundle(bundle.model_dump(mode="json"))

    assert result.dry_run is False
    assert result.artists_created == 1
    assert result.albums_created == 1
    assert result.tracks_created == 1
    assert result.track_files_created == 1
    assert result.tags_created == 1
    assert result.listening_events_created == 1

    # Verify persisted in database
    artist = session.scalar(select(ArtistModel).where(ArtistModel.name == "Portishead"))
    assert artist is not None
    assert artist.id == bundle.data.artists[0].id

    track = session.scalar(select(TrackModel).where(TrackModel.title == "Mysterons"))
    assert track is not None
    assert track.album_id == bundle.data.albums[0].id

    le = session.scalar(select(ListeningEventModel).where(ListeningEventModel.track_id == track.id))
    assert le is not None
    assert le.was_completed is True


def test_import_checksum_mismatch_raises_integrity_error(session: Session) -> None:
    bundle = _make_sample_bundle()
    bundle_dict = bundle.model_dump(mode="json")
    bundle_dict["checksum"] = "0" * 64  # Corrupt the integrity digest

    service = CatalogImportService(session)
    with pytest.raises(CatalogIntegrityError) as exc_info:
        service.import_bundle(bundle_dict)

    assert "integrity check failed" in str(exc_info.value).lower()
    # Confirm zero database modifications
    assert session.scalar(select(ArtistModel)) is None


def test_import_unsupported_version_raises(session: Session) -> None:
    bundle = _make_sample_bundle(schema_version="2.0.0")
    bundle_dict = bundle.model_dump(mode="json")
    # Recompute checksum with new version in manifest
    bundle_dict["checksum"] = compute_data_checksum(bundle_dict["data"])

    service = CatalogImportService(session)
    with pytest.raises(CatalogVersionError) as exc_info:
        service.import_bundle(bundle_dict)

    assert "Unsupported catalog schema version '2.0.0'" in str(exc_info.value)


def test_import_malformed_json_raises_validation_error(session: Session, tmp_path: Path) -> None:
    bad_file = tmp_path / "corrupt.json"
    bad_file.write_text("{ this is not valid JSON !!!", encoding="utf-8")

    service = CatalogImportService(session)
    with pytest.raises(CatalogValidationError) as exc_info:
        service.import_bundle(bad_file)

    assert "Malformed JSON" in str(exc_info.value)


def test_import_missing_envelope_keys_raises(session: Session) -> None:
    service = CatalogImportService(session)
    with pytest.raises(CatalogValidationError) as exc_info:
        service.import_bundle({"data": {}})

    assert "Missing required keys" in str(exc_info.value)


def test_import_missing_entity_reference_raises(session: Session) -> None:
    bundle = _make_sample_bundle()
    # Point track to non-existent artist and album
    bundle.data.tracks[0].artist_id = uuid4()
    bundle.checksum = compute_data_checksum(bundle.data)
    bundle_dict = bundle.model_dump(mode="json")

    service = CatalogImportService(session)
    with pytest.raises(CatalogValidationError) as exc_info:
        service.import_bundle(bundle_dict)

    assert "references non-existent artist_id" in str(exc_info.value)


def test_import_conflict_policy_skip(session: Session) -> None:
    bundle = _make_sample_bundle()
    service = CatalogImportService(session)

    # Initial import
    res1 = service.import_bundle(bundle.model_dump(mode="json"))
    assert res1.tracks_created == 1

    # Second import with on_conflict="skip"
    res2 = service.import_bundle(bundle.model_dump(mode="json"), on_conflict="skip")
    assert res2.tracks_created == 0
    assert res2.tracks_skipped == 1
    assert res2.artists_skipped == 1
    assert res2.albums_skipped == 1


def test_import_conflict_policy_replace(session: Session) -> None:
    bundle = _make_sample_bundle(artist_name="Portishead", track_title="Mysterons")
    service = CatalogImportService(session)
    service.import_bundle(bundle.model_dump(mode="json"))

    # Modify title in bundle
    bundle.data.tracks[0].title = "Mysterons (Live at Roseland NYC)"
    bundle.checksum = compute_data_checksum(bundle.data)

    res_replace = service.import_bundle(bundle.model_dump(mode="json"), on_conflict="replace")
    assert res_replace.tracks_updated == 1

    # Verify track in DB was updated
    track = session.scalar(select(TrackModel).where(TrackModel.id == bundle.data.tracks[0].id))
    assert track is not None
    assert track.title == "Mysterons (Live at Roseland NYC)"


def test_import_conflict_policy_fail_raises_and_rolls_back(session: Session) -> None:
    bundle = _make_sample_bundle()
    service = CatalogImportService(session)
    service.import_bundle(bundle.model_dump(mode="json"))

    # Re-import with on_conflict="fail"
    with pytest.raises(CatalogConflictError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"), on_conflict="fail")

    assert "already exists" in str(exc_info.value)


def test_import_dry_run_does_not_mutate_db(session: Session) -> None:
    bundle = _make_sample_bundle()
    service = CatalogImportService(session)

    result = service.import_bundle(bundle.model_dump(mode="json"), dry_run=True)

    assert result.dry_run is True
    assert result.tracks_created == 1

    # Confirm database is completely untouched
    assert session.scalar(select(TrackModel)) is None
    assert session.scalar(select(ArtistModel)) is None


def test_import_never_modifies_source_audio_files(session: Session, tmp_path: Path) -> None:
    # Create a real dummy audio file on disk
    audio_file = tmp_path / "real_song.flac"
    audio_content = b"ORIGINAL_FLAC_AUDIO_BYTES_TEST_12345"
    audio_file.write_bytes(audio_content)
    orig_mtime = audio_file.stat().st_mtime_ns

    bundle = _make_sample_bundle()
    bundle.data.track_files[0].file_path = str(audio_file)
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    service.import_bundle(bundle.model_dump(mode="json"))

    # Verify audio file on disk was not modified, renamed, or touched
    assert audio_file.exists()
    assert audio_file.read_bytes() == audio_content
    assert audio_file.stat().st_mtime_ns == orig_mtime


def test_import_cannot_silently_bind_unrelated_file_at_coincidental_path(
    session: Session, tmp_path: Path
) -> None:
    # 1. Create a local file with arbitrary bytes at a path matching the bundle's filename hint
    local_file = tmp_path / "song.flac"
    local_file.write_bytes(b"LOCAL_DIFFERENT_UNRELATED_BYTES_999")

    bundle = _make_sample_bundle()
    bundle.data.track_files[0].file_path = str(local_file)
    bundle.data.track_files[0].file_hash = "a" * 64  # Stream hash from original export host
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    result = service.import_bundle(bundle.model_dump(mode="json"))

    # Assert TrackFile was NOT linked to the local file because hashes mismatch
    tf = session.scalar(select(TrackFileModel).where(TrackFileModel.id == bundle.data.track_files[0].id))
    assert tf is not None
    assert tf.file_path != str(local_file.resolve())
    assert "unlinked" in tf.file_path
    assert tf.verified_at is None
    # Diagnostic warning must be appended
    assert any("does not match imported stream hash" in w for w in result.warnings)
    # The local file content remains pristine
    assert local_file.read_bytes() == b"LOCAL_DIFFERENT_UNRELATED_BYTES_999"


def test_import_binds_verified_matching_file(session: Session, tmp_path: Path) -> None:
    # 1. Create a local file whose SHA-256 strictly matches the bundle's file_hash
    matching_file = tmp_path / "01_mysterons.flac"
    file_bytes = b"VERIFIED_AUTHENTIC_AUDIO_STREAM_BYTES"
    matching_file.write_bytes(file_bytes)
    actual_hash = compute_file_hash(matching_file)

    bundle = _make_sample_bundle()
    bundle.data.track_files[0].file_path = str(matching_file)
    bundle.data.track_files[0].file_hash = actual_hash
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    result = service.import_bundle(bundle.model_dump(mode="json"))

    # Assert TrackFile was safely associated to the verified local file
    tf = session.scalar(select(TrackFileModel).where(TrackFileModel.id == bundle.data.track_files[0].id))
    assert tf is not None
    assert tf.file_path == str(matching_file.resolve())
    assert tf.verified_at is not None
    assert result.track_files_created == 1


def test_import_missing_media_safely_represented(session: Session) -> None:
    bundle = _make_sample_bundle()
    # Path does not exist on disk
    bundle.data.track_files[0].file_path = "non_existent_file.flac"
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    result = service.import_bundle(bundle.model_dump(mode="json"))

    tf = session.scalar(select(TrackFileModel).where(TrackFileModel.id == bundle.data.track_files[0].id))
    assert tf is not None
    assert "unlinked" in tf.file_path
    assert tf.verified_at is None
    assert any("not found on this host" in w for w in result.warnings)


def test_import_metadata_field_referential_integrity(session: Session) -> None:
    bundle = _make_sample_bundle()
    # Add a metadata field pointing to a non-existent track
    ghost_track_id = uuid4()
    bundle.data.metadata_fields.append(
        MetadataFieldBundleItem(
            id=uuid4(),
            entity_type="track",
            entity_id=ghost_track_id,
            field_name="title",
            provider="user",
            raw_value="Ghost",
            normalized_value="Ghost",
            is_active=True,
            priority=100,
        )
    )
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    with pytest.raises(CatalogValidationError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"))

    assert "references non-existent track_id" in str(exc_info.value)
    # Zero records persisted
    assert session.scalar(select(MetadataFieldModel)) is None


def test_import_metadata_field_unsupported_entity_type_raises(session: Session) -> None:
    bundle = _make_sample_bundle()
    # Add a metadata field with unsupported entity type
    bundle.data.metadata_fields.append(
        MetadataFieldBundleItem(
            id=uuid4(),
            entity_type="playlist",  # Unsupported
            entity_id=bundle.data.tracks[0].id,
            field_name="mood",
            provider="user",
            raw_value="Dark",
            normalized_value="Dark",
        )
    )
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    with pytest.raises(CatalogValidationError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"))

    assert "unsupported entity_type 'playlist'" in str(exc_info.value)


def test_import_secondary_unique_mbid_collision_raises(session: Session) -> None:
    # Seed DB with an artist having a specific MBID
    shared_mbid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
    existing_artist = ArtistModel(
        id=uuid4(),
        name="Existing Artist",
        mbid=shared_mbid,
    )
    session.add(existing_artist)
    session.commit()

    # Create bundle with a new artist UUID but identical MBID
    bundle = _make_sample_bundle()
    new_artist_id = uuid4()
    bundle.data.artists[0].id = new_artist_id
    bundle.data.albums[0].artist_id = new_artist_id
    bundle.data.tracks[0].artist_id = new_artist_id
    bundle.data.artists[0].name = "New Colliding Artist"
    bundle.data.artists[0].mbid = shared_mbid
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    with pytest.raises(CatalogConflictError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"), on_conflict="fail")

    assert "MusicBrainz ID" in str(exc_info.value)
    # Confirm new artist was NOT created
    assert session.scalar(select(ArtistModel).where(ArtistModel.name == "New Colliding Artist")) is None


def test_import_secondary_unique_mbid_collision_dry_run(session: Session) -> None:
    shared_mbid = "b2c3d4e5-f6a7-8901-bcde-fa2345678901"
    existing_artist = ArtistModel(
        id=uuid4(),
        name="Existing Artist 2",
        mbid=shared_mbid,
    )
    session.add(existing_artist)
    session.commit()

    bundle = _make_sample_bundle()
    new_artist_id = uuid4()
    bundle.data.artists[0].id = new_artist_id
    bundle.data.albums[0].artist_id = new_artist_id
    bundle.data.tracks[0].artist_id = new_artist_id
    bundle.data.artists[0].mbid = shared_mbid
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    with pytest.raises(CatalogConflictError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"), on_conflict="fail", dry_run=True)

    assert "MusicBrainz ID" in str(exc_info.value)


def test_import_intra_bundle_duplicate_ids_rejected(session: Session) -> None:
    bundle = _make_sample_bundle()
    duplicate_artist = bundle.data.artists[0].model_copy()
    bundle.data.artists.append(duplicate_artist)
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    with pytest.raises(CatalogValidationError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"))

    assert "Duplicate ID" in str(exc_info.value)
    assert "artists" in str(exc_info.value)


def test_import_intra_bundle_duplicate_tags_rejected(session: Session) -> None:
    bundle = _make_sample_bundle()
    duplicate_tag = bundle.data.tags[0].model_copy(update={"id": uuid4()})
    bundle.data.tags.append(duplicate_tag)
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    with pytest.raises(CatalogValidationError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"))

    assert "Duplicate tag definition in bundle" in str(exc_info.value)


def test_import_intra_bundle_duplicate_metadata_fields_rejected(session: Session) -> None:
    bundle = _make_sample_bundle()
    track_id = bundle.data.tracks[0].id
    mf1 = MetadataFieldBundleItem(
        id=uuid4(),
        entity_type="track",
        entity_id=track_id,
        field_name="genre",
        provider="user",
        raw_value="Trip Hop",
        normalized_value="Trip Hop",
    )
    mf2 = MetadataFieldBundleItem(
        id=uuid4(),
        entity_type="track",
        entity_id=track_id,
        field_name="genre",
        provider="user",
        raw_value="Downtempo",
        normalized_value="Downtempo",
    )
    bundle.data.metadata_fields.extend([mf1, mf2])
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    with pytest.raises(CatalogValidationError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"))

    assert "Duplicate metadata field record in bundle" in str(exc_info.value)


def test_import_mid_stream_failure_rolls_back_partial_inserts(session: Session) -> None:
    # 1. Setup pre-existing database with an album and track that will collide later in import order
    existing_artist = ArtistModel(id=uuid4(), name="Existing Unrelated Artist")
    existing_album = AlbumModel(id=uuid4(), title="Existing Album", artist_id=existing_artist.id)
    colliding_track_id = uuid4()
    existing_track = TrackModel(
        id=colliding_track_id,
        title="Colliding Original Track",
        album_id=existing_album.id,
        artist_id=existing_artist.id,
    )
    session.add_all([existing_artist, existing_album, existing_track])
    session.commit()

    # 2. Build bundle with a brand new artist (early in import order) and the colliding track
    new_artist_id = uuid4()
    new_album_id = uuid4()
    bundle_data = CatalogData(
        artists=[ArtistBundleItem(id=new_artist_id, name="Brand New Artist To Rollback")],
        albums=[AlbumBundleItem(id=new_album_id, title="Brand New Album", artist_id=new_artist_id)],
        tracks=[
            TrackBundleItem(
                id=colliding_track_id,  # Will collide during track step!
                title="Incoming Colliding Track",
                artist_id=new_artist_id,
                album_id=new_album_id,
            )
        ],
    )
    manifest = CatalogManifest(
        schema_version="1.0.0",
        harmonia_version=__version__,
        exported_at=datetime.now(UTC),
        source_system="harmonia-desktop",
        entity_counts={"artists": 1, "albums": 1, "tracks": 1},
    )
    checksum = compute_data_checksum(bundle_data)
    bundle = CatalogBundle(manifest=manifest, data=bundle_data, checksum=checksum)

    service = CatalogImportService(session)
    with pytest.raises(CatalogConflictError) as exc_info:
        service.import_bundle(bundle.model_dump(mode="json"), on_conflict="fail")

    assert "Track with id" in str(exc_info.value)
    assert "already exists" in str(exc_info.value)

    # 3. Verify that the new artist and new album were completely rolled back
    assert session.scalar(select(ArtistModel).where(ArtistModel.id == new_artist_id)) is None
    assert session.scalar(select(AlbumModel).where(AlbumModel.id == new_album_id)) is None

    # 4. Verify original data remains intact
    restored_track = session.scalar(select(TrackModel).where(TrackModel.id == colliding_track_id))
    assert restored_track is not None
    assert restored_track.title == "Colliding Original Track"


def test_sanitize_unlinked_filename_security() -> None:
    assert sanitize_unlinked_filename(None) == "audio"
    assert sanitize_unlinked_filename("") == "audio"
    assert sanitize_unlinked_filename("   ") == "audio"
    assert sanitize_unlinked_filename(".") == "audio"
    assert sanitize_unlinked_filename("..") == "audio"
    assert sanitize_unlinked_filename("../..") == "audio"
    assert sanitize_unlinked_filename("../../etc/passwd") == "passwd"
    assert sanitize_unlinked_filename(r"..\..\Windows\System32\calc.exe") == "calc.exe"
    assert sanitize_unlinked_filename("/var/data/../../../secret.key") == "secret.key"
    assert sanitize_unlinked_filename("track?.flac") == "track.flac"
    assert sanitize_unlinked_filename("my*audio|file.mp3") == "myaudiofile.mp3"


def test_import_unlinked_path_traversal_sanitized_and_unplayable(session: Session) -> None:
    bundle = _make_sample_bundle()
    # Inject a traversal path in TrackFile
    bundle.data.track_files[0].file_path = "../../etc/shadow"
    bundle.checksum = compute_data_checksum(bundle.data)

    service = CatalogImportService(session)
    res = service.import_bundle(bundle.model_dump(mode="json"))
    assert res.tracks_created == 1
    assert len(res.warnings) >= 1
    assert "not found on this host" in res.warnings[0]

    # Verify TrackFileModel in database
    tf = session.scalar(select(TrackFileModel).where(TrackFileModel.id == bundle.data.track_files[0].id))
    assert tf is not None
    # Path must be unlinked/<uuid>/shadow without any traversal component
    assert tf.file_path == f"unlinked/{tf.id}/shadow"
    assert tf.verified_at is None

    # Verify that the path is not playable and cannot resolve to a local file
    source_path = Path(tf.file_path)
    assert not source_path.is_file()
    assert not source_path.is_absolute()
    assert ".." not in source_path.parts

    backend = DesktopAudioBackend()
    with pytest.raises(FileNotFoundError) as exc_info:
        backend.load(LocalFileSource(file_path=source_path, mime_type="audio/flac"))
    assert "does not exist on disk" in str(exc_info.value)


def test_scanner_reconciles_unlinked_track_file_via_hash(session: Session, tmp_path: Path) -> None:
    # 1. Create a physical audio file on the local machine
    audio_file = tmp_path / "actual_song.mp3"
    audio_file.write_bytes(b"ID3\x03\x00\x00\x00fake audio content for testing scanner reconciliation")
    real_hash = compute_file_hash(audio_file)

    # 2. Build and import a bundle containing this track with matching file_hash but remote/unlinked path
    bundle = _make_sample_bundle(artist_name="Boards of Canada", album_title="Music Has the Right", track_title="Roygbiv")
    bundle.data.track_files[0].file_hash = real_hash
    bundle.data.track_files[0].file_path = "/remote/export/machine/roygbiv.mp3"
    bundle.checksum = compute_data_checksum(bundle.data)

    catalog_import = CatalogImportService(session)
    import_result = catalog_import.import_bundle(bundle.model_dump(mode="json"))
    assert import_result.tracks_created == 1

    # Verify initial unlinked state
    tf_initial = session.scalar(select(TrackFileModel).where(TrackFileModel.id == bundle.data.track_files[0].id))
    assert tf_initial is not None
    assert tf_initial.file_path == f"unlinked/{tf_initial.id}/roygbiv.mp3"
    assert tf_initial.verified_at is None
    first_track = session.scalar(select(TrackModel))
    assert first_track is not None
    assert first_track.title == "Roygbiv"

    # 3. Run the directory scanner (ImportService) on the physical file
    scanner = ImportService(
        session=session,
        supported_formats=[".mp3"],
        extract_artwork=False,
        compute_hashes=True,
    )
    mock_meta = ExtractedMetadata(
        title="Roygbiv",
        artist="Boards of Canada",
        album="Music Has the Right to Children",
        duration_seconds=151.0,
    )
    with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
        scanned_ok = scanner.import_file(audio_file)

    assert scanned_ok is True

    # 4. Assert zero duplicate tracks were created
    all_tracks = session.scalars(select(TrackModel)).all()
    assert len(all_tracks) == 1
    assert all_tracks[0].id == bundle.data.tracks[0].id

    # 5. Assert the unlinked TrackFile record was reconciled in-place
    all_files = session.scalars(select(TrackFileModel)).all()
    assert len(all_files) == 1
    reconciled_tf = all_files[0]
    assert reconciled_tf.id == tf_initial.id
    assert reconciled_tf.file_path == str(audio_file.resolve())
    assert reconciled_tf.verified_at is not None
