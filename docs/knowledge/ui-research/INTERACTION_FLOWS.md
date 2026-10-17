# Harmonia Mobile — Critical Interaction Flows & State Transitions (Stage 5B)

**Document Classification:** Architectural Interaction Specification  
**Status:** COMPLETE & PROPOSED (Stage 5B)  
**Author:** Principal Mobile UX Architect for Harmonia  
**Repository Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  
**Dependencies:** Jetpack Compose, AndroidX Media3, Room SQLite, Storage Access Framework  

---

## 1. Flow 1: First Launch & Audio Storage Permission

- **Trigger:** User launches Harmonia Mobile for the very first time.
- **Preconditions:** Catalog database is unpopulated; Android `READ_MEDIA_AUDIO` (API 33+) or `READ_EXTERNAL_STORAGE` (< API 33) is ungranted.
- **State Transition:**
  1. `HomeScreen` displays empty onboarding card: `"Welcome to Harmonia. Grant audio storage access to discover your local music."`.
  2. User taps `"Grant Permission"` button.
  3. System permission modal launches via `ActivityResultContracts.RequestPermission()`.
  4. If granted: `LibraryViewModel.onPermissionResult(true)` is invoked, triggering the initial media reconciliation scan.
- **Visible Feedback:** Button ripples, system dialog appears, screen transitions to `ScanningState` with animated progress spinner.
- **Error Handling (Permission Denied):**
  - If user denies with rationale available: UI presents `PermissionRequiredState` card explaining why access is essential.
  - If permanently denied (`SETTINGS_REQUIRED`): Button text changes to `"Open System Settings"` with direct intent launch to `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
- **Accessibility:** TalkBack announces: `"Audio permission required. Tap to grant permission."`.
- **Acceptance Criteria:** Never loop indefinitely or crash; automatically re-check permission in `ON_RESUME` via `DisposableEffect`.

---

## 2. Flow 2: Selecting a Storage Folder via SAF (Storage Access Framework)

- **Trigger:** User taps `"Add Folder"` in `SettingsScreen`.
- **Preconditions:** App is open; `SettingsScreen` is active.
- **State Transition:**
  1. Triggers Android system document tree picker via `ActivityResultContracts.OpenDocumentTree()`.
  2. User selects an external directory or SD card music folder and confirms.
  3. Returned tree URI is persisted via `contentResolver.takePersistableUriPermission`.
  4. Folder name is extracted from URI and registered with `SettingsViewModel.registerSafFolder()`.
- **Visible Feedback:** `SettingsScreen` immediately inserts a new `StorageSourceRow` displaying folder name, initial track count (`0 tracks`), and a status chip: `"INDEXING"`.
- **Error Handling:** If user cancels the system folder picker, no new row is added and no error banner is displayed.
- **Accessibility:** TalkBack announces: `"Storage folder [Name] added. Scanning started."`.
- **Acceptance Criteria:** Persistable URI permissions survive device reboots without requiring re-selection.

---

## 3. Flow 3: Media Scanning & Populated Library Display

- **Trigger:** Automatic trigger upon folder addition, or manual tap of `"Scan Library"` in `SettingsScreen`.
- **Preconditions:** At least one storage source is registered and enabled.
- **State Transition:**
  1. `isScanning` becomes `true`.
  2. `SourceScanCoordinator` executes per-source serialization (`Mutex.withLock`, `INC-002`).
  3. In `LibraryScreen`, `ScanningBanner` appears above tabs showing progress: `"Updating music library... Found [N] tracks"`.
  4. On scan completion: `isScanning` resets to `false`; Room database emits new track list to `LibraryViewModel.uiState`.
- **Visible Feedback:** Smooth crossfade from `EmptyLibraryState` or `ScanningState` into the populated `LazyColumn` of `TrackRowCard` items.
- **Error Handling (Scan Failure):** If an I/O error or permission revocation occurs, `ScanFailedState` renders with a red error icon, exact failure message, and a `"Retry Scan"` button.
- **Accessibility:** TalkBack announces: `"Media scan complete. [N] tracks in catalog."`.
- **Acceptance Criteria:** Database writes must occur in batches within Room transactions; zero UI stuttering or dropped frames on the main thread.

---

## 4. Flow 4: Starting Playback from Track, Album, Artist, or Playlist

- **Trigger:** User taps a track in `LibraryScreen`, taps `"Play All"` on `AlbumDetailScreen`, or taps `"Shuffle All"` on `HomeScreen`.
- **Preconditions:** At least one playable track exists in the selected collection.
- **State Transition:**
  1. UI invokes `PlaybackViewModel.playTrackList(tracks, startIndex)`.
  2. ViewModel constructs `MediaItem` list and sends command to `HarmoniaPlaybackService` via `HarmoniaPlayerController`.
  3. ExoPlayer prepares media source, requests audio focus, and begins decoding.
  4. `PlaybackState.isPlaying` updates to `true`.
  5. Docked `MiniPlayer` slides into view at `Alignment.BottomCenter` above navigation tabs.
- **Visible Feedback:**
  - Clicked track row displays an active playing indicator (Teal soundwave icon).
  - Mini-Player appears with album art thumbnail, title, artist, and running progress bar.
- **Error Handling:** If audio focus is rejected by the system, playback stays in paused state without crashing.
- **Accessibility:** TalkBack announces: `"Playing [Track Title] by [Artist]"`.
- **Acceptance Criteria:** Playback must start within **150ms** of user tap on local storage files.

---

## 5. Flow 5: Expanding & Collapsing Now Playing

- **Trigger:** User taps the docked `MiniPlayer` card to expand; taps header down-arrow or drags sheet handle down to collapse.
- **Preconditions:** An active track exists in playback.
- **State Transition:**
  - **Expand:** `showNowPlayingSheet` toggles to `true`; `ModalBottomSheet` elevates with dark scrim over background content.
  - **Collapse:** User swipes down or taps Back; sheet state dismisses, scrim fades out, focus returns to `MiniPlayer`.
- **Visible Feedback:** Fluid 250ms elevation transition. In expanded state, large 280dp album art, full transport controls, and seekbar occupy the viewport.
- **Error Handling:** System back button or predictive back swipe cleanly dismisses the sheet without popping the underlying navigation destination.
- **Accessibility:** Focus is transferred to the Now Playing sheet header when expanded, and restored to `MiniPlayer` when dismissed.
- **Acceptance Criteria:** Active playback, seek progress, and queue state remain strictly undisturbed during expansion and collapse.

---

## 6. Flow 6: Opening & Editing the Playback Queue

- **Trigger:** User taps the Queue button (`HarmoniaIcons.QueueMusic`) in the Now Playing header.
- **Preconditions:** Now Playing sheet is open; queue contains at least 1 track.
- **State Transition:**
  1. Now Playing view switches to `QueueView` (`showQueue = true`).
  2. Queue displays two distinct sections: **Layer 1: Up Next (FIFO)** and **Layer 2: Context Playback (Album/Playlist)**.
  3. User can drag handles to reorder upcoming tracks or swipe to dismiss unwanted tracks.
  4. Tapping any queue item jumps playback immediately to that track index via `PlaybackViewModel.jumpToQueueIndex(index)`.
- **Visible Feedback:** Queue list animates item repositioning; the newly active track highlights in `AcousticTeal`.
- **Error Handling:** Swiping the currently playing track is disallowed or acts as `"Skip Next"`.
- **Accessibility:** TalkBack announces: `"Reordered [Track Title] to position [N]"`.
- **Acceptance Criteria:** Reordering in the UI updates ExoPlayer's playlist queue deterministically without resetting playback position.

---

## 7. Flow 7: Searching & Preserving State Across Navigation

- **Trigger:** User enters text into `SearchScreen` input, views results, taps an album to view details, then presses Back.
- **Preconditions:** User is on `SearchScreen`.
- **State Transition:**
  1. User types query `"Radiohead"`.
  2. Room FTS/LIKE debounces query (300ms) and renders sectioned results (`TRACKS`, `ALBUMS`, `ARTISTS`).
  3. User taps album card `"OK Computer"`.
  4. Screen navigates to `AlbumDetailScreen`.
  5. User inspects tracks and presses System Back.
  6. Screen pops back to `SearchScreen`.
- **Visible Feedback:**
  - Query string `"Radiohead"` remains in the text box.
  - Previous search results and scroll position remain completely intact without re-querying the database.
- **Accessibility:** Focus is restored to the clicked album card upon returning.
- **Acceptance Criteria:** Zero query flicker; zero loss of user search context during nested drill-down.

---

## 8. Flow 8: Detail Screen Navigation & Return

- **Trigger:** User taps an album or artist card from `LibraryScreen`.
- **Preconditions:** User is browsing the `Albums` or `Artists` tab.
- **State Transition:**
  1. Navigation stack pushes `Route.AlbumDetail(albumId)` or `Route.ArtistDetail(artistId)`.
  2. Target detail screen fetches tracks via repository and renders full discography/tracklist.
  3. User taps Back arrow or performs system edge-back swipe.
  4. Navigation stack pops, returning to `LibraryScreen`.
- **Visible Feedback:** Slide-in transition from right; slide-out transition to right on return.
- **Acceptance Criteria:** Library tab selection (`ALBUMS` or `ARTISTS`) and scroll position are 100% preserved.

---

## 9. Flow 9: Storage Disconnection During Active Playback

- **Trigger:** External SD card or USB-OTG drive containing the actively playing track is physically unmounted or detached.
- **Preconditions:** Playback is actively running from an external storage volume.
- **State Transition:**
  1. `StorageVolumeLifecycleCoordinator` detects unmount broadcast (`ACTION_MEDIA_EJECT` or `ACTION_MEDIA_UNMOUNTED`).
  2. ExoPlayer triggers error or pause; coordinator captures event and sets `PlaybackState.STORAGE_DISCONNECTED_MESSAGE`.
  3. Playback **pauses immediately**; does NOT advance to next track or wipe the queue.
  4. In `NowPlayingSheet`, an amber/red diagnostic banner appears:
     `"Storage Disconnected. The storage volume for this track is unavailable. Reconnect storage to resume playback."`.
  5. In `LibraryScreen`, tracks residing on the detached volume dim to 50% alpha with a `"Storage Unmounted"` badge.
- **Recovery Path:** When the user plugs the SD card back in:
  - System mounts volume; coordinator detects mount event.
  - Banner updates: `"Storage reconnected. Tap play to resume."`.
  - User taps Play; playback resumes from the saved position. **Harmonia never auto-resumes audio without user intent.**
- **Accessibility:** High-priority TalkBack announcement: `"Alert: Storage volume disconnected. Playback paused."`.
- **Acceptance Criteria:** Zero catalog rows are deleted; Room database records remain intact throughout the disconnection.

---

## 10. Flow 10: Revoked Storage Permission in Background

- **Trigger:** User navigates to Android OS Settings, revokes Harmonia's Audio Storage Permission, and switches back to Harmonia.
- **Preconditions:** Harmonia was previously granted access.
- **State Transition:**
  1. `MainActivity` resumes (`Lifecycle.Event.ON_RESUME`).
  2. `LibraryViewModel.refreshPermissionState()` checks `ContextCompat.checkSelfPermission()`.
  3. Permission state evaluates to `SETTINGS_REQUIRED`.
  4. `LibraryScreen` displays `PermissionWarningBanner` (if library was populated) or `PermissionRequiredState` card (if empty).
- **Recovery Path:** User taps `"Open Settings"` button; Harmonia launches `Settings.ACTION_APPLICATION_DETAILS_SETTINGS`.
- **Acceptance Criteria:** App does not crash or throw unhandled `SecurityException`.

---

## 11. Flow 11: Sleep Timer Activation, Adjustment & Cancellation

- **Trigger:** User taps the Timer icon in `NowPlayingSheet` header.
- **Preconditions:** Audio is currently playing or loaded.
- **State Transition:**
  1. `SleepTimerDialog` appears displaying preset chips: `15m`, `30m`, `45m`, `60m`, `End of Track`, `Cancel`.
  2. User selects `30m`.
  3. Timer initiates coroutine countdown (`ADR-035`).
  4. Dialog closes; Now Playing header displays active timer countdown: `"29m"`.
  5. At $T - 30$ seconds: Audio volume begins a non-destructive linear fade-out to 0.0.
  6. At $T = 0$: Playback pauses deterministically; system volume is restored to pre-fade level; coroutine cleans up.
- **Cancellation / Modification:** Tapping the timer button while active re-opens the dialog, allowing the user to select another preset or tap `"Turn Off Timer"`.
- **Accessibility:** TalkBack announces: `"Sleep timer set for 30 minutes"`.
- **Acceptance Criteria:** Pausing playback manually pauses the countdown clock; resuming audio resumes the countdown without lost time.

---

## 12. Flow 12: Equalizer Becoming Unsupported or Preempted

- **Trigger:** Audio route switches to an external device (e.g. Bluetooth DAC or USB sink) that rejects platform `AudioEffect` processing, or another priority app claims exclusive DSP control.
- **Preconditions:** Equalizer was enabled in `SettingsScreen`.
- **State Transition:**
  1. `AudioEffect.ERROR_BAD_VALUE` or `CONTROL_LOST` is received by `HarmoniaPlaybackService`.
  2. `SettingsViewModel` receives status update from `AudioSessionCoordinator`.
  3. Equalizer status chip in `SettingsScreen` transitions from `"ACTIVE"` to `"CONTROL PREEMPTED"` (amber) or `"UNSUPPORTED"` (red).
  4. Equalizer band sliders are automatically disabled (dimmed to 50% alpha, non-interactive).
  5. Informative text updates: `"Hardware equalizer is unsupported on this audio route."`.
- **Recovery Path:** When audio routes back to built-in speaker or standard headphones, session ID is re-bound and sliders re-enable automatically.
- **Acceptance Criteria:** Zero audio glitching, stutter, or playback crash when DSP control is lost.

---

## 13. Flow 13: Personalization Phase 4 Future Requirements (Unimplemented)

> [!IMPORTANT]
> The following interaction flow is specified as a future design requirement only. No implementation code is authorized in Stage 5B:

- **Favorite Toggle Flow:**
  - *Trigger:* User taps Heart icon on Now Playing or track list item.
  - *State Transition:* Invokes `PersonalizationViewModel.toggleFavorite()`. Heart glyph fills with `AcousticTeal`; haptic click triggers; Room database commits `FavoriteEntity`.
  - *Feedback:* Immediate visual state toggle; TalkBack announces `"Added to Favorites"` / `"Removed from Favorites"`.
- **Star Rating Flow:**
  - *Trigger:* User taps star 4 on 5-star rating bar.
  - *State Transition:* Invokes `PersonalizationViewModel.setRating(4)`. Stars 1–4 fill with `HarmonicViolet`; Room database commits `RatingEntity(rating = 4)`. Tapping star 4 again clears rating.
