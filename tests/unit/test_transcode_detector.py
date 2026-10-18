"""Unit tests for SpectralTranscodeDetector and transcode result contracts."""

from __future__ import annotations

import math
import subprocess
from pathlib import Path
from unittest.mock import patch

import pytest

from harmonia.core.exceptions import AudioAnalysisError
from harmonia.domain.ports.audio_analyzer import (
    AudioAnalyzer,
    TechnicalAudioProperties,
    TranscodeAnalysisResult,
    TranscodeClassification,
    TranscodeReasonCode,
)
from harmonia.infrastructure.audio.ffmpeg_analyzer import FFmpegAudioAnalyzer
from harmonia.infrastructure.audio.transcode_detector import (
    SpectralTranscodeDetector,
    _pure_python_fft,
)


def test_transcode_classification_values() -> None:
    """Verify exact result contract vocabulary."""
    assert TranscodeClassification.SUSPICIOUS_CUTOFF == "suspicious_cutoff"
    assert TranscodeClassification.NO_SUSPICIOUS_CUTOFF == "no_suspicious_cutoff"
    assert TranscodeClassification.INSUFFICIENT_EVIDENCE == "insufficient_evidence"
    assert TranscodeClassification.ANALYSIS_UNAVAILABLE == "analysis_unavailable"
    # Verify forbidden terminology is NOT used
    assert not hasattr(TranscodeClassification, "CLEAN_LOSSLESS")
    assert not hasattr(TranscodeClassification, "GENUINE_LOSSLESS")


def test_transcode_analysis_result_defaults() -> None:
    """Verify TranscodeAnalysisResult default fields and structure."""
    res = TranscodeAnalysisResult(
        classification=TranscodeClassification.NO_SUSPICIOUS_CUTOFF,
        is_lossless_container=True,
    )
    assert res.classification == TranscodeClassification.NO_SUSPICIOUS_CUTOFF
    assert res.is_lossless_container is True
    assert res.cutoff_frequency_hz is None
    assert res.cutoff_steepness_db_per_khz is None
    assert res.high_frequency_ratio_db is None
    assert res.duration_analyzed_sec == 0.0
    assert res.details == ""
    assert res.error is None
    assert res.reason_code is None


def test_audio_analyzer_protocol_compliance() -> None:
    """Verify FFmpegAudioAnalyzer satisfies the AudioAnalyzer protocol."""
    analyzer = FFmpegAudioAnalyzer()
    assert isinstance(analyzer, AudioAnalyzer)
    assert hasattr(analyzer, "check_transcode")
    assert callable(analyzer.check_transcode)


def test_non_lossless_container_unavailable(tmp_path: Path) -> None:
    """Lossy containers (.mp3, .m4a, .ogg) must return ANALYSIS_UNAVAILABLE."""
    detector = SpectralTranscodeDetector()
    mp3_file = tmp_path / "track.mp3"
    mp3_file.write_bytes(b"dummy mp3 data")

    res = detector.check_transcode(mp3_file)
    assert res.classification == TranscodeClassification.ANALYSIS_UNAVAILABLE
    assert res.is_lossless_container is False
    assert res.reason_code == TranscodeReasonCode.UNSUPPORTED_CONTAINER
    assert "lossless" in res.details.lower()


def test_nonexistent_file_raises_error(tmp_path: Path) -> None:
    """Checking a non-existent file must raise AudioAnalysisError."""
    detector = SpectralTranscodeDetector()
    missing_file = tmp_path / "ghost.flac"
    with pytest.raises(AudioAnalysisError) as exc_info:
        detector.check_transcode(missing_file)
    assert "does not exist" in str(exc_info.value).lower()


def test_low_sample_rate_insufficient_evidence(tmp_path: Path) -> None:
    """Sample rates below 32 kHz have Nyquist below cutoff search region and return INSUFFICIENT_EVIDENCE."""
    detector = SpectralTranscodeDetector()
    flac_file = tmp_path / "low_sr.flac"
    flac_file.write_bytes(b"dummy flac data")

    low_sr_props = TechnicalAudioProperties(
        codec="flac",
        sample_rate=22050,
        channels=2,
        duration_seconds=10.0,
    )
    res = detector.check_transcode(flac_file, tech_props=low_sr_props)
    assert res.classification == TranscodeClassification.INSUFFICIENT_EVIDENCE
    assert res.is_lossless_container is True
    assert res.reason_code == TranscodeReasonCode.LOW_SAMPLE_RATE
    assert "nyquist" in res.details.lower()
    assert "22050" in res.details


def test_short_duration_insufficient_evidence(tmp_path: Path) -> None:
    """Files under 0.5s are too short for stable STFT and return INSUFFICIENT_EVIDENCE."""
    detector = SpectralTranscodeDetector()
    flac_file = tmp_path / "short.flac"
    flac_file.write_bytes(b"dummy flac data")

    short_props = TechnicalAudioProperties(
        codec="flac",
        sample_rate=44100,
        channels=2,
        duration_seconds=0.3,
    )
    res = detector.check_transcode(flac_file, tech_props=short_props)
    assert res.classification == TranscodeClassification.INSUFFICIENT_EVIDENCE
    assert res.is_lossless_container is True
    assert res.reason_code == TranscodeReasonCode.SHORT_DURATION
    assert "too short" in res.details.lower()


def test_silence_insufficient_evidence(tmp_path: Path) -> None:
    """Digital silence (no acoustic energy in 2-8 kHz) returns INSUFFICIENT_EVIDENCE."""
    detector = SpectralTranscodeDetector()
    flac_file = tmp_path / "silence.flac"
    flac_file.write_bytes(b"dummy flac data")

    props = TechnicalAudioProperties(
        codec="flac",
        sample_rate=44100,
        channels=2,
        duration_seconds=2.0,
    )
    # Mock decoded samples as pure silence zeros
    with patch.object(detector, "_decode_pcm_segment", return_value=[0.0] * 4096):
        res = detector.check_transcode(flac_file, tech_props=props)
    assert res.classification == TranscodeClassification.INSUFFICIENT_EVIDENCE
    assert res.reason_code == TranscodeReasonCode.SILENCE_OR_LOW_ENERGY
    assert "negligible acoustic energy" in res.details.lower()


def test_pure_python_fft_math() -> None:
    """Verify Cooley-Tukey Radix-2 FFT mathematical implementation."""
    # 1. Delta impulse [1, 0, 0, 0] -> all bins magnitude 1
    impulse = [complex(1.0, 0.0), complex(0.0), complex(0.0), complex(0.0)]
    out_impulse = _pure_python_fft(impulse)
    for val in out_impulse:
        assert math.isclose(abs(val), 1.0, rel_tol=1e-5)

    # 2. DC constant [1, 1, 1, 1] -> bin 0 magnitude 4, other bins 0
    dc = [complex(1.0, 0.0)] * 4
    out_dc = _pure_python_fft(dc)
    assert math.isclose(abs(out_dc[0]), 4.0, rel_tol=1e-5)
    for val in out_dc[1:]:
        assert math.isclose(abs(val), 0.0, abs_tol=1e-5)

    # 3. Sine wave at bin 1 of size 8
    n = 8
    sine = [complex(math.sin(2.0 * math.pi * k / n), 0.0) for k in range(n)]
    out_sine = _pure_python_fft(sine)
    # Peak at bin 1 and bin 7 (Hermitian symmetry)
    assert abs(out_sine[1]) > 3.0
    assert abs(out_sine[7]) > 3.0
    assert abs(out_sine[0]) < 1e-4
    assert abs(out_sine[4]) < 1e-4


def test_synthetic_broadband_signal_detection(tmp_path: Path) -> None:
    """Synthetic broadband white noise spanning to Nyquist should classify as NO_SUSPICIOUS_CUTOFF."""
    flac_path = tmp_path / "synthetic_white_noise.flac"
    # Generate 2s of stereo white noise with ffmpeg
    cmd = [
        "ffmpeg",
        "-y",
        "-f",
        "lavfi",
        "-i",
        "anoisesrc=d=2.0:c=white:r=44100:a=0.5",
        "-c:a",
        "flac",
        str(flac_path),
    ]
    sub = subprocess.run(cmd, capture_output=True)
    if sub.returncode != 0:
        pytest.skip(f"ffmpeg not available or anoisesrc filter unsupported: {sub.stderr.decode('utf-8', errors='ignore')}")

    detector = SpectralTranscodeDetector()
    res = detector.check_transcode(flac_path)
    assert res.classification == TranscodeClassification.NO_SUSPICIOUS_CUTOFF
    assert res.is_lossless_container is True
    assert res.reason_code == TranscodeReasonCode.NO_SUSPICIOUS_CUTOFF_DETECTED
    assert res.cutoff_frequency_hz is None
    assert res.cutoff_steepness_db_per_khz is not None
    # Slope should be shallow for white noise
    assert res.cutoff_steepness_db_per_khz < 5.0


def test_synthetic_cutoff_signal_detection(tmp_path: Path) -> None:
    """Synthetic white noise with sharp lowpass at 16 kHz should trigger SUSPICIOUS_CUTOFF."""
    flac_path = tmp_path / "synthetic_cutoff_16k.flac"
    # Generate 2s white noise with steep brickwall cutoff at 16 kHz
    filter_graph = "anoisesrc=d=2.0:c=white:r=44100:a=0.5,firequalizer=gain='if(gte(f,16000),-100,0)'"
    cmd = [
        "ffmpeg",
        "-y",
        "-f",
        "lavfi",
        "-i",
        filter_graph,
        "-c:a",
        "flac",
        str(flac_path),
    ]
    sub = subprocess.run(cmd, capture_output=True)
    if sub.returncode != 0:
        pytest.skip(f"ffmpeg not available or filter unsupported: {sub.stderr.decode('utf-8', errors='ignore')}")

    detector = SpectralTranscodeDetector()
    res = detector.check_transcode(flac_path)
    assert res.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
    assert res.is_lossless_container is True
    assert res.reason_code == TranscodeReasonCode.SUSPICIOUS_CUTOFF_DETECTED
    assert res.cutoff_frequency_hz is not None
    # Cutoff should be detected near 16000-17000 Hz
    assert 15000 <= res.cutoff_frequency_hz <= 18000
    assert res.cutoff_steepness_db_per_khz is not None
    assert res.cutoff_steepness_db_per_khz >= 14.0
    assert "Suspicious brickwall cutoff" in res.details


def test_multichannel_handling(tmp_path: Path) -> None:
    """Multichannel (5.1 surround) inputs are downmixed cleanly without graph errors."""
    flac_path = tmp_path / "synthetic_5ch1.flac"
    # Generate 5.1 multichannel audio (6 channels)
    filter_graph = "aevalsrc=exprs='0.1*sin(2*PI*440*t)|0.1*sin(2*PI*880*t)|0.1*sin(2*PI*220*t)|0.05*sin(2*PI*60*t)|0.1*sin(2*PI*1760*t)|0.1*sin(2*PI*3520*t)':s=48000:d=1.5:c=5.1"
    cmd = [
        "ffmpeg",
        "-y",
        "-f",
        "lavfi",
        "-i",
        filter_graph,
        "-c:a",
        "flac",
        str(flac_path),
    ]
    sub = subprocess.run(cmd, capture_output=True)
    if sub.returncode != 0:
        pytest.skip(f"ffmpeg multichannel synth not supported: {sub.stderr.decode('utf-8', errors='ignore')}")

    detector = SpectralTranscodeDetector()
    res = detector.check_transcode(flac_path)
    # Multichannel audio was decoded and analyzed successfully
    assert res.classification in (
        TranscodeClassification.INSUFFICIENT_EVIDENCE,
        TranscodeClassification.NO_SUSPICIOUS_CUTOFF,
    )
    assert res.is_lossless_container is True


def test_corrupt_file_handling(tmp_path: Path) -> None:
    """Unparseable / corrupt files return ANALYSIS_UNAVAILABLE with error details."""
    corrupt_file = tmp_path / "corrupt.flac"
    corrupt_file.write_bytes(b"NOT_A_VALID_FLAC_STREAM_AT_ALL_JUST_JUNK_DATA_FOR_TESTING")

    detector = SpectralTranscodeDetector()
    res = detector.check_transcode(corrupt_file)
    assert res.classification == TranscodeClassification.ANALYSIS_UNAVAILABLE
    assert res.is_lossless_container is True
    assert res.reason_code == TranscodeReasonCode.CORRUPT_OR_UNPARSEABLE
    assert res.error is not None
    assert "failed" in res.details.lower()


def test_missing_tool_handling(tmp_path: Path) -> None:
    """Missing ffprobe executable returns ANALYSIS_UNAVAILABLE."""
    flac_file = tmp_path / "test.flac"
    flac_file.write_bytes(b"dummy")

    detector = SpectralTranscodeDetector(ffprobe_bin="non_existent_ffprobe_binary_xyz")
    res = detector.check_transcode(flac_file)
    assert res.classification == TranscodeClassification.ANALYSIS_UNAVAILABLE
    assert res.reason_code == TranscodeReasonCode.TOOL_MISSING
    assert "metadata" in res.details.lower()


def test_subprocess_timeout_handling(tmp_path: Path) -> None:
    """Subprocess timeout during decode returns ANALYSIS_UNAVAILABLE."""
    flac_file = tmp_path / "test.flac"
    flac_file.write_bytes(b"dummy")

    props = TechnicalAudioProperties(
        codec="flac",
        sample_rate=44100,
        channels=2,
        duration_seconds=5.0,
    )
    detector = SpectralTranscodeDetector()
    with patch("subprocess.run", side_effect=subprocess.TimeoutExpired(cmd="ffmpeg", timeout=1.0)):
        res = detector.check_transcode(flac_file, tech_props=props)
    assert res.classification == TranscodeClassification.ANALYSIS_UNAVAILABLE
    assert res.reason_code == TranscodeReasonCode.TIMEOUT
    assert "timed out" in res.details.lower()


def test_pure_python_fft_invalid_lengths() -> None:
    """_pure_python_fft must raise ValueError on non-power-of-2 lengths."""
    with pytest.raises(ValueError, match="power of two"):
        _pure_python_fft([])

    with pytest.raises(ValueError, match="power of two"):
        _pure_python_fft([complex(1.0, 0.0)] * 3)

    with pytest.raises(ValueError, match="power of two"):
        _pure_python_fft([complex(1.0, 0.0)] * 5)

    with pytest.raises(ValueError, match="power of two"):
        _pure_python_fft([complex(1.0, 0.0)] * 6)


def test_transcode_reason_codes_enumeration() -> None:
    """Verify all defined reason codes are accessible and distinct."""
    codes = {rc.value for rc in TranscodeReasonCode}
    assert "unsupported_container" in codes
    assert "low_sample_rate" in codes
    assert "short_duration" in codes
    assert "silence_or_low_energy" in codes
    assert "natural_rolloff_or_speech" in codes
    assert "suspicious_cutoff_detected" in codes
    assert "no_suspicious_cutoff_detected" in codes
    assert "tool_missing" in codes
    assert "timeout" in codes
    assert "corrupt_or_unparseable" in codes


def test_synthetic_known_cutoff_accuracy(tmp_path: Path) -> None:
    """Verify cutoff estimator locates known synthetic cutoffs with <= 500 Hz tolerance."""
    detector = SpectralTranscodeDetector()
    for target_freq in [15000, 16000, 17000, 18000]:
        flac_path = tmp_path / f"synth_cutoff_{target_freq}.flac"
        filter_graph = f"anoisesrc=d=2.0:c=white:r=44100:a=0.5,firequalizer=gain='if(gte(f,{target_freq}),-100,0)'"
        cmd = ["ffmpeg", "-y", "-f", "lavfi", "-i", filter_graph, "-c:a", "flac", str(flac_path)]
        sub = subprocess.run(cmd, capture_output=True)
        if sub.returncode != 0:
            pytest.skip("FFmpeg firequalizer filter not available")

        res = detector.check_transcode(flac_path)
        assert res.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
        assert res.cutoff_frequency_hz is not None
        assert abs(res.cutoff_frequency_hz - target_freq) <= 500
        assert res.cutoff_steepness_db_per_khz is not None
        assert res.cutoff_steepness_db_per_khz >= 14.0


def test_boundary_cutoff_estimate_unavailable(tmp_path: Path) -> None:
    """Cutoff right at the upper analysis boundary returns SUSPICIOUS_CUTOFF but cutoff_frequency_hz=None."""
    detector = SpectralTranscodeDetector()
    # 44.1 kHz audio with cutoff at 21.0 kHz (within 1000 Hz of 21.55 kHz limit)
    flac_path = tmp_path / "synth_cutoff_boundary.flac"
    filter_graph = "anoisesrc=d=2.0:c=white:r=44100:a=0.5,firequalizer=gain='if(gte(f,21000),-100,0)'"
    cmd = ["ffmpeg", "-y", "-f", "lavfi", "-i", filter_graph, "-c:a", "flac", str(flac_path)]
    sub = subprocess.run(cmd, capture_output=True)
    if sub.returncode != 0:
        pytest.skip("FFmpeg firequalizer filter not available")

    res = detector.check_transcode(flac_path)
    assert res.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
    assert res.cutoff_frequency_hz is None
    assert "unavailable" in res.details.lower()


