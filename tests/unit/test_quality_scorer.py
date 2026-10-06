"""Unit tests for deterministic quality scorer."""

from __future__ import annotations

from uuid import uuid4

from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.services.quality_scorer import compute_quality_score
from harmonia.domain.values.audio_format import AudioFormat


def test_flac_hi_res_score() -> None:
    """Hi-res FLAC (24-bit / 96kHz stereo) should score very high."""
    tf = TrackFile(
        track_id=uuid4(),
        file_path="/music/hires.flac",
        file_size=50000000,
        format=AudioFormat.FLAC,
        codec="flac",
        sample_rate=96000,
        bit_depth=24,
        channels=2,
        is_lossless=True,
    )
    score = compute_quality_score(tf)
    # Lossless: 40 + SR: 15 + BD: 15 + Channels: 5 = 75
    assert score == 75


def test_flac_cd_quality_score() -> None:
    """CD-quality FLAC (16-bit / 44.1kHz stereo)."""
    tf = TrackFile(
        track_id=uuid4(),
        file_path="/music/cd.flac",
        file_size=30000000,
        format=AudioFormat.FLAC,
        codec="flac",
        sample_rate=44100,
        bit_depth=16,
        channels=2,
        is_lossless=True,
    )
    score = compute_quality_score(tf)
    # Lossless: 40 + SR: 10 + BD: 10 + Channels: 5 = 65
    assert score == 65


def test_mp3_320kbps_score() -> None:
    """High quality MP3 (320kbps stereo 44.1kHz)."""
    tf = TrackFile(
        track_id=uuid4(),
        file_path="/music/song.mp3",
        file_size=10000000,
        format=AudioFormat.MP3,
        codec="mp3",
        bitrate=320,
        sample_rate=44100,
        channels=2,
        is_lossless=False,
    )
    score = compute_quality_score(tf)
    # Lossy 320kbps: 30 + SR: 10 + BD: 0 + Channels: 5 + Codec mp3: 0 = 45
    assert score == 45


def test_opus_bonus_score() -> None:
    """Opus codec receives quality bonus over mp3 at equal parameters."""
    tf_mp3 = TrackFile(
        track_id=uuid4(),
        file_path="/music/test.mp3",
        file_size=5000000,
        format=AudioFormat.MP3,
        codec="mp3",
        bitrate=160,
        sample_rate=48000,
        channels=2,
        is_lossless=False,
    )
    tf_opus = TrackFile(
        track_id=uuid4(),
        file_path="/music/test.opus",
        file_size=5000000,
        format=AudioFormat.OPUS,
        codec="opus",
        bitrate=160,
        sample_rate=48000,
        channels=2,
        is_lossless=False,
    )
    assert compute_quality_score(tf_opus) == compute_quality_score(tf_mp3) + 5
