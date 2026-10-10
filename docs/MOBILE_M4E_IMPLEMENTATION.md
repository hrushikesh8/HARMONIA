# Harmonia Mobile — Milestone M4-E Implementation Report
**Audio Ergonomics, Full Integration & Forensic Release Gate**

**Document Classification:** Engineering Release Report & Verification Evidence  
**Status:** COMPLETE (Stage 1 Documentation Record — Baseline Verification Captured)  
**Baseline Anchor:**
- M4-D Accepted Commit / Tag: `7048c7e3ecc1834fde80de0f86553cdaf3c10af4` (`restore-point/mobile-m4d-complete`)
- M4-C Accepted Commit / Tag: `def15998b1b2076809471fcf3b355f29930efd02` (`restore-point/mobile-m4c-complete`)
- Current Verified `HEAD`: `7048c7e3ecc1834fde80de0f86553cdaf3c10af4`
- Tagged Baseline Restore Point: `restore-point/mobile-m4e-complete` (points to `7048c7e3ecc1834fde80de0f86553cdaf3c10af4`)

> [!IMPORTANT]
> **Working-Tree and Restore-Point Semantics:**  
> The Git tag `restore-point/mobile-m4e-complete` was authorized and resolved at commit `7048c7e3ecc1834fde80de0f86553cdaf3c10af4` on branch `master`. That commit represents the committed baseline prior to the M4-E implementation. All M4-E source code, unit tests, and integration test suites currently reside **uncommitted in the local working tree**. The tag does not encapsulate these working-tree modifications. Preserving the working tree intact without reset, stash, checkout, or staging is required.

---

## 1. Milestone Purpose and Scope

Milestone **M4-E** represents the terminal implementation and verification phase of Milestone 4 as specified in [`docs/MOBILE_M4_ARCHITECTURE.md:§22`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/MOBILE_M4_ARCHITECTURE.md#L621-L654) and [`docs/MOBILE_M4_RESEARCH.md:§18`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/MOBILE_M4_RESEARCH.md#L309-L332).

### 1.1 In-Scope Responsibilities
1. **End-to-End Media Ingestion & User Onboarding:**  
   Bridges runtime permission handling with initial scanning. On fresh installation, guides the user through runtime audio permission granting (API 33+ `READ_MEDIA_AUDIO` / legacy `READ_EXTERNAL_STORAGE`), transitioning from empty-library states to automatic discovery and playback readiness.
2. **Multi-Source Storage Management UI:**  
   Provides user-facing controls in [`SettingsScreen.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/settings/SettingsScreen.kt) to list active storage sources, view runtime availability, enable/disable individual sources without data destruction, register custom SAF folder trees via `ACTION_OPEN_DOCUMENT_TREE`, and trigger manual library rescans.
3. **Cross-Caller Scan Coordination & Mutual Exclusion:**  
   Prevents race conditions, duplicate reconciliation passes, and SQLite lock contention when multiple UI surfaces (e.g. `LibraryViewModel` auto-scan on resume and `SettingsViewModel` manual rescan) or background tasks trigger reconciliation simultaneously.
4. **Cancellation-Safe SAF Permission Grants:**  
   Safeguards Storage Access Framework URI permissions through [`SafPermissionDelegate`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/storage/permission/SafPermissionDelegate.kt). If a coroutine is cancelled or database insertion fails after acquiring a persistable URI permission grant, the grant is rolled back cleanly without leaking persisted grants or revoking pre-existing grants.
5. **Peripheral Audio Lifecycle Safety:**  
   Verifies single-authority audio focus management and `AUDIO_BECOMING_NOISY` auto-pause via AndroidX Media3 ExoPlayer, enforcing the strict non-auto-resume invariant when headphones or Bluetooth disconnect.
6. **Forensic Multi-Tier Release Gate:**  
   Executes a 4-tier regression suite spanning Mobile JVM unit tests, Android connected instrumented tests on an API 36 emulator, Core Python unit regression tests, and debug APK assembly.

### 1.2 Explicit Non-Goals & Milestone Boundaries
Per [`docs/MOBILE_M4_ARCHITECTURE.md:§24`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/MOBILE_M4_ARCHITECTURE.md#L668-L678), the following areas remain strictly outside M4-E:
- **No Android Auto (`MediaLibraryService`):** Deferred to Milestone M5.
- **No System Equalizer / DSP (`AudioEffect`):** Deferred to Milestone M5.
- **No LocalSync P2P (mDNS / QR / TLS):** Deferred to Milestone M6.
- **No Tag Write-Back / File Renaming:** Permanently rejected; Harmonia maintains a strictly read-only media policy.
- **No Room Database Schema Migration v4 $\rightarrow$ v5:** M4-E operates exclusively within Room schema version 4 (`MIGRATION_3_4`).

---

## 2. Architecture and Data Flow

### 2.1 Multi-Layer Ingestion & Scanning Architecture

```
                  ┌───────────────────────────────┐
                  │       UI Presentation         │
                  │  LibraryScreen / SettingsUI   │
                  └───────┬───────────────┬───────┘
                          │               │
        Scan Trigger /    │               │  SAF Registration /
        OnResume AutoScan │               │  Manual Rescan
                          ▼               ▼
                 LibraryViewModel   SettingsViewModel
                          │               │  (SafPermissionDelegate
                          │               │   Ownership-Safe Rollback)
                          ▼               ▼
           ┌──────────────────────────────────────────────┐
           │        SourceScanCoordinator (Domain)        │
           │  • Per-Source Mutex (ConcurrentHashMap)      │
           │  • Mutual Exclusion for Identical Source IDs │
           │  • True Concurrency for Distinct Source IDs  │
           └──────────────────────┬───────────────────────┘
                                  │
                                  ▼
           ┌──────────────────────────────────────────────┐
           │ DefaultMediaReconciliationEngine (Data/Local)│
           │  • Permission Check Gate                     │
           │  • MediaStoreScanner / SafTreeScanner        │
           │  • Batch Metadata & Artwork Extraction       │
           │  • Non-Destructive Room v4 Reconciliation    │
           └──────────────────────┬───────────────────────┘
                                  │
                                  ▼
           ┌──────────────────────────────────────────────┐
           │             Room SQLite Database             │
           │  • mobile_storage_sources (User Config)      │
           │  • mobile_local_files (source_id indexed)    │
           │  • mobile_tracks, albums, artists            │
           └──────────────────────────────────────────────┘
```

### 2.2 Four-Tier Storage Semantics & Lifecycle Isolation
Harmonia strictly separates user intent from ephemeral platform and permission states:

| Tier | Semantics | Layer / Storage | Mutated By | Behavioral Invariant |
| :--- | :--- | :--- | :--- | :--- |
| **Tier 1: User Config** | `isEnabled: Boolean` | Room `mobile_storage_sources.is_enabled` | Explicit user toggle in Settings UI | Never mutated by physical unmount, OS reboot, or permission revocation. |
| **Tier 2: Platform Status** | `isAvailable: Boolean` | In-memory `SourceRuntimeState` | `StoragePlatformBridge` / OS callbacks | True only when underlying physical medium is mounted; unmount never deletes database records. |
| **Tier 3: SAF Validity** | `hasPermission: Boolean` | In-memory `SourceRuntimeState` | `SafPermissionDelegate` / `ContentResolver` | Reflects traversal grant status; revoked grant flags source unavailable without dropping catalog rows. |
| **Tier 4: File Existence** | `availabilityStatus` | Room `mobile_local_files.availability_status` | `MediaReconciliationEngine` | Evaluated solely during active scans; unmounted storage sources are skipped to prevent false `MISSING` flagging. |

### 2.3 SourceScanCoordinator & Cross-Caller Concurrency
Located at [`SourceScanCoordinator.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/domain/scanner/SourceScanCoordinator.kt):
- **Per-Source Locking:** Maintains a `ConcurrentHashMap<String, Mutex>()`. Calls to `withSourceLock(sourceId)` serialize overlapping scan requests targeting the same source ID.
- **Cross-Source Concurrency:** Distinct storage sources (e.g. `MEDIA_STORE_PRIMARY` and an external SD card SAF tree) acquire separate mutex instances, executing discovery and processing concurrently without blocking one another.
- **Cancellation Safety:** Built on Kotlin coroutines `Mutex.withLock`. If a scanning coroutine is cancelled (e.g. user navigates away or job is cancelled), the lock is released in a `finally` block, ensuring subsequent calls never deadlock.
- **Engine Integration:** [`DefaultMediaReconciliationEngine`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/scanner/DefaultMediaReconciliationEngine.kt) acquires the coordinator lock at line 68 immediately following the permission check gate.

### 2.4 SafPermissionDelegate & Ownership-Safe Rollback
Located at [`SafPermissionDelegate.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/storage/permission/SafPermissionDelegate.kt) and implemented by [`AndroidSafPermissionDelegate.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/permission/AndroidSafPermissionDelegate.kt):
- **Grant Acquisition:** Invokes `ContentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)`.
- **Pre-Existing Ownership Check:** In [`SettingsViewModel.registerSafFolder`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/settings/SettingsViewModel.kt#L170-L214), before taking permissions, the view model inspects `hadPreExistingGrant` and `isAlreadyRegistered`.
- **Cancellation & Failure Rollback:**
  - If a coroutine cancellation (`CancellationException`) or runtime error (`Throwable`) occurs after `takePersistableReadPermission`, the grant is released via `releasePersistableReadPermission` **only if** Harmonia did not already hold the grant prior to this operation.
  - If rollback fails, the rollback exception is added as a suppressed exception (`ce.addSuppressed(rollbackError)`).
  - Cancellation exceptions are rethrown immediately without swallowing or converting them into error messages.

### 2.5 Playback & Peripheral Audio Safety
- **Single Media3 Authority:** `HarmoniaPlaybackService` configures `ExoPlayer` with `setAudioAttributes(..., true)` and `setHandleAudioBecomingNoisy(true)`. Zero competing broadcast receivers or manual `AudioManager` listeners exist.
- **Audio Becoming Noisy:** Headphone or Bluetooth disconnect triggers an immediate transition to `PAUSED_BY_USER` in `HarmoniaPlayerController`. Autoplay upon peripheral reconnection is strictly prevented.
- **Transient Audio Focus:** Transient focus loss (e.g. navigation instructions) transitions the player to `PlaybackStatus.SUPPRESSED` without generating false `ListeningEventType.PAUSED` telemetry. When focus returns, playback automatically resumes **only if** the player was actively playing beforehand.
- **Storage Disconnection:** If external storage is detached during active playback, `HarmoniaPlayerController` pauses playback, latches `wasExplicitlyPaused = true`, preserves the exact track, queue, and millisecond position, and sets `errorMessage = "Storage volume disconnected"`.
- **Explicit Remount Invariant:** Reconnecting the storage source does **not** auto-resume playback. Playback remains paused until the user deliberately initiates `play()`.

---

## 3. Implementation Details

### 3.1 Primary Production Changes

1. **`org.harmonia.player.domain.scanner.SourceScanCoordinator`**  
   New domain component providing per-source mutual exclusion and status querying (`isSourceScanning`).
2. **`org.harmonia.player.domain.model.storage.permission.SafPermissionDelegate` & `AndroidSafPermissionDelegate`**  
   Clean domain port and Android `ContentResolver` adapter for taking, releasing, and inspecting persistable SAF read permissions.
3. **`org.harmonia.player.data.local.scanner.DefaultMediaReconciliationEngine`**  
   Updated constructor accepting `SourceScanCoordinator`. Wrapped discovery and reconciliation execution inside `scanCoordinator.withSourceLock(source.id)`.
4. **`org.harmonia.player.core.di.ServiceLocator`**  
   Updated `initialize(context)` to instantiate and wire `AndroidSafPermissionDelegate` and inject the shared coordinator across the reconciliation engine and view models.
5. **`org.harmonia.player.presentation.library.LibraryViewModel` & `LibraryScreen`**  
   Added reactive handling of `StoragePermissionState` (Granted, Denied, Settings Required), scanning progress indicators, error banners, and truthful empty-library messaging.
6. **`org.harmonia.player.presentation.settings.SettingsViewModel` & `SettingsScreen`**  
   Added storage source management list, SAF directory registration launcher, source enable/disable toggling, cancellation-safe grant rollbacks, and manual library rescan actions.

---

## 4. Verification Evidence

### 4.1 Test Hierarchy Summary (Recorded Final Release Gate)

> [!NOTE]
> The test metrics below reflect the authoritative results recorded during the final M4-E forensic release gate run. In accordance with the read-only inspection protocol, test suites were **not** re-executed during the authoring of this document.

| Verification Target | Scope | Passed | Failed | Skipped | Status (Recorded Run) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Mobile JVM Unit Tests** | 28 Test Classes | **184** | 0 | 0 | **PASS** |
| **Android Connected Tests (API 36 AVD)** | 14 Test Classes | **86** | 0 | 0 | **PASS** |
| **Core Python Regression** | `tests/unit` | **173** | 0 | 0 | **PASS** (8 runtime warnings) |
| **Debug APK Assembly** | `:app:assembleDebug` | 1 | 0 | 0 | **PASS** |
| **Git Hygiene Check** | `git diff --check` | 0 errors | 0 | 0 | **PASS** |

### 4.2 Targeted Component & Determinism Verification (Subset Validations)

> [!IMPORTANT]
> **Distinction Between Full Release Gate and Targeted Runs:**  
> The tests listed below represent targeted component verification runs executed during development iterations (Stage 2.1 cancellation hardening, Stage 3 integration suite creation, and the final permission-determinism audit). These targeted runs validate specific sub-components and **must not be added** to the full release-gate totals reported in §4.1, which already encompass the entire test suite.

Following the cancellation safety corrections and integration suite additions, targeted verification was executed across the affected test classes with 100% pass rates:

1. **`M3EndToEndIntegrationTest.kt` (6/6 Passed in Targeted Run):**
   - `flowA_fresh_install_empty_library_usable_without_crash`: Proves fresh install starts in clean empty state without crash when audio permission is granted.
   - `flowA_fresh_install_without_permission_shows_onboarding_without_crash`: Proves fresh install without permission displays educational onboarding rationale and permission request action on Library tab while leaving Home, Search, and Playlists usable without crash.
   - `flowB_library_to_search_to_playback_flow`: Proves navigating to Search, querying local catalog, initiating playback via track click, and reflecting playing state in MiniPlayer.
   - `flowC_playlist_lifecycle_and_restart_persistence`: Proves local playlist creation, renaming, track addition, item reordering, and Room SQLite persistence across configuration.
   - `flowD_playlist_to_queue_to_now_playing`: Proves active playlist playback into timeline queue, MiniPlayer expansion to Now Playing sheet, queue inspection, and track jumping.
   - `flowE_missing_track_file_deterministic_graceful_handling`: Proves graceful UI rendering and database preservation when encountering unmounted/missing files in playlists.
   *(Note: Peripheral audio focus ducking/recovery and `AUDIO_BECOMING_NOISY` pause invariants are verified in dedicated suites `HarmoniaPlaybackInterruptionTest.kt` [22 scenarios] and `RealAndroidPlaybackTest.kt`.)*

2. **`HarmoniaM4EEndToEndIntegrationTest.kt` (3/3 Passed in Targeted Run):**
   - `onboarding_permission_denial_displays_rationale_and_recovery_on_grant`: Proves runtime permission denial rationale, settings recovery link, and automatic initial scan upon grant.
   - `settings_saf_folder_registration_deduplication_and_toggle_state`: Proves SAF folder selection, persistable URI grant retention, deduplication of identical tree URIs, and source enable/disable toggling.
   - `library_browse_and_initiate_playback_updates_queue_and_miniplayer`: Proves end-to-end user journey from library browsing to playback initiation and MiniPlayer display.

3. **`CrossCallerEngineScanCoordinationTest.kt` (3/3 Passed in Targeted Run):**
   - `engine_serializes_overlapping_scan_requests_for_the_same_source`: Proves serialized execution of overlapping reconciliation requests for the same source ID.
   - `engine_allows_concurrent_scans_of_different_storage_sources`: Proves true independent concurrency for reconciliation requests targeting distinct storage sources.
   - `engine_releases_lock_on_cancellation_permitting_subsequent_scans`: Proves lock release and recovery following unhandled scan failures or coroutine cancellation.

4. **Unit-Level Concurrency & ViewModel Tests:**
   - **`SourceScanCoordinatorTest.kt` (4/4 Passed):** Proves per-source mutex serialization, independent multi-source concurrency, and lock release upon cancellation and failure.
   - **`SettingsViewModelTest.kt` (26/26 Passed):** Verified via build test report XML (`TEST-org.harmonia.player.presentation.settings.SettingsViewModelTest.xml`, 26 tests, 0 failures, 0 skipped). Exercises reactive source observation, toggle persistence, SAF folder registration, duplicate folder selection, cancellation-safe rollback, pre-existing grant preservation, and shared scan coordination.

### 4.3 Python Regression & Runtime Warnings
The Core Python suite passed completely with **173 passing tests** (`pytest tests/unit`). The test runner emitted **8 runtime warnings** in the aggregate. Because detailed breakdown categories for these 8 warnings were not captured in the release-gate log, only the verified total count of 8 warnings is recorded. Zero test failures, errors, or assertion regressions occurred.

### 4.4 Platform Runtime vs Simulation Boundaries
- **Connected Tests on API 36 Emulator:** Tests run within a real Android ART environment on Android 16 (API 36) with real AndroidX Media3 `ExoPlayer` instances, real SQLite databases, and real Compose UI composition.
- **Storage Lifecycle Simulation:** In instrumented tests, volume unmount events are dispatched through `SimulatedStorageVolumeLifecycleCoordinator` rather than physical micro-SD card ejection. While coroutine state transitions, player listeners, and Room invariants are authoritatively exercised, physical hardware bus detachment remains an empirical real-device boundary.

---

## 5. Acceptance Criteria Mapping

Mapping the 5 Acceptance Gates from [`docs/MOBILE_M4_ARCHITECTURE.md:§23`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/MOBILE_M4_ARCHITECTURE.md#L658-L665):

| # | Milestone Gate | Authoritative Requirement | Code Implementation Reference | Verification Evidence | Status |
| :- | :--- | :--- | :--- | :--- | :--- |
| **1** | **M4-A Gate** | Permissions requested cleanly; Room schema migrated to v3; `StorageSource` models verified. | [`AndroidStoragePermissionChecker.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/permission/AndroidStoragePermissionChecker.kt), [`HarmoniaDatabase.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/HarmoniaDatabase.kt) | `RoomMigrationV2ToV3Test`, `StorageSourceTest` | **SATISFIED** |
| **2** | **M4-B Gate** | `AudioMetadataReader` parses MP3/FLAC/M4A/WAV; artwork extracted to `ArtworkProvider` without leaks. | [`AndroidAudioMetadataReader.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/metadata/AndroidAudioMetadataReader.kt), [`ArtworkProvider.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/ArtworkProvider.kt) | `AudioMetadataReaderTest`, `ArtworkProviderTest` | **SATISFIED** |
| **3** | **M4-C Gate** | MediaStore and SAF scanning index audio into Room; incremental scan checkpoints operate via `getGeneration()`. | [`DefaultMediaReconciliationEngine.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/scanner/DefaultMediaReconciliationEngine.kt), [`MediaStoreScanner.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/scanner/MediaStoreScanner.kt) | `DefaultMediaReconciliationEngineTest`, `MediaReconciliationEngineTest` | **SATISFIED** |
| **4** | **M4-D Gate** | Storage unmount marks files `STORAGE_UNMOUNTED` without deletion; full reconciliation flags deleted files `MISSING`. | [`DefaultStorageVolumeLifecycleCoordinator.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/storage/lifecycle/DefaultStorageVolumeLifecycleCoordinator.kt), [`HarmoniaPlayerController.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt) | `StorageVolumeLifecycleCoordinatorTest`, `StorageVolumeLifecycleIntegrationTest` | **SATISFIED** |
| **5** | **M4-E Gate** | Full user flow (Permission $\rightarrow$ Scan $\rightarrow$ Play); headphone unplug auto-pauses; focus loss ducks/pauses; 100% regression pass. | [`HarmoniaPlaybackService.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/service/HarmoniaPlaybackService.kt), [`LibraryScreen.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/library/LibraryScreen.kt), [`HarmoniaM4EEndToEndIntegrationTest.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/androidTest/java/org/harmonia/player/HarmoniaM4EEndToEndIntegrationTest.kt) | Full 4-Tier Regression: 184 JVM, 86 Connected, 173 Python, assembleDebug clean | **SATISFIED** |

---

## 6. Known Limitations and Milestone Handoff

### 6.1 Known Limitations
1. **Disconnected Storage Visual Feedback:**  
   When a storage source is detached during playback, the playback engine pauses truthfully and records `errorMessage = "Storage volume disconnected"`. However, [`NowPlayingSheet.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/NowPlayingSheet.kt) does not render a persistent warning badge or banner, and [`LibraryScreen.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/library/LibraryScreen.kt) does not visually distinguish or badge unmounted tracks.
2. **Uncommitted Working-Tree State:**  
   The M4-E implementation files remain uncommitted in the local working tree. The restore-point tag `restore-point/mobile-m4e-complete` was anchored to commit `7048c7e3` prior to creating an M4-E commit.

### 6.2 Relationship to Milestone M4-F
- **Authoritative Baseline Note:**  
  Milestone 4 as authored in `docs/MOBILE_M4_ARCHITECTURE.md` terminates at **M4-E**. M4-F was **not** part of the original authoritative architecture specification.
- **Proposed M4-F Scope:**  
  M4-F represents a recommended engineering hardening and consolidation phase aimed at resolving the UI feedback limitations noted above (Now Playing storage disconnection banner, Library unmounted track badges, and Settings track counts) and consolidating the uncommitted M4-E working tree into a formal commit on `master`.

---

## 7. Milestone M4-F Completion Addendum (Hardening & Consolidation)

Milestone **M4-F** was executed as an authorized hardening phase addressing the UI feedback limitations identified in §6.1, without altering the underlying data architecture or Room v4 schema.

### 7.1 Stage 1: Documentation Audit & Discrepancy Reconciliation
- Reconciled historical test inventory and verification counts in this document.
- Verified exact test suite sizes (`SettingsViewModelTest.kt` historically 26/26 in M4-E gate; aggregate 8 Python warnings).
- Confirmed Git baseline semantics: tag `restore-point/mobile-m4e-complete` targets pre-M4-E commit `7048c7e3ecc1834fde80de0f86553cdaf3c10af4` on `master`, with all M4-E and M4-F implementation residing in the working tree.

### 7.2 Stage 2: Disconnected Storage Visual Feedback & Badge Hierarchy
- **Now Playing Banner:** Centralized `PlaybackState.STORAGE_DISCONNECTED_MESSAGE` ("Storage volume disconnected") and added a persistent warning banner in [`NowPlayingSheet.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/NowPlayingSheet.kt) when storage disconnection halts playback.
- **Library Source Status Badges:** Updated [`LibraryScreen.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/library/LibraryScreen.kt) and [`LibraryViewModel.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/library/LibraryViewModel.kt) to visually distinguish unavailable tracks without claiming file deletion:
  - **Badge Resolution Hierarchy:** User configuration (`Source Disabled`) strictly precedes physical status (`Storage Unmounted`), followed by generic neutral fallback (`File Unavailable`).
  - **Source Semantics Guard:** Prevented SAF permission revocation or non-removable source states from being falsely classified as physical unmounts.
- **Verified Suite:** Added `HarmoniaDisconnectedStorageUiTest.kt` (10 instrumented UI tests) and expanded `LibraryViewModelTest.kt` (21 unit tests).

### 7.3 Stage 3: Reactive Per-Source Indexed Track Counts
- **Settings Screen Integration:** Added reactive indexed track counts to `StorageSourceRow` in [`SettingsScreen.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/settings/SettingsScreen.kt) (`"${source.typeDescription} • $trackCountText"`).
- **Decoupled Identity Aggregation:** Grouped tracks in [`SettingsViewModel.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/settings/SettingsViewModel.kt) via primary `Track.localFile?.sourceId` following ADR-025 two-tier ownership, excluding tracks without local file associations or null source IDs. Avoided any database migration or schema modification.
- **Verified Suite:** Expanded `SettingsViewModelTest.kt` from 26 to 28 tests (covering reactive track counting, primary-source attribution, and null-source exclusion).
