# Harmonia Chronological Engineering Journal

This journal documents key milestones, architectural breakthroughs, defect investigations, and release gates in chronological order. All entries are anchored by verifiable Git commits, tags, or authenticated session audit records.

---

## Chronological Log

### 2026-10-08 (Thursday)

#### M4-A: Storage Foundation & Room v2 Migration
- **Commit:** `f389fbd9` (`restore-point/mobile-m4a-complete`) — 21:58:55 +0530
- **Scope:** Introduced `StorageSourceEntity` and Room schema v2 (`MIGRATION_1_2`), establishing the database schema for local storage sources (Internal, Removable SD, Primary MediaStore).

#### M4-B: Metadata Extraction & Artwork Caching Engine
- **Commit:** `91447c77` (`restore-point/mobile-m4b-complete`) — 22:21:10 +0530
- **Scope:** Implemented `AndroidAudioMetadataReader` utilizing Android platform extractors and pure Kotlin fallback parsing for title, artist, album, track number, disc number, and embedded artwork hashing.

#### M4-C: Local Media Scanner & Crawler Foundation
- **Commit:** `3b8abb07` / `797fdf23` (`restore-point/mobile-m4c-complete`) — 23:21:45 +0530
- **Scope:** Built dual-scanner infrastructure: `MediaStoreScanner` for platform-indexed audio and `SafTreeScanner` for recursive document tree crawling via Storage Access Framework.

---

### 2026-10-09 (Friday)

#### Storage Scanner Hardening & Watermark Separation
- **Commits:** `0dde72b2` (06:27:12), `def15998` (06:50:14)
- **Incident & Fix:** Separated MediaStore generation watermarks from SAF folder checkpoint timestamps. Prevented incremental MediaStore scans from dropping newly added files when an interleaved SAF scan ran.

#### Storage Identity & SAF Normalization
- **Commits:** `00f85156` (10:47:50) through `54157384` (12:02:31)
- **Incident & Fix:** Preserved trailing slashes on SAF document tree URIs during normalization to protect provider authority. Enforced strict SHA-256 canonical source identity and removable volume UUID matching.

#### M4-D / M4-E: Volume Lifecycle, Disconnection Protection & Multi-Source UI
- **Commit:** `7048c7e3` (`restore-point/mobile-m4d-complete`, `restore-point/mobile-m4e-complete`) — 17:18:26 +0530
- **Scope:** Implemented `StorageVolumeLifecycleCoordinator`. Solved SD card / USB OTG detachment during playback. Enforced the strict non-auto-resume invariant (reconnection marks tracks available but never auto-resumes without deliberate user `play()`). Added multi-source storage management in Settings.

---

### 2026-10-10 (Saturday)

#### M4-F: Final Preflight Audit & Terminal Milestone Release
- **Commit:** `ad3d1a0764ba594a303d6175314b8fd3deab3089` — 14:45:39 +0530
- **Tag:** `restore-point/mobile-m4f-complete` (Annotated tag `789552af` at 14:47:55 +0530)
- **Significance:** HEAD commit of branch `master`. Sealed Milestone 4 with end-to-end integration verification, error discrimination, and UI feedback hardening.
- **Verification:** Final release gate reported 192 JVM tests, 97 connected Android tests, and 173 Python tests passing at M4-F release (distinguished from current working-tree `pytest --collect-only` discovery across D1–D4).

#### M5: Scope Freeze & Architecture Specification
- **Timeframe:** Afternoon / Evening *(Working tree record)*
- **Deliverable:** Created [`docs/MOBILE_M5_ARCHITECTURE.md`](../MOBILE_M5_ARCHITECTURE.md). Formally separated Favorites & Ratings into a dedicated Personalization milestone prior to M6 LocalSync. Froze M5 scope: Automotive `MediaLibraryService` (M5-A, M5-B), Platform Audio Effects & System Equalizer (M5-C), and Coroutine Sleep Timer (M5-D).

#### M5-A: MediaLibraryService Foundation & Automotive Manifest
- **Timeframe:** Evening *(Working tree record)*
- **Scope:** Migrated `HarmoniaPlaybackService` to AndroidX Media3 `MediaLibraryService`. Declared automotive capabilities in `AndroidManifest.xml` and `automotive_app_desc.xml`. Verified Media3 1.9.2 callback signatures.
- **Verification:** 200 JVM tests passing at M5-A recorded gate.

#### M5-B: MediaItemTreeResolver & Automotive Search
- **Timeframe:** Night *(Working tree record)*
- **Remediation & Fixes:**
  - Standardized search query cache keys to `query.trim()` (removing `.lowercase()` to prevent ASCII/Unicode case-folding discrepancies with SQLite `LIKE`).
  - Corrected storage coordinator initialization ordering in `HarmoniaPlaybackService.onCreate()` prior to serving browser root requests.
- **Verification:** 223 JVM tests passing at M5-B recorded gate.

#### M5-C: Platform Audio Effects & Equalizer Integration (C1, C2, C3)
- **Timeframe:** Late Night *(Working tree record)*
- **Subphases:**
  - **M5-C1:** Domain contracts and monotonic instance generation state machine (`DefaultAudioEffectManager`).
  - **M5-C2:** ExoPlayer audio session ID bridge and platform intent broadcasts (`PlaybackServiceEqualizerBridge`). Resolved main-thread ANR risk during effect teardown.
  - **M5-C3:** SharedPreferences intent persistence and Settings screen UI equalizer controls.
- **Verification:** Subphases progressed through 245 JVM tests (M5-C1), 268 JVM tests (M5-C2), to 290 JVM tests (M5-C3) at respective recorded gates.

---

### 2026-10-11 (Sunday)

#### M5-D: Sleep Timer & Playback Ergonomics
- **Timeframe:** Morning *(Working tree record)*
- **Scope & Verification:** Built coroutine sleep timer with 30-second smooth volume attenuation fade-out; 305 total JVM tests passing at M5-D recorded gate.
- **Invariants Verified:** Monotonic clock suspension (`SystemClock.elapsedRealtime()`) while paused; master volume baseline preservation; fade-job cancellation token race protection; end-of-track detection separating repeat transitions from genuine queue end. Audited in [`HarmoniaSleepTimerTest.kt`](../../harmonia-mobile/app/src/test/java/org/harmonia/player/playback/HarmoniaSleepTimerTest.kt).

#### Personalization Discovery & Schema v5 Reconciliation
- **Timeframe:** Mid-Day *(Working tree record)*
- **Architectural Decision:** Reconciled in [`docs/MOBILE_ARCHITECTURE_v0.1.md:§11.2`](../MOBILE_ARCHITECTURE_v0.1.md). Confirmed row presence represents active favorites and ratings; row deletion represents unfavoriting/clearing. Established that synchronization tombstones will be managed in Milestone M6 via a dedicated `mobile_sync_tombstones` table.

#### Personalization Phase 1: Persistence Foundation
- **Timeframe:** Afternoon *(Working tree record)*
- **Scope:** Created `FavoriteEntity`, `RatingEntity`, `PersonalizationDao` with `CHECK(rating >= 1 AND rating <= 5)`, `HarmoniaDatabase` with `MIGRATION_4_5`, and exported Room schema `5.json`. Added real Room migration test `RoomMigrationV4ToV5AndPersonalizationDaoTest.kt` in `androidTest`.
- **Verification:** 305 total JVM tests passing at Phase 1 recorded gate; Room migration test compiled in androidTest APK (`assembleDebugAndroidTest` clean; physical device execution pending).

#### Personalization Phase 2: Domain Layer & Repository Implementation
- **Timeframe:** Afternoon *(Working tree record)*
- **Scope:** Added domain models `TrackPersonalization` and `RatedTrack` (enforcing 1..5 star validation), pure domain interface `PersonalizationRepository`, and Room-backed `OfflinePersonalizationRepository`. Wired into `ServiceLocator`.
- **Verification:** 23 focused unit tests passing (`TrackPersonalizationDomainTest` + `OfflinePersonalizationRepositoryTest`), 328 total JVM tests passing at Phase 2 recorded gate.

#### Personalization Phase 3: ViewModels and Presentation State
- **Timeframe:** Late Afternoon *(Working tree record)*
- **Architecture & Implementation:** Selected dedicated `PersonalizationViewModel` over bloated `PlaybackViewModel`. Implemented `TrackPersonalizationUiState`. Added dual-operation tracking (auto-playback follow via `PlaybackPort` + manual track binding). Utilized `flatMapLatest` to eliminate stale emissions across track changes and `Mutex` to serialize rapid mutations.
- **Verification:** 16 focused ViewModel tests passing (`PersonalizationViewModelTest`), **344 total JVM tests passing across 38 suites at Phase 3 working-tree gate**, `assembleDebug` and `assembleDebugAndroidTest` clean (physical device execution pending).

#### Knowledge Base Phase 0 & Stage 1: Documentation Framework
- **Timeframe:** Evening *(2026-10-11)*
- **Action:** Completed Phase 0 audit and established the initial `docs/knowledge/` framework (`README.md`, `IMPLEMENTATION_MAP.md`, `JOURNAL.md`, and templates).

#### Knowledge Base Stage 2: Historical Milestone Records
- **Timeframe:** 2026-10-11 Late Evening *(Current Documentation Session — Distinct from historical implementation dates)*
- **Action:** Created ten durable, evidence-based phase completion records under [`docs/knowledge/phases/`](phases/):
  - [`mobile_m4f.md`](phases/mobile_m4f.md): Milestone M4-F storage lifecycle, UI feedback, and preflight hardening.
  - [`mobile_m5a_automotive.md`](phases/mobile_m5a_automotive.md): Milestone M5-A `MediaLibraryService` migration and automotive manifest.
  - [`mobile_m5b_media_library.md`](phases/mobile_m5b_media_library.md): Milestone M5-B `MediaItemTreeResolver` hierarchy browsing, pagination, and external search.
  - [`mobile_m5c1_audio_effect_contracts.md`](phases/mobile_m5c1_audio_effect_contracts.md): Stage M5-C1 audio effect contracts, value models, and state machine.
  - [`mobile_m5c2_audio_session_integration.md`](phases/mobile_m5c2_audio_session_integration.md): Stage M5-C2 platform equalizer bridge and audio session integration (reconciling synchronous release under `NonCancellable` vs channelized async session events).
  - [`mobile_m5c3_equalizer_settings.md`](phases/mobile_m5c3_equalizer_settings.md): Stage M5-C3 SharedPreferences user intent and Settings UI integration.
  - [`mobile_m5d_sleep_timer.md`](phases/mobile_m5d_sleep_timer.md): Stage M5-D coroutine sleep timer with 30s linear volume fade and monotonic clock suspension.
  - [`mobile_personalization_p1_persistence.md`](phases/mobile_personalization_p1_persistence.md): Personalization Phase 1 Room schema v5 persistence foundation.
  - [`mobile_personalization_p2_repository.md`](phases/mobile_personalization_p2_repository.md): Personalization Phase 2 domain models, repository contracts, and Room implementation.
  - [`mobile_personalization_p3_viewmodel.md`](phases/mobile_personalization_p3_viewmodel.md): Personalization Phase 3 `PersonalizationViewModel`, UI state, and dual-mode observation.
- **Verification:** Updated `README.md` and `IMPLEMENTATION_MAP.md`, verified 100% of internal links, confirmed `git diff --check` clean, and preserved all working-tree code without mutation.

#### Knowledge Base Stage 3: Architecture Decisions & Incident Records
- **Timeframe:** 2026-10-11 Night *(Current Documentation Session — Distinct from historical implementation dates)*
- **Action:** Created six Architecture Decision Records under [`docs/knowledge/adr/`](adr/) and six Engineering Incident Records under [`docs/knowledge/incidents/`](incidents/):
  - **ADRs Created:**
    - [`ADR-032`](adr/ADR-032-media-library-automotive-browsing.md): Android Auto MediaLibraryService Architecture & Hierarchical Browsing
    - [`ADR-033`](adr/ADR-033-equalizer-persisted-user-intent.md): Decoupling Persisted Equalizer User Intent from Transient Hardware Audio Sessions
    - [`ADR-034`](adr/ADR-034-audio-effect-lifecycle-and-teardown.md): Deterministic Audio Effect Lifecycle, Asynchronous Event Decoupling & Synchronous Teardown
    - [`ADR-035`](adr/ADR-035-sleep-timer-monotonic-clock-and-volume-safety.md): Coroutine Sleep Timer Monotonic Clock Suspension, Baseline Volume Safety & Transition Handling
    - [`ADR-036`](adr/ADR-036-personalization-persistence-schema-v5.md): Room-Backed Track Personalization Schema v5 — Row-Presence Semantics & Deletion Tombstone Segregation
    - [`ADR-037`](adr/ADR-037-personalization-presentation-architecture.md): Decoupled Presentation Architecture for Track Personalization — Follow-Mode vs Explicit Binding
  - **Incidents Created:**
    - [`INC-001`](incidents/INC-001-storage-unmount-during-playback.md): Storage Volume Disconnection During Active Playback
    - [`INC-002`](incidents/INC-002-concurrent-scan-mutex-lock-contention.md): Concurrent Scan Lock Contention & Scanner Deadlock
    - [`INC-003`](incidents/INC-003-rapid-audio-session-race-and-generation-fencing.md): Rapid Audio Session Race & Generation Invalidation
    - [`INC-004`](incidents/INC-004-audio-effect-cancellation-masking-hal-leak.md): AudioEffect Native HAL Leak from Scope Cancellation
    - [`INC-005`](incidents/INC-005-sleep-timer-repeat-one-infinite-loop.md): Sleep Timer Repeat-One Infinite Loop
    - [`INC-006`](incidents/INC-006-personalization-manual-bind-playback-reversion.md): Personalization Manual Binding Reversion
- **Verification:** Updated knowledge-base navigation and implementation map; verified 100% of internal links and anchors; verified `git diff --check` clean.

#### Knowledge Base Stage 4: Roadmap Reconciliation
- **Timeframe:** 2026-10-11 Late Night *(Current Documentation Session — Distinct from historical implementation dates)*
- **Action:** Reconciled Harmonia's existing roadmaps and architecture documentation with actual repository state:
  - **Authoritative Master Roadmap Established:** Completely updated [`docs/ROADMAP.md`](../ROADMAP.md) to serve as the unified, authoritative roadmap across all 5 product streams (Desktop, Android Native, Future iOS, Core Sync, Harmonia AI/Jarvis), using strict status taxonomy (`COMMITTED`, `IMPLEMENTED UNCOMMITTED`, `VALIDATION PENDING`, `PLANNED`, `DEFERRED`, `BLOCKED`).
  - **Architecture Reconciliation Notices:** Added reconciliation preambles to [`docs/MOBILE_M5_ARCHITECTURE.md`](../MOBILE_M5_ARCHITECTURE.md) (noting M5 JVM completion and pending hardware validation) and [`docs/MOBILE_ARCHITECTURE_v0.1.md`](../MOBILE_ARCHITECTURE_v0.1.md) (preserving M0 historical proposal while noting superseded KMP/SQLDelight recommendations).
  - **Subsystem Scope Demarcation:** Clarified subsystem scopes and added cross-platform references in [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md), [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md), and [`docs/DATA_MODEL.md`](../DATA_MODEL.md).
  - **UI Boundary Enforcement:** Formally marked Personalization Phase 4 as `PLANNED` and strictly unauthorized for code authoring prior to a dedicated UI research and specification stage.
- **Verification:** Verified all Markdown relative links and anchors, ran `git diff --check` clean, verified zero changes outside documentation scope, and confirmed working-tree source files and untracked artifacts remain intact.

---

### 2026-10-16 (Friday)

#### Stage 5A–5C: UI Research, Information Architecture & Design Review
- **Timeframe:** Morning *(Working tree record)*
- **Scope & Deliverables:** Authored comprehensive UI research and specification framework across 11 primary documents and 14 review files under [`ui-research/`](ui-research/):
  - Benchmarked acoustic minimalism, tactile hardware, and precision instrument ergonomics (Acoustic Black, Dieter Rams / Braun, Teenage Engineering OP-1, Sony Walkman WM-1ZM2) in [`REFERENCE_RESEARCH.md`](ui-research/REFERENCE_RESEARCH.md) and [`DESIGN_PRINCIPLES.md`](ui-research/DESIGN_PRINCIPLES.md).
  - Defined the "Obsidian Acoustic Instrument" visual design system, typography tokens, elevation, and tactile states in [`DESIGN_SYSTEM_SPEC.md`](ui-research/DESIGN_SYSTEM_SPEC.md).
  - Drafted screen specifications, responsive breakpoints, edge-to-edge layout, and gesture flows in [`SCREEN_SPECIFICATIONS.md`](ui-research/SCREEN_SPECIFICATIONS.md) and [`NAVIGATION_SPEC.md`](ui-research/NAVIGATION_SPEC.md).
  - Generated visual concept mockups (`concept_a_home_library.jpg`, `concept_b_now_playing.jpg`, `concept_c_settings_storage_eq.jpg`) and finalized design approvals in [`stage-5c/FINAL_DESIGN_REVIEW.md`](ui-research/stage-5c/FINAL_DESIGN_REVIEW.md).

#### Personalization Phase 4: UI Primitives & Cross-Surface Presentation
- **Timeframe:** Mid-Day *(Working tree record)*
- **Scope & Implementation:** Implemented interactive Jetpack Compose personalization UI:
  - Created reusable primitives in `PersonalizationPrimitives.kt`: `FavoriteButton` with explicit `Role.Checkbox` semantics, `touchTarget48()` minimum touch bounding box, and animated spring micro-transitions; `RatingBar` and `RatingDialog` supporting 1–5 star ratings and clear-rating actions.
  - Integrated `FavoriteButton` and `RatingDialog` into `NowPlayingSheet.kt` (`NowPlayingControls`, `NowPlayingHeader`) and docked `MiniPlayer.kt`.
  - Added dedicated Favorites tab to `LibraryScreen.kt` with multi-criteria sorting (`FavoriteSortOrder.RECENTLY_FAVORITED`, `TITLE_ASC`, `ARTIST_ASC`) and empty state (`EmptyFavoritesState`).
- **Verification:** Verified 100% pass across **10 connected Android UI tests** executed on `medium_phone(AVD) - 16` (`NowPlayingAndMiniPlayerPersonalizationUiTest` [5 tests], `PersonalizationPrimitivesUiTest` [5 tests]). Total JVM test suite progressed to 363 passing unit tests across 39 XML suites.

#### Stage 5D: "Obsidian Acoustic Instrument" UI Implementation & Acceptance
- **Timeframe:** Afternoon / Evening *(Working tree record)*
- **Scope & Implementation:** Executed full UI overhaul of Harmonia Mobile across 5 subphases:
  - **Stage 5D.1:** Implemented semantic theme tokens (`HarmoniaThemeTokens`, `Color.kt`, `Spacing.kt`, `Modifiers.kt`) with dark-first color palette (`ObsidianSpace`, `AcousticCharcoal`, `StudioSlate`, `MutedSlate`, `PureSignalWhite`, `AcousticTeal`, `AnalogAmber`). Validated WCAG contrast mathematically in `HarmoniaThemeFoundationTest.kt`.
  - **Stage 5D.2:** Implemented tactile playback surfaces in `NowPlayingSheet.kt` and `MiniPlayer.kt`, including expandable sheets, queue drawer, and coroutine sleep timer dialog.
  - **Stage 5D.3:** Rebuilt `HomeScreen.kt` and `LibraryScreen.kt` with tactile tabs (Tracks, Favorites, Albums, Artists) and bottom sorting sheets.
  - **Stage 5D.4:** Rebuilt `SettingsScreen.kt` with multi-source storage manager, `EqualizerBandSlider`, and subsystem status inspection.
  - **Stage 5D.5:** Executed visual acceptance, accessibility audit, and regression testing. Captured and verified 16 genuine high-resolution emulator screenshots across all primary surfaces.
- **Verification:** 363 JVM unit tests passing (0 failures, 0 errors, 0 skipped); `git diff --check` clean.

#### Post-P4 Comprehensive Audit & Milestone Consolidation Initiation (Phase C1)
- **Timeframe:** Late Evening *(Current Session)*
- **Audit Findings:** Comprehensive inspection of the working tree revealed:
  - Branch `master` remains at HEAD `ad3d1a0764ba594a303d6175314b8fd3deab3089`.
  - 57 tracked modified files and 32 untracked items represent uncommitted work across Milestone M5 (Automotive, Equalizer, Sleep Timer), Personalization P1–P4, and Stage 5D UI.
  - All test suites passing cleanly: 363 JVM unit tests (39 XML suites), 10 connected Android UI tests (100% pass), and 173 Python tests discovered.
  - Critical documentation discrepancies identified: `docs/ROADMAP.md` and `docs/knowledge/IMPLEMENTATION_MAP.md` still reported Personalization Phase 4 as `PLANNED` and strictly unauthorized.
  - Invariant ambiguity identified: Playing from the Library Favorites tab loads a point-in-time snapshot of favorited tracks; if a user un-favorites a track during active playback, it remains in the playback queue until the next queue load.
- **Action Taken (Phase C1):** Reconciled [`docs/ROADMAP.md`](../ROADMAP.md), [`IMPLEMENTATION_MAP.md`](IMPLEMENTATION_MAP.md), and this journal to reflect verified reality. Scoped follow-on Phase C2 (`ADR-038` Favorites queue snapshot behavior) and Phase C3 (atomic Git commit checkpoints) prior to Milestone M6.


