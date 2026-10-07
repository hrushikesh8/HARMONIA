"""Audio format value object — file format classification."""

from __future__ import annotations

import enum


class AudioFormat(enum.StrEnum):
    """Recognized audio file formats.

    Determined from file extension during scanning. The codec within
    a container may differ (e.g., an M4A file could contain AAC or ALAC).
    """

    MP3 = "mp3"
    FLAC = "flac"
    M4A = "m4a"
    OGG = "ogg"
    OPUS = "opus"
    WAV = "wav"
    AAC = "aac"
    WMA = "wma"
    ALAC = "alac"
    AIFF = "aiff"

    @property
    def is_lossless_format(self) -> bool:
        """Whether this format is typically lossless.

        Note: This is a format-level heuristic. Actual losslessness
        depends on the codec (e.g., M4A can be AAC or ALAC).
        """
        return self in (AudioFormat.FLAC, AudioFormat.WAV, AudioFormat.ALAC, AudioFormat.AIFF)

    @classmethod
    def from_extension(cls, ext: str) -> AudioFormat | None:
        """Resolve an AudioFormat from a file extension.

        Args:
            ext: File extension with or without leading dot (e.g., ".mp3" or "mp3").

        Returns:
            Matching AudioFormat, or None if not recognized.
        """
        ext_clean = ext.lower().lstrip(".")
        try:
            return cls(ext_clean)
        except ValueError:
            return None
