# Harmonia Mobile — Visual Verification & UI Acceptance Plan (Stage 5B)

**Document Classification:** Architectural QA & Verification Plan  
**Status:** COMPLETE & PROPOSED (Stage 5B Gate)  
**Author:** Principal Mobile UX Architect & QA Lead for Harmonia  
**Repository Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  
**Target Execution Gate:** Subsequent Implementation Phase (Stage 5C / Phase 4)  

---

## 1. Verification Purpose & Quality Protocol

This document defines the formal protocol for visually reviewing, validating, and regression-testing Harmonia Mobile’s Jetpack Compose UI prior to release approval.

> [!NOTE]
> **Plan Classification Notice:**
> This document specifies the *future verification protocol*. It does not claim that visual testing, device benchmarking, or screenshot capture have already been executed for unimplemented components. No mockups or unverified renders are presented as approved production screens.

---

## 2. Standardized Device & Matrix Configurations

Visual acceptance testing must be executed across standardized emulator configurations representing the primary Android device archetypes:

```
┌─────────────────┬──────────────────────┬───────────────┬──────────────────────────┐
│ Device Archetype│ Viewport / Resolution│ DPI / Density │ Representation Target    │
├─────────────────┼──────────────────────┼───────────────┼──────────────────────────┤
│ **Compact Phone**│ 360 × 640 dp         │ ~320 dpi (xh) │ Small/budget handset     │
│ **Standard Phone**│ 412 × 915 dp         │ ~420 dpi (xxh)│ Standard modern phone    │
│ **Large Phone** │ 448 × 998 dp         │ ~480 dpi (xxxh│ Flagship phablet         │
│ **Foldable/Med**│ 673 × 841 dp         │ ~420 dpi (xxh)│ Unfolded inner display   │
│ **Tablet/Exp**  │ 1280 × 800 dp        │ ~240 dpi (hdpi│ 10-inch landscape tablet │
└─────────────────┴──────────────────────┴───────────────┴──────────────────────────┘
```

---

## 3. Test Scenarios & Visual Review Criteria

### Scenario 1: Dual Theme Verification (Dark & Light Modes)
- **Condition:** Switch between system-wide Dark Theme and Light Theme.
- **Review Procedure:**
  - Automated accessibility scan via Google Accessibility Scanner or Compose `AccessibilityChecks`.
  - In Dark Mode, canvas background must be pure `ObsidianSpace #090B10` without unintended light grey card flashes.
  - In Light Mode, canvas background must be `SlateDay #F4F6F9`, card surfaces pure white `#FFFFFF`, and text dark charcoal `#0F172A`.
  - Contrast ratios for text must meet or exceed **4.5:1** across all interactive and static states.

### Scenario 2: Dynamic Text Scaling Resilience (100% to 200%)
- **Condition:** Increase Android system font scale to **200%** (largest accessible font size).
- **Review Criteria:**
  - Card heights must expand gracefully without overlapping adjacent elements.
  - Track titles and subtitles must truncate cleanly with `TextOverflow.Ellipsis` rather than breaking into unreadable multi-line clippings.
  - Transport buttons in Now Playing and Mini-Player must remain centered and completely accessible.

### Scenario 3: Artwork Robustness & Missing Artwork Fallback
- **Condition:** Test with:
  1. Standard high-res square album art (3000 × 3000 px).
  2. Non-square rectangular artwork (16:9 or 4:3).
  3. Audio files containing zero embedded artwork.
- **Review Criteria:**
  - Non-square artwork must center-crop smoothly to a 1:1 aspect ratio without distorting.
  - Missing artwork must render `HarmoniaArtwork` geometric note placeholder on `StudioSlate` container with hairline border. Never render blank grey boxes or broken image icons.

### Scenario 4: Extreme String Lengths & Edge Cases
- **Condition:** Ingest audio tracks with 150+ character song titles, 80-character artist names, and long album names.
- **Review Criteria:**
  - Mini-Player and Now Playing must truncate gracefully with trailing ellipsis (`...`).
  - Marquee scrolling (if enabled) must scroll smoothly without dropped frames.

### Scenario 5: Large Catalog Scrolling & Benchmark Testing
- **Measurement Procedure:**
  - Execute an Android Macrobenchmark test measuring frame timing (`FrameTimingMetric`) and jank percentage during programmatic flings across a synthetic **10,000-track** and **1,000-artist** Room database fixture.
  - Test Hardware Baseline: Mid-tier reference device (e.g. Pixel 6a / Snapdragon 700-series).
  - Target Criteria: 95th percentile frame time $\le$ 16.6ms (60 fps benchmark target).
  - Dragging the `FastScrollRail` must trigger tactile haptic ticks and jump to the corresponding alphabetical section in under **50ms**.

### Scenario 6: Storage Disconnection & Error State Truth
- **Condition:** Unmount external SD card or USB volume while a track is actively playing.
- **Review Criteria:**
  - Playback pauses immediately.
  - Now Playing sheet renders the explicit amber/red banner: `"Storage Disconnected"`.
  - Library track cards residing on that volume dim to **50% alpha** with the explicit badge `"Storage Unmounted"`.
  - Reconnecting storage updates the banner to `"Storage reconnected. Tap play to resume."` without auto-resuming.

### Scenario 7: Touch Target Bounding Box Verification
- **Condition:** Enable `"Show layout bounds"` in Android Developer Options.
- **Review Criteria:**
  - Every interactive glyph (Shuffle, Repeat, Sleep Timer, Overflow 3-dots, Back button) must show a bounding box of at least **48 × 48 dp**.
  - Adjacent touch targets must maintain at least 4dp separation to prevent accidental double-taps.

---

## 4. Visual Verification Sign-Off Checklist

```
[ ] 1. Dark theme renders pure Obsidian (#090B10) baseline across all 5 tabs.
[ ] 2. Light theme renders clean high-contrast SlateDay (#F4F6F9) palette.
[ ] 3. 200% font scaling causes zero clipping or overlapping cards.
[ ] 4. All interactive icon buttons possess minimum 48x48dp touch bounds.
[ ] 5. Missing artwork fallback renders tailored Harmonia geometric placeholder.
[ ] 6. Storage unmount renders 50% alpha dimming + text badge + TalkBack announcement.
[ ] 7. FastScrollRail benchmark tested on 10,000-track fixture with frame timing metrics.
[ ] 8. Sleep timer countdown updates every minute and performs 30s volume fade-out.
[ ] 9. Equalizer sliders disable gracefully when audio session is preempted.
[ ] 10. Navigating into detail screens and pressing Back preserves exact prior scroll position.
```
