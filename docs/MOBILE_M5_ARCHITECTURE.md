# Harmonia Mobile — Milestone M5 Architecture Specification
**Automotive Integration, Audio Ergonomics & Platform Effects**

**Document Classification:** Architectural Specification & Implementation Baseline
**Status:** IMPLEMENTED IN WORKING TREE (JVM Verified; Physical Hardware & Head Unit Validation Pending)
**Baseline Anchor:**
- M4-F Accepted Release Commit: `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`
- M4-F Release Tag: `restore-point/mobile-m4f-complete` (Annotated tag `789552affe5dac1034aab39c90e3c431a47c93e6`)
- Authoritative References:
  - Master Roadmap: [`docs/ROADMAP.md`](ROADMAP.md)
  - Knowledge Base Implementation Records: [`docs/knowledge/phases/mobile_m5a_automotive.md`](knowledge/phases/mobile_m5a_automotive.md), [`mobile_m5b_media_library.md`](knowledge/phases/mobile_m5b_media_library.md), [`mobile_m5c1_audio_effect_contracts.md`](knowledge/phases/mobile_m5c1_audio_effect_contracts.md), [`mobile_m5c2_audio_session_integration.md`](knowledge/phases/mobile_m5c2_audio_session_integration.md), [`mobile_m5c3_equalizer_settings.md`](knowledge/phases/mobile_m5c3_equalizer_settings.md), [`mobile_m5d_sleep_timer.md`](knowledge/phases/mobile_m5d_sleep_timer.md)
  - Architectural Decisions: [`ADR-032`](knowledge/adr/ADR-032-media-library-automotive-browsing.md), [`ADR-033`](knowledge/adr/ADR-033-equalizer-persisted-user-intent.md), [`ADR-034`](knowledge/adr/ADR-034-audio-effect-lifecycle-and-teardown.md), [`ADR-035`](knowledge/adr/ADR-035-sleep-timer-monotonic-clock-and-volume-safety.md)
  - Engineering Incidents: [`INC-003`](knowledge/incidents/INC-003-rapid-audio-session-race-and-generation-fencing.md), [`INC-004`](knowledge/incidents/INC-004-audio-effect-cancellation-masking-hal-leak.md), [`INC-005`](knowledge/incidents/INC-005-sleep-timer-repeat-one-infinite-loop.md)
  - AndroidX Media3 `1.9.2` Official Specification (`androidx.media3:media3-session`)
  - Android Platform Audio Effects Framework (`android.media.audiofx.AudioEffect`)

> [!NOTE]
> **Implementation Reconciliation Notice (Stage 4):**
> This specification originally served as the M5 Scope Freeze Candidate. Milestones M5-A, M5-B, M5-C (C1–C3), and M5-D have since been fully implemented and verified against automated JVM unit test suites (progressing through 200 JVM tests at M5-A, 223 at M5-B, 245 at M5-C1, 268 at M5-C2, 290 at M5-C3, to 305 JVM tests at the M5-D recorded gate, with 344 in the active working tree after Personalization Phase 3). Physical Android Auto head unit and real-device audio hardware validation remain pending. See the authoritative master roadmap at [`docs/ROADMAP.md`](ROADMAP.md) and the corresponding knowledge-base records cited above for verified evidence and current execution state.

---

## 1. Milestone Purpose and Scope

Milestone **M5** advances Harmonia Mobile from an autonomous personal handset player into a platform-integrated automotive and audio-system participant.

Having proven offline-first local media ingestion, Storage Access Framework (SAF) folder management, volume unmount protection, and decoupled catalog identity across Milestones M1 through M4-F, M5 addresses external media browsing and platform audio hooks:

1. **Android Auto / Automotive OS Media Browsing (M5-A & M5-B):** Migrates the playback service from basic `MediaSessionService` to AndroidX Media3 `MediaLibraryService`, publishing an asynchronous, paginated media browser hierarchy (Albums, Artists, Playlists, Tracks) navigable on car dashboard head units.
2. **Automotive Voice & Text Search (M5-B):** Integrates Android Auto search queries directly with Harmonia's existing offline `SearchRepository`, enabling hands-free speech queries through Google Assistant.
3. **Platform Audio Effects & System Equalizer (M5-C):** Captures ExoPlayer's native audio session ID and broadcasts platform `AudioEffect` lifecycle intents, unlocking system sound enhancements and external third-party DSP equalizers (e.g. Wavelet, Poweramp EQ, OEM sound tools) without embedding custom DSP code. Custom in-app equalizer fallbacks, custom DSP pipelines, and ReplayGain are strictly excluded.
4. **Coroutine Sleep Timer with Volume Preservation (M5-D):** Introduces a non-destructive sleep timer featuring a consistent 30-second volume attenuation fade-out, pre-fade volume capture, user-volume tracking, countdown clock suspension during interruptions, and deterministic coroutine cleanup.
5. **Database Stability:** Preserves Room schema version 4 (`MIGRATION_3_4`) without modification.
6. **Favorites & Ratings:** Documented with full traceability to research requirements; recommended for a dedicated Personalization milestone rather than M5 or M6 LocalSync (decision awaiting approval).

---

## 2. Preserved Architectural Invariants from M4-F

Milestone M5 is built strictly on top of the established M4-F foundation and maintains these core invariants without regression:

1. **Strict Offline-First & Read-Only Policy:** Harmonia Mobile never connects to cloud streaming servers or remote backends for playback. No audio files are ever deleted, renamed, or modified.
2. **Room Schema v4 Stability:** M5 operates exclusively within the existing Room database schema version 4 (`MIGRATION_3_4`). Zero database schema migrations are permitted for M5.
3. **Storage Disconnection Protection:** If an external storage volume is detached during playback, playback pauses immediately, preserves queue position, and marks unavailable tracks without purging catalog records. Reconnection never auto-resumes playback without deliberate user action.
4. **Single-Authority Audio Focus & Noisy Interruption:** ExoPlayer retains sole authority over Android audio focus and `AUDIO_BECOMING_NOISY` headset disconnects. External equalizer tools and sleep timers must not create competing focus requests.
5. **Two-Tier Decoupled Identity (ADR-025 / ADR-026):** Catalog entities (`Track`, `Album`, `Artist`, `Playlist`) remain decoupled from physical storage locators (`LocalFile`). External client media items map to stable content-addressed identifiers.

---

## 3. Authoritative Requirements Traceability & Scope Matrix

The M5 requirements trace directly to preceding architectural decisions and research documents:

| Requirement / Component | Source Document & Section | Original Decision / Rationale | Classification |
| :--- | :--- | :--- | :--- |
| **Android Auto (`MediaLibraryService`)** | `docs/MOBILE_M4_ARCHITECTURE.md:§24` (L673), `docs/MOBILE_M4_RESEARCH.md:§15` (L244) | *"Requires async MediaBrowser paging tree; best built on top of a fully ingested local library. Deferred from M4 to M5."* | **Authoritative Requirement (M5-A, M5-B)** |
| **System Equalizer (`AudioEffect`)** | `docs/MOBILE_M4_ARCHITECTURE.md:§24` (L674), `docs/MOBILE_M4_RESEARCH.md:§6` (L91) | *"Peripheral audio effects follow core ingestion. Broadcast session ID to enable external DSP tools without native NDK bloat."* | **Authoritative Requirement (M5-C)** |
| **Sleep Timer (30s Fade)** | `docs/MOBILE_M4_RESEARCH.md:§6` (L90) | *"Standard in modern players. Coroutine timer with smooth fade-out and pause on expiration."* | **Authoritative Requirement (M5-D)** |
| **Favorites / User Ratings** | `docs/MOBILE_M4_RESEARCH.md:§6` (L95) | *"1-5 stars / heart flags. Candidate for M5; requires schema expansion review."* | **Decision Awaiting Approval (See §8)** |
| **Custom In-App Equalizer DSP** | Product Direction Review | *"Avoid custom biquad DSP complexity, audio latency, and battery drain. Delegate strictly to platform panels."* | **Strict Non-Goal (Excluded from M5-C)** |
| **ReplayGain Normalization** | `docs/MOBILE_M4_RESEARCH.md:§6` (L92) | *"AudioProcessor DSP pipeline belongs to audiophile hardening milestone. Deferred beyond M5."* | **Strict Non-Goal** |
| **LocalSync P2P Engine** | `docs/MOBILE_M4_ARCHITECTURE.md:§24` (L672) | *"Mobile local storage foundation must be proven before network synchronization is introduced. Deferred to Milestone M6."* | **Strict Non-Goal** |
| **Room Schema v5 Migration** | Product Direction Review | *"Keep Room v4 stable. Zero schema migrations permitted for M5."* | **Strict Non-Goal** |

---

## 4. Stage M5-A: MediaLibraryService Foundation & Manifest Configuration

### 4.1 Service Architecture Migration
The background service [`HarmoniaPlaybackService.kt`](../harmonia-mobile/app/src/main/java/org/harmonia/player/service/HarmoniaPlaybackService.kt) is refactored from `androidx.media3.session.MediaSessionService` to `androidx.media3.session.MediaLibraryService`:

```
┌─────────────────────────────────────────────────────────────┐
│                 HarmoniaPlaybackService                     │
│               extends MediaLibraryService                   │
├─────────────────────────────────────────────────────────────┤
│  • Holds authoritative ExoPlayer instance                   │
│  • Instantiates MediaLibrarySession via Builder             │
│  • Overrides onGetSession(): MediaLibrarySession?           │
│  • Attaches HarmoniaLibrarySessionCallback                  │
└─────────────────────────────────────────────────────────────┘
```

### 4.2 MediaLibrarySession Callback Signatures (Media3 1.9.2 Verified)
Media3 `1.9.2` requires subclassing `MediaLibrarySession.Callback` (which extends `MediaSession.Callback`). The verified method signatures for this version are:

```kotlin
private class HarmoniaLibrarySessionCallback(
    private val treeResolver: MediaItemTreeResolver,
    private val searchRepository: SearchRepository
) : MediaLibrarySession.Callback {

    override fun onGetLibraryRoot(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return treeResolver.getRoot(browser, params)
    }

    override fun onGetChildren(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return treeResolver.getChildren(parentId, page, pageSize, params)
    }

    override fun onGetItem(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        mediaId: String
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return treeResolver.getItem(mediaId)
    }

    override fun onSearch(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        return treeResolver.search(browser, query, params)
    }

    override fun onGetSearchResult(
        session: MediaLibrarySession,
        browser: MediaSession.ControllerInfo,
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return treeResolver.getSearchResult(query, page, pageSize, params)
    }

    override fun onAddMediaItems(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        return treeResolver.resolvePlayableMediaItems(mediaItems)
    }

    override fun onSetMediaUri(
        mediaSession: MediaSession,
        controller: MediaSession.ControllerInfo,
        uri: Uri,
        extras: Bundle?
    ): Int {
        return treeResolver.handleSetMediaUri(uri, extras)
    }
}
```

### 4.3 Android Manifest & Automotive Declarations
To declare compatibility with Android Auto and legacy media browser clients, [`AndroidManifest.xml`](../harmonia-mobile/app/src/main/AndroidManifest.xml) is augmented:

1. **Service Intent Filters:**
   ```xml
   <service
       android:name=".service.HarmoniaPlaybackService"
       android:foregroundServiceType="mediaPlayback"
       android:exported="true">
       <intent-filter>
           <action android:name="androidx.media3.session.MediaLibraryService" />
           <action android:name="android.media.browse.MediaBrowserService" />
       </intent-filter>
   </service>
   ```
2. **Automotive Application Metadata:**
   ```xml
   <application ...>
       <meta-data
           android:name="com.google.android.gms.car.application"
           android:resource="@xml/automotive_app_desc" />
   </application>
   ```
3. **Automotive Resource Descriptor (`res/xml/automotive_app_desc.xml`):**
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <automotiveApp>
       <uses name="media" />
   </automotiveApp>
   ```

### 4.4 Software Readiness vs. Physical Vehicle Hardware Validation
Manifest declarations and `MediaLibraryService` compliance establish **Android Auto software readiness**. Formal validation on physical vehicle head-unit hardware remains an empirical boundary outside the repository's CI pipeline; local automated validation uses Google Desktop Head Unit (DHU) emulation and instrumented `MediaBrowser` test clients.

---

## 5. Stage M5-B: Media Tree Hierarchy, Browsing & External Playback

### 5.1 Exact Stable Node IDs and Browsability Rules
Android Auto requires deterministic, stable string `mediaId` keys and strict browsability/playability flags:

```
root (ID: "root", BROWSABLE, NOT PLAYABLE)
 ├── Albums Tab (ID: "root_albums", BROWSABLE, NOT PLAYABLE, Grid Style)
 │    ├── Album Item (ID: "album:{album_id}", BROWSABLE, PLAYABLE, Grid Style)
 │    │    └── Track Item (ID: "track:{track_id}", NOT BROWSABLE, PLAYABLE, List Style)
 │    └── ...
 ├── Artists Tab (ID: "root_artists", BROWSABLE, NOT PLAYABLE, Grid Style)
 │    ├── Artist Item (ID: "artist:{artist_id}", BROWSABLE, PLAYABLE, Grid Style)
 │    │    └── Track Item (ID: "track:{track_id}", NOT BROWSABLE, PLAYABLE, List Style)
 │    └── ...
 ├── Playlists Tab (ID: "root_playlists", BROWSABLE, NOT PLAYABLE, List Style)
 │    ├── Playlist Item (ID: "playlist:{playlist_id}", BROWSABLE, PLAYABLE, List Style)
 │    │    └── Track Item (ID: "track:{track_id}", NOT BROWSABLE, PLAYABLE, List Style)
 │    └── ...
 └── Tracks Tab (ID: "root_tracks", BROWSABLE, NOT PLAYABLE, List Style)
      ├── Track Item (ID: "track:{track_id}", NOT BROWSABLE, PLAYABLE, List Style)
      └── ...
```

#### Node Flags and Styling Rules:
1. **Root Node (`"root"`):**
   - Flags: `FLAG_BROWSABLE = true`, `FLAG_PLAYABLE = false`.
   - Returns the 4 top-level navigation tabs.
2. **Category Nodes (`"root_albums"`, `"root_artists"`, `"root_playlists"`, `"root_tracks"`):**
   - Flags: `FLAG_BROWSABLE = true`, `FLAG_PLAYABLE = false`.
   - Content style: `"root_albums"` and `"root_artists"` declare `CONTENT_STYLE_GRID_ITEM_HINT_VALUE`. `"root_playlists"` and `"root_tracks"` declare `CONTENT_STYLE_LIST_ITEM_HINT_VALUE`.
3. **Container Items (`"album:{album_id}"`, `"artist:{artist_id}"`, `"playlist:{playlist_id}"`):**
   - Flags: `FLAG_BROWSABLE = true`, `FLAG_PLAYABLE = true`.
   - Opening navigates into child tracks; pressing play directly enqueues and plays the container's contents.
4. **Leaf Items (`"track:{track_id}"`):**
   - Flags: `FLAG_BROWSABLE = false`, `FLAG_PLAYABLE = true`.
   - Content style: `CONTENT_STYLE_LIST_ITEM_HINT_VALUE`.

### 5.2 Empty-Library & Edge Case Handling
1. **Empty Catalog / Unindexed SAF Storage:**
   - If the database contains 0 tracks or folders are unindexed, `onGetChildren` returns `LibraryResult.ofItemList(emptyList(), params)` with result code `RESULT_SUCCESS`. It **never** throws an exception, returns an error status code, or hangs.
2. **Invalid or Unrecognized Node IDs:**
   - If `onGetItem(mediaId)` or `onGetChildren(parentId)` is called with an unknown or malformed ID, it returns `LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)`.
3. **Deleted or Disconnected Catalog Entries:**
   - If a requested track exists in the catalog but its physical `LocalFile` is missing or on an unmounted volume, it is safely excluded from playable lists or marked non-playable (`FLAG_PLAYABLE = false`), preventing ExoPlayer playback errors.

### 5.3 Pagination & Latency Contracts
1. **Platform Requirements vs. Harmonia Design Targets:**
   - *Android Auto Latency Guideline:* Android Auto UI monitors client responsiveness and may disconnect after ~3 seconds of inactivity. Harmonia offloads all database queries in `MediaItemTreeResolver` strictly to `Dispatchers.IO`.
   - *Harmonia Pagination Target:* While Media3 allows unbounded lists, Harmonia enforces an explicit design target of **50 items per page** by default (`pageSize = 50`) to conserve head-unit memory and prevent IPC binder transaction buffer overflows (`TransactionTooLargeException`).
2. **Offset Calculation:**
   - Handled via `page` and `pageSize`: offset is `page * pageSize`. If offset exceeds available items, an empty list with `RESULT_SUCCESS` is returned.

### 5.4 Automotive Search Contract & Failure Handling
1. **Speech and Text Search Integration:**
   - Android Auto voice commands ("Play Miles Davis on Harmonia") route through `onSearch(query, params)`.
   - The query is dispatched to Harmonia's existing `SearchRepository.search(query)`.
   - The result count is cached or notified via `session.notifySearchResultChanged(browser, query, totalItemCount, params)`.
2. **Search Results Retrieval (`onGetSearchResult`):**
   - Head unit requests items via `onGetSearchResult(query, page, pageSize, params)`.
   - Matching tracks, albums, and artists are mapped into `MediaItem` instances.
3. **Failure Handling:**
   - Blank or whitespace queries return an empty list with `RESULT_SUCCESS`.
   - If the search engine encounters a database or parsing failure, the error is caught, logged, and returns `LibraryResult.ofItemList(emptyList(), params)` rather than failing the session binder.

### 5.5 External Playback Resolution (`onAddMediaItems` & `onSetMediaUri`)
When a user selects an item on the head unit or issues a voice command:
1. External client sends a `MediaItem` with `mediaId = "track:{track_id}"`.
2. `onAddMediaItems` resolves the track entity from `TrackRepository.getTrackById(trackId)`.
3. The track's active storage locator is obtained from `LocalFileRepository`.
4. A playable `MediaItem` is constructed containing:
   - `requestMetadata.mediaUri`: The physical playable `contentUri` from `LocalFile`.
   - `mediaMetadata`: Title, Artist, Album, Track Number, and content-addressed Artwork URI (`content://org.harmonia.player.artwork/{hash}`).
5. External URI intents received via `onSetMediaUri` are parsed, matched against known local track locators, or rejected with `SessionResult.RESULT_ERROR_NOT_SUPPORTED` if external web URIs are passed.

---

## 6. Stage M5-C: Platform Equalizer & Audio Effects Integration

### 6.1 Audio Session Lifecycle & Platform Broadcast Protocol
Android platform audio effects hook into audio streams via unique `audioSessionId` integers managed by the audio HAL and `AudioTrack`. In Harmonia, `ExoPlayer` manages the `AudioTrack` instance.

#### Official Android Lifecycle Semantics
Per official Android platform documentation (`android.media.audiofx.AudioEffect`):
- `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` announces to the audio framework and external equalizer engines (e.g., Wavelet, Poweramp Equalizer, OEM sound tools) that a specific audio session is active.
- **Critical Lifecycle Distinction:** In ExoPlayer, pausing playback does **NOT** release the underlying `AudioTrack` or destroy the `audioSessionId`. Broadcasting `ACTION_CLOSE...` on every standard pause is an anti-pattern that causes external DSP engines to deconstruct their audio processing graphs, causing audible clicks, pops, or dropped settings upon resumption.
- **Authoritative Session Contract:**
  1. Broadcast `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` when a valid non-zero `audioSessionId` is allocated by ExoPlayer (via `AnalyticsListener.onAudioSessionIdChanged` or `Player.Listener`).
  2. Re-broadcast `ACTION_OPEN...` if ExoPlayer recreates the `AudioTrack` and allocates a new `audioSessionId`.
  3. Keep the session **OPEN** through transient pauses, user seeks, track transitions, and audio-focus ducks.
  4. Broadcast `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION` **ONLY** when:
     - The playback service is destroyed (`HarmoniaPlaybackService.onDestroy`).
     - ExoPlayer is explicitly released (`player.release()`).
     - The current `audioSessionId` is decommissioned by the platform.

```
┌────────────────────────────────────────────────────────────────────────┐
│                   EXOPLAYER AUDIO SESSION LIFECYCLE                    │
├────────────────────────────────────────────────────────────────────────┤
│ 1. ExoPlayer allocates AudioTrack                                     │
│    └─► onAudioSessionIdChanged(audioSessionId: Int)                    │
│ 2. Broadcast ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION                  │
│    └─► Session remains ACTIVE across play, pause, seek, and track next │
│ 3. On Service onDestroy() or Player release():                        │
│    └─► Broadcast ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION             │
└────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Broadcast Intent Construction
```kotlin
private fun broadcastAudioEffect(action: String, audioSessionId: Int) {
    if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId == 0) return
    val intent = Intent(action).apply {
        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
        putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
    }
    sendBroadcast(intent)
}
```

### 6.3 System Control Panel Launcher (`ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL`)
In Compose UI surfaces ([`NowPlayingSheet.kt`](../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/NowPlayingSheet.kt) menu and [`SettingsScreen.kt`](../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/settings/SettingsScreen.kt)):
- An "Equalizer" action triggers:
  ```kotlin
  val intent = Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
      putExtra(AudioEffect.EXTRA_AUDIO_SESSION, currentAudioSessionId)
      putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
  }
  ```
- **Graceful Absence Guard:** Because pure AOSP, emulators, and custom ROMs frequently lack a system equalizer panel, the launcher is wrapped in `try { ... } catch (e: ActivityNotFoundException) { ... }`. Upon failure, a user-friendly Snackbar is presented: *"No equalizer application found on this device."*

### 6.4 Firm Rejection of Custom In-App Equalizer DSP
M5-C delegates audio effects strictly to external system/OEM panels. Embedding a custom in-app equalizer (e.g. `android.media.audiofx.Equalizer` sliders or custom biquad IIR filters) is **firmly rejected**:
- Custom filter implementations introduce phase distortion, clip headroom, and latency jitter.
- Device vendor implementations of `android.media.audiofx.Equalizer` are notoriously buggy across OEM builds.
- Power and battery efficiency are maximized by allowing hardware-accelerated platform DSPs to manage equalisation.
- If no system panel exists, Harmonia transparently reports its absence and continues pure, bit-accurate playback.

---

## 7. Stage M5-D: Coroutine Sleep Timer & Volume Preservation

### 7.1 Lifecycle & Ownership
The sleep timer is owned by [`HarmoniaPlayerController`](../harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt) and operates through a dedicated coroutine job scoped to the controller's lifecycle.

```
┌─────────────────────────────────────────────────────────────┐
│                    SleepTimerState                          │
├─────────────────────────────────────────────────────────────┤
│  isActive: Boolean = false                                  │
│  remainingDurationMs: Long = 0L                             │
│  targetDurationMs: Long = 0L                                │
│  mode: SleepTimerMode (DURATION_MINUTES | END_OF_TRACK)     │
│  baselineVolume: Float = 1.0f                               │
└─────────────────────────────────────────────────────────────┘
```

### 7.2 Unified 30-Second Fade-Out Window
All fade-out calculations throughout the application are standardized to **exactly 30 seconds** (`FADE_WINDOW_MS = 30_000L`):
- If the remaining timer duration is greater than 30 seconds, playback remains at 100% of `baselineVolume`.
- When remaining duration reaches $\le 30\,000\,\text{ms}$, the linear attenuation window begins.
- If the user selects a duration shorter than 30 seconds, the entire duration serves as the fade window.

### 7.3 Lifecycle-Safe Volume Preservation Contract
**Core Invariant: Player software volume must NEVER be unconditionally reset to `1.0f`.**

1. **Pre-Fade Baseline Volume Capture:**
   - Before attenuation begins (at $t_{\text{remaining}} = 30\,000\,\text{ms}$), the engine captures:
     $$\text{baselineVolume} = \text{player.volume}$$
2. **User Volume Tracking During Active Timer:**
   - If the user manually adjusts software volume during the timer countdown or while fade-out is actively progressing, the new user setting immediately becomes the new `baselineVolume`.
3. **Absolute Ratio Attenuation Formula:**
   - Attenuation is **NEVER** calculated by repeatedly multiplying the current volume (which would compound errors and destroy the baseline).
   - Attenuation at time $t$ is calculated strictly as an absolute ratio of `baselineVolume`:
     $$\text{player.volume}(t) = \text{baselineVolume} \times \max\left(0.0f, \frac{t_{\text{remaining}}}{30\,000\,\text{ms}}\right)$$
4. **Volume Restoration on Expiry / Pause:**
   - When the countdown reaches zero, the controller triggers `player.pause()`, deactivates the timer, and immediately restores:
     $$\text{player.volume} = \text{baselineVolume}$$
   - This ensures that subsequent manual playback starts at the user's expected volume rather than remaining muted at 0.0f.
5. **Cancellation & Replacement Restoration:**
   - If the user explicitly cancels the timer or selects a new duration while a fade is active, `player.volume` is immediately restored to `baselineVolume` before the new timer is armed.

### 7.4 End-of-Current-Track Mode Contract
When `mode == SleepTimerMode.END_OF_TRACK`:
1. **Fade Initiation:**
   - The fade window begins when the current track position reaches $\text{duration} - 30\,000\,\text{ms}$.
   - If track duration is $< 30$ seconds, the fade window covers the final 10 seconds of playback.
2. **Repeat Mode Override:**
   - If repeat mode is enabled (`REPEAT_MODE_ONE` or `REPEAT_MODE_ALL`), the sleep timer explicitly **overrides** repeat mode for that track. Once the current track completes (`STATE_ENDED` or position reaching duration), playback is paused and does not repeat.
3. **Manual Track Skipping:**
   - If the user manually skips to another track (Next / Previous) while End-of-Track mode is active, the timer re-binds to the newly active track's duration and immediately restores volume to `baselineVolume`.

### 7.5 Explicit Interruption & Pause Policy
**Authoritative Policy: Countdown Clock Suspension on Playback Pause**
1. **Transient Interruption / Manual Pause:**
   - If playback is paused (manual user pause, transient audio-focus loss, incoming phone call), the sleep timer countdown is **suspended**.
   - The countdown clock does not tick down while audio is paused.
   - If paused during an active fade, volume is restored to `baselineVolume` during the pause. Upon playback resumption, the fade resumes from the remaining duration.
2. **Playback Termination / Service Destruction:**
   - If the playback service is destroyed or the player is released, the coroutine scope is cancelled and the timer clears.
3. **Storage Disconnection:**
   - If the underlying media volume is unmounted during playback, storage disconnection handling pauses playback immediately and **cancels** the sleep timer, restoring volume to `baselineVolume`.

### 7.6 Overlapping / Stale Coroutine Prevention
- Single-job concurrency is enforced via a private atomic `Job?` reference:
  ```kotlin
  private var timerJob: Job? = null

  fun setSleepTimer(...) {
      timerJob?.cancel() // Deterministically abort any active countdown/fade
      timerJob = scope.launch { ... }
  }
  ```

### 7.7 Testable Clock & Coroutine Dispatcher
To guarantee fast, deterministic unit test execution without thread sleeps:
- The sleep timer engine accepts an injectable `CoroutineDispatcher` (defaults to `Dispatchers.Default`, injected with `StandardTestDispatcher` in tests).
- Time queries are driven by an injectable `TimeProvider` / `Clock` interface, enabling tests to advance virtual time deterministically using `advanceTimeBy(30_000L)`.

---

## 8. Roadmap Placement of Favorites & Ratings (Decision Awaiting Approval)

### 8.1 Requirements Traceability
`docs/MOBILE_M4_RESEARCH.md:§6` identified Favorites and User Ratings (1–5 stars / heart flags) as candidate M5 features.

### 8.2 Architectural Trade-Off Analysis

```
┌────────────────────────────────────────────────────────────────────────┐
│                    FAVORITES & RATINGS TRADE-OFF                       │
├───────────────────────────────────┬────────────────────────────────────┤
│ Option A: Incorporate in M5       │ Option B: Dedicated Milestone      │
│ (Bounded Stage M5-E)              │ (Personalization Milestone)        │
├───────────────────────────────────┼────────────────────────────────────┤
│ • Adds is_favorite & rating to    │ • Preserves Room v4 schema         │
│   mobile_tracks                   │   without any database migration   │
│ • Requires Room schema migration  │ • Focuses M5 exclusively on        │
│   v4 -> v5 (MIGRATION_4_5)        │   MediaLibraryService & audio      │
│ • Implements local UI heart icon  │ • Aligns ratings directly with     │
│   in Now Playing & Library        │   future personalization needs     │
│ • Adds "Favorites" Auto tab       │ • Zero SQLite migration risk       │
└───────────────────────────────────┴────────────────────────────────────┘
```

### 8.3 Recommended Proposal (Awaiting Human Approval)
**Recommendation:** **Option B (Separate Personalization Milestone).**
- *Rationale:* Milestone M5's core mission is **automotive integration and platform audio ergonomics**. Introducing a Room database migration (`MIGRATION_4_5`) creates unnecessary schema risk that is independent of `MediaLibraryService`. Furthermore, favorites and ratings should not be conflated with M6 LocalSync (which handles network synchronization). Placing them in a dedicated Personalization milestone keeps M5 tightly scoped and verified.
- *Status:* **DECISION AWAITING HUMAN APPROVAL**. Room schema remains strictly at version 4.

---

## 9. Database Schema v4 Stability

Room schema version 4 (`MIGRATION_3_4`) remains the active, authoritative database schema for all M5 stages.
- No new columns, tables, indices, or type converters are added.
- In-memory models and domain mappings represent media item metadata for Android Auto dynamically without persisting new entity fields.
- Any future schema migration requires formal architectural justification and separate approval.

---

## 10. Explicit Non-Goals & Boundaries for Milestone M5

The following features are strictly out of scope for Milestone M5:

| Non-Goal | Target Milestone | Rationale |
| :--- | :--- | :--- |
| **Custom Graphic Equalizer DSP** | Strictly Excluded | Custom 10-band IIR biquad filters introduce device distortion, phase artifacts, and high battery consumption. Delegation to platform panels is robust and standard. |
| **ReplayGain / Loudness Normalization** | Audiophile Hardening Milestone | Custom floating-point `AudioProcessor` pipelines belong to a future specialized DSP milestone. |
| **LocalSync P2P Transfer** | Milestone M6 | mDNS discovery, TLS pairing, and delta catalog synchronization belong to M6. |
| **Tag Write-Back / File Renaming** | Permanently Rejected | Read-only media mandate. |
| **Cloud Streaming / Remote Server** | Permanently Rejected | Offline-first mandate. |

---

## 11. Component Flow Diagram & Architecture Topology

```
                 ┌────────────────────────────────────────────────────────┐
                 │          Android Auto / Automotive Head Unit           │
                 │              (External MediaBrowser)                   │
                 └───────────────────────────┬────────────────────────────┘
                                             │
                       MediaLibraryService   │
                       IPC (Binder)          │ getChildren / onSearch /
                                             │ onAddMediaItems / onSetMediaUri
                                             ▼
                 ┌────────────────────────────────────────────────────────┐
                 │                HarmoniaPlaybackService                 │
                 │             (extends MediaLibraryService)              │
                 ├────────────────────────────────────────────────────────┤
                 │  MediaLibrarySession                                   │
                 │  HarmoniaLibrarySessionCallback                        │
                 │    │                                                   │
                 │    ├───► MediaItemTreeResolver                         │
                 │    │       (Offloaded to Dispatchers.IO)               │
                 │    │       ├───► TrackRepository / LocalFileRepository │
                 │    │       ├───► AlbumRepository / ArtistRepository    │
                 │    │       └───► SearchRepository / PlaylistRepository │
                 │    │                                                   │
                 │    └───► AudioEffect Session Broadcast                 │
                 │            ├───► ACTION_OPEN_AUDIO_EFFECT_SESSION      │
                 │            └───► ACTION_CLOSE_AUDIO_EFFECT_SESSION     │
                 └───────────────────────────┬────────────────────────────┘
                                             │
                             authoritative   │
                             ExoPlayer       │
                                             ▼
                 ┌────────────────────────────────────────────────────────┐
                 │           HarmoniaPlayerController (Domain)            │
                 │  • In-Memory Authoritative Queue                       │
                 │  • SleepTimerController (30s fade & volume recovery)   │
                 │  • StorageVolumeLifecycleCoordinator Listener          │
                 │  • PlaybackStateRepository & ListeningEventRepository  │
                 └───────────────────────────┬────────────────────────────┘
                                             │
                                             ▼
                 ┌────────────────────────────────────────────────────────┐
                 │                Mobile Jetpack Compose UI               │
                 │  • NowPlayingSheet (Equalizer Action / Sleep Timer)    │
                 │  • SettingsScreen (Equalizer Action / M5 Label)        │
                 └────────────────────────────────────────────────────────┘
```

---

## 12. Performance, Threading & Failure-Handling Invariants

1. **Main Thread Safety:** Zero Room database I/O is permitted on the Android main looper during `MediaLibrarySession.Callback` execution. All tree node resolutions execute on `Dispatchers.IO`.
2. **Bounded Pagination:** Android Auto root tabs and child collections return bounded lists with maximum 50 items per page by default to prevent head-unit memory exhaustion.
3. **Graceful Empty State:** If the local catalog is unindexed or permissions have not been granted, `onGetChildren` returns an empty collection rather than throwing exceptions or hanging.
4. **Equalizer Fallback:** Absence of an installed equalizer app must never crash the UI or interrupt active playback.
5. **WakeLock & Service Lifecycle:** The foreground service retains `WAKE_MODE_LOCAL` during active playback. Sleep timer volume attenuation does not release the wake lock until playback is fully paused.

---

## 13. Implementation Stages & Independent Review Gates

Milestone M5 will be executed across 4 sequential, individually verifiable stages. **Each stage requires an independent code review and explicit human approval before the next stage begins:**

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      MILESTONE M5 EXECUTION STAGES                      │
├────────────────────┬────────────────────────────────────────────────────┤
│ Stage M5-A         │ MediaLibraryService Migration & Manifest Car Setup │
│                    │ • HarmoniaPlaybackService extends MediaLibrarySvc  │
│                    │ • automotive_app_desc.xml + Manifest intent-filters│
│                    │ • Root navigation nodes (Albums, Artists, etc.)    │
│                    │ • Review Gate: MediaBrowser connection test passes │
├────────────────────┼────────────────────────────────────────────────────┤
│ Stage M5-B         │ Library Tree Resolution, Pagination & Auto Search  │
│                    │ • Child node queries for Albums, Artists, Playlists│
│                    │ • Content style hints (Grid vs List)               │
│                    │ • onSearch() wired to SearchRepository             │
│                    │ • onAddMediaItems() external playback resolution   │
│                    │ • Review Gate: Browsing, search & resolution pass  │
├────────────────────┼────────────────────────────────────────────────────┤
│ Stage M5-C         │ Platform Equalizer & Audio Effects Integration     │
│                    │ • Audio session ID capture from ExoPlayer          │
│                    │ • ACTION_OPEN/CLOSE_AUDIO_EFFECT broadcast intents │
│                    │ • UI launch hook with ActivityNotFoundException    │
│                    │ • Strict exclusion of custom in-app DSP            │
│                    │ • Review Gate: Broadcast lifecycle & UI fallback   │
├────────────────────┼────────────────────────────────────────────────────┤
│ Stage M5-D         │ Sleep Timer, Fade-Out & Milestone Consolidation    │
│                    │ • Coroutine-backed countdown in controller         │
│                    │ • 30-second linear software volume fade-out        │
│                    │ • Baseline volume capture and restoration contract │
│                    │ • Compose dialog/sheet in Now Playing              │
│                    │ • Review Gate: Full 5-tier regression & freeze tag │
└────────────────────┴────────────────────────────────────────────────────┘
```

---

## 14. Objective Behavioral Acceptance Criteria

Acceptance criteria are evaluated strictly by observable behavioral contracts:

1. **Automotive Root Discovery Contract (Stage M5-A):**
   An external `MediaBrowser` connected to `HarmoniaPlaybackService` receives a valid `LibraryResult` containing root node `"root"` with `FLAG_BROWSABLE = true, FLAG_PLAYABLE = false` and 4 primary navigation tabs (`root_albums`, `root_artists`, `root_playlists`, `root_tracks`).
2. **Automotive Item Browsing & Playback Contract (Stage M5-B):**
   Requesting `getChildren("root_albums")` returns indexed albums styled as grid items; selecting an album returns its tracks; selecting a track resolves a playable `MediaItem` and initiates audio output.
3. **Voice & Assistant Search Contract (Stage M5-B):**
   Invoking `search(query = "...")` on the media library session returns matching tracks, albums, and artists within 500ms without ANR.
4. **Equalizer Lifecycle Contract (Stage M5-C):**
   Allocating an `audioSessionId` broadcasts `AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION`; pausing playback preserves the session; destroying the playback service broadcasts `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION`; tapping Equalizer without an installed EQ app triggers a non-intrusive warning notice without crash.
5. **Sleep Timer Volume Preservation Contract (Stage M5-D):**
   - Sleep timer countdown attenuates volume smoothly over exactly 30 seconds.
   - Pausing countdown restores volume to the user's pre-fade baseline volume (`baselineVolume`).
   - If the user changes volume during the timer, the new volume becomes the baseline without compounding attenuation.
   - Transient playback interruptions suspend the countdown rather than abandoning it.
   - Timer expiry transitions state to `PAUSED` and restores baseline volume.
6. **M4-F Invariant Protection Contract (All Stages):**
   - Detached storage marks files unavailable without deleting catalog records.
   - Reconnecting storage does not auto-resume playback.
   - Single audio focus authority is maintained across phone calls and noisy unplug events.
   - Full 5-tier regression suite passes cleanly (JVM unit suite, connected instrumented suite, assembleDebug, Python core regression suite, and `git diff --check`).

---

## 15. Estimated File Change Areas

*Note: Architectural planning estimates; zero source code modified in this stage.*

### New Files to Create:
1. `harmonia-mobile/app/src/main/res/xml/automotive_app_desc.xml`
2. `harmonia-mobile/app/src/main/java/org/harmonia/player/service/tree/MediaItemTreeResolver.kt`
3. `harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/audio/SleepTimerState.kt`
4. Test files:
   - `harmonia-mobile/app/src/test/java/org/harmonia/player/service/tree/MediaItemTreeResolverTest.kt`
   - `harmonia-mobile/app/src/test/java/org/harmonia/player/domain/model/audio/SleepTimerTest.kt`
   - `harmonia-mobile/app/src/androidTest/java/org/harmonia/player/HarmoniaMediaLibraryServiceTest.kt`

### Existing Files to Modify:
1. `harmonia-mobile/app/src/main/AndroidManifest.xml`
2. `harmonia-mobile/app/src/main/java/org/harmonia/player/service/HarmoniaPlaybackService.kt`
3. `harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt`
4. `harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/PlaybackState.kt`
5. `harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/NowPlayingSheet.kt`
6. `harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/settings/SettingsScreen.kt`

---

## 16. Scope Freeze Summary & Open Decisions Requiring Human Input

Before code execution begins, the following decisions are frozen or pending approval:

| Topic | Status | Proposed Resolution |
| :--- | :--- | :--- |
| **Stage Breakdown (M5-A to M5-D)** | **Ready for Freeze** | 4 sequential stages with independent review gates. |
| **System Equalizer Only** | **Ready for Freeze** | No custom in-app DSP or ReplayGain. |
| **Sleep Timer Contract** | **Ready for Freeze** | 30s linear fade, baseline volume capture, suspension on pause. |
| **Room Schema v4** | **Ready for Freeze** | Schema v4 preserved without migrations. |
| **Favorites & Ratings Placement** | **Awaiting Human Input** | Recommended: Dedicated Personalization milestone. Alternative: M5-E (requires `MIGRATION_4_5`). |
| **Stage M5-A Authorization** | **Awaiting Human Input** | Awaiting explicit user command to begin Stage M5-A implementation. |
