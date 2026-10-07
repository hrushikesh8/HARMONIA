"""Unit tests for MetadataResolver domain service."""

import time
from uuid import uuid4

from harmonia.domain.services.metadata_resolver import MetadataResolver


def test_resolve_field_priority() -> None:
    entity_id = uuid4()
    rec_path = MetadataResolver.create_record("track", entity_id, "title", "path", "Track01", 10)
    rec_file = MetadataResolver.create_record("track", entity_id, "title", "file", "Echoes", 30)
    rec_mb = MetadataResolver.create_record("track", entity_id, "title", "musicbrainz", "Echoes (Remaster)", 50)

    winner = MetadataResolver.resolve_field([rec_path, rec_file, rec_mb])
    assert winner is not None
    assert winner.id == rec_mb.id
    assert winner.normalized_value == "Echoes (Remaster)"
    assert rec_mb.is_active is True
    assert rec_file.is_active is False
    assert rec_path.is_active is False


def test_user_override_always_wins() -> None:
    entity_id = uuid4()
    rec_file = MetadataResolver.create_record("track", entity_id, "title", "file", "Echoes", 30)
    rec_mb = MetadataResolver.create_record("track", entity_id, "title", "musicbrainz", "Echoes", 50)
    rec_user = MetadataResolver.create_record("track", entity_id, "title", "user", "My Custom Title", 100)

    winner = MetadataResolver.resolve_field([rec_file, rec_mb, rec_user])
    assert winner is not None
    assert winner.id == rec_user.id
    assert winner.normalized_value == "My Custom Title"
    assert rec_user.is_active is True


def test_empty_high_priority_does_not_beat_valid_lower_priority() -> None:
    entity_id = uuid4()
    rec_file = MetadataResolver.create_record("track", entity_id, "year", "file", "1971", 30)
    rec_mb_empty = MetadataResolver.create_record("track", entity_id, "year", "musicbrainz", "", 50)

    winner = MetadataResolver.resolve_field([rec_file, rec_mb_empty])
    assert winner is not None
    assert winner.id == rec_file.id
    assert winner.normalized_value == "1971"
    assert rec_file.is_active is True
    assert rec_mb_empty.is_active is False


def test_tiebreak_prefers_older_record() -> None:
    entity_id = uuid4()
    rec_file1 = MetadataResolver.create_record("track", entity_id, "title", "file", "Title 1", 30)
    time.sleep(0.01)
    rec_file2 = MetadataResolver.create_record("track", entity_id, "title", "file", "Title 2", 30)

    winner = MetadataResolver.resolve_field([rec_file1, rec_file2])
    assert winner is not None
    assert winner.id == rec_file1.id
    assert winner.normalized_value == "Title 1"


def test_resolve_entity_fields_multiple() -> None:
    entity_id = uuid4()
    records = [
        MetadataResolver.create_record("track", entity_id, "title", "file", "Time", 30),
        MetadataResolver.create_record("track", entity_id, "title", "user", "Time (2023 Remaster)", 100),
        MetadataResolver.create_record("track", entity_id, "artist_name", "file", "Pink Floyd", 30),
        MetadataResolver.create_record("track", entity_id, "year", "file", "1973", 30),
        MetadataResolver.create_record("track", entity_id, "year", "musicbrainz", "1973", 50),
    ]

    winners = MetadataResolver.resolve_entity_fields(records)
    assert len(winners) == 3
    assert winners["title"].normalized_value == "Time (2023 Remaster)"
    assert winners["artist_name"].normalized_value == "Pink Floyd"
    assert winners["year"].normalized_value == "1973"
    assert winners["year"].provider == "musicbrainz"


def test_empty_candidate_list() -> None:
    assert MetadataResolver.resolve_field([]) is None
    assert MetadataResolver.resolve_entity_fields([]) == {}
