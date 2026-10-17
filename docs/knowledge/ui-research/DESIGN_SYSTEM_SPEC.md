# Harmonia Mobile — Design System & Visual Specification (Stage 5B)

**Document Classification:** Architectural Design System Specification  
**Status:** COMPLETE & PROPOSED (Stage 5B Gate)  
**Author:** Principal Mobile Product Designer & Design Systems Engineer for Harmonia  
**Repository Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  
**Dependencies:** Material 3 (`androidx.compose.material3`), Jetpack Compose, Android Platform Accessibility Guidelines  

---

## 1. Visual Philosophy: The Acoustic Architectural Instrument

Harmonia Mobile does not follow the noisy, feed-driven "jukebox" paradigm of streaming services. It is designed as a **precision acoustic instrument**—quiet, tactile, authoritative, and structurally disciplined.

### Core Visual Tenets:
1. **Calm Obsidian Baseline:** The interface recedes into the display, allowing uncropped album artwork and audio controls to command focus.
2. **Acoustic Teal Electrical Signal:** A vibrant, high-contrast cyan-teal accent communicates active signal flow, transport states, and audio frequency peaks.
3. **Harmonic Violet Secondary Depth:** Used sparingly for user-curated spaces (playlists, personalized badges, smart collections).
4. **Hairline Structural Boundaries:** In place of heavy, blurred drop-shadows or skeuomorphic bevels, subtle 1dp structural borders (`BorderSlate`) delineate cards and interactive surfaces.
5. **Strict Tactile Geometry:** Consistent corner radii (6dp, 12dp, 16dp, 24dp) and exact 48dp minimum touch bounds ensure total motor confidence.

---

## 2. Semantic Design Tokens

Harmonia uses semantic tokens mapped across both **Dark Theme** (primary baseline in `Color.kt`, `[VERIFIED-SRC]`) and **Light / Daylight Theme** (secondary high-contrast outdoor mode, `[PROPOSED]`).

### 2.1 Color Palette & Semantic Roles

```
┌─────────────────────────┬───────────────────┬───────────────────┬────────────────────────────────────────┐
│ Semantic Token Role     │ Dark Theme Hex    │ Light Theme Hex   │ Architectural Usage                    │
├─────────────────────────┼───────────────────┼───────────────────┼────────────────────────────────────────┤
│ colorBackgroundRoot     │ #090B10 (Obsidian)│ #F4F6F9 (SlateDay)│ Window background, scaffold base       │
│ colorSurfaceElevated    │ #141721 (Charcoal)│ #FFFFFF (White)   │ Primary cards, list cards, bottom bar  │
│ colorSurfaceContainer   │ #1E2333 (Studio)  │ #E8ECF2 (Cloud)   │ Secondary containers, badges, sliders  │
│ colorBorderStructural   │ #2A3042 (Border)  │ #CBD5E1 (Mist)    │ 1dp hairline borders around cards      │
│ colorPrimarySignal      │ #00D2B4 (Teal)    │ #006064 (DeepTeal)│ Active playback, sliders, focal points │
│ colorSecondarySignal    │ #6E56CF (Violet)  │ #5E35B1 (Plum)    │ Playlists, ratings, curated tags       │
│ colorTextPrimary        │ #F0F3F8 (SignalW) │ #0F172A (InkBlack)│ Headings, track titles, active labels  │
│ colorTextSecondary      │ #8E95A5 (Muted)   │ #475569 (SlateText) Subtitles, artists, inactive labels   │
│ colorTextDisabled       │ #565E70 (Subtle)  │ #94A3B8 (DimText) │ Disabled buttons, offline placeholders │
│ colorStatusSuccess      │ #00D2B4 (Teal)    │ #006064 (DeepTeal)│ Scan complete, storage healthy         │
│ colorStatusWarning      │ #FFB020 (Amber)   │ #D97706 (DeepAmber)Storage warning, control preempted      │
│ colorStatusError        │ #FF4B4B (Crimson) │ #DC2626 (DeepRed) │ Scan failed, storage disconnected      │
│ colorOverlayScrim       │ #000000 (80% a)   │ #000000 (40% a)   │ Modal bottom sheet scrim               │
└─────────────────────────┴───────────────────┴───────────────────┴────────────────────────────────────────┘
```

#### Exact Mathematical Contrast Ratio Verification:
Calculated using the official WCAG relative luminance formula $L = 0.2126R + 0.7152G + 0.0722B$ and $(L_1 + 0.05) / (L_2 + 0.05)$:

- **Dark Theme Tokens (`[VERIFIED-SRC]` in `Color.kt`):**
  - `colorTextPrimary` (`#F0F3F8`) on `colorBackgroundRoot` (`#090B10`): **17.70:1** (Exceeds WCAG AAA requirement of 7.0:1).
  - `colorPrimarySignal` (`#00D2B4`) on `colorBackgroundRoot` (`#090B10`): **10.19:1** (Exceeds WCAG AAA requirement).
  - `colorTextSecondary` (`#8E95A5`) on `colorSurfaceElevated` (`#141721`): **5.95:1** (Exceeds WCAG AA requirement of 4.5:1).
  - `colorSecondarySignal` (`#6E56CF`) on `colorBackgroundRoot` (`#090B10`): **3.65:1**.
    > [!IMPORTANT]
    > **Violet Contrast Constraint:** The ratio of 3.65:1 meets the WCAG AA threshold for **large text** ($\ge$ 18sp bold or $\ge$ 24sp regular) and **graphical components / icons** ($\ge$ 3.0:1), but does **NOT** meet the 4.5:1 threshold for small body text. Therefore, `HarmonicViolet` is strictly restricted to playlist cards, star rating icons, and large category headers, and must never be used for small secondary text.

- **Light Theme Tokens (`[PROPOSED]`):**
  - `colorTextPrimary` (`#0F172A`) on `colorBackgroundRoot` (`#F4F6F9`): **16.49:1** (Exceeds WCAG AAA requirement).
  - `colorPrimarySignal` (`#006064`) on `colorBackgroundRoot` (`#F4F6F9`): **6.79:1** (Exceeds WCAG AA requirement of 4.5:1; deepened from `#00838F` which scored 4.18:1).
  - `colorTextSecondary` (`#475569`) on `colorSurfaceElevated` (`#FFFFFF`): **7.58:1** (Exceeds WCAG AAA requirement).

*Contrast Validation Note:* Theoretical contrast ratios must be verified on running Compose builds across disabled, selected, and pressed interaction states during future visual QA.

---

### 2.2 Typography Scale

Harmonia builds on Android Material 3 typography with tightened line heights and precise tabular numeral alignment for audio timecodes:

```
┌──────────────────┬──────────────┬────────────┬─────────────┬──────────────┬──────────────────────────────┐
│ Typography Role  │ Font Family  │ Weight     │ Size (sp)   │ Line Ht (sp) │ Tracking / Usage             │
├──────────────────┼──────────────┼────────────┼─────────────┼──────────────┼──────────────────────────────┤
│ displayLarge     │ Sans-Serif   │ Bold (700) │ 28sp - 32sp │ 36sp - 40sp  │ -0.5sp; Screen header titles │
│ headlineMedium   │ Sans-Serif   │ SemiBold   │ 22sp        │ 28sp         │ 0.0sp; Now Playing track title│
│ titleLarge       │ Sans-Serif   │ SemiBold   │ 18sp        │ 24sp         │ 0.15sp; Card & section titles│
│ titleMedium      │ Sans-Serif   │ Medium(500)│ 16sp        │ 22sp         │ 0.15sp; Track list item title│
│ bodyLarge        │ Sans-Serif   │ Normal(400)│ 15sp        │ 20sp         │ 0.25sp; Settings row values  │
│ bodyMedium       │ Sans-Serif   │ Normal(400)│ 13sp - 14sp │ 18sp - 20sp  │ 0.25sp; Artist/album subtit. │
│ labelMedium      │ Sans-Serif   │ SemiBold   │ 11sp - 12sp │ 16sp         │ 0.4sp; Category tags, badges │
│ labelSmall       │ Sans-Serif   │ Bold (700) │ 10sp        │ 14sp         │ 1.5sp UPPERCASE; Tech labels │
│ timecodeTabular  │ Monospace    │ Medium(500)│ 12sp        │ 16sp         │ Tabular time elapsed/remain  │
└──────────────────┴──────────────┴────────────┴─────────────┴──────────────┴──────────────────────────────┘
```

---

### 2.3 Spacing Scale & Layout Grid

Harmonia utilizes an **8dp baseline grid** with 4dp sub-grid units for tight micro-alignments:

- `spacingXSmall`: **4.dp** (Icon-to-text gaps, badge inner padding, micro-spacers).
- `spacingSmall`: **8.dp** (Spacing between list items, button inner horizontal padding).
- `spacingMedium`: **12.dp** (Inner padding of compact cards, thumbnail-to-text spacing).
- `spacingStandard`: **16.dp** (Screen horizontal gutter, standard vertical section spacing).
- `spacingLarge`: **20.dp - 24.dp** (Card internal padding, Now Playing horizontal gutter).
- `spacingXLarge`: **32.dp** (Spacing between major visual groups, bottom padding above navigation).
- `spacingBottomScaffold`: **96.dp** (Content bottom padding in `LazyColumn` to clear docked Mini-Player + Navigation Bar).

---

### 2.4 Shape, Corner Radii & Elevation

Harmonia avoids blurry shadow elevations in favor of clean geometric containers with hairline boundary borders:

- `shapePill`: **CircleShape** (Play/Pause circular action button, status indicator dots).
- `shapeSmall`: **RoundedCornerShape(6.dp)** (Status chips, retry buttons, unmounted badges).
- `shapeMedium`: **RoundedCornerShape(12.dp)** (Track list cards, album grid cards, artist cards).
- `shapeLarge`: **RoundedCornerShape(16.dp)** (Elevated settings cards, modal containers).
- `shapeSheetTop`: **RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)** (Now Playing sheet, Mini-Player top edges).
- `borderHairline`: **1.dp** border using `colorBorderStructural` on all elevated cards.

---

### 2.5 Touch Target & Interactive Component Dimensions

Strictly conforms to Android accessibility guidelines: **Every interactive element must have a minimum bounding box of 48 × 48 dp**, regardless of the visual glyph size.

- **Bottom Navigation Bar Height:** **64.dp** (excl. system insets).
- **Docked Mini-Player Height:** **56.dp** (+ 3dp top linear progress bar).
- **Standard IconButton Touch Target:** **48.dp × 48.dp** (Visual icon: 24.dp - 28.dp).
- **Center Transport Play/Pause Button:** **64.dp × 64.dp** circular button (Visual icon: 36.dp).
- **Track Row Item Height:** **72.dp** (Allows generous touch margin without accidental multi-taps).
- **Alphabet Fast-Scroll Rail Width:** **32.dp** visual rail with **48.dp** horizontal touch slop.
- **Slider Track Height:** Active track **4.dp**; Thumb diameter **16.dp**; Vertical touch slop **48.dp**.

---

### 2.6 Album Artwork Conventions & Screen Resilience

- **Aspect Ratio:** Strictly **1:1 square** (`aspectRatio(1f)`).
- **Now Playing Dimension:** Responsive width using `Modifier.fillMaxWidth(0.85f).aspectRatio(1f)` with a max constraint of **320.dp** on compact handsets (up to 400.dp on tablets). This guarantees that under 200% dynamic font scaling or on small 360dp-width handsets, Now Playing does not clip the transport controls vertically.
- **Mini-Player Thumbnail:** **44.dp × 44.dp** with **8.dp** rounded corners.
- **Track Row Thumbnail:** **48.dp × 48.dp** with **8.dp** rounded corners.
- **Album Grid Item:** Flexible width based on 2-column or 3-column grid; **8.dp** rounded corners.
- **Missing Artwork Fallback:** Must render `HarmoniaArtwork` displaying a clean `HarmoniaIcons.MusicNote` on `colorSurfaceContainer` with hairline border. Never render raw broken file icons or empty transparent boxes.

---

### 2.7 Motion, Transitions & Benchmark Targets

- **Standard Sheet Expansion Duration:** **250ms** using `FastOutSlowInEasing`.
- **Mini-Player Alpha Fade:** **150ms** crossfade when transitioning to Now Playing.
- **Transport State Morphing:** **100ms** scale spring when toggling Play/Pause.
- **Performance Benchmarks (Not Runtime Guarantees):**
  - *Scrolling Target:* **60 fps** frame budget (16.6ms per frame) during active `LazyColumn` flings. Must be validated via Android Macrobenchmark on a defined reference device (e.g., Pixel 6a / mid-tier device) with a 10,000-track test fixture.
  - *Query Latency Target:* Search query execution targeted at sub-16ms; requires benchmark testing on large SQLite indexes.
- **System Reduced-Motion (`prefers-reduced-motion`):**
  - If the user enables "Remove animations" in Android Accessibility settings:
    - Sheet expansions snap immediately without spring interpolation.
    - Mini-player progress bar updates without continuous linear tweening.
    - Album art stays static without scale effects during seek scrubbing.

---

## 3. Measurable Accessibility Specifications

1. **TalkBack Content Descriptions:**
   - Every icon button must declare an explicit, localized, dynamic `contentDescription`:
     - Play/Pause: `"Play"` or `"Pause"` (avoiding redundant `"button"` suffix).
     - Shuffle: `"Shuffle tracks, currently [on / off]"`.
     - Repeat: `"Repeat mode: [Off / Repeat all / Repeat one]"`.
     - Sleep Timer: `"Sleep timer active, [N] minutes remaining"` or `"Set sleep timer"`.
     - Track Row: `"Play [Title] by [Artist], duration [M:SS]"`. If unmounted: `"Play [Title] by [Artist] (Storage Unmounted)"`.
2. **Keyboard & D-Pad Traversal:**
   - Sequential tab navigation must follow top-to-bottom reading order.
   - Now Playing sheet must trap keyboard focus while open; pressing Escape / Back dismisses the sheet and restores focus to the Mini-Player.
3. **Text Scaling Resilience:**
   - All text must use `sp` units and support up to **200% Android system font scaling** without clipping or overlapping containers.
   - Track titles and subtitles must use `maxLines = 1` with `TextOverflow.Ellipsis` rather than breaking card heights.
4. **Color-Independent Status Indicators:**
   - Storage unmount is indicated by **dimming (50% alpha)** AND an **explicit text badge ("Storage Unmounted")** AND a distinct semantic TalkBack label, ensuring users with color vision deficiency are never confused.
