"""Port protocol for audio analysis probes and signal measurements."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Protocol


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


class AudioAnalyzer(Protocol):
    """Interface for running technical probes and signal analysis on audio files."""

    def probe_technical(self, file_path: Path) -> TechnicalAudioProperties:
        """Extract technical stream headers and properties (fast ffprobe)."""
        ...

    def analyze_signal(self, file_path: Path) -> SignalAnalysisResult:
        """Decode and measure objective audio signal characteristics (ffmpeg ebur128)."""
        ...
