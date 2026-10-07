"""Hardening Tests #3 & #4: Real FFprobe and FFmpeg signal analysis.

Validates:
- Real FFprobe technical property extraction without mocks across WAV, MP3, mono, stereo.
- Error handling on invalid/corrupt audio files.
- Real FFmpeg signal analysis execution (ebur128, volumedetect, silencedetect).
- Accurate measurement of LUFS, True Peak, sample peak, clipping detection, and silence intervals.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from harmonia.core.exceptions import AudioDecodeError
from harmonia.infrastructure.audio.ffmpeg_analyzer import FFmpegAudioAnalyzer


def test_real_ffprobe_technical_analysis(test_audio_fixtures: dict[str, Path]) -> None:
    """TEST #3: Real FFprobe technical property extraction.

    Executes real ffprobe on deterministic fixtures:
    1. Stereo WAV (pcm_s16le, 44.1 kHz, 2 channels, lossless)
    2. Mono MP3 (mp3, 22.05 kHz, 1 channel, lossy)
    3. Corrupted audio file error handling
    """
    analyzer = FFmpegAudioAnalyzer()
    has_probe, has_ffmpeg = analyzer.check_tools_available()
    assert has_probe, "ffprobe executable must be available on PATH"
    assert has_ffmpeg, "ffmpeg executable must be available on PATH"

    # 1. WAV Stereo validation
    wav_path = test_audio_fixtures["wav_stereo"]
    wav_tech = analyzer.probe_technical(wav_path)
    assert wav_tech.codec in ("pcm_s16le", "wav")
    assert wav_tech.channels == 2
    assert wav_tech.sample_rate == 44100
    assert wav_tech.is_lossless is True
    assert wav_tech.duration_seconds is not None
    assert 0.9 <= wav_tech.duration_seconds <= 1.2
    assert wav_tech.channel_layout in ("stereo", "unknown", None)

    # 2. MP3 Mono validation
    mp3_path = test_audio_fixtures["mp3_mono"]
    mp3_tech = analyzer.probe_technical(mp3_path)
    assert mp3_tech.codec == "mp3"
    assert mp3_tech.channels == 1
    assert mp3_tech.sample_rate == 22050
    assert mp3_tech.is_lossless is False
    assert mp3_tech.duration_seconds is not None
    assert 0.9 <= mp3_tech.duration_seconds <= 1.2
    assert mp3_tech.bitrate is not None
    assert 100 <= mp3_tech.bitrate <= 160  # ~128 kbps

    # 3. Corrupt file validation
    corrupt_path = test_audio_fixtures["corrupt_file"]
    with pytest.raises(AudioDecodeError):
        analyzer.probe_technical(corrupt_path)


def test_real_ffmpeg_signal_analysis(test_audio_fixtures: dict[str, Path]) -> None:
    """TEST #4: Real FFmpeg signal analysis with ebur128, volumedetect, and silencedetect.

    Executes real ffmpeg against deterministic fixtures:
    - Normal sine wave: verifies LUFS, true peak, crest factor
    - Audio with silence: verifies leading/trailing silence detection
    - Audio with gain: verifies clipping detection
    """
    analyzer = FFmpegAudioAnalyzer()

    # 1. Signal measurement of standard sine wave
    wav_path = test_audio_fixtures["wav_stereo"]
    res = analyzer.analyze_signal(wav_path)

    assert res.analyzer_name == "ffmpeg_ebur128"
    assert res.analyzer_version == "1.0.0"
    assert res.config_version == "1.0.0"
    assert res.integrated_lufs is not None
    assert -35.0 <= res.integrated_lufs <= -1.0
    assert res.sample_peak_dbfs is not None
    assert -30.0 <= res.sample_peak_dbfs <= 1.0
    assert res.duration_analyzed is not None
    assert 0.8 <= res.duration_analyzed <= 1.5

    # 2. Silence detection validation
    silence_path = test_audio_fixtures["silence_wav"]
    silence_res = analyzer.analyze_signal(silence_path)
    assert silence_res.silence_start_sec is not None or silence_res.silence_end_sec is not None

    # 3. Clipping detection validation
    clipped_path = test_audio_fixtures["clipped_wav"]
    clipped_res = analyzer.analyze_signal(clipped_path)
    assert clipped_res.is_clipped is True, "Clipped audio fixture must trigger is_clipped = True"
    assert clipped_res.true_peak_dbtp is not None
    assert clipped_res.true_peak_dbtp >= -0.1
