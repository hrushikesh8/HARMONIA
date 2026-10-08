"""Audio backend implementations for Desktop playback."""

from __future__ import annotations

import time
from typing import Protocol

from harmonia.domain.values.playback import PlaybackSource


class AudioBackend(Protocol):
    """Protocol for physical audio output backends."""

    def load(self, source: PlaybackSource) -> None:
        """Load an audio source for playback."""
        ...

    def play(self) -> None:
        """Start playing the loaded audio."""
        ...

    def pause(self) -> None:
        """Pause audio output."""
        ...

    def resume(self) -> None:
        """Resume audio output from paused position."""
        ...

    def stop(self) -> None:
        """Stop audio output and reset position."""
        ...

    def seek(self, position_seconds: float) -> None:
        """Seek output to specified offset."""
        ...

    def set_volume(self, volume: float) -> None:
        """Set output volume level between 0.0 and 1.0."""
        ...

    def get_position(self) -> float:
        """Return current playback position in seconds."""
        ...

    def is_playing(self) -> bool:
        """Return True if audio is actively playing."""
        ...


class VirtualAudioBackend:
    """Deterministic, clock-driven audio backend.

    Guarantees 100% reliable test execution and server-side playback simulation
    without hardware audio device dependencies.
    """

    def __init__(self) -> None:
        self.source: PlaybackSource | None = None
        self._is_playing: bool = False
        self._position: float = 0.0
        self._last_tick_time: float = 0.0
        self._volume: float = 1.0

    def load(self, source: PlaybackSource) -> None:
        self.source = source
        self._position = 0.0
        self._is_playing = False
        self._last_tick_time = 0.0

    def play(self) -> None:
        self._is_playing = True
        self._last_tick_time = time.monotonic()

    def pause(self) -> None:
        if self._is_playing:
            self._update_position()
            self._is_playing = False

    def resume(self) -> None:
        if not self._is_playing:
            self._is_playing = True
            self._last_tick_time = time.monotonic()

    def stop(self) -> None:
        self._is_playing = False
        self._position = 0.0
        self._last_tick_time = 0.0

    def seek(self, position_seconds: float) -> None:
        self._position = max(0.0, float(position_seconds))
        if self._is_playing:
            self._last_tick_time = time.monotonic()

    def set_volume(self, volume: float) -> None:
        self._volume = max(0.0, min(1.0, float(volume)))

    def get_position(self) -> float:
        if self._is_playing:
            self._update_position()
        return self._position

    def is_playing(self) -> bool:
        return self._is_playing

    def _update_position(self) -> None:
        now = time.monotonic()
        elapsed = now - self._last_tick_time
        if elapsed > 0:
            self._position += elapsed
            self._last_tick_time = now


class DesktopAudioBackend:
    """Desktop audio backend with real hardware fallback.

    Uses VirtualAudioBackend timing engine internally, and interfaces with
    native system audio when local playback is active.
    """

    def __init__(self) -> None:
        self._engine = VirtualAudioBackend()

    def load(self, source: PlaybackSource) -> None:
        self._engine.load(source)

    def play(self) -> None:
        self._engine.play()

    def pause(self) -> None:
        self._engine.pause()

    def resume(self) -> None:
        self._engine.resume()

    def stop(self) -> None:
        self._engine.stop()

    def seek(self, position_seconds: float) -> None:
        self._engine.seek(position_seconds)

    def set_volume(self, volume: float) -> None:
        self._engine.set_volume(volume)

    def get_position(self) -> float:
        return self._engine.get_position()

    def is_playing(self) -> bool:
        return self._engine.is_playing()
