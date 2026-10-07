"""Unit tests for FFmpegAudioAnalyzer."""

from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from harmonia.core.exceptions import (
    AnalysisTimeoutError,
    AudioAnalysisError,
    AudioDecodeError,
    AudioToolNotFoundError,
)
from harmonia.infrastructure.audio.ffmpeg_analyzer import FFmpegAudioAnalyzer

SAMPLE_FFPROBE_OUTPUT = """{
    "streams": [
        {
            "codec_name": "flac",
            "codec_type": "audio",
            "sample_rate": "96000",
            "channels": 2,
            "channel_layout": "stereo",
            "bits_per_raw_sample": "24",
            "bit_rate": "2850000",
            "duration": "245.500000"
        }
    ],
    "format": {
        "format_name": "flac",
        "duration": "245.500000",
        "tags": {
            "encoder": "Lavf62.1.103"
        }
    }
}"""

SAMPLE_FFMPEG_OUTPUT = """
Input #0, flac, from 'test.flac':
  Duration: 00:04:05.50, start: 0.000000, bitrate: 2850 kb/s
  Stream #0:0: Audio: flac, 96000 Hz, stereo, s32 (24 bit)
[Parsed_ebur128_0 @ 000001] Summary:

  Integrated loudness:
    I:         -14.2 LUFS
    Threshold: -24.5 LUFS

  Loudness range:
    LRA:         8.5 LU
    Threshold: -34.8 LUFS
    LRA low:   -19.0 LUFS
    LRA high:  -10.5 LUFS

  True peak:
    Peak:       -0.4 dBFS
[Parsed_volumedetect_1 @ 000002] n_samples: 47136000
[Parsed_volumedetect_1 @ 000002] mean_volume: -18.5 dB
[Parsed_volumedetect_1 @ 000002] max_volume: -0.6 dB
[Parsed_volumedetect_1 @ 000002] histogram_0db: 0
[Parsed_silencedetect_2 @ 000003] silence_start: 0
[Parsed_silencedetect_2 @ 000003] silence_end: 0.15 | silence_duration: 0.15
size=N/A time=00:04:05.50 bitrate=N/A speed=45.2x
"""


def test_probe_technical_success(tmp_path: Path) -> None:
    test_file = tmp_path / "song.flac"
    test_file.write_bytes(b"dummy audio content")

    analyzer = FFmpegAudioAnalyzer()
    mock_res = MagicMock()
    mock_res.returncode = 0
    mock_res.stdout = SAMPLE_FFPROBE_OUTPUT

    with patch("subprocess.run", return_value=mock_res):
        props = analyzer.probe_technical(test_file)

    assert props.codec == "flac"
    assert props.is_lossless is True
    assert props.sample_rate == 96000
    assert props.bit_depth == 24
    assert props.channels == 2
    assert props.channel_layout == "stereo"
    assert props.duration_seconds == 245.5
    assert props.encoder == "Lavf62.1.103"


def test_analyze_signal_success(tmp_path: Path) -> None:
    test_file = tmp_path / "song.flac"
    test_file.write_bytes(b"dummy audio content")

    analyzer = FFmpegAudioAnalyzer()
    mock_res = MagicMock()
    mock_res.returncode = 0
    mock_res.stderr = SAMPLE_FFMPEG_OUTPUT

    with patch("subprocess.run", return_value=mock_res):
        result = analyzer.analyze_signal(test_file)

    assert result.integrated_lufs == -14.2
    assert result.loudness_range_lu == 8.5
    assert result.true_peak_dbtp == -0.4
    assert result.sample_peak_dbfs == -0.6
    assert result.clipping_samples == 0
    assert result.is_clipped is False
    assert result.peak_to_loudness_db == 13.8  # -0.4 - (-14.2)
    assert result.silence_start_sec == 0.15
    assert result.analyzer_name == "ffmpeg_ebur128"
    assert result.analyzer_version == "1.0.0"


def test_analyze_signal_clipping_detected(tmp_path: Path) -> None:
    test_file = tmp_path / "clipped.mp3"
    test_file.write_bytes(b"dummy audio")

    clipped_output = SAMPLE_FFMPEG_OUTPUT.replace("Peak:       -0.4 dBFS", "Peak:       +1.2 dBFS")
    clipped_output = clipped_output.replace("histogram_0db: 0", "histogram_0db: 42")

    analyzer = FFmpegAudioAnalyzer()
    mock_res = MagicMock(returncode=0, stderr=clipped_output)

    with patch("subprocess.run", return_value=mock_res):
        result = analyzer.analyze_signal(test_file)

    assert result.true_peak_dbtp == 1.2
    assert result.clipping_samples == 42
    assert result.is_clipped is True


def test_probe_missing_tool(tmp_path: Path) -> None:
    test_file = tmp_path / "song.flac"
    test_file.write_bytes(b"dummy audio")

    analyzer = FFmpegAudioAnalyzer()
    with patch("subprocess.run", side_effect=FileNotFoundError), pytest.raises(
        AudioToolNotFoundError
    ):
        analyzer.probe_technical(test_file)


def test_analyze_missing_tool(tmp_path: Path) -> None:
    test_file = tmp_path / "song.flac"
    test_file.write_bytes(b"dummy audio")

    analyzer = FFmpegAudioAnalyzer()
    with patch("subprocess.run", side_effect=FileNotFoundError), pytest.raises(
        AudioToolNotFoundError
    ):
        analyzer.analyze_signal(test_file)


def test_analyze_file_not_found() -> None:
    analyzer = FFmpegAudioAnalyzer()
    with pytest.raises(AudioAnalysisError):
        analyzer.analyze_signal(Path("/nonexistent/file.wav"))


def test_analyze_timeout(tmp_path: Path) -> None:
    import subprocess

    test_file = tmp_path / "song.flac"
    test_file.write_bytes(b"dummy audio")

    analyzer = FFmpegAudioAnalyzer()
    with (
        patch(
            "subprocess.run", side_effect=subprocess.TimeoutExpired(cmd="ffmpeg", timeout=10)
        ),
        pytest.raises(AnalysisTimeoutError),
    ):
        analyzer.analyze_signal(test_file)


def test_analyze_decode_error(tmp_path: Path) -> None:
    test_file = tmp_path / "corrupt.flac"
    test_file.write_bytes(b"corrupt")

    analyzer = FFmpegAudioAnalyzer()
    mock_res = MagicMock(returncode=1, stderr="Invalid data found when processing input")
    with patch("subprocess.run", return_value=mock_res), pytest.raises(AudioDecodeError):
        analyzer.analyze_signal(test_file)
