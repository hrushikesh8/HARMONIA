"""Unit tests for catalog portability schemas, versioning, and integrity calculation."""

from __future__ import annotations

import copy
from datetime import UTC, datetime
from uuid import uuid4

import pytest
from pydantic import ValidationError

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


def test_artist_bundle_item_valid() -> None:
    artist_id = uuid4()
    item = ArtistBundleItem(id=artist_id, name="Boards of Canada", sort_name="Boards of Canada")
    assert item.id == artist_id
    assert item.name == "Boards of Canada"
    assert item.mbid is None


def test_album_bundle_item_omits_machine_paths() -> None:
    album_id = uuid4()
    artist_id = uuid4()
    item = AlbumBundleItem(
        id=album_id,
        title="Music Has the Right to Children",
        artist_id=artist_id,
        year=1998,
        art_hash="a" * 64,
    )
    assert item.art_hash == "a" * 64
    # art_path is intentionally absent from AlbumBundleItem schema
    assert not hasattr(item, "art_path")


def test_track_bundle_item_fields() -> None:
    track_id = uuid4()
    item = TrackBundleItem(
        id=track_id,
        title="Roygbiv",
        track_number=6,
        disc_number=1,
        duration_seconds=151.0,
        genre="IDM",
        lyrics="Sample lyrics text",
    )
    assert item.id == track_id
    assert item.title == "Roygbiv"
    assert item.track_number == 6
    assert item.lyrics == "Sample lyrics text"


def test_track_file_bundle_item_fields() -> None:
    tf_id = uuid4()
    track_id = uuid4()
    item = TrackFileBundleItem(
        id=tf_id,
        track_id=track_id,
        file_hash="b" * 64,
        file_size=1024000,
        format="FLAC",
        codec="flac",
        bitrate=1000,
        sample_rate=44100,
        bit_depth=16,
        channels=2,
        is_lossless=True,
    )
    assert item.id == tf_id
    assert item.file_hash == "b" * 64
    assert item.is_lossless is True


def test_compute_data_checksum_determinism() -> None:
    artist_id = uuid4()
    track_id = uuid4()

    data1 = CatalogData(
        artists=[ArtistBundleItem(id=artist_id, name="Aphex Twin")],
        tracks=[TrackBundleItem(id=track_id, title="Xtal", artist_id=artist_id)],
    )

    data2 = CatalogData(
        artists=[ArtistBundleItem(id=artist_id, name="Aphex Twin")],
        tracks=[TrackBundleItem(id=track_id, title="Xtal", artist_id=artist_id)],
    )

    hash1 = compute_data_checksum(data1)
    hash2 = compute_data_checksum(data2)
    assert hash1 == hash2
    assert len(hash1) == 64

    # Any modification must change the checksum
    data_modified = copy.deepcopy(data1)
    data_modified.tracks[0].title = "Tha"
    hash_modified = compute_data_checksum(data_modified)
    assert hash_modified != hash1


def test_catalog_bundle_full_serialization() -> None:
    artist_id = uuid4()
    track_id = uuid4()
    data = CatalogData(
        artists=[ArtistBundleItem(id=artist_id, name="Autechre")],
        tracks=[TrackBundleItem(id=track_id, title="Bike", artist_id=artist_id)],
        tags=[TagBundleItem(id=uuid4(), track_id=track_id, name="genre", value="IDM", source="file")],
        metadata_fields=[
            MetadataFieldBundleItem(
                id=uuid4(),
                entity_type="track",
                entity_id=track_id,
                field_name="title",
                provider="file",
                raw_value="Bike",
                normalized_value="Bike",
                is_active=True,
                priority=10,
            )
        ],
        listening_events=[
            ListeningEventBundleItem(
                id=uuid4(),
                track_id=track_id,
                timestamp_utc=datetime.now(UTC),
                duration_listened_seconds=120.0,
                was_completed=True,
            )
        ],
    )
    manifest = CatalogManifest(
        schema_version="1.0.0",
        harmonia_version="0.1.0",
        exported_at=datetime.now(UTC),
        source_system="harmonia-desktop",
        entity_counts={"artists": 1, "tracks": 1, "tags": 1, "listening_events": 1},
    )
    checksum = compute_data_checksum(data)
    bundle = CatalogBundle(manifest=manifest, data=data, checksum=checksum)

    dumped = bundle.model_dump(mode="json")
    assert dumped["manifest"]["schema_version"] == "1.0.0"
    assert dumped["checksum"] == checksum
    assert len(dumped["data"]["artists"]) == 1


def test_schema_missing_required_fields_raises() -> None:
    with pytest.raises(ValidationError):
        # Missing required 'name' field
        ArtistBundleItem(id=uuid4())  # type: ignore[call-arg]

    with pytest.raises(ValidationError):
        # Missing required 'title' field
        TrackBundleItem(id=uuid4())  # type: ignore[call-arg]
