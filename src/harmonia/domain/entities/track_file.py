"""TrackFile domain entity — a physical audio file on disk."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4

from harmonia.domain.values.audio_format import AudioFormat


@dataclass
class TrackFile:
    """A physical audio file that represents a Track.

    One Track can have multiple TrackFiles (e.g., FLAC + MP3 of the same
    recording). This separation is critical for:
    - Quality management (picking the best available version)
    - Deduplication (detecting the same audio in different files)
    - Format flexibility (keeping multiple encodings)

    Technical audio properties come from metadata extraction (mutagen)
    and audio analysis (FFprobe). The ``quality_score`` is computed by
    the quality scorer domain service.
    """

    track_id: UUID
    file_path: str  # Absolute path on disk
    file_size: int  # Bytes
    format: AudioFormat
    id: UUID = field(default_factory=uuid4)
    file_hash: str | None = None  # SHA-256 hex digest
    codec: str | None = None  # e.g., "mp3", "flac", "aac", "opus"
    bitrate: int | None = None  # kbps (average for VBR)
    sample_rate: int | None = None  # Hz (e.g., 44100, 48000, 96000)
    bit_depth: int | None = None  # Bits (e.g., 16, 24, 32)
    channels: int | None = None  # 1=mono, 2=stereo
    duration_seconds: float | None = None
    is_lossless: bool = False
    quality_score: int | None = None  # 0-100, computed by quality scorer
    added_at: datetime = field(default_factory=lambda: datetime.now(UTC))
    verified_at: datetime | None = None

    @property
    def file_size_display(self) -> str:
        """Human-readable file size."""
        if self.file_size < 1024:
            return f"{self.file_size} B"
        elif self.file_size < 1024 * 1024:
            return f"{self.file_size / 1024:.1f} KB"
        else:
            return f"{self.file_size / (1024 * 1024):.1f} MB"

    @property
    def bitrate_display(self) -> str:
        """Human-readable bitrate."""
        if self.bitrate is None:
            return "—"
        return f"{self.bitrate} kbps"

    @property
    def sample_rate_display(self) -> str:
        """Human-readable sample rate (e.g., '44.1 kHz')."""
        if self.sample_rate is None:
            return "—"
        return f"{self.sample_rate / 1000:.1f} kHz"

    def __str__(self) -> str:
        parts = [self.format.value.upper()]
        if self.bitrate:
            parts.append(f"{self.bitrate}kbps")
        if self.sample_rate:
            parts.append(f"{self.sample_rate / 1000:.1f}kHz")
        return " · ".join(parts)
