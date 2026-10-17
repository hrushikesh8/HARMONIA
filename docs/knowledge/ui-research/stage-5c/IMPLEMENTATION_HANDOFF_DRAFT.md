# Harmonia Mobile — Implementation Handoff Draft (Stage 5C)

**Document Classification:** Implementation Sequencing & Architectural Handoff Draft  
**Status:** DRAFT FOR STAKEHOLDER REVIEW (Stage 5C — Not Authorized for Implementation)  
**Author:** Senior Android UI/UX Architect & Design Systems Engineer  

---

> [!WARNING]
> **Implementation Authorization Gate:** This document outlines the proposed sequence, component dependencies, and state prerequisites for implementing the visual design in Phase 5D. **It does not authorize code implementation, dependency additions, or file edits.**

---

## 1. Screen Implementation Sequence & Phasing

To ensure architectural stability and minimize regressions across the existing 50 modified files, implementation should proceed in four sequential, testable increments:

```
┌────────────────────────────────────────────────────────────────────────┐
│ Phase 5D.1: Core Design System & Tokens (Theme.kt, Color.kt, Shape.kt) │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Phase 5D.2: Navigation Frame & Persistent Playback (Mini-Player & Tabs)│
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Phase 5D.3: Catalog Surfaces (Home, Library, Search, Playlists)        │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│ Phase 5D.4: Playback Stage & Settings (Now Playing, EQ, Inspector)     │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Phase-by-Phase Component Breakdown & Prerequisites

### Phase 5D.1: Design Tokens & System Primitives
- **Target Files:** `Theme.kt`, `Color.kt`, `Type.kt`, `Shape.kt`, `HarmoniaIcons.kt`.
- **Deliverables:**
  - Update `HarmoniaColors` data class with verified semantic tokens (`colorBackgroundRoot`, `colorSurfaceElevated`, `colorPrimarySignal`, `colorSecondarySignal`, etc.).
  - Define exact 1dp hairline border stroke (`BorderSlate #2A3042`).
  - Establish standard corner radii (6dp, 12dp, 16dp, 24dp) in `Shape.kt`.
  - Validate minimum 48dp touch bounds utility modifier (`Modifier.touchTarget48()`).
- **Prerequisites:** None. Pure Compose styling tokens.

### Phase 5D.2: Shell, Navigation & Persistent Mini-Player
- **Target Files:** `MainActivity.kt`, `MiniPlayer.kt`, `NavigationHost.kt`.
- **Deliverables:**
  - Resolve Navigation Architecture (Pending Decision: Navigation Compose vs enhanced `MainActivity` state machine).
  - Implement edge-to-edge insets handling (`WindowInsets.safeDrawing`).
  - Refactor `MiniPlayer` with uncropped 40dp square artwork, teal micro-progress hairline, and 48dp play/pause button.
- **Prerequisites:** Phase 5D.1 design tokens; explicit architectural approval for navigation migration.

### Phase 5D.3: Catalog, Search & Playlist Detail Screens
- **Target Files:** `HomeScreen.kt`, `LibraryScreen.kt`, `SearchScreen.kt`, `PlaylistDetailScreen.kt`.
- **Deliverables:**
  - Build `RecentlyPlayedRow` using `ListeningEventRepository`.
  - Implement `LibraryMetricsBadge` displaying factual offline track counts.
  - Upgrade `PlaylistDetailScreen` discrete `Move Up` / `Move Down` buttons to strictly enforce **48dp × 48dp touch targets**.
  - (Optional pending approval) Implement continuous drag-and-drop reorder handle.
- **Prerequisites:** Phase 5D.2 navigation shell.

### Phase 5D.4: Playback Stage, Equalizer & Audio Inspector
- **Target Files:** `NowPlayingSheet.kt`, `SettingsScreen.kt`, `AudioInspectorSheet.kt`.
- **Deliverables:**
  - Implement responsive album artwork (`fillMaxWidth(0.85f)`, `max(320dp)`).
  - Add `FLAC 24-bit / 96 kHz` technical format chip on Now Playing.
  - Build `AudioInspectorSheet` displaying container format, decoded stream, and output device routing.
  - Refactor Settings screen cards: Storage mount status badges (Connected vs Unmounted SD Card) and 5-band graphic equalizer sliders.
- **Prerequisites:** Phase 5D.2 playback state; `PlaybackViewModel` integration.

---

## 3. Acceptance Criteria per Screen

| Screen / Surface | Visual Acceptance Criteria | Functional / State Acceptance Criteria |
| :--- | :--- | :--- |
| **Home & Library** | 1dp hairline borders on cards; 48dp thumbnails; teal active indicator on tabs. | Metrics match Room track count; tapping track immediately plays; FastScrollRail functions on long lists. |
| **Now Playing** | Artwork does not clip under 200% font scale; transport buttons $\ge$ 48dp; format chip clearly visible. | Scrubbing updates timecodes; dismiss collapses cleanly to Mini-Player; background playback uninterrupted. |
| **Settings & EQ** | Storage card displays distinct badges for connected vs unmounted volumes; EQ sliders feature visible dB labels. | Toggling EQ applies platform `Equalizer`; unmounting SD card pauses playback and updates badge without crashing. |
| **Search & Playlists** | Search bar auto-focuses; playlist reorder buttons measure $\ge$ 48dp; violet playlist accents meet contrast. | Reordering updates Room playlist order transactionally; search filters locally without network calls. |

---

## 4. Test Harness & Verification Suite Requirements

Before accepting any Phase 5D implementation:
1. **Robolectric & Compose UI Tests:** Automated screenshot and semantics tests asserting 48dp bounds and content descriptions.
2. **Contrast Ratio Test Rule:** Automated check that rendered text-to-background combinations meet WCAG AA ($\ge$ 4.5:1 for body, $\ge$ 3.0:1 for large/icons).
3. **Storage Disconnection Physical Test:** Verification that unmounting physical SD card transitions UI to warning state without SQLite disk I/O crashes.
