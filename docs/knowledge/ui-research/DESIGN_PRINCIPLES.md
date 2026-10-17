# Harmonia Mobile — Design Principles & Visual Identity Framework

**Document Classification:** Architectural Design Specification  
**Status:** COMPLETE & PROPOSED (Stage 5A)  
**Author:** Principal Mobile UX Researcher & Android UI Architect for Harmonia  
**Repository Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  

---

## 1. Identity Evaluation & Research Hypothesis

Harmonia’s established brand identity is conceived as a **dark, sovereign, audiophile-inspired architectural instrument**.

### 1.1 Aesthetic Evaluation: Instrument vs. Jukebox
- **The Jukebox Anti-Pattern (Spotify/YouTube Music):** Modern streaming apps are designed as social feeds and engagement funnels. They bombard users with algorithmic banners, dynamic canvas video loops, sponsored podcasts, and intrusive popups.
- **The Instrument Archetype (Harmonia Hypothesis):** A musical instrument or high-end mastering console (e.g. Nagra, Teenage Engineering, Braun audio systems) does not clamor for attention. It exudes precision, structural calm, tactile physical feedback, and unadorned truth to the audio material.

### 1.2 Color & Form Evaluation
- **Obsidian Baseline (`ObsidianSpace #090B10`):** An almost pitch-black canvas that blends seamlessly into modern AMOLED displays, reducing eye fatigue and battery power during long listening sessions.
- **Acoustic Teal Accent (`AcousticTeal #00D2B4`):** Represents electrical audio signal, frequency response peaks, and precision calibration. At **10.2:1** contrast on Obsidian, it delivers effortless legibility without blinding luminance.
- **Harmonic Violet Secondary (`HarmonicViolet #6E56CF`):** Adds harmonic warmth for secondary personal touches (user playlists, custom tags, favorite badges).
- **Hairline Architectural Borders (`BorderSlate #2A3042`):** Rather than muddy drop-shadows or skeuomorphic bevels, 1dp hairline borders define tactile physical cards and enclosures.

---

## 2. Core Harmonia UX Principles

### Principle 1: Audio Sovereignty & Read-Only Safety
- **User Need:** Listeners need absolute peace of mind that their carefully curated local files will never be corrupted, retagged, renamed, or accidentally deleted by the app.
- **Evidence:** `[VERIFIED-SRC]` Harmonia’s backend and mobile architecture enforce strict read-only media policies (`ADR-025`, `ADR-026`). Every file operation is non-destructive.
- **How to Evaluate:** Verify that zero UI screens contain destructive "Delete from Device" or "Edit File Tag" buttons. All user metadata (Favorites, Ratings, Playlists) lives purely in Harmonia's isolated Room SQLite database.

---

### Principle 2: Artwork as the Primary Visual Anchor
- **User Need:** For personal music collectors, album artwork is an emotional and aesthetic gateway to an era, memory, or artist.
- **Evidence:** `[OBSERVED-REF]` Poweramp and Oto Music demonstrate that dominant, uncropped square album artwork elevates user engagement far more than text lists alone.
- **How to Evaluate:**
  - In `NowPlayingSheet`, the album art occupies at least 65% of the horizontal viewport width with rounded architectural corners (16–20dp).
  - When artwork is missing, display a tailored, sandboxed geometric placeholder (`HarmoniaArtwork`) rather than a blank grey box.
  - Avoid intrusive gradient overlays or text superimposed directly over the album cover.

---

### Principle 3: Tactile & Predictable Playback Controls
- **User Need:** Transport controls (Play/Pause, Next, Previous, Seekbar) are the most frequently touched surfaces on the device. They must react instantly, reliably, and without cognitive pause.
- **Evidence:** `[VERIFIED-SRC]` `HarmoniaPlaybackService` and `ExoPlayer` deliver zero-latency playback. Current text buttons (`SHF`/`RPT`) in `NowPlayingSheet` introduce cognitive friction compared to standard vector icons.
- **How to Evaluate:**
  - Transport buttons must use standardized, stateful Material 3 vector icons (`HarmoniaIcons.Play`, `Pause`, `SkipNext`, `SkipPrevious`, `Shuffle`, `Repeat`, `RepeatOne`).
  - Active states (Shuffle on, Repeat on) must visually glow with `AcousticTeal` and carry dynamic TalkBack announcements.
  - The Play/Pause button must remain the undisputed center of gravity (64dp target).

---

### Principle 4: One-Handed Reachability & Low Interaction Cost
- **User Need:** Handset users frequently operate their music player with a single thumb while walking, commuting, or cooking. Reaching for the top of a 6.7-inch screen causes drops and user frustration.
- **Evidence:** `[VERIFIED-DOC]` Google Material 3 navigation guidelines recommend placing all high-frequency primary controls in the bottom 60% of the display.
- **How to Evaluate:**
  - Top-level navigation tabs sit at the bottom (`NavigationBar`).
  - The persistent `MiniPlayer` is docked immediately above the bottom navigation bar.
  - In `NowPlayingSheet`, the seekbar, volume indicators, transport cluster, and queue drawer toggles are situated in the lower half of the modal canvas.

---

### Principle 5: Uncluttered Technical Depth (The Audio Inspector)
- **User Need:** Audiophiles and serious collectors care deeply about audio fidelity, bit depth, sampling rates, and output routes. However, displaying raw telemetry permanently on screen clutters the UI for relaxed listening.
- **Evidence:** `[OBSERVED-REF]` LocalTracklist's dedicated `AudioDetailsScreen` proves that technical depth is best delivered via an on-demand modal sheet rather than permanently cluttering the primary playback screen.
- **How to Evaluate:**
  - Display a subtle, non-intrusive format chip on Now Playing (e.g., `FLAC · 24-bit / 96 kHz` or `MP3 · 320 kbps`).
  - Tapping the format chip opens an **Audio Signal Inspector** modal displaying container format, codec, bitrate, sample rate, bit depth, channel configuration, and active hardware AudioTrack sink.

---

### Principle 6: High Performance with Large Local Catalogs
- **User Need:** Local music libraries range from 500 tracks to 50,000+ tracks. Scrolling through thousands of artists or songs must never drop frames or induce thumb fatigue.
- **Evidence:** `[OBSERVED-REF]` Resonate’s `FastScrollRail` demonstrates that a slender vertical alphabetical rail with haptic ticks allows jumping to letter "S" in 200ms across 10,000 items.
- **How to Evaluate:**
  - Lazy lists (`LazyColumn`, `LazyVerticalGrid`) must maintain stable keys (`track.id`, `album.id`).
  - Implement `FastScrollRail` on Tracks and Artists tabs with distinct haptic feedback ticks during dragging.
  - In-flight search queries must debounce input and execute indexed Room queries in under 16ms.

---

### Principle 7: State Honesty & Graceful Degradation
- **User Need:** When an SD card is unmounted, permissions are revoked, or media scans fail, the user must immediately understand *what* happened, *why* it happened, and *how* to resolve it without cryptic crash dialogs.
- **Evidence:** `[VERIFIED-SRC]` Harmonia's existing `PlaybackState.STORAGE_DISCONNECTED_MESSAGE` and `TrackRowCard` dimming already lead the industry in storage honesty.
- **How to Evaluate:**
  - If a storage volume is unmounted, affected tracks must be visibly dimmed with an explicit badge ("Storage Unmounted"), and Now Playing must present a non-fatal banner explaining that reconnecting storage will restore playback.
  - Permission states must present educational rationale before linking to system settings.
  - Empty library states must guide the user directly to folder addition in Settings.

---

### Principle 8: Accessibility, High Contrast & Reduced Motion
- **User Need:** Every music lover, regardless of visual, motor, or cognitive ability, deserves full access to their music library.
- **Evidence:** `[VERIFIED-DOC]` Material 3 and WCAG 2.1 AA require 4.5:1 contrast for regular text, 3.0:1 for large text/icons, and minimum 48dp touch targets.
- **How to Evaluate:**
  - All text colors must pass WCAG AA against their background containers (Harmonia’s `PureSignalWhite` on `ObsidianSpace` achieves **16.8:1**; `AcousticTeal` achieves **10.2:1**).
  - Every interactive element (even 24dp icons) must have an invisible bounding box of at least **48 × 48 dp**.
  - Respect Android system `prefers-reduced-motion` settings by bypassing animated sheet transitions and bouncy physics.

---

### Principle 9: The Dark-First Theme Strategy (and Light Theme Evaluation)
- **User Need:** Music players are heavily used at night, during commutes, and in dimmed environments. Dark mode is the primary operating environment.
- **Evaluation of Light Theme (`[PROPOSED]`):**
  - *Recommendation:* Harmonia is designed as a **dark-first application** (`ObsidianSpace` baseline).
  - *Light Theme Policy:* While an inverted high-contrast daytime theme (architectural slate grey `#F4F6F9`, dark charcoal text `#141721`, deep acoustic teal `#00838F`) can be specified for extreme outdoor sunlight readability, dark mode must remain the primary, default, and uncompromised expression of Harmonia’s identity.

---

## 3. Summary of Design Principles

| Principle | Primary Focus | Verification Method |
| :--- | :--- | :--- |
| **1. Audio Sovereignty** | Read-only media safety | Zero tag-edit or delete buttons in UI |
| **2. Artwork Anchor** | Uncropped, dominant focal point | Artwork occupies ≥ 65% width; sandboxed fallback |
| **3. Tactile Controls** | Predictable, zero-latency transport | 48dp minimum targets; stateful vector icons |
| **4. One-Handed Reach** | Thumb-zone optimization | Bottom 60% contains all primary playback actions |
| **5. Technical Depth** | Transparent audio telemetry | On-demand Audio Signal Inspector sheet |
| **6. Catalog Performance**| 10k+ track fluid navigation | FastScrollRail with haptic ticks; 60fps scrolling |
| **7. State Honesty** | Disconnected storage & error clarity | Explicit unmount badges and actionable banners |
| **8. Accessibility** | WCAG AA contrast & TalkBack | Strict 48dp touch bounds; 10:1+ primary contrast |
| **9. Dark-First Calm** | Architectural OLED serenity | Default obsidian canvas; daylight mode as secondary |
