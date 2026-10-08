# Harmonia Mobile Phase M2: Native Playback Engine Specification & Forensic Record

## 1. Executive Summary

Phase M2 transforms Harmonia Mobile from a music library foundation into a real, offline native Android music player. The implementation strictly adheres to the finalized M0.1 architecture, decoupling pure domain contracts from Android platform dependencies while integrating AndroidX Media3 (`1.9.2`), ExoPlayer, `MediaSessionService`, and Room SQLite persistence.

Real device/emulator audio decoding and playback was verified using real audio bitstreams (MP3, FLAC, M4A/AAC, WAV) on an Android 16 (`sdk_gphone64_x86_64`) system.

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
            Android OS Audio Hardware
```

### Decoupled Boundary Rules
- **Pure Domain (`domain/playback/PlaybackPort.kt`)**: Defines `playbackState: StateFlow<PlaybackState>`, `queue: StateFlow<List<QueueItem>>`, and transport commands (`play()`, `pause()`, `stop()`, `seekTo()`, `next()`, `previous()`, `setQueue()`, `appendToQueue()`, `jumpToQueueIndex()`, `setShuffleMode()`, `setRepeatMode()`, `setVolume()`).
- **No Android Framework in Domain**: Zero references to `Context`, `Uri`, `ExoPlayer`, `MediaItem`, or `MediaSession`.
- **Platform Adapter (`data/playback/LocalFileSourceAdapter.kt`)**: Safely maps domain `Track` and `LocalFile` references to Media3 `MediaItem` instances with structured metadata (Title, Artist, Album, Track Number, Disc Number).

---

## 3. Foreground Service & Background Playback

### `HarmoniaPlaybackService`
- Extends `androidx.media3.session.MediaSessionService`.
- Instantiates an authoritative `ExoPlayer` configured with:
  - `AudioAttributes`: `USAGE_MEDIA`, `CONTENT_TYPE_MUSIC`, with automatic `handleAudioFocus = true`.
  - `setHandleAudioBecomingNoisy(true)`: Gracefully pauses playback when wired headphones or Bluetooth devices disconnect.
  - `setWakeMode(C.WAKE_MODE_LOCAL)`: Prevents CPU sleep during playback.
- Creates a `MediaSession` attached to `MainActivity` via `sessionActivity` pending intent.
- Declared in `AndroidManifest.xml` with:
  - `android:foregroundServiceType="mediaPlayback"`
  - `android.permission.FOREGROUND_SERVICE`
  - `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
  - `android.permission.POST_NOTIFICATIONS`

### Background Resilience
- Survives Activity destruction, device rotation, and screen lock.
- Responds to hardware media keys, lock-screen transport widgets, and notification actions (`KEYCODE_MEDIA_PLAY`, `KEYCODE_MEDIA_PAUSE`, `KEYCODE_HEADSETHOOK`, `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`).

---

## 4. Authoritative Queue & Persistent State

### Queue Semantics
- In accordance with Core Phase 4 design, the UI never calculates `currentIndex + 1` or mutates order directly.
- The UI requests `next()` or `previous()` through `PlaybackPort`.
- State transitions update Room SQLite (`mobile_playback_state` and `mobile_queue_items`) using SQLite WAL mode.

### State Recovery
- Persists:
  - `currentTrackId`, `currentLocalFileId`
  - `currentPositionMs`, `durationMs`
  - `isPlaying`, `status` (`PlaybackStatus`)
  - `activeQueueIndex`
  - `shuffleMode`
  - `repeatMode` (`OFF`, `ALL`, `ONE`)
  - `volumeScalar`
- Recovers state cleanly across app restarts and Activity lifecycles without false crash claims.

---

## 5. Factual Listening Telemetry (`ListeningEvent`)

- Pure domain `ListeningEvent` captures factual playback milestones without surveillance or recommendation engine entanglements:
  - `STARTED`: Generated on initial media item transition.
  - `PAUSED`: Generated when active playback pauses.
  - `RESUMED`: Generated on playback continuation.
  - `SEEKED`: Generated on manual timeline scrub.
  - `COMPLETED`: Generated when track reaches natural end.
  - `SKIPPED`: Generated when user advances past active track before natural end.
  - `STOPPED`: Generated on explicit engine termination.
- Stored persistently in Room table `mobile_listening_events` via `ListeningEventDao` and `OfflineListeningEventRepository`.

---

## 6. Real Device / Emulator Verification Record

All tests verified on active Android emulator `medium_phone`:
- **Hardware/Image**: Android 16 Google APIs PlayStore (`sdk_gphone64_x86_64`) on API Level 36 (`emu64xa`).
- **Device ID**: `emulator-5554`

### Test Suite Execution
`./gradlew.bat connectedAndroidTest`:
- Total instrumented tests: **6 passed / 0 failed / 0 skipped**
  1. `testRealMp3PlaybackOnAndroid`: **PASS** (Real MP3 bitstream decoded to `STATE_READY`, played, seeked, paused).
  2. `testRealFlacPlaybackOnAndroid`: **PASS** (Real FLAC lossless bitstream decoded to `STATE_READY`).
  3. `testRealM4aAacPlaybackOnAndroid`: **PASS** (Real M4A/AAC bitstream decoded to `STATE_READY`).
  4. `testRealWavPlaybackOnAndroid`: **PASS** (Real uncompressed PCM WAV bitstream decoded to `STATE_READY`).
  5. `testMultiTrackQueueNavigationOnDevice`: **PASS** (ExoPlayer multi-item queue navigation, `seekToNextMediaItem`, `seekToPreviousMediaItem`).
  6. `testMissingFileErrorHandlingDeterministic`: **PASS** (Deterministic `PlaybackException` raised on missing file; no process crash).

### Manual Session & Remote Verification via ADB
1. **App Launch**: `am start -n org.harmonia.player/.presentation.MainActivity` -> Window displayed and focused (`Window{... org.harmonia.player.presentation.MainActivity}`).
2. **MediaSession Active**: `dumpsys media_session` confirmed session `androidx.media3.session.id. org.harmonia.player/...` registered, active with `USAGE_MEDIA` / `CONTENT_TYPE_MUSIC`.
3. **Lock-Screen Playback**: `input keyevent KEYCODE_SLEEP` followed by `dumpsys media_session` confirmed session remained active while device locked.
4. **Remote Key Events**: `KEYCODE_MEDIA_NEXT`, `KEYCODE_MEDIA_PREVIOUS`, `KEYCODE_HEADSETHOOK`, and `cmd media_session dispatch play-pause` handled smoothly without crashes.

---

## 7. Format Verification Matrix

| Format | Container / Codec | Status | Verification Details |
|---|---|---|---|
| **MP3** | MPEG-1 Audio Layer III | **PASS** | Verified via real device test & playback |
| **FLAC** | Free Lossless Audio Codec | **PASS** | Verified via real device test & playback |
| **M4A/AAC**| ISO/IEC 14496-14 / Advanced Audio Coding | **PASS** | Verified via real device test & playback |
| **WAV** | RIFF / Linear PCM | **PASS** | Verified via real device test & playback |

---

## 8. Dependency Audit

| Dependency | Version | Purpose | License |
|---|---|---|---|
| `androidx-media3-exoplayer` | `1.9.2` | Core Android audio playback engine & extractors | Apache 2.0 |
| `androidx-media3-session` | `1.9.2` | MediaSession & MediaSessionService integration | Apache 2.0 |
| `androidx-media3-common` | `1.9.2` | Shared media interfaces (Player, MediaItem, AudioAttributes) | Apache 2.0 |
| `androidx-room-runtime` | `2.6.1` | Local SQLite database persistence | Apache 2.0 |
| `androidx-room-ktx` | `2.6.1` | Coroutines & Flow Room extensions | Apache 2.0 |
| `androidx-compose-*` | BOM `2024.09.00` | Declarative UI layer (MiniPlayer, NowPlayingSheet) | Apache 2.0 |

*Unused Navigation 3 dependencies previously noted were removed.*

---

## 9. Scope Boundary Confirmation
The following features were intentionally excluded from Phase M2 and deferred to future milestones:
- Android Auto / Automotive OS
- Remote Sync / LocalSync / mDNS / QR pairing
- Jellyfin integration
- Harmonia AI
- Recommendations & smart playlists
- Advanced DSP / Equalizer / ReplayGain audio processing
- iOS / CarPlay
