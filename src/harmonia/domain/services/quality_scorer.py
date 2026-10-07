"""Quality scorer — deterministic, objective audio quality ranking."""

from __future__ import annotations

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from harmonia.domain.entities.track_file import TrackFile


def compute_quality_score(track_file: TrackFile) -> int:
    """Compute an objective technical quality score (0-100) for a TrackFile.

    This score ranks audio files by technical quality only. It does NOT
    encode subjective preferences (e.g., "vinyl warmth"). User preferences
    belong in Harmonia AI.

    Scoring breakdown:
    - Lossless format: up to 40 points
    - Lossy bitrate: up to 30 points (scaled)
    - Sample rate: up to 15 points
    - Bit depth: up to 15 points
    - Channels: up to 5 points
    - Codec quality bonus: up to 5 points

    Args:
        track_file: The TrackFile to score.

    Returns:
        Quality score between 0 and 100.
    """
    score = 0

    # ── Lossless vs. lossy (biggest factor) ──────────────────────────────
    if track_file.is_lossless:
        score += 40
    else:
        # Scale lossy bitrate: 320kbps → 30, 128kbps → 12
        bitrate = track_file.bitrate or 0
        score += min(30, bitrate * 30 // 320)

    # ── Sample rate ──────────────────────────────────────────────────────
    sr = track_file.sample_rate or 0
    if sr >= 96000:
        score += 15
    elif sr >= 48000:
        score += 12
    elif sr >= 44100:
        score += 10
    elif sr > 0:
        score += 5

    # ── Bit depth ────────────────────────────────────────────────────────
    bd = track_file.bit_depth or 0
    if bd >= 24:
        score += 15
    elif bd >= 16:
        score += 10
    elif bd > 0:
        score += 5

    # ── Channels ─────────────────────────────────────────────────────────
    channels = track_file.channels or 0
    if channels >= 2:
        score += 5
    elif channels == 1:
        score += 2

    # ── Codec quality bonus ──────────────────────────────────────────────
    # At equivalent bitrates, some codecs are more efficient.
    codec = (track_file.codec or "").lower()
    codec_bonus = {"opus": 5, "aac": 3, "vorbis": 2, "mp3": 0}
    score += codec_bonus.get(codec, 0)

    return max(0, min(100, score))
