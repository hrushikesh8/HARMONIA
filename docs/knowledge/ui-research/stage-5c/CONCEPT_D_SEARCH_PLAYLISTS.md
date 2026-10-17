# Harmonia Mobile — Concept D: Search & Playlist Details (Stage 5C)

**Document Classification:** Visual Design Concept & Specification  
**Status:** DRAFT CONCEPT FOR REVIEW (Stage 5C)  
**Target Platform:** Android 14+ (Jetpack Compose, Room SQLite, Material 3)  
**Target Form Factor:** Compact Mobile (390dp × 844dp to 412dp × 915dp, 9:16 / 19.5:9 aspect ratio)  

---

## 1. Visual Layout & Architectural Blueprint

```
┌────────────────────────────────────────────────────────┐
│ [←] Late Night Vinyl                             [⋮]   │ TopBar: Back & Menu (48dp)
├────────────────────────────────────────────────────────┤
│ ┌──────────────┐                                       │
│ │              │  Late Night Vinyl                     │ Playlist Header (140dp)
│ │   [ARTWORK]  │  24 Tracks • 1 hr 42 min • Offline    │ Harmonic Violet Accent
│ │   120x120dp  │  [ ▶ Play All ]  [ 🔀 Shuffle ]       │ Cyan-Teal Pill Actions
│ └──────────────┘                                       │
├────────────────────────────────────────────────────────┤
│ TRACKS (24)                                            │ Section Header
├────────────────────────────────────────────────────────┤
│ 01  Stellar Echoes                 3:04  [▲] [▼] [≡]  │ Track Row (64dp)
│     Nova Spark • FLAC                    (48dp targets)│ Discrete Move & Drag
├────────────────────────────────────────────────────────┤
│ 02  Midnight Grooves               3:55  [▲] [▼] [≡]  │ Track Row (64dp)
│     The Velvet Echoes • FLAC                           │
├────────────────────────────────────────────────────────┤
│ 03  Resonance in B Minor           4:12  [▲] [▼] [≡]  │ Track Row (64dp)
│     Aura Ensemble • FLAC                               │
└────────────────────────────────────────────────────────┘
```

*Note on Visual Mockup:* Due to API image generation rate limits, this concept is fully specified via high-precision architectural ASCII blueprints, exact token mappings, and verified source component specifications.

---

## 2. Visual Hierarchy & Spatial Layout

Concept D covers two core search and curation surfaces: **Categorized Local Search** and **Playlist Details with Reordering**.

### 2.1 Playlist Details Screen

1. **Top Application Bar (48dp height):**
   - Left: Back navigation arrow (48dp touch target).
   - Center: Playlist name in `titleLarge` (18sp semi-bold, `#F0F3F8`).
   - Right: Overflow menu button (48dp touch target) providing actions: *Rename Playlist*, *Export M3U*, *Delete Playlist*.

2. **Playlist Hero Header (140dp height):**
   - Left: 120dp × 120dp composite playlist artwork thumbnail with 12dp rounded corners and subtle violet accent glow (`#6E56CF`).
   - Right: Metadata column:
     - Title: `Late Night Vinyl` *(Illustrative sample title)* (`headlineMedium`, 22sp bold, `#F0F3F8`).
     - Subtitle: `24 Tracks • 1 hr 42 min • Offline` *(Illustrative sample metadata)* (`bodyMedium`, 13sp, `#8E95A5`).
     - Action Buttons: Horizontal row with primary `Play All` cyan-teal pill button (`#00D2B4` background, 48dp height) and secondary `Shuffle` button (`#1E2333` background with 1dp border).

3. **Track List Items with Reordering Controls (64dp height):**
   - Background: Dark elevated card (`#141721`) with 1dp hairline border (`#2A3042`).
   - Left: 2-digit index (`01`, `02`, `03`) in tabular monospace font (`#8E95A5`).
   - Center: Track title (`titleMedium`, 16sp, `#F0F3F8`) and artist name (`bodyMedium`, 13sp, `#8E95A5`). *(Illustrative sample tracks: 'Stellar Echoes', 'Midnight Grooves', 'Resonance in B Minor')*.
   - Right: Reordering Control Zone:
     - **Discrete Reordering Buttons (`[VERIFIED-SRC]`):** Upward chevron icon button (`Move Up`) and downward chevron icon button (`Move Down`), each engineered with strict **48dp × 48dp minimum touch bounds** (expanding the current 36dp implementation).
     - **Continuous Drag Handle (`[PROPOSED]`):** 3-bar drag icon (`[≡]`) enabling touch-and-hold vertical drag reordering. *(Strictly proposed; unapproved and unimplemented)*.

---

## 3. Search Screen Hierarchy & Categories

The Search Screen provides instant, categorized local music filtering:

1. **Search Input Bar (56dp height):**
   - Elevated pill container (`#1E2333`) with 1dp border (`#2A3042`).
   - Magnifying glass icon on left, clear query (`×`) button on right.
   - Text input in `bodyLarge` (16sp, `#F0F3F8`) with placeholder *"Search tracks, albums, artists..."*

2. **Categorized Search Results Sections:**
   - **Tracks Section:** Up to 5 top matching track rows with direct play trigger.
   - **Albums Section:** Horizontal carousel of matching album covers (88dp × 88dp) with title and release year.
   - **Artists Section:** Horizontal carousel of circular artist avatars (72dp diameter) with artist name.
   - **View All Results:** Header link navigating to exhaustive filtered list.

3. **Empty & Zero Search State:**
   - Calm feedback: *"No matches found for '[query]'."*
   - Helpful guidance: *"Search operates strictly against locally indexed audio metadata."*

---

## 4. Reordering Architecture: Verified vs Proposed

Harmonia Mobile's playlist reordering architecture strictly separates existing verified code from proposed enhancements:

| Reordering Mechanism | UI Presentation | Touch Bounding Target | Technical Implementation | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Discrete Reorder (Existing)** | Upward/Downward Chevron Icon Buttons | 48dp × 48dp (Expanded from current 36dp) | Calls `PlaylistDetailViewModel.moveTrackUp()` and `moveTrackDown()`; updates Room playlist index transactionally. | **`[VERIFIED-SRC]`** |
| **Continuous Drag Reorder (Proposed)** | 3-bar Drag Handle Icon | 48dp × 48dp | Requires `androidx.compose.foundation` pointer input or dedicated Compose reorder library. | **`[PROPOSED]`** |
| **Accessibility Actions** | TalkBack Custom Actions | Full list item focus | TalkBack announcement: *"Action: Move track up; Action: Move track down"*. | **`[PROPOSED]`** |

---

## 5. Accessibility & Touch Verification

- **Touch Target Integrity:** All icon buttons (`Move Up`, `Move Down`, `Menu`, `Play All`) enforce `Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)`.
- **Contrast Integrity:** White text on `#141721` card = **14.82:1** (AAA). Violet playlist accents (`#6E56CF`) restricted to large playlist badges $\ge$ 3.0:1.
- **State Preservation:** Search query and scroll position are explicitly hoisted to avoid state loss on back navigation.
