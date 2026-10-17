# Incident & Defect Record: INC-001 Storage Volume Disconnection During Active Playback

## 1. Incident Overview
- **Incident ID:** `INC-001`
- **Date Detected:** 2026-10-10 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Target Subsystem:** Harmonia Mobile / Playback Engine (`harmonia-mobile/app/`)
- **Milestone / Phase:** Milestone M4-D / M4-F Hardening
- **Severity Classification:** **HIGH**
- **Investigation Status:** **RESOLVED & VERIFIED**
- **Confidence Rating:** `[VERIFIED-SRC]`

---

## 2. Symptoms & User/System Impact
- **Observed Behavior:**
  When a removable micro-SD card or USB-OTG drive hosting playing audio was physically detached, ExoPlayer threw unhandled `FileNotFoundException` and `PlaybackException(ERROR_CODE_IO_FILE_NOT_FOUND)`. In early builds, this crash cleared the current playback queue, reset position to 0, and caused the player service to terminate unexpectedly.
- **User Impact:**
  User lost active playlist position, audio crashed with an error dialog, and reconnecting the storage volume required manually navigating back to the album or playlist to rebuild the queue.
- **Reproduction Steps:**
  1. Ingest an external storage source (e.g. SD card) containing audio files.
  2. Start playback of a track located on the external storage source.
  3. Physically eject or unmount the external storage source during active playback.
  4. Observe ExoPlayer I/O crash and loss of queue state.

---

## 3. Root Cause Analysis
- **Confirmed Root Cause (Evidence-Backed):**
  Android OS broadcast intent `ACTION_MEDIA_EJECT` / `ACTION_MEDIA_UNMOUNTED` was initially observed asynchronously by storage scanning components but had no direct wiring to `HarmoniaPlayerController`. ExoPlayer continued attempting to buffer chunks from an invalid file descriptor or revoked SAF URI, resulting in fatal I/O exceptions on ExoPlayer's internal playback thread.
- **Hypotheses Considered & Disproven:**
  - *Hypothesis A: Database record deletion caused query failure:* Disproven. Database records were not being deleted; the failure occurred purely in the media extractor layer reading the underlying bitstream.
  - *Hypothesis B: ExoPlayer could automatically pause on IO error without state loss:* Disproven. Without pre-emptive intervention, ExoPlayer transitions to `STATE_IDLE` with a fatal error, clearing its media item playlist context.

---

## 4. Corrective Action & Implementation
- **Remediation Summary:**
  Introduced proactive storage volume detachment coordination via [`DefaultStorageVolumeLifecycleCoordinator.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/storage/lifecycle/DefaultStorageVolumeLifecycleCoordinator.kt) registered with `HarmoniaPlayerController`.
- **Code Modifications:**
  - [`DefaultStorageVolumeLifecycleCoordinator.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/storage/lifecycle/DefaultStorageVolumeLifecycleCoordinator.kt): Dispatches volume disconnection callbacks before file handles are invalidated.
  - [`HarmoniaPlayerController.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt): Captures exact current position, pauses playback immediately, marks track status as `STORAGE_UNMOUNTED`, and preserves queue and track metadata in Room.
- **Alternatives Rejected:**
  - *Automatic Retry Loop:* Rejected. Retrying I/O against an unmounted storage medium causes infinite loops and battery drain.

---

## 5. Regression Prevention & Invariant Protection
- **Regression Test Suite:**
  - **Playback State & Queue Preservation Test:**
    - Test File: [`HarmoniaPlaybackInterruptionTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/playback/HarmoniaPlaybackInterruptionTest.kt)
    - Test Method: `` `scenario 13 - track ID, local file identity, queue, ordering, and position are preserved on storage disconnect` `` `[VERIFIED-TEST]`
    - Verification: Proves that track ID, local file ID, current playback position, queue ordering, and queue index are preserved intact with `STORAGE_DISCONNECTED_MESSAGE` set.
  - **Storage Lifecycle Coordination Test:**
    - Test File: [`StorageVolumeLifecycleCoordinatorTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/data/local/storage/lifecycle/StorageVolumeLifecycleCoordinatorTest.kt)
    - Test Method: `unmount_marks_source_unavailable_without_changing_user_is_enabled()` `[VERIFIED-TEST]`
  - **UI Banner & Badge Integration Tests:**
    - Test File: [`HarmoniaDisconnectedStorageUiTest.kt`](../../../harmonia-mobile/app/src/androidTest/java/org/harmonia/player/HarmoniaDisconnectedStorageUiTest.kt)
    - Test Methods: `nowPlaying_shows_storage_disconnected_banner_when_storage_disconnected_error_active()`, `library_track_row_displays_storage_unmounted_badge_when_source_is_physically_unmounted()` `[VERIFIED-TEST]`
  - Execution Output: Verified passing with 0 errors across test suites.
- **Durable Architectural Invariant:**
  - Unmounting a storage volume must pause playback, preserve queue and track position, mark tracks unavailable, and **never** delete records from Room or wipe the queue `[VERIFIED-SRC]`.

---

## 6. Related Documentation & Architecture
- **Phase Record:** [`mobile_m4f.md`](../phases/mobile_m4f.md)
- **ADR Reference:** [`ADR-030`](../../MOBILE_M4_ARCHITECTURE.md#27-architectural-decision-records-adrs)
