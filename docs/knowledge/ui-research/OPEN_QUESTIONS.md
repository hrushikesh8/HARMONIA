# Harmonia Mobile — Open Architecture, UX & Governance Questions (Stage 5B Update)

**Document Classification:** Architectural Decision & Governance Record  
**Status:** SPECIFICATION COMPLETE & PENDING STAKEHOLDER APPROVAL (Stage 5B Gate)  
**Author:** Principal Mobile Product Designer & Android UX Architect for Harmonia  
**Repository Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  

---

## 1. Executive Context & Evaluation Status

During Stage 5A, six architectural and design questions were identified. In Stage 5B, each question was evaluated against the existing working tree, empirical references, and Material 3 guidelines.

This document records the **Stage 5B Architectural Recommendations** (which are fully specified in [`DESIGN_SYSTEM_SPEC.md`](DESIGN_SYSTEM_SPEC.md), [`NAVIGATION_SPEC.md`](NAVIGATION_SPEC.md), and [`SCREEN_SPECIFICATIONS.md`](SCREEN_SPECIFICATIONS.md)), and isolates the **Governance Decisions Awaiting User Approval** before implementation begins.

---

## 2. Stage 5B Evaluated Architectural Recommendations

### Recommendation A: Navigation Architecture
- **Stage 5B Recommendation:** Adopt **Jetpack Compose Navigation** (`androidx.navigation:navigation-compose`) with type-safe `@Serializable` routes (`Route.Home`, `Route.Library`, `Route.AlbumDetail(albumId)`, etc.).
- **Rationale:** The current manual state machine in `MainActivity.kt` lacks a proper back-stack for drill-down navigation (e.g., Search -> Album Detail -> Track), cannot handle Android 14+ predictive back gestures cleanly, and forces nested boolean flags. Playback state is already held globally in `PlaybackViewModel` / `HarmoniaPlaybackService`, making it fully decoupled and safe to wrap in a `NavHost`.
- **Implementation Impact:** Adds `androidx.navigation:navigation-compose` dependency to `build.gradle.kts` in a future implementation phase.
- **Status:** Evaluated and fully specified in [`NAVIGATION_SPEC.md`](NAVIGATION_SPEC.md). **Awaiting user approval to add dependency.**

---

### Recommendation B: Now Playing Sheet Transition Mechanics
- **Stage 5B Recommendation:** Maintain **Material 3 standard `ModalBottomSheet`** for initial baseline stability with strict 48dp touch bounds, while structuring the presentation state to allow an upgrade to custom gesture-interpolating bottom sheet behavior (Auxio pattern) once core detail screens are in place.
- **Rationale:** Custom gesture drag interpolation requires complex coordinate mapping and custom layout math. Standard `ModalBottomSheet` delivers 90% of the user value with zero risk of gesture contention with Android system edge back gestures.
- **Status:** Evaluated and specified in [`SCREEN_SPECIFICATIONS.md`](SCREEN_SPECIFICATIONS.md).

---

### Recommendation C: Personalization Phase 4 Boundary Enforcement
- **Stage 5B Recommendation:** Strictly preserve Personalization Phase 4 as an **unimplemented future milestone**.
- **Rationale:** Room schema v5, `OfflinePersonalizationRepository`, and `PersonalizationViewModel` are 100% complete and tested in the working tree. Stage 5B specifies the exact visual and interaction requirements for the heart toggle and 1–5 star rating bar in [`SCREEN_SPECIFICATIONS.md`](SCREEN_SPECIFICATIONS.md) and [`INTERACTION_FLOWS.md`](INTERACTION_FLOWS.md), but **zero Compose UI code will be written until Phase 4 is explicitly authorized**.
- **Status:** Boundary locked. **Awaiting user authorization to begin Phase 4 implementation.**

---

### Recommendation D: Equalizer UI Surface Placement
- **Stage 5B Recommendation:** Retain the **master 5-band Equalizer control panel in `SettingsScreen`** as the primary source of truth, and add a **quick-access shortcut icon button** in the header of `NowPlayingSheet` that opens an Equalizer modal bottom sheet during active listening.
- **Rationale:** Eliminates the friction of forcing users to exit Now Playing, tap Settings, and scroll down to adjust a frequency band while actively listening to a track.
- **Status:** Evaluated and specified in [`SCREEN_SPECIFICATIONS.md`](SCREEN_SPECIFICATIONS.md).

---

### Recommendation E: Theme Strategy (Dark-First vs. Dual Theme)
- **Stage 5B Recommendation:** Harmonia is designed as a **dark-first application** (`ObsidianSpace #090B10` baseline), with an architectural **Light / Daylight Theme** (`SlateDay #F4F6F9`) fully specified in [`DESIGN_SYSTEM_SPEC.md`](DESIGN_SYSTEM_SPEC.md) for extreme outdoor sunlight legibility.
- **Rationale:** Dark mode is the authentic expression of Harmonia’s audiophile instrument identity and optimizes battery life on AMOLED screens. However, strict WCAG AA contrast (15.2:1 text contrast) in Light Mode guarantees daylight accessibility.
- **Status:** Evaluated and specified. Initial implementation may ship Dark Mode first, followed by Light Mode toggle in Settings.

---

### Recommendation F: Technical Audio Telemetry Presentation
- **Stage 5B Recommendation:** Display a subtle, non-intrusive format chip on Now Playing (e.g. `FLAC · 24-bit / 96 kHz` or `MP3 · 320 kbps`). Tapping it opens an **Audio Signal Inspector** modal sheet displaying:
  - Container Format (`FLAC`, `MP3`, `ALAC`, `Opus`).
  - Bitrate (`kbps`).
  - Sample Rate (`Hz / kHz`).
  - Bit Depth (`16-bit`, `24-bit Hi-Res`).
  - Channel Count (`Stereo`, `Mono`).
  - Hardware AudioTrack Sink: Verifies whether the platform is outputting direct or resampling.
- **Rationale:** Satisfies audiophile verification needs without cluttering the primary playback surface for relaxed listeners.
- **Data Source Reliability:** Formats and bitrates are extracted directly from `TrackFile` / `LocalFile` Room entities (`[VERIFIED-SRC]`). AudioTrack sink telemetry uses platform `AudioTrack.getPlaybackHeadPosition()` / `getSampleRate()` (`[VERIFIED-SRC]`).
- **Status:** Evaluated and specified in [`SCREEN_SPECIFICATIONS.md`](SCREEN_SPECIFICATIONS.md).

---

## 3. Decisions Awaiting Explicit Stakeholder Approval

Before any implementation work begins, explicit stakeholder approval is requested on the following three gates:

1. **Gate 1: Authorize `navigation-compose` Dependency:**
   - *Question:* Do you approve adding `androidx.navigation:navigation-compose` (2.8+) to `harmonia-mobile/app/build.gradle.kts` for type-safe routing and detail screen back-stacks?
2. **Gate 2: Authorize Personalization Phase 4 Implementation:**
   - *Question:* Should Personalization Phase 4 (Favorite heart toggle and 1–5 star rating bar UI controls) be scheduled as the immediate next implementation milestone (Stage 5C)?
3. **Gate 3: Light Theme Implementation Sequencing:**
   - *Question:* Should the Light/Daylight theme be implemented concurrently with Dark Mode, or should Dark Mode be completed and verified first with Light Mode scheduled as a fast follow?
