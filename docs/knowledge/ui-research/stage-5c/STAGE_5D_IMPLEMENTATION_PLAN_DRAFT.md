# Harmonia Mobile — Stage 5D Implementation Plan Draft (Stage 5C.3)

**Document Classification:** Technical Implementation Plan & Phasing Draft  
**Status:** DRAFT FOR PROJECT LEAD APPROVAL (Stage 5C.3 Gate)  
**Author:** Senior Android UI/UX Architect & Engineering Documentation Auditor  
**Baseline Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  

---

> [!WARNING]
> **Implementation Authorization Gate:** This document defines the recommended architectural phasing, component dependencies, test strategies, and acceptance criteria for Stage 5D. **It does not authorize modifying application source code, version catalogs, manifests, or dependencies.**

---

## 1. Implementation Phasing Sequence

To maintain repository integrity and protect the existing 50 modified files, Stage 5D should execute across five strictly ordered phases:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 5D.1: Navigation Strategy & Shared Design System Primitives     │
│             (Theme.kt, Color.kt, Shape.kt, 1dp Border Modifier)        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Phase 5D.2: Shell, Persistent Mini-Player & Now Playing Surface        │
│             (MainActivity.kt, MiniPlayer.kt, NowPlayingSheet.kt)        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Phase 5D.3: Catalog Surfaces, FastScroll & Discrete Playlist Reorder   │
│             (HomeScreen.kt, LibraryScreen.kt, PlaylistDetailScreen.kt) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Phase 5D.4: Search, Storage Lifecycle Feedback & Equalizer Controls    │
│             (SearchScreen.kt, SettingsScreen.kt, EqualizerPanel.kt)    │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Phase 5D.5: Accessibility, Contrast Automation & Visual Regressions    │
│             (Automated Compose Semantics, Contrast Rule, 200% Scale)   │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Detailed Phase Specifications

### Phase 5D.1: Navigation Strategy & Shared Design System Primitives

- **Scope:**
  - Execute approved Navigation decision: migrate to `androidx.navigation:navigation-compose:2.8.x` with `@Serializable` routes OR extend `MainActivity.kt` `Screen` state machine with a robust back-stack.
  - Implement core tokens: `ObsidianSpace` (`#090B10`), `CharcoalElevated` (`#141721`), `AcousticTeal` (`#00D2B4`), `HarmonicViolet` (`#6E56CF`), `BorderSlate` (`#2A3042`).
  - Establish reusable `Modifier.hairlineBorder()` (1dp border) and `Modifier.touchTarget48()` (minimum 48dp bounds).
- **Existing Screens & State Owners:**
  - `MainActivity.kt` (Screen routing and window insets).
  - `Theme.kt`, `Color.kt`, `Type.kt`, `Shape.kt`, `HarmoniaIcons.kt`.
- **Prerequisites:** Project lead approval on Decision 1 (Navigation Architecture).
- **Acceptance Criteria:**
  - Color tokens match `DESIGN_SYSTEM_SPEC.md` §2.
  - All interactive tokens enforce $\ge$ 48dp bounds.
  - Edge-to-edge system insets render cleanly on Android 14/15.
- **Focused Tests & Visual Verification:**
  - Compose theme preview rendering in dark mode.
  - Unit test verifying token relative luminance and mathematical contrast ratios.
- **Regression Risks:** Regressing top-level destination state in `MainActivity.kt`.
- **Explicit Exclusions:** No screen-specific UI refactoring; no Light Theme implementation in this phase.

---

### Phase 5D.2: Shell, Persistent Mini-Player & Now Playing Surface

- **Scope:**
  - Refactor `MiniPlayer.kt`: Uncropped 40dp artwork thumbnail, track title, artist name, 48dp play/pause button, and 2dp cyan-teal top progress line.
  - Refactor `NowPlayingSheet.kt`: Responsive album artwork (`fillMaxWidth(0.85f)`, `max(320dp)`), precision horizontal scrub bar with tabular monospace timecodes, 64dp Play/Pause FAB, skip controls, and format chip.
  - Implement queue peek drawer handle (`UP NEXT (N)`).
- **Existing Screens & State Owners:**
  - `PlaybackViewModel.kt`, `HarmoniaPlayerController.kt`, `NowPlayingSheet.kt`, `MiniPlayer.kt`.
- **Prerequisites:** Phase 5D.1 design tokens.
- **Acceptance Criteria:**
  - Mini-Player remains persistently visible across all top-level destinations.
  - Tapping Mini-Player expands `NowPlayingSheet`.
  - Scrubbing tracks updates audio position without audio stuttering.
  - Now Playing artwork does not clip under 200% font scaling or on compact viewports.
- **Focused Tests & Visual Verification:**
  - `NowPlayingAndMiniPlayerUiTest.kt` pass.
  - Compose rule test verifying 48dp touch bounds on play/pause, skip, shuffle, repeat, and dismiss chevron.
- **Regression Risks:** Breaking Media3 session binding or background playback continuity.
- **Explicit Exclusions:** No 3-layer Audio Signal Inspector modal sheet yet (deferred to Phase 5D.4); no continuous gesture expansion.

---

### Phase 5D.3: Catalog Surfaces, FastScroll & Discrete Playlist Reorder

- **Scope:**
  - Refactor `HomeScreen.kt`: Recently Played vinyl carousel (`ListeningEventRepository`), factual offline metrics badge (`1,248 Tracks • 84 Albums • 100% Offline`).
  - Refactor `LibraryScreen.kt`: Segmented tabs (`Tracks`, `Albums`, `Artists`), list item cards with 1dp borders, 48dp thumbnails, and `FLAC` format badges.
  - Refactor `PlaylistDetailScreen.kt`: Upgrade discrete `Move Up` and `Move Down` buttons to strictly enforce **48dp × 48dp touch bounds** (expanding the current 36dp implementation).
- **Existing Screens & State Owners:**
  - `HomeViewModel.kt`, `LibraryViewModel.kt`, `PlaylistDetailViewModel.kt`, `OfflinePlaylistRepository.kt`.
- **Prerequisites:** Phase 5D.2 Mini-Player.
- **Acceptance Criteria:**
  - Home metrics match actual Room counts dynamically.
  - Track rows trigger immediate playback without navigation lag.
  - Moving tracks up/down updates Room playlist order transactionally.
  - Touch bounding boxes for Move Up and Move Down measure exactly $\ge$ 48dp.
- **Focused Tests & Visual Verification:**
  - `OfflinePlaylistRepositoryTest.kt` passes.
  - UI test verifying track reordering updates the list visually.
- **Regression Risks:** Performance regressions on large catalog lists (>1,000 tracks) during fast scrolling.
- **Explicit Exclusions:** No continuous drag-and-drop library (strictly deferred).

---

### Phase 5D.4: Search, Storage Lifecycle Feedback & Equalizer Controls

- **Scope:**
  - Refactor `SearchScreen.kt`: Local search input bar, instant categorized results (`Tracks`, `Albums`, `Artists`), zero network calls.
  - Refactor `SettingsScreen.kt`: Storage & Library card with distinct status badges (`CONNECTED` vs `UNMOUNTED / OFFLINE`) based on `StorageVolumeLifecycleCoordinator`.
  - Implement 5-band Graphic Equalizer UI panel: Sliders with live dB labels and preset chips dynamically bound to `EqualizerBand` and `EqualizerPreset`.
  - Build `AudioInspectorSheet.kt` modal sheet displaying verified container format, decoded stream, and output device routing.
- **Existing Screens & State Owners:**
  - `SearchViewModel.kt`, `SettingsViewModel.kt`, `StorageVolumeLifecycleCoordinator.kt`, `DefaultAudioEffectManager.kt`.
- **Prerequisites:** Phase 5D.1 tokens and Phase 5D.2 playback bindings.
- **Acceptance Criteria:**
  - Unmounting SD card displays amber `UNMOUNTED / OFFLINE` badge without SQLite crashes.
  - Toggling EQ engages platform `Equalizer` on active audio session.
  - Audio Signal Inspector displays verified container metadata and routed audio device.
  - Search queries preserve state across back navigation.
- **Focused Tests & Visual Verification:**
  - `StorageVolumeLifecycleCoordinatorTest.kt` passes.
  - `SettingsViewModelEqualizerTest.kt` passes.
- **Regression Risks:** Hardware equalizer crashes on emulator or devices lacking audio effect HAL support.
- **Explicit Exclusions:** No physical DAC chip identification; no Room/DataStore EQ persistence migrations in this phase (in-memory session baseline maintained).

---

### Phase 5D.5: Accessibility, Contrast Automation & Visual Regressions

- **Scope:**
  - Run automated Compose semantics and accessibility verification suite.
  - Execute Robolectric contrast test verifying all rendered text/icon pairs meet WCAG AA ($\ge$ 4.5:1 for body, $\ge$ 3.0:1 for large text/icons).
  - Verify layout stability under 200% system font scaling and talkback accessibility traversal.
- **Prerequisites:** Phases 5D.1 through 5D.4 complete.
- **Acceptance Criteria:**
  - 100% compliance with 48dp touch targets across all screens.
  - Zero text clipping or overlapping elements under 200% font scaling.
  - Zero talkback focus traps.
- **Focused Tests:** Complete automated UI test suite execution.

---

## 3. Explicit Implementation Exclusions (Safety Guardrails)

The following items are **strictly prohibited** from inclusion in Stage 5D:
1. **Personalization Phase 4 Implementation:** Zero favorite heart toggles, star ratings, or algorithmic weights in UI.
2. **Third-Party Drag-and-Drop Libraries:** No `reorderable` or complex gesture pointer libraries.
3. **Database Schema Migrations for Equalizer:** No Room migrations or DataStore additions for EQ persistence.
4. **Unverified Audio Hardware Claims:** No claims of bit-perfect output, kernel bypass, or physical DAC chip names.
5. **Cloud / Streaming / AI Dependencies:** Zero network calls, remote streaming, or companion sync code.
