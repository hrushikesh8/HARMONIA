"""Unit tests for mutagen metadata reader and hash utility."""

from __future__ import annotations

import hashlib
from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from harmonia.core.exceptions import MetadataExtractionError
from harmonia.infrastructure.audio.mutagen_reader import (
    _parse_number_pair,
    _parse_year,
    compute_file_hash,
    extract_metadata,
)


def test_compute_file_hash(temp_dir: Path) -> None:
    test_file = temp_dir / "sample.bin"
    content = b"Harmonia audio metadata test byte content"
    test_file.write_bytes(content)

    expected = hashlib.sha256(content).hexdigest()
    actual = compute_file_hash(test_file)
    assert actual == expected


def test_parse_year() -> None:
    assert _parse_year("1969") == 1969
    assert _parse_year("2024-05-18") == 2024
    assert _parse_year("Recorded in 1982") == 1982
    assert _parse_year("") is None
    assert _parse_year(None) is None
    assert _parse_year("Unknown date") is None


def test_parse_number_pair() -> None:
    assert _parse_number_pair("5") == (5, None)
    assert _parse_number_pair("5/12") == (5, 12)
    assert _parse_number_pair(" 03 / 10 ") == (3, 10)
    assert _parse_number_pair("") == (None, None)
    assert _parse_number_pair(None) == (None, None)
    assert _parse_number_pair("invalid/pair") == (None, None)


def test_extract_metadata_unrecognized_file(temp_dir: Path) -> None:
    bad_file = temp_dir / "not_audio.mp3"
    bad_file.write_bytes(b"garbage not an audio stream")

    with pytest.raises(MetadataExtractionError):
        extract_metadata(bad_file)


def test_extract_metadata_with_mock() -> None:
    fake_path = Path("/mock/music/track.flac")

    mock_audio = MagicMock()
    mock_audio.get.side_effect = lambda k: {
        "title": ["Shine On You Crazy Diamond"],
        "artist": ["Pink Floyd"],
        "album": ["Wish You Were Here"],
        "tracknumber": ["1/5"],
        "date": ["1975"],
        "genre": ["Progressive Rock"],
        "musicbrainz_trackid": ["mb-track-123"],
    }.get(k)

    mock_info = MagicMock()
    mock_info.length = 810.5
    mock_info.sample_rate = 44100
    mock_info.channels = 2
    mock_info.bitrate = 850000
    mock_info.bits_per_sample = 16
    mock_audio.info = mock_info

    with patch("mutagen.File", return_value=mock_audio):
        meta = extract_metadata(fake_path)

        assert meta.title == "Shine On You Crazy Diamond"
        assert meta.artist == "Pink Floyd"
        assert meta.album == "Wish You Were Here"
        assert meta.track_number == 1
        assert meta.total_tracks == 5
        assert meta.year == 1975
        assert meta.genre == "Progressive Rock"
        assert meta.duration_seconds == 810.5
        assert meta.sample_rate == 44100
        assert meta.bit_depth == 16
        assert meta.is_lossless is True
        assert meta.codec == "flac"
        assert meta.mb_track_id == "mb-track-123"
