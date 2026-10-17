# Harmonia Research: Playback Architecture Comparison

**Document Classification:** Architectural Research Specification  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Mobile Media3, desktop playback engines, gapless transitions, audio focus, queue semantics, and background playback lifecycle across 20 media ecosystems.

---

## 1. Playback Architecture Landscape

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                             PLAYBACK ENGINE TAXONOMY                                        │
├──────────────┬──────────────────┬─────────────────┬─────────────────────────────────────────┤
│ Platform     │ Engine / API     │ Audio Output    │ Exemplary Projects Inspected            │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Android      │ AndroidX Media3  │ AudioTrack /    │ LocalTracklist, Auxio, Booming,         │
│              │ (ExoPlayer)      │ Oboe / AAudio   │ Resonate, Aurora, decent-player         │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Multi/Mobile │ Flutter          │ AudioTrack /    │ Flick, Finamp, Isai                     │
│              │ (just_audio)     │ AVQueuePlayer   │                                         │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Desktop C++  │ Custom Pipeline  │ WASAPI / ALSA / │ fooyin (FFmpeg/SoX),                    │
│              │ / GStreamer      │ PipeWire / JACK │ Strawberry (GStreamer)                  │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Desktop Py   │ SDL2 / Phazor    │ Pulse / PipeWire│ Tauon Music Box                         │
│              │ / mpv backend    │ / WASAPI        │                                         │
└──────────────┴──────────────────┴─────────────────┴─────────────────────────────────────────┘
```

---

## 2. Mobile Playback: Android Media3 & Background Resilience

On modern Android (API 26 to 36), high-fidelity background playback is governed strictly by the **AndroidX Media3** ecosystem (`androidx.media3.exoplayer` and `androidx.media3.session`).

### 2.1 MediaSessionService & Foreground Lifecycle
Inspected across [LocalTracklist](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/LocalTracklist-master/app/src/main/java/com/libreplayer/media/service/PlaybackService.kt), [Auxio](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Auxio-dev/app/src/main/java/org/oxycblt/auxio/AuxioService.kt), and [Booming Music](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/BoomingMusic-master/app/src/main/java/com/mardous/booming/playback/PlaybackService.kt):

1. **Service Type:** Implements `MediaSessionService` with manifest permission `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.
2. **Foreground Transition:** 
   - When playback starts, service enters foreground with a media-style notification showing album art, track details, play/pause, seek bar, and skip buttons.
   - When playback pauses, service stays alive in the background for a grace period (e.g. 10 minutes), then drops foreground status and releases `WAKE_LOCK` to conserve battery.
3. **Audio Focus Handling:**
   - Handled natively via `AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).setUsage(C.USAGE_MEDIA).build()`.
   - On transient loss (incoming phone call / navigation voice prompt): pauses or ducks volume automatically.
   - On `ACTION_AUDIO_BECOMING_NOISY` (wired headphones unplugged or Bluetooth disconnect): immediately pauses playback to prevent embarrassing public loudspeaker blasting.
4. **Hardware Media Buttons & Bluetooth AVRCP:**
   - Media3 automatically registers `MediaSession` callbacks handling Bluetooth headset clicks:
     - Single click: Play / Pause
     - Double click: Skip Next
     - Triple click: Skip Previous / Restart Track

---

### 2.2 Gapless Playback & Container Padding

Gapless playback is the seamless concatenation of consecutive tracks without audible pauses, clicks, or ticks (critical for classical symphonies, concept albums like Pink Floyd's *The Dark Side of the Moon*, and live DJ sets).

#### How Gapless Fails:
Lossy codecs (MP3, AAC) do not store exact audio sample counts; they compress audio in fixed-size frames (1152 samples per frame for MP3). This creates artificial padding silence at the beginning (encoder delay) and end (encoder padding).

#### The Verified Solution in Media3 & fooyin:
1. **LAME & iTunSMPB Extraction:** Read `encoderDelay` and `encoderPadding` from the LAME MP3 header or MP4 `iTunSMPB` tag atom.
2. **ExoPlayer Gapless Trimming:** ExoPlayer's `MediaCodecAudioRenderer` silently discards the exact leading delay samples and trailing padding samples from the decoded PCM stream.
3. **Flac / Lossless Audio:** FLAC explicitly stores `total_samples` in the `STREAMINFO` block; gapless transitions require zero trimming, but require the player to pre-buffer the subsequent track's decoder before the current track finishes.

---

### 2.3 Crossfade & Transition Algorithms

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CROSSFADE MODES COMPARED                        │
├────────────────────┬───────────────────────────────────────────────────┤
│ Mode               │ Implementation & Trade-offs                       │
├────────────────────┼───────────────────────────────────────────────────┤
│ 1. Linear Crossfade│ $V_{\text{out}} = 1 - t/T, \quad V_{\text{in}} =  │
│                    │ t/T$. Audible dip in perceived loudness at center │
│                    │ ($t = T/2$). Common in naive players.             │
├────────────────────┼───────────────────────────────────────────────────┤
│ 2. Equal-Power     │ $V_{\text{out}} = \cos(\frac{\pi t}{2T}), \quad   │
│    (Recommended)   │ V_{\text{in}} = \sin(\frac{\pi t}{2T})$. Sum of   │
│                    │ acoustic power is constant: $V_1^2 + V_2^2 = 1$.  │
│                    │ Smooth, professional transition.                  │
├────────────────────┼───────────────────────────────────────────────────┤
│ 3. Beat-Matched    │ [Aurora MixPlayer]: Computes BPM, aligns beat     │
│    Intelligent DJ  │ phases, pitch-shifts, and crossfades on dynamic   │
│                    │ 8-bar boundaries. High CPU overhead.              │
└────────────────────┴───────────────────────────────────────────────────┘
```

#### Harmonia's Silence Skipping Integration
In Phase 3, Harmonia Core's `FFmpegAudioAnalyzer` computes:
- `silence_start_sec`: Timestamp where audio first exceeds $-60$ dBFS.
- `silence_end_sec`: Timestamp where audio drops below $-60$ dBFS before EOF.

**Harmonia Playback Innovation:** The playback engine can use these objective database fields to initiate early crossfades or skip dead silence at track boundaries without requiring real-time silence detection algorithms!

---

### 2.4 Queue Management & Persistence

A frequent user complaint in music players is lost queue state on app reboot, or erratic track insertion when shuffling.

#### 1. Non-Scrambling "Play Next" ([Auxio `BetterShuffleOrder`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Auxio-dev/app/src/main/java/org/oxycblt/auxio/playback/service/BetterShuffleOrder.kt#L68)) `[SOURCE VERIFIED]`
- When user clicks **"Play Next"** on track $K$, default ExoPlayer randomly scrambles $K$ somewhere in the shuffled array.
- `BetterShuffleOrder` maintains a pointer to current index `pivot` in the shuffled permutation, shifting all elements $> \text{pivot}$ to make room and inserting $K$ at `pivot + 1`.

#### 2. Persistent Playback State Snapshot ([LocalTracklist `PlaybackSnapshotStore`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/LocalTracklist-master/app/src/main/java/com/libreplayer/media/playback/PlaybackSnapshotStore.kt)) `[SOURCE VERIFIED]`
- Uses Jetpack DataStore / Room to record:
  - `active_song_id: UUID`
  - `active_seek_position_ms: Long`
  - `queue_song_ids: List<UUID>`
  - `shuffle_mode: Boolean`
  - `repeat_mode: Enum`
- On app launch, the queue and seek position are restored instantly in paused state. Zero delay, zero audio playback hitch.

#### 3. Long-Form Media Resume & Bookmarks ([Isai](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Isai-main/lib/features/audiobooks)) `[SOURCE VERIFIED]`
- For tracks with duration $>15$ minutes (audiobooks, podcast episodes, DJ sets):
  - Stores exact seek position in `resume_positions` table.
  - Variable speed playback ($0.5\times$ to $2.5\times$) with pitch correction (time-stretching via `SonicAudioProcessor`).
  - Chapter marker parsing from ID3v2 `CHAP` frames or embedded CUE sheets.

---

## 3. Desktop Playback Engine Comparison

For Harmonia Desktop, two primary architectures were evaluated:

### 1. Embedded `mpv` (`libmpv`)
- **Pros:** Plays every container, format, and codec natively without external codecs; supports bit-perfect WASAPI exclusive / PipeWire / ALSA output; built-in gapless and equalizer.
- **Cons:** External C library binary dependency.

### 2. Native HTML5 Audio via HTTP 206 Streaming (Harmonia Web/Desktop UI)
- **Status:** Already implemented in Harmonia Phase 3 (`/api/v1/stream/{file_id}`).
- **Pros:** Zero client-side dependencies; instant UI playback across browsers, desktop Electron/Tauri shells, and mobile web.
- **Cons:** Browser HTML5 audio lacks bit-perfect USB DAC output, native ReplayGain hook, and fine-grained PCM buffer control.

### Harmonia Dual-Tier Desktop Recommendation:
- **Tier 1 (Default / Universal):** Keep native HTML5 streaming for the embedded Desktop Web UI.
- **Tier 2 (Audiophile Dedicated Console):** Add an optional `libmpv` desktop audio engine adapter (Phase 4) for users requiring bit-perfect hardware output, WASAPI exclusive mode, and low-latency ASIO/PipeWire routing.
