# Harmonia Mobile — Milestone M4-D Architecture & Research Specification (Revised)
**Storage-Volume Lifecycle, Media3 Audio Interruption & Peripheral Telemetry Architecture**

**Document Classification:** Engineering Architecture Specification & Research Evidence  
**Status:** PROPOSED & REVISED — AWAITING ARCHITECTURE GATE APPROVAL (DO NOT IMPLEMENT PRODUCTION CODE UNTIL REVIEWED)  
**Baseline Anchor:**
- M4-C Accepted Commit / Tag: `def15998b1b2076809471fcf3b355f29930efd02` (`restore-point/mobile-m4c-complete`)
- M4-B Baseline Tag: `restore-point/mobile-m4b-complete` (`91447c7762da2e2919d716a473c685ba5dc06dfe`)
- M4-A Baseline Tag: `restore-point/mobile-m4a-complete` (`f389fbd909a657f708605b0599fb35c6cd33e524`)
- M3 Baseline Tag: `restore-point/mobile-m3-complete` (`c952e159a2d11671403ac169cff439ae9965c507`)

---

## 1. Executive Summary & Core Architectural Corrections

This revised specification resolves the architectural requirements established in the M4-D Architecture Revision Gate:
1. **Four-Tier Storage Semantics:** Strictly separates user configuration (`isEnabled`), physical platform mount status (`isAvailable`), SAF permission validity (`hasPermission`), and physical file existence (`availabilityStatus`). Storage volume ejection proves that a source is unavailable—**never** that its files are deleted. Mass-updating files to `MISSING` on volume ejection is rejected.
2. **Durable Source-to-File Association:** Rejects heuristic `file_uri LIKE prefix` queries. Introduces `source_id: String` to `LocalFileEntity` via Room `MIGRATION_3_4`, establishing an injective, indexed relation between every `LocalFile` and its owning `StorageSource`.
3. **Preservation of Single Media3 Authority:** Retains ExoPlayer in `HarmoniaPlaybackService` as the sole authority for audio focus, ducking, and audio becoming-noisy handling. Rejects duplicate audio-focus managers and duplicate broadcast receivers.
4. **Truthful Playback State & Telemetry:** Observes `Player.Listener.onPlayWhenReadyChanged(playWhenReady, reason)` and `onPlaybackSuppressionReasonChanged(reason)`. Distinguishes explicit user pause from transient suppression, permanent focus loss, becoming-noisy disconnects, and storage disappearance. Prevents false `ListeningEventType.PAUSED` telemetry churn during transient focus dips.
5. **Hardened Lifecycle & Readiness-Checked Scanning:** Replaces arbitrary hardcoded sleep delays with bounded provider readiness checks and debounced dispatch. Handles out-of-order unmount/eject events idempotently. Distinguishes SAF permission revocation from empty directory trees.

---

## 2. Evidence Classification & Source Inspection

To ensure total transparency, all architectural statements are categorized into three explicit evidence tiers:
- **`[CURRENT HARMONIA CODE]`**: Directly verified in the active Harmonia Mobile codebase.
- **`[CONFIRMED OFFICIAL DOCS/SOURCE]`**: Verified in Android Open Source Project (AOSP), official Android documentation, or Media3 source code.
- **`[PROPOSED M4-D SPECIFICATION]`**: Architectural design specified for M4-D implementation.

### 2.1 Current Harmonia Code Evidence
1. **`HarmoniaPlaybackService.kt:L28-L38` `[CURRENT HARMONIA CODE]`:**
   ```kotlin
   val audioAttributes = AudioAttributes.Builder()
       .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
       .setUsage(C.USAGE_MEDIA)
       .build()
   val exoPlayer = ExoPlayer.Builder(this)
       .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
       .setHandleAudioBecomingNoisy(true)
       .setWakeMode(C.WAKE_MODE_LOCAL)
       .build()
   ```
   *Finding:* Media3 audio focus and becoming-noisy handling are already enabled at the player instance boundary.
2. **`HarmoniaPlayerController.kt:L121-L132` `[CURRENT HARMONIA CODE]`:**
   ```kotlin
   override fun onIsPlayingChanged(isPlaying: Boolean) {
       updateStateFromController(controller)
       val currentTrackId = _playbackState.value.currentTrackId
       if (currentTrackId != null) {
           if (isPlaying) {
               recordEvent(ListeningEventType.RESUMED)
           } else if (controller.playbackState != Player.STATE_ENDED) {
               recordEvent(ListeningEventType.PAUSED)
           }
       }
   }
   ```
   *Defect Discovered:* When ExoPlayer suppresses playback due to a transient focus loss (e.g. navigation prompt chime), `isPlaying` becomes `false`. The controller records `ListeningEventType.PAUSED`. When focus returns, it records `ListeningEventType.RESUMED`. This telemetry churn incorrectly logs transient ducking/suppression as deliberate user pauses.
3. **`LocalFileEntity.kt:L24-L70` `[CURRENT HARMONIA CODE]`:**
   *Defect Discovered:* `LocalFileEntity` contains `storageType: String` and `fileUri: String`, but **no `sourceId` column**. Querying files by volume currently requires ambiguous URI prefix matching.
4. **`HarmoniaDatabase.kt:L50` `[CURRENT HARMONIA CODE]`:**
   Database is at schema `version = 3`. Schema v3 added `mobile_storage_sources` and `mobile_scan_checkpoints`.

### 2.2 Authoritative Reference & Media3 Documentation
1. **Media3 `AudioFocusManager` (`androidx.media3.exoplayer.AudioFocusManager`) `[CONFIRMED OFFICIAL DOCS/SOURCE]`:**
   - *URL:* [https://developer.android.com/reference/androidx/media3/exoplayer/AudioFocusManager](https://developer.android.com/reference/androidx/media3/exoplayer/AudioFocusManager)
   - *Behavior:* When `setAudioAttributes(..., handleAudioFocus = true)` is configured, ExoPlayer internally requests focus on `play()` and abandons focus on `stop()` or release.
   - *Transient Loss:* Sets `playbackSuppressionReason = PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS`. `playWhenReady` remains `true`. When focus is regained, suppression is cleared and playback resumes automatically.
   - *Permanent Loss:* Sets `playWhenReady = false` with change reason `PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS`. Playback does **not** resume automatically when focus returns.
   - *User Pause During Suppression:* If user taps pause while transiently suppressed, `playWhenReady` is set to `false` with reason `PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST`. When focus returns, ExoPlayer checks `playWhenReady`; because it is `false`, it does not auto-resume.
2. **Media3 `AudioBecomingNoisyManager` (`androidx.media3.exoplayer.AudioBecomingNoisyManager`) `[CONFIRMED OFFICIAL DOCS/SOURCE]`:**
   - *URL:* [https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder#setHandleAudioBecomingNoisy(boolean)](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder#setHandleAudioBecomingNoisy(boolean))
   - *Behavior:* Dynamically registers an internal receiver for `android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY` only while `playWhenReady == true` and the player is in `STATE_READY` or `STATE_BUFFERING`.
   - *On Trigger:* Sets `playWhenReady = false` with reason `PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY`. Unregisters the receiver when paused.
   - *Headphone Reconnection:* Media3 does **not** register or respond to `ACTION_HEADSET_PLUG` connection events; playback remains paused.
3. **Android `StorageManager.StorageVolumeCallback` `[CONFIRMED OFFICIAL DOCS/SOURCE]`:**
   - *URL:* [https://developer.android.com/reference/android/os/storage/StorageManager.StorageVolumeCallback](https://developer.android.com/reference/android/os/storage/StorageManager.StorageVolumeCallback)
   - *Added in API 30 (Android 11):* Provides `onStateChanged(StorageVolume volume)`. Emits volume state transitions (`MEDIA_MOUNTED`, `MEDIA_UNMOUNTED`, `MEDIA_EJECTING`, `MEDIA_REMOVED`, `MEDIA_BAD_REMOVAL`).
   - *Pre-API 30 Fallback:* `Intent.ACTION_MEDIA_MOUNTED`, `Intent.ACTION_MEDIA_UNMOUNTED`, `Intent.ACTION_MEDIA_EJECT` broadcast intents with `dataScheme = "file"`.
4. **Strawberry Music Player (`RESOURCES/strawberry-master`) `[CONFIRMED OPEN-SOURCE PATTERN]`:**
   - `data/schema/schema-13.sql:L45`: Disconnected storage uses a soft availability flag (`unavailable = 1`). Never deletes track rows, playlists, or play history upon volume detachment.
5. **Auxio (`RESOURCES/Auxio-dev`) `[CONFIRMED OPEN-SOURCE PATTERN]`:**
   - `SystemPlaybackReceiver.kt:L102-L105`: Explains why duplicate receivers for `ACTION_AUDIO_BECOMING_NOISY` must be avoided when ExoPlayer's built-in handler is active.

---

## 3. Four-Tier Storage & File State Model

### 3.1 Taxonomy of States
```
┌──────────────────────────────────────────────────────────────────────────────────┐
│                             FOUR-TIER STORAGE TAXONOMY                            │
├────────────────────┬────────────────────┬──────────────────┬─────────────────────┤
│ Domain Tier        │ State Representation│ Storage Layer    │ Mutated By          │
├────────────────────┼────────────────────┼──────────────────┼─────────────────────┤
│ 1. User Config     │ isEnabled: Boolean │ Room Database    │ User in Settings    │
│                    │                    │ (is_enabled)     │ (NEVER by hardware) │
├────────────────────┼────────────────────┼──────────────────┼─────────────────────┤
│ 2. Platform Mount  │ isAvailable: Bool  │ Runtime State    │ Hardware hotplug &  │
│                    │                    │ (Coordinator)    │ StorageVolume events│
├────────────────────┼────────────────────┼──────────────────┼─────────────────────┤
│ 3. Permission Grant│ hasPermission: Bool│ Runtime State    │ ContentResolver     │
│                    │                    │ (Coordinator)    │ persisted permissions│
├────────────────────┼────────────────────┼──────────────────┼─────────────────────┤
│ 4. File Existence  │ availabilityStatus │ Room Database    │ Completed scan of   │
│                    │ AVAILABLE | MISSING│ (mobile_local_   │ an available source │
│                    │ | CORRUPT          │  files)          │ (NEVER on unmount)  │
└────────────────────┴────────────────────┴──────────────────┴─────────────────────┘
```

### 3.2 Definition of Boundaries & Guarantees
1. **User Configuration (`isEnabled`):**
   - Stored in `mobile_storage_sources.is_enabled: Boolean`.
   - Represents user intent: "I want Harmonia to index and display media from this storage root."
   - **Invariant:** Physical volume unmount, ejection, device reboot, or permission revocation **never** mutates `isEnabled`.
2. **Platform Mount State (`isAvailable`):**
   - Managed at runtime by `StorageVolumeLifecycleCoordinator`.
   - For MediaStore Primary: always `true`.
   - For MediaStore Volume: `true` if `StorageVolume.state == Environment.MEDIA_MOUNTED`, otherwise `false`.
   - For SAF Document Tree: `true` if parent volume is mounted AND `hasPermission == true`.
   - **Startup Reconciliation:** On app launch, `StorageVolumeLifecycleCoordinator` queries `StorageManager.storageVolumes` and `ContentResolver.persistedUriPermissions` to evaluate the fresh, accurate mount state. It does **not** rely on stale flags saved from prior device sessions.
3. **SAF Permission State (`hasPermission`):**
   - Evaluated by verifying `treeUri` against `context.contentResolver.persistedUriPermissions`.
   - If revoked in system settings: `hasPermission = false`.
   - **Invariant:** Revocation is distinct from physical ejection. It signals that Harmonia cannot query the tree, **not** that the files inside the tree were deleted.
4. **File Availability Status (`availabilityStatus`):**
   - Stored in `mobile_local_files.availability_status`:
     * `AVAILABLE`: File exists locally and is verified.
     * `MISSING`: File was confirmed absent during a **completed scan of an available source**.
     * `CORRUPT`: File exists but has corrupt headers or unreadable bitstream.
     * `UNVERIFIED`: Initial discovery before metadata/hash verification.
   - **CRITICAL INVARIANT:** Volume ejection does **not** mutate `availabilityStatus` to `MISSING`. When an SD card with 10,000 songs is unplugged, the songs still physically exist on that SD card. The source is simply unavailable (`isAvailable = false`).
   - Mass database writes on eject are completely eliminated.

### 3.3 Derived Effective Playability
A local audio track is playable if and only if:
```kotlin
val isPlayable: Boolean = source.isEnabled && source.isAvailable && file.availabilityStatus == "AVAILABLE"
```
If `source.isAvailable == false`, the UI displays a "Storage Disconnected" indicator without corrupting or modifying database records.

---

## 4. Reliable Source-to-File Association (Schema Migration 3 $\to$ 4)

### 4.1 Rejection of Heuristic Prefix Matching
Matching file ownership by `file_uri LIKE prefix || '%'` fails because:
- MediaStore URIs on Android (`content://media/external/audio/media/12345`) do not contain volume UUID prefixes.
- Document tree URIs can have URL-encoded path segments or varying tree authority formats across Android vendor builds.
- File moves across directories create false association bugs.

### 4.2 Durable Explicit Foreign Key (`source_id`)
We introduce an explicit, indexed column `source_id: String` to `mobile_local_files`:

```sql
-- Migration 3 -> 4
ALTER TABLE `mobile_local_files` ADD COLUMN `source_id` TEXT;
CREATE INDEX IF NOT EXISTS `index_mobile_local_files_source_id` ON `mobile_local_files` (`source_id`);
```

### 4.3 Injective Source ID Generation & Migration Strategy
| Storage Type | `StorageSource.id` Format | Associated `LocalFileEntity.source_id` | Migration Default for Existing Rows |
| :--- | :--- | :--- | :--- |
| **Primary MediaStore** | `"mediastore_primary"` | `"mediastore_primary"` | Set where `storage_type = 'MEDIA_STORE_PRIMARY'` |
| **Removable Volume** | `"mediastore_vol_<uuid>"` | `"mediastore_vol_<uuid>"` | Set where `storage_type = 'EXTERNAL_SD'` to matching volume ID |
| **SAF Document Tree** | `"saf_tree_<hash>"` | `"saf_tree_<hash>"` | Set where `storage_type = 'SAF_IMPORTED'` to tree ID |
| **Internal Sync** | `"internal_sync_store"` | `"internal_sync_store"` | Set where `storage_type = 'INTERNAL_SYNC'` |

During future scans, `DefaultMediaReconciliationEngine` injects `sourceId = source.id` directly upon candidate insertion:
```kotlin
val localFile = LocalFileEntity(
    id = UUID.randomUUID().toString(),
    trackId = trackId,
    sourceId = source.id, // Explicit, durable, indexed association
    storageType = candidate.sourceType.name,
    fileUri = candidate.runtimeLocator,
    ...
)
```

---

## 5. Playback State, Media3 Listener Semantics & Telemetry

### 5.1 Playback State Domain Extension
Extend `PlaybackState` in `org.harmonia.player.domain.model`:
```kotlin
enum class PlaybackInterruptionReason {
    NONE,
    TRANSIENT_AUDIO_FOCUS_LOSS,
    PERMANENT_AUDIO_FOCUS_LOSS,
    AUDIO_BECOMING_NOISY,
    STORAGE_UNAVAILABLE
}

data class PlaybackState(
    val currentTrackId: String? = null,
    val currentLocalFileId: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val interruptionReason: PlaybackInterruptionReason = PlaybackInterruptionReason.NONE,
    val isSuppressed: Boolean = false,
    ...
)
```

### 5.2 Listener Observation Matrix
In `HarmoniaPlayerController`, observe the fine-grained Media3 events:
```
                                Media3 Event Stream
               ┌─────────────────────────┴─────────────────────────┐
               │                                                   │
onPlaybackSuppressionReasonChanged                 onPlayWhenReadyChanged
               │                                                   │
  reason == TRANSIENT_FOCUS_LOSS                     reason == USER_REQUEST -> Record PAUSED/RESUMED
  -> isSuppressed = true                             reason == BECOMING_NOISY -> interruption = BECOMING_NOISY
  -> Do NOT record ListeningEventType.PAUSED         reason == AUDIO_FOCUS_LOSS -> interruption = PERMANENT_FOCUS
  -> Auto-resumes on focus gain                      reason == END_OF_ITEM -> checkAndRecordCompletion()
```

### 5.3 Behavior When Active Storage Volume Disappears
When `StorageVolumeLifecycleCoordinator` detects that a volume has been unmounted, or if ExoPlayer throws an I/O error (`ERROR_CODE_IO_FILE_NOT_FOUND`):
1. **Identify Affected Playback:** If `_playbackState.value.currentLocalFileId` belongs to the unmounted `source_id`:
2. **Halt Unsafe Reads:** Immediately call `mediaController.pause()` or `mediaController.stop()` to prevent infinite decode retry loops or native crashes.
3. **Preserve Playback Context:** Keep `currentTrackId`, queue items, and `currentPositionMs` intact in `PlaybackState` and database.
4. **Expose Truthful State:**
   - Set `status = PlaybackStatus.PAUSED` (or `ERROR`).
   - Set `interruptionReason = PlaybackInterruptionReason.STORAGE_UNAVAILABLE`.
   - Set `errorMessage = "Storage volume disconnected"`.
5. **Auto-Resume Invariant:** When the volume is reinserted, Harmonia **must never auto-resume** playback. The user must explicitly press Play.

---

## 6. Hardened Storage Lifecycle Coordinator

### 6.1 Architecture of `StorageVolumeLifecycleCoordinator`
- **Location:** `org.harmonia.player.data.local.storage.StorageVolumeLifecycleCoordinator`
- **Lifecycle:** Singleton registered at application startup (`HarmoniaApp.onCreate()`).
- **Platform Registration:**
  - **API 30+ (Android 11+):** Registers `StorageManager.StorageVolumeCallback` on a background thread.
  - **API 26–29 (Android 8.0–10):** Dynamically registers `BroadcastReceiver` with filter for `ACTION_MEDIA_MOUNTED`, `ACTION_MEDIA_UNMOUNTED`, and `ACTION_MEDIA_EJECT` (data scheme `"file"`).
  - Uses `ContextCompat.registerReceiver(..., RECEIVER_EXPORTED)` on API 34+.

### 6.2 Event Transition Handling & Readiness Check
```
[Event Received: ACTION_MEDIA_MOUNTED or onStateChanged(MEDIA_MOUNTED)]
                           │
                           ▼
               Is Volume Valid & Registered?
               ┌───────────┴───────────┐
              NO                       YES
               │                        │
             Ignore                     ▼
                         Update Coordinator State:
                         source.isAvailable = true
                                │
                                ▼
                     Readiness Validation Check:
                 Can ContentResolver query volume?
                 (Bounded loop: 5 attempts @ 300ms)
                     ┌───────────┴───────────┐
                  READY                    TIMEOUT
                     │                        │
                     ▼                        ▼
              Debounced Dispatch        Log warning;
              (coalesce events <500ms)  mark available
                     │                  without scan
                     ▼
       MediaReconciliationEngine.reconcileSource(
           source,
           incremental = true
       )
```

1. **Idempotency Under Rapid Flapping:**
   - Rapid sequence: `ACTION_MEDIA_EJECT` $\to$ `ACTION_MEDIA_UNMOUNTED` $\to$ `ACTION_MEDIA_REMOVED`.
   - Coordinator sets `source.isAvailable = false`. Repeated unmount calls are safe no-ops.
2. **Mount Readiness Over Fixed Sleeps:**
   - MediaStore requires a brief window after Linux mount to register the volume in its database.
   - Instead of a blind sleep, the coordinator executes a lightweight readiness check:
     `contentResolver.query(MediaStore.Audio.Media.getContentUri(volumeName), arrayOf(MediaStore.Audio.Media._ID), null, null, null)`
     Retries up to 5 times with 300ms backoff. As soon as the cursor opens without exception, readiness is established.
3. **SAF Tree Verification:**
   - Validates trees against `contentResolver.persistedUriPermissions`.
   - If missing: flags source as `hasPermission = false` and `isAvailable = false`.
   - Distinct from physical removal: does not attempt filesystem queries.

---

## 7. Architecture Decision Records (ADRs)

### ADR-M4D-03: Four-Tier Storage and File State Model
- **Status:** Proposed
- **Context:** Previous design conflated physical mount state with user configuration (`isEnabled`) and marked all files `MISSING` on volume ejection.
- **Decision:** Separate user configuration (`isEnabled`), platform availability (`isAvailable`), permission validity (`hasPermission`), and file existence (`availabilityStatus`). Volume ejection only affects `isAvailable`. Files are only marked `MISSING` when confirmed absent by a completed scan of an available source.
- **Consequences:** Eliminates destructive reconciliation, avoids database thrashing on hotplug, and provides truthful status.

### ADR-M4D-04: Explicit Source-to-File Foreign Key Association
- **Status:** Proposed
- **Context:** Associating files with storage roots using `file_uri LIKE prefix` is fragile and platform-dependent.
- **Decision:** Add `source_id: String` to `mobile_local_files` with schema migration `MIGRATION_3_4`. Every candidate directly records its owning `StorageSource.id`.
- **Consequences:** Deterministic, indexed source ownership queries. Clean separation of concerns.

### ADR-M4D-05: Single Authority for Audio Focus and Peripheral Interruptions
- **Status:** Proposed
- **Context:** Multiple components managing audio focus or headset unplug events create race conditions and audio glitches.
- **Decision:** ExoPlayer remains the sole authority for audio focus, ducking, and becoming-noisy pause via `setAudioAttributes(..., true)` and `setHandleAudioBecomingNoisy(true)`. Harmonia observes Media3 reasons to keep UI and telemetry truthful without interfering with playback logic.
- **Consequences:** Zero duplicate receivers. Zero competing focus requests. Pure Media3 compliance.

---

## 8. Implementation Steps & Planned Changes

| Phase | Action | Files Affected |
| :--- | :--- | :--- |
| **Phase 1: Schema Migration** | Add `source_id` to `LocalFileEntity`, implement `MIGRATION_3_4`, update `HarmoniaDatabase` | `LocalFileEntity.kt`, `HarmoniaDatabase.kt`, `LocalFileDao.kt` |
| **Phase 2: Reconciliation Update** | Populate `source_id = source.id` in `DefaultMediaReconciliationEngine` during import & update | `DefaultMediaReconciliationEngine.kt` |
| **Phase 3: Lifecycle Coordinator** | Implement `StorageVolumeLifecycleCoordinator` with readiness checks and SAF verification | `StorageVolumeLifecycleCoordinator.kt` (new), `HarmoniaApp.kt` |
| **Phase 4: Player Controller** | Observe `onPlaybackSuppressionReasonChanged` and `onPlayWhenReadyChanged(reason)`; handle volume loss | `HarmoniaPlayerController.kt`, `PlaybackState.kt` |
| **Phase 5: Unit Tests** | JVM test suite for coordinator, state transitions, focus suppression, and migration | `StorageVolumeLifecycleCoordinatorTest.kt`, `PlaybackSuppressionTest.kt`, `Migration3To4Test.kt` |
| **Phase 6: Connected Tests** | Android instrumentation tests for simulated volume eject/mount and audio interruption | `StorageVolumeLifecycleIntegrationTest.kt`, `RealAndroidPlaybackTest.kt` |

---

## 9. Comprehensive Test Strategy & Acceptance Matrix

### 9.1 Focused JVM Unit Tests
1. **`Migration3To4Test`:**
   - Verifies SQLite migration 3 $\to$ 4 preserves all existing track and local file data.
   - Verifies `source_id` column added and indexed properly.
2. **`StorageVolumeLifecycleCoordinatorTest`:**
   - Unmount event marks source `isAvailable = false`; user `isEnabled` is preserved; 0 files are marked `MISSING`.
   - Repeated unmount events are idempotent.
   - Mount event marks source `isAvailable = true` and triggers readiness check.
   - SAF permission revocation marks `hasPermission = false` and `isAvailable = false` without file deletion.
3. **`PlaybackInterruptionAndTelemetryTest`:**
   - `onPlaybackSuppressionReasonChanged(TRANSIENT_AUDIO_FOCUS_LOSS)` sets `isSuppressed = true`; does **not** emit `ListeningEventType.PAUSED`.
   - Clearing suppression sets `isSuppressed = false`; does **not** emit duplicate `STARTED`.
   - `onPlayWhenReadyChanged(false, USER_REQUEST)` emits `ListeningEventType.PAUSED`.
   - `onPlayWhenReadyChanged(false, AUDIO_BECOMING_NOISY)` sets `interruptionReason = AUDIO_BECOMING_NOISY`.
   - User pause while suppressed sets `playWhenReady = false`; prevents auto-resume on focus return.

### 9.2 Connected Android Instrumentation Tests (Emulator API 36)
1. **`StorageVolumeLifecycleIntegrationTest` (Synthetic & Platform State):**
   - Simulated volume eject during playback: player pauses gracefully, exposes `STORAGE_UNAVAILABLE`, preserves queue and track position.
   - Reinserting volume: source returns to available; player remains paused (no unexpected auto-resume).
   - Re-running reconciliation on reconnected volume restores access without duplicate track creation.
2. **`RealAndroidPlaybackTest` (Audio Focus & Pipeline):**
   - Existing real audio playback tests (MP3, FLAC, WAV, M4A) continue passing.
   - Audio focus request and release verified against Android platform `AudioManager`.

---

## 10. Non-Goals & Acceptance Criteria

### Non-Goals for M4-D
- **No Settings / Storage Picker UI:** Deferred to M4-E.
- **No Bluetooth Autoplay:** Strictly forbidden by product principles.
- **No Network / Cloud Syncing:** Offline-first architecture preserved.
- **No Custom Codec Selectors:** Handled natively by Media3.

### Acceptance Criteria for M4-D Gate Authorization
1. [ ] Four-tier storage model strictly preserves user `isEnabled` and file records across unmount/eject events.
2. [ ] Zero files marked `MISSING` purely due to volume ejection.
3. [ ] `LocalFileEntity.source_id` explicitly links every local file to its owning source.
4. [ ] Single Media3 authority for audio focus and becoming-noisy preserved with zero competing managers.
5. [ ] Telemetry logs zero false `PAUSED` events during transient audio focus loss.
6. [ ] Volume removal during active playback pauses cleanly with truthful state and zero auto-resume upon remount.
7. [ ] All regression test gates pass: JVM unit tests, Android connected tests, Core Python tests, and APK assembly.
