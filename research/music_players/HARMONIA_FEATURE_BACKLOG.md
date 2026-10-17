# Harmonia Master Feature Backlog

**Document Classification:** Product Roadmap & Architectural Backlog  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Exhaustive prioritization and domain categorization of all evaluated music ecosystem capabilities.

---

## 1. Classification Categories

Every feature identified across the 20 investigated specimens is assigned to an authoritative backlog bucket:

- **`[NOW]`**: Immediate Phase 4 milestones (Playback engine, transport controls, queue management, listening event telemetry, filesystem watcher).
- **`[NEXT]`**: Phase 5 milestones (Harmonia Mobile native player, LocalSync P2P protocol, multi-provider consensus triage inbox).
- **`[LATER]`**: Phase 6+ milestones (Advanced audiophile DSP, AutoEQ database, FIR impulse response convolver, bit-perfect USB UAC2 driver).
- **`[AI]`**: Harmonia AI independent layer (Subjective taste models, context engine, intelligent dynamic playlists, Jarvis assistant integration).
- **`[MOBILE]`**: Mobile-specific native capabilities (Android Media3, SAF scanner, offline Room SQLite, widgets, Android Auto, dynamic theming).
- **`[DESKTOP]`**: Desktop-specific capabilities (Audio engineering console, libmpv adapter, WASAPI exclusive mode, duplicate triage dashboard).
- **`[CORE]`**: Harmonia Core foundational services (Data models, SQLite WAL, objective audio analysis, REST API, secure streaming).
- **`[DEFERRED]`**: Valid capabilities postponed to future specialized iterations (Neural stem separation, Whisper AI lyric generation, auto-DJ tempo mixing).
- **`[REJECTED]`**: Antipatterns and architectures permanently excluded from Harmonia.

---

## 2. Master Backlog by Category

### 2.1 Category: `[NOW]` — Phase 4 Core Playback & Events
- **F-NOW-01: Playback Engine Port & Transport Controls:** Abstract playback port with play, pause, seek, stop, next, previous, and volume commands.
- **F-NOW-02: Deterministic Queue Management:** Dual-list queue model (Explicit Up Next queue + Background Shuffled Context queue).
- **F-NOW-03: Contiguous "Play Next" Shuffle Insertion:** Adapt Auxio's `BetterShuffleOrder` logic; inserting "Play Next" places tracks immediately after current song rather than scrambling randomly.
- **F-NOW-04: ListeningEvent Factual Telemetry Recording:** Record every play event (`track_id`, `timestamp_utc`, `duration_listened_sec`, `completed_ratio`, `skipped`).
- **F-NOW-05: User Ratings & Favorites:** Star ratings ($1-5$) and Boolean favorite status on `Track` and `Album` entities with instant SQLite indexing.
- **F-NOW-06: Filesystem Watcher (Auto-Import):** Background watcher monitoring registered library roots for created, modified, or deleted files, triggering incremental scans.
- **F-NOW-07: Persistent Queue Restoration:** Snapshot active queue, shuffle state, and seek position to SQLite on playback pause/shutdown; restore seamlessly on restart.

---

### 2.2 Category: `[NEXT]` — Phase 5 Mobile & LocalSync
- **F-NEXT-01: Harmonia Mobile Native Player (Android):** High-performance offline music player built with Kotlin, Jetpack Compose, Media3 ExoPlayer, and Room DB.
- **F-NEXT-02: Harmonia LocalSync Protocol (Core $\leftrightarrow$ Mobile):** Adapt LocalSend's discovery and transfer architecture: mDNS beacon, dynamic TLS certificates, Ed25519 pairing, and chunked HTTP/2 streaming.
- **F-NEXT-03: Multi-Provider Consensus Triage Inbox:** Adapt MusicHoarder's consensus engine: cross-check AcoustID + MusicBrainz; auto-apply matches with $\ge 2$ provider agreement; place ambiguities in a review Inbox.
- **F-NEXT-04: Offline Download State Machine:** Adapt Finamp's `DownloadItemState` model (`missing`, `queued`, `downloading`, `complete`, `failed`) with disk-full protection.
- **F-NEXT-05: Synchronized Lyrics Support:** Integrated LRC / TTML lyric parser with line-level auto-scrolling and local SQLite caching; query LRCLIB for missing lyrics.
- **F-NEXT-06: Disconnected Storage Soft-Delete:** Adapt Strawberry's `is_available` flag; mark tracks as unavailable when external storage is detached without truncating playlists.

---

### 2.3 Category: `[LATER]` — Phase 6+ Audiophile Engine
- **F-LATER-01: Headphone AutoEQ Database Integration:** Pre-populate 4,000+ Jaakko Pasanen parametric equalizer profiles into local SQLite; one-tap headphone frequency linearization.
- **F-LATER-02: FIR Impulse Response Convolver:** 16,384-tap direct time-domain FIR convolver for headphone crossfeed (Bauer/Chu crossfeed) and acoustic room correction.
- **F-LATER-03: Bit-Perfect Windows WASAPI Exclusive Mode:** Direct hardware clock programming on desktop via libmpv / WASAPI exclusive output.
- **F-LATER-04: Native C++ USB UAC2 Direct Driver:** High-res mobile audiophile driver bypassing Android AudioTrack for bit-perfect DSD and high-res PCM playback.
- **F-LATER-05: SoX High-Fidelity Resampling Engine:** Zero-jitter sample rate conversion for desktop audio playback.

---

### 2.4 Category: `[AI]` — Harmonia AI Foundation
- **F-AI-01: User Taste Profile & Affinity Models:** Compute genre, tempo, era, and artist affinity distributions from factual `ListeningEvent` records.
- **F-AI-02: Multi-Dimensional Context Engine:** Predict music needs based on situational factors: time of day, day of week, activity (focus, workout, relaxation, sleep).
- **F-AI-03: Natural Language Understanding (NLU):** Conversational search and retrieval (e.g. *"Play upbeat 80s synth-pop from Japan"* or *"Give me something melancholic like Radiohead"*).
- **F-AI-04: Intelligent Dynamic Playlists:** Generate self-updating playlists based on semantic embeddings and behavioral listening patterns.
- **F-AI-05: AI Music Discovery Recommendations:** Suggest tracks already in the user's library that have been overlooked or forgotten (re-discovery engine).
- **F-AI-06: Jarvis Assistant Integration Protocol:** Voice query endpoint connecting home voice assistants to Harmonia AI.

---

### 2.5 Category: `[MOBILE]` — Mobile Native Innovations
- **F-MOB-01: Media3 Foreground Audio Service:** Robust background playback service with `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and notification controls.
- **F-MOB-02: MediaStore & SAF Delta Scanner:** High-speed incremental Android library scanner reading MediaStore cursors with SAF document tree permissions.
- **F-MOB-03: FastScroll Alphabet Rail:** Alphabet slider on library lists with haptic feedback ticks for rapid navigation across 10,000+ tracks.
- **F-MOB-04: Material You Dynamic Theming with Chroma Clamping:** Monet palette extraction with WCAG 4.5:1 contrast enforcement and true OLED black mode.
- **F-MOB-05: Android Auto Car App Integration:** Expose Media3 `MediaLibraryService` for seamless in-car touch and voice navigation.
- **F-MOB-06: Home Screen Widget:** Compact 4x1 and 4x2 playback control widgets with live artwork and transport buttons.
- **F-MOB-07: Audio Details Telemetry Sheet:** Live modal displaying codec, bit depth, sample rate, bitrate, and active audio sink configuration.

---

### 2.6 Category: `[DESKTOP]` — Audio Engineering Console
- **F-DSK-01: Real-Time Audio Metering Panel:** Live ITU-R BS.1770-4 integrated LUFS, momentary LUFS, short-term LUFS, and True Peak dBTP meters.
- **F-DSK-02: Waveform Seek Bar:** Content-addressed precomputed waveform peaks display with silence boundaries marked.
- **F-DSK-03: Metadata Curation & Batch Editor:** Multi-track tag editing with field-level provenance display and rollback history.
- **F-DSK-04: Duplicate Candidate Triage Console:** Visual diff viewer for comparing physical duplicate candidates (spectral crest, bitrate, size) to select primary copies.
- **F-DSK-05: Audio Quality Spectrum Inspector:** Fast Fourier Transform (FFT) spectrogram view detecting lossy upconverts (e.g. MP3 transcoded to FLAC showing 16kHz cut-off).

---

### 2.7 Category: `[CORE]` — Foundational Infrastructure
- **F-COR-01: Decoupled Track vs. TrackFile Entities:** Maintain 1:Many relationship between logical recordings and physical container files.
- **F-COR-02: Objective ITU-R BS.1770-4 Signal Analyzer:** Subprocess FFmpeg audio analysis with crash recovery and bounded worker pool.
- **F-COR-03: SQLite WAL Mode Persistence:** High-concurrency SQLite storage with indexed foreign keys and busy timeouts.
- **F-COR-04: Full-Text Search (FTS5):** Sanitized multi-token search across titles, artists, albums, genres, and phonetic sort keys.
- **F-COR-05: Secure HTTP 206 Streaming:** RFC 7233 byte-range streaming with strict host filesystem path concealment.
- **F-COR-06: Content-Addressed Artwork Storage:** SHA-256 deduplicated image store with automatic thumbnail generation.

---

### 2.8 Category: `[DEFERRED]` — Postponed Capabilities
- **D-01: On-Device Neural Stem Separation:** (Aurora ONNX model). High CPU/battery drain; defer until desktop Phase 7.
- **D-02: Whisper AI Audio-to-Lyrics Transcription:** (MusicHoarder). Resource-intensive; defer until Harmonia AI Phase 6.
- **D-03: Automated DJ Beat-Matching & Tempo Pitching:** (Aurora). Complex DSP; defer until Phase 7.
- **D-04: Vinyl Rip De-clicking & Scratch Filters:** Specialized audio restoration filter; defer.

---

### 2.9 Category: `[REJECTED]` — Permanent Anti-Patterns
- **R-01: Mandatory Cloud Accounts or Remote Servers:** Harmonia must operate 100% locally and offline without internet gating.
- **R-02: Telemetry, Remote Crash Reporting, or Tracking:** Strict zero-telemetry policy; complete user privacy.
- **R-03: In-Place Destructive Tag Overwrites by Default:** Source files remain read-only; metadata changes are managed in SQLite unless user explicitly executes tag write-back.
- **R-04: Non-Deterministic LLM-Gated Playback:** Playback, queueing, and shuffle must be instantaneous, reliable, and deterministic. AI suggests; Core executes.
- **R-05: Heavy Distributed Infrastructure:** No Docker, Kubernetes, Celery, Redis, or microservices inside the personal player.
