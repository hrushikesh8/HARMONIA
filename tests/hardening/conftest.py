"""Shared fixtures and audio generation utilities for Phase 3.1 hardening tests."""

from __future__ import annotations

import subprocess
from pathlib import Path

import pytest


def make_sine_wav(
    path: Path,
    duration: float = 1.0,
    freq: int = 440,
    channels: int = 2,
    sample_rate: int = 44100,
) -> Path:
    """Generate a tiny deterministic WAV file using ffmpeg."""
    path.parent.mkdir(parents=True, exist_ok=True)
    channel_layout = "stereo" if channels == 2 else "mono"
    cmd = [
        "ffmpeg",
        "-nostats",
        "-loglevel",
        "error",
        "-f",
        "lavfi",
        "-i",
        f"sine=frequency={freq}:duration={duration}:sample_rate={sample_rate}",
        "-ac",
        str(channels),
        "-channel_layout",
        channel_layout,
        "-c:a",
        "pcm_s16le",
        "-y",
        str(path),
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    return path


def make_sine_mp3(
    path: Path,
    duration: float = 1.0,
    freq: int = 440,
    channels: int = 1,
    sample_rate: int = 22050,
) -> Path:
    """Generate a tiny deterministic MP3 file using ffmpeg."""
    path.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg",
        "-nostats",
        "-loglevel",
        "error",
        "-f",
        "lavfi",
        "-i",
        f"sine=frequency={freq}:duration={duration}:sample_rate={sample_rate}",
        "-ac",
        str(channels),
        "-c:a",
        "libmp3lame",
        "-b:a",
        "128k",
        "-y",
        str(path),
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    return path


def make_silence_wav(
    path: Path,
    silence_before: float = 0.6,
    sound_duration: float = 1.0,
    silence_after: float = 0.6,
) -> Path:
    """Generate audio with leading and trailing silence for silence detection validation."""
    path.parent.mkdir(parents=True, exist_ok=True)
    # Using an anullsrc and sine filter concatenation
    filter_complex = (
        f"aevalsrc=0:d={silence_before}[s1];"
        f"sine=f=440:d={sound_duration}[snd];"
        f"aevalsrc=0:d={silence_after}[s2];"
        f"[s1][snd][s2]concat=n=3:v=0:a=1[out]"
    )
    cmd = [
        "ffmpeg",
        "-nostats",
        "-loglevel",
        "error",
        "-filter_complex",
        filter_complex,
        "-map",
        "[out]",
        "-c:a",
        "pcm_s16le",
        "-y",
        str(path),
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    return path


def make_clipping_wav(path: Path) -> Path:
    """Generate an audio file with intentional volume gain that causes digital clipping."""
    path.parent.mkdir(parents=True, exist_ok=True)
    cmd = [
        "ffmpeg",
        "-nostats",
        "-loglevel",
        "error",
        "-f",
        "lavfi",
        "-i",
        "sine=frequency=1000:duration=1.0:sample_rate=44100",
        "-af",
        "volume=30dB",
        "-c:a",
        "pcm_s16le",
        "-y",
        str(path),
    ]
    subprocess.run(cmd, check=True, capture_output=True)
    return path


@pytest.fixture
def test_audio_fixtures(tmp_path: Path):
    """Fixture returning dictionary of generated test audio file paths."""
    fixtures_dir = tmp_path / "audio_fixtures"
    fixtures_dir.mkdir(parents=True, exist_ok=True)

    wav_stereo = make_sine_wav(fixtures_dir / "stereo_440hz.wav", duration=1.0, channels=2, sample_rate=44100)
    mp3_mono = make_sine_mp3(fixtures_dir / "mono_22050hz.mp3", duration=1.0, channels=1, sample_rate=22050)
    silence_wav = make_silence_wav(fixtures_dir / "silence_test.wav", silence_before=0.6, sound_duration=1.0, silence_after=0.6)
    clipped_wav = make_clipping_wav(fixtures_dir / "clipped_test.wav")

    corrupt_file = fixtures_dir / "corrupted.wav"
    corrupt_file.write_bytes(b"RIFF\x00\x00\x00\x00WAVEfmt \x10\x00\x00\x00GARBAGE_PAYLOAD")

    return {
        "wav_stereo": wav_stereo,
        "mp3_mono": mp3_mono,
        "silence_wav": silence_wav,
        "clipped_wav": clipped_wav,
        "corrupt_file": corrupt_file,
    }
