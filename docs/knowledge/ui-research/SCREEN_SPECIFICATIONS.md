# Harmonia Mobile — Screen-by-Screen UI & Component Specification (Stage 5B)

**Document Classification:** Architectural UI Specification  
**Status:** COMPLETE & PROPOSED (Stage 5B Gate)  
**Author:** Principal Mobile Product Designer & Android UI Architect for Harmonia  
**Repository Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  
**Dependencies:** Jetpack Compose, Material 3, AndroidX Media3, Room SQLite  

---

## 1. App Shell & Navigation Container

### 1.1 Screen Purpose
Provides the root application frame, coordinates top-level tab destinations, manages window insets, docks the persistent `MiniPlayer`, and hosts modal overlays (`NowPlayingSheet`).

### 1.2 Layout Hierarchy (Top to Bottom)
1. **Window Scrim:** System status bar rendered edge-to-edge with `ObsidianSpace #090B10`.
2. **Main Content Canvas (`NavHost`):** Active destination screen (`Home`, `Library`, `Playlists`, `Search`, `Settings`).
3. **Docked Mini-Player Container:** Pinned at `Alignment.BottomCenter` directly above navigation.
4. **Bottom Navigation Bar:** 64dp Material 3 `NavigationBar` styled in `AcousticCharcoal #141721` with `BorderSlate` top boundary.
5. **System Inset Spacer:** Navigation bar window inset padding.

### 1.3 Major Components & Data Dependencies
- **Data Source:** `PlaybackViewModel.uiState` (observes `PlaybackState` and `currentTrack`).
- **Components:** `MainAppContainer`, `NavigationBarItem`, `MiniPlayer`, `NowPlayingSheet`.

### 1.4 Acceptance Criteria
- Tapping any tab switches the primary view without dropping playback.
- Docked `MiniPlayer` appears if and only if `currentTrack != null` or `playbackState.currentTrackId != null`.
- Navigating across all 5 tabs preserves individual screen scroll positions when list states are hoisted.

---

## 2. Home Screen (Listening Dashboard)

### 2.1 Screen Purpose
The personal entry dashboard offering quick resume, recent playback history, library volume statistics, and instant shuffle playback.

### 2.2 Layout Hierarchy (Top to Bottom)
1. **Header Block:** Brand display title `"HARMONIA"` (`displayLarge`, `PureSignalWhite`) and subtitle `"USER-OWNED MUSIC PLAYER"` (`labelSmall`, `AcousticTeal`).
2. **Resume Playback Card (`[VERIFIED-SRC]`):** Elevated card displaying the last played track thumbnail, title, artist, elapsed progress indicator, and a prominent 48dp Resume button. If no prior playback exists, displays a welcoming `"Start Listening"` card.
3. **Quick Actions Row (`[VERIFIED-SRC]`):** Dual pill buttons: `"Resume Last Played"` (Teal) and `"Shuffle All Tracks"` (Studio Slate outline).
4. **Library Overview Metrics (`[VERIFIED-SRC]`):** Horizontal metrics row showing clickable summary chips: Albums (`N`), Artists (`N`), Playlists (`N`).
5. **Recently Played Carousel (`[VERIFIED-SRC]`):** Horizontal `LazyRow` of square cards displaying tracks from factual `ListeningEvent` history (`ListeningEventRepository.observeRecentEvents(30)`).
6. **Recently Added Carousel (`[VERIFIED-SRC]`):** Horizontal `LazyRow` of square cards displaying newly indexed local tracks sorted by `createdAt` descending.
7. **Bottom Scaffold Spacer:** 96dp bottom padding clearing docked Mini-Player.

### 2.3 Data Sources & ViewModels
- `HomeViewModel` (`[VERIFIED-SRC]`): Queries `OfflineTrackRepository`, `OfflineAlbumRepository`, `OfflineArtistRepository`, `OfflinePlaylistRepository`, `ListeningEventRepository`, `PlaybackStateRepository`.

### 2.4 State Variations
- **Empty Library State:** If catalog track count is 0, replaces carousels with an onboarding prompt card: `"No Audio Files Found. Add a music folder in Settings to start listening."` with a direct `"Go to Settings"` button.
- **Loading State:** Centered `CircularProgressIndicator(color = AcousticTeal)`.

### 2.5 Acceptance Criteria
- Tapping `"Shuffle All"` initiates deterministic shuffle playback of the entire catalog via `PlaybackViewModel.shuffleAll()`.
- Tapping any track in "Recently Played" plays that track immediately.
- Tapping the Albums chip navigates directly to `LibraryScreen` with `LibraryTab.ALBUMS` selected.

---

## 3. Library Screen (Catalog Browsing & Organization)

### 3.1 Screen Purpose
The central browsing surface organizing local audio into **Tracks**, **Albums**, and **Artists** with multi-attribute sorting and storage-volume status indications.

### 3.2 Layout Hierarchy (Top to Bottom)
1. **Header Row:**
   - Title: `"LIBRARY"` / `"LOCAL MEDIA CATALOG"`.
   - Top-right Action: **Sort Button** (48dp touch target) opening a contextual sort dropdown menu (`[VERIFIED-SRC]`).
2. **Diagnostic Banners (Conditional) (`[VERIFIED-SRC]`):**
   - Scanning Banner: Subtle horizontal strip with animated spinner: `"Updating music library..."`.
   - Scan Failed Banner: Red-tinted card with error message and `"Retry"` button.
   - Storage Warning Banner: Displayed when audio permissions are pending or storage volumes are disconnected.
3. **Category Tabs (`SecondaryTabRow`) (`[VERIFIED-SRC]`):**
   - `Tracks (N)` | `Albums (N)` | `Artists (N)`.
   - Active indicator: `AcousticTeal` bar.
4. **Active Tab Content:**
   - **Tracks Tab:** Vertical `LazyColumn` of `TrackRowCard` items with right-edge `FastScrollRail` (`[PROPOSED]`).
   - **Albums Tab:** 2-column (compact) or 3-column (medium) `LazyVerticalGrid` of `AlbumGridCard` items (`[VERIFIED-SRC]`).
   - **Artists Tab:** Vertical `LazyColumn` of `ArtistRowCard` items with circular avatar icons (`[VERIFIED-SRC]`).
5. **Alphabetical Fast-Scroll Rail (`FastScrollRail`) (`[PROPOSED]`):**
   - Slender vertical bar on the right margin containing alphabet letters (`#`, `A`–`Z`).
   - Dragging thumb triggers haptic vibration ticks and jumps the list to that section header.
   - *Implementation Prerequisite:* Requires computing an alphabetical section index map from the active track/artist list in `LibraryViewModel`.

### 3.3 Components & Data Contracts
- **Data Source:** `LibraryViewModel.uiState` (observes tracks, albums, artists, unmounted source IDs).
- **Track Card (`TrackRowCard`):** 48dp artwork, title, artist, duration, track number, and storage unmount badge.
- **Unmounted Storage Handling (`[VERIFIED-SRC]`):** If a track's source is in `unmountedSourceIds`, the card renders at **50% alpha** with an explicit text badge: `"Storage Unmounted"`.

### 3.4 Acceptance Criteria
- Tapping a track starts playback of the current sorted list at that index.
- Unmounted tracks do not crash or throw unhandled exceptions when clicked; clicking an unmounted track shows a snackbar: `"Storage volume is disconnected."`.
- Sorting by Title, Artist, Release Year, or Duration updates the list reactively.

---

## 4. Album & Artist Detail Screens (`[PROPOSED]`)

### 4.1 Album Detail Screen (`AlbumDetailScreen`)
- **Status:** Proposed New Destination (`[PROPOSED]`).
- **Underlying Domain Prerequisite:** Backed by existing `TrackRepository.getTracksByAlbumId(albumId)` (`[VERIFIED-SRC]`).
- **Layout Hierarchy:**
  1. Top App Bar: Back button, album title, overflow menu.
  2. Album Header: Responsive square artwork (capped at 200dp) with 16dp rounded corners, album title (`titleLarge`), artist name (`bodyLarge`, clickable link to Artist Detail), release year, track count, total duration.
  3. Action Bar: Prominent `"Play All"` button (Teal) and `"Shuffle"` button (Slate outline).
  4. Track List: Chronological numbered list of album tracks (`#1`, `#2`, ...), duration, and availability status.

### 4.2 Artist Detail Screen (`ArtistDetailScreen`)
- **Status:** Proposed New Destination (`[PROPOSED]`).
- **Underlying Domain Prerequisite:** Backed by existing `TrackRepository.getTracksByArtistId(artistId)` (`[VERIFIED-SRC]`).
- **Layout Hierarchy:**
  1. Top App Bar: Back button, artist name.
  2. Artist Header: Circular 96dp artist icon, artist name, total tracks, total albums count.
  3. Action Bar: `"Play Artist"` and `"Shuffle All"`.
  4. Discography Section: Horizontal carousel of the artist's albums (`AlbumGridCard`).
  5. All Songs Section: Complete list of songs by this artist.

---

## 5. Playlists & Playlist Detail Screen

### 5.1 Playlists Screen (`PlaylistsScreen`) (`[VERIFIED-SRC]`)
- **Purpose:** User collection manager supporting custom playlist creation, renaming, and deletion.
- **Layout Hierarchy:**
  1. Header: `"PLAYLISTS"` / `"USER-CURATED COLLECTIONS"`.
  2. Action Button: Top-right `"New Playlist"` button opening a text-input `AlertDialog`.
  3. Playlists List: Vertical `LazyColumn` of playlist cards displaying playlist title, track count, cumulative duration, and 3-dot context menu (Rename, Delete).
- **Data Source:** `PlaylistsViewModel.uiState`.

### 5.2 Playlist Detail Screen (`PlaylistDetailScreen`)
- **Status:** Existing Screen (`[VERIFIED-SRC]`) with Proposed Usability Enhancements (`[PROPOSED]`).
- **Existing Verified Functionality (`[VERIFIED-SRC]`):**
  - Displays ordered playlist tracks, hero artwork, Play All, Shuffle All, Rename, Delete, and Add Tracks.
  - Track reordering is **already implemented and operational** via discrete **Move Up** and **Move Down** icon buttons (`HarmoniaIcons.ArrowUp`/`ArrowDown`) invoking `OfflinePlaylistRepository.reorderPlaylistItem()` (`[VERIFIED-SRC]`).
- **Identified Usability & Accessibility Gaps:**
  - Existing Move Up and Move Down buttons are sized at **36.dp**, which is below the 48dp minimum touch-target requirement.
- **Proposed Enhancements (`[PROPOSED]`):**
  - Expand discrete reorder buttons to full **48 × 48 dp** touch bounding boxes.
  - Introduce optional continuous drag-and-drop handles for faster multi-item reordering.

---

## 6. Search Screen (`[VERIFIED-SRC]`)

### 6.1 Screen Purpose
Local zero-latency search engine indexing tracks, albums, artists, and playlists.

### 6.2 Layout Hierarchy (Top to Bottom)
1. Header: `"SEARCH"` / `"LOCAL CATALOG INDEX"`.
2. Search Input Field: `OutlinedTextField` with search icon prefix, placeholder `"Search songs, albums, artists..."`, and clear icon suffix (`HarmoniaIcons.Close`).
3. Results Canvas (`LazyColumn`):
   - Categorized result sections:
     - `TRACKS (N)`: Matching tracks with direct playback on tap.
     - `ALBUMS (N)`: Matching albums; tapping navigates to `AlbumDetailScreen`.
     - `ARTISTS (N)`: Matching artists; tapping navigates to `ArtistDetailScreen`.
     - `PLAYLISTS (N)`: Matching playlists; tapping navigates to `PlaylistDetailScreen`.
4. Empty Query State: Displays prompt `"Type to search your offline library"`.
5. No Results State: Displays `"No matches found for '[query]'"` with suggestion to check spelling.

---

## 7. Settings Screen (`[VERIFIED-SRC]`)

### 7.1 Screen Purpose
System architecture inspection, Storage Access Framework (SAF) folder management, media reconciliation triggering, and platform 5-band audio equalizer.

### 7.2 Layout Hierarchy (Top to Bottom)
1. Header: `"SETTINGS"` / `"SYSTEM ARCHITECTURE & PERSISTENCE"`.
2. **Storage Sources Card (`[VERIFIED-SRC]`):**
   - Action Buttons: `"Add Folder"` (triggers SAF `OpenDocumentTree`) and `"Scan Library"` (manual rescan).
   - Scanning Status: Progress indicator and file count during active scans.
   - Storage Sources List: Registered folders with name, track count, runtime status badge (`CONNECTED` / `DISCONNECTED`), and master enable/disable switch.
3. **Audio Equalizer Card (`EqualizerSection`) (`[VERIFIED-SRC]`):**
   - Master Switch Row: Equalizer Master toggle with status chip (`ACTIVE`, `STANDBY`, `CONTROL PREEMPTED`, `UNSUPPORTED`, `DISABLED`).
   - Presets Row: Horizontal scrollable chips of device-reported presets (e.g. `Flat`, `Rock`, `Jazz`, `Bass Boost`).
   - Frequency Bands Sliders: Vertical sliders for 60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz (-15dB to +15dB).
4. **Persistence Subsystem Telemetry Card (`[VERIFIED-SRC]`):**
   - Displays Database Engine (`Room SQLite 3`), Journaling Mode (`WAL`), Identity Model (`ADR-025 Two-Tier Decoupled`), Catalog Track Count, Verified Offline Count, and Storage Usage in MB.
5. **Audio Engine Specification Card (`[VERIFIED-SRC]`):**
   - Playback Service (`MediaLibraryService`), Output Path (`AudioTrack direct`), Network Streaming (`Zero network in playback path`).
6. **Build & Foundation Card (`[VERIFIED-SRC]`):**
   - App version, Target SDK (36), Minimum SDK (26), Milestone anchor.

---

## 8. Mini-Player & Now Playing Sheet

### 8.1 Mini-Player (`MiniPlayer`) (`[VERIFIED-SRC]`)
- **Docking:** Pinned above `NavigationBar` at `Alignment.BottomCenter`.
- **Layout:**
  - Pinned top 3dp `LinearProgressIndicator` showing track progress in `AcousticTeal`.
  - Row: 44dp album art thumbnail, track title (bold, 1 line), artist/album subtitle, 48dp Play/Pause IconButton, 48dp Skip Next IconButton.
- **Interactions:**
  - Tapping anywhere expands `NowPlayingSheet`.
  - Swiping horizontally skips to Next/Previous track (`[PROPOSED]`).

### 8.2 Now Playing Sheet (`NowPlayingSheet`)
- **Presentation:** Full-screen modal canvas (`ModalBottomSheet`).
- **Layout Hierarchy:**
  1. Header Bar:
     - Category Label: `"NOW PLAYING"` (or `"PLAYING QUEUE (N)"`).
     - Right Actions: Sleep Timer trigger button (with remaining countdown display) and Queue Drawer toggle button.
  2. Large Artwork: Responsive 1:1 square cover (`Modifier.fillMaxWidth(0.85f).aspectRatio(1f)`, max 320dp on phones, corner radius 20dp) with subtle architectural hairline border.
  3. Track Metadata Block:
     - Title (`headlineMedium`, bold, pure white).
     - Artist and Album subtitle (`bodyMedium`, muted slate).
     - Storage Disconnection Banner (if detached): Red-accented banner explaining volume unavailability.
     - Technical Audio Chip (`[PROPOSED]`): Non-intrusive badge (e.g. `FLAC · 24-bit / 96 kHz`) which opens the Audio Signal Inspector on tap.
  4. Personalization Bar (Phase 4 Requirement, `[PROPOSED]`):
     - Heart Toggle (Favorite) + 1–5 Star Rating interactive bar.
  5. Seekbar & Timecodes (`[VERIFIED-SRC]`):
     - Full-width `Slider` (Acoustic Teal thumb and active track, Studio Slate inactive).
     - Row below: Left tabular elapsed time (`0:45`), right tabular remaining time (`-3:12`).
  6. Primary Transport Cluster (Centered in bottom 30% thumb zone) (`[PROPOSED]`):
     - Shuffle Button: Stateful vector icon (`HarmoniaIcons.Shuffle`), glowing Teal when active (replaces plain text `SHF`).
     - Skip Previous: 48dp button (`HarmoniaIcons.SkipPrevious`, 32dp glyph).
     - Play/Pause Button: Dominant **64dp circular button** in solid `AcousticTeal` with dark `ObsidianSpace` icon (`[VERIFIED-SRC]`).
     - Skip Next: 48dp button (`HarmoniaIcons.SkipNext`, 32dp glyph).
     - Repeat Button: Stateful vector icon cycling `HarmoniaIcons.Repeat` (Off), `Repeat` (All), `RepeatOne` (One) (replaces plain text `RPT`).

---

## 9. Auxiliary Modal Sheets & Technical Audio Inspector

### 9.1 Queue Sheet (`QueueSheet` / `QueueView`)
- **Purpose:** Full inspection and editing of active listening queue.
- **Structure:**
  - Layer 1: Explicit "Up Next" user-added tracks (with remove buttons).
  - Layer 2: Ambient Context queue (album or playlist order).
  - Drag-and-drop handles for reordering items.
  - Tapping any item jumps playback directly to that index.

### 9.2 Audio Signal Inspector Modal (`AudioSignalInspectorSheet`) (`[PROPOSED]`)
- **Purpose:** Audiophile technical transparency without screen clutter.

#### Field-by-Field Specification & Feasibility Matrix:
```
┌──────────────────┬─────────────────────────────┬───────────────────────────┬─────────────────────────────────────────────────┐
│ Field Name       │ Data Source in Code         │ Scope & Classification    │ Reliability, Fallback & Limitations             │
├──────────────────┼─────────────────────────────┼───────────────────────────┼─────────────────────────────────────────────────┤
│ Container Format │ `LocalFileEntity.format`    │ Media file container      │ `[VERIFIED-SRC]`. Highly reliable. Fallback: -- │
│ Codec / MIME     │ `Format.sampleMimeType`     │ Encoded audio stream      │ Runtime-only from ExoPlayer. Fallback: from fmt │
│ Bitrate          │ `LocalFileEntity.bitrate`   │ Encoded audio stream      │ `[VERIFIED-SRC]`. CBR exact; VBR average.       │
│ Sample Rate      │ `LocalFileEntity.sampleRate`│ Decoded PCM audio         │ `[VERIFIED-SRC]`. Highly reliable. Fallback: -- │
│ Bit Depth        │ `LocalFileEntity.bitDepth`  │ File PCM quantization     │ `[VERIFIED-SRC]`. Accurate for FLAC/WAV.        │
│                  │                             │                           │ Fallback for lossy (MP3/AAC): "Lossy / 16-bit"  │
│ Channels         │ `LocalFileEntity.channels`  │ Decoded audio channels    │ `[VERIFIED-SRC]`. 1 = Mono, 2 = Stereo.         │
│ Output Route     │ `AudioTrack.getRoutedDevice`│ Active audio sink route   │ `[VERIFIED-DOC]`. Speaker, Wired, Bluetooth.    │
│ DAC Hardware Sink│ UNSUPPORTED on Android APIs │ Hardware chip / ALSA sink │ `[DEFERRED]`. Android public APIs do NOT expose │
│ Bit-Perfect Mode │ UNSUPPORTED on Android APIs │ Kernel AudioFlinger resamp│ `[DEFERRED]`. Cannot be verified via SDK APIs.  │
└──────────────────┴─────────────────────────────┴───────────────────────────┴─────────────────────────────────────────────────┘
```

> [!CAUTION]
> **Platform Audio Telemetry Boundary:**
> Standard Android public SDK APIs (`AudioTrack`, `AudioManager`) do NOT expose the physical DAC chip identity, ALSA driver bit depth, or kernel-level resampling bypass. Harmonia Mobile will **strictly NOT display fabricated claims of DAC models or direct-sink verification**. Only container metadata, ExoPlayer decoder format, and active platform route devices will be displayed.

### 9.3 Sleep Timer Dialog (`SleepTimerDialog`) (`[VERIFIED-SRC]`)
- **Presets Available:** 15 min, 30 min, 45 min, 60 min, End of Current Track, Cancel Timer.
- **Volume Preservation:** Non-destructive 30-second smooth volume attenuation curve (`ADR-035`) before pausing.

---

## 10. Future Personalization Phase 4 Requirements (Unimplemented)

> [!IMPORTANT]
> The following components are specified as future UI requirements only. No Compose implementation code is included in Stage 5B:

1. **Favorite Heart Toggle:**
   - Placed in `NowPlayingSheet` adjacent to track title and on each `TrackRowCard`.
   - Binds reactively to `PersonalizationViewModel.toggleFavorite()`.
   - Accessible TalkBack state: `"Mark as favorite"` / `"Remove from favorites"`.
2. **1–5 Star Rating Bar:**
   - Placed below track title in `NowPlayingSheet` and inside track row overflow menus.
   - Displays 5 outlined stars; tapping star 4 sets rating to 4 via `PersonalizationViewModel.setRating(4)`. Tapping an active star clears rating.
3. **Favorites Smart Playlist:**
   - Top-anchored playlist card in `PlaylistsScreen` displaying all tracks marked favorite via `PersonalizationViewModel.observeFavoriteTracks()`.
