# Harmonia Mobile — Phase M3-C Implementation Report
**Now Playing + Mini Player + Authoritative Queue Architecture**

## 1. Forensic Inspection of Pre-Existing M2 Baseline

Before modifying or implementing any M3-C code, an exhaustive architectural audit of the M2 playback system was conducted across `PlaybackPort`, `HarmoniaPlaybackService`, `HarmoniaPlayerController`, `PlaybackViewModel`, and associated repositories.

### 1. Existing Playback Authority
- **Component**: `HarmoniaPlaybackService` hosting `androidx.media3.exoplayer.ExoPlayer`.
- **Location**: `org.harmonia.player.service.HarmoniaPlaybackService`.
- **Lifecycle & Ownership**: Inherits from AndroidX `MediaSessionService`. Manages the single authoritative instance of `ExoPlayer`, configured with `C.AUDIO_CONTENT_TYPE_MUSIC`, `handleAudioFocus = true`, `handleAudioBecomingNoisy = true`, and `WAKE_MODE_LOCAL`.
- **Survives**: Activity lifecycle destruction, backgrounding, configuration changes, and screen-off events.

### 2. Existing Queue Authority
- **Component**: `HarmoniaPlayerController` (domain implementation of `PlaybackPort`).
- **Location**: `org.harmonia.player.data.playback.HarmoniaPlayerController`.
- **Queue Semantics**: Maintains the in-memory `_queue: MutableStateFlow<List<QueueItem>>` synchronized with `MediaController.setMediaItems(...)` and persisted via `PlaybackStateRepository` into `mobile_queue_items`.
- **Queue Navigation**: Handled strictly via `next()`, `previous()`, and `jumpToQueueIndex(index)`. Index bounds and next/previous logic are owned by `MediaController` and Media3's queue progression rules. UI never calculates `currentIndex + 1` or `currentIndex - 1`.

### 3. Existing Playback-State Authority
- **Component**: `HarmoniaPlayerController` bridging `MediaController.Listener` to `PlaybackState`.
- **State Properties**: `isPlaying`, `status` (`PlaybackStatus`), `currentPositionMs`, `durationMs`, `currentTrackId`, `currentLocalFileId`, `activeQueueIndex`, `volumeScalar`, `shuffleMode`, `repeatMode`.
- **Persistence**: Persisted to SQLite via `PlaybackStateRepository` (`mobile_playback_state` table, singleton ID = 1) on media item transitions, playback state changes, and seeks.

### 4. Existing ListeningEvent Authority
- **Component**: `HarmoniaPlayerController` delegating to `ListeningEventRepository`.
- **Event Flow**: Emits factual, immutable telemetry:
  - `STARTED` on `Player.Listener.onMediaItemTransition`
  - `RESUMED` on `Player.Listener.onIsPlayingChanged(true)`
  - `PAUSED` on `Player.Listener.onIsPlayingChanged(false)`
  - `SEEKED` on `PlaybackPort.seekTo(positionMs)`
  - `SKIPPED` on `PlaybackPort.next()`
  - `COMPLETED` on `Player.STATE_ENDED` (guarded by `hasRecordedCompletion`)
  - `STOPPED` on `PlaybackPort.stop()`
- **Integrity**: UI never creates, intercepts, or fabricates listening events.

### 5. Existing MediaSession Authority
- **Component**: `MediaSession` inside `HarmoniaPlaybackService`.
- **Session Token**: Exposed to Android system, lock screen, notification manager, and Bluetooth/headset controls.
- **Client Connection**: Handled via `SessionToken` and `MediaController.Builder(context, sessionToken).buildAsync()`.

### 6. What M3-C Consumes
- `PlaybackPort.playbackState: StateFlow<PlaybackState>`
- `PlaybackPort.queue: StateFlow<List<QueueItem>>`
- `PlaybackViewModel` as the single Compose-facing presentation controller
- `PlaybackViewModel.play()`, `pause()`, `next()`, `previous()`, `seekTo()`, `jumpToQueueIndex()`, `toggleShuffle()`, `cycleRepeat()`
- `HarmoniaArtwork` for sandboxed, offline image loading via `artworkHash`

### 7. What M3-C Added
1. **Polished Mini-Player**:
   - Docked above bottom navigation across all primary tabs.
   - Genuine offline artwork thumbnail (`HarmoniaArtwork`).
   - Track title and artist metadata with text truncation.
   - Linear progress bar showing real authoritative position.
   - Touch-friendly 48dp play/pause and next controls with TalkBack accessibility labels.
   - Tap gesture opening the dedicated Now Playing screen.
2. **Dedicated Now Playing Presentation**:
   - Full-bleed dark audiophile aesthetic adhering to Harmonia design tokens (`ObsidianSpace`, `AcousticCharcoal`, `AcousticTeal`, `HarmonicViolet`, `PureSignalWhite`, `MutedSlate`).
   - Large hero artwork with offline fallback (`HarmoniaArtwork`).
   - Track title, artist, and album title with high legibility typography.
   - Interactive scrubbable Slider seek bar with elapsed and remaining time indicators.
   - Comprehensive transport controls (Shuffle toggle, Previous, large primary Play/Pause, Next, Repeat cycle).
   - Dedicated toggle to view Up Next / Active Queue with queue length badge.
3. **Authoritative Queue Presentation**:
   - List view of active `QueueItem`s in true playback order.
   - Visual highlighting of the currently playing track.
   - Individual track artwork thumbnails in queue rows.
   - Tap-to-jump (`jumpToQueueIndex(index)`).
   - Empty state when queue is empty.
4. **Authoritative Position Polling**:
   - A non-intrusive 250ms polling loop in `HarmoniaPlayerController` that reads `MediaController.currentPosition` while `isPlaying == true`, ensuring slider and progress bar reflect real hardware playback position without fabricating an independent timer.
5. **Artwork Association**:
   - Exposing `artworkHash` on `Track` domain model resolved from its parent `AlbumEntity.artworkHash`, giving every playing track direct access to offline cached artwork without altering Room schema.

### 8. What M3-C Explicitly Does NOT Own
- Does NOT construct a second `ExoPlayer` or `MediaController`.
- Does NOT create a secondary queue system or local queue reordering that diverges from Media3.
- Does NOT manipulate indices via `currentIndex + 1` or `currentIndex - 1`.
- Does NOT touch or expose raw audio file paths to Composables.
- Does NOT log or alter `ListeningEvent`s.
- Does NOT access DAOs or Room directly from Compose UI.
- Does NOT request `INTERNET` permission or fetch remote artwork.

---

## 2. Component Design & Layout

```
                  ┌───────────────────────────────┐
                  │       MainAppContainer        │
                  └───────────────┬───────────────┘
                                  │
         ┌────────────────────────┴────────────────────────┐
         ▼                                                 ▼
┌──────────────────┐                              ┌──────────────────┐
│  Navigation Tabs │                              │    MiniPlayer    │
│ (Home, Library,  │                              │ (Docked Bottom,  │
│  Playlists, etc) │                              │  Always Synced)  │
└──────────────────┘                              └────────┬─────────┘
                                                           │ (Tap)
                                                           ▼
                                                  ┌──────────────────┐
                                                  │  NowPlayingSheet │
                                                  │ ┌──────────────┐ │
                                                  │ │ Hero Artwork │ │
                                                  │ ├──────────────┤ │
                                                  │ │ Seek Bar     │ │
                                                  │ ├──────────────┤ │
                                                  │ │ Controls     │ │
                                                  │ ├──────────────┤ │
                                                  │ │ Queue View   │ │
                                                  │ └──────────────┘ │
                                                  └──────────────────┘
```

---

## 3. Position and Seek Mechanics

1. **Authoritative Source**: `MediaController.currentPosition` and `MediaController.duration`.
2. **Real-time Sync**: While `controller.isPlaying` is true, `HarmoniaPlayerController` polls the controller every 250ms and updates `PlaybackState.currentPositionMs`. Polling job is canceled immediately upon pause/stop/release.
3. **User Seeking**:
   - While dragging the slider in `NowPlayingSheet`, `isUserSeeking` is set to `true` to decouple transient thumb movement from incoming state updates.
   - On release (`onValueChangeFinished`), `PlaybackViewModel.seekTo(targetMs)` is invoked.
   - `HarmoniaPlayerController.seekTo(targetMs)` calls `MediaController.seekTo(targetMs)`, logs `ListeningEventType.SEEKED`, and updates state immediately.

---

## 4. Verification & Testing Evidence

### Test Count Evolution
- **JVM Unit Tests**: 28 → **37** (+9 in `PlaybackViewModelTest`)
- **Connected Android Tests**: 29 → **33** (+4 in `NowPlayingAndMiniPlayerUiTest` on `emulator-5554` with real audio enabled)
- **Core Pytest**: 173 → **173** (Unbroken baseline)

### Verification Summary
1. `PlaybackViewModelTest`:
   - `cycleRepeat cycles OFF to ALL to ONE to OFF` -> PASS
   - `next and previous delegate directly to authoritative playback port` -> PASS
   - `position and duration mapping updates accurately` -> PASS
   - `initial ui state matches idle playback port` -> PASS
   - `play and pause delegate correctly to playback port` -> PASS
   - `jumpToQueueIndex delegates to playback port and updates current track` -> PASS
   - `playback UI state mapping reflects playback port updates` -> PASS
   - `unavailable or error state maps correctly into presentation` -> PASS
   - `toggleShuffle cycles boolean on playback port` -> PASS
2. `NowPlayingAndMiniPlayerUiTest` (on AVD `medium_phone` API 36 with audio enabled):
   - `mini_player_appears_when_playback_starts_and_toggles_play_pause` -> PASS
   - `mini_player_next_button_delegates_to_playback_port` -> PASS
   - `now_playing_sheet_opens_and_renders_controls_and_queue` -> PASS
   - `navigation_between_tabs_preserves_mini_player_state` -> PASS
3. `RealAndroidPlaybackTest` (on AVD `medium_phone` API 36 with audio enabled):
   - `testRealMp3PlaybackWithAudioPipeline` -> PASS
   - `testRealFlacPlaybackWithAudioPipeline` -> PASS
   - `testRealM4aPlaybackWithAudioPipeline` -> PASS
   - `testRealWavPlaybackWithAudioPipeline` -> PASS
   - `testAuthoritativeQueueProgression` -> PASS
   - `testMissingFileDoesNotCrashPipeline` -> PASS
   - `testBackgroundPlaybackAndAudioPipelineRelease` -> PASS

---

## 5. Forensic Code Audit

- **`android.*` / `androidx.*` in `domain/`**: 0 instances. Clean Kotlin domain.
- **Direct DAO usage in Compose**: 0 instances. Compose consumes only ViewModels.
- **Direct Room usage in Compose**: 0 instances.
- **Direct ExoPlayer usage in Compose**: 0 instances.
- **Second `MediaController` construction**: 0 instances (only 1 inside `HarmoniaPlayerController`).
- **Second `ExoPlayer` construction**: 0 instances in production (only 1 inside `HarmoniaPlaybackService`).
- **Second queue implementation**: 0 instances. UI directly renders `StateFlow<List<QueueItem>>` from `PlaybackPort`.
- **Manual index arithmetic (`currentIndex + 1` / `- 1`)**: 0 instances for queue logic.
- **Filesystem paths exposed to UI**: 0 instances.
- **Network / Remote libraries (`INTERNET`, Retrofit, OkHttp, Ktor, URL)**: 0 instances. 100% offline.
- **TODO / FIXME**: 0 unresolved comments in application codebase.
