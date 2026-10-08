"""Audio backend implementations for Desktop playback."""

from __future__ import annotations

import array
import subprocess
import threading
import time
from collections.abc import Callable, Generator
from typing import Any, Protocol

from harmonia.core.logging import get_logger
from harmonia.domain.values.playback import LocalFileSource, PlaybackSource

logger = get_logger("harmonia.audio.backend")


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

    def set_eof_callback(self, callback: Callable[[], None] | None) -> None:
        """Set callback to trigger when playback reaches the end of track."""
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
        self._eof_callback: Callable[[], None] | None = None

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

    def set_eof_callback(self, callback: Callable[[], None] | None) -> None:
        self._eof_callback = callback

    def trigger_eof(self) -> None:
        """Deterministically simulate reaching the end of the current track."""
        self._is_playing = False
        if self._eof_callback:
            self._eof_callback()

    def _update_position(self) -> None:
        now = time.monotonic()
        elapsed = now - self._last_tick_time
        if elapsed > 0:
            self._position += elapsed
            self._last_tick_time = now


class DesktopAudioBackend:
    """Native desktop audio output backend powered by miniaudio.

    Streams decoded audio directly to physical sound hardware (Windows WASAPI,
    macOS CoreAudio, Linux ALSA/Pulse). For headless CI environments lacking
    physical sound cards, gracefully falls back to an internal clock-driven
    simulation.
    """

    def __init__(self, sample_rate: int = 44100, nchannels: int = 2) -> None:
        self._sample_rate = sample_rate
        self._nchannels = nchannels
        self._source: PlaybackSource | None = None
        self._file_path: str | None = None
        self._is_playing: bool = False
        self._is_paused: bool = False
        self._volume: float = 1.0
        self._frames_played: int = 0
        self._eof_callback: Callable[[], None] | None = None

        # Concurrency & device management
        self._op_lock = threading.Lock()
        self._device: Any | None = None
        self._proc: subprocess.Popen[bytes] | None = None
        self._is_virtual_fallback: bool = False
        self._virtual_last_tick: float = 0.0

    def load(self, source: PlaybackSource) -> None:
        """Load an audio source for native playback."""
        with self._op_lock:
            self._is_playing = False
            self._is_paused = False
            self._frames_played = 0
            self._source = source

            if isinstance(source, LocalFileSource):
                if not source.file_path.is_file():
                    raise FileNotFoundError(f"Audio file does not exist on disk: {source.file_path}")
                self._file_path = str(source.file_path)
            else:
                self._file_path = None
                logger.info("Non-local audio source provided to DesktopAudioBackend: %s", source)

        # Stop active hardware outside the lock to prevent deadlock with worker thread
        self._teardown_device()

    def play(self) -> None:
        """Start or resume physical audio playback."""
        with self._op_lock:
            if not self._file_path and not self._source:
                return

            if self._is_paused:
                self._is_paused = False
                self._virtual_last_tick = time.monotonic()
                return

            self._is_playing = True
            self._is_paused = False
            self._virtual_last_tick = time.monotonic()

            if self._file_path:
                self._launch_device()

    def pause(self) -> None:
        """Pause audio output."""
        with self._op_lock:
            if self._is_playing and not self._is_paused:
                self._is_paused = True
                if self._is_virtual_fallback:
                    elapsed = time.monotonic() - self._virtual_last_tick
                    if elapsed > 0:
                        self._frames_played += int(elapsed * self._sample_rate)

    def resume(self) -> None:
        """Resume audio output from paused position."""
        with self._op_lock:
            if self._is_paused:
                self._is_paused = False
                self._virtual_last_tick = time.monotonic()

    def stop(self) -> None:
        """Stop audio output and reset position."""
        with self._op_lock:
            self._is_playing = False
            self._is_paused = False

        # Terminate hardware device outside the lock
        self._teardown_device()

        with self._op_lock:
            self._frames_played = 0

    def seek(self, position_seconds: float) -> None:
        """Seek playback to target offset in seconds."""
        target_pos = max(0.0, float(position_seconds))
        was_playing = False

        with self._op_lock:
            self._frames_played = int(target_pos * self._sample_rate)
            self._virtual_last_tick = time.monotonic()
            was_playing = self._is_playing and bool(self._file_path) and not self._is_virtual_fallback

        if was_playing:
            self._teardown_device()
            with self._op_lock:
                if self._is_playing:
                    self._launch_device()

    def set_volume(self, volume: float) -> None:
        """Set output volume level between 0.0 and 1.0."""
        self._volume = max(0.0, min(1.0, float(volume)))

    def get_position(self) -> float:
        """Return current playback position in seconds."""
        if self._is_virtual_fallback and self._is_playing and not self._is_paused:
            now = time.monotonic()
            elapsed = now - self._virtual_last_tick
            if elapsed > 0:
                self._frames_played += int(elapsed * self._sample_rate)
                self._virtual_last_tick = now

        return self._frames_played / float(self._sample_rate)

    def is_playing(self) -> bool:
        """Return True if audio is actively outputting sound."""
        return self._is_playing and not self._is_paused

    def set_eof_callback(self, callback: Callable[[], None] | None) -> None:
        """Register callback invoked on track completion (EOF)."""
        self._eof_callback = callback

    # ── Internal Audio Engine Implementation ────────────────────────────────

    def _open_stream_generator(self, seek_frame: int) -> Generator[Any, int, None]:
        """Create PCM stream generator for the current file."""
        import miniaudio

        assert self._file_path is not None
        file_path = self._file_path

        # Try native miniaudio decoding first (MP3, FLAC, Vorbis, WAV)
        try:
            return miniaudio.stream_file(  # type: ignore[no-any-return]
                file_path,
                sample_rate=self._sample_rate,
                nchannels=self._nchannels,
                seek_frame=seek_frame,
            )
        except Exception as exc:
            logger.debug("miniaudio native decode failed (%s); trying ffmpeg pipe", exc)

        # Fallback to FFmpeg raw PCM pipe (handles AAC, M4A, Opus, etc.)
        seek_sec = seek_frame / float(self._sample_rate)
        cmd = [
            "ffmpeg",
            "-y",
            "-ss",
            f"{seek_sec:.3f}",
            "-i",
            file_path,
            "-f",
            "s16le",
            "-ac",
            str(self._nchannels),
            "-ar",
            str(self._sample_rate),
            "pipe:1",
        ]
        self._proc = subprocess.Popen(
            cmd,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )

        def _ffmpeg_gen() -> Generator[bytes, int, None]:
            num_frames = yield b""
            bytes_per_frame = self._nchannels * 2
            while True:
                req_bytes = num_frames * bytes_per_frame
                chunk = b""
                if self._proc and self._proc.stdout:
                    chunk = self._proc.stdout.read(req_bytes)
                if not chunk:
                    break
                num_frames = yield chunk

        g = _ffmpeg_gen()
        next(g)
        return g

    def _launch_device(self) -> None:
        """Initialize and start physical miniaudio device with playback stream."""
        import miniaudio

        try:
            seek_frame = self._frames_played
            stream = self._open_stream_generator(seek_frame)
            bytes_per_frame = self._nchannels * 2

            def audio_callback_generator() -> Generator[bytes | array.array[int], int, None]:
                num_frames = yield b""
                while True:
                    if not self._is_playing:
                        yield b""
                        break

                    # If paused, output silence to keep hardware pipeline alive
                    if self._is_paused:
                        num_frames = yield b"\x00" * (num_frames * bytes_per_frame)
                        continue

                    vol = self._volume
                    try:
                        samples = stream.send(num_frames)
                        self._frames_played += num_frames

                        # Apply linear software volume scaling if needed
                        if vol < 0.999:
                            if vol <= 0.001:
                                samples = b"\x00" * (num_frames * bytes_per_frame)
                            elif isinstance(samples, array.array):
                                samples = array.array("h", (int(s * vol) for s in samples))

                        num_frames = yield samples
                    except StopIteration:
                        # EOF reached
                        self._is_playing = False
                        if self._eof_callback:
                            threading.Thread(target=self._eof_callback, daemon=True).start()
                        yield b""
                        break

            g = audio_callback_generator()
            next(g)

            dev = miniaudio.PlaybackDevice(
                sample_rate=self._sample_rate,
                nchannels=self._nchannels,
            )
            dev.start(g)
            self._device = dev
            self._is_virtual_fallback = False
            logger.info("Native hardware audio output active on miniaudio device: %s", self._file_path)

        except (miniaudio.MiniaudioError, Exception) as exc:
            logger.warning(
                "Physical audio output unavailable (%s); falling back to virtual clock",
                exc,
            )
            self._device = None
            self._is_virtual_fallback = True
            self._virtual_last_tick = time.monotonic()

    def _teardown_device(self) -> None:
        """Halt miniaudio device and close subprocess pipes without holding locks."""
        dev = self._device
        self._device = None
        if dev is not None:
            try:
                dev.stop()
                dev.close()
            except Exception:
                pass

        proc = self._proc
        self._proc = None
        if proc is not None:
            try:
                proc.terminate()
                proc.wait(timeout=0.2)
            except Exception:
                pass

