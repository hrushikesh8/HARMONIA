# Harmonia Ecosystem Authoritative Master Roadmap

**Document Classification:** Authoritative Ecosystem Roadmap
**Status:** ACTIVE & AUTHORITATIVE
**Repository Anchor:** HEAD `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`
**Last Reconciled:** 2026-10-16 (Consolidation Phase C1 Reconciliation)
**Primary References:**
- Master Knowledge Base Index: [`docs/knowledge/README.md`](knowledge/README.md)
- Implementation Map: [`docs/knowledge/IMPLEMENTATION_MAP.md`](knowledge/IMPLEMENTATION_MAP.md)
- Engineering Journal: [`docs/knowledge/JOURNAL.md`](knowledge/JOURNAL.md)
- Desktop Architecture: [`docs/ARCHITECTURE.md`](ARCHITECTURE.md)
- Mobile Architecture Baseline: [`docs/MOBILE_ARCHITECTURE_v0.1.md`](MOBILE_ARCHITECTURE_v0.1.md)
- Mobile Architectural Reconciliation: [`docs/MOBILE_ARCHITECTURE_RECONCILIATION.md`](MOBILE_ARCHITECTURE_RECONCILIATION.md)
- Mobile M5 Specification: [`docs/MOBILE_M5_ARCHITECTURE.md`](MOBILE_M5_ARCHITECTURE.md)

---

## 1. Governance, Invariants & Taxonomy

Harmonia is an offline-first, deterministic, high-fidelity music ecosystem designed around the immutable boundary:
> **"Harmonia owns the music. Harmonia AI understands the person."**

To maintain absolute architectural clarity across independent platforms and codebases, all streams, milestones, and deliverables adhere to the following evidence-based status taxonomy.

### 1.1 Status Definitions

- `COMMITTED`: Code is merged and committed into Git repository history at or prior to the baseline HEAD commit (`ad3d1a07`). Fully reproducible from clean checkout.
- `IMPLEMENTED UNCOMMITTED`: Code is fully authored, integrated, and verified by passing local automated unit/integration suites within the active working tree, but has not yet been staged or committed to Git history.
- `VALIDATION PENDING`: Code is authored and compiles/passes JVM unit tests, but hardware-in-the-loop, physical device, Android Auto head unit, or real audio hardware verification has not yet been executed.
- `PLANNED`: Architecturally specified or scoped work that has not yet begun implementation. **A roadmap entry is NOT authorization to begin implementation.**
- `DEFERRED`: Formally recognized capability or platform whose implementation is intentionally postponed to prevent premature cross-platform abstraction or parallel execution thrash.
- `BLOCKED`: Work that cannot proceed until explicit technical or dependency prerequisites are fulfilled.

### 1.2 Core Architectural Principles

1. **Independent Execution Streams:** Desktop Python, Android Native, future iOS Native, Core Synchronization, and Harmonia AI are **independent evolutionary layers**, not parallel multi-platform targets being coded simultaneously.
2. **Local-First & Read-Only Media Invariant:** Harmonia never alters, mutates, re-encodes, or deletes user music files. Storage access is strictly non-destructive.
3. **Decoupled Identity:** Abstract musical works (`Track`, `Album`, `Artist`) remain strictly decoupled from physical storage files and locators (`TrackFile`, `LocalFile`).
4. **Offline Autonomy:** Network connectivity is an optional luxury. The baseline operating state across all players is zero-network local playback from disk.
5. **UI Work Boundary:** UI design and implementation requires an explicit, separate design and research phase before code authoring is authorized.

---

## 2. Stream 1: Desktop Music Library & Audio Engineering Console

**Codebase:** Python 3.11+, SQLAlchemy 2.0 (WAL SQLite), Mutagen, FFmpeg/FFprobe, Typer CLI, FastAPI REST API, Embedded Web UI.
**Directory:** `src/harmonia/`, `tests/`, `migrations/`
**Guiding Architecture:** [`docs/ARCHITECTURE.md`](ARCHITECTURE.md), [`docs/DATA_MODEL.md`](DATA_MODEL.md)

### Milestone D1: Foundation & Core Ingestion
- **Status:** `COMMITTED` (HEAD `ad3d1a07`)
- **Scope:** Project setup (`pyproject.toml`), domain entities (`Track`, `TrackFile`, `Album`, `Artist`, `Tag`), Alembic schema migration `001_initial_schema`, filesystem scanner with SHA-256 stream hashing, Mutagen metadata parser, quality score calculator, and Rich/Typer CLI.
- **Evidence:** Foundation test suite passing; CLI commands `init-db`, `import`, `info`, `list` operational. (Note: `pytest --collect-only` discovers 173 test items across all Desktop test suites, confirming test discovery across both committed D1–D3 suites and uncommitted D4 working-tree tests, rather than test execution).
- **Non-Goals:** Audio decoding, psychoacoustic signal analysis, network endpoints.
- **Authorization:** Completed.

### Milestone D2: Metadata Normalization & Discovery
- **Status:** `COMMITTED` (HEAD `ad3d1a07`)
- **Scope:** Unicode NFC normalization, field provenance tracking (`MetadataFieldRecord`), conflict resolution engine (User > MusicBrainz > File > Path), MusicBrainz rate-limited API port, SQLite FTS5 full-text search, artwork extraction/deduplication, and duplicate detection (Tier A physical & Tier B candidate review).
- **Evidence:** Tested normalization rules, FTS indexing, and duplicate candidate evaluation.
- **Non-Goals:** Cloud synchronization, external file tagging/mutation.
- **Authorization:** Completed.

### Milestone D3: Audio Intelligence & Local HTTP API
- **Status:** `COMMITTED` (HEAD `ad3d1a07`)
- **Scope:** `AudioAnalysis` domain entity, FFprobe stream property extraction, ITU-R BS.1770-4 signal analysis (integrated LUFS, loudness range, True Peak dBTP, clipping, silence detection), bounded worker thread pool with crash recovery, local-first FastAPI REST API, and HTTP 206 Range streaming with path traversal protection.
- **Evidence:** Comprehensive test suite for analyzer, thread pool recovery, and streaming security.
- **Non-Goals:** Real-time playback engine controls, public internet exposure (bound to loopback `127.0.0.1`).
- **Authorization:** Completed.

### Milestone D-UI: Desktop Web Management Interface
- **Status:** `COMMITTED` (HEAD `ad3d1a07`)
- **Scope:** Embedded desktop web UI served at `/` and `/ui`. Real-time music library browser, format badges, track inspector, live ITU-R BS.1770-4 analysis meters, asynchronous analysis queue trigger, dock player connected to HTTP 206 stream endpoints, and CLI launch commands (`harmonia serve --open`, `harmonia ui`).
- **Evidence:** Production UI assets served directly by FastAPI static files.
- **Non-Goals:** Mobile responsive layout, remote control capabilities.
- **Authorization:** Completed.

### Milestone D4: Playback Engine & Listening Telemetry
- **Status:** `IMPLEMENTED UNCOMMITTED` (Working tree)
- **Scope:** Playback engine port (`PlaybackPort`), mpv integration adapter, deterministic queue management (`QueueItem`, `QueueSnapshot`), transport controls, and immutable `ListeningEvent` telemetry persistence.
- **Evidence:** Authored in working tree (`src/harmonia/application/playback_service.py`, `src/harmonia/domain/entities/playback.py`, `tests/unit/test_playback_service.py`).
- **Outstanding Validation:** Execution on headless systems without libmpv installed; pending repository staging and commit.
- **Authorization:** Work completed in working tree; awaiting packaging/commit. Further feature additions NOT authorized.

### Milestone D5: Desktop Hardening & Library Maintenance
- **Status:** `PLANNED`
- **Scope:** Library JSON/CSV export/import utilities, suspicious transcode detection (lossy upconverts masquerading as lossless via spectral cutoff heuristics), filesystem watcher for automatic incremental import, and index performance tuning.
- **Dependencies:** Milestone D4 completion and commit.
- **Authorization:** NOT authorized for current work.

---

## 3. Stream 2: Harmonia Mobile (Android-Native Offline Player)

**Codebase:** Kotlin 2.0+, Jetpack Compose, AndroidX Media3 (ExoPlayer), Room SQLite (WAL), Storage Access Framework (SAF).
**Directory:** `harmonia-mobile/app/`
**Guiding Architecture:** [`docs/MOBILE_M4_ARCHITECTURE.md`](MOBILE_M4_ARCHITECTURE.md), [`docs/MOBILE_M5_ARCHITECTURE.md`](MOBILE_M5_ARCHITECTURE.md), [`docs/knowledge/phases/`](knowledge/phases/)

### Milestone M1–M3: Mobile Core Player Foundation
- **Status:** `COMMITTED` (HEAD `ad3d1a07`)
- **Scope:** Initial Android project baseline, ExoPlayer playback service, Room schema v1, basic library navigation, queue management, and audio focus handling.
- **Evidence:** Historical Git commit history leading to Milestone 4.
- **Authorization:** Completed.

### Milestone M4: Storage Engine, Ingestion & Lifecycle Resilience (M4-A through M4-F)
- **Status:** `COMMITTED` (HEAD `ad3d1a07`, Tag `restore-point/mobile-m4f-complete`)
- **Scope:** Multi-source storage architecture (Internal, Removable SD, MediaStore, SAF tree), recursive document crawler, metadata extraction with artwork caching, storage volume lifecycle coordinator (`StorageVolumeLifecycleCoordinator`), volume unmount/disconnect protection during playback, and non-auto-resume safety.
- **Evidence:** Documented in [`docs/knowledge/phases/mobile_m4f.md`](knowledge/phases/mobile_m4f.md); recorded historical test gate at M4-F final release reported 192 JVM tests, 97 connected Android tests, and 173 Python tests passed at commit `ad3d1a07` (tag `restore-point/mobile-m4f-complete`).
- **Non-Goals:** Network streaming, file tag writing, cloud backups.
- **Authorization:** Completed.

### Milestone M5: Automotive Integration, Audio Ergonomics & Platform Effects (M5-A through M5-D)
- **Status:** `IMPLEMENTED UNCOMMITTED` / `VALIDATION PENDING`
- **Subphases:**
  - **M5-A:** AndroidX Media3 `MediaLibraryService` migration, Android Auto manifest declarations ([`docs/knowledge/phases/mobile_m5a_automotive.md`](knowledge/phases/mobile_m5a_automotive.md), [`ADR-032`](knowledge/adr/ADR-032-media-library-automotive-browsing.md)).
  - **M5-B:** Hierarchical media browser tree (`MediaItemTreeResolver`), pagination, and automotive search ([`docs/knowledge/phases/mobile_m5b_media_library.md`](knowledge/phases/mobile_m5b_media_library.md), [`ADR-032`](knowledge/adr/ADR-032-media-library-automotive-browsing.md)).
  - **M5-C:** Audio session ID bridging, system equalizer intent broadcasting (`DefaultAudioEffectManager`), SharedPreferences intent persistence, and Settings UI controls ([`docs/knowledge/phases/mobile_m5c1_audio_effect_contracts.md`](knowledge/phases/mobile_m5c1_audio_effect_contracts.md), [`mobile_m5c2_audio_session_integration.md`](knowledge/phases/mobile_m5c2_audio_session_integration.md), [`mobile_m5c3_equalizer_settings.md`](knowledge/phases/mobile_m5c3_equalizer_settings.md), [`ADR-033`](knowledge/adr/ADR-033-equalizer-persisted-user-intent.md), [`ADR-034`](knowledge/adr/ADR-034-audio-effect-lifecycle-and-teardown.md), [`INC-003`](knowledge/incidents/INC-003-rapid-audio-session-race-and-generation-fencing.md), [`INC-004`](knowledge/incidents/INC-004-audio-effect-cancellation-masking-hal-leak.md)).
  - **M5-D:** Coroutine sleep timer with 30-second linear volume fade, baseline volume restoration, monotonic clock suspension, and repeat-one loop prevention ([`docs/knowledge/phases/mobile_m5d_sleep_timer.md`](knowledge/phases/mobile_m5d_sleep_timer.md), [`ADR-035`](knowledge/adr/ADR-035-sleep-timer-monotonic-clock-and-volume-safety.md), [`INC-005`](knowledge/incidents/INC-005-sleep-timer-repeat-one-infinite-loop.md)).
- **Evidence:** Documented across [`docs/knowledge/phases/mobile_m5*.md`](knowledge/phases/); historical recorded gates progressed through M5-A (200 JVM tests), M5-B (223 JVM tests), M5-C1 (245 JVM tests), M5-C2 (268 JVM tests), M5-C3 (290 JVM tests), and M5-D (305 JVM tests). Clean debug compilation.
- **Outstanding Validation:** Physical Android Auto head unit testing; third-party DSP hardware broadcast verification (e.g. Wavelet, Poweramp EQ); physical device sleep timer lifecycle under deep Android Doze mode.
- **Authorization:** Work completed in working tree; awaiting packaging/commit. Further feature additions NOT authorized.

### Milestone Personalization (Phase 1 through Phase 3)
- **Status:** `IMPLEMENTED UNCOMMITTED` / `VALIDATION PENDING`
- **Scope:**
  - **Phase 1 (Persistence Foundation):** Room schema v5 (`MIGRATION_4_5`), `FavoriteEntity`, `RatingEntity` with `CHECK(rating >= 1 AND rating <= 5)`, row-presence semantics, and `PersonalizationDao` ([`docs/knowledge/phases/mobile_personalization_p1_persistence.md`](knowledge/phases/mobile_personalization_p1_persistence.md), [`ADR-036`](knowledge/adr/ADR-036-personalization-persistence-schema-v5.md)).
  - **Phase 2 (Domain & Repository):** Pure domain models (`TrackPersonalization`, `RatedTrack`), domain port `PersonalizationRepository`, Room-backed `OfflinePersonalizationRepository`, and ServiceLocator wiring ([`docs/knowledge/phases/mobile_personalization_p2_repository.md`](knowledge/phases/mobile_personalization_p2_repository.md)).
  - **Phase 3 (ViewModels & Presentation State):** `PersonalizationViewModel`, `TrackPersonalizationUiState`, follow-mode vs explicit track binding, `flatMapLatest` lifecycle protection, and `Mutex` concurrency serialization ([`docs/knowledge/phases/mobile_personalization_p3_viewmodel.md`](knowledge/phases/mobile_personalization_p3_viewmodel.md), [`ADR-037`](knowledge/adr/ADR-037-personalization-presentation-architecture.md), [`INC-006`](knowledge/incidents/INC-006-personalization-manual-bind-playback-reversion.md)).
- **Evidence:** Documented across [`docs/knowledge/phases/mobile_personalization_p*.md`](knowledge/phases/); historical recorded gates progressed through Phase 1 (305 JVM tests), Phase 2 (328 JVM tests), and Phase 3 (344 JVM tests across 38 XML suites in the active working tree). Room migration v4->v5 verified in compiled androidTest APK (`assembleDebugAndroidTest` clean; physical device execution pending).
- **Outstanding Validation:** Execution of Room migration tests on physical Android device or emulator; instrumentation test execution verification.
- **Authorization:** Core persistence, domain, and viewmodel phases completed in working tree; awaiting packaging/commit.

### Milestone Personalization (Phase 4: UI & Gestures)
- **Status:** `IMPLEMENTED UNCOMMITTED` / `VALIDATION PENDING`
- **Scope:** Jetpack Compose interactive favorite icon buttons (`FavoriteButton` with `Role.Checkbox` semantics, `touchTarget48()`), 1–5 star rating dialog (`RatingDialog`, `RatingBar`), Library Favorites tab with multi-criteria sorting (Recently Favorited, Title A–Z, Artist A–Z), empty favorites state (`EmptyFavoritesState`), and reactive cross-surface synchronization between Now Playing sheet, MiniPlayer, and Library.
- **Evidence:** Authored in working tree (`PersonalizationPrimitives.kt`, `NowPlayingSheet.kt`, `MiniPlayer.kt`, `LibraryScreen.kt`). Verified by 363 JVM unit tests (0 failures, 0 errors, 0 skipped across 39 XML suites). Verified by **10 connected Android UI tests** passing 100% on `medium_phone(AVD) - 16` (`NowPlayingAndMiniPlayerPersonalizationUiTest` [5 tests], `PersonalizationPrimitivesUiTest` [5 tests]). Verified by genuine high-resolution emulator screenshots.
- **Outstanding Validation & Known Limitations:**
  - Physical TalkBack screen-reader gesture exploration, announcements, and tactile traversal on physical hardware (Compose semantic tree and 48dp touch targets verified in code/tests).
  - Playback queue behavior: Clicking a favorite track in Library loads a point-in-time snapshot into the active queue via `playbackViewModel.playTrackList()`. If a user unfavorites a track while playing from this queue, it remains in the active in-memory queue until the next queue load (formalized as point-in-time snapshot semantics in [ADR-038](knowledge/adr/ADR-038-favorites-playback-queue.md)).
  - Awaiting repository staging and commit.
- **Authorization:** Implementation completed in working tree; awaiting packaging/commit. Further feature additions NOT authorized.

### Milestone UI Stage 5: "Obsidian Acoustic Instrument" System (Stages 5A through 5D)
- **Status:** `IMPLEMENTED UNCOMMITTED` / `VALIDATION PENDING`
- **Subphases:**
  - **Stage 5A–5C (UI Research, Architecture & Design Review):** Comprehensive reference research (Acoustic Black, Dieter Rams / Braun, Teenage Engineering OP-1, Sony Walkman WM-1ZM2), design principles, design system tokens (`HarmoniaThemeTokens`), information architecture, 11 screen/component specifications, and 14 Stage 5C review artifacts ([`docs/knowledge/ui-research/`](knowledge/ui-research/), [`docs/knowledge/ui-research/stage-5c/`](knowledge/ui-research/stage-5c/)).
  - **Stage 5D.1 (Design Tokens):** Semantic color palette (`ObsidianSpace`, `AcousticCharcoal`, `StudioSlate`, `MutedSlate`, `PureSignalWhite`, `AcousticTeal`, `AnalogAmber`), `HarmoniaSpacing`, `touchTarget48()`, dark-first theme. Verified mathematically in `HarmoniaThemeFoundationTest.kt` (WCAG AAA/AA $\ge 4.5:1$ contrast compliance).
  - **Stage 5D.2 (Playback Sheet & MiniPlayer):** Expanded Now Playing sheet, dock mini-player, queue sheet, sleep timer dialog.
  - **Stage 5D.3 (Home & Library Screens):** Home screen, Library tabs (Tracks, Favorites, Albums, Artists), sorting menu sheet.
  - **Stage 5D.4 (Settings & Storage Management):** Settings screen, platform equalizer band sliders, multi-source storage manager.
  - **Stage 5D.5 (Accessibility Verification & Visual Acceptance):** Touch targets ($\ge 48\text{dp}$), font scaling resilience (1.0x, 1.30x, 2.0x), Compose accessibility semantics. Verified by 16 bit-perfect emulator screenshots captured across all primary surfaces.
- **Evidence:** Authored in working tree (`Theme.kt`, `Color.kt`, `Spacing.kt`, `Modifiers.kt`, `NowPlayingSheet.kt`, `MiniPlayer.kt`, `HomeScreen.kt`, `LibraryScreen.kt`, `SettingsScreen.kt`). 363 JVM unit tests passing. 16 high-resolution screenshots archived.
- **Outstanding Validation:** Physical TalkBack screen-reader exploration and tactile navigation on physical hardware.
- **Authorization:** Completed in working tree; awaiting packaging/commit.

### Milestone M6: Local Synchronization & Catalog Portability
- **Status:** `PLANNED`
- **Scope:** Local Wi-Fi / USB synchronization with Harmonia Desktop, incremental playlist and favorite export/import, sync tombstone management via `mobile_sync_tombstones`, and conflict resolution.
- **Dependencies:** Personalization milestone completion; Desktop Milestone D4/D5 completion.
- **Authorization:** NOT authorized for current work.

---

## 4. Stream 3: Future iOS-Native Player

**Platform:** Apple iOS (iOS 17+), Swift 6, SwiftUI, SwiftData / SQLite, AVFoundation.
**Status:** `PLANNED / DEFERRED` (Zero Parallel Work)

### Scope and Strategy
- **Autonomous Native Client:** When prioritized, Harmonia iOS will be implemented as a sovereign, native Swift/SwiftUI application following identical offline-first, read-only principles.
- **Rejection of Premature Multiplatform:** Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP) were formally evaluated and rejected for the mobile baseline ([`docs/MOBILE_ARCHITECTURE_RECONCILIATION.md`](MOBILE_ARCHITECTURE_RECONCILIATION.md)). No multiplatform abstractions shall be introduced prior to complete maturation of the Android product.
- **Interoperability Target:** Common catalog export schemas (JSON/SQLite sync packages) defined by Harmonia Core.
- **Dependencies:** Full completion of Android milestones (M1–M6) and stable Core sync protocol specification.
- **Authorization:** **ZERO CODE AUTHORIZATION.** Completely deferred.

---

## 5. Stream 4: Harmonia Core & Synchronization Infrastructure

**Scope:** Cross-platform protocols, catalog interchange formats, local network sync.
**Status:** `PLANNED / DEFERRED`

### Planned Capabilities
- **LocalSync Protocol:** Peer-to-peer or local-network (mDNS / TLS) catalog synchronization between Harmonia Desktop host and Harmonia Mobile clients.
- **Tombstone Reconciliation:** Two-way synchronization of favorite toggles, ratings, and play history using deterministic timestamp/vector clocks without central cloud dependence.
- **Catalog Export/Import Bundles:** Portable self-contained archive packages for offline backup and device migration.
- **Non-Goals:** Cloud SaaS accounts, third-party centralized streaming relays.
- **Authorization:** NOT authorized for current work.

---

## 6. Stream 5: Harmonia AI & Conversational Companion

**Codebase:** Independent future project, dedicated SQLite database (`harmonia_ai.db`), local Python/FastAPI service.
**Guiding Boundary:** [`docs/ARCHITECTURE.md:§Harmonia vs Harmonia AI Boundary`](ARCHITECTURE.md#harmonia-vs-harmonia-ai-boundary)
**Status:** `PLANNED / SEPARATE COMPANION LAYER`

### Architectural Isolation
- **Pure Client Relationship:** Harmonia AI operates strictly as an external HTTP client consuming Harmonia's public REST API (`GET /api/v1/tracks`, `GET /api/v1/stream/{file_id}`).
- **Zero Database Contamination:** Harmonia AI never writes to or opens Harmonia's internal library database. All intelligence models, embeddings, taste profiles, and conversational memories reside in `harmonia_ai.db`.
- **Zero Playback Coupling:** Harmonia's playback engine is 100% deterministic and operates with zero AI dependency.

### Phase AI-1: AI Foundation & Intelligence Entities
- **Status:** `PLANNED`
- **Scope:** Independent repository setup, `UserProfile` and `TasteProfile` schemas, affinity scoring based on immutable `ListeningEvent` history, content-based recommendation heuristics, and LLM provider abstractions.
- **Authorization:** NOT authorized for current work.

### Phase AI-2: Context Engine & Natural Language
- **Status:** `PLANNED`
- **Scope:** Situational context evaluation (time of day, activity, listener mood), natural language query parsing, smart playlist generator.
- **Authorization:** NOT authorized for current work.

### Phase AI-3: Jarvis Assistant Integration
- **Status:** `PLANNED`
- **Scope:** Jarvis personal assistant protocol adapter, bidirectional voice/intent execution: `Jarvis` -> `Harmonia AI` -> `Harmonia API` -> Playback.
- **Authorization:** NOT authorized for current work.

---

## 7. Current Authorization Summary

| Stream / Milestone | Implementation Status | Evidence / Verification | Authorized for Current Coding? |
| :--- | :--- | :--- | :--- |
| **Desktop D1–D3 & Web UI** | `COMMITTED` | Committed D1–D3 & Web UI test suites passing; production UI (historical M4-F release reported 173 Python tests passed; current working tree `pytest --collect-only` discovers 173 test items across D1–D4) | No (Completed) |
| **Desktop D4 (Playback)** | `IMPLEMENTED UNCOMMITTED` | Unit tests passing in working tree | No (Awaiting staging/commit) |
| **Desktop D5 (Hardening)** | `PLANNED` | Scoped in roadmap | **NO** |
| **Mobile M1–M4-F** | `COMMITTED` | 192 JVM tests, 97 connected Android tests at M4-F gate (commit `ad3d1a07`) | No (Completed) |
| **Mobile M5 (Automotive/AudioFX/Timer)** | `IMPLEMENTED UNCOMMITTED` | 305 JVM tests at M5-D recorded gate (cumulative across M5-A..D); 363 cumulative JVM tests across mobile suites; hardware validation pending | No (Awaiting device test / commit) |
| **Mobile Personalization P1–P3** | `IMPLEMENTED UNCOMMITTED` | 344 JVM tests across 38 suites at Phase 3 working-tree gate; androidTest APK compiled (device execution pending) | No (Awaiting staging/commit) |
| **Mobile Personalization P4 (UI)** | `IMPLEMENTED UNCOMMITTED` / `VALIDATION PENDING` | 363 JVM unit tests; 10 connected Android UI tests passing 100% on AVD; 16 genuine screenshots; physical TalkBack pending | No (Awaiting staging/commit) |
| **Mobile UI Stage 5 (5A–5D)** | `IMPLEMENTED UNCOMMITTED` / `VALIDATION PENDING` | 11 research/spec documents, 14 review files, 16 verified screenshots, 363 JVM unit tests; physical TalkBack pending | No (Awaiting staging/commit) |
| **Mobile M6 (LocalSync)** | `PLANNED` | Architectural concept documented | **NO — Awaiting Consolidation & Commit Checkpointing** |
| **iOS Player** | `PLANNED / DEFERRED` | Zero code | **NO** |
| **Core Sync Protocol** | `PLANNED / DEFERRED` | Zero code | **NO** |
| **Harmonia AI & Jarvis** | `PLANNED / SEPARATE LAYER` | Zero code | **NO** |

---

## 8. Next Authorized Milestone

Following the completion and verification of Mobile Milestone M5, Personalization Phases 1–4, and UI Stages 5A–5D, the next authorized engineering activity is **Consolidation Milestone (Phases C1–C3)**:

1. **Phase C1 (Documentation Reconciliation & Evidence Alignment):** Reconcile authoritative documentation (`docs/ROADMAP.md`, `docs/knowledge/IMPLEMENTATION_MAP.md`, `docs/knowledge/JOURNAL.md`) with the verified repository state. *(Completed)*
2. **Phase C2 (Architectural Decision Record Formulation):** Formulate `ADR-038` to define the formal contract for Library Favorites queue loading, playback queue snapshotting, and dynamic eviction behavior. *(Completed — see [`ADR-038`](knowledge/adr/ADR-038-favorites-playback-queue.md))*
3. **Phase C3 (Milestone Commit Checkpointing):** Create clean, atomic Git commits and annotated restore tags for Milestone M5 (`restore-point/mobile-m5-complete`), Personalization P1–P4 (`restore-point/mobile-personalization-p4-complete`), and Stage 5D UI (`restore-point/mobile-stage-5d-complete`). *(Next Authorized Step)*

**Under no circumstances shall Milestone M6 (LocalSync) or Desktop D5 begin before Consolidation Phases C1–C3 are fully executed and all working-tree changes are committed to Git history.**
