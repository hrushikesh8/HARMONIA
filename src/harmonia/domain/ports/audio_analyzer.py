"""Port protocol for audio analysis probes and signal measurements."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from typing import Protocol, runtime_checkable


class TranscodeClassification(StrEnum):
    """Categorical outcome of suspicious spectral cutoff analysis."""

    SUSPICIOUS_CUTOFF = "suspicious_cutoff"
    NO_SUSPICIOUS_CUTOFF = "no_suspicious_cutoff"
    INSUFFICIENT_EVIDENCE = "insufficient_evidence"
    ANALYSIS_UNAVAILABLE = "analysis_unavailable"


class TranscodeReasonCode(StrEnum):
    """Structured machine-readable reason code clarifying the classification."""

    UNSUPPORTED_CONTAINER = "unsupported_container"
    LOW_SAMPLE_RATE = "low_sample_rate"
    SHORT_DURATION = "short_duration"
    SILENCE_OR_LOW_ENERGY = "silence_or_low_energy"
    NATURAL_ROLLOFF_OR_SPEECH = "natural_rolloff_or_speech"
    BANDLIMITED_BELOW_SEARCH_RANGE = "bandlimited_below_search_range"
    INSUFFICIENT_FRAMES = "insufficient_frames"
    BANDWIDTH_EXCEEDED = "bandwidth_exceeded"
    SUSPICIOUS_CUTOFF_DETECTED = "suspicious_cutoff_detected"
    NO_SUSPICIOUS_CUTOFF_DETECTED = "no_suspicious_cutoff_detected"
    TOOL_MISSING = "tool_missing"
    TIMEOUT = "timeout"
    DECODE_ERROR = "decode_error"
    CORRUPT_OR_UNPARSEABLE = "corrupt_or_unparseable"
    ANALYSIS_FAILED = "analysis_failed"


@dataclass(frozen=True)
class TranscodeAnalysisResult:
    """Factual, objective result of spectral cutoff and transcode detection."""

    classification: TranscodeClassification
    is_lossless_container: bool
    cutoff_frequency_hz: int | None = None
    cutoff_steepness_db_per_khz: float | None = None
    high_frequency_ratio_db: float | None = None
    duration_analyzed_sec: float = 0.0
    details: str = ""
    error: str | None = None
    reason_code: str | None = None


@dataclass(frozen=True)
class TechnicalAudioProperties:
    """Stream container and technical encoding properties extracted via probe."""

    duration_seconds: float | None = None
    codec: str | None = None
    bitrate: int | None = None  # kbps
    sample_rate: int | None = None  # Hz
    bit_depth: int | None = None
    channels: int | None = None
    channel_layout: str | None = None
    encoder: str | None = None
    is_lossless: bool = False


@dataclass(frozen=True)
class SignalAnalysisResult:
    """Objective signal measurements computed by audio decoding."""

    integrated_lufs: float | None = None
    loudness_range_lu: float | None = None
    true_peak_dbtp: float | None = None
    sample_peak_dbfs: float | None = None
    peak_to_loudness_db: float | None = None
    clipping_samples: int | None = None
    is_clipped: bool = False
    duration_analyzed: float | None = None
    silence_start_sec: float | None = None
    silence_end_sec: float | None = None
    analyzer_name: str = "ffmpeg_ebur128"
    analyzer_version: str = "1.0.0"
    config_version: str = "1.0.0"


@runtime_checkable
class AudioAnalyzer(Protocol):
    """Interface for running technical probes and signal analysis on audio files."""

    def probe_technical(self, file_path: Path) -> TechnicalAudioProperties:
        """Extract technical stream headers and properties (fast ffprobe)."""
        ...

    def analyze_signal(self, file_path: Path) -> SignalAnalysisResult:
        """Decode and measure objective audio signal characteristics (ffmpeg ebur128)."""
        ...

    def check_transcode(self, file_path: Path) -> TranscodeAnalysisResult:
        """Analyze audio for suspicious artificial spectral cutoffs (lossy upconverts)."""
        ...
