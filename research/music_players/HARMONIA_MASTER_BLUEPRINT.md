# Harmonia Master Architecture Blueprint

**Document Classification:** Final Architectural Synthesis & Specification  
**Status:** Approved Architectural Blueprint  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Principle:** *"Harmonia owns the music. Harmonia AI understands the person."*

---

## 1. What Harmonia Must Become

Harmonia is **not** another derivative clone of Spotify, VLC, or foobar2000.

Harmonia is a **sovereign, local-first personal music intelligence ecosystem**. It unites two traditionally disjointed worlds into a cohesive, private computing environment:

1. **The Physical & Audio Domain (Harmonia Core & Players):** A deterministic, audiophile-grade music library, archival metadata engine, and high-fidelity audio reproduction instrument. It operates 100% offline, without telemetry, cloud servers, or subscription gates.
2. **The Cognitive & Taste Domain (Harmonia AI):** A local, private intelligence layer that learns your evolving musical tastes, understands listening context (time of day, mood, activity, environment), and curates personalized listening journeys without ever touching or modifying your raw audio files.

---

## 2. Definitive Ownership & Separation of Concerns

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ECOSYSTEM DOMAIN BOUNDARIES                     │
├────────────────────┬───────────────────────────────────────────────────┤
│ Harmonia Core      │ • Physical audio files (`TrackFile`)              │
│ (Desktop Archive & │ • Logical musical works (`Track`)                 │
│ Engine Server)     │ • Factual container & tag metadata                │
│                    │ • Objective ITU-R BS.1770-4 signal analysis       │
│                    │ • Content-addressed artwork storage               │
│                    │ • SQLite WAL database & FTS5 search index         │
│                    │ • Secure HTTP 206 streaming with path concealment │
│                    │ • LocalSync server & peer-to-peer pairing         │
├────────────────────┼───────────────────────────────────────────────────┤
│ Harmonia Mobile    │ • Autonomous offline playback via Android Media3  │
│ (Sovereign Native  │ • Scoped storage / SAF / MediaStore delta scanner │
│ Player)            │ • Local embedded Room SQLite database             │
│                    │ • Persistent queue & playback snapshot store      │
│                    │ • Audiophile DSP (AutoEQ, ReplayGain, Convolver)  │
│                    │ • Foreground audio service & Android Auto         │
│                    │ • LocalSync client for Wi-Fi delta sync           │
├────────────────────┼───────────────────────────────────────────────────┤
│ Harmonia Desktop   │ • Audio Engineering Console (visualizer, meters)  │
│ (Curator & Console)│ • Metadata curation & conflict review Inbox       │
│                    │ • Multi-provider consensus matching engine        │
│                    │ • Lossless transcode & duplicate manager          │
│                    │ • Optional bit-perfect WASAPI / ALSA playback     │
├────────────────────┼───────────────────────────────────────────────────┤
│ Harmonia AI        │ • Subjective user taste profiles & embeddings     │
│ (Personal          │ • Context engine (time, activity, mood, setting)  │
│ Intelligence)      │ • Natural Language Understanding (conversational) │
│                    │ • Dynamic, self-updating intelligent playlists    │
│                    │ • Re-discovery & library discovery suggestions    │
│                    │ • Home voice assistant integration (Jarvis)       │
└────────────────────┴───────────────────────────────────────────────────┘
```

### Critical Invariant: The AI Barrier
Harmonia AI **never** possesses write access to Harmonia's database or raw audio files. Harmonia AI interacts with Harmonia Core exclusively as an authenticated local client over `http://127.0.0.1:8000/api/v1/`, consuming factual DTOs and requesting audio streams by opaque file UUIDs.

---

## 3. Shared vs. Platform-Specific Blueprint

### 3.1 Shared Architectural Contracts
- **Entity Schemas:** The definitions of `Track`, `TrackFile`, `Album`, `Artist`, `AudioAnalysis`, and `ListeningEvent` use identical UUIDs and semantics across Python (Desktop) and Kotlin (Mobile).
- **Audio Measurement Standard:** ITU-R BS.1770-4 gated integrated loudness (LUFS), True Peak (dBTP), and Loudness Range (LU) serve as the universal volume normalization standard across all platforms.
- **LocalSync Wire Protocol:** LocalSend-style JSON manifest exchange over TLS with mDNS discovery and streaming SHA-256 integrity verification.
- **Content Hashing:** SHA-256 content hashes identify audio streams and artwork files universally.

### 3.2 Platform-Specific Specializations
- **Mobile Specializations:**
  - `androidx.media3` foreground service lifecycle with Android `MediaSessionCompat`.
  - Android Auto Car App templates.
  - Storage Access Framework (SAF) URI persistence (`takePersistableUriPermission`).
  - Hardware power-state management (`WAKE_LOCK`, battery optimization ignore intents).
  - Material You dynamic Monet color extraction with WCAG contrast clamping.
- **Desktop Specializations:**
  - Multi-threaded FFmpeg subprocess execution with process group timeouts.
  - Windows 32-bit/64-bit WASAPI exclusive mode and Linux PipeWire hardware clock switching.
  - Desktop web audio engineering console (HTML5 Web Audio API meters, canvas waveforms).

---

## 4. Phase-by-Phase Roadmap

### 4.1 Phase 4: Core Playback Engine & Factual Telemetry (Immediate Next Phase)
1. **Playback Engine Port & Adapter:** Implement deterministic playback service supporting play, pause, seek, stop, next, previous, and volume.
2. **Dual-List Deterministic Queue:** Implement persistent queue with separate "Up Next" manual queue and "Context Shuffle" queue.
3. **Auxio "BetterShuffleOrder" Logic:** Ensure inserting tracks into a shuffled queue preserves contiguous placement after the active song.
4. **Factual ListeningEvent Telemetry:** Record immutable play events (`track_id`, `timestamp`, `duration_listened`, `completed`, `skipped`).
5. **Persistent Queue Restoration:** Store queue snapshots in SQLite on shutdown and resume on startup.
6. **Filesystem Watcher:** Background thread monitoring registered library roots for automatic import of added/modified files.

---

### 4.2 Phase 5: Harmonia Mobile & LocalSync P2P
1. **Harmonia Mobile Native Android Player:** Kotlin + Jetpack Compose + Media3 ExoPlayer + Room SQLite.
2. **Harmonia LocalSync Protocol:**
   - mDNS local discovery (`_harmonia._tcp`).
   - Self-signed TLS certificate generation.
   - QR code / Ed25519 mutual pairing handshake.
   - Delta catalog synchronization & chunked audio file transfer.
3. **Multi-Provider Consensus Engine & Triage Inbox:**
   - Integrate AcoustID + MusicBrainz consensus matching.
   - Auto-resolve matches with $\ge 2$ provider consensus; route conflicts to user Inbox.
4. **Synchronized Lyrics Infrastructure:**
   - LRC & TTML parser with timestamp auto-scrolling.
   - Local SQLite lyrics cache with fallback to LRCLIB API.
5. **Storage Disconnection Protection:**
   - Soft-delete `is_available = false` flag on unmounted storage volumes (from Strawberry).

---

### 4.3 Phase 6: Harmonia Audiophile Engine & DSP
1. **Headphone AutoEQ Integration:** Pre-indexed database of 4,000+ headphone calibration profiles; one-tap Harman target correction.
2. **FIR Impulse Response Convolver:** 16,384-tap direct time-domain FIR convolver for headphone crossfeed and speaker simulation.
3. **True Peak Lookahead Limiter:** 32-bit float audio safety limiter preventing positive ReplayGain clipping.
4. **Desktop Bit-Perfect Output:** Optional `libmpv` adapter providing WASAPI exclusive output on Windows and ALSA direct hardware output on Linux.

---

### 4.4 Phase 7: Harmonia AI Foundation
1. **Harmonia AI Independent Service & Database (`harmonia_ai.db`).**
2. **Taste Profile & Behavioral Affinity Modeling:** Derive user preferences from historical `ListeningEvent` records.
3. **Multi-Dimensional Context Engine:** Time, day, activity, and mood classification.
4. **Natural Language Understanding (NLU):** Conversational search and music retrieval queries.
5. **Intelligent Dynamic Playlists:** Self-updating smart playlists based on semantic embeddings.
6. **Jarvis Voice Assistant Integration:** Natural language bridge for home automation control.

---

## 5. Architectural Decisions: Reaffirmed vs. Reconsidered

### 5.1 Decisions Reaffirmed & Kept Unchanged
1. **Track vs. TrackFile Separation (ADR-002):** The research verified that collapsing physical files and logical songs into a single entity (as done by most players) is fundamentally flawed. Harmonia's separation is superior and must remain.
2. **Objective ITU-R BS.1770-4 Signal Analysis (ADR-013):** Storing factual integrated LUFS and True Peak measurements on disk is vastly superior to passive tag reading.
3. **Clean Architecture in Harmonia Core:** Strict separation of Domain, Application, Infrastructure, and Presentation prevents the spaghetti architecture seen in legacy desktop players.
4. **Host Path Concealment & UUID Streaming:** Hiding internal filesystem paths behind UUIDs and HTTP 206 streaming is essential for security and platform agnosticism.

### 5.2 Decisions Reconsidered & Improved
1. **Acoustic Fingerprinting Scope:** Original roadmap deferred AcoustID/Chromaprint. Research shows acoustic fingerprinting is vital for identifying messy collections (`track_047.mp3`). Elevate AcoustID to Phase 5.
2. **Listening History Storage Key:** Instead of keying play counts solely on autoincrement IDs, bind listening history to stable `Track UUID` and content hash (`TrackHash`), preserving history across file relocations.
3. **Mobile Tech Stack:** While Flutter was initially considered, research into Media3 audio diagnostics, background services, and AutoEQ proves **Kotlin + Jetpack Compose + Media3** is the superior, rock-solid path for Android native performance.

---

## 6. What Gives Harmonia a Genuine Advantage Over Existing Players?

Existing players fall into two unsatisfactory extremes:
1. **Dumb Offline Players (Auxio, LocalTracklist, foobar2000):** Excellent audio engines, but zero intelligence, no behavioral learning, and no cross-device sync.
2. **Streaming Cloud Services (Spotify, Apple Music):** Intelligent recommendations, but zero user privacy, terrible lossy audio compression, mandatory subscription fees, and no ownership of music files.

### Harmonia's Competitive Moat:
- **Sovereign Ownership:** You own the lossless audio files. No cloud server can delete your favorite album due to licensing disputes.
- **Audiophile Engineering:** Real ITU-R BS.1770-4 LUFS normalization, 32-bit float DSP, AutoEQ headphone correction, FIR crossfeed convolution, and bit-perfect DAC streaming.
- **Deep Metadata Provenance:** Multi-provider consensus matching with field-level provenance and a human review Inbox.
- **Zero-Cloud LocalSync:** Move music and playlists between laptop and phone over encrypted Wi-Fi in seconds without third-party servers.
- **True Personal AI:** An AI that understands your musical soul, runs locally on your machine, respects your privacy, and never turns your listening habits into advertising telemetry.
