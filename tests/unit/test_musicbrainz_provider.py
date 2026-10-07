"""Unit tests for MusicBrainzProvider."""

import urllib.error
from unittest.mock import patch
from uuid import uuid4

from harmonia.infrastructure.metadata.musicbrainz_provider import MusicBrainzProvider


def test_musicbrainz_lookup_success() -> None:
    provider = MusicBrainzProvider(rate_limit_delay=0.0)
    track_id = uuid4()

    mock_response = {
        "recordings": [
            {
                "id": "rec-12345",
                "score": 100,
                "title": "Time",
                "artist-credit": [
                    {"artist": {"id": "art-12345", "name": "Pink Floyd"}}
                ],
                "releases": [
                    {
                        "id": "rel-12345",
                        "title": "The Dark Side of the Moon",
                        "date": "1973-03-01",
                    }
                ],
                "tags": [{"name": "Progressive Rock", "count": 10}],
            }
        ]
    }

    with patch.object(provider, "_execute_query", return_value=mock_response):
        records = provider.lookup_track(
            entity_id=track_id,
            artist_name="Pink Floyd",
            album_title="The Dark Side of the Moon",
            track_title="Time",
        )

        assert len(records) > 0
        by_field = {r.field_name: r for r in records}

        assert by_field["title"].normalized_value == "Time"
        assert by_field["mbid"].raw_value == "rec-12345"
        assert by_field["artist_name"].normalized_value == "Pink Floyd"
        assert by_field["album_title"].normalized_value == "The Dark Side of the Moon"
        assert by_field["year"].normalized_value == "1973"
        assert by_field["genre"].normalized_value == "Progressive Rock"

        for r in records:
            assert r.provider == "musicbrainz"
            assert r.priority == 50
            assert r.entity_id == track_id


def test_musicbrainz_low_score_ignored() -> None:
    provider = MusicBrainzProvider(rate_limit_delay=0.0, min_score=80)
    mock_response = {
        "recordings": [
            {
                "id": "rec-999",
                "score": 50,  # Below threshold
                "title": "Something Else",
            }
        ]
    }

    with patch.object(provider, "_execute_query", return_value=mock_response):
        records = provider.lookup_track(
            entity_id=uuid4(),
            artist_name="Unknown",
            album_title="Unknown",
            track_title="Song",
        )
        assert records == []


def test_musicbrainz_offline_network_failure() -> None:
    provider = MusicBrainzProvider(rate_limit_delay=0.0)
    with patch(
        "urllib.request.urlopen",
        side_effect=urllib.error.URLError("Network unreachable"),
    ):
        records = provider.lookup_track(
            entity_id=uuid4(),
            artist_name="Pink Floyd",
            album_title="The Wall",
            track_title="Comfortably Numb",
        )
        assert records == []


def test_musicbrainz_missing_track_title() -> None:
    provider = MusicBrainzProvider(rate_limit_delay=0.0)
    records = provider.lookup_track(
        entity_id=uuid4(),
        artist_name="Pink Floyd",
        album_title="The Wall",
        track_title=None,
    )
    assert records == []
