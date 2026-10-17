# Harmonia Phase 4 Forensic Verification Audit

**Audit Date:** October 2026  
**Audit Target:** Harmonia Core Phase 4 (Core Playback Engine & Factual Telemetry)  
**Auditor:** Lead Research Architect for Harmonia  
**Status:** COMPLETE  

---

## Executive Summary

A comprehensive forensic audit of the Harmonia Core Phase 4 implementation was conducted against the frozen architecture specifications:
- `research/music_players/PHASE_4_IMPLEMENTATION_SPEC.md`
- `research/music_players/HARMONIA_VALIDATED_ARCHITECTURE.md`
- `research/music_players/ARCHITECTURE_VALIDATION_DECISIONS.md`
- `research/music_players/IDENTITY_AND_HISTORY_REVIEW.md`
- `research/music_players/AUDIO_ARCHITECTURE_VALIDATION.md`
- `research/music_players/LOUDNESS_PIPELINE.md`
- `docs/ARCHITECTURE.md`

Every implementation artifact, test file, migration, REST route, and UI script was forensically inspected.

**Key Verdict:** While the domain abstractions, database schema, Alembic migrations, telemetry models, and filesystem watcher adhere cleanly to Clean Architecture, **critical gaps and divergences exist in the audio backend and UI playback pipeline**. Specifically, `DesktopAudioBackend` is a simulation clock rather than a real audio device driver, and the browser UI manages its own track advancement independently of the server's authoritative queue.

---

## 1. Playback Architecture & Dependency Direction

### Findings
- **Clean Architecture Hierarchy:**
  - `Domain` defines pure interfaces: `PlaybackPort` in `harmonia.domain.ports.playback_port` and entities/values in `harmonia.domain.entities.playback` and `harmonia.domain.values.playback`.
  - Zero imports of `FastAPI`, `sqlalchemy`, audio driver libraries, or browser constructs exist inside `src/harmonia/domain/`.
  - `Application` (`PlaybackService`) implements `PlaybackPort` and coordinates domain ports without coupling to presentation frameworks.
  - `Infrastructure` (`DesktopAudioBackend`, `SqliteListeningEventRepository`, `SqlitePlaybackSnapshotRepository`, `LibraryWatcher`) provides concrete implementations.
  - `Presentation` (`harmonia.api.routers.playback`, `harmonia.api.routers.queue`) depends strictly on `PlaybackService` through dependency injection.
- **Abstraction Integrity:**
  - `PlaybackSource` (`LocalFileSource`, `StreamUriSource`, `PlatformUriSource`) provides polymorphic audio source definitions without hidden backend coupling.

**Audit Status:** `PASS`

---

## 2. Desktop Audio Backend Audit

Forensic inspection of `src/harmonia/infrastructure/audio/backend.py`:

```python
class DesktopAudioBackend:
    def __init__(self) -> None:
        self._engine = VirtualAudioBackend()
    
    def load(self, source: PlaybackSource) -> None:
        self._engine.load(source)
    # ... all methods delegate 100% to _engine ...
```

### Forensic Answers to Specified Queries:
1. **What library performs audio output?**  
   **NONE.** Neither `sounddevice`, `miniaudio`, `pyaudio`, `libmpv`, `winsound`, nor any OS hardware API is imported or utilized.
2. **Does it produce audible desktop audio?**  
   **NO.** It produces 0 dB / zero acoustic output.
3. **Is playback blocking or non-blocking?**  
   Non-blocking. It updates an in-memory boolean flag `_is_playing` and measures elapsed `time.monotonic()`.
4. **How is playback position tracked?**  
   Via clock accumulation: `position += (time.monotonic() - last_tick_time)`.
5. **How is end-of-track detected?**  
   There is **no internal end-of-track detection** or timer callback in `DesktopAudioBackend` or `VirtualAudioBackend`. The position continues ticking past `duration_seconds` until an external caller invokes `next()`, `stop()`, or `seek()`.
6. **How does seek work?**  
   Mutates `self._position = max(0.0, float(position_seconds))` and resets `self._last_tick_time = time.monotonic()`.
7. **What happens if the file disappears while playing?**  
   Nothing in the backend notices or raises an error, because the file is never opened or decoded by `DesktopAudioBackend`.
8. **What happens if the decoder fails?**  
   There is no decoder in `DesktopAudioBackend`.
9. **What happens when playback is stopped?**  
   Resets `_is_playing = False`, `_position = 0.0`.
10. **What happens when the application exits?**  
    No hardware handles or background audio device threads exist; clean process exit occurs immediately.

### Critical Distinction:
- `VirtualAudioBackend`: Deterministic clock simulation for unit/integration tests and headless verification.
- `DesktopAudioBackend`: Currently an identical clone/wrapper over `VirtualAudioBackend`.  
**Audible desktop output does NOT originate from Python.**

**Audit Status:** `PARTIAL` (P1 Finding)

---

## 3. UI Playback Path Audit

Trace of UI playback:

```
[UI Play Button] 
       │
       ├──► HTML5 <audio id="audioElement">.play()
       │          │
       │          └──► GET /api/v1/stream/{file_id} (HTTP 206 Byte-Range Stream)
       │                     └──► Audible sound through browser's audio engine!
       │
       └──► fetch('/api/v1/playback/play', { method: 'POST' })
                  │
                  └──► PlaybackService.play()
                             └──► DesktopAudioBackend.play() (Virtual Clock advances)
```

### Forensic Findings:
1. **Source of Audible Sound:**  
   Physical sound is produced **exclusively by the browser's native `<audio>` element** consuming the HTTP 206 range stream from `/api/v1/stream/{file_id}`.
2. **Dual Playback State Divergence:**  
   In `src/harmonia/api/static/app.js`:
   - When a track is selected in the UI table, `app.js` loads the stream into `elements.audioElement` and makes a `POST /api/v1/queue/set`.
   - When the track finishes, `elements.audioElement` emits `'ended'`, triggering `playNextTrack()`.
   - In `playNextTrack()` (line 726):
     ```javascript
     const currentIndex = state.filteredTracks.findIndex((t) => t.id === state.playingTrack.id);
     const nextIndex = (currentIndex + 1) % state.filteredTracks.length;
     fetch('/api/v1/playback/next', { method: 'POST' }).catch(console.debug);
     playTrack(state.filteredTracks[nextIndex]);
     ```
   - **Architectural Violation (P0):** The UI client determines the next track by incrementing its own filtered DOM array (`state.filteredTracks`), instead of querying `PlaybackService`'s authoritative queue! If the user enqueued tracks in the server's manual "Up Next" queue, the UI bypasses them completely.

**Audit Status:** `PARTIAL` (P0 Finding)

---

## 4. Listening Event Telemetry Audit

Forensic inspection of `_PlaybackSession` in `src/harmonia/application/playback_service.py` and `ListeningEvent` in `src/harmonia/domain/entities/listening_event.py`:

### Identity Verification:
- Every event persists `track_id` (UUID), `track_file_id` (UUID | None), `id` (UUID), and `timestamp_utc` (datetime UTC).
- `device_id` is nullable (correct for Phase 4; reserved for Phase 5 LocalSync).
- Zero subjective mood, user preference, or AI fields exist in the model.

### Duration Accounting:
- Active listening duration accumulates strictly during playing state:
  `play -> 10s -> pause -> 10s -> resume -> 5s -> stop`
  - Active time accumulated: `10s + 5s = 15s`.
  - Pause duration (10s) is excluded because `last_tick` is cleared on pause.
- Seeking accounting:
  `play -> 20s -> seek +30s -> play 10s`
  - Seeking updates `self.ended_at_sec = current_pos`, but `listened_seconds` accumulates wall-clock delta `now - last_tick`. Seeking does **not** falsely add 30s to listened duration.

### Lifecycle & Deduplication:
- Pause and resume do not emit events.
- Transition (`next()`), explicit `stop()`, or track conclusion emits **exactly one** `ListeningEvent`.
- Completion criteria: `listened >= 80% track_duration OR listened >= 240.0s`.
- Skip criteria: `listened < 30.0s AND not completed`.

### Crash Behavior:
- **Known Phase 4 Limitation:** `_PlaybackSession` is held in memory during playback. If the process is killed (SIGKILL, power failure) before `_finalize_active_session()` is invoked, the active in-flight listening event is lost. Completed events committed to SQLite remain safe.

**Audit Status:** `PASS`

---

## 5. Queue Forensics

Forensic inspection of `PlaybackService` in `src/harmonia/application/playback_service.py`:

1. **Layer 1 (Manual Up Next):** FIFO order maintained via `_manual_queue.pop(0)`.
2. **Layer 2 (Context Queue):** Sequential index maintained via `_context_index`.
3. **Shuffle Stability:**
   - Enabling shuffle preserves current track and history (`[: _context_index + 1]`), shuffling only future items (`[_context_index + 1 :]`).
   - Disabling shuffle restores the original sequence (`_original_context_queue`) without interrupting current track.
4. **Contiguous Play Next Insertion (Auxio logic):**
   - Calling `insert_play_next(X)` inserts `X` at index 0 of `_manual_queue`.
   - Future randomized context sequence A, B, C, D remains completely untouched.
   - Playback order: Current -> X -> A -> B -> C -> D. Verified by unit test.
5. **Restart & Persistence:**
   - Active queue items (both manual and context) serialize to `PlaybackQueueSnapshotModel.queue_json`.
   - Re-instantiating `PlaybackService` and calling `restore_snapshot()` restores `_context_queue`, index, volume, repeat mode, and shuffle state.
6. **Previous Track Semantics:**
   - If played > 3.0s: restarts current track (`seek(0.0)`).
   - If played <= 3.0s: finalizes session for current track, and decrements context index if > 0.
7. **Missing Physical File Handling:**
   - `_resolve_playback_source` tests `Path(best_file.file_path).is_file()`.
   - If deleted, falls back to `StreamUriSource` rather than raising unhandled exceptions.

**Audit Status:** `PASS`

---

## 6. Snapshot Consistency & Storage

Forensic inspection of `PlaybackQueueSnapshotModel` and `SqlitePlaybackSnapshotRepository`:

- **Singleton Row:** Stored at primary key `id = 1`.
- **Atomic Persistence:** Serialized via `json.dumps(queue_data)` and committed inside database transactions.
- **Corruption Resilience:** `SqlitePlaybackSnapshotRepository.load()` wraps JSON deserialization in `try...except Exception: return None`. Malformed or partially written snapshots do not crash the service; the service falls back to an empty initial state.
- **Save Frequency:** Saved on every state mutation (`play`, `pause`, `seek`, `next`, `volume`, queue edit). SQLite WAL mode and busy timeout prevent concurrency locking.

**Audit Status:** `PASS`

---

## 7. Filesystem Watcher Forensics

Forensic inspection of `src/harmonia/infrastructure/filesystem/watcher.py`:

- **Thread Isolation:**
  - `on_any_event` only inspects file extension and appends timestamped path to an in-memory dictionary under a thread lock.
  - Zero database queries or metadata reads occur on the watchdog observer thread.
- **2-Second Sliding Debounce:**
  - `DebouncedEventHandler._debounce_worker` polls every 100ms. An event only triggers when `now - last_seen >= 2.0s`.
  - Burst modifications (e.g. tag writes or copy chunks) coalesce into a single path dispatch.
- **Temporary File Filtering:**
  - Automatically discards files matching `.tmp`, `.temp`, `.crdownload`, `.part`, `.swp`, and dotfiles/tilde files.
- **Worker Concurrency & Deletions:**
  - `_process_coalesced_batch` opens an independent database session from `session_factory()`. The playback engine transaction is never held or blocked.
  - File deletions look up `TrackFileModel` by exact path and invoke `delete_file(file_id)`. Foreign keys in `listening_events` cascade to `SET NULL`, preserving factual historical telemetry.

**Audit Status:** `PASS`

---

## 8. API & Security Audit

Forensic inspection of `/api/v1/playback/*`, `/api/v1/queue/*`, `/api/v1/listening-events`, and `/api/v1/stream/*`:

- **Path Concealment:**
  - Grep search for `file_path` across `src/harmonia/api/` returned **0 results**.
  - All endpoints accept and return strictly UUIDs (`track_id`, `track_file_id`).
  - Response DTOs contain zero internal absolute filesystem paths.
- **Loopback Security:**
  - API bound strictly to loopback (`127.0.0.1:8000`).
  - CORS policy regex locked to `localhost` and `127.0.0.1`.
- **HTTP 206 Streaming:**
  - Validates `Range: bytes=start-end`.
  - Chunks yielded via 64KB generator.
  - Out-of-bounds ranges return `416 Range Not Satisfiable` with proper `Content-Range: bytes */size`.
  - Missing file UUIDs return clean `404` without leaking filesystem paths.

**Audit Status:** `PASS`

---

## 9. Database & Migration Audit

Forensic inspection of `migrations/versions/004_phase4_playback_and_telemetry.py`:

- **Schema Elements:**
  - Table `listening_events`: Primary key `id` (UUID), foreign key `track_id` (CASCADE), foreign key `track_file_id` (SET NULL), nullable `device_id`.
  - Indexes: `ix_listening_events_track_id`, `ix_listening_events_timestamp`, `ix_listening_events_track_file_id`.
  - Table `playback_queue_snapshots`: Primary key `id` (Integer = 1), `current_track_id`, `current_track_file_id`, `position_seconds`, `volume`, `repeat_mode`, `shuffle_enabled`, `queue_json`, `current_queue_index`, `updated_at`.
- **Downgrade Path:** Drops tables and indexes in reverse order.
- **Migration Round-Trip:** Validated via `test_migration_round_trip_with_data_preservation` and `test_alembic_migrations`.

**Audit Status:** `PASS`

---

## 10. Regression & Verification Results

### Test Suite Execution:
```bash
uv run pytest
```
```text
Total tests: 166
Passed:      166
Failed:      0
Skipped:     0
Errors:      0
Duration:    20.70s
```

### Static Analysis:
```bash
uv run ruff check .
```
```text
All checks passed!
```

---

## 11. Real Audio Output Verification

### Forensic Statement:
```text
Audio fixture:     RESOURCES/fooyin-master/tests/data/audio/audiotest.mp3, audiotest.flac
Backend:           DesktopAudioBackend (VirtualAudioBackend)
Output device:     N/A (Virtual Clock)
Playback started:  Simulated / Clock-driven
Audible output:    REAL HARDWARE AUDIO OUTPUT COULD NOT BE VERIFIED IN THIS ENVIRONMENT.
Seek:              Simulated (Clock adjusted)
Pause:             Simulated (Clock paused)
Resume:            Simulated (Clock resumed)
Stop:              Simulated (Clock reset)
Next:              Simulated (Track transitioned)
```

**Audible physical sound in the Desktop UI is produced exclusively by the browser's HTML5 `<audio>` engine over HTTP 206, NOT by `DesktopAudioBackend`.**

---

## 12. Architectural Conformance Matrix

| Requirement | Implementation | Test | Status | Risk |
|---|---|---|---|---|
| **PlaybackPort** | `harmonia.domain.ports.playback_port` | `tests/unit/test_domain_playback.py` | **PASS** | None |
| **PlaybackSource** | `harmonia.domain.values.playback` | `tests/unit/test_domain_playback.py` | **PASS** | Low |
| **Desktop playback** | `DesktopAudioBackend` (`backend.py`) | `tests/hardening/test_phase4_end_to_end_verification.py` | **PARTIAL** | High (Virtual clock only, no hardware output) |
| **Queue** | Dual-layer queue in `PlaybackService` | `test_playback_service.py` | **PASS** | Low |
| **Shuffle** | `PlaybackService.toggle_shuffle` | `test_playback_service.py` | **PASS** | Low |
| **Play Next** | `PlaybackService.insert_play_next` | `test_auxio_contiguous_play_next_during_shuffle` | **PASS** | Low |
| **Snapshot** | `SqlitePlaybackSnapshotRepository` | `test_snapshot_restoration` | **PASS** | Low |
| **ListeningEvent** | `ListeningEvent` entity + SQLite repo | `test_listening_event_session_deduplication` | **PASS** | Low |
| **Session deduplication** | `_PlaybackSession` | `test_listening_event_session_deduplication` | **PASS** | Medium (In-flight session uncommitted until track end) |
| **Filesystem watcher** | `LibraryWatcher` + `DebouncedEventHandler` | `test_watcher.py`, `test_watcher_integration.py` | **PASS** | Low |
| **API** | `/api/v1/playback`, `/queue`, `/listening-events` | `test_api_playback.py` | **PASS** | Low |
| **Path concealment** | UUID addressing in API & DTOs | `test_phase4_end_to_end_verification.py` | **PASS** | None |
| **HTTP 206** | `StreamingService` | `test_api_stream.py` | **PASS** | Low |
| **Migration** | `004_phase4_playback_and_telemetry.py` | `test_alembic_migrations.py` | **PASS** | Low |
| **Regression suite** | 166 tests across all phases | `pytest` + `ruff` | **PASS** | Low |

---

## 13. Critical Findings

### P0 — Architectural Violation
- **UI Client Queue Divergence:**  
  `src/harmonia/api/static/app.js` handles track auto-advance (`playNextTrack()`) by computing index modulo on the client's HTML table (`state.filteredTracks`), bypassing the server's authoritative `PlaybackService` queue. If the user queued tracks with "Play Next" or "Up Next", the browser skips them upon track end.  
  *Correction Required:* The UI client must query `GET /api/v1/playback/state` or `POST /api/v1/playback/next` to determine the next track to play, ensuring the domain queue remains authoritative.

### P1 — Correctness Bug
- **DesktopAudioBackend Lacks Native Audio Driver:**  
  `DesktopAudioBackend` delegates 100% of its calls to `VirtualAudioBackend`. It does not interface with native desktop sound devices (e.g. miniaudio, sounddevice, or mpv). In a native desktop context (outside a web browser), Harmonia cannot emit physical sound.  
  *Correction Required:* While acceptable for web-presentation playback where the browser handles HTTP 206 decoding, standalone desktop audio playback requires a genuine audio output driver adapter.

### P2 — Reliability Issue
- **No Automatic End-Of-Track Detection in Virtual Backend:**  
  In headless or non-browser API usage, `DesktopAudioBackend` / `VirtualAudioBackend` lacks a timer or tick thread to auto-advance playback when `position >= duration`. The virtual clock continues running indefinitely past the track end until an external command arrives.
- **In-flight ListeningEvent Lost on Unclean Process Termination:**  
  If the Harmonia process abruptly crashes during playback, the active `_PlaybackSession` in memory is lost because events are only written to SQLite when finalized upon `next()`, `stop()`, or track completion.

### P3 — Enhancement (Deferred)
- **Ratings & Favorites:** Deferred per specification instructions.
- **WebSocket State Push:** The UI currently polls `/api/v1/playback/state` rather than receiving pushed updates.

---

## 14. Final Decision

Based on the forensic evidence:
1. `DesktopAudioBackend` is a virtual clock simulation rather than a hardware audio output driver.
2. The UI client auto-advance diverges from the server's authoritative queue.

```text
PHASE 4 REQUIRES CORRECTIONS — DO NOT START PHASE 5
```
