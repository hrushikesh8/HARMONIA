"""Unit tests for Harmonia domain entities and value objects."""

from __future__ import annotations

from uuid import uuid4

from harmonia.core.types import TagSource
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.tag import Tag
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat


def test_artist_creation() -> None:
    artist = Artist(name="The Beatles", sort_name="Beatles, The")
    assert artist.name == "The Beatles"
    assert artist.sort_name == "Beatles, The"
    assert artist.id is not None
    assert artist.mbid is None


def test_album_creation() -> None:
    artist_id = uuid4()
    album = Album(
        title="Abbey Road",
        artist_id=artist_id,
        year=1969,
        total_tracks=17,
        total_discs=1,
    )
    assert album.title == "Abbey Road"
    assert album.artist_id == artist_id
    assert album.year == 1969
    assert album.total_tracks == 17
    assert album.total_discs == 1


def test_track_creation_and_duration_display() -> None:
    track = Track(
        title="Come Together",
        track_number=1,
        disc_number=1,
        duration_seconds=259.0,
        genre="Rock",
        year=1969,
    )
    assert track.title == "Come Together"
    assert track.duration_display == "4:19"

    # Edge cases for duration display
    track_none = Track(title="Untimed", duration_seconds=None)
    assert track_none.duration_display == "-"

    track_long = Track(title="Epic", duration_seconds=3665.0)
    assert track_long.duration_display == "61:05"


def test_track_file_creation_and_size_display() -> None:
    track_id = uuid4()
    tf = TrackFile(
        track_id=track_id,
        file_path="/music/beatles/come_together.flac",
        file_size=31457280,  # ~30MB
        format=AudioFormat.FLAC,
        codec="flac",
        bitrate=900,
        sample_rate=44100,
        bit_depth=16,
        channels=2,
        is_lossless=True,
    )
    assert tf.track_id == track_id
    assert tf.format == AudioFormat.FLAC
    assert tf.is_lossless is True
    assert "MB" in tf.file_size_display


def test_tag_creation() -> None:
    track_id = uuid4()
    tag = Tag(
        track_id=track_id,
        name="mood",
        value="energetic",
        source=TagSource.USER,
    )
    assert tag.track_id == track_id
    assert tag.name == "mood"
    assert tag.value == "energetic"
    assert tag.source == TagSource.USER


def test_audio_format_from_extension() -> None:
    assert AudioFormat.from_extension(".mp3") == AudioFormat.MP3
    assert AudioFormat.from_extension("flac") == AudioFormat.FLAC
    assert AudioFormat.from_extension(".M4A") == AudioFormat.M4A
    assert AudioFormat.from_extension(".unknown") is None


def test_audio_format_lossless() -> None:
    assert AudioFormat.FLAC.is_lossless_format is True
    assert AudioFormat.WAV.is_lossless_format is True
    assert AudioFormat.ALAC.is_lossless_format is True
    assert AudioFormat.MP3.is_lossless_format is False
    assert AudioFormat.AAC.is_lossless_format is False
    assert AudioFormat.OGG.is_lossless_format is False
