# Harmonia Mobile Architecture Reconciliation (Phase M0.1)

**Document Classification:** Formal Architecture Reconciliation & Resolution  
**Status:** Final & Frozen  
**Date:** October 2026  
**Author:** Lead Systems Architect for Harmonia  
**Governing Principle:** *"Harmonia owns the music. Harmonia Mobile owns the device. Harmonia AI understands the person."*

---

## 1. Executive Summary & Context

Following the completion of the provisional **M0 Architecture Document** ([`docs/MOBILE_ARCHITECTURE_v0.1.md`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/MOBILE_ARCHITECTURE_v0.1.md)) and mobile integration contract export ([`docs/contracts/openapi.json`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/contracts/openapi.json)), **Phase M0.1** was instituted to perform a comprehensive architectural quality and consistency audit.

This audit:
1. Reconciles the database engine (Android Room vs. SQLDelight).
2. Clarifies the technology stack (Android-First Native vs. premature KMP).
3. Finalizes the on-device metadata parser evaluation.
4. Formalizes the Android Auto integration boundary.
5. Replaces all unverified/absolute performance claims with technically defensible engineering language.
6. Reconciles ADR lineage across Harmonia Core and Mobile without collisions.

---

## 2. On-Device Tag Parser: Comparative Evaluation & Final Decision

### 2.1 Comparative Analysis of Evaluated Options

To support direct, sovereign on-device imports (e.g. music folders opened via the Android Storage Access Framework), mobile requires a lightweight, offline metadata inspector. Four candidates were evaluated:

| Candidate | Supported Formats | Artwork & Unicode | Dependency Size & Stability | Risk / Overhead | Verdict |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A. `androidx.media3:media3-extractor`** | MP3, FLAC, M4A, Ogg, WAV | Supported | Part of Media3; heavily marked `@UnstableApi` | High API churn across Media3 minor versions; intended for audio pipelines, not batch scanning | **Rejected** |
| **B. Media3 Inspector / `MetadataRetriever`** | Broad | Supported | Requires `media3-transformer` / `media3-exoplayer` | Heavy overhead; spins up player/source pipelines per file; marked `@UnstableApi` | **Rejected** |
| **C. Android Platform `MediaMetadataRetriever`** | MP3 (ID3v1/v2), FLAC, M4A/AAC, Ogg/Vorbis, WAV | Supported (`getEmbeddedPicture()`, full UTF-8) | **0 KB added to APK**; Built into Android OS framework since API 10 | Stable public Android SDK API; hardware/stagefright accelerated | **SELECTED** |
| **D. External Parser (TagLib JNI or JAudioTagger)** | Extensive | Supported | Adds 2–5 MB native `.so` binaries or legacy Java classes | Build complexity, NDK toolchains, potential memory leaks | **Rejected** |

### 2.2 Decision: Android Platform `android.media.MediaMetadataRetriever`

**Harmonia Mobile selects the Android platform's built-in `MediaMetadataRetriever`.**

- **Why it won:**
  1. **Zero Dependency Overhead:** Built into the Android runtime, adding zero megabytes to application APK size.
  2. **Public API Stability:** Unlike Media3 extractors, `MediaMetadataRetriever` is a stable, mature Android public API with zero `@UnstableApi` annotations.
  3. **Format Coverage:** Natively extracts Title, Artist, Album, AlbumArtist, Track Number, Disc Number, Year, Genre, Duration, Bitrate, and embedded artwork (`byte[]`) across MP3, FLAC, M4A, and Vorbis containers.
  4. **Architectural Parity:** Directly mirrors Apple's native `AVURLAsset` / `AVMetadataItem` on iOS, maintaining architectural symmetry without requiring cross-platform C++ wrappers.

### 2.3 Strict Authority and Mutability Boundary
- **Strictly Read-Only:** Mobile metadata inspection is **strictly read-only**. Harmonia Mobile is forbidden from modifying, rewriting, or injecting tags into the user's audio files on disk.
- **Provisional Classification:** Metadata extracted on-device is stored with provenance `SOURCE_LOCAL_PROVISIONAL`.
- **Desktop Core Sovereignty:** Harmonia Core remains the sole canonical metadata authority. When a mobile device synchronizes with Harmonia Desktop, **Desktop metadata (provenance-audited, MusicBrainz-aligned, and ITU-R BS.1770 analyzed) unconditionally supersedes provisional mobile tags**.
- **No Complex Processing on Mobile:** Mobile never executes acoustic fingerprinting (`fpcalc`), multi-provider web scraping, or psychoacoustic loudness calculation.

---

## 3. Technology Stack & Framework Reconciliation

### 3.1 Android-First Native Architecture

**Decision: Harmonia Mobile is an Android-First Native application: Kotlin + Jetpack Compose + AndroidX Media3 + Android Room.**

- **Rationale:** Audio players require uncompromising background stability, precise audio focus handling, zero-latency Bluetooth AVRCP response, and seamless lock-screen transport integration. Building a native Android application using Google's canonical media stack guarantees maximum platform quality.
- **Portability Guardrail without Premature KMP:**
  - The application will **not** be structured as a premature Kotlin Multiplatform (KMP) Gradle build at Phase M1. It will be built as a clean, idiomatic Android Gradle project.
  - As a standard Clean Architecture discipline, the **Domain Layer** (entities, value objects, queue state machines, shuffle logic, sync state machines) will be written in **Pure Kotlin** avoiding `android.*` framework dependencies where practical.
  - When iOS development begins in a future phase, these domain contracts can be ported directly to Swift or shared via Room 2.7+ KMP if justified, without having compromised Android platform quality during early development.

---

## 4. Mobile Database Architecture: Android Room (SQLite WAL Mode)

### 4.1 Comparative Evaluation: Room vs. SQLDelight

| Evaluation Dimension | Android Room (Selected) | SQLDelight (Rejected) |
| :--- | :--- | :--- |
| **Android Jetpack Integration** | First-party Google standard with KSP compile-time query verification | Community-driven compiler plugin |
| **Large Library Scrolling** | Native support for **AndroidX Paging 3** (`PagingSource`), managing memory windows during rapid scrolling | Requires manual cursor windowing or custom paging adapters |
| **Tooling & Inspection** | Built-in **Android Studio Database Inspector** (live query execution and table inspection on running devices) | Requires command-line `adb shell sqlite3` inspection |
| **Migrations** | Declarative `@AutoMigration` with compile-time schema JSON verification (`schemas/1.json`) | Raw `.sqm` scripts with less automated IDE assistance |
| **Future Portability** | Google officially added Kotlin Multiplatform support to Room (Room 2.7+) | Multiplatform native from inception |

**Decision: Adopt Android Room with SQLite WAL Mode.**  
Room provides superior developer tooling, native Paging 3 integration for large music libraries, and robust migration validation, while maintaining an official Google path to multiplatform support if needed later.

---

## 5. Android Auto Integration Boundary

### 5.1 Architecture: `MediaLibraryService` & `MediaLibrarySession`

- **Decision:** `HarmoniaPlaybackService` will extend `androidx.media3.session.MediaLibraryService` (the official Media3 subclass of `MediaSessionService`).
- **Integration Boundary:**
  - `MediaLibraryService` exposes standard `MediaLibrarySession.Callback` methods:
    - `onGetLibraryRoot(session, browser, params)`: Returns the top-level browsable root (`MediaItem`).
    - `onGetChildren(session, browser, parentId, page, pageSize, params)`: Streams paginated children (Artists, Albums, Playlists).
    - `onGetItem(session, browser, mediaId)`: Resolves an individual playable `MediaItem`.
- **Clarification on Scope:**
  - Establishing `MediaLibraryService` provides the **architectural integration boundary** for Android Auto and Android Automotive OS (AAOS).
  - This does **not** mean Android Auto functionality requires zero effort. Complete automotive support requires:
    1. Declaring `<meta-data android:name="com.google.android.gms.car.application" ... />` in `AndroidManifest.xml`.
    2. Proper intent filters and service export flags.
    3. Constructing browsable vs. playable `MediaItem` hierarchies.
    4. Rigorous validation against the Android Auto Desktop Head Unit (DHU).
  - **Phasing:** The integration boundary is reserved in M0/M1; full Android Auto qualification and testing is scheduled for **Phase M8**.

---

## 6. iOS Strategy: Native SwiftUI (Deferred to Phase M-iOS)

**Decision: Adopt Native SwiftUI for iOS; Reject Compose Multiplatform on iOS.**

- **Rationale:** Harmonia prioritizes authentic native UX quality over theoretical code reuse. SwiftUI provides authentic Apple Human Interface Guidelines, fluid spring physics, native Dynamic Island / Live Activities, lock-screen widgets, and native VoiceOver accessibility. Compose Multiplatform on iOS (rendering via Skia onto a canvas) is rejected for iOS.

---

## 7. Audit of Performance Language: Technically Defensible Claims

In accordance with Section 2 of the quality review, all unverified/absolute performance claims in the architecture documentation are revised as follows:

| Previous Unverified Claim | Technically Defensible Engineering Statement |
| :--- | :--- |
| *"Guarantees 60fps scrolling"* | **Engineered for smooth list scrolling** through Compose virtualized lazy lists (`LazyColumn`) and AndroidX Paging 3 chunked memory windowing. |
| *"Sub-5ms query times across 100,000 tracks"* | **Optimized for large libraries (targeting 50,000+ tracks)** through composite B-Tree indexing, SQLite WAL mode, and paginated cursor queries. |
| *"Zero overhead"* | **Low runtime overhead** by eliminating cross-language bridges (JNI/JS/Dart) on Android. |
| *"Instant search"* | **Low-latency search** powered by SQLite FTS5 prefix indexing with bounded result limits. |
| *"Zero latency audio switching"* | **Gapless audio transitions** implemented via ExoPlayer pre-buffering pipelines. |

---

## 8. Master Architectural Decision Record (ADR) Registry

To guarantee numbering continuity across Harmonia Core and Harmonia Mobile, the master registry is formally recorded:

```
┌────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              HARMONIA MASTER ADR REGISTRY                                      │
├─────────┬──────────────────────────────────────────────────────────┬───────────┬───────────────┤
│ ADR #   │ Title                                                    │ Scope     │ Target Phase  │
├─────────┼──────────────────────────────────────────────────────────┼───────────┼───────────────┤
│ ADR-001 │ Clean Architecture & Domain Separation                   │ Core      │ Phase 1       │
│ ADR-002 │ Decoupling Logical Track from Physical TrackFile         │ Core      │ Phase 1       │
│ ADR-003 │ SQLite WAL Mode Persistence & Pragma Standards           │ Core      │ Phase 1       │
│ ADR-004 │ Unicode NFC Normalization & Sort Key Generation          │ Core      │ Phase 2       │
│ ADR-005 │ Field-Level Metadata Provenance & Conflict Priority      │ Core      │ Phase 2       │
│ ADR-006 │ SQLite FTS5 Full-Text Search Integration                 │ Core      │ Phase 2       │
│ ADR-007 │ Content-Addressed SHA-256 Artwork Storage                │ Core      │ Phase 2       │
│ ADR-008 │ Multi-Tier Duplicate Candidate Review Pipeline           │ Core      │ Phase 2       │
│ ADR-009 │ MusicBrainz Offline-Safe Rate-Limited Lookup             │ Core      │ Phase 2       │
│ ADR-010 │ Host Path Concealment & UUID Public API Boundary         │ Core      │ Phase 3       │
│ ADR-011 │ HTTP 206 Partial Content Audio Streaming                 │ Core      │ Phase 3       │
│ ADR-012 │ FFmpeg Subprocess Process-Group Execution Isolation      │ Core      │ Phase 3       │
│ ADR-013 │ Objective ITU-R BS.1770-4 Signal Intelligence Storage    │ Core      │ Phase 3       │
│ ADR-014 │ Domain PlaybackPort Protocol & Polymorphic PlaybackSource│ Core      │ Phase 4       │
│ ADR-015 │ Dual-Layer Queue (Up Next FIFO + Context Shuffle)        │ Core      │ Phase 4       │
│ ADR-016 │ Dual-Pointer ListeningEvent Telemetry (`track`+`file`)   │ Core      │ Phase 4       │
│ ADR-017 │ Background Coalescing Filesystem Watcher Architecture    │ Core      │ Phase 4       │
│ ADR-018 │ Multi-Provider Consensus Metadata Matching & Inbox       │ Core      │ Phase 5 (Res) │
│ ADR-019 │ Harmonia LocalSync Wire Protocol (mDNS + TLS P2P)        │ Core/Mob  │ Phase 5 (Res) │
│ ADR-020 │ Android-First Native Architecture (Kotlin + Media3)      │ Mobile    │ Phase M0.1    │
│ ADR-021 │ Mobile Local Persistence — Android Room (SQLite WAL)     │ Mobile    │ Phase M0.1    │
│ ADR-022 │ Foreground MediaLibraryService & Unidirectional Control  │ Mobile    │ Phase M0.1    │
│ ADR-023 │ Sovereign Storage & MediaMetadataRetriever Tag Reading   │ Mobile    │ Phase M0.1    │
│ ADR-024 │ Local-First Opportunistic Synchronization Protocol       │ Mobile    │ Phase M0.1    │
│ ADR-025 │ Two-Tier Identity Model (Metadata vs. File Availability) │ Mobile    │ Phase M0.1    │
└─────────┴──────────────────────────────────────────────────────────┴───────────┴───────────────┘
```

---

## 9. OpenAPI Contract & Jellyfin Compatibility Audit

1. **OpenAPI 3.1 Contract ([`docs/contracts/openapi.json`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/contracts/openapi.json)):**
   - Verified that the mobile contract consumes the existing Harmonia Core API as-is.
   - Strict host filesystem path concealment and UUID streaming are enforced across all endpoints.
   - Future synchronization endpoints (manifest delta queries, batch event pushes) are documented as future protocol requirements for Phase M7; no Core API modifications were made today.
2. **Jellyfin Compatibility:**
   - The polymorphic `PlaybackSource` abstraction (`LocalFileSource`, `StreamUriSource`, `PlatformUriSource`) ensures that a future remote streaming adapter (e.g., Jellyfin) can provide streams via `StreamUriSource` without modifying the core player engine.

---

## 10. Reconciled Architectural Topology

```text
HARMONIA CORE (Workstation Archive)
    │
    │  API / integration contracts (OpenAPI 3.1 & LocalSync)
    ▼
HARMONIA MOBILE
    │
    ├── Android Target (Primary Implementation Target)
    │     ├── Presentation: Jetpack Compose (Material You + Harmonia Brand)
    │     ├── Application: Unidirectional MVI StateFlow Controllers
    │     ├── Playback Subsystem: MediaLibraryService + ExoPlayer
    │     ├── Local Metadata: android.media.MediaMetadataRetriever (Read-Only)
    │     ├── Persistence: Android Room SQLite (WAL Mode + Paging 3)
    │     ├── Storage: Android Scoped Storage / SAF Document Picker
    │     └── Automotive: Android Auto MediaLibrarySession integration boundary
    │
    └── iOS Target (Future Phase M-iOS)
          ├── Presentation: Native SwiftUI (Apple HIG)
          ├── Application: Native Swift State Controllers
          ├── Playback Subsystem: AVQueuePlayer / AVAudioEngine
          ├── Local Metadata: AVURLAsset / AVMetadataItem (Read-Only)
          ├── Persistence: GRDB SQLite or Room KMP
          └── Automotive: Apple CarPlay MediaCenter integration
```

---

## 11. Core vs. Mobile Ownership Summary

| Concern | Sovereign Authority | Invariant Rule |
| :--- | :--- | :--- |
| **Music Library Archive** | Harmonia Core | Master bitstream archive, canonical metadata, ITU-R BS.1770 measurements, provenance. |
| **Mobile Device** | Harmonia Mobile | 100% autonomous offline playback, local SAF imports, local queue, seek state, foreground session. |
| **Personal Intelligence** | Harmonia AI | Optional external client; understands taste/context; zero access to playback engine or DB. |
| **Remote Adapters** | Extension Source | Plugs into `PlaybackSource` without modifying player engine. |
