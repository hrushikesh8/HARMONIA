# Harmonia Architecture Validation: Audio Engine & Playback Contract

**Document Classification:** Pre-Implementation Freeze — Finalized Playback Contract  
**Status:** FROZEN & APPROVED  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Rule:** *HTTP 206 is a file transport mechanism, not the playback abstraction. Define a clean domain PlaybackPort and abstract PlaybackSource so backends can access audio via native files, platform URIs, or HTTP streams without forcing network transport.*

---

## 1. The PlaybackPort Domain Contract

In Harmonia's Clean Architecture, the Application and Domain layers interact with audio playback strictly via the **`PlaybackPort` protocol** (`harmonia.domain.ports.playback_port`):

```python
class PlaybackPort(Protocol):
    """Abstract playback port for controlling audio transport and state."""

    def play(self) -> None:
        """Begin or resume playback of the active track."""
        ...

    def pause(self) -> None:
        """Pause audio playback at current position."""
        ...

    def resume(self) -> None:
        """Resume audio playback from paused position."""
        ...

    def stop(self) -> None:
        """Stop playback and reset position to beginning."""
        ...

    def seek(self, position_seconds: float) -> None:
        """Seek playback to specific offset in seconds."""
        ...

    def next(self) -> None:
        """Advance to the next track in the queue."""
        ...

    def previous(self) -> None:
        """Return to previous track or restart current track."""
        ...

    def set_volume(self, volume: float) -> None:
        """Set output volume level (clamped between 0.0 and 1.0)."""
        ...

    def set_queue(self, track_ids: list[UUID], play_index: int = 0) -> None:
        """Replace the active playback queue."""
        ...

    def insert_play_next(self, track_id: UUID) -> None:
        """Insert a track to play immediately after the active track."""
        ...

    def get_state(self) -> PlaybackState:
        """Return the current factual playback state snapshot."""
        ...
```

---

## 2. The PlaybackSource Abstraction

To ensure Harmonia Core never forces local playback to route through an HTTP network stack, audio bytes are accessed via polymorphic **`PlaybackSource`** resolvers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PLAYBACK SOURCE RESOLUTION                      │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
                   [TrackFile Entity (track_file_id)]
                                   │
         ┌─────────────────────────┼─────────────────────────┐
         │                         │                         │
         ▼                         ▼                         ▼
 [1. LocalFileSource]      [2. StreamUriSource]    [3. PlatformUriSource]
 (Direct Path on Disk)     (HTTP 206 Byte-Range)   (Android SAF / Content)
 • Native Desktop Backend  • Embedded Desktop Web  • Mobile Media3 Engine
 • libmpv / local audio    • Remote Web Client     • Scoped Storage URI
 • Zero network overhead   • Zero client code      • Direct OS handle
```

### 2.1 Backend Decoupling:
1. **Desktop Native (libmpv / local):** Resolves `LocalFileSource(file_path=Path)`. The audio decoder directly reads the file from disk using native OS file descriptors. Zero HTTP overhead.
2. **Desktop Web UI (`/` & `/ui`):** Resolves `StreamUriSource(url="/api/v1/stream/{file_id}")`. The browser HTML5 audio element streams bytes over HTTP 206 Partial Content Range.
3. **Harmonia Mobile (Android):** Resolves `PlatformUriSource(uri="content://...")` or local cache path. ExoPlayer plays directly from local device storage without calling an HTTP server.

---

## 3. Platform Audio Backends Summary

```
┌────────────────────────────────────────────────────────────────────────┐
│                        VALIDATED AUDIO BACKENDS                        │
├────────────────────┬───────────────────────────────────────────────────┤
│ Backend            │ Architecture & Capabilities                       │
├────────────────────┼───────────────────────────────────────────────────┤
│ 1. Phase 4 Desktop │ Core Playback Controller + Web UI Dock Player     │
│    Playback        │ • Coordinates queue state and playback commands.  │
│                    │ • Streams audio to UI via HTTP 206.               │
├────────────────────┼───────────────────────────────────────────────────┤
│ 2. Phase 5 Mobile  │ AndroidX Media3 (ExoPlayer)                       │
│    (Android)       │ • Autonomous local foreground service.            │
│                    │ • Software FLAC/ALAC decoding.                    │
│                    │ • Native AudioTrack 24-bit high-res pipeline.     │
├────────────────────┼───────────────────────────────────────────────────┤
│ 3. Phase 6 Desktop │ Optional libmpv Audiophile Backend                │
│    Audiophile      │ • Bit-perfect WASAPI Exclusive Mode (Windows).    │
│                    │ • Direct ALSA device addressing (Linux).          │
│                    │ • Automatic external DAC hardware clock switching.│
└────────────────────┴───────────────────────────────────────────────────┘
```

---

## 4. Final Classification

- **`PlaybackPort` Contract:** **CONFIRMED & FROZEN**.
- **`PlaybackSource` Abstraction:** **CONFIRMED & FROZEN** (Decoupled from HTTP).
- **HTTP 206 Role:** **CONFIRMED AS TRANSPORT ONLY** (Used by web presentation layer).
- **Desktop Phase 4 Implementation:** **CONFIRMED** (Core playback controller + UI dock).
