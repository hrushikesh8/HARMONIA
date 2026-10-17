# Harmonia Mobile — Preliminary Screen, Flow & State Inventory

**Document Classification:** Architectural Specification  
**Status:** COMPLETE & PROPOSED (Stage 5A)  
**Author:** Principal Mobile UX Researcher & Android UI Architect for Harmonia  
**Repository Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  

---

## 1. Inventory Classification Taxonomy

Every screen, modal sheet, dialog, and state in this inventory is categorized using standard criteria:

1. **Existing and Verified (`[VERIFIED-SRC]`):** Fully implemented in working-tree Compose code and verified via source inspection.
2. **Existing with Usability Improvements Proposed (`[PROPOSED]`):** Implemented in working-tree code, but specific ergonomic, visual, or interaction enhancements are proposed.
3. **Proposed New Screen / Modal (`[PROPOSED]`):** Not currently implemented in the Compose layer; proposed based on reference research and user task requirements.
4. **Deferred / Out of Scope (`[DEFERRED]`):** Explicitly excluded from current milestones (e.g. tag editing, cloud streaming, AI companion, iOS).

---

## 2. Comprehensive Screen & Component Inventory

### 2.1 Primary Top-Level Navigation Destinations

| Screen Name | Current Status | Primary Source File | Primary User Task | Architecture & Grouping Recommendation |
| :--- | :--- | :--- | :--- | :--- |
| **Home (Listening Dashboard)** | `[PROPOSED]` (Enhance Existing `[VERIFIED-SRC]`) | [`HomeScreen.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/home/HomeScreen.kt) | Quick resume, recent listening history, shuffle all, volume stats. | Top-Level Tab 1 (`HOME`). Retain as default landing page. Add quick-access Favorites carousel (Phase 4). |
| **Library (Catalog Root)** | `[PROPOSED]` (Enhance Existing `[VERIFIED-SRC]`) | [`LibraryScreen.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/library/LibraryScreen.kt) | Browse catalog via Tracks, Albums, and Artists tabs with sorting. | Top-Level Tab 2 (`LIBRARY`). Retain SecondaryTabRow. Add `FastScrollRail` and drill-down routes to details. |
| **Playlists (Collections)** | `[PROPOSED]` (Enhance Existing `[VERIFIED-SRC]`) | [`PlaylistsScreen.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playlists/PlaylistsScreen.kt) | View, create, rename, and delete custom user playlists. | Top-Level Tab 3 (`PLAYLISTS`). Add smart playlist anchors (Favorites, Top Rated). |
| **Search (Indexed Catalog)** | `[VERIFIED-SRC]` | [`SearchScreen.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/search/SearchScreen.kt) | Real-time text search across tracks, albums, artists, playlists. | Top-Level Tab 4 (`SEARCH`). Wire album/artist taps to dedicated detail views. |
| **Settings (System & Audio)** | `[VERIFIED-SRC]` | [`SettingsScreen.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/settings/SettingsScreen.kt) | SAF folder management, 5-band EQ, scan triggers, telemetry. | Top-Level Tab 5 (`SETTINGS`). Keep as system configuration hub. |

---

### 2.2 Secondary & Detail Screens

| Screen / Flow Name | Current Status | Existing Evidence | Proposed Enhancement / User Task |
| :--- | :--- | :--- | :--- |
| **Album Detail Screen** | `[PROPOSED]` (New Screen) | Backed by `TrackRepository.getTracksByAlbumId()` `[VERIFIED-SRC]` | Dedicated view displaying large album artwork, release year, artist link, complete tracklist with track numbers and durations, and Play All / Shuffle All buttons. |
| **Artist Detail Screen** | `[PROPOSED]` (New Screen) | Backed by `TrackRepository.getTracksByArtistId()` `[VERIFIED-SRC]` | Dedicated view displaying artist name, total tracks, album discography grid, and chronological track list. |
| **Playlist Detail Screen** | `[PROPOSED]` (Enhance Existing `[VERIFIED-SRC]`) | [`PlaylistDetailScreen.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playlists/PlaylistDetailScreen.kt) | Add drag-and-drop handles for manual track reordering, track removal context menu, and playlist playback actions. |
| **Folder Browser Screen** | `[DEFERRED]` | Backed by `StorageSource` paths `[VERIFIED-SRC]` | Physical directory tree explorer. Deferred to future power-user milestone to avoid initial navigation complexity. |

---

### 2.3 Playback Chrome & Modal Sheets

| Component Name | Current Status | Primary Source File | Interaction Design & Enhancements |
| :--- | :--- | :--- | :--- |
| **Mini-Player** | `[PROPOSED]` (Enhance Existing `[VERIFIED-SRC]`) | [`MiniPlayer.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/MiniPlayer.kt) | Persistent 56dp card docked above bottom tabs. Add horizontal swipe gesture to skip tracks (Auxio pattern); maintain 48dp touch targets. |
| **Now Playing Sheet** | `[PROPOSED]` (Enhance Existing `[VERIFIED-SRC]`) | [`NowPlayingSheet.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/NowPlayingSheet.kt) | Full-screen modal canvas. Replace text buttons (`SHF`/`RPT`) with stateful vector icons; add audio format badge (`FLAC 24/96`); integrate Phase 4 Favorite heart and Star rating bar. |
| **Queue Sheet / Drawer** | `[PROPOSED]` (Enhance Existing `[VERIFIED-SRC]`) | `QueueView` in `NowPlayingSheet.kt` `[VERIFIED-SRC]` | Expand from basic list into dedicated drawer supporting drag-to-reorder, swipe-to-dismiss, and visual separation between Layer 1 (Up Next) and Layer 2 (Context Queue). |
| **Audio Signal Inspector** | `[PROPOSED]` (New Modal Sheet) | Backed by `LocalFile` & `HarmoniaPlayerController` `[VERIFIED-SRC]` | On-demand technical modal sheet (LocalTracklist pattern) displaying container format, codec, bitrate, sample rate, bit depth, channel configuration, and active AudioTrack sink. |
| **Sleep Timer Dialog** | `[VERIFIED-SRC]` | `SleepTimerDialog` in `NowPlayingSheet.kt` `[VERIFIED-SRC]` | Fully implemented modal dialog with presets (15m, 30m, 45m, 60m, End of Track, Cancel) and live remaining-time countdown display. |

---

### 2.4 Personalization Phase 4 Components (Future Design Requirement)

> [!IMPORTANT]
> **Personalization Implementation Notice:**
> Personalization Phase 4 is not authorized for code implementation in Stage 5A. The following items represent research and specification requirements only:

| Personalization Component | Target Screen / Placement | Underlying Working-Tree Dependency | Scope Status |
| :--- | :--- | :--- | :--- |
| **Favorite Heart Toggle** | `NowPlayingSheet` & `TrackRowCard` | `PersonalizationViewModel.toggleFavorite()` `[VERIFIED-SRC]` | `[PROPOSED]` (Phase 4 Design Req) |
| **1–5 Star Rating Bar** | `NowPlayingSheet` & Context Menu | `PersonalizationViewModel.setRating(stars)` `[VERIFIED-SRC]` | `[PROPOSED]` (Phase 4 Design Req) |
| **Favorites Library Filter** | `LibraryScreen` & `HomeScreen` | `PersonalizationViewModel.observeFavoriteTracks()` `[VERIFIED-SRC]` | `[PROPOSED]` (Phase 4 Design Req) |
| **Top Rated Smart Playlist**| `PlaylistsScreen` | `PersonalizationViewModel.observeTopRatedTracks()` `[VERIFIED-SRC]` | `[PROPOSED]` (Phase 4 Design Req) |

---

### 2.5 State Matrix (Edge Cases & Degradation)

| State Category | Existing Implementation | Observed Evidence | Proposed UX Treatment |
| :--- | :--- | :--- | :--- |
| **First-Run Onboarding** | None (Jumps to empty state) | `[INFERRED]` | Welcoming initial card explaining offline local-first model with a prominent "Select Music Folder" button. |
| **Empty Library** | `EmptyLibraryState` composable | `LibraryScreen.kt#L550` `[VERIFIED-SRC]` | Clear call-to-action redirecting to Settings SAF folder selection. |
| **Scan In-Flight** | `ScanningBanner` & `ScanningState` | `LibraryScreen.kt#L459` `[VERIFIED-SRC]` | Non-blocking subtle progress bar in top app bar or library header. |
| **Scan Error / Failure** | `ScanFailedState` & `ScanErrorBanner` | `LibraryScreen.kt#L490` `[VERIFIED-SRC]` | Informative error card with explicit "Retry Scan" action. |
| **Missing Artwork** | `HarmoniaArtwork` placeholder | `HarmoniaArtwork.kt` `[VERIFIED-SRC]` | Clean geometric musical note icon on `StudioSlate` container. |
| **Storage Unmounted / Inaccessible** | Dimmed row (`0.5f` alpha) + "Storage Unmounted" badge | `LibraryScreen.kt#L702` `[VERIFIED-SRC]` | Visual dimming prevents confusion; track remains in catalog without being purged. |
| **Storage Disconnected During Playback** | Banner in `NowPlayingSheet` | `NowPlayingSheet.kt#L222` `[VERIFIED-SRC]` | Non-fatal banner explaining that reconnecting storage will resume playback. |
| **Audio Permission Denied / Settings Required** | `PermissionRequiredState` card | `LibraryScreen.kt#L389` `[VERIFIED-SRC]` | Clear educational explanation with one-tap link to Android App Settings. |
| **Equalizer Unsupported / Control Preempted** | Status badges in `SettingsScreen` | `SettingsScreen.kt#L390` `[VERIFIED-SRC]` | Explicit status chips (`STANDBY`, `CONTROL PREEMPTED`, `UNSUPPORTED`). |
