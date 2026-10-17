# Harmonia Research: Mobile UX Comparison

**Document Classification:** Architectural Research Specification  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Navigation paradigms, mini-players, Now Playing architectures, dynamic theming, audio diagnostics UI, and Android Auto integration across inspected mobile players.

---

## 1. Mobile UX Archetypes Across Projects

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                              MOBILE UX PATTERNS MATRIX                                      │
├──────────────┬──────────────────┬─────────────────┬─────────────────────────────────────────┤
│ Project      │ Framework / UI   │ Navigation      │ Distinctive UX Feature                  │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Resonate     │ Jetpack Compose  │ Bottom Nav +    │ Material You Monet dynamic color with   │
│              │ Material 3       │ FastScrollRail  │ chroma clamping; OLED black mode        │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ LocalTracklist│ Jetpack Compose │ Bottom Chrome + │ Comprehensive Audio Details sheet;      │
│              │ Material 3       │ Tabbed Library  │ persistent mini-player; zero lag        │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Booming      │ Jetpack Compose  │ Bottom Nav +    │ Integrated AutoEQ profile picker;       │
│              │ Material 3       │ Custom Sheets   │ TTML karaoke-style synchronized lyrics  │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Auxio        │ Android Views /  │ Multi-level     │ Wavy slider seekbar; cover pager;       │
│              │ Material Components Bottom Sheet   │ seamless gesture drag from mini-player  │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ MusicHoarder │ React Native /   │ Floating Glass  │ Apple Music style glass tab bar; Now    │
│              │ Web PWA          │ Bar (iOS style) │ Playing with synchronized lyrics sheet  │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ Phoebe       │ Compose          │ Bottom Tabs /   │ Waveform seekbar; 31-band graphic EQ;   │
│              │ Multiplatform    │ Sidebar Desktop │ audio-reactive visualizers              │
└──────────────┴──────────────────┴─────────────────┴─────────────────────────────────────────┘
```

---

## 2. Core UI Components: Best-in-Class Findings

### 2.1 The Mini-Player & Now Playing Transition
Inspected across [Auxio](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Auxio-dev/app/src/main/java/org/oxycblt/auxio/playback/PlaybackBottomSheetBehavior.kt), [LocalTracklist](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/LocalTracklist-master/app/src/main/java/com/libreplayer/ui/screens/PlaybackScreens.kt), and [Resonate](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/resonate-main/app/src/main/kotlin/com/resonate/player/ui/components/MiniPlayer.kt):

- **The Mini-Player:**
  - Floats persistently above the bottom navigation bar.
  - Displays thumbnail artwork, track title, artist name, and a combined play/pause + next transport control.
  - Contains a subtle 2dp progress bar pinned to the top or bottom edge of the bar, showing seek progress without needing a full slider.
- **Gesture Expansion:**
  - Tapping or swiping upward expands into the full-screen **Now Playing** sheet.
  - Auxio uses custom `PlaybackBottomSheetBehavior` that smoothly interpolates alpha, scale, and corner radius between the compact mini-player card and the full-screen canvas.
- **Now Playing Layout:**
  - Dominant high-resolution square album cover with soft drop-shadow.
  - Track title and artist with one-tap favorite (heart) toggle.
  - Time elapsed, scrubbable seekbar (or waveform), time remaining.
  - Transport buttons: Shuffle, Skip Previous, Play/Pause (large center circle), Skip Next, Repeat.
  - Bottom auxiliary drawer toggles: **Lyrics Sheet**, **Queue (Up Next)**, and **Audio Signal Inspector**.

---

### 2.2 Synchronized Lyrics & Karaoke Display
Inspected in [Booming Music](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/BoomingMusic-master/app/src/main/java/com/mardous/booming/data/local/lyrics/ttml/TtmlLyricsParser.kt) and [Resonate](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/resonate-main/app/src/main/kotlin/com/resonate/player/ui/nowplaying/LyricsSheet.kt):

- **LRC Line Sync vs. TTML Word Sync:**
  - Line-level (LRC): Highlights current line in bold high-contrast text, auto-scrolling with smooth easing curve.
  - Word-level (TTML / Karaoke): Smoothly fills text color word-by-word or syllable-by-syllable in real-time as the singer articulates each sound.
- **Interactive Tapping:** Tapping any lyrics line immediately seeks playback to that exact timestamp.
- **Backdrop:** Full-screen blurred copy of current album artwork dimmed to 30% brightness, allowing text to remain 100% accessible and readable.

---

### 2.3 Large Library Navigation & Fast Scroll Rail
When browsing a local music library of 10,000+ tracks or 1,000+ artists on a phone, traditional vertical scrolling causes fatigue and lag.

#### The Solution in [Resonate `FastScrollRail.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/resonate-main/app/src/main/kotlin/com/resonate/player/ui/components/FastScrollRail.kt) `[SOURCE VERIFIED]`:
- Renders a slender vertical alphabet rail (`#`, `A`, `B`, ..., `Z`) along the right edge of the screen.
- Dragging a finger down the rail triggers haptic feedback ticks, instantly jumping the `LazyColumn` to that section's scroll index.
- A floating letter bubble appears beside the user's thumb to indicate the current alphabetical section.

---

### 2.4 Audio Details & Signal Diagnostics Sheet
In ordinary music players, technical details are either absent or buried in deep sub-menus.

#### The Solution in [LocalTracklist `AudioDetailsScreen.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/LocalTracklist-master/app/src/main/java/com/libreplayer/ui/screens/AudioDetailsScreen.kt) `[SOURCE VERIFIED]`:
One tap on the track menu opens an **Audio Details Modal** displaying:
- Container Format (`FLAC`, `MP3`, `ALAC`, `Opus`)
- Codec & Audio Profile
- Bitrate (kbps)
- Sample Rate (Hz / kHz)
- Bit Depth (16-bit, 24-bit, 32-bit float)
- Channel Layout (`Stereo`, `5.1 Surround`, `Mono`)
- File Size (MB) & Duration
- Active AudioTrack Output Sink telemetry (proves whether hardware is resampling or playing bit-perfect)

---

### 2.5 Dynamic Color & Theme Extraction
Inspected in [Resonate `Chroma.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/resonate-main/app/src/main/kotlin/com/resonate/player/ui/theme/Chroma.kt):
- Dynamically extracts dominant accent colors from the active album cover using Android's Material You Palette API.
- **Critical Safety Guard (`ChromaClampTest.kt`):** Naive palette extraction often yields dull gray or muddy brown on dark album covers, or blinding white on pale covers. Resonate clamps minimum saturation and sets strict luminance boundaries to guarantee:
  1. Minimum 4.5:1 WCAG contrast ratio for all text elements.
  2. Vibrant, aesthetically pleasing glow on the playback slider and mini-player background.
  3. True OLED pitch-black background option (`#000000`) to save battery on AMOLED screens.

---

### 2.6 Android Auto & Platform Integration
Inspected in [LocalTracklist](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/LocalTracklist-master/app/src/main/java/com/libreplayer/media/service/PlaybackService.kt) and [Finamp `android_auto_helper.dart`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/finamp-redesign/lib/services/android_auto_helper.dart):
- Exposes Media3 `MediaLibraryService` with standard root hierarchy:
  - `Playlists`, `Albums`, `Artists`, `Recently Added`, `Favorites`.
- Android Auto dashboard renders native car-optimized large touch targets without requiring custom UI code.
- Resumes car playback automatically when phone connects to automotive Bluetooth or USB head unit.

---

## 3. Harmonia Mobile UX Specification

Based on this empirical investigation, Harmonia Mobile should be structured as follows:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        HARMONIA MOBILE APP SHELL                       │
├────────────────────────────────────────────────────────────────────────┤
│ [TOP APP BAR] (Search Bar, Library Rescan Status, Settings Icon)       │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│                            MAIN CONTENT VIEW                           │
│ (Tabs: 1. Home / 2. Library / 3. Playlists / 4. Audio Console)         │
│                                                                        │
│ • Home: Heavy Rotation, Recently Added, Forgotten Favorites            │
│ • Library: Songs, Albums (Grid), Artists (A-Z FastScroll), Folders     │
│ • Playlists: User Playlists, Smart Playlists, M3U Imports              │
│ • Audio Console: AutoEQ Profiles, ReplayGain Toggle, DAC Status        │
│                                                                        │
├────────────────────────────────────────────────────────────────────────┤
│ [MINI-PLAYER] (Persistent 56dp card above nav bar; tap to expand)      │
├────────────────────────────────────────────────────────────────────────┤
│ [BOTTOM NAVIGATION BAR] (Home · Library · Playlists · Console)         │
└────────────────────────────────────────────────────────────────────────┘
```
