# Harmonia Mobile — Navigation Architecture & Adaptive Layout Specification (Stage 5B)

**Document Classification:** Architectural Navigation Specification  
**Status:** COMPLETE & PROPOSED (Stage 5B Gate)  
**Author:** Principal Mobile UX Architect for Harmonia  
**Repository Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  
**Dependencies:** Jetpack Compose, Material 3 Adaptive Layouts, AndroidX Navigation  

---

## 1. Architectural Evaluation: Navigation Paradigms

### 1.1 Current Baseline (`[VERIFIED-SRC]`)
In [`MainActivity.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/MainActivity.kt), top-level tabs and sub-screens are currently coordinated via a local Compose state machine:
```kotlin
var selectedTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }
var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
```
- **Current Behavior:**
  - Tab switching swaps the displayed Composable immediately in a `when (selectedTab)` block.
  - Playlist details are presented conditionally when `selectedPlaylistId != null`.
  - Pressing Android system back from `PlaylistDetailScreen` resets `selectedPlaylistId = null` via manual callback (`onBack = { selectedPlaylistId = null }`).
- **Observed Strengths:** Lightweight, zero external navigation library dependencies, minimal APK size impact.
- **Architectural Limitations:**
  - **No Hierarchical Back-Stack:** If a user navigates `SearchScreen` -> taps an Artist -> taps an Album -> taps a Track, pressing Android System Back does not naturally unwind the navigation history; it exits the app unless complex nested state variables are manually maintained.
  - **Predictive Back Gestures (Android 14+):** Cannot interpolate cross-screen slide animations without a navigation component that supports predictive back callbacks.
  - **Deep-Link Fragility:** Handling external intents (from Android Auto or notification clicks) requires custom imperative dispatch rather than declarative route arguments.

### 1.2 Proposed Migration Target: `Navigation Compose` (`[PROPOSED]`)
- **Evaluation:** Adopt `androidx.navigation:navigation-compose` (2.8+) utilizing Kotlin Serialization type-safe routes (`@Serializable` objects and data classes).
- **Approval Gate & Dependency Status:** **STRICTLY UNAPPROVED AND UNIMPLEMENTED.** The dependency `androidx.navigation:navigation-compose` is NOT present in `build.gradle.kts` and cannot be added without explicit stakeholder authorization (Gate 1 in [`OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md)).
- **Trade-Off Analysis:**
  - *Benefits:* Full native integration with Android system back gestures, automatic predictive back transitions, isolated ViewModel lifecycles per destination, and standardized deep linking.
  - *Costs & Risks:* Adds external library footprint (~500KB); requires refactoring `MainAppContainer` in `MainActivity.kt` into a `NavHost`; requires explicit state hoisting for scroll offsets and search queries.
  - *Playback Decoupling Invariant:* Playback state remains owned globally by `HarmoniaPlaybackService` and `PlaybackViewModel` via `ServiceLocator`. Wrapping presentation in a `NavHost` does not affect audio continuity.

---

## 2. Destination Hierarchy & Routing Contracts (`[PROPOSED]`)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        TOP-LEVEL DESTINATIONS                          │
├──────────────┬───────────────────────────────┬─────────────────────────┤
│ Route Object │ Presentation Composable       │ Destination Type        │
├──────────────┼───────────────────────────────┼─────────────────────────┤
│ `Route.Home` │ `HomeScreen`                  │ Top-Level Tab (Default) │
│ `Route.Library`│ `LibraryScreen`             │ Top-Level Tab           │
│ `Route.Playlists`│ `PlaylistsScreen`         │ Top-Level Tab           │
│ `Route.Search`│ `SearchScreen`               │ Top-Level Tab           │
│ `Route.Settings`│ `SettingsScreen`           │ Top-Level Tab           │
└──────────────┴───────────────────────────────┴─────────────────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                     NESTED DETAIL DESTINATIONS                         │
├────────────────────────────────┬─────────────────────┬─────────────────┤
│ Route Object / Arguments       │ Composable          │ Entry Points    │
├────────────────────────────────┼─────────────────────┼─────────────────┤
│ `Route.AlbumDetail(albumId)`   │ `AlbumDetailScreen` │ Library, Search │
│ `Route.ArtistDetail(artistId)` │ `ArtistDetailScreen`│ Library, Search │
│ `Route.PlaylistDetail(playlistId)`│ `PlaylistDetailScreen`│ Playlists, Home │
└────────────────────────────────┴─────────────────────┴─────────────────┘
```

---

## 3. Back-Stack, Return & State Preservation Specifications

### 3.1 Top-Level Tab Switching & State Management Requirements
When switching between top-level tabs (`Home`, `Library`, `Playlists`, `Search`, `Settings`):
- Navigation invocation must specify:
  ```kotlin
  navController.navigate(tab.route) {
      popUpTo(navController.graph.findStartDestination().id) { saveState = true }
      launchSingleTop = true
      restoreState = true
  }
  ```
- **State Preservation Prerequisites (Non-Automatic):**
  - **Scroll Position Restoration:** Scroll offsets in `LazyColumn` and `LazyVerticalGrid` are **NOT automatically saved** merely by calling `restoreState = true`. The presentation layer must either:
    1. Hoist `LazyListState` instances into their respective ViewModels (`LibraryViewModel`, `PlaylistsViewModel`), or
    2. Maintain `rememberSaveable(saver = LazyListState.Saver)` at the screen scope.
  - **Search Query Preservation:** When navigating from `SearchScreen` to another tab and returning, the text query and search results are preserved because `SearchViewModel` holds the reactive `StateFlow`. If `SearchViewModel` is scoped to the `NavBackStackEntry`, destination swapping will destroy it unless it is scoped to the navigation graph or root activity. The specification mandates scoping `SearchViewModel` to the graph root.

### 3.2 Detail Navigation & System Back Behavior
- Navigating into `AlbumDetailScreen`, `ArtistDetailScreen`, or `PlaylistDetailScreen` pushes the destination onto the active back-stack.
- Pressing **System Back** or the **TopAppBar Back Arrow**:
  - Pops the detail screen and returns to the exact prior scroll position in `LibraryScreen`, `SearchScreen`, or `PlaylistsScreen`.
  - Does NOT alter, pause, or reinitialize audio playback.

### 3.3 Playback Persistence During Navigation
- **Single-Authority Invariant:** Playback is owned by `HarmoniaPlaybackService` via ExoPlayer. The `PlaybackViewModel` is injected at the root `MainAppContainer`.
- Navigating between any screens, opening sheets, or rotating the device has **zero effect** on playback state, seek position, or active queue indices.

### 3.4 Predictive Back Support (Android 14+)
- The `NowPlayingSheet` modal and all detail screens (`AlbumDetailScreen`, etc.) must handle back gestures gracefully:
  - Swiping from the edge shows the underlying screen scaling up slightly while the active detail screen slides away.
  - Canceling the swipe restores the active screen without graphical artifacts.

---

## 4. Responsive & Adaptive Breakpoint Behavior

Harmonia adapts gracefully across window size classes following Material 3 Adaptive Layout guidelines:

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                            ADAPTIVE LAYOUT ARCHITECTURE                                     │
├──────────────────┬──────────────────────┬──────────────────────┬────────────────────────────┤
│ Window Size      │ Navigation Structure │ Library / Catalog    │ Now Playing Sheet          │
├──────────────────┼──────────────────────┼──────────────────────┼────────────────────────────┤
│ **Compact**      │ Persistent Bottom    │ 1-column Track list  │ Full-screen Modal Bottom   │
│ (< 600dp)        │ `NavigationBar`      │ 2-column Album grid  │ Sheet with vertical flow   │
│ (Standard Phone) │ (64dp height)        │                      │                            │
├──────────────────┼──────────────────────┼──────────────────────┼────────────────────────────┤
│ **Medium**       │ Vertical             │ 1-column Track list  │ Modal Bottom Sheet or      │
│ (600dp - 840dp)  │ `NavigationRail`     │ 3-column Album grid  │ Centered Dialog (560dp max)│
│ (Foldable/Tablet)│ on start edge        │                      │                            │
├──────────────────┼──────────────────────┼──────────────────────┼────────────────────────────┤
│ **Expanded**     │ Permanent Navigation │ Two-Pane Master-     │ Right Supporting Pane or   │
│ (> 840dp)        │ Drawer or Rail       │ Detail Scaffold      │ Persistent Bottom Console  │
│ (Desktop/Tablet) │                      │ (List + Detail)      │                            │
└──────────────────┴──────────────────────┴──────────────────────┴────────────────────────────┘
```

### 4.1 Compact Layouts (Handsets in Portrait, < 600dp)
- Top-level destinations occupy a persistent **Bottom Navigation Bar** with 5 items.
- Docked `MiniPlayer` is pinned directly above the bottom navigation bar.
- Albums render in a **2-column vertical grid**.
- Now Playing is presented as a full-screen modal bottom sheet.

### 4.2 Medium Layouts (Landscape Phones & Small Foldables, 600dp – 840dp)
- The bottom navigation bar transitions to a **Navigation Rail** positioned on the left/start edge, preserving vertical screen height for tracklists.
- The `MiniPlayer` remains pinned at the bottom, extending from the navigation rail to the end edge.
- Albums render in a **3-column vertical grid**.

### 4.3 Expanded Layouts (Large Tablets & Desktop Displays, > 840dp)
- Navigation uses a **Permanent Navigation Rail or Drawer**.
- Catalog browsing uses a **ListDetailPaneScaffold**:
  - Left pane: Library tracklist, album grid, or playlist list.
  - Right pane: Active album details, artist discography, or active playback queue.
- This eliminates deep back-stack navigation on wide screens, allowing direct simultaneous browsing and queue editing.
