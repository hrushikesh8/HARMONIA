# Harmonia Research: Architecture Comparison

**Document Classification:** Architectural Research Specification  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Structural, domain, and platform analysis of 20 media ecosystems compared to Harmonia Core and Mobile.

---

## 1. Architectural Archetypes Across the Ecosystem

The 20 analyzed projects represent six distinct architectural paradigms:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ARCHITECTURAL PARADIGMS                         │
├──────────────────────────┬─────────────────────────────────────────────┤
│ 1. Android Native Modern │ Kotlin + Jetpack Compose + Media3 + Room    │
│    (Clean/MVI)           │ Specs: LocalTracklist, Booming, Resonate    │
├──────────────────────────┼─────────────────────────────────────────────┤
│ 2. Android Modern Multi  │ Kotlin Multiplatform (KMP) + Compose (CMP)  │
│    Platform (Clean/DDD)  │ Specs: Phoebe                               │
├──────────────────────────┼─────────────────────────────────────────────┤
│ 3. Native Cross-Platform │ Flutter + Dart + Rust Core + Isar/Riverpod  │
│    Hybrid (Reactive)     │ Specs: Flick, LocalSend, Finamp, Isai       │
├──────────────────────────┼─────────────────────────────────────────────┤
│ 4. Desktop C++/Qt Core   │ C++20 + Qt6 + TagLib + SQLite Pool + DSP    │
│    (Modular Monolith)    │ Specs: fooyin, Strawberry                   │
├──────────────────────────┼─────────────────────────────────────────────┤
│ 5. Python System Audio   │ Python 3 + GTK/Cairo + SDL/Phazor + Mutagen │
│    (Event-driven Loop)   │ Specs: Tauon Music Box, beets, Picard       │
├──────────────────────────┼─────────────────────────────────────────────┤
│ 6. Enterprise Clean Arch │ C# / .NET 9 + Aspire + Minimal APIs         │
│    (Vertical Slices)     │ Specs: MusicHoarder                         │
└──────────────────────────┴─────────────────────────────────────────────┘
```

---

## 2. Deep Project Architectural Analysis

### 2.1 Android Native Tier

#### [LocalTracklist (LibrePlayer)](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/LocalTracklist-master)
- **Structure:** Modular Android application (`data`, `library`, `media`, `ui`, `navigation`).
- **Domain Boundaries:** Clear separation between `LibraryEntities` (Room DB), `DeviceLibraryScanner` (MediaStore/SAF ingest), and `PlaybackService` (Media3 foreground service).
- **State Management:** Android ViewModel + Kotlin StateFlow. Unidirectional data flow (UDF).
- **Storage Abstraction:** Room DAO with SQLite compile-time verification (`LibraryDao.kt`, `SourceDao.kt`, `PlaylistDao.kt`).
- **Platform Abstraction:** Native `androidx.media3.session.MediaSessionService`. Custom `PlaybackAudioDiagnosticRenderersFactory` provides telemetry on audio track configuration, buffer underruns, and decoder latency.
- **Strengths:** 
  - Zero third-party network SDKs; clean boundary between platform storage (`MediaStore` / `SAF`) and application entities.
  - Snapshot persistence (`PlaybackSnapshotStore.kt`) restores queue and seek position across process termination without blocking main thread.
- **Weaknesses:**
  - Tight coupling to Android SDK limits direct cross-platform portability.
- **Harmonia Assessment:** **Prime reference for Harmonia Mobile's Android implementation.**

#### [Auxio](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Auxio-dev)
- **Structure:** Multi-package Android application (`music`, `playback`, `detail`, `list`, `ui`).
- **Domain Boundaries:** Heavy domain model centered around custom music indexing library (`Musikr`). Separation between playback state (`PlaybackStateManager`) and UI listeners.
- **Audio Processing:** Implements low-level Media3 `AudioProcessor` (`ReplayGainAudioProcessor.kt`) directly in the PCM pipeline.
- **Strengths:**
  - Independent tag parsing without relying on Android's buggy MediaStore tag extractors.
  - In-memory music graph enables instantaneous search and navigation across 100,000 tracks.
- **Weaknesses:**
  - High memory footprint due to holding the entire library graph in RAM.
  - Lack of persistent SQLite database for track metadata makes cold start indexing slow on large collections.
- **Harmonia Assessment:** Adopt Auxio's `BetterShuffleOrder` and `ReplayGainAudioProcessor` concepts, but reject its Room-less in-memory model in favor of SQLite.

#### [Booming Music](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/BoomingMusic-master)
- **Structure:** Clean MVVM with repository pattern (`data/local/room`, `data/remote`, `playback`, `ui`).
- **Audio Processing:** Highly sophisticated: contains `AlacWorkaroundCodecSelector` (protecting against broken SoC hardware decoders), `DynamicsProcessingEngine` (Android 9+ native limiter/compressor), and custom `BalanceAudioProcessor`.
- **Strengths:**
  - AutoEQ headphone correction database integrated directly into Room.
  - TTML karaoke-style synchronized lyrics parser (`TtmlLyricsParser.kt`).
- **Weaknesses:**
  - Multiple remote API dependencies (Last.fm, Deezer, Genius) mixed into core repositories.
- **Harmonia Assessment:** Strong architectural template for lyrics parsers and audiophile DSP controls.

---

### 2.2 Kotlin Multiplatform Tier

#### [Phoebe](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Phoebe-main)
- **Structure:** Multi-module Kotlin Multiplatform (KMP) + Compose Multiplatform (CMP):
  - `:core` (utility, logging, common models)
  - `:domain` (business logic, repository interfaces)
  - `:data` (SQLDelight database, network adapters, local file reader)
  - `:feature` (feature-specific UI & ViewModels)
  - `:playback` (multiplatform audio engine abstraction)
  - `:androidApp`, `:iosApp`, `:composeApp` (platform runners)
- **Database:** SQLDelight generates type-safe Kotlin APIs from `.sq` SQL files. Supports Android SQLite, iOS native SQLite, and JVM desktop SQLite seamlessly.
- **Audio Abstraction:** Expect/actual pattern mapping to Media3 on Android, AVPlayer on iOS, and native JVM audio on desktop.
- **Strengths:**
  - 85%+ code sharing across Android, iOS, Desktop, and Web.
  - Strict unidirectional architecture following Jetpack Compose guidelines.
- **Weaknesses:**
  - Multiplatform audio layer requires complex platform bridges for advanced DSP (parametric EQ, bit-perfect DAC access).
- **Harmonia Assessment:** **Proves the viability of KMP/CMP for Harmonia Mobile if iOS support is prioritized.**

---

### 2.3 Native Cross-Platform Hybrid Tier

#### [Flick](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Flick-main)
- **Structure:** Flutter UI with a high-performance native Rust audio engine linked via FFI (`flutter_rust_bridge`).
- **Audio Architecture:**
  - Normal mode: `just_audio` / ExoPlayer (Android shared mixer).
  - High-res DAP mode: Rust engine $\to$ Oboe / AAudio.
  - USB DAC Experimental: Rust engine $\to$ `libusb` isochronous transfers directly to hardware.
- **DSP Chain:** Lock-free realtime audio thread: Sources $\to$ Crossfader/Speed $\to$ Equalizer $\to$ FX (Spatial) $\to$ Convolver (FIR) $\to$ Dynamics (Limiter) $\to$ Volume.
- **Strengths:**
  - Unmatched audiophile engineering: direct UAC2 USB driver, native DSD/DoP streaming, 16,384-tap FIR convolver.
- **Weaknesses:**
  - Flutter-to-Rust FFI serialization creates complexity; Flutter background execution in separate isolates introduces state synchronization overhead.
- **Harmonia Assessment:** The Rust DSP architecture is best-in-class for audiophile playback. Harmonia should emulate its signal chain.

#### [LocalSend](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/localsend-main)
- **Structure:** Riverpod state management + Flutter UI + Rust networking core.
- **Protocol Architecture:**
  - Discovery: UDP multicast / mDNS announcing device type, OS, fingerprint.
  - Security: TLS self-signed certificates generated dynamically per session.
  - Transfer: HTTP/2 REST endpoints (`/api/localsend/v2/prepare-upload`, `/api/localsend/v2/upload`).
  - Hashing: SHA-256 stream digests computed on-the-fly.
- **Strengths:**
  - Completely local-first, zero internet dependency, cross-platform LAN sync.
  - Graceful handling of firewalls, IP changes, and partial transfer resumes.
- **Weaknesses:**
  - Designed for generic file transfer rather than structured music catalog delta synchronization.
- **Harmonia Assessment:** **The definitive architectural model for Harmonia's device-to-device local network synchronization.**

---

### 2.4 Desktop Native C++/Qt Tier

#### [fooyin](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/fooyin-master)
- **Structure:** Modular C++20 / Qt6 desktop monolith with dynamic shared library plugins (`libfooyin_core`, `libfooyin_gui`, `plugins/*`).
- **Database:** SQLite with custom multi-threaded `DbConnectionPool`. XML-driven schema migrations (`dbschema.xml`).
- **Audio Pipeline:**
  - Input plugins: FFmpeg, libsndfile, mpg123, OpenMPT.
  - Pipeline: Input $\to$ Decoder $\to$ Resampler (SoX) $\to$ DSP Chain (ReplayGain, SuperEQ, SoundTouch) $\to$ Output.
  - Output plugins: WASAPI (Windows exclusive/shared), PipeWire, ALSA, PulseAudio, JACK, SDL.
- **Strengths:**
  - Flawless audiophile design; bit-perfect WASAPI exclusive output on Windows; SoX resampler for zero-jitter rate conversion.
  - Decoupled `TrackStats` using `TrackHash`.
  - Extensible widget layout system allowing user-customizable audio engineering dashboards.
- **Weaknesses:**
  - Desktop-only C++ codebase; zero mobile portability.
- **Harmonia Assessment:** **Best-in-class reference for Harmonia Desktop's Audio Engineering Console.**

#### [Strawberry](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/strawberry-master)
- **Structure:** Classical Qt C++ desktop player (evolved from Clementine/Amarok).
- **Backend:** GStreamer audio pipeline with fallback to VLC or Xine.
- **Database:** Direct SQLite with sequential SQL migration scripts (`schema-10.sql` to `schema-13.sql`).
- **Strengths:**
  - Robust handling of external storage (`unavailable=1` soft-delete).
  - Tri-state compilation detection (`compilation_on`, `compilation_off`, `compilation_detected`).
  - Split manual vs automatic artwork columns (`art_manual`, `art_automatic`).
- **Weaknesses:**
  - GStreamer dependency introduces complex system library requirements on Windows/macOS.
- **Harmonia Assessment:** Excellent reference for database schema edge cases and compilation grouping.

---

### 2.5 Metadata & Intelligence Tier

#### [beets](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/beets-master)
- **Structure:** Python package centered around `dbcore` (an ORM-like layer over SQLite) and a rich plugin architecture (`beetsplug`).
- **Domain Boundaries:** Clear separation between physical audio files (`MediaFile`, wrapping Mutagen) and library entities (`Item`, `Album`).
- **Strengths:**
  - Outstanding matching algorithms (`distance.py`): weighted string edit distance with musical heuristics.
  - Plugin architecture allows plugging in Chromaprint, ReplayGain, Lyrics, Discogs, and Spotify without altering core.
- **Weaknesses:**
  - CLI only; no native GUI or real-time streaming services.
- **Harmonia Assessment:** High alignment with Harmonia's Python foundation. Algorithms in `distance.py` should be adapted into Harmonia Core.

#### [MusicHoarder](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/MusicHoarder-main)
- **Structure:** C# / .NET 9 ASP.NET Core Minimal APIs with vertical slice architecture:
  - `Matching`, `Metadata`, `Enrichment`, `Quality`, `Scanner`, `Artwork`, `StreamingFlac`, `Playlists`.
- **Domain Model:** Immutable source library $\to$ Enrichment Pipeline $\to$ Normalized Destination Library.
- **Strengths:**
  - Multi-provider consensus engine (AcoustID + MusicBrainz + Spotify + Apple + Deezer).
  - Triage "Inbox" pattern for human review of ambiguous matches.
  - Canonical album tracklist reconciliation (identifying missing album tracks).
- **Weaknesses:**
  - Dependent on external cloud APIs (Spotify, Apple, Deezer credentials).
- **Harmonia Assessment:** Best reference for the metadata matching consensus workflow and triage inbox.

---

## 3. Comparison with Harmonia's Existing Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│               HARMONIA CORE vs RESEARCHED SPECIMENS                    │
├────────────────────┬──────────────────┬────────────────────────────────┤
│ Architectural Area │ Harmonia Core    │ Researched Ecosystem Norm      │
├────────────────────┼──────────────────┼────────────────────────────────┤
│ Entity Modeling    │ Track vs         │ Most collapse file & track     │
│                    │ TrackFile (1:M)  │ into a single Song row.        │
│                    │ [SUPERIOR]       │ Only fooyin/beets separate.    │
├────────────────────┼──────────────────┼────────────────────────────────┤
│ Signal Analysis    │ Objective ITU-R  │ Most rely on passive ID3 tags. │
│                    │ BS.1770-4 LUFS & │ Only fooyin/Tauon run active   │
│                    │ True Peak meters │ ReplayGain scan on import.     │
│                    │ [SUPERIOR]       │                                │
├────────────────────┼──────────────────┼────────────────────────────────┤
│ Worker Queue       │ Bounded Thread   │ Often runs synchronously or in │
│                    │ Pool with crash  │ unmanaged background threads;  │
│                    │ recovery         │ prone to DB locks during I/O.  │
│                    │ [SUPERIOR]       │                                │
├────────────────────┼──────────────────┼────────────────────────────────┤
│ API & Streaming    │ FastAPI + HTTP   │ Often exposes raw filesystem   │
│                    │ 206 + UUID path  │ or requires heavy media server │
│                    │ concealment      │ protocols (Subsonic/Plex).     │
│                    │ [SUPERIOR]       │                                │
├────────────────────┼──────────────────┼────────────────────────────────┤
│ Device Sync        │ None yet         │ LocalSend provides proven      │
│                    │ (Planned Phase 5)│ LAN protocol (TLS/mDNS).       │
│                    │                  │ [HARMONIA TO ADAPT]            │
├────────────────────┼──────────────────┼────────────────────────────────┤
│ Mobile Playback    │ None yet         │ LocalTracklist/Booming provide │
│                    │ (Mobile v0.1)    │ battle-tested Media3 engines.  │
│                    │                  │ [HARMONIA TO ADAPT]            │
└────────────────────┴──────────────────┴────────────────────────────────┘
```

---

## 4. Architectural Decisions for Harmonia

1. **Retain Clean Architecture in Harmonia Core:** Harmonia's strict separation (`Domain` $\to$ `Application` $\to$ `Infrastructure` $\to$ `Presentation`) is far cleaner than the tangled monoliths found in Strawberry, Tauon, or Isai. Do not compromise it.
2. **Harmonia Core / Mobile Separation of Concerns:**
   - **Harmonia Core (Desktop/Server):** Authoritative music archive, multi-provider consensus, ITU-R BS.1770-4 deep signal analysis, lossless transcode, and sync host.
   - **Harmonia Mobile:** Sovereign local player, Media3 ExoPlayer audio pipeline, Room database, SAF/MediaStore local scanner, offline queue, and sync client.
3. **Adopt LocalSend's Transport Protocol for Sync:** Implement zero-cloud LAN synchronization over TLS with mDNS discovery, directly reusing LocalSend's handshake and chunked transfer patterns.
