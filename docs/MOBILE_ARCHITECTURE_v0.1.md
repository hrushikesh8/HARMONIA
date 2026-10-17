# Harmonia Mobile v0.1 — Architecture Design Document

**Status:** Proposed Architecture (Subject to M0.1 Reconciliation)
**Version:** 0.1.1
**Date:** October 2026
**Document Author:** Harmonia Engineering Architecture Group
**Classification:** Historical Core System Specification (M0 Baseline)
**Architecture Reconciliation:** See [`docs/MOBILE_ARCHITECTURE_RECONCILIATION.md`](MOBILE_ARCHITECTURE_RECONCILIATION.md) for authoritative M0.1 decisions (Room SQLite, Android-First Native, SwiftUI on iOS, Media3 Extractor, and MediaLibraryService for Android Auto) and [`docs/ROADMAP.md`](ROADMAP.md) for the active master roadmap.

> [!IMPORTANT]
> **Historical Baseline & Superseded Proposals Notice (Stage 4):**
> This document preserves the historical **Milestone M0** architectural proposal as originally authored in October 2026. Several early exploratory technology proposals evaluated in §3 and §4 (such as Kotlin Multiplatform / Compose Multiplatform and SQLDelight) were **formally superseded by M0.1 architectural decisions** documented in [`docs/MOBILE_ARCHITECTURE_RECONCILIATION.md`](MOBILE_ARCHITECTURE_RECONCILIATION.md) (which established Android-First Native Jetpack Compose, Room SQLite, and AndroidX Media3).
> This document is retained for historical traceability and domain boundary definition. Do not interpret superseded exploratory sections as active architecture. Consult [`docs/ROADMAP.md`](ROADMAP.md) and [`docs/knowledge/`](knowledge/README.md) for current implementation records and invariants.

---

## 1. Executive Summary

Harmonia Mobile is the native, offline-first personal music player for the Harmonia ecosystem. It is designed from first principles to provide sovereign, high-fidelity audio playback on mobile devices without dependence on network connectivity, cloud accounts, remote servers, or external artificial intelligence.

While **Harmonia Desktop** serves as an archival music library and audio engineering console (specializing in deep metadata curation, filesystem organization, and objective ITU-R BS.1770-4 signal analysis), **Harmonia Mobile** is a dedicated, distraction-free listening instrument. It focuses on fluid one-handed navigation, responsive queue management, robust background playback, and seamless integration with platform audio subsystems (lock-screen controls, Bluetooth AVRCP, headphones, and Android Auto).

Harmonia Mobile adheres strictly to the foundational ecosystem boundary:
> **"Harmonia owns the music. Harmonia AI understands the person."**

The player operates with 100% autonomy using local music files and an embedded SQLite database. Synchronization with Harmonia Desktop is strictly optional, opportunistic, and non-blocking. Network absence is the baseline operational state, not an exceptional condition.

---

## 2. Product Boundary & Separation of Concerns

### 2.1 The Two Pillars of Harmonia

| Architectural Dimension | Harmonia Desktop | Harmonia Mobile |
| :--- | :--- | :--- |
| **Primary Identity** | Audio Engineering Console & Library Archive | Sovereign Personal Audio Player |
| **Primary User Action** | Curate, inspect, analyze, organize, tag | Play, explore, queue, favorite, listen |
| **Audio Processing** | Subprocess FFmpeg/EBUR128 analysis, loudness scanning | Low-latency playback, gapless transitions, ReplayGain/LUFS volume normalization |
| **Filesystem Interaction** | Multi-root filesystem scanner, path watcher, archival storage | Scoped storage / SAF import, managed local cache, sandboxed audio store |
| **Execution Context** | Local workstation / home server (Python/FastAPI) | Portable device (Android first, iOS portable) |
| **Network Role** | Sync Host / Local API provider | Sync Client / Autonomous Player |
| **AI Relationship** | Exposes REST API to Harmonia AI | Client for optional smart playlists; zero runtime AI dependency |

### 2.2 What Harmonia Mobile Is NOT

- **Not a Web Wrapper or PWA:** Web runtimes suffer from erratic background thread termination, high memory consumption, and brittle audio focus handling. Harmonia Mobile is 100% native.
- **Not a Remote Control:** Harmonia Mobile does not exist to control the desktop application. It plays music directly from local device storage to device audio hardware.
- **Not a Cloud Streaming Client:** The application does not require a remote server to be online. If the phone is on an airplane in mid-flight, playback of all local files is instantaneous and complete.
- **Not an AI-Dependent Player:** Algorithms and recommendations never gate playback. Queueing, shuffling, and searching operate deterministically on the local SQLite database.

---

## 3. Technology Evaluation & Decision

### 3.1 Comparative Analysis of Evaluated Frameworks

To determine the optimal engineering foundation, four primary technical paths were evaluated against Harmonia's rigorous requirements:

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                        Technology Evaluation Matrix                             │
├───────────────────────┬──────────────┬──────────────┬─────────────┬─────────────┤
│ Architectural Metric  │ KMP + CMP    │ Pure Kotlin  │ Flutter     │ React Native│
│                       │ (Recommended)│ (Android)    │             │             │
├───────────────────────┼──────────────┼──────────────┼─────────────┼─────────────┤
│ Native Audio Fidelity │ Tier 1 (100%)│ Tier 1 (100%)│ Tier 2 (85%)│ Tier 3 (70%)│
│ Background Resilience │ Tier 1 (100%)│ Tier 1 (100%)│ Tier 2 (80%)│ Tier 3 (65%)│
│ Media3/Session Support│ Tier 1 (100%)│ Tier 1 (100%)│ Bridge/Shim │ Bridge/Shim │
│ Bluetooth/AVRCP Sync  │ Tier 1 (100%)│ Tier 1 (100%)│ Latent      │ High Jitter │
│ Large Library (100k)  │ Tier 1 (Fast)│ Tier 1 (Fast)│ Moderate    │ Poor        │
│ iOS Portability       │ Tier 1 (High)│ Tier 4 (0%)  │ Tier 1      │ Tier 2      │
│ Memory Overhead       │ Low (JVM/ART)│ Low (JVM/ART)│ Moderate    │ High (V8/JSC│
│ Android Auto Ready    │ Full Native  │ Full Native  │ Complex Shim│ Complex Shim│
└───────────────────────┴──────────────┴──────────────┴─────────────┴─────────────┘
```

#### Detailed Candidate Breakdown:

1. **Kotlin + Jetpack Compose (Pure Android Native)**
   - *Strengths:* Direct access to `androidx.media3` (`ExoPlayer`, `MediaSessionService`), Android Auto Car App Library, Room DB, zero IPC overhead.
   - *Weaknesses:* Requires an absolute 100% rewrite in Swift/SwiftUI for iOS. Zero sharing of domain entities, local database schemas, sync logic, or playlist manipulation.
2. **Flutter (Dart)**
   - *Strengths:* Single codebase for Android and iOS UI; high rendering consistency via Impeller.
   - *Weaknesses:* Audio playback requires bridging across platform channels (`just_audio` / `audio_service`). Background execution runs in a separate Dart isolate, introducing serialization lag for rapid seek/queue events. Android Auto Car App templates do not map cleanly to Flutter widgets. Large database access via SQLite bridges incurs significant serialization overhead on 100,000-row recordsets.
3. **React Native (TypeScript / New Architecture)**
   - *Strengths:* Broad component ecosystem and developer familiarity.
   - *Weaknesses:* JavaScript event loop introduces non-deterministic latency in audio focus transitions. Background audio threads risk suspension under aggressive OS battery management (e.g., Samsung OneUI, Xiaomi MIUI). Rendering lists of 50,000+ tracks over the JS bridge creates measurable memory bloat and scroll stutter.
4. **Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP)**
   - *Strengths:*
     - **Shared Core (80% of Codebase):** Pure Kotlin Common module housing domain models, SQLDelight database, local sync protocol, queue state machines, and view models.
     - **Zero Audio Compromise on Android:** Android target compiles directly to native JVM bytecode with zero bridging overhead. Plugs directly into `androidx.media3:media3-exoplayer` and `androidx.media3:media3-session`.
     - **Preserved iOS Portability:** When targeting iOS, KMP compiles down to native Objective-C/Swift frameworks via Kotlin/Native. The shared domain, SQLDelight SQLite database, and sync engine run natively on iOS without code duplication. The audio playback engine wraps native `AVQueuePlayer` / `AVAudioEngine` behind the shared `PlaybackEngine` interface.
     - **Unified Modern UI:** Compose Multiplatform shares the UI design system between Android and iOS while maintaining platform-specific escape hatches.

### 3.2 Architectural Decision: Android-First Native Architecture

**Harmonia Mobile selects an Android-First Native Architecture: Kotlin, Jetpack Compose, AndroidX Media3 (`MediaLibraryService` + ExoPlayer), and Android Room (SQLite WAL Mode).**

- **Native Android Priority:** Maximum platform quality, background playback resilience, low battery consumption, and seamless system media session integration on Android.
- **Portability Guardrail:** Clean Architecture separation ensuring domain entities, value objects, queue state machine logic, and repository interfaces are written in **Pure Kotlin** without `android.*` framework dependencies where practical.
- **iOS Strategy:** Dedicated native iOS implementation using Swift, SwiftUI, and AVFoundation in a subsequent phase, rather than compromising platform quality through premature cross-platform abstraction layers.

---

## 4. Repository Strategy & Monorepo vs. Polyrepo Analysis

### 4.1 Structural Evaluation

```
Option A: Monorepo                             Option B: Dedicated Polyrepo (Recommended)
harmonia/                                      harmonia/                 harmonia-mobile/
├── src/harmonia/ (Core Python)                └── (Core Python)         ├── app/ (Android App)
├── tests/                                                               └── docs/
├── docs/
└── mobile/
    └── app/
```

- **Analysis of Monorepo (`harmonia/mobile`):**
  - *Pros:* Colocated API schemas; single commit can update API and mobile client simultaneously.
  - *Cons:* Heavy toolchain friction. Mobile builds require Android SDKs (30+ GB cache), Gradle daemons, Java 17+, and NDK, while Harmonia Core requires Python 3.12, uv, FFmpeg, and pytest. CI matrix complexity multiplies significantly. Release cadences diverge: Core releases archival releases; Mobile releases frequent patch builds for mobile app store guidelines.
- **Analysis of Polyrepo (`harmonia-mobile`):**
  - *Pros:* Independent release cadence; isolated CI/CD pipelines (Fastlane, Android Gradle caching, Play Store / F-Droid tracks); clean dependency isolation; dedicated issue tracker for mobile UX bugs.
  - *Cons:* Requires contract synchronization for API definitions.

### 4.2 Recommendation: Dedicated Repository (`harmonia-mobile`) with Automated Contract Publishing

Harmonia Mobile will reside in a dedicated repository: **`harmonia-mobile`**.
Contract synchronization between Harmonia Core and Harmonia Mobile will be governed by:
1. Canonical OpenAPI 3.1 schema generated from FastAPI (`docs/contracts/openapi.json`).
2. Vector brand assets linked via git submodule or automated release artifact consumption from `harmonia/assets/brand/`.

*(Note: During this architecture design phase, zero code or repositories are created).*

---

## 5. Architectural Topology & System Layers

Harmonia Mobile implements Clean Architecture with strict unidirectional data flow (MVI - Model-View-Intent).

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                              │
│             Jetpack Compose UI (Screens, Components, Theme)            │
│               NowPlaying  │  Library  │  Search  │  Queue              │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ UI Events / Intents
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                        APPLICATION LAYER                               │
│  PlayerController  │  LibraryViewModel  │  SyncViewModel  │  QueueMgr  │
│  StateFlow<PlayerState>   │   StateFlow<QueueState>   │   Actions      │
└─────────────────┬───────────────────────────────────┬──────────────────┘
                  │                                   │
                  ▼                                   ▼
┌───────────────────────────────────┐ ┌──────────────────────────────────┐
│       PLAYBACK SUBSYSTEM          │ │        DOMAIN LAYER              │
│ HarmoniaPlaybackService (Media3)  │ │ Entities (Track, Album, Playlist)│
│ MediaLibraryService (Android Auto)│ │ UseCases (Import, Enqueue, Rate) │
│ NativeAudioEngine (ExoPlayer)     │ │ Repositories (Pure Kotlin Ports) │
└─────────────────┬─────────────────┘ └──────────────────┬───────────────┘
                  │ Read Local File                      │
                  ▼                                      ▼
┌───────────────────────────────────┐ ┌──────────────────────────────────┐
│       DEVICE AUDIO HARDWARE       │ │      INFRASTRUCTURE LAYER        │
│   HAL / AudioTrack / Bluetooth    │ │ Android Room SQLite DB (WAL Mode)│
│                                   │ │ Scoped Storage / SAF Scanner     │
│                                   │ │ Ktor / OkHttp (Desktop Sync)     │
└───────────────────────────────────┘ └──────────────────────────────────┘
```

### Unidirectional Invariant
The UI layer **never** communicates directly with the `NativeAudioEngine` or the database. All interactions flow through the `PlayerController` and `PlaybackService`. The audio engine emits continuous playback telemetry via StateFlow back to the application state.

---

## 6. Offline-First Data Architecture & Local Database Model

### 6.1 Data Tier Classification

The mobile database categorizes data into three distinct tiers:

1. **Tier 1: Synced Metadata (Authoritative on Desktop):**
   - Canonical metadata mirroring Harmonia Core: artist names, album titles, track numbering, disc numbering, genres, durations, and ITU-R BS.1770 audio analysis metrics (integrated LUFS, true peak).
2. **Tier 2: Device-Local State (Authoritative on Mobile Device):**
   - Transient state and hardware-specific configurations: active playback queue, exact playback position (millisecond precision), local storage paths, download state, volume adjustments, sleep timer settings, and local listening event logs.
3. **Tier 3: Derived / Ephemeral State (Rebuildable):**
   - SQLite FTS5 search indexes, waveform display caches, in-memory artwork downscaled textures, and aggregated artist track counts.

### 6.2 SQLite Entity Schema (SQLDelight Specification)

```sql
-- =====================================================================
-- HARMONIA MOBILE LOCAL DATABASE SCHEMA (SQLITE 3 with WAL Mode)
-- =====================================================================

-- Core Canonical Entities
CREATE TABLE mobile_artists (
    id TEXT PRIMARY KEY NOT NULL,             -- UUID string matching Core
    name TEXT NOT NULL,
    sort_name TEXT NOT NULL,
    mbid TEXT,
    artwork_hash TEXT,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE mobile_albums (
    id TEXT PRIMARY KEY NOT NULL,             -- UUID string matching Core
    title TEXT NOT NULL,
    artist_id TEXT REFERENCES mobile_artists(id) ON DELETE SET NULL,
    year INTEGER,
    total_tracks INTEGER,
    total_discs INTEGER,
    mbid TEXT,
    artwork_hash TEXT,                       -- Content-addressed hash for cover art
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE mobile_tracks (
    id TEXT PRIMARY KEY NOT NULL,             -- UUID string matching Core
    album_id TEXT REFERENCES mobile_albums(id) ON DELETE SET NULL,
    artist_id TEXT REFERENCES mobile_artists(id) ON DELETE SET NULL,
    title TEXT NOT NULL,
    track_number INTEGER,
    disc_number INTEGER DEFAULT 1,
    duration_sec REAL NOT NULL,
    year INTEGER,
    genre TEXT,
    mbid TEXT,
    integrated_lufs REAL,                     -- Loudness metadata for ReplayGain
    true_peak_dbtp REAL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Physical File Presence & Offline Availability Abstraction
CREATE TABLE mobile_local_files (
    id TEXT PRIMARY KEY NOT NULL,             -- UUID
    track_id TEXT NOT NULL REFERENCES mobile_tracks(id) ON DELETE CASCADE,
    storage_type TEXT NOT NULL,               -- 'INTERNAL_SYNC', 'SAF_IMPORTED', 'EXTERNAL_SD'
    file_uri TEXT NOT NULL UNIQUE,            -- Local file URI / SAF content URI
    file_size INTEGER NOT NULL,
    file_hash TEXT NOT NULL,                  -- SHA-256 for bitstream integrity
    format TEXT NOT NULL,                     -- 'FLAC', 'MP3', 'AAC', 'OPUS'
    bitrate INTEGER,
    sample_rate INTEGER,
    bit_depth INTEGER,
    channels INTEGER,
    is_lossless INTEGER NOT NULL DEFAULT 0,
    availability_status TEXT NOT NULL,        -- 'AVAILABLE', 'MISSING', 'CORRUPT'
    verified_at INTEGER NOT NULL,
    added_at INTEGER NOT NULL
);

-- Playlists & Collaborative Structures
CREATE TABLE mobile_playlists (
    id TEXT PRIMARY KEY NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    artwork_hash TEXT,
    is_smart INTEGER NOT NULL DEFAULT 0,
    criteria_json TEXT,                       -- Filter definitions if smart playlist
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL,
    version_vector TEXT NOT NULL              -- Lamport revision or vector clock
);

CREATE TABLE mobile_playlist_items (
    id TEXT PRIMARY KEY NOT NULL,
    playlist_id TEXT NOT NULL REFERENCES mobile_playlists(id) ON DELETE CASCADE,
    track_id TEXT NOT NULL REFERENCES mobile_tracks(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    added_at INTEGER NOT NULL,
    UNIQUE(playlist_id, position)
);

-- Personal Annotations (Dedicated Schema v5: Local-first persistence)
-- Active favorite indicated by row presence; unfavoriting performs row deletion.
CREATE TABLE mobile_favorites (
    track_id TEXT PRIMARY KEY NOT NULL REFERENCES mobile_tracks(id) ON DELETE CASCADE,
    client_updated_at INTEGER NOT NULL
);

-- User ratings strictly integer 1..5; unrated/cleared indicated by absence of row.
CREATE TABLE mobile_ratings (
    track_id TEXT PRIMARY KEY NOT NULL REFERENCES mobile_tracks(id) ON DELETE CASCADE,
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    client_updated_at INTEGER NOT NULL
);

-- Listening Telemetry (Immutable Append-Only Log)
CREATE TABLE mobile_listening_events (
    id TEXT PRIMARY KEY NOT NULL,             -- UUIDv7 (time-ordered)
    track_id TEXT NOT NULL REFERENCES mobile_tracks(id) ON DELETE CASCADE,
    local_file_id TEXT REFERENCES mobile_local_files(id) ON DELETE SET NULL,
    started_at INTEGER NOT NULL,
    completed_at INTEGER,
    duration_played_sec REAL NOT NULL,
    completed_fully INTEGER NOT NULL,         -- 1 if played > 80%
    output_route TEXT,                        -- 'SPEAKER', 'BLUETOOTH', 'HEADPHONES'
    is_synced INTEGER NOT NULL DEFAULT 0
);

-- Persistent Device Playback State (1 Row Invariant)
CREATE TABLE mobile_playback_state (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    current_track_id TEXT REFERENCES mobile_tracks(id) ON DELETE SET NULL,
    current_position_ms INTEGER NOT NULL DEFAULT 0,
    is_playing INTEGER NOT NULL DEFAULT 0,
    shuffle_mode INTEGER NOT NULL DEFAULT 0,  -- 0: Off, 1: On
    repeat_mode INTEGER NOT NULL DEFAULT 0,   -- 0: Off, 1: All, 2: One
    volume_scalar REAL NOT NULL DEFAULT 1.0,
    updated_at INTEGER NOT NULL
);

-- Persistent Active Queue
CREATE TABLE mobile_queue_items (
    position INTEGER PRIMARY KEY NOT NULL,    -- 0-indexed order
    track_id TEXT NOT NULL REFERENCES mobile_tracks(id) ON DELETE CASCADE,
    original_index INTEGER,                   -- Position before shuffle
    added_at INTEGER NOT NULL
);

-- Indices for 100,000+ Track Scale
CREATE INDEX idx_tracks_album ON mobile_tracks(album_id);
CREATE INDEX idx_tracks_artist ON mobile_tracks(artist_id);
CREATE INDEX idx_tracks_title ON mobile_tracks(title COLLATE NOCASE);
CREATE INDEX idx_local_files_track ON mobile_local_files(track_id);
CREATE INDEX idx_playlist_items_order ON mobile_playlist_items(playlist_id, position);
CREATE INDEX idx_listening_sync ON mobile_listening_events(is_synced) WHERE is_synced = 0;
```

---

## 7. Local File Ownership & Storage Architecture

### 7.1 Multi-Source File Ingestion Abstraction

Harmonia Mobile does not assume files originate from a desktop computer. The application treats local storage with first-class sovereignty through the `StorageSource` abstraction:

```
                          ┌────────────────────────┐
                          │  Local Media Importer  │
                          └───────────┬────────────┘
                                      │
         ┌────────────────────────────┼────────────────────────────┐
         ▼                            ▼                            ▼
┌──────────────────┐        ┌──────────────────┐        ┌──────────────────┐
│   SAF Importer   │        │   Desktop Sync   │        │   Direct File    │
│ (User On-Device) │        │ (Wi-Fi Protocol) │        │ (MTP / USB Drop) │
└────────┬─────────┘        └────────┬─────────┘        └────────┬─────────┘
         │                           │                           │
         └───────────────────────────┼───────────────────────────┘
                                     ▼
                  ┌──────────────────────────────────────┐
                  │      Unified Local File Entity       │
                  │ (Extract Tags, Hash, Register in DB) │
                  └──────────────────────────────────────┘
```

1. **Direct On-Device Import (Android Storage Access Framework):**
   - The user selects any directory or file tree on their device storage or SD card via Android's document picker (`ACTION_OPEN_DOCUMENT_TREE`).
   - The app persists URI permissions (`takePersistableUriPermission`) and indexes basic container tags directly on-device using the Android platform's built-in `android.media.MediaMetadataRetriever` (strictly read-only and provisional).
2. **Synchronized Storage (Harmonia Managed Sandbox):**
   - Files transferred from Harmonia Desktop are saved into app-specific external storage (`/Android/data/org.harmonia.player/files/music/`) or user-selected shared Music folder (`/Music/Harmonia/`).
   - Files are stored using a deterministic content-addressed or path-sanitized naming scheme: `Music/Harmonia/{Artist}/{Album}/{TrackNumber} - {Title}.{ext}`.
3. **USB / MTP Transfer Detection:**
   - When the user copies music onto the device via USB cable, the local scanner performs an incremental scan of the registered Harmonia folder, validating file headers and extracting tags without needing network connectivity.

---

## 8. Playback Architecture & Subsystem Specification

### 8.1 Layered Playback Pipeline

```
┌────────────────────────────────────────────────────────┐
│                      UI Layer                          │
│   NowPlayingScreen  │  MiniPlayerDock  │  QueueSheet   │
└───────────────────────────┬────────────────────────────┘
                            │ Intents (Play, Pause, Skip, Seek)
                            ▼
┌────────────────────────────────────────────────────────┐
│                  PlayerController                      │
│   Unidirectional StateFlow<PlaybackState> Provider     │
│   Queue Management & Shuffle History Resolution        │
└───────────────────────────┬────────────────────────────┘
                            │ MediaControllerCompat / IPC
                            ▼
┌────────────────────────────────────────────────────────┐
│           HarmoniaPlaybackService (Media3)             │
│   Android Foreground Service + MediaSession            │
│   System Notifications │ Lock Screen │ Bluetooth AVRCP │
└───────────────────────────┬────────────────────────────┘
                            │ Direct Method Invocation
                            ▼
┌────────────────────────────────────────────────────────┐
│                 NativeAudioEngine                      │
│   ExoPlayer / MediaSource Pipeline                     │
│   Gapless Pre-buffering │ AudioAttributes (Music)      │
│   AudioFocusRequestCompat Listener (Ducking / Pause)   │
└───────────────────────────┬────────────────────────────┘
                            │ PCM Bitstream
                            ▼
┌────────────────────────────────────────────────────────┐
│               Android Audio Hardware (HAL)             │
│   AudioTrack / OpenSL ES / AAudio (Hi-Res Output)      │
└────────────────────────────────────────────────────────┘
```

### 8.2 Detailed Component Responsibilities

1. **`PlayerController`:**
   - Exposes an immutable `StateFlow<PlayerSnapshot>` to all UI components.
   - Manages queue indices, playback history stack, and shuffle sequence generation.
   - Persists playback state (current track ID, elapsed ms) to SQLite whenever playback pauses, tracks transition, or periodically every 5 seconds.
2. **`HarmoniaPlaybackService` (`androidx.media3.session.MediaLibraryService`):**
   - Executes as an Android Foreground Service with `foregroundServiceType="mediaPlayback"`.
   - Extends `MediaLibraryService` to establish the architectural integration boundary for Android Auto and system media sessions (`MediaLibrarySession.Callback`).
   - Manages the ongoing playback notification with full transport controls, elapsed time scrubbing, and album artwork bitmap rendering.
   *(Note: Full Android Auto automotive manifest declarations and DHU testing are scheduled for Phase M8).*
3. **`NativeAudioEngine` (`androidx.media3.exoplayer.ExoPlayer`):**
   - Configures audio attributes with `AudioAttributes.USAGE_MEDIA` and `AudioAttributes.CONTENT_TYPE_MUSIC`.
   - Implements **Gapless Playback**: Pre-loads the next media item in the playlist queue into memory buffer 10 seconds before the current track terminates, achieving zero-millisecond cross-track silence for classical pieces and live concert recordings.
   - Implements **Audio Focus**:
     - `AUDIOFOCUS_LOSS`: Pause playback immediately.
     - `AUDIOFOCUS_LOSS_TRANSIENT`: Pause playback temporarily.
     - `AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`: Attenuate volume scalar to 0.2 (ducking) for incoming notifications.
     - `AUDIOFOCUS_GAIN`: Restore volume or resume playback if paused by transient interruption.
   - Implements **Noisy Audio Interruption (`ACTION_AUDIO_BECOMING_NOISY`)**:
     - Pauses playback immediately when headphones are disconnected or Bluetooth audio disconnects, preventing accidental loud public broadcast.
4. **Volume Normalization (ReplayGain / ITU-R BS.1770-4):**
   - When a track begins, the engine reads `integrated_lufs` from `mobile_tracks`.
   - Applies volume gain scaling based on target loudness (-18.0 LUFS standard) to ensure consistent listening volume across diverse masterings without dynamic range compression.

---

## 9. Platform Abstraction Boundary (Android-First, Cross-Platform Ready)

To prevent platform lock-in while preserving zero-overhead performance on Android, the codebase isolates platform-specific implementations behind common interfaces in KMP:

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      COMMON KMP INTERFACES                              │
├──────────────────────────┬──────────────────────┬───────────────────────┤
│ AudioPlaybackEngine      │ MediaNotificationHub │ LocalStorageManager   │
│ Play, pause, seek, load  │ Post & update media  │ Open file streams,    │
│ Gapless queue transitions│ notification controls│ query storage quota   │
└────────────┬─────────────┴──────────┬───────────┴───────────┬───────────┘
             │                        │                       │
             ▼                        ▼                       ▼
┌──────────────────────────┬──────────────────────┬───────────────────────┐
│ ANDROID IMPLEMENTATION   │ ANDROID IMPLEMENTATION│ ANDROID IMPLEMENTATION│
│ ExoPlayerAudioEngine     │ Media3NotificationHub│ AndroidStorageManager │
│ Wraps ExoPlayer & Media3 │ Wraps MediaSession   │ Scoped Storage & SAF  │
└──────────────────────────┴──────────────────────┴───────────────────────┘
             │                        │                       │
             ▼                        ▼                       ▼
┌──────────────────────────┬──────────────────────┬───────────────────────┐
│ FUTURE iOS IMPLEMENTATION│ FUTURE iOS IMPL      │ FUTURE iOS IMPL       │
│ AVPlayerAudioEngine      │ MPNowPlayingHub      │ AppleSandboxManager   │
│ Wraps AVAudioEngine      │ MPRemoteCommandCenter│ iOS Documents Sandbox │
└──────────────────────────┴──────────────────────┴───────────────────────┘
```

---

## 10. Harmonia Desktop ↔ Mobile Synchronization Architecture

### 10.1 Synchronization Paradigm: Opportunistic & Decoupled

Synchronization is strictly **optional and non-blocking**. The mobile player never stalls user interactions to wait for desktop confirmation.

```
┌────────────────────────┐                   ┌────────────────────────┐
│    Harmonia Desktop    │                   │    Harmonia Mobile     │
│ (Archival Workstation) │                   │ (Sovereign Listening)  │
└───────────┬────────────┘                   └───────────┬────────────┘
            │                                            │
            │ <─────── 1. mDNS Discovery (_harmonia._tcp)│
            │ ───────> 2. Mutual TLS Handshake ─────────>│
            │ <─────── 3. Capability & Manifest Sync ────│
            │                                            │
            │ ───────> 4. Metadata Push (Desktop Wins) ─>│
            │ <─────── 5. Telemetry Push (Mobile Wins) ──│
            │ <─────── 6. User Annotations (CRDT Merge) ─│
            │ ───────> 7. Resumable Binary Transfer ────>│
            │          (HTTP 206 Partial Content)        │
```

### 10.2 Authority Matrix by Entity Domain

| Domain | Authoritative Node | Conflict Resolution Strategy | Rationale |
| :--- | :--- | :--- | :--- |
| **Track Metadata** | Harmonia Desktop | Desktop Overwrites Mobile | Desktop possesses complete archival tags, MusicBrainz IDs, and FFmpeg signal measurements. |
| **Audio Bitstream** | Harmonia Desktop | Desktop is Master Archive | Mobile stores selective offline copies based on user choice and storage constraints. |
| **Listening Events** | Harmonia Mobile | Append-Only Union by UUIDv7 | Listening sessions executed on the mobile device are immutable factual telemetry. Desktop records them without modification. |
| **Favorites / Star** | Equal (Peer-to-Peer) | Last-Write-Wins (LWW) via Lamport Timestamp + Tombstone | User should be able to favorite on desktop or mobile and have the most recent intent preserved. |
| **Ratings (1-5★)** | Equal (Peer-to-Peer) | Last-Write-Wins (LWW) via Lamport Timestamp | Explicit ratings overwrite previous state based on the most recent client update timestamp. |
| **Playlists** | Collaborative | Three-Way Merge / Position-Based Rebase | If modified concurrently, item additions and deletions are reconciled. Structural divergence triggers automatic branch backup. |
| **Device State** | Harmonia Mobile | 100% Mobile Authority | Active queue, elapsed position, and playback volume are never overwritten by desktop. |

---

## 11. Deterministic Conflict Resolution Engine

To prevent data loss and eliminate ambiguous race conditions, conflict handling implements deterministic mathematical rules:

### 11.1 Playlist Merging (3-Way Vector Rebase)
- Every playlist tracks a `version_vector` (integer incremented on each mutation).
- When a playlist is modified on mobile while offline, and simultaneously modified on desktop:
  1. Items added to Mobile that are absent on Desktop are appended to Desktop.
  2. Items added to Desktop that are absent on Mobile are appended to Mobile.
  3. Items explicitly deleted on either device are removed, provided the deletion timestamp is newer than the item's addition timestamp.
  4. If an order conflict occurs that cannot be resolved cleanly, the system performs a non-destructive branch: Mobile retains `Playlist Name (Mobile)` while syncing the desktop version as canonical.

### 11.2 Synchronization & Tombstone Strategy for Favorites and Ratings
- **Local Persistence (Schema v5):** Local operational tables (`mobile_favorites`, `mobile_ratings`) use row presence/deletion semantics to keep database size compact and queries fast without unbounded tombstone accumulation. The `client_updated_at` column represents the timestamp of active user mutation and does **not** itself represent deletions.
- **Future Milestone M6 (LocalSync) Conflict Resolution:**
  - Milestone M6 will introduce a dedicated mutation-journal / tombstone synchronization table (e.g. `mobile_sync_tombstones(id, entity_type, entity_id, is_deleted, timestamp_ms, device_id)`).
  - Soft deletes during synchronization are recorded via this sync journal:
   ```json
   {
     "track_id": "a98f12c4-...",
     "entity": "favorite",
     "is_deleted": true,
     "timestamp_ms": 1791456000000,
     "device_id": "mobile-pixel-8"
   }
   ```
  - If Node A receives a favorite deletion at $T_2$ and Node B attempts to send a favorite creation from $T_1$ (where $T_1 < T_2$), the deletion persists. If timestamps match exactly, the lexicographically higher `device_id` breaks the tie deterministically.
  - This keeps operational domain tables clean in local mode while enabling mathematically deterministic Last-Write-Wins (LWW) conflict resolution when network synchronization is active.

### 11.3 Listening Event Merging
- All listening events utilize UUIDv7 identifiers (which embed millisecond-precision Unix timestamps in the high 48 bits followed by cryptographically random bits).
- When syncing, Desktop executes an idempotent `INSERT OR IGNORE INTO listening_events WHERE id = :event_id`. Duplication is mathematically impossible.

---

## 12. Local-Network Synchronization Protocol Specification

### 12.1 Discovery & Pairing
1. **Discovery:**
   - Harmonia Desktop broadcasts a zero-configuration mDNS service: `_harmonia._tcp.local` on port 8000 with TXT records specifying `version=1.0` and `node_id={UUID}`.
   - Mobile scans for `_harmonia._tcp` upon launching the Sync screen.
2. **First-Time Pairing (Zero-Knowledge Exchange):**
   - Harmonia Desktop displays a high-contrast QR code encoding a single-use 256-bit pairing secret and the desktop's TLS certificate fingerprint.
   - Harmonia Mobile scans the QR code, establishing an encrypted TLS 1.3 channel verified against the pinned certificate fingerprint.
   - Both nodes persist the paired cryptographic identity key; subsequent reconnections execute automatically without user prompting.

### 12.2 Incremental Sync Protocol Flow
1. **Handshake & Capability Exchange:**
   - Mobile sends `POST /api/v1/sync/handshake` containing:
     - `client_device_id`
     - `last_sync_revision`
     - `available_storage_bytes`
2. **Metadata Delta Manifest:**
   - Desktop calculates entities modified since `last_sync_revision` and streams a compact JSON delta stream.
   - Mobile applies changes within a single atomic SQLite transaction.
3. **Telemetry Push:**
   - Mobile sends all un-synced listening events via `POST /api/v1/sync/events`. Upon receiving HTTP 200, mobile marks events `is_synced = 1`.
4. **Resumable Audio File Transfer:**
   - For all tracks designated for offline caching, Mobile issues HTTP requests:
     `GET /api/v1/stream/{file_id}` with standard HTTP byte-range headers (`Range: bytes={start}-`).
   - Transfers write to temporary files (`.part`). Once the stream completes, Mobile verifies the SHA-256 hash against the metadata manifest before moving the file to active storage. If interrupted by Wi-Fi disconnection, the transfer resumes from the exact byte offset upon reconnection.

---

## 13. Security, Privacy & Local Sovereignty Model

1. **Zero External Network Dependencies:**
   - Harmonia Mobile contains zero analytics SDKs, zero telemetry collectors, zero crash-reporting daemons, and zero third-party advertising frameworks.
   - The application requires zero internet permissions to function. Network access is restricted to the local subnet (`ACCESS_NETWORK_STATE`, `INTERNET` for LAN communication only).
2. **Cryptographic Device Pinning:**
   - Sync communication over Wi-Fi is protected with mutual TLS (mTLS) or pre-shared key authenticated tokens. Unpaired devices on the same Wi-Fi network cannot snoop metadata or audio streams.
3. **Filesystem Isolation & Path Concealment:**
   - Mobile adheres to Android Scoped Storage. Internal device filesystem paths are never leaked over network APIs.
   - Path traversal attacks are completely mitigated by referencing files strictly through UUID identifiers and content hashes.
4. **Resilient Metadata Extraction:**
   - Embedded audio tag parsing (ID3v2, Vorbis, MP4 atoms) is executed with strict bounds checking. Malformed tags or corrupted headers fail gracefully without crashing the application process.

---

## 14. Mobile UI & Navigation Architecture

### 14.1 Information Architecture & Navigation Hierarchy

Harmonia Mobile employs a bottom-anchored navigation model designed specifically for effortless one-handed thumb interaction on modern high-aspect-ratio mobile screens.

```
┌────────────────────────────────────────────────────────────┐
│                    TOP APP BAR                             │
│   Harmonia Monogram   │   Screen Title   │   Search Icon   │
├────────────────────────────────────────────────────────────┤
│                                                            │
│                                                            │
│                   ACTIVE SCREEN CONTENT                    │
│           (Home / Library / Offline / Playlists)           │
│                                                            │
│                                                            │
├────────────────────────────────────────────────────────────┤
│                 PERSISTENT MINI-PLAYER DOCK                │
│  [Artwork]  Track Title • Artist              [Play] [Next]│
│  ────────────────────────────────────────── (Progress Bar) │
├────────────────────────────────────────────────────────────┤
│                  BOTTOM NAVIGATION BAR                     │
│    [Home]     [Search]     [Library]     [Offline]         │
└────────────────────────────────────────────────────────────┘
```

### 14.2 Screen Specifications

1. **Home Screen (`/home`):**
   - *Jump Back In:* Horizontal carousel of recently played albums and playlists.
   - *Heavy Rotation:* Frequently played tracks calculated from local listening history.
   - *Recently Added:* Newly imported or synced music.
2. **Library Screen (`/library`):**
   - Segmented control / swipeable tabs:
     - **Songs:** Alphabetical virtualized list with quick-scroll alphabet scrubber index.
     - **Albums:** 2-column high-density grid showing artwork, title, artist, and year.
     - **Artists:** Clean list with round artist avatars and track counts.
     - **Playlists:** User and synced playlists with custom cover grids.
     - **Genres:** Filterable tag cloud.
3. **Offline / Downloads Manager (`/offline`):**
   - Global switch: *"Show Downloaded Only"* (instantly filters entire library to locally available tracks).
   - Storage breakdown meter: Visual bar indicating Harmonia music storage, other apps, and free space.
   - Active download queue: Live progress bars for ongoing Wi-Fi sync transfers with pause/resume buttons.
4. **Search Screen (`/search`):**
   - Zero-latency local SQLite FTS5 search. Results categorize instantaneously into Tracks, Albums, and Artists as the user types.

### 14.3 The Now Playing Experience (Primary Interaction Surface)

The Now Playing screen is an immersive full-screen audiophile surface accessed by tapping or swiping up on the persistent Mini-Player dock.

```
┌────────────────────────────────────────────────────────────┐
│ [Collapse ▼]           Now Playing            [Menu •••]   │
├────────────────────────────────────────────────────────────┤
│                                                            │
│                     ┌─────────────────┐                    │
│                     │                 │                    │
│                     │  ALBUM ARTWORK  │                    │
│                     │   (Squircle)    │                    │
│                     │                 │                    │
│                     └─────────────────┘                    │
│                                                            │
│   Track Title                                [Heart ♡]     │
│   Artist Name — Album Title                                │
│                                                            │
│   ───────────────────●──────────────────────────────────   │
│   01:42                                            04:18   │
│   (Tabular figures prevent layout jitter during scrubbing) │
│                                                            │
│      [Shuffle]   [Prev]   [ PLAY / PAUSE ]   [Next]   [Repeat]     │
│                                                            │
│   ┌────────────────────────────────────────────────────┐   │
│   │ [FLAC 24-bit/96kHz]  [-18.2 LUFS]  [ReplayGain ON] │   │
│   └────────────────────────────────────────────────────┘   │
│                                                            │
│              [Swipe Up: Up Next Queue & Lyrics]            │
└────────────────────────────────────────────────────────────┘
```

- **Thumb-Zone Optimization:** Transport controls (Play/Pause, Skip, Scrub bar) occupy the lower 40% of the screen.
- **Audio Quality Inspector Badge:** Audiophile pill displaying format, bitrate, sample rate, bit depth, and LUFS measurement. Tapping the badge opens a technical modal detailing ITU-R BS.1770 audio properties.
- **Draggable Up Next Queue:** Bottom sheet sliding up over the artwork to display upcoming tracks, with drag-and-drop reordering.

---

## 15. Brand Identity Integration

Harmonia Mobile faithfully adopts the approved **Harmonia Visual Identity** (`docs/BRAND_IDENTITY.md`):

1. **Application Icon:**
   - Uses the canonical **Resonant Monogram** (`assets/brand/icon-512x512.png` and `assets/brand/harmonia-mark.svg`).
   - Rendered within an Android adaptive squircle mask, respecting the 62% safe-zone rule to prevent stem clipping.
2. **Color Palette:**
   - **Background:** Obsidian Space (`#090B10`) — deep, non-fatiguing dark canvas.
   - **Surfaces & Cards:** Acoustic Charcoal (`#141721`) and Studio Slate (`#1E2333`).
   - **Primary Accent:** Acoustic Teal (`#00D2B4`) — indicates active playback state, scrubber thumb, verified badges, and offline availability.
   - **Typography:** Pure Signal White (`#F0F3F8`) for titles; Muted Slate (`#8E95A5`) for secondary metadata.
3. **Typography & Tabular Figures:**
   - Primary typeface: **Inter** / system sans-serif.
   - **Mandatory Tabular Figures (`tnum`):** Enforced across all timecode scrubbers (`03:42`), duration displays, and audio format specs to eliminate horizontal layout jitter during playback.

---

## 16. Harmonia AI Architectural Boundary

Harmonia Mobile preserves strict adherence to the ecosystem boundary:
> **"Harmonia owns the music. Harmonia AI understands the person."**

```
┌────────────────────────────────────────────────────────┐
│                   HARMONIA MOBILE                      │
│     Autonomous Playback, Queue, Local SQLite DB        │
│     (100% Functional with Zero AI Infrastructure)      │
└───────────────────────────▲────────────────────────────┘
                            │
               OPTIONAL REST/JSON INTERFACE
                            │
┌───────────────────────────▼────────────────────────────┐
│                     HARMONIA AI                        │
│   Optional Companion Service (Local LLM / Context Engine│
│   Generates taste profiles, smart playlists, prompts   │
└────────────────────────────────────────────────────────┘
```

- **Zero In-Engine AI:** No neural networks, probabilistic models, or recommendation weights exist in the mobile playback loop or local SQLite database.
- **Optional Client Interaction:** When Harmonia AI is present, it interacts with Harmonia Mobile purely as an external client:
  1. AI queries the mobile library via standard read-only JSON interfaces.
  2. AI generates a playlist (a list of track UUIDs) and submits it via `POST /playlists`.
  3. Harmonia Mobile enqueues the playlist deterministically.
- **Failure Mode:** If Harmonia AI is unavailable, uninstalled, or disabled, Harmonia Mobile behaves flawlessly without any warning prompts or disabled UI states.

---

## 17. Harmonia Core API Alignment & Mobile Extension Contract

### 17.1 Existing Endpoints Reused Without Modification
- `GET /api/v1/tracks` — Paginated metadata retrieval.
- `GET /api/v1/tracks/{track_id}` — Detailed track inspection.
- `GET /api/v1/albums` & `GET /api/v1/albums/{album_id}` — Album browsing.
- `GET /api/v1/artists` & `GET /api/v1/artists/{artist_id}` — Artist browsing.
- `GET /api/v1/search` — FTS5 query search.
- `GET /api/v1/artwork/{artwork_hash}` — SHA-256 content-addressed artwork download.
- `GET /api/v1/stream/{file_id}` — Audio streaming and file downloading via HTTP 206 Partial Content.
- `GET /api/v1/health` — Sync connectivity check.

### 17.2 Endpoints Reserved for Desktop Only (Not Used by Mobile)
- `POST /api/v1/files/scan` — Workstation filesystem import.
- `POST /api/v1/files/analyze` — Server-side FFmpeg subprocess trigger.
- `GET /api/v1/duplicates` — Archival deduplication review.

### 17.3 Proposed Future Endpoints (Required for Phase M7 Desktop Sync)
*(Note: These are specifications for future implementation; no backend code is modified during this phase).*
- `GET /api/v1/sync/manifest?since_revision={rev}` — Returns delta changes of tracks, albums, artists, and playlists.
- `POST /api/v1/sync/events` — Batch ingestion of mobile `listening_events`.
- `POST /api/v1/sync/favorites` — Bidirectional sync of user favorites.
- `POST /api/v1/sync/ratings` — Bidirectional sync of user ratings.
- `POST /api/v1/sync/playlists` — Bidirectional sync and merge of user playlists.

---

## 18. Large Library Performance Architecture (10,000 to 100,000+ Tracks)

### 18.1 Database & Query Optimization
- **Index Optimization:** B-Tree indices covering `(album_id, track_number)`, `(artist_id, title)`, and lowercase collation indexes (`NOCASE`) optimize lookup performance for large music collections.
- **Cursor-Based Windowing:** Compose `LazyColumn` couples directly with Android Room and AndroidX Paging 3 (`PagingSource`), streaming windowed chunks into memory to prevent memory pressure during rapid scrolling.
- **SQLite Pragmas:** Configured with `PRAGMA journal_mode = WAL`, `PRAGMA synchronous = NORMAL`, `PRAGMA cache_size = -16000` (16MB memory cache), and `PRAGMA temp_store = MEMORY`.

### 18.2 Two-Tier Artwork Caching Pipeline
- **Memory (L1):** Android `LruCache<String, Bitmap>` capped at 32MB. Bitmaps are downsampled at load time based on target view bounds (e.g., 160×160 for list rows; 800×800 for Now Playing).
- **Disk (L2):** Content-addressed disk cache storing raw images named by their SHA-256 hash (`cache/artwork/{hash}.webp`). Capped at 250MB with LRU eviction. Missing artwork displays an acoustic geometric gradient generated deterministically from the album UUID.

---

## 19. Two-Tier Identity Model: Metadata Presence vs. Local File Availability

A central architectural innovation of Harmonia Mobile is the decoupling of **Logical Track Awareness** from **Physical Bitstream Presence**:

```
                              ┌────────────────────┐
                              │    Logical Track   │
                              │ (Known to Library) │
                              └─────────┬──────────┘
                                        │
                 ┌──────────────────────┴──────────────────────┐
                 │                                             │
                 ▼                                             ▼
       [Local File Exists]                           [Local File Absent]
       - Stored in Flash / SD Card                   - Synced metadata only
       - Bitstream verified                          - Not downloaded yet
                 │                                             │
                 ▼                                             ▼
         OFFLINE PLAYABLE                              UNAVAILABLE OFFLINE
    (Full vibrant UI, normal text)              (Muted title, download icon,
                                                 filtered out in Offline Mode)
```

1. **State Independence:** A user may synchronize their entire 50,000-track desktop metadata library (requiring ~35MB of SQLite storage), allowing full browsing, searching, and playlist curation without storing all audio files locally.
2. **Selective Offline Caching:** The user explicitly marks albums, artists, or playlists with *"Make Available Offline"*. The sync engine downloads the corresponding audio files in the background.
3. **Strict Offline Enforcement:** When *"Show Downloaded Only"* is enabled, queries execute `INNER JOIN mobile_local_files ON ... WHERE availability_status = 'AVAILABLE'`, completely hiding non-downloaded tracks and guaranteeing that playback never fails due to an absent bitstream.

---

## 20. Resilient Error & Offline Failure Modes

| Failure Scenario | Immediate System Reaction | Recovery / Fallback Behavior |
| :--- | :--- | :--- |
| **Wi-Fi disconnects mid-playback** | Zero interruption. Current track plays from local cache. | Sync daemon suspends silently; zero alert dialogs or interruptions. |
| **Desktop server shuts down** | Local player operates normally. | Sync status indicator switches from "Connected" to "Idle". |
| **Physical audio file deleted by user** | Audio engine detects file missing error on playback attempt. | Marks `availability_status = 'MISSING'`; cleanly advances to next queue track; displays brief toast notification. |
| **Corrupted audio stream / decode error** | ExoPlayer catches `AudioCodecException`. | Skips bad track automatically; logs failure; prevents infinite skip loops by halting if 5 consecutive tracks fail. |
| **Device storage reaches capacity** | Sync worker detects < 500MB free space. | Suspends active downloads; displays non-blocking storage alert; protects existing library database integrity. |
| **Application process killed by OS** | Android OS terminates service due to extreme memory pressure. | On restart, player restores exact queue, track, and playback position from `mobile_playback_state`. |
| **Bluetooth audio device disconnects** | System fires `ACTION_AUDIO_BECOMING_NOISY`. | Playback engine pauses immediately; prevents audio from playing over phone speakers. |

---

## 21. Comprehensive Testing Strategy

### 21.1 Verification Matrix

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HARMONIA MOBILE TEST SUITE                      │
├─────────────────┬──────────────────────────────────┬───────────────────┤
│ Test Tier       │ Test Focus & Scope               │ Execution Target  │
├─────────────────┼──────────────────────────────────┼───────────────────┤
│ Unit Tests      │ Queue state machine, shuffle     │ JVM Local         │
│                 │ algorithm, CRDT conflict rules,  │ (< 5 seconds)     │
│                 │ SQLDelight queries, Lamport LWW  │                   │
├─────────────────┼──────────────────────────────────┼───────────────────┤
│ Integration     │ SQLite migration integrity,      │ Robolectric & JVM │
│ Tests           │ SAF metadata extraction, Ktor    │ (< 30 seconds)    │
│                 │ sync protocol, HTTP range resume │                   │
├─────────────────┼──────────────────────────────────┼───────────────────┤
│ Instrumented    │ Foreground service lifecycle,    │ Android Emulator  │
│ Device Tests    │ MediaSession token registration, │ & Physical Device │
│                 │ Audio focus ducking & interrupts │                   │
├─────────────────┼──────────────────────────────────┼───────────────────┤
│ Benchmark &     │ 100,000 track scroll frame rate, │ Physical Hardware │
│ Performance     │ cold startup latency, battery    │ (Profiled Runs)   │
│                 │ drain over 8h continuous audio   │                   │
└─────────────────┴──────────────────────────────────┴───────────────────┘
```

### 21.2 Core Test Invariants
- **Queue Invariant Test:** Verifies that shuffling a 10,000-track queue is deterministic, contains zero duplicate IDs, and preserves the currently playing track at index 0.
- **Audio Focus Invariant Test:** Simulates incoming phone call (`AUDIOFOCUS_LOSS_TRANSIENT`) and validates that `is_playing` transitions to `false` within 50ms.
- **Offline Invariant Test:** Disables all network interfaces on the test device and executes a full playback loop (play, pause, next, prev, seek, playlist create) to prove 100% network independence.

---

## 22. Architectural Decision Records (ADRs)

Continuing Harmonia's architectural decision record discipline from the Core foundation (where **ADR-002** established `Track` vs `TrackFile` separation and **ADR-013** established `AudioAnalysis` signal intelligence):

---

### ADR-020: Mobile Technology Stack — Android-First Native Architecture (Kotlin + Media3)

- **Status:** Accepted (M0.1 Reconciled)
- **Date:** October 2026
- **Context:** Harmonia requires a native mobile application prioritizing offline playback reliability, low-latency background audio, and Android media integration, while preventing long-term architectural lock-in against future iOS support.
- **Decision:** Build Harmonia Mobile as an Android-First Native application: Kotlin, Jetpack Compose, AndroidX Media3 (`MediaLibraryService` + ExoPlayer), and Android Room SQLite.
- **Rationale:** Audio players require uncompromising background stability, precise audio focus handling, and seamless system media session integration. Domain contracts are maintained in pure Kotlin without `android.*` dependencies to facilitate future iOS development, without prematurely imposing multiplatform Gradle complexity during early foundation phases.
- **Alternatives Considered:**
  - *Kotlin Multiplatform (KMP) at M1:* Premature build complexity on CI and tooling friction during early Android foundation development.
  - *Flutter / React Native:* Audio focus latency, isolate bridging overhead, and fragile background service lifecycles.
- **Consequences:** Focuses Phase M1–M8 strictly on native Android excellence.

---

### ADR-021: Mobile Local Persistence — Android Room with SQLite WAL Mode

- **Status:** Accepted (M0.1 Reconciled)
- **Date:** October 2026
- **Context:** The mobile player must manage large libraries with responsive query performance, support SQLite FTS5 search, provide seamless Android Studio debugging, and integrate natively with list paging.
- **Decision:** Adopt Android Room (SQLite 3 with WAL mode) with AndroidX Paging 3 integration.
- **Rationale:** Room provides compile-time query verification via KSP, first-party Android Studio Database Inspector support for real-time device debugging, and seamless integration with Paging 3 (`PagingSource`) for smooth virtualized scrolling. Room also provides an official KMP evolution path (Room 2.7+).
- **Alternatives Considered:**
  - *SQLDelight:* Lacks first-party Android Studio Database Inspector integration and native Paging 3 support.
  - *Realm / ObjectBox:* Proprietary query engines with binary size overhead.
- **Consequences:** Schema migrations use Room schema JSON verification (`schemas/1.json`).

---

### ADR-022: Playback Decoupling — Foreground MediaLibraryService with Unidirectional PlayerController

- **Status:** Accepted (M0.1 Reconciled)
- **Date:** October 2026
- **Context:** Mobile operating systems aggressively terminate background audio processes that fail to integrate properly with OS foreground service lifecycles. Furthermore, UI components must never directly mutate low-level player hardware state.
- **Decision:** Isolate all playback logic inside an Android `MediaLibraryService` (`HarmoniaPlaybackService`). Mediate all UI interactions through a unidirectional `PlayerController` exposing immutable `StateFlow<PlayerSnapshot>` states.
- **Rationale:** Decouples UI component lifecycles from audio playback. If the UI activity is destroyed to reclaim memory, playback continues uninterrupted in the foreground service. `MediaLibraryService` also reserves the architectural integration boundary for Android Auto browsing.
- **Alternatives Considered:**
  - *In-Activity MediaPlayer:* Prone to immediate process death when screen turns off.
  - *Bare MediaSessionService:* Lacks standard MediaLibrarySession hierarchical browsing for automotive integration.
- **Consequences:** Requires implementing `MediaLibrarySession.Callback` methods.

---

### ADR-023: Sovereign Local Storage & Android Platform MediaMetadataRetriever Tag Reading

- **Status:** Accepted (M0.1 Reconciled)
- **Date:** October 2026
- **Context:** Harmonia Mobile must function as an independent personal music player, allowing users to play music imported directly on their device via filesystems or SD cards without ever connecting to a Harmonia Desktop instance.
- **Decision:** Implement a multi-source `StorageSource` abstraction supporting direct SAF on-device imports, USB/MTP transfers, and desktop synchronization. Use the Android platform's built-in `android.media.MediaMetadataRetriever` for provisional on-device tag reading.
- **Rationale:** Adds zero dependency overhead (0 KB APK size), uses stable public Android APIs, and handles MP3, FLAC, M4A, Vorbis, and embedded artwork natively. Mobile parsing is strictly read-only and provisional; Harmonia Desktop remains the sole authoritative metadata source.
- **Alternatives Considered:**
  - *androidx.media3:media3-extractor:* Heavily marked with `@UnstableApi` annotations; intended for playback demuxing rather than metadata inspection.
  - *Heavy C++ TagLib JNI:* Adds unnecessary binary size (2–5 MB) and NDK toolchain complexity.
- **Consequences:** Mobile parsing is restricted to provisional read-only inspection. Desktop sync unconditionally supersedes mobile tags.

---

### ADR-024: Local-First Optional Synchronization Protocol & CRDT-Inspired Conflict Resolution

- **Status:** Proposed
- **Date:** October 2026
- **Context:** Synchronization between Harmonia Desktop and Mobile over Wi-Fi must be resilient to intermittent connection loss, simultaneous playlist edits, and divergent ratings without data loss.
- **Decision:** Implement a local Wi-Fi sync protocol utilizing mDNS discovery, TLS encryption, and deterministic conflict resolution (Desktop authority for audio metadata; Mobile authority for listening history; Last-Write-Wins with Lamport timestamps for favorites/ratings; 3-way rebase for playlists).
- **Rationale:** Prevents race conditions and ensures synchronization is completely idempotent and resumable.
- **Alternatives Considered:**
  - *Full Two-Way Database Mirroring (Symmetric Replication):* Overly complex; breaks due to schema divergences between desktop engineering tables and mobile playback tables.
- **Consequences:** Requires maintaining revision vectors and tombstones for deleted items.

---

### ADR-025: Two-Tier Identity Model — Decoupled Metadata vs. Local Bitstream Availability

- **Status:** Proposed
- **Date:** October 2026
- **Context:** Users with large libraries (500GB+) cannot fit all audio files on their mobile devices, but still desire full browsing, searching, and playlist curation capabilities.
- **Decision:** Decouple `Track` (logical metadata presence) from `LocalFile` (physical bitstream presence on device).
- **Rationale:** Allows users to sync their entire metadata catalog into a compact SQLite database (~35MB) while selectively downloading only the albums or playlists they choose.
- **Alternatives Considered:**
  - *Only Sync What Is Downloaded:* Prevents mobile users from browsing their full music collection and requesting remote downloads.
- **Consequences:** UI must render visual indicators distinguishing downloaded tracks from metadata-only tracks, and support an instant "Offline Only" filter.

---

## 23. Reference-First Engineering: Open-Source Project Comparative Study

In accordance with Section 26 of the design specification, established open-source music players and synchronization systems were evaluated to leverage proven patterns and avoid known pitfalls:

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                             Open-Source Reference Analysis                                       │
├──────────────┬─────────────┬───────────┬───────────────────────────────┬─────────────────────────┤
│ Project      │ License     │ Maturity  │ Architectural Strengths       │ Lessons for Harmonia    │
├──────────────┼─────────────┼───────────┼───────────────────────────────┼─────────────────────────┤
│ Auxio        │ GPL-3.0     │ High      │ Pure offline, zero bloat,     │ Adopt clean Media3      │
│ (Android)    │             │ (Active)  │ fast ExoPlayer integration,   │ audio focus handling;   │
│              │             │           │ excellent MediaStore scanner  │ avoid hardcoded UI state│
├──────────────┼─────────────┼───────────┼───────────────────────────────┼─────────────────────────┤
│ Gramophone   │ GPL-3.0     │ Medium    │ Material You Jetpack Compose, │ Study Compose playback  │
│ (Android)    │             │ (Active)  │ modern Media3 session architecture, controls; avoid heavy │
│              │             │           │ Room persistence              │ Room reflection overhead│
├──────────────┼─────────────┼───────────┼───────────────────────────────┼─────────────────────────┤
│ Finamp       │ GPL-3.0     │ High      │ Jellyfin mobile client in     │ Strong offline download │
│ (Cross-plat) │             │ (Active)  │ Flutter using just_audio      │ cache model; avoid      │
│              │             │           │                               │ Flutter isolate bridge  │
├──────────────┼─────────────┼───────────┼───────────────────────────────┼─────────────────────────┤
│ Symfonium    │ Proprietary │ Very High │ Gold standard for local music │ Adopt multi-source      │
│ (Android)    │ Reference   │ (Active)  │ caching, gapless playback,    │ abstraction and         │
│              │ Benchmark   │           │ ReplayGain, Android Auto      │ strict offline mode     │
├──────────────┼─────────────┼───────────┼───────────────────────────────┼─────────────────────────┤
│ LocalSend    │ MIT         │ High      │ mDNS discovery, TLS Wi-Fi     │ Adopt lightweight mDNS  │
│ (Cross-plat) │             │ (Active)  │ transfer without central server│ discovery & pairing flow│
└──────────────┴─────────────┴───────────┴───────────────────────────────┴─────────────────────────┘
```

### Key Takeaways Incorporated into Harmonia Mobile:
- **From Auxio:** The discipline of treating offline music as sovereign; eliminating all network calls from the playback path; implementing rock-solid `AUDIO_BECOMING_NOISY` headset unplug handlers.
- **From Gramophone:** The elegant separation of Media3 session notifications and Jetpack Compose state collection.
- **From LocalSend:** The zero-configuration mDNS discovery mechanism for finding desktop instances on the local Wi-Fi subnet without needing cloud relay servers.
- **What to Avoid (from Flutter/React Native players):** Avoid bridging audio commands across isolates or JS threads; audio state mutations must occur directly on the native platform audio thread.

---

## 24. Mobile Phased Implementation Roadmap

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                               HARMONIA MOBILE ROADMAP                                  │
├────────┬─────────────────────────────┬─────────────────────────────────────────────────┤
│ Phase  │ Title                       │ Core Deliverables                               │
├────────┼─────────────────────────────┼─────────────────────────────────────────────────┤
│ **M0** │ Architecture & Specs        │ Complete architectural design (THIS DOCUMENT)   │
│ **M1** │ Native App Foundation       │ KMP project structure, SQLDelight DB, Theme/Brand│
│ **M2** │ Local Storage & Importer    │ SAF file picker, on-device tag reader, scanner  │
│ **M3** │ Native Playback Engine      │ Media3 ExoPlayer, Foreground Svc, Lockscreen/BT │
│ **M4** │ Playlists & Active Queue    │ Queue manager, shuffle/repeat, playlist editor  │
│ **M5** │ Offline Availability Model  │ Two-tier identity, storage manager, indicators  │
│ **M6** │ Listening Telemetry         │ Append-only listening event logger (UUIDv7)     │
│ **M7** │ Desktop Wi-Fi Sync          │ mDNS discovery, TLS pairing, delta sync, resume │
│ **M8** │ Hardening & Audiophile Polsh│ ReplayGain normalization, gapless testing, 100k │
│ **M9** │ Optional Harmonia AI Bridge │ Client port for AI-curated playlist injection   │
└────────┴─────────────────────────────┴─────────────────────────────────────────────────┘
```

### Detailed Phase Specifications

#### Phase M0: Architecture & Specifications (Current Phase)
- **Scope:** Complete architectural definition, ADRs, database schemas, and protocol design.
- **Acceptance Criteria:** Comprehensive design document approved; zero implementation code written.

#### Phase M1: Native App Foundation
- **Scope:** Set up `harmonia-mobile` repository; configure Kotlin Multiplatform with Android target; implement SQLDelight schema; integrate Harmonia brand system (colors, typography, squircle icon).
- **Acceptance Criteria:** Clean build; database migrations verify; app launches to empty state branded UI.
- **Explicit Non-Goals:** Audio playback; network sync.

#### Phase M2: Local Storage & On-Device Import
- **Scope:** Storage Access Framework (SAF) folder picker; background file crawler; pure Kotlin tag reader (extracting title, artist, album, track number, duration, artwork); SQLite indexing.
- **Acceptance Criteria:** User can select a local folder on their phone containing FLAC/MP3 files; library indexes 1,000 files in < 5 seconds with artwork thumbnails.
- **Explicit Non-Goals:** Network streaming or desktop connection.

#### Phase M3: Native Audio Playback Subsystem
- **Scope:** `androidx.media3` integration; `HarmoniaPlaybackService` foreground service; notification controls; lock-screen controls; Bluetooth AVRCP integration; audio focus handling (ducking/pausing); headphone disconnect handler.
- **Acceptance Criteria:** Continuous background playback of local FLAC/MP3 files; lockscreen scrubbing works; unplugging headphones pauses playback instantly.
- **Explicit Non-Goals:** Desktop sync; complex playlist editing.

#### Phase M4: Queue Management & Playlists
- **Scope:** Dynamic queue management; non-repeating Fisher-Yates shuffle; repeat modes; user playlist creation, editing, and track reordering; persistent playback position.
- **Acceptance Criteria:** User can reorder queue tracks; killing and relaunching app restores exact track and millisecond playback position.
- **Explicit Non-Goals:** Cloud playlists.

#### Phase M5: Offline Availability & Storage Management
- **Scope:** Implementation of Two-Tier identity model (`mobile_tracks` vs `mobile_local_files`); "Downloaded Only" global filter switch; device storage visualizer.
- **Acceptance Criteria:** User can toggle "Downloaded Only" to hide non-cached music; storage meter accurately reports Harmonia file usage.

#### Phase M6: Listening Telemetry
- **Scope:** Immutable append-only `mobile_listening_events` logging with UUIDv7; tracking play start, duration, completion (>80%), and output route (Bluetooth vs Speaker).
- **Acceptance Criteria:** Playback records factual events in SQLite without performance hit; verifiable via unit tests.

#### Phase M7: Harmonia Desktop Wi-Fi Sync
- **Scope:** mDNS discovery (`_harmonia._tcp`); QR-code pairing with pinned TLS; delta manifest synchronization; resumable HTTP 206 audio file downloading; deterministic conflict resolution.
- **Acceptance Criteria:** Phone connects to desktop over local Wi-Fi; syncs 5,000 track metadata records in < 10 seconds; downloads selected album with resume support.
- **Explicit Non-Goals:** Internet-based remote streaming or cloud relay.

#### Phase M8: Polish, Audiophile Hardening & Performance Tuning
- **Scope:** ReplayGain / ITU-R BS.1770 volume normalization; gapless playback validation; performance tuning for 100,000 tracks; Android Auto basic media session integration.
- **Acceptance Criteria:** Zero stutter on 100k track list scroll; seamless gapless playback between live album tracks.

#### Phase M9: Optional Harmonia AI Companion Integration
- **Scope:** Optional client port for receiving smart playlist recommendations from external Harmonia AI instances.
- **Acceptance Criteria:** Receives AI playlist via JSON; enqueues seamlessly; player continues functioning 100% when AI is disabled.

---

## 25. Open Decisions for Future Architectural Alignment

Before Phase M1 implementation begins, the following tactical engineering decisions are flagged for collaborative review:

1. **Compose Multiplatform vs. Platform-Native UI for iOS:**
   - *Option A:* Use Compose Multiplatform (CMP) for both Android and iOS UI.
   - *Option B:* Use CMP for Android, but write a thin native SwiftUI layer on iOS bound to the shared KMP domain/data module.
   - *Recommendation:* Begin with Compose Multiplatform for rapid parity; evaluate SwiftUI only if iOS Metal rendering introduces platform idiosyncrasies.
2. **On-Device Audio Metadata Reader Library:**
   - Need to select a lightweight, pure Kotlin or C/C++ (via Kotlin/Native) metadata extraction library for direct phone imports (e.g., TagLib via NDK, or pure Kotlin parser) to avoid heavy external dependencies.
3. **Android Auto Interface Model:**
   - Determine whether Android Auto support in Phase M8 should use standard `MediaLibraryService` browse trees (standard media browser UI) or custom templates via `androidx.car.app`. Standard `MediaLibraryService` is recommended as it requires zero extra automotive permission overhead.

---

*End of Architecture Design Document — Harmonia Mobile v0.1*
