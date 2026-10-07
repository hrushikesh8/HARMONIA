"""Mutagen-based audio metadata reader.

Reads embedded metadata from MP3 (ID3), FLAC (Vorbis comments),
M4A/MP4, OGG, and other formats supported by mutagen.
"""

from __future__ import annotations

import contextlib
import hashlib
from dataclasses import dataclass
from typing import TYPE_CHECKING

import mutagen

from harmonia.core.exceptions import MetadataExtractionError
from harmonia.core.logging import get_logger

if TYPE_CHECKING:
    from pathlib import Path

logger = get_logger(__name__)


@dataclass
class ExtractedMetadata:
    """Raw metadata extracted from a music file.

    All fields are optional — not every file contains every tag.
    This is the raw extraction result before normalization or resolution.
    """

    title: str | None = None
    artist: str | None = None
    album_artist: str | None = None
    album: str | None = None
    track_number: int | None = None
    total_tracks: int | None = None
    disc_number: int | None = None
    total_discs: int | None = None
    year: int | None = None
    genre: str | None = None
    duration_seconds: float | None = None
    codec: str | None = None
    bitrate: int | None = None  # kbps
    sample_rate: int | None = None  # Hz
    bit_depth: int | None = None
    channels: int | None = None
    is_lossless: bool = False

    # MusicBrainz IDs (if embedded in tags)
    mb_track_id: str | None = None
    mb_artist_id: str | None = None
    mb_album_id: str | None = None


def extract_metadata(file_path: Path) -> ExtractedMetadata:
    """Extract metadata from an audio file using mutagen.

    Supports: MP3 (ID3), FLAC, M4A/MP4, OGG Vorbis, OGG Opus.

    Args:
        file_path: Path to the audio file.

    Returns:
        ExtractedMetadata with all available fields populated.

    Raises:
        MetadataExtractionError: If the file cannot be read or is corrupt.
    """
    try:
        audio = mutagen.File(str(file_path), easy=True)
    except Exception as e:
        raise MetadataExtractionError(str(file_path), str(e)) from e

    if audio is None:
        raise MetadataExtractionError(str(file_path), "Unrecognized audio format")

    meta = ExtractedMetadata()

    # Extract tags (easy mode normalizes across formats).
    meta.title = _get_tag(audio, "title")
    meta.artist = _get_tag(audio, "artist")
    meta.album_artist = _get_tag(audio, "albumartist") or _get_tag(audio, "album_artist")
    meta.album = _get_tag(audio, "album")
    meta.genre = _get_tag(audio, "genre")
    meta.year = _parse_year(_get_tag(audio, "date"))

    # Track/disc numbers.
    meta.track_number, meta.total_tracks = _parse_number_pair(
        _get_tag(audio, "tracknumber")
    )
    meta.disc_number, meta.total_discs = _parse_number_pair(
        _get_tag(audio, "discnumber")
    )

    # MusicBrainz IDs.
    meta.mb_track_id = _get_tag(audio, "musicbrainz_trackid")
    meta.mb_artist_id = _get_tag(audio, "musicbrainz_artistid")
    meta.mb_album_id = _get_tag(audio, "musicbrainz_albumid")

    # Audio properties (from stream info, not tags).
    _extract_audio_properties(file_path, audio, meta)

    return meta


def compute_file_hash(file_path: Path, algorithm: str = "sha256") -> str:
    """Compute a hash of a file's contents.

    Args:
        file_path: Path to the file.
        algorithm: Hash algorithm (default: sha256).

    Returns:
        Hex digest string.
    """
    h = hashlib.new(algorithm)
    with open(file_path, "rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


# ── Internal helpers ──────────────────────────────────────────────────────────


def _get_tag(audio: mutagen.FileType, key: str) -> str | None:
    """Safely get a tag value, returning None if missing.

    mutagen returns lists for tag values; we take the first element.
    """
    try:
        values = audio.get(key)
        if values:
            value = values[0] if isinstance(values, list) else values
            return str(value).strip() or None
    except (KeyError, IndexError, TypeError):
        pass
    return None


def _parse_year(date_str: str | None) -> int | None:
    """Extract a 4-digit year from a date string.

    Handles: "2023", "2023-05-15", "May 2023", etc.
    """
    if not date_str:
        return None
    # Find the first 4-digit number.
    import re

    match = re.search(r"\b(1[89]\d{2}|20\d{2})\b", date_str)
    if match:
        return int(match.group(1))
    return None


def _parse_number_pair(value: str | None) -> tuple[int | None, int | None]:
    """Parse 'N' or 'N/M' format into (number, total).

    Examples: '3' → (3, None), '3/12' → (3, 12).
    """
    if not value:
        return None, None
    parts = value.split("/")
    try:
        number = int(parts[0].strip())
    except (ValueError, IndexError):
        return None, None
    total = None
    if len(parts) > 1:
        with contextlib.suppress(ValueError):
            total = int(parts[1].strip())
    return number, total


def _extract_audio_properties(
    file_path: Path, audio: mutagen.FileType, meta: ExtractedMetadata
) -> None:
    """Extract technical audio properties from the stream info."""
    info = audio.info
    if info is None:
        return

    meta.duration_seconds = getattr(info, "length", None)
    meta.sample_rate = getattr(info, "sample_rate", None)
    meta.channels = getattr(info, "channels", None)

    # Bitrate — mutagen reports in bps, we want kbps.
    raw_bitrate = getattr(info, "bitrate", None)
    if raw_bitrate is not None:
        meta.bitrate = raw_bitrate // 1000 if raw_bitrate > 1000 else raw_bitrate

    # Format-specific properties.
    suffix = file_path.suffix.lower()

    if suffix == ".mp3":
        meta.codec = "mp3"
        meta.is_lossless = False

    elif suffix == ".flac":
        meta.codec = "flac"
        meta.is_lossless = True
        meta.bit_depth = getattr(info, "bits_per_sample", None)

    elif suffix in (".m4a", ".mp4"):
        # M4A can be AAC (lossy) or ALAC (lossless).
        meta.codec = "aac"  # Default assumption.
        meta.is_lossless = False
        meta.bit_depth = getattr(info, "bits_per_sample", None)
        if meta.bit_depth and meta.bit_depth > 16:
            # Likely ALAC.
            meta.codec = "alac"
            meta.is_lossless = True

    elif suffix == ".ogg":
        meta.codec = "vorbis"
        meta.is_lossless = False

    elif suffix == ".opus":
        meta.codec = "opus"
        meta.is_lossless = False

    elif suffix in (".wav", ".aiff"):
        meta.codec = suffix.lstrip(".")
        meta.is_lossless = True
        meta.bit_depth = getattr(info, "bits_per_sample", None)

    elif suffix == ".wma":
        meta.codec = "wma"
        meta.is_lossless = False
