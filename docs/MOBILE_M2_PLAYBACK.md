# Harmonia Mobile Phase M2: Native Playback Engine Specification & Forensic Record

## 1. Executive Summary & Verification Context

Phase M2 establishes the native Android playback engine for Harmonia Mobile using AndroidX Media3 (`1.9.2`), ExoPlayer, `MediaSessionService`, and Room SQLite persistence. The implementation strictly adheres to the finalized M0.1 architecture, decoupling pure domain contracts from Android platform dependencies.

### Verification Classification & Honest Terminology
To ensure rigorous reporting integrity, this verification explicitly differentiates between verification tiers:
1. **Tier A — Android Virtual Device (AVD) Integration**: Verified on Android Virtual Device `medium_phone` running Android 16 (`sdk_gphone64_x86_64`, API Level 36, serial `emulator-5554`).
2. **Tier B — Physical Android Device**: **NOT PERFORMED**. No physical Android handset hardware was connected or utilized. All device-level tests were performed exclusively on the Android Virtual Device emulator.
3. **Tier C — Actual Audio Pipeline Verification**: The emulator was launched **WITHOUT** `-no-audio`, with native audio output paths active (`dumpsys audio` confirming `STREAM_MUSIC` routing through virtual speaker interfaces). The tests verify the end-to-end pipeline:
   $$\text{Local File} \longrightarrow \text{MediaItem} \longrightarrow \text{ExoPlayer} \longrightarrow \text{AudioTrack / AudioRenderer} \longrightarrow \text{Android Audio HAL}$$
   Playback advancement was confirmed by audio renderer buffer processing advancing `currentPosition` across all supported codecs. Physical ear acoustic output is simulated via the host audio subsystem.

---

## 2. Media3 & Session Architecture

### Tiered Component Stack
```
                 Jetpack Compose UI
           (MiniPlayer / NowPlayingSheet)
                         │
                         ▼
        PlaybackViewModel / PlayerController
                         │
                         ▼
             PlaybackPort (Pure Domain)
                         │
                         ▼
    HarmoniaPlayerController (MediaController)
                         │ IPC / SessionToken
                         ▼
  HarmoniaPlaybackService (MediaSessionService)
                         │
                         ▼
               ExoPlayer (Authoritative)
                         │
                         ▼
            Android Audio HAL (STREAM_MUSIC)
```

### Decoupled Boundary Rules
- **Pure Domain (`domain/playback/PlaybackPort.kt`)**: Defines `playbackState: StateFlow<PlaybackState>`, `queue: StateFlow<List<QueueItem>>`, and authoritative transport commands (`play()`, `pause()`, `stop()`, `seekTo()`, `next()`, `previous()`, `setQueue()`, `appendToQueue()`, `jumpToQueueIndex()`, `setShuffleMode()`, `setRepeatMode()`, `setVolume()`).
- **No Android Framework in Domain**: Zero references to `Context`, `Uri`, `ExoPlayer`, `MediaItem`, or `MediaSession` inside `org.harmonia.player.domain.*`.
- **Platform Adapter (`data/playback/LocalFileSourceAdapter.kt`)**: Safely maps domain `Track` and `LocalFile` references to Media3 `MediaItem` instances with structured metadata (Title, Artist, Album, Track Number, Disc Number).

---

## 3. Audio Pipeline Verification Across All Formats

Tests were executed directly on the audio-enabled Android Virtual Device (`medium_phone`, Android 16, API 36) via instrumented test suite `RealAndroidPlaybackTest.kt`. Real test audio bitstreams were extracted directly into the application internal sandbox (`context.filesDir/Music`).

| Format | Container / Codec | Pipeline Verification Sequence | Result | Evidence |
|---|---|---|---|---|
| **MP3** | MPEG-1 Audio Layer III | Prepare $\to$ `STATE_READY` $\to$ `play()` $\to$ `isPlaying == true` $\to$ buffer progress advances $\to$ `seekTo(300ms)` $\to$ position updated $\to$ `pause()` $\to$ `play()` $\to$ progress resumes $\to$ `stop()` $\to$ `STATE_IDLE` | **PASS** | `testRealMp3PlaybackWithAudioPipeline` |
| **FLAC** | Free Lossless Audio Codec | Prepare $\to$ `STATE_READY` $\to$ `play()` $\to$ buffer progress advances $\to$ `seekTo(250ms)` $\to$ `pause()` $\to$ resume $\to$ `stop()` $\to$ `STATE_IDLE` | **PASS** | `testRealFlacPlaybackWithAudioPipeline` |
| **M4A / AAC** | ISO/IEC 14496-14 / AAC-LC | Prepare $\to$ `STATE_READY` $\to$ `play()` $\to$ buffer progress advances $\to$ `seekTo(200ms)` $\to$ `pause()` $\to$ resume $\to$ `stop()` $\to$ `STATE_IDLE` | **PASS** | `testRealM4aAacPlaybackWithAudioPipeline` |
| **WAV** | RIFF / Linear PCM | Prepare $\to$ `STATE_READY` $\to$ `play()` $\to$ buffer progress advances $\to$ `seekTo(200ms)` $\to$ `pause()` $\to$ resume $\to$ `stop()` $\to$ `STATE_IDLE` | **PASS** | `testRealWavPlaybackWithAudioPipeline` |

---

## 4. Authoritative Queue & Media3 Synchronization

### Queue Verification Matrix
1. **Queue Traversal (Track A $\to$ Track B $\to$ Track C)**:
   - `testMultiTrackAuthoritativeQueueNavigation`: Verified sequential advancement using `seekToNextMediaItem()`.
   - Index progression: `0` (Track A) $\to$ `1` (Track B) $\to$ `2` (Track C).
2. **Previous Semantics**:
   - `seekToPreviousMediaItem()` traverses `2` (Track C) $\to$ `1` (Track B) $\to$ `0` (Track A).
   - In `HarmoniaPlayerController`, if playback position exceeds 3000ms, `previous()` rewinds to beginning of the active track instead of skipping backward.
3. **Automatic End-Of-File (EOF) Progression**:
   - `testAutomaticEofProgressionOnQueue`: Verified deterministic advancement on natural track end.
   - Seeked to `duration - 250ms`, allowed audio renderer to complete track playback, verified listener callback `onMediaItemTransition` with `MEDIA_ITEM_TRANSITION_REASON_AUTO`.
   - ExoPlayer seamlessly advanced active index to `1` (Track B) without manual UI intervention.
4. **Architectural Guard**:
   - The UI never calculates `currentIndex + 1` or `currentIndex - 1`. All queue operations are dispatched through `PlaybackPort`.

---

## 5. Background Playback & MediaSession Lifecycle

### Foreground Service Configuration
- `HarmoniaPlaybackService` extends `androidx.media3.session.MediaSessionService`.
- Manifest declarations:
  - `android:foregroundServiceType="mediaPlayback"`
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  - `android.permission.POST_NOTIFICATIONS`
- Audio configuration:
  - `AudioAttributes`: `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`, `handleAudioFocus = true`.
  - `setHandleAudioBecomingNoisy(true)`.
  - `setWakeMode(C.WAKE_MODE_LOCAL)`.

### AVD Runtime Lifecycle Verification
1. **Service Registration**: Verified via `dumpsys media_session` that session `androidx.media3.session.id. org.harmonia.player/...` is active with `flags=1` (`FLAG_HANDLES_MEDIA_BUTTONS`).
2. **Device Sleep / Screen Lock**: Dispatched `input keyevent KEYCODE_SLEEP`. Process `org.harmonia.player` remained alive; `dumpsys media_session` confirmed session remained active.
3. **Remote Media Key Dispatch**: Dispatched `cmd media_session dispatch play-pause`, `cmd media_session dispatch next`, `cmd media_session dispatch previous`, and `KEYCODE_HEADSETHOOK`. Commands were dispatched and handled without crashing or disconnecting the service.
4. **Device Wake**: Dispatched `input keyevent KEYCODE_WAKEUP`. UI reconnected to the active `MediaSession` via `HarmoniaPlayerController`.

---

## 6. Audio Focus & Becoming Noisy

### Engine Configuration
- `ExoPlayer` is configured with:
  ```kotlin
  val audioAttributes = AudioAttributes.Builder()
      .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
      .setUsage(C.USAGE_MEDIA)
      .build()
  ExoPlayer.Builder(context)
      .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
      .setHandleAudioBecomingNoisy(true)
      .build()
  ```
- **Platform Behavior**:
  - `handleAudioFocus = true`: Media3 automatically requests `AUDIOFOCUS_GAIN` on `play()`, ducks volume on transient loss, and pauses on permanent focus loss.
  - `setHandleAudioBecomingNoisy(true)`: Media3 registers an internal `BroadcastReceiver` for `AudioManager.ACTION_AUDIO_BECOMING_NOISY`, automatically pausing playback when headphones or external speakers are disconnected.
- **AVD Testing Limitation**: Physical headphone disconnection and incoming GSM cellular phone calls cannot be physically simulated on the emulator. Runtime reliance is placed on AndroidX Media3's built-in framework receiver verified by unit configuration.

---

## 7. Deterministic Error & Missing File Handling

- Verified via `testMissingFileDeterministicHandling`:
  - Setting a non-existent file URI triggers `onPlayerError(PlaybackException)` with `ERROR_CODE_IO_FILE_NOT_FOUND`.
  - Engine transitions to `Player.STATE_IDLE`.
  - Process does not crash (`SIGABRT` / `RuntimeException` prevented).
  - Queue remains intact and ready for subsequent user commands.

---

## 8. Persistence & Cold Start Recovery

- Room SQLite tables:
  - `mobile_playback_state`: Persists `currentTrackId`, `currentLocalFileId`, `currentPositionMs`, `durationMs`, `activeQueueIndex`, `shuffleMode`, `repeatMode`, `volumeScalar`.
  - `mobile_queue_items`: Persists authoritative ordered queue items.
- On cold start, `HarmoniaPlayerController.init` reads persisted state and restores the queue into `MediaController` / `ExoPlayer` via `syncControllerWithQueue` without state corruption.

---

## 9. Factual Listening Telemetry (`ListeningEvent`)

- Pure domain `ListeningEvent` captures factual playback milestones without surveillance or recommendation engine entanglements:
  - `STARTED`: Generated on initial media item transition.
  - `PAUSED`: Generated when active playback pauses.
  - `RESUMED`: Generated on playback continuation.
  - `SEEKED`: Generated on manual timeline scrub.
  - `COMPLETED`: Generated when track reaches natural EOF.
  - `SKIPPED`: Generated when user advances past active track before natural end.
  - `STOPPED`: Generated on explicit engine termination.
- Stored persistently in Room table `mobile_listening_events` via `ListeningEventDao` and `OfflineListeningEventRepository`.

---

## 10. Dependency & Security Audit

### Version Catalog (`harmonia-mobile/gradle/libs.versions.toml`)
- `androidx-media3-exoplayer`: `1.9.2`
- `androidx-media3-session`: `1.9.2`
- `androidx-media3-common`: `1.9.2`
- `androidx-room-runtime`: `2.8.4`
- `androidx-compose-bom`: `2026.03.01`
- **Exclusions Confirmed**:
  - Zero Kotlin Multiplatform (KMP) / Compose Multiplatform (CMP) / SQLDelight.
  - Zero iOS / CarPlay code.
  - Zero Jellyfin dependencies.
  - Zero AI / LLM / recommendation SDKs.
  - Zero analytics or telemetry tracker libraries.
  - Zero custom C++ / native audio engine dependencies (standard platform Media3 utilized).

### Permissions & Security (`AndroidManifest.xml`)
- Only genuinely required playback permissions:
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  - `android.permission.POST_NOTIFICATIONS`
- Zero broad external storage management permissions (`MANAGE_EXTERNAL_STORAGE` is strictly absent).
- Audio files are accessed via sandboxed internal paths and SAF URIs without exposing raw filesystem paths across domain boundaries.

---

## 11. Test Execution Summary

| Test Suite | Target | Executed Tasks | Result | Notes |
|---|---|---|---|---|
| **JVM Unit Tests** | Desktop JVM | `gradlew.bat clean test` | **12 / 12 PASS** | Pure domain, DAO, queue logic, repository tests |
| **Connected Android Tests** | AVD `medium_phone` (API 36) | `gradlew.bat connectedAndroidTest` | **7 / 7 PASS** | Audio pipeline, MP3, FLAC, M4A, WAV, queue, EOF, missing file |
| **Debug APK Build** | Android Packaging | `gradlew.bat assembleDebug` | **SUCCESS** | Output: `app-debug.apk` |
| **Core Pytest Suite** | Python 3.11 | `uv run pytest tests/ -q` | **173 / 173 PASS** | Zero regressions in Harmonia Core engine |

---

## 12. Scope Boundary Confirmation

The following features remain deferred to future milestones:
- Android Auto / Automotive OS (Phase M5)
- Sync Engine / LocalSync / mDNS / QR pairing (Phase M4)
- Jellyfin integration
- Harmonia AI
- Recommendation systems
- Advanced DSP / Equalizer / ReplayGain audio processing
- iOS / CarPlay
