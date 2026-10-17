# Harmonia Validated Development Roadmap

**Document Classification:** Pre-Implementation Freeze — Authoritative Engineering Roadmap  
**Status:** FROZEN & APPROVED  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Rule:** *Do NOT overload Phase 4. Keep Phase 4 strictly focused on deterministic playback, queue management, and factual listening telemetry.*

---

## 1. Roadmap Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HARMONIA 8-PHASE ROADMAP                        │
├────────────────────┬───────────────────────────────────────────────────┤
│ PHASE 1 [COMPLETE] │ Foundation: Domain, SQLite WAL, Mutagen, CLI      │
├────────────────────┼───────────────────────────────────────────────────┤
│ PHASE 2 [COMPLETE] │ Metadata Provenance, Normalization, FTS5, Artwork │
├────────────────────┼───────────────────────────────────────────────────┤
│ PHASE 3 [COMPLETE] │ Audio Intelligence (LUFS), FastAPI HTTP 206, UI   │
├────────────────────┼───────────────────────────────────────────────────┤
│ PHASE 4 [FROZEN]   │ Core Playback Engine, Queue, Listening Telemetry  │
├────────────────────┼───────────────────────────────────────────────────┤
│ PHASE 5            │ Harmonia Mobile (Android) & LocalSync P2P         │
├────────────────────┼───────────────────────────────────────────────────┤
│ PHASE 6            │ Audiophile Engine (AutoEQ, FIR Convolver, WASAPI) │
├────────────────────┼───────────────────────────────────────────────────┤
│ PHASE 7            │ Harmonia AI Foundation (Taste, Context, NLU)      │
├────────────────────┼───────────────────────────────────────────────────┤
│ PHASE 8            │ Hardening, Recovery, Lossy Transcode Detector     │
└────────────────────┴───────────────────────────────────────────────────┘
```

---

## 2. Detailed Phase Specifications

### Phase 4: Core Playback Engine & Factual Telemetry (Immediate Next Phase)

- **Objective:** Establish rock-solid, deterministic audio playback execution on Desktop Core, complete with transport controls, dual-layer queue mechanics, persistent queue snapshots, factual listening event recording, and debounced filesystem watching.
- **In-Scope Features:**
  1. `PlaybackPort` domain interface and desktop playback controller implementing transport controls (`play`, `pause`, `resume`, `stop`, `seek`, `next`, `previous`, `set_volume`).
  2. Polymorphic `PlaybackSource` abstraction (`LocalFileSource`, `StreamUriSource`, `PlatformUriSource`).
  3. Dual-layer deterministic queue (FIFO manual "Up Next" queue + Shuffled/Sequential Context queue).
  4. Non-scrambling "Play Next" shuffle insertion (Auxio contiguous placement logic).
  5. Persistent queue snapshot storage in SQLite (restoring active track, seek position, and queue state across restarts).
  6. Immutable `ListeningEvent` factual telemetry recording (`track_id`, `track_file_id`, `timestamp_utc`, `duration_listened_seconds`, `was_completed`, `was_skipped`, `playback_source`; `device_id = None` in Phase 4).
  7. Filesystem watcher with debounced event queue triggering incremental imports without blocking playback or locking SQLite.
  8. REST API endpoints (`/api/v1/playback/*`, `/api/v1/queue/*`, `/api/v1/listening-events/*`) exposing state cleanly without filesystem path leakage.
  9. Comprehensive test suite (unit tests for queue and state transitions, integration tests for DB persistence and event recording, regression tests for Phases 1-3).
- **Optional Only if Trivial:**
  - Ratings (1-5 stars) and favorite boolean flags on `Track` / `Album`. *Strict rule: Do NOT force ratings/favorites into Phase 4 if they delay or complicate the core playback/telemetry deliverable.*
- **Strictly Out of Scope:**
  - Harmonia AI (taste profiles, conversational NLU, smart playlists)
  - Harmonia Mobile (Android, iOS)
  - LocalSync P2P synchronization
  - Web metadata consensus & automated enrichment
  - Acoustic fingerprinting (Chromaprint / AcoustID)
  - Synchronized lyrics display (LRC / TTML)
  - AutoEQ database integration
  - FIR impulse response convolution / crossfeed
  - Direct USB DAC output / WASAPI exclusive bit-perfect backends
  - Stem separation
  - Audio file tag write-back
  - Cloud services or remote streaming servers
- **Architecture:**
  - Domain port `PlaybackPort` in `harmonia.domain.ports.playback_port`.
  - Application service `PlaybackService` in `harmonia.application.services.playback_service`.
  - Presentation controller exposing endpoints in `harmonia.presentation.api.routers.playback`.
  - SQLAlchemy model `ListeningEventModel` mapped to `ListeningEvent` entity.
  - Background watcher in `harmonia.infrastructure.filesystem.watcher`.
- **Dependencies:** `watchdog` (for filesystem directory event monitoring). Zero heavy C++ audio libraries.
- **Git Restore Points:**
  - Initiation: `restore-point/phase-4-start`
  - Completion: `restore-point/phase-4-complete`

---

### Phase 5: Harmonia Mobile Native Player & LocalSync Protocol

- **Objective:** Deliver a sovereign, offline-first personal music player for Android, and establish zero-cloud peer-to-peer Wi-Fi catalog synchronization between Desktop Core and Mobile.
- **Features:**
  1. **Harmonia Mobile (Android):** Kotlin + Jetpack Compose + Media3 ExoPlayer + Room SQLite.
  2. **Background Playback:** Media3 `MediaSessionService` with notification controls, lock screen display, and Bluetooth AVRCP.
  3. **Local Library Ingestion:** Android MediaStore delta scanner + SAF document tree permission import (`SourceIdentity.kt` pattern).
  4. **Disconnected Storage Protection:** Storage volume unmount state machine (`STORAGE_UNMOUNTED`, `FILE_MISSING`).
  5. **Harmonia LocalSync Protocol:**
     - Zero-cloud local peer discovery via mDNS (`_harmonia._tcp`).
     - Dynamic self-signed TLS certificates for encrypted LAN transfer.
     - Ed25519 cryptographic pairing via QR code.
     - Delta catalog JSON sync + chunked audio stream downloads with SHA-256 verification.
  6. **Multi-Provider Consensus & Triage Inbox (Desktop Core):**
     - Chromaprint (`fpcalc`) acoustic fingerprinting integration.
     - Multi-signal confidence matching (MusicBrainz + AcoustID) with duration and version qualifier guards.
     - Desktop UI review Inbox for ambiguous metadata matches.
  7. **Synchronized Lyrics Infrastructure:**
     - LRC & TTML karaoke-style lyric parsers with local SQLite caching.
- **Architecture:**
  - Pure native Android repository in `mobile/android`.
  - LocalSync server endpoint in FastAPI (`/api/v1/sync/*`).
  - Desktop Review Inbox table in SQLite (`metadata_inbox`).
- **Dependencies:** AndroidX Media3, Room, Kotlinx Coroutines, Coil (Android); `pychromaprint` / `fpcalc` CLI (Desktop).
- **Git Restore Points:**
  - Initiation: `restore-point/phase-5-start`
  - Completion: `restore-point/phase-5-complete`

---

### Phase 6: Audiophile Engine & DSP

- **Objective:** Elevate Harmonia into an uncompromising audiophile instrument with headphone correction, acoustic convolution, and bit-perfect hardware output.
- **Features:**
  1. **Headphone AutoEQ Database:** Local SQLite table of 4,000+ Jaakko Pasanen parametric EQ profiles; one-tap Harman target linearization.
  2. **FIR Impulse Response Convolver:** 16,384-tap direct time-domain FIR convolver for headphone crossfeed (Bauer crossfeed) and room correction.
  3. **Desktop Bit-Perfect Output:** Optional `libmpv` desktop backend providing WASAPI Exclusive output on Windows and ALSA direct addressing on Linux.
  4. **True Peak Headroom Clamping:** Mathematical clipping protection preventing positive gain distortion.
  5. **Mobile High-Res Direct USB Driver:** Direct USB UAC2 driver for dedicated Android DAPs.
- **Dependencies:** AutoEQ database dataset (CC0), `libmpv` (desktop optional).
- **Git Restore Points:**
  - Initiation: `restore-point/phase-6-start`
  - Completion: `restore-point/phase-6-complete`

---

### Phase 7: Harmonia AI Foundation

- **Objective:** Establish the personal music intelligence layer that understands the listener's tastes, context, and mood without cloud dependencies or privacy compromise.
- **Features:**
  1. **Independent AI Service & Database:** `harmonia_ai.db` SQLite database, completely isolated from Core.
  2. **Taste Profile & Behavioral Affinity Modeling:** Derive genre, tempo, era, and artist affinity vectors from historical `ListeningEvent` records.
  3. **Multi-Dimensional Context Engine:** Time of day, day of week, activity (focus, workout, relaxation, sleep) classification.
  4. **Natural Language Understanding (NLU):** Conversational search and music retrieval queries (e.g. *"Play upbeat 80s synth-pop from Japan"*).
  5. **Intelligent Dynamic Playlists:** Self-updating smart playlists based on semantic embeddings.
  6. **Jarvis Voice Assistant Integration:** Natural language bridge for home automation control.
- **Git Restore Points:**
  - Initiation: `restore-point/phase-7-start`
  - Completion: `restore-point/phase-7-complete`

---

### Phase 8: Ecosystem Hardening & Diagnostics

- **Objective:** Final production hardening, disaster recovery tooling, and audio quality verification.
- **Features:**
  1. Full library backup, JSON/CSV export, and disaster restore CLI commands.
  2. Lossy upconvert detector (FFT analysis detecting MP3 transcodes masquerading as 24/96 FLAC).
  3. SQLite database index tuning and vacuum optimization on 100,000-track catalogs.
- **Git Restore Points:**
  - Initiation: `restore-point/phase-8-start`
  - Completion: `restore-point/phase-8-complete`
