"""Playback domain value objects and types."""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path


class PlaybackStatus(StrEnum):
    """Factual state of the audio transport."""

    IDLE = "idle"
    PLAYING = "playing"
    PAUSED = "paused"
    STOPPED = "stopped"


class RepeatMode(StrEnum):
    """Queue repeat policy."""

    OFF = "off"
    ALL = "all"
    ONE = "one"


@dataclass(frozen=True)
class LocalFileSource:
    """Direct unbuffered filesystem source for native desktop backends."""

    file_path: Path
    mime_type: str = "audio/octet-stream"


@dataclass(frozen=True)
class StreamUriSource:
    """HTTP 206 byte-range stream URL for web/presentation clients."""

    stream_url: str
    mime_type: str = "audio/octet-stream"


@dataclass(frozen=True)
class PlatformUriSource:
    """Platform-specific URI (e.g. Android SAF content://) for mobile backends."""

    content_uri: str
    mime_type: str = "audio/octet-stream"


PlaybackSource = LocalFileSource | StreamUriSource | PlatformUriSource
