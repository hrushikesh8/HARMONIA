# Harmonia Phase 4 Implementation Specification: Core Playback Engine & Factual Telemetry

**Document Classification:** Implementation Specification — Pre-Implementation Freeze  
**Status:** FROZEN & APPROVED FOR IMPLEMENTATION  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Principle:** *"Harmonia owns the music. Harmonia AI understands the person."*

---

## 1. Objective

Deliver a rock-solid, deterministic, local-first audio playback engine for Harmonia Core on Desktop. This encompasses:
- Abstracting playback control through a pure domain `PlaybackPort` and polymorphic `PlaybackSource`.
- Managing a dual-layer queue (manual "Up Next" FIFO queue + deterministic Context queue) with contiguous "Play Next" insertion.
- Persisting playback and queue state deterministically across process restarts.
- Recording factual, immutable `ListeningEvent` telemetry linking logical tracks to physical files.
- Providing a background, debounced filesystem watcher that triggers incremental imports without locking SQLite or interrupting audio playback.
- Exposing clean, secure REST endpoints that strictly conceal host filesystem paths.

---

## 2. Scope

The following components and capabilities are strictly **IN SCOPE** for Phase 4:

1. **Domain Abstraction:**
   - Domain protocol `PlaybackPort` in `harmonia.domain.ports.playback_port`.
   - Domain entities/values: `PlaybackState`, `PlaybackSource`, `QueueItem`, `QueueSnapshot`, `RepeatMode`, `ShuffleState`.
   - Domain entity `ListeningEvent` with dual pointers (`track_id` and `track_file_id`).
2. **Application Layer:**
   - `PlaybackService` coordinating playback commands, queue mutations, and listening session lifecycle.
   - Session tracking logic emitting completed/skipped facts based on factual listen duration.
3. **Infrastructure Layer:**
   - Desktop Playback Controller adapter implementing `PlaybackPort`.
   - SQLite persistence for queue state and `listening_events` table (SQLAlchemy + Alembic migration).
   - Background filesystem watcher (`watchdog`) with thread-safe debouncing and coalescing queue.
4. **Presentation Layer:**
   - REST API endpoints for playback transport (`/api/v1/playback/*`), queue operations (`/api/v1/queue/*`), and listening events (`/api/v1/listening-events/*`).
   - Desktop UI Dock Player integration consuming REST endpoints and HTTP 206 audio stream.
5. **Quality & Tooling:**
   - Comprehensive unit, integration, and regression test suites.
   - Alembic migration script for new database tables.
   - Git restore points (`restore-point/phase-4-start`, `restore-point/phase-4-complete`).

### Optional Only if Trivial
- Ratings (1–5 stars) and favorite boolean flags on `Track` and `Album`. If adding ratings expands scope or delays core playback, it must be deferred.

---

## 3. Non-Goals

The following areas are **STRICTLY OUT OF SCOPE** for Phase 4:

- **Harmonia AI:** No subjective taste models, genre affinity vectors, or conversational NLU.
- **Mobile Platforms:** No Android or iOS code, Media3, or Room persistence.
- **LocalSync:** No local network pairing, mDNS discovery, or TLS peer-to-peer sync.
- **Metadata Web Consensus:** No external queries to MusicBrainz, Discogs, or AcoustID.
- **Acoustic Fingerprinting:** No Chromaprint/`fpcalc` binary subprocesses.
- **Advanced DSP & Audiophile Engine:** No AutoEQ parametric profiles, FIR crossfeed convolver, or brickwall limiter.
- **Hardware Output:** No direct WASAPI Exclusive or ALSA hardware clock switching.
- **File Modifications:** No audio file tag write-backs; files remain strictly read-only.
- **Cloud/Remote Server:** No remote streaming, public internet binding, or multi-user authentication.

---

## 4. Domain Model

The Phase 4 domain models reside in `harmonia.domain.entities` and `harmonia.domain.values`:

```python
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from uuid import UUID


class PlaybackStatus(str, Enum):
    IDLE = "idle"
    PLAYING = "playing"
    PAUSED = "paused"
    STOPPED = "stopped"


class RepeatMode(str, Enum):
    OFF = "off"
    ALL = "all"
    ONE = "one"


@dataclass(frozen=True)
class QueueItem:
    """An entry in the playback queue."""

    id: UUID
    track_id: UUID
    track_file_id: UUID | None
    title: str
    artist_name: str
    duration_seconds: float
    is_manual: bool = False  # True if manually enqueued via 'Up Next'


@dataclass
class PlaybackState:
    """Current factual state of the playback engine."""

    status: PlaybackStatus = PlaybackStatus.IDLE
    current_track_id: UUID | None = None
    current_track_file_id: UUID | None = None
    position_seconds: float = 0.0
    duration_seconds: float = 0.0
    volume: float = 1.0  # Clamped to [0.0, 1.0]
    repeat_mode: RepeatMode = RepeatMode.OFF
    shuffle_enabled: bool = False
    current_queue_index: int = -1
    active_session_id: UUID | None = None
```

---

## 5. PlaybackPort

The domain interface protocol is defined in `harmonia.domain.ports.playback_port`:

```python
from typing import Protocol
from uuid import UUID


class PlaybackPort(Protocol):
    """Abstract playback port for controlling audio transport and queue state."""

    def play(self) -> None:
        """Begin or resume playback of the active track."""
        ...

    def pause(self) -> None:
        """Pause audio playback at current position."""
        ...

    def resume(self) -> None:
        """Resume playback from paused position."""
        ...

    def stop(self) -> None:
        """Stop playback and reset position to 0.0s."""
        ...

    def seek(self, position_seconds: float) -> None:
        """Seek playback to specific offset in seconds."""
        ...

    def next(self) -> None:
        """Advance to the next track in the queue."""
        ...

    def previous(self) -> None:
        """Return to previous track or beginning of current track."""
        ...

    def set_volume(self, volume: float) -> None:
        """Set output volume level, clamped to [0.0, 1.0]."""
        ...

    def set_queue(
        self, track_ids: list[UUID], play_index: int = 0
    ) -> None:
        """Replace the active context queue and start playback."""
        ...

    def insert_play_next(self, track_id: UUID) -> None:
        """Insert track into manual queue immediately after the current track."""
        ...

    def append_up_next(self, track_id: UUID) -> None:
        """Append track to the end of the manual Up Next queue."""
        ...

    def remove_from_queue(self, index: int) -> None:
        """Remove a track at a specific index from the queue."""
        ...

    def clear_queue(self) -> None:
        """Clear all queued items and stop playback."""
        ...

    def toggle_shuffle(self) -> bool:
        """Toggle shuffle state and return new state."""
        ...

    def set_repeat_mode(self, mode: RepeatMode) -> None:
        """Set repeat mode."""
        ...

    def get_state(self) -> PlaybackState:
        """Return current factual playback state."""
        ...

    def get_queue(self) -> list[QueueItem]:
        """Return full queue list in active playback order."""
        ...
```

---

## 6. PlaybackSource

To ensure Harmonia Core is never locked into HTTP, audio backends resolve tracks through polymorphic `PlaybackSource` objects:

```python
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class LocalFileSource:
    """Direct filesystem path for native desktop engines."""

    file_path: Path
    mime_type: str


@dataclass(frozen=True)
class StreamUriSource:
    """HTTP 206 byte-range stream URL for web frontends."""

    stream_url: str
    mime_type: str


@dataclass(frozen=True)
class PlatformUriSource:
    """Platform-specific URI (e.g. Android SAF content://)."""

    content_uri: str
    mime_type: str


PlaybackSource = LocalFileSource | StreamUriSource | PlatformUriSource
```

**Resolution Rule:** The `PlaybackService` inspects the active backend adapter:
- For web clients (embedded Desktop UI), it provides `StreamUriSource(f"/api/v1/tracks/{track_id}/stream", mime_type)`.
- For local desktop backends, it resolves the optimal `TrackFile` and provides `LocalFileSource(track_file.file_path, mime_type)`.

---

## 7. Queue Architecture

Harmonia implements a dual-layer queue separating manual intent from playlist context:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HARMONIA DUAL QUEUE MODEL                       │
├────────────────────────────────────────────────────────────────────────┤
│ 1. MANUAL "UP NEXT" QUEUE (FIFO)                                       │
│    • Tracks explicitly queued by the user via 'Play Next' or 'Add to   │
│      Queue'.                                                           │
│    • Highest priority: always plays before context queue.              │
│    • Consumed upon playback (pop from front).                          │
├────────────────────────────────────────────────────────────────────────┤
│ 2. CONTEXT QUEUE (Sequential or Shuffled)                              │
│    • The album, playlist, or artist catalog the user started playing.  │
│    • Maintains stable index pointer (current_index).                   │
│    • When shuffle is enabled, uses Auxio contiguous insertion:         │
│      newly inserted tracks are placed immediately after the current    │
│      pointer without reshuffling the remaining future tracks.          │
└────────────────────────────────────────────────────────────────────────┘
```

### Resolution Logic on `next()`:
1. If `repeat_mode == RepeatMode.ONE`: seek current track to 0.0s.
2. If `manual_queue` is not empty: pop head of `manual_queue`, set as active track.
3. If `manual_queue` is empty:
   - Advance `context_pointer += 1`.
   - If `context_pointer >= len(context_queue)`:
     - If `repeat_mode == RepeatMode.ALL`: reset `context_pointer = 0`.
     - Else: set status to `STOPPED`.

---

## 8. ListeningEvent Model

The `ListeningEvent` is an immutable factual record committed to SQLite upon track transition:

```python
@dataclass(frozen=True)
class ListeningEvent:
    """Factual telemetry record of a listening session."""

    id: UUID  # uuid.uuid4()
    track_id: UUID  # Logical musical work (authoritative link)
    track_file_id: UUID | None  # Physical file played (technical fidelity link)
    timestamp_utc: datetime  # Playback start timestamp (UTC)
    started_at_sec: float  # Track offset where playback began
    ended_at_sec: float  # Track offset where playback ended
    duration_listened_seconds: float  # Cumulative active playback time (excluding pauses)
    track_duration_seconds: float  # Total track duration
    was_completed: bool  # True if duration_listened >= 0.8 * track_duration (or >= 240s)
    was_skipped: bool  # True if duration_listened < 30.0s and next/stop called
    playback_source: str  # "queue", "album", "playlist", "search"
    device_id: UUID | None = None  # None in Phase 4; populated in Phase 5 LocalSync
```

### Database Schema (`listening_events`):
```sql
CREATE TABLE listening_events (
    id TEXT PRIMARY KEY,                       -- UUID string (UUIDv4)
    track_id TEXT NOT NULL,                   -- Foreign key to tracks.id
    track_file_id TEXT,                       -- Foreign key to track_files.id
    timestamp_utc TIMESTAMP NOT NULL,         -- Indexed UTC timestamp
    started_at_sec REAL NOT NULL,
    ended_at_sec REAL NOT NULL,
    duration_listened_seconds REAL NOT NULL,
    track_duration_seconds REAL NOT NULL,
    was_completed BOOLEAN NOT NULL,
    was_skipped BOOLEAN NOT NULL,
    playback_source TEXT NOT NULL,
    device_id TEXT,                           -- NULL in Phase 4
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE,
    FOREIGN KEY(track_file_id) REFERENCES track_files(id) ON DELETE SET NULL
);

CREATE INDEX idx_listening_events_track_id ON listening_events(track_id);
CREATE INDEX idx_listening_events_timestamp ON listening_events(timestamp_utc);
```

---

## 9. Persistence & Snapshot Restoration

To prevent loss of playback state across application restarts, Harmonia Core stores a queue snapshot table:

```sql
CREATE TABLE playback_queue_snapshots (
    id INTEGER PRIMARY KEY CHECK (id = 1),     -- Single-row singleton table
    current_track_id TEXT,
    current_track_file_id TEXT,
    position_seconds REAL NOT NULL DEFAULT 0.0,
    volume REAL NOT NULL DEFAULT 1.0,
    repeat_mode TEXT NOT NULL DEFAULT 'off',
    shuffle_enabled BOOLEAN NOT NULL DEFAULT 0,
    queue_json TEXT NOT NULL,                 -- Serialized list of QueueItem dicts
    current_queue_index INTEGER NOT NULL DEFAULT -1,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### Restoration Protocol:
1. On startup, `PlaybackService` queries `playback_queue_snapshots`.
2. If row exists and `queue_json` is valid:
   - Deserialize queue items and verify that `current_track_id` still exists in the library.
   - Restore queue, active track pointer, volume, repeat, and shuffle flags.
   - Set status to `PAUSED` at `position_seconds` (do NOT auto-play on launch).
3. If row is missing or corrupt: initialize with empty state and log a warning.

---

## 10. Filesystem Watcher

The filesystem watcher automatically detects library file modifications without degrading playback performance:

```
┌────────────────────────────────────────────────────────────────────────┐
│                     FILESYSTEM WATCHER PIPELINE                        │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │ WATCHDOG DIRECTORY OBSERVER (Background Thread)                      │
 │ • Listens for on_created, on_modified, on_deleted, on_moved.          │
 │ • Filters non-audio files (ignores .tmp, .DS_Store, hidden files).   │
 └─────────────────────────────────┬────────────────────────────────────┘
                                   │ Raw FS Events
                                   ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │ DEBOUNCE & COALESCING BUFFER                                         │
 │ • Groups events by path over a sliding debounce window (2.0 seconds).│
 │ • Coalesces rapid write bursts (e.g. taggers writing multiple blocks)│
 │   into a single terminal event.                                      │
 └─────────────────────────────────┬────────────────────────────────────┘
                                   │ Coalesced Batch
                                   ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │ ASYNC IMPORT QUEUE (QueueService)                                    │
 │ • Passes distinct modified/created paths to existing Phase 2         │
 │   incremental import pipeline.                                       │
 │ • Operates in a background worker thread; NEVER locks SQLite on      │
 │   the main/playback thread.                                          │
 └──────────────────────────────────────────────────────────────────────┘
```

### Safety Rules:
- **Debounce Window:** 2000 ms sliding window.
- **Lock Avoidance:** All library updates are dispatched to the existing `ImportService` using transactional reconciliation (`WAL` mode).
- **Graceful Shutdown:** Watcher thread stops cleanly when `PlaybackService` or FastAPI server shuts down.

---

## 11. REST API Specification

All Phase 4 endpoints are nested under `/api/v1` and bound to loopback:

### Playback Transport Endpoints:
- `POST /api/v1/playback/play` — Start or resume playback.
- `POST /api/v1/playback/pause` — Pause active playback.
- `POST /api/v1/playback/stop` — Stop playback and reset position.
- `POST /api/v1/playback/seek` — Seek to position (`{"position_seconds": float}`).
- `POST /api/v1/playback/next` — Advance to next track.
- `POST /api/v1/playback/previous` — Return to previous track.
- `POST /api/v1/playback/volume` — Set volume (`{"volume": float}`).
- `POST /api/v1/playback/repeat` — Set repeat mode (`{"mode": "off" | "all" | "one"}`).
- `POST /api/v1/playback/shuffle` — Toggle shuffle state (`{"enabled": bool}`).
- `GET /api/v1/playback/state` — Return current `PlaybackStateResponse`.

### Queue Management Endpoints:
- `GET /api/v1/queue` — Return current queue items and active pointer.
- `POST /api/v1/queue/set` — Replace queue (`{"track_ids": list[UUID], "play_index": int}`).
- `POST /api/v1/queue/play-next` — Insert track immediately after current (`{"track_id": UUID}`).
- `POST /api/v1/queue/append` — Append track to queue (`{"track_id": UUID}`).
- `DELETE /api/v1/queue/{index}` — Remove item at index.
- `DELETE /api/v1/queue` — Clear the queue.

### Listening Events Endpoints:
- `GET /api/v1/listening-events` — Paginated history of listening events (`page`, `limit`).
- `GET /api/v1/listening-events/stats` — Summary analytics (total tracks played, completion rate).

---

## 12. Test Strategy

Harmonia maintains strict zero-regression quality. Required test suites:

### Unit Tests (`tests/unit/`):
- `test_queue.py`:
  - Up Next FIFO priority over Context queue.
  - Repeat modes (`OFF`, `ALL`, `ONE`) behavior at boundary conditions.
  - Auxio contiguous shuffle insertion preserves existing queue ordering.
  - Empty queue safety on `next()` / `previous()`.
- `test_listening_event_lifecycle.py`:
  - Verify `was_completed = True` when duration $\ge 80\%$ or $\ge 240\text{s}$.
  - Verify `was_skipped = True` when track changed before $30.0\text{s}$.
  - Verify pausing/seeking does not duplicate events.
- `test_watcher_debounce.py`:
  - Verify file write bursts within $2.0\text{s}$ produce exactly one import task.

### Integration Tests (`tests/integration/`):
- `test_playback_persistence.py`:
  - Save queue snapshot to SQLite $\to$ restart service $\to$ verify restored state.
  - Corrupt snapshot recovery test (graceful empty fallback).
- `test_playback_api.py`:
  - End-to-end HTTP calls verifying state transitions and range streaming integration.
- `test_listening_events_repo.py`:
  - Database round-trip verifying foreign key integrity and indexed queries.

### Regression Verification:
- All Phase 1, Phase 2, and Phase 3 tests must pass with 100% green status.

---

## 13. Failure Handling & Resilience

1. **Missing Audio File during Playback:**
   - If an active `TrackFile` is unreadable or deleted while playing, mark `TrackFile` availability as missing, log an error, skip to the next track, and emit an alert to the client.
2. **Database Locked / Busy Timeout:**
   - SQLite WAL mode with 5000ms busy timeout ensures telemetry writes never block UI read queries.
3. **Corrupted Audio Header:**
   - Catch decoding/streaming exceptions gracefully without crashing the server process.

---

## 14. Observability & Logging

- Standardized Python structured logging using Harmonia logger format.
- Telemetry logging: Log event creation at `INFO` level:
  `ListeningEvent recorded: track_id={track_id} duration={duration}s completed={was_completed}`
- Watcher logging: Log file system detections at `DEBUG` level; log import completions at `INFO`.

---

## 15. Security & Isolation

- **Path Concealment:** Internal operating system filesystem paths (`C:\Users\...`) must NEVER be exposed in any REST DTO or error message. All tracks are referenced strictly via UUID.
- **Loopback Enforcement:** API remains strictly bound to `127.0.0.1:8000`.
- **Read-Only Audio Files:** Playback and watching processes open audio files strictly with read permissions (`rb`).

---

## 16. Measurable Performance Targets

All performance criteria are designated as **HARMONIA ENGINEERING TARGETS**:

1. **Queue Mutation Latency:** Adding 1,000 tracks to the queue must execute in $< 50\text{ ms}$.
2. **State Snapshot Write:** Writing a queue snapshot to SQLite must take $< 15\text{ ms}$.
3. **Telemetry Recording:** Committing a `ListeningEvent` row must take $< 10\text{ ms}$.
4. **Filesystem Event Coalescing:** Event debouncer must filter $95\%+$ of redundant OS burst events during batch tagging operations.
5. **Startup Restoration:** Playback state restoration on server boot must complete in $< 100\text{ ms}$.

---

## 17. Migration Requirements

- Create Alembic migration: `versions/004_phase_4_playback_and_telemetry.py`.
- Schema changes:
  - Add `listening_events` table with indexes on `track_id` and `timestamp_utc`.
  - Add `playback_queue_snapshots` singleton table.
  - (Optional if trivial): Add `rating` (smallint) and `is_favorite` (boolean) to `tracks` and `albums`.

---

## 18. Documentation Requirements

- Update `README.md` with Phase 4 capabilities.
- Add API docs for new playback endpoints to FastAPI OpenAPI schemas.
- Ensure all new public classes and functions have comprehensive docstrings adhering to Google/Clean Code standards.

---

## 19. Exit Criteria

Phase 4 is complete when and only when all of the following conditions are met:

1. **Playback Functionality:** Desktop playback controller reliably executes play, pause, seek, stop, next, and previous across all supported audio formats (FLAC, MP3, AAC, ALAC, Opus, Vorbis).
2. **Deterministic Queue:** FIFO manual "Up Next" queue and shuffled context queue behave predictably with Auxio contiguous shuffle insertion.
3. **Factual Telemetry:** Every played track emits a verified, immutable `ListeningEvent` linking `track_id` and `track_file_id` with accurate duration and completion facts.
4. **State Persistence:** Restarting the Harmonia server completely restores queue state, active track, and seek offset.
5. **Filesystem Watcher:** Background watcher triggers incremental imports upon file changes without locking SQLite or degrading playback.
6. **Code Quality:** All tests pass (100% green), Ruff linter passes with zero warnings, and clean git working tree.
7. **Git Restore Point:** Tagged as `restore-point/phase-4-complete`.

---

## 20. Restore Point Strategy

Consistent with Harmonia's established restore point conventions:
- **Phase 4 Start Tag:** `restore-point/phase-4-start` (created immediately before touching code).
- **Phase 4 Complete Tag:** `restore-point/phase-4-complete` (created upon satisfying all exit criteria).
