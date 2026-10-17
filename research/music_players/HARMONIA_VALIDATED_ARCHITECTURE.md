# Harmonia Validated System Architecture

**Document Classification:** Pre-Implementation Freeze — Authoritative Architecture Specification  
**Status:** FROZEN & APPROVED  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Foundational Principle:** *"Harmonia owns the music. Harmonia AI understands the person."*

---

## 1. Ecosystem Overview & Architecture Topology

Harmonia is structured as a Clean Architecture system comprising four distinct, decoupled tiers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HARMONIA ECOSYSTEM TOPOLOGY                     │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
 ┌──────────────────────────────────────────────────────────────────────┐
 │                     HARMONIA CORE (Desktop / Server)                 │
 │  • Archival Music Library & Data Authority (SQLite WAL + FTS5)       │
 │  • PlaybackPort Domain Interface & PlaybackState Coordinator         │
 │  • Objective ITU-R BS.1770-4 Signal Analysis Engine (FFmpeg Worker)  │
 │  • Metadata Provenance & Consensus Resolver (MusicBrainz / AcoustID) │
 │  • Content-Addressed Artwork Storage (SHA-256)                       │
 │  • Transport Layer: HTTP 206 Partial Content (Path Concealment)      │
 │  • LocalSync Wi-Fi Server (mDNS / TLS Peer Discovery & Handshake)    │
 └─────────────────┬──────────────────────────────────┬─────────────────┘
                   │                                  │
    HTTP REST API  │                   LocalSync      │ Encrypted Wi-Fi
    (Loopback Only)│                   Protocol       │ (mDNS / TLS)
                   ▼                                  ▼
 ┌──────────────────────────────────┐ ┌─────────────────────────────────┐
 │       HARMONIA AI (Local)        │ │    HARMONIA MOBILE (Android)    │
 │ • Subjective Taste Profiles      │ │ • Sovereign Offline Player      │
 │ • Multi-Dimensional Context      │ │ • Media3 ExoPlayer Engine       │
 │ • Conversational NLU             │ │ • Local Embedded Room SQLite    │
 │ • Intelligent Dynamic Playlists  │ │ • SAF & MediaStore Local Scanner│
 │ • Private DB (harmonia_ai.db)    │ │ • Offline Queue & Diagnostics   │
 └──────────────────────────────────┘ └─────────────────────────────────┘
                   ▲
                   │ Embedded Web UI (`/` & `/ui`)
 ┌─────────────────┴────────────────┐
 │     HARMONIA DESKTOP CONSOLE     │
 │ • Audio Engineering Console      │
 │ • Live ITU-R BS.1770-4 Meters    │
 │ • Waveform & Spectrum Visualizer │
 │ • Metadata Curation & Inbox      │
 │ • HTML5 / Native Playback Client │
 └──────────────────────────────────┘
```

---

## 2. Definitive Ownership & Separation of Concerns

### 2.1 Data Ownership
- **Harmonia Core Owns:**
  - All factual music entities (`Track`, `TrackFile`, `Album`, `Artist`, `Tag`, `MetadataFieldRecord`).
  - Objective audio signal measurements (`AudioAnalysis`).
  - Physical audio file storage across registered library filesystem roots.
  - Authoritative listening history event log (`ListeningEvent`).
  - User playlists, ratings, and favorite statuses.
- **Harmonia Mobile Owns:**
  - Local device playback state (active track, seek position, local queue).
  - Sandboxed local audio cache and SAF directory permissions.
  - Local offline Room database (mirror of user-synced catalog + local device audio files).
  - Unsynced local listening events buffer.
- **Harmonia AI Owns:**
  - Subjective behavioral models (genre affinity vectors, artist affinity weights).
  - Situational context rules (morning focus, late-night relaxation, workout tempo curves).
  - Natural language conversational memory and chat sessions.
  - Semantic embeddings of musical tracks and personal playlists.
  - **Invariant:** Harmonia AI has ZERO write access to Harmonia Core's database or filesystem.

---

### 2.2 Playback Contract: `PlaybackPort` & `PlaybackSource`

#### The Domain Contract: `PlaybackPort`
Harmonia does NOT couple its domain to any audio hardware, browser API, or streaming protocol. The playback abstraction is defined as the domain port protocol **`PlaybackPort`** (`harmonia.domain.ports.playback_port`), supporting:
- Transport controls: `play()`, `pause()`, `resume()`, `stop()`, `seek(position_seconds)`
- Navigation: `next()`, `previous()`
- Gain & Audio: `set_volume(volume_scalar)`
- Queue management: `set_queue(track_ids, play_index)`, `insert_play_next(track_id)`
- Inspection: `get_state() -> PlaybackState`

#### The Audio Delivery Abstraction: `PlaybackSource`
Core does NOT force every playback platform to use HTTP. While HTTP 206 is an excellent file transport mechanism for web browsers and network players, native engines require direct file descriptors or platform URIs. Audio backends resolve audio via polymorphic **`PlaybackSource`**:
1. **`LocalFileSource(file_path: Path)`**: Used by native desktop engines (e.g., Python audio output, future `libmpv`) for direct unbuffered filesystem reads.
2. **`StreamUriSource(stream_url: str, mime_type: str)`**: Used by web frontends (Desktop Console HTML5 Audio) consuming Core's secure RFC 7233 HTTP 206 range endpoint (`GET /api/v1/tracks/{id}/stream`).
3. **`PlatformUriSource(content_uri: str)`**: Used by mobile players (Android `content://` URIs via Storage Access Framework).

---

### 2.3 Listening-Event Telemetry Ownership

- **Dual-Pointer Identity:** Every playback session records an immutable `ListeningEvent` row containing:
  - `id` (UUIDv4)
  - `track_id` (UUID link to logical musical work for user analytics and AI)
  - `track_file_id` (UUID link to specific physical file encoding played for technical audit)
  - `timestamp_utc` (playback start time in UTC)
  - `duration_listened_seconds`, `track_duration_seconds`, `was_completed`, `was_skipped`, `playback_source`
  - `device_id` (UUID identifying Desktop or Mobile instance; **nullable in Phase 4**, populated during Phase 5 LocalSync).
- **Session Lifecycle & Deduplication:** Ephemeral session tracking in memory commits exactly one record upon track change, completion, skip, or stop. Rapid seeks or pause/resume toggles do not fragment history.
- **Reconciliation:** Mobile records local events offline. When paired over Wi-Fi, Mobile pushes new events to Desktop Core. Events are **monotonically additive**; no play count can be lost.

---

### 2.4 Metadata Ownership & Provenance

- **Field-Level Provenance:** Every metadata field on an entity tracks its origin:
  $$\text{USER (100)} > \text{PROVIDER (50)} > \text{FILE (20)} > \text{PATH (10)}$$
- **User Lock Immutability:** Any field edited by the user receives priority `USER` and a lock flag. Rescans or provider matches can update unset fields, but will NEVER overwrite user-locked values.
- **Non-Destructive Storage:** Metadata updates reside virtually in SQLite. Source audio files on disk remain strictly read-only unless the user explicitly invokes an intentional file tag write-back command.

---

### 2.5 Loudness Pipeline Separation

Harmonia enforces a strict three-tier separation of concerns for loudness normalization:
1. **Analysis Tier (Phase 3 Core):** Objective ITU-R BS.1770-4 EBUR128 measurements (`integrated_lufs`, `true_peak_dbtp`, `loudness_range_lu`, silence boundaries) calculated offline by FFmpeg workers and stored in `AudioAnalysis`.
2. **Playback Policy Tier (Phase 4 Application):** Calculates target gain offset $\Delta G = L_{\text{target}} - L_{\text{measured}} + P$, clamped by True Peak headroom guard $G_{\text{max\_safe}} = -TP - 0.5 \text{ dBTP}$ to guarantee zero inter-sample clipping. Emits scalar linear multiplier.
3. **DSP Tier (Phase 6 Audio Engine):** Real-time signal processing (Parametric AutoEQ biquads, FIR crossfeed convolver, optional brickwall limiter) performed downstream in the audio backend.

---

## 3. Shared Architectural Contracts

```
┌────────────────────────────────────────────────────────────────────────┐
│                      SHARED ARCHITECTURAL CONTRACTS                    │
├────────────────────┬───────────────────────────────────────────────────┤
│ Universal Entity   │ Authoritative entities (Track, TrackFile, Album,  │
│ Identifiers        │ Artist, ListeningEvent) use 128-bit RFC 4122      │
│                    │ UUIDs. Identifiers are stable across syncs.       │
├────────────────────┼───────────────────────────────────────────────────┤
│ Playback Contract  │ • PlaybackPort domain protocol.                   │
│                    │ • PlaybackSource polymorphism (File / HTTP / URI).│
│                    │ • PlaybackState snapshot observation.             │
├────────────────────┼───────────────────────────────────────────────────┤
│ Audio Transport    │ • RFC 7233 HTTP 206 byte-range streaming.         │
│ (Web Clients)      │ • Path concealment: access exclusively by UUID.   │
│                    │ • Objective ITU-R BS.1770-4 LUFS telemetry.       │
├────────────────────┼───────────────────────────────────────────────────┤
│ LocalSync P2P      │ • Discovery: mDNS announcement (_harmonia._tcp).  │
│ Protocol Contract  │ • Security: Dynamic TLS self-signed certificates. │
│                    │ • Authentication: Ed25519 cryptographic pairing.  │
│                    │ • Delta Catalog JSON + chunked stream transfer.   │
├────────────────────┼───────────────────────────────────────────────────┤
│ Content Hashing    │ SHA-256 content hashes identify audio streams and │
│                    │ album artwork universally across all devices.     │
└────────────────────┴───────────────────────────────────────────────────┘
```

---

## 4. Subsystem Specifications

### 4.1 Harmonia Core (Desktop / Server)
- **Language / Runtime:** Python 3.11+
- **Architecture:** Clean Architecture (`domain`, `application`, `infrastructure`, `presentation`).
- **Database:** SQLite 3 in WAL mode with foreign keys enabled, busy timeout 5000ms, and FTS5 full-text search.
- **Audio Intelligence:** Subprocess FFmpeg/ffprobe analyzer with process group timeouts and stale job crash recovery.
- **API Boundary:** FastAPI bound exclusively to loopback (`127.0.0.1:8000`), completely concealing internal host filesystem paths.
- **Playback Controller:** Implements `PlaybackPort` managing queue state, shuffle context, and factual `ListeningEvent` emission.

### 4.2 Harmonia Mobile (Android)
- **Language / Runtime:** Kotlin 2.0+ / Android API 26+ (Target API 35+)
- **UI Framework:** Jetpack Compose + Material 3 (Material You Monet dynamic color with contrast clamping).
- **Audio Engine:** AndroidX Media3 (`androidx.media3.exoplayer` + `androidx.media3.session`).
- **Persistence:** Android Room (SQLite WAL) with compile-time verified DAOs and Jetpack Paging 3 integration.
- **Scanner:** MediaStore incremental delta queries + SAF document tree traversal (`SourceIdentity.kt` pattern).

### 4.3 Harmonia Desktop Console
- **Implementation:** Embedded Desktop Web UI served directly by FastAPI at `/` and `/ui`.
- **Capabilities:**
  - Transport dock player controlling playback via `PlaybackPort` REST endpoints.
  - Real-time ITU-R BS.1770-4 meters (integrated LUFS, True Peak dBTP, LRA, Crest factor).
  - Waveform and spectrum inspector for detecting lossy upconverts.
  - Triage Inbox for reviewing ambiguous metadata matches and duplicate files.
  - Batch metadata curation console with provenance history diffs.

---

## 5. Engineering Standards & Clean-Room IP Policy
Harmonia enforces the **Harmonia Clean-Room Implementation Policy**:
- **Tier A (Permissive):** MIT / Apache 2.0 / BSD code is eligible for direct reuse after dependency audits, with full notice preservation.
- **Tier B (Weak Copyleft):** MPL 2.0 / LGPL 2.1+ components are isolated behind clean subprocess or dynamic interface boundaries.
- **Tier C (Strong Copyleft):** GPL / AGPL repositories are **strictly reference-only** for studying behavior and equations. Zero code copying. Independent clean-room implementation from scratch.
- **Tier D (Proprietary / Ambiguous):** Feature inspiration only. Any ambiguous license is flagged as `NEEDS LEGAL REVIEW`.
