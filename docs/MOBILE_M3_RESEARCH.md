# Harmonia Mobile Phase M3: Reference Research & Competitive Analysis

## 1. Executive Summary & Baseline Anchor

Phase M3 builds upon the fully verified and locked native playback engine completed in Phase M2 (Git checkpoint `3578e58`, tag `restore-point/mobile-m2-complete`). The playback engine provides verified offline playback for MP3, FLAC, M4A/AAC, and WAV formats, background playback via `HarmoniaPlaybackService` (`MediaSessionService`), authoritative queue synchronization with AndroidX Media3 (`1.9.2`), and Room SQLite persistence.

The objective of Phase M3 is to design and structure the user-facing mobile application experience:
- Intuitive navigation (Home, Library, Search, Playlists, Settings)
- Responsive library browsing with album/artist/track hierarchy
- Ergonomic Now Playing & persistent Mini Player UX
- Authoritative queue management (Up Next, reordering, item removal)
- Local playlist authoring (creation, reordering, deterministic ordering)
- Fast on-device local search across all metadata entities
- Offline artwork presentation with deterministic fallback

To ensure clean-room compliance, architectural rigor, and genuine mobile usability, this research pass audits eight reference open-source music player implementations located in `RESOURCES/`.

---

## 2. Audited Reference Repositories

| Repository | Path | Tech Stack | Architectural Paradigm |
|---|---|---|---|
| **Auxio** | `RESOURCES/Auxio-dev` | Kotlin, Android Views, ExoPlayer | Custom BottomSheetBehavior, Multi-level UI, BetterShuffleOrder |
| **LibrePlayer / LocalTracklist** | `RESOURCES/LocalTracklist-master` | Kotlin, Jetpack Compose, ExoPlayer | Tabbed library, audio details sheet, collection viewmodels |
| **Resonate** | `RESOURCES/resonate-main` | Kotlin, Jetpack Compose, Media3 | FastScroll rail, Material You Chroma theme, ModalBottomSheet queue |
| **Booming Music** | `RESOURCES/BoomingMusic-master` | Kotlin, Jetpack Compose, Media3 | Multi-tab library, TTML lyrics parser, Coil image loader |
| **Phoebe** | `RESOURCES/Phoebe-main` | Kotlin Multiplatform, Compose MP | Multiplatform domain, waveform seekbars, 31-band graphic EQ |
| **Flick** | `RESOURCES/Flick-main` | Flutter, Rust Bridge | Cross-platform Rust audio core, smooth gesture sheets |
| **Wavio** | `RESOURCES/wavio-main` | React Native, Expo, TypeScript | Monorepo mobile client, cross-platform audio bindings |
| **Aurora** | `RESOURCES/aurora-main` | C++, Java, JNI, OpenSL/AAudio | Custom low-level audio engine, high-performance C++ decoder |

---

## 3. Thematic Analysis Across Architectural Dimensions

### A. Navigation Architecture
- **Resonate**: Implements a `RootScaffold` with a persistent bottom navigation bar hosting four primary tabs (`Home`, `Library`, `Search`, `You/Settings`). Type-safe routes via Jetpack Navigation Compose (`HomeRoute`, `LibraryRoute`, `SearchRoute`). A persistent `MiniPlayer` card sits directly above the bottom navigation tabs.
- **Auxio**: Utilizes a single activity with fragments, driving navigation via a custom multi-level bottom sheet. Navigation state is kept minimal, but deep-linking into albums and artists pushes fragment transactions.
- **LibrePlayer**: Uses Compose tabs for top-level collections (`Songs`, `Albums`, `Artists`, `Playlists`, `Folders`). Navigation to detail views replaces the main content while keeping bottom chrome visible.
- **Harmonia Decision**: Adopt a 5-destination bottom navigation bar: **Home**, **Library**, **Search**, **Playlists**, and **Settings**. Detail routes (`AlbumDetail`, `ArtistDetail`, `PlaylistDetail`) push onto the Compose `NavHost` backstack, smoothly fading in while preserving the mini-player across destinations.

### B. Home Screen Organization
- **Resonate**: Displays a dynamic greeting, "Shuffle All" quick action, "Heavy Rotation" carousel, and "Recently Added" horizontal track cards.
- **Booming Music**: Combines "Recently Played" horizontal carousel, "Most Played", "Recently Added", and fast shortcuts to library categories.
- **Auxio**: Avoids a heavy algorithm-driven home screen; defaults to library tabs, but offers a customizable start tab.
- **Harmonia Decision**: Home serves as the personal launchpad:
  1. *Quick Play Bar*: "Shuffle All" and "Resume Last Session".
  2. *Recently Played*: Horizontal card row derived from recent `ListeningEvent` records (last 10 unique tracks/albums).
  3. *Recently Added*: Tracks/albums with the latest file creation/import timestamps.
  4. *Library Shortcuts*: Quick chips linking to Artists, Albums, and Playlists.
  5. *Empty State*: Clean, intentional onboarding encouraging music library scanning.

### C. Library Organization
- **LibrePlayer**: Organizes library via top tab rows (`Tracks`, `Albums`, `Artists`, `Playlists`). Each tab provides dedicated sorting options (Title, Artist, Date Added, Duration).
- **Resonate**: Introduces a high-performance `FastScrollRail` on the right screen edge for large lists ($> 100$ items), enabling instant thumb-dragging to alphabetical sections with haptic feedback.
- **Harmonia Decision**: Implement standard tabbed Library views:
  - **Tracks**: Full alphabetical listing with track title, artist, duration, and overflow action sheet.
  - **Albums**: 2-column adaptive grid displaying square artwork, album title, artist, and release year.
  - **Artists**: List with circular artist avatar/placeholder, artist name, and track/album count.
  - Provide sorting dialogs (Title, Artist, Recently Added, Year) and alphabetical quick index.

### D. Album, Artist, and Track Presentation
- **Resonate & LibrePlayer**:
  - *Album Detail*: Large hero header with high-resolution artwork, album metadata, total tracks and runtime, followed by sequential track listing (`discNumber`, `trackNumber`, `title`, `duration`).
  - *Artist Detail*: Hero header with artist name, "Play" and "Shuffle" buttons, horizontal carousel of discography albums, followed by top/all tracks.
- **Harmonia Decision**: Maintain strict multi-disc awareness. Display disc headers (`Disc 1`, `Disc 2`) when `discNumber > 1`. Track rows show `trackNumber` aligned left, track title and subtitle (artist/format), and duration aligned right.

### E. Now Playing UX
- **Resonate**: Full-screen modal that overlays the active scaffold with 400ms emphasized cubic-bezier motion. Features dominant square artwork, title/artist marquee text, scrubbable slider, playback controls, shuffle/repeat toggles, and buttons for Queue and Track Details.
- **Auxio**: Cohesive bottom sheet that smoothly interpolates between compact mini-player card and full canvas, featuring a custom wavy seekbar.
- **Booming Music**: Provides lyrics toggle and sound output diagnostics.
- **Harmonia Decision**: Full-screen modal overlay expanding from Mini Player:
  - Prominent square album artwork with rounded corners (16dp).
  - Title and Artist with marquee scrolling for long strings.
  - High-precision slider with elapsed time (left) and remaining time (right).
  - Central control cluster: Shuffle, Previous, Large Play/Pause button, Next, Repeat mode toggle.
  - Header actions: Down arrow (collapse), Track Info/Details modal.
  - Bottom action bar: Queue button (opens Up Next sheet).

### F. Mini-Player UX
- **Resonate**: Persistent 64dp card docked directly above the bottom navigation bar. Displays thumbnail artwork (48dp), track title, artist, play/pause button, and skip next button. Contains a thin 2dp linear progress indicator along the top edge.
- **LocalTracklist**: Floating card with elevation and subtle shadow, dismissible on swipe-down or long press.
- **Harmonia Decision**: Persistent docked card (64dp) above the navigation bar:
  - 48dp square artwork thumbnail.
  - Column with track title and artist name (truncated with ellipsis).
  - Play/Pause icon button and Next icon button.
  - Top 2dp accent progress indicator showing real-time playback fraction.
  - Tapping anywhere on the card triggers the expand animation to Now Playing.

### G. Queue UX & Deterministic Mechanics
- **Auxio**: Implements `BetterShuffleOrder` which solves ExoPlayer's flaw of randomly scattering "Play Next" insertions across the shuffled timeline.
- **Resonate**: Displays a `QueueSheet` modal with clear demarcation between "Currently Playing" and "Up Next". Supports drag-and-drop reordering, swipe-to-dismiss track removal, and a "Clear Queue" action.
- **Harmonia Decision**:
  - Dual-layer queue architecture established in M0/M2 research:
    1. *Currently Playing*: Active track with progress indicator.
    2. *Explicit User Queue ("Up Next")*: Items added via "Play Next" or "Add to Queue". Supports drag-to-reorder and swipe-to-remove.
    3. *Context Queue*: Underlying album or playlist.
  - Queue management actions: Reorder items, Remove item, Clear queue, Save queue as new playlist.
  - Synchronized authoritatively with Media3 timeline.

### H. Search UX
- **Resonate**: Search screen with auto-focus text field, 300ms debounce, and grouped instant results (`Songs`, `Albums`, `Artists`, `Playlists`).
- **Booming Music**: Categorized results with filter chips (`All`, `Songs`, `Albums`, `Artists`) and search query history chips.
- **Harmonia Decision**: Local SQLite FTS / Room query search:
  - Instant debounced query execution ($300\text{ms}$).
  - Grouped result sections: Top Match, Tracks, Albums, Artists, Playlists.
  - Empty search state displays recent search terms and quick genre/category chips.

### I. Playlist UX
- **Resonate**: Simple Room-backed playlists with `AddToSheet` dialog invoked from any track overflow menu.
- **LibrePlayer**: Supports M3U export/import in addition to internal SQLite playlists.
- **Harmonia Decision**:
  - Deterministic SQLite Room schema: `playlists` and `playlist_items` with integer `position` column.
  - User capabilities:
    - Create playlist (name dialog).
    - Add track to playlist (via track menu bottom sheet).
    - Remove track from playlist.
    - Drag-and-drop reorder playlist items.
    - Rename and Delete playlist.

### J. Artwork Handling
- **Booming Music**: Uses Coil with dedicated fetchers for `content://` and file URIs, custom memory and disk cache policies.
- **Resonate**: Extracts dominant colors via Palette API (`Chroma.kt`) and clamps chroma/luminance to guarantee WCAG 4.5:1 text contrast.
- **Harmonia Decision**:
  - Authoritative artwork identity based on SHA-256 hash matching Harmonia Core (`artwork_hash`).
  - Cache local artwork files in `context.filesDir/artwork/{hash}.jpg`.
  - Display elegant geometric gradient placeholders when artwork is unavailable.
  - Coil image loading with disk caching and crossfade transitions.

### K. Offline States
- **LibrePlayer & Auxio**: Zero network requests. All data is sourced locally from SQLite and MediaStore/internal files.
- **Harmonia Decision**: Harmonia Mobile is offline-first. No network indicators, degraded cloud banners, or remote fallbacks are present in M3.

### L. Empty States
- **Resonate**: High-contrast graphic illustrations with clear call-to-action text:
  - Empty Library: "No music found. Scan a folder to add music."
  - Empty Queue: "Your queue is empty. Add songs to start listening."
  - Empty Playlist: "This playlist has no songs yet."
  - Empty Search: "No matches found for '{query}'."
- **Harmonia Decision**: Implement branded, aesthetically aligned empty states using `ObsidianSpace` / `AcousticTeal` styling and clear primary actions.

### M. Error States
- **Resonate**: Dispatches transient Snackbar on playback error ("Unable to play track: file unreadable") while maintaining engine stability.
- **Harmonia Decision**: Non-fatal error presentation:
  - Visual error indicator in Now Playing / Mini Player.
  - Informative bottom Snackbar for unreadable or missing media files.
  - Engine automatically skips to next valid queue item without process termination.

### N. Android System Media Integration
- **Harmonia M2 Baseline**: Full `MediaSessionService` and `ExoPlayer` integration with lock-screen widgets, media notification, and hardware media key routing.
- **M3 Enhancement**: Ensure `MediaMetadataCompat` contains high-resolution artwork URIs so lock-screen and notification canvases match the in-app experience.

### O. Accessibility
- All interactive controls must feature `contentDescription` attributes.
- Minimum touch target sizing of $48\text{dp} \times 48\text{dp}$ for all interactive buttons.
- Minimum text contrast ratio of 4.5:1 (WCAG AA) against dark backgrounds (`ObsidianSpace` `#0D0E11` and `AcousticCharcoal` `#16181D`).

### P. Performance Patterns
- `LazyColumn` and `LazyVerticalGrid` must define explicit stable keys: `key = { it.id }`.
- Prevent unnecessary recompositions by wrapping state in immutable data models.
- Room queries expose reactive `Flow<List<T>>` with SQLite Write-Ahead Logging (WAL) mode.
- Debounced search queries ($300\text{ms}$) prevent redundant database operations.

### Q. State Management
- Unidirectional Data Flow (UDF) via Android ViewModel and Kotlin `StateFlow`.
- Presentation layer observes `StateFlow` via `collectAsStateWithLifecycle()`.
- User interactions dispatch immutable intent events to ViewModels.

### R. Persistence Boundaries
- Presentation layer interacts solely with domain Repositories and `PlaybackPort`.
- Zero direct DAO or database invocations from Compose components.
- Zero raw filesystem path manipulations in UI layer.

---

## 4. Cross-Project Comparative Matrix

| Project | Useful Idea | Architectural Strength | Weakness | Harmonia Adoption Verdict | Implementation Notes |
|---|---|---|---|---|---|
| **Resonate** | `FastScrollRail`, Chroma Palette clamping, `RootScaffold` overlay | Clean Jetpack Compose architecture; type-safe navigation | Queue reordering relies on raw index swaps without persistent priority | **ADOPT (Scaffold, FastScroll, Overlay)** | Integrate Compose `AnimatedVisibility` overlay and A-Z fast scroller |
| **Auxio** | `BetterShuffleOrder` contiguous queue insertion | Superior shuffle algorithm prevents scattered "Play Next" | Android Views / Fragment architecture; complex custom views | **ADOPT (Queue logic)** | Implement contiguous queue insertion in domain queue logic |
| **Booming Music** | Library multi-tab views, Coil image loader, Coil keying | Rich feature set; native Android Compose | High coupling between UI and ExoPlayer controller | **ADOPT (Coil integration & Library layout)** | Keep decoupled via `PlaybackPort` boundary |
| **LibrePlayer** | Audio Details Modal, collection detail screens | Deep audio format inspection; clean UI | Minimal animation polish; rigid tab structure | **ADOPT (Audio Details Sheet)** | Expose technical format details via a dedicated bottom sheet |
| **Phoebe** | Audio-reactive waveforms, 31-band EQ | Multiplatform Compose; rich visualizers | High complexity; experimental multiplatform audio dependencies | **DEFER (Visualizers/DSP to M6)** | Retain standard high-precision slider for M3 |
| **Flick** | Smooth gesture physics | Rust audio core performance | Flutter UI overhead; cross-language bridge complexity | **REJECT (Flutter/Rust stack)** | Harmonia uses native Kotlin/Compose |
| **Wavio** | Sleek glassmorphic tab bar | Modern visual aesthetic | React Native bridge; non-native audio pipeline | **REJECT (RN stack)** | Native Android provides superior latency and background stability |
| **Aurora** | Native C++ audio engine | Bit-perfect hardware output | Extreme maintenance burden; lacks modern Media3 integration | **REJECT (C++ engine)** | Media3 ExoPlayer provides full format support and platform compliance |

---

## 5. Clean-Room & Licensing Compliance

All audited repositories were inspected strictly for architectural patterns, component layouts, and UX behavior:
- **Zero code copying**: No source code, layout XML, or proprietary assets were copied from audited repositories.
- **Clean-room specification**: All Harmonia M3 architecture and component specifications are authored independently to satisfy Harmonia's design system (`ObsidianSpace`, `AcousticCharcoal`, `AcousticTeal`).
- **Open-source compatibility**: All reference insights originate from permissively licensed open-source projects (GPL-3.0, Apache 2.0, MIT) used strictly for educational and architectural benchmarking.
