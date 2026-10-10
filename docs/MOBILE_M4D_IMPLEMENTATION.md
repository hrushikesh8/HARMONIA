# Harmonia Mobile — Milestone M4-D Implementation Report
**Volume Lifecycle & Disconnected Storage Protection**

**Document Classification:** Engineering Release Report & Verification Evidence  
**Status:** COMPLETE (Stage 1 Documentation Record — Awaiting Stage 2 Final Regression Gate Approval)  
**Baseline Anchor:**
- M4-C Accepted Commit / Tag: `def15998b1b2076809471fcf3b355f29930efd02` (`restore-point/mobile-m4c-complete`)
- M4-B Baseline Tag: `restore-point/mobile-m4b-complete` (`91447c7762da2e2919d716a473c685ba5dc06dfe`)
- M4-A Baseline Tag: `restore-point/mobile-m4a-complete` (`f389fbd909a657f708605b0599fb35c6cd33e524`)
- M3 Baseline Tag: `restore-point/mobile-m3-complete` (`c952e159a2d11671403ac169cff439ae9965c507`)
- Current Verified `HEAD`: `7048c7e3ecc1834fde80de0f86553cdaf3c10af4`

---

## 1. Milestone Purpose and Scope

Milestone **M4-D** hardens Harmonia Mobile against asynchronous external storage detachment, platform lifecycle transitions, and peripheral audio interruptions.

### 1.1 In-Scope Responsibilities
1. **Four-Tier Storage Semantics:** Strictly distinguishes user configuration (`isEnabled`), physical platform mount status (`isAvailable`), SAF directory permission validity (`hasPermission`), and physical file existence (`availabilityStatus`).
2. **Durable Source-to-File Association:** Introduces `source_id: String` to `LocalFileEntity` via Room `MIGRATION_3_4`, establishing an indexed foreign relation between every tracked audio file and its owning `StorageSource`.
3. **Storage Disconnection Protection:** Observes platform volume lifecycle events; pauses active playback promptly upon disappearance of the underlying storage source; preserves the active track, queue, ordering, and exact playback position.
4. **Strict Non-Auto-Resume Invariant:** Prevents volume remount, platform callback reordering, or ExoPlayer state recovery from triggering implicit playback resumption without deliberate user interaction (`play()`).
5. **Truthful Playback & Telemetry Integration:** Distinguishes transient system suppression (e.g. navigation prompts) from explicit user pause; prevents false `ListeningEventType.PAUSED` telemetry churn; handles `AUDIO_BECOMING_NOISY` headphone unplugs via native Media3 capabilities.
6. **Error Discrimination:** Distinguishes storage disconnection (`PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND` where storage is known unavailable) from permanent media bitstream corruption or missing-file errors on healthy storage.

### 1.2 Explicit Non-Goals & Boundary to Milestone M4-E
Per `docs/MOBILE_M4D_RESEARCH.md:§10` and `docs/MOBILE_M4_ARCHITECTURE.md:§22, §24`, the following concerns are strictly out-of-scope for M4-D and deferred:
- **No Settings / Storage Picker UI:** Deferred to Milestone M4-E.
- **No Bluetooth Autoplay:** Strictly forbidden by Harmonia product principles; reconnecting audio devices must never trigger automated playback.
- **No Network / Cloud Syncing:** Offline-first local storage architecture preserved.
- **No Custom Codec Selectors:** Handled natively by Media3 ExoPlayer.
- **No Tag Write-Back / File Renaming:** Harmonia Mobile enforces an immutable read-only policy over media files.

---

## 2. Architecture and Data Flow

### 2.1 Component Flow & Storage Lifecycle Architecture

```
 Android Platform (StorageManager / BroadcastReceiver / AudioBecomingNoisy)
                                    │
                                    ▼
       StoragePlatformBridge (API 30+ Callback & Intent Fallback)
                                    │
                                    ▼
       DefaultStorageVolumeLifecycleCoordinator (Runtime States)
                                    │
              ┌─────────────────────┴─────────────────────┐
              ▼                                           ▼
 DefaultMediaReconciliationEngine             HarmoniaPlayerController
   (Checks Source Availability before         (Observes Source Availability;
    Full Reconcile; Prevents False             Pauses & Preserves Position
    Mass-Flagging to MISSING)                  on Source Disconnection)
              │                                           │
              ▼                                           ▼
       Room Database v4                            Media3 ExoPlayer
    (`mobile_local_files.source_id`             (Single Focus & Noisy Authority;
     Index via MIGRATION_3_4)                    Enforces Zero Auto-Resume)
```

### 2.2 Four-Tier Storage State Model

| Tier | Property | Persistence / Layer | Managed By | Invariant Enforced |
| :--- | :--- | :--- | :--- | :--- |
| **Tier 1: User Config** | `isEnabled: Boolean` | Room `mobile_storage_sources` | User Action in UI | NEVER mutated by hardware ejection or OS broadcasts. |
| **Tier 2: Platform Status**| `isAvailable: Boolean` | In-memory `SourceRuntimeState` | `StoragePlatformBridge` | Reflects physical mount state; unmount never deletes records. |
| **Tier 3: SAF Validity** | `hasPermission: Boolean`| In-memory `SourceRuntimeState` | `ContentResolver` check | Traversal permission status; revoked permission flags source unavailable. |
| **Tier 4: File Existence**| `availabilityStatus` | Room `mobile_local_files` | `MediaReconciliationEngine`| Mutated ONLY during active reconciliation; ejection never flags files `MISSING`. |

### 2.3 Single Playback Authority (Media3 ExoPlayer)
- `HarmoniaPlaybackService` configures `ExoPlayer.Builder` with:
  - `setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)`
  - `setHandleAudioBecomingNoisy(true)`
- No competing broadcast receivers or duplicate `AudioManager` listeners are introduced.
- `HarmoniaPlayerController` observes Media3 reasons:
  - `onPlaybackSuppressionReasonChanged(PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)` updates UI status to `PlaybackStatus.SUPPRESSED` without emitting `ListeningEventType.PAUSED`.
  - `onPlayWhenReadyChanged(false, PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)` truthfully logs `PAUSED`.
  - `onPlayWhenReadyChanged(false, PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)` safely stops playback on headphone disconnect.

### 2.4 Remount Behavior & Explicit-Play Invariant
When a disconnected volume is remounted:
1. `DefaultStorageVolumeLifecycleCoordinator` marks `isAvailable = true` in `runtimeStates`.
2. `HarmoniaPlayerController` observes the state update, but because `wasExplicitlyPaused = true` was latched during disconnection, the player **remains paused**.
3. The error message `"Storage volume disconnected"` is preserved until the user deliberately invokes `controller.play()`.
4. Only user-initiated `play()` clears the error and commands `mediaController.play()`.

### 2.5 Storage Disconnection vs Missing-File Error Discrimination
- If ExoPlayer throws `PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND`:
  - If the active file's owning `source_id` is confirmed unavailable by `storageVolumeLifecycleCoordinator`, the error is classified truthfully as `"Storage volume disconnected"`, `wasExplicitlyPaused` is set to `true`, and context is preserved.
  - If the source is available and mounted, the error is reported as a genuine I/O missing file error (`"Playback error [ERROR_CODE_IO_FILE_NOT_FOUND]: ..."`), alerting the user to file deletion without corrupting volume lifecycle semantics.

---

## 3. Implementation History

The M4-D milestone was developed across three focused, sequential sub-milestones:

```
def15998 (restore-point/mobile-m4c-complete)
   │
   ├─► 00f85156 feat(mobile): implement M4-D1 storage identity and lifecycle foundation
   ├─► 5f019f90 fix(mobile): enforce production volume callbacks and audit legacy source resolution invariants
   ├─► ceff1ecf fix(mobile): enforce SHA-256 SAF canonical source identity and strict UUID volume matching
   ├─► 03ae4d13 fix(mobile): enforce legacy SAF source reuse and authoritative removable volume persistence matching
   ├─► 54157384 fix(mobile): preserve trailing slashes in SAF tree URI normalization to protect provider identity
   │
   ├─► 981fe3e5 fix(mobile): distinguish playback interruptions from user intent (M4-D2)
   │
   └─► 7048c7e3 feat(mobile): handle storage disconnection during playback (M4-D3)
```

### 3.1 M4-D1: Storage Identity & Lifecycle Foundation
- **Commit:** `00f85156` (with hardening commits `5f019f90`, `ceff1ecf`, `03ae4d13`, `54157384`)
- **Key Responsibilities:**
  - Upgraded Room database schema from version 3 to 4 with `MIGRATION_3_4`.
  - Added `source_id: String?` and index `index_mobile_local_files_source_id` to `LocalFileEntity`.
  - Implemented `StorageVolumeLifecycleCoordinator` and `DefaultStorageVolumeLifecycleCoordinator`.
  - Implemented `StoragePlatformBridge` handling Android 11+ `StorageVolumeCallback` and legacy broadcast receivers.
  - Hardened canonical source identity with SHA-256 URI hashing and trailing-slash preservation.
  - Updated `DefaultMediaReconciliationEngine` to populate `source_id = source.id`.
- **Primary Source Files:**
  - [`HarmoniaDatabase.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/HarmoniaDatabase.kt#L136-L140)
  - [`LocalFileEntity.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/entity/LocalFileEntity.kt#L25-L35)
  - [`DefaultStorageVolumeLifecycleCoordinator.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/storage/lifecycle/DefaultStorageVolumeLifecycleCoordinator.kt)
  - [`StoragePlatformBridge.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/storage/lifecycle/StoragePlatformBridge.kt)
  - [`DefaultMediaReconciliationEngine.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/scanner/DefaultMediaReconciliationEngine.kt)
  - [`StorageSource.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/storage/StorageSource.kt)

### 3.2 M4-D2: Playback Interruption Semantics & Telemetry
- **Commit:** `981fe3e5cae17da941587deac55ed7d6ecd93a64`
- **Key Responsibilities:**
  - Preserved single Media3 authority for audio focus and `AUDIO_BECOMING_NOISY`.
  - Added `wasExplicitlyPaused` tracking in `HarmoniaPlayerController` to distinguish user pause from transient suppression.
  - Suppressed spurious `ListeningEventType.PAUSED` and `RESUMED` events during transient audio focus loss.
  - Added comprehensive 11-scenario JVM interruption suite in `HarmoniaPlaybackInterruptionTest`.
- **Primary Source Files:**
  - [`HarmoniaPlayerController.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt)
  - [`PlaybackState.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/PlaybackState.kt)
  - [`PlaybackStatus.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/PlaybackStatus.kt)
  - [`HarmoniaPlaybackInterruptionTest.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/test/java/org/harmonia/player/playback/HarmoniaPlaybackInterruptionTest.kt)

### 3.3 M4-D3: Storage-Disconnection Playback Handling & Lifecycle Integration
- **Commit:** `7048c7e3ecc1834fde80de0f86553cdaf3c10af4`
- **Key Responsibilities:**
  - Wired `storageVolumeLifecycleCoordinator` directly into `HarmoniaPlayerController`.
  - Implemented `observeStorageLifecycle()` and `handleStorageDisconnection()`.
  - Preserved playback position, track identity, queue, and order upon volume disconnection.
  - Discriminated `ERROR_CODE_IO_FILE_NOT_FOUND` against storage availability state.
  - Enforced strict non-auto-resume on volume remount.
  - Added 11 storage-specific unit test scenarios to `HarmoniaPlaybackInterruptionTest` (scenarios 12–22).
  - Implemented instrumented integration test `StorageVolumeLifecycleIntegrationTest` on Android emulator.
- **Primary Source Files:**
  - [`HarmoniaApp.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/HarmoniaApp.kt#L13-L25)
  - [`HarmoniaPlayerController.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt#L119-L171)
  - [`HarmoniaPlaybackInterruptionTest.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/test/java/org/harmonia/player/playback/HarmoniaPlaybackInterruptionTest.kt#L482-L691)
  - [`StorageVolumeLifecycleIntegrationTest.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/androidTest/java/org/harmonia/player/StorageVolumeLifecycleIntegrationTest.kt)

---

## 4. Verification Evidence

### 4.1 Test Hierarchy Summary (Prior Verification Runs)

> [!NOTE]
> Per the read-only execution guidelines for this documentation stage, test suites were **not** re-executed during Stage 1 authoring. The test counts and outcomes below reflect the authoritative, previously verified results recorded immediately prior to commit `7048c7e3`. Formal re-execution across all 4 gates occurs in Stage 2.

| Verification Gate | Test Scope | Passed | Failed | Skipped | Status (Prior Run) |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Mobile JVM Unit Tests** | 23 Test Classes | 139 | 0 | 0 | **PASS** |
| **Android Connected Tests (API 36)** | 12 Test Classes | 80 | 0 | 0 | **PASS** |
| **Core Python Regression** | `tests/unit` | 173 | 0 | 0 | **PASS** |
| **Debug APK Assembly** | `:app:assembleDebug` | 1 | 0 | 0 | **PASS** |

### 4.2 Key M4-D Test Classes & Responsibilities

#### JVM Unit Test Classes (`harmonia-mobile/app/src/test/java/org/harmonia/player/`)
1. **`playback/HarmoniaPlaybackInterruptionTest.kt` (22 Scenarios):**
   - Transient audio focus loss sets `status = SUPPRESSED` without emitting `ListeningEventType.PAUSED`.
   - Permanent focus loss pauses playback and records factual `PAUSED`.
   - Transient suppression return resumes playback without duplicate `STARTED` telemetry.
   - User pause during transient suppression is preserved and prevents auto-resume when focus returns.
   - Headphone disconnection (`AUDIO_BECOMING_NOISY`) pauses playback cleanly.
   - Disconnection of active storage source pauses playback, captures position, and sets `"Storage volume disconnected"`.
   - Track ID, local file ID, queue, and position are preserved on storage disconnect.
   - Storage remount does NOT auto-resume playback.
   - Explicit user `play()` resumes playback and clears the storage disconnected message.
   - Disconnection of unrelated storage source does not interrupt active playback.
   - Repeated or reordered availability callbacks are idempotent.
   - File-not-found error discrimination against storage availability.
   - Controller release cleanly cancels lifecycle observation jobs.
2. **`data/local/storage/lifecycle/StorageVolumeLifecycleCoordinatorTest.kt`:**
   - Volume unmount marks source `isAvailable = false` and `isMounted = false`.
   - Preserves `StorageSourceEntity.isEnabled` across unmount.
   - Repeated unmount/mount events are idempotent.
   - SAF permission revocation updates `hasPermission = false` without record deletion.
3. **`data/StorageSourceAndCheckpointRepositoryTest.kt`:**
   - CRUD and persistence verification for sources, removable volumes, and incremental scan checkpoints.

#### Android Connected Test Classes (`harmonia-mobile/app/src/androidTest/java/org/harmonia/player/`)
1. **`RoomMigrationV3ToV4AndSourceAssociationTest.kt`:**
   - Real SQLite execution of `MIGRATION_3_4`.
   - Validates addition of `source_id` column and index on `mobile_local_files`.
   - Verifies existing Track and LocalFile records from v3 remain byte-identical and uncorrupted.
2. **`StorageVolumeLifecycleIntegrationTest.kt`:**
   - Exercises real Media3 `ExoPlayer` instance on Android API 36 emulator.
   - Plays real MP3 audio file from local directory.
   - Simulates storage source disconnection: asserts playback pauses, position is preserved, and status updates.
   - Simulates volume remount: asserts player remains paused (`playWhenReady == false`).
   - Executes explicit `controller.play()`: asserts playback resumes and error is cleared.
   - Verifies unrelated storage disconnection does not interrupt playback.
3. **`MediaReconciliationEngineTest.kt`:**
   - Validates that unmounted or missing files are handled non-destructively during reconciliation.
4. **`RealAndroidPlaybackTest.kt`:**
   - Real platform playback pipeline verification across supported codecs (MP3, FLAC, WAV, M4A).

### 4.3 Platform Runtime vs Synthetic Classification
- **Synthetic Lifecycle Simulation on Emulator:** In `StorageVolumeLifecycleIntegrationTest`, storage disconnection is driven by `SimulatedStorageVolumeLifecycleCoordinator.setSourceState(sourceId, false)` and in-memory Room database. While this authoritatively verifies the end-to-end coroutine flows, player listener hooks, and position preservation in the Android runtime, **it is not physical SD-card ejection**. Genuine hardware eject involves kernel-level VFS unmount, block device detachment, and `EIO` OS signals.
- **Physical Volume Callback Bridge:** `StoragePlatformBridge` registers `StorageManager.registerStorageVolumeCallback` on API 30+ with legacy broadcast receiver fallback (`ACTION_MEDIA_MOUNTED`, `ACTION_MEDIA_UNMOUNTED`, `ACTION_MEDIA_EJECT`). In production, this bridge receives OS callbacks and delivers volume IDs to `DefaultStorageVolumeLifecycleCoordinator`.

---

## 5. Acceptance Criteria Mapping

Mapping each of the 7 Acceptance Criteria from `docs/MOBILE_M4D_RESEARCH.md:§10`:

| # | M4-D Acceptance Criterion | Code Implementation Reference | Verification Evidence | Status |
| :- | :--- | :--- | :--- | :--- |
| **1** | Four-tier storage model strictly preserves user `isEnabled` and file records across unmount/eject events. | [`DefaultStorageVolumeLifecycleCoordinator.kt:L68-L90`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/storage/lifecycle/DefaultStorageVolumeLifecycleCoordinator.kt#L68-L90) (only mutates in-memory `runtimeStates`) | `StorageVolumeLifecycleCoordinatorTest` | **SATISFIED** |
| **2** | Zero files marked `MISSING` purely due to volume ejection. | [`DefaultStorageVolumeLifecycleCoordinator.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/storage/lifecycle/DefaultStorageVolumeLifecycleCoordinator.kt) (zero invocations of `localFileDao` on eject) | `StorageVolumeLifecycleCoordinatorTest` & `MediaReconciliationEngineTest` | **SATISFIED** |
| **3** | `LocalFileEntity.source_id` explicitly links every local file to its owning source. | [`LocalFileEntity.kt:L34`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/entity/LocalFileEntity.kt#L34), [`HarmoniaDatabase.kt:L136-L140`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/HarmoniaDatabase.kt#L136-L140) (`MIGRATION_3_4`) | `RoomMigrationV3ToV4AndSourceAssociationTest` | **SATISFIED** |
| **4** | Single Media3 authority for audio focus and becoming-noisy preserved with zero competing managers. | [`HarmoniaPlaybackService.kt:L35-L43`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/service/HarmoniaPlaybackService.kt#L35-L43) | `HarmoniaPlaybackInterruptionTest` (scenarios 7–9), `RealAndroidPlaybackTest` | **SATISFIED** |
| **5** | Telemetry logs zero false `PAUSED` events during transient audio focus loss. | [`HarmoniaPlayerController.kt:L200-L228`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt#L200-L228) (`wasExplicitlyPaused` guard) | `HarmoniaPlaybackInterruptionTest` (scenarios 1–4) | **SATISFIED** |
| **6** | Volume removal during active playback pauses cleanly with truthful state and zero auto-resume upon remount. | [`HarmoniaPlayerController.kt:L147-L171`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt#L147-L171) & [`L419-L438`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt#L419-L438) | `HarmoniaPlaybackInterruptionTest` (scenarios 12–15), `StorageVolumeLifecycleIntegrationTest` | **SATISFIED** |
| **7** | All regression test gates pass: JVM unit tests, Android connected tests, Core Python tests, and APK assembly. | All 4 test targets passed in prior run (139 JVM, 80 connected, 173 pytest, debug APK) | Scheduled for Stage 2 final execution gate | **PENDING STAGE 2 RUN** |

---

## 6. Known Limitations and Milestone Handoff

### 6.1 Known Limitations
1. **Physical Ejection vs Emulated Callback:** The Android emulator environment does not model abrupt electrical bus disconnection of micro-SD cards during active DMA audio streaming. Hardware testing on physical devices with removable SD cards remains recommended for long-term regression monitoring.
2. **UI Banners / Snackbars:** Disconnected storage status is fully exposed in `PlaybackState` (`PlaybackStatus.PAUSED`/`ERROR` with `errorMessage = "Storage volume disconnected"`), but custom UI notification banners in `NowPlayingScreen` or `LibraryScreen` were deliberately excluded from M4-D per architectural scope boundaries.
3. **Storage Settings Management:** User configuration for toggling storage sources on or off (`isEnabled`) is backed by the Room database schema and DAO, but dedicated Settings management UI is deferred to M4-E.

### 6.2 Milestone Handoff to M4-E
- **M4-D Status:** Core engineering scope (M4-D1, M4-D2, M4-D3) is complete, functionally integrated, and documented.
- **Stage 2 Requirement:** Execution of the complete 4-tier forensic regression gate and establishment of tag `restore-point/mobile-m4d-complete` at commit `7048c7e3ecc1834fde80de0f86553cdaf3c10af4`.
- **Milestone M4-E:** Covers *Audio Ergonomics, Full Integration & Forensic Release Gate* (`docs/MOBILE_M4_ARCHITECTURE.md:§22-23`), including Settings/Storage UI, end-to-end user journeys (Permission → Scan → Play), and the final mobile production release gate. **Milestone M4-E has NOT been started by this task.**
