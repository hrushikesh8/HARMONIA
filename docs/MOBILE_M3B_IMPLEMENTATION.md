# Harmonia Mobile — Phase M3-B Implementation Report
**Search + Home Completion + Offline Artwork Architecture**

## 1. Executive Summary

Phase M3-B completes the discovery experience of Harmonia Mobile strictly offline, building on the M1 Android foundation, the authoritative M2 native playback engine, and the M3-A database/navigation baseline.

Key accomplishments in Phase M3-B:
1. **Local Search**: Fast, debounced (250ms), Room SQLite-backed search across Tracks, Albums, Artists, and Playlists. Supports case-insensitive partial substring matching, whitespace normalization, empty query guards, and clear actions.
2. **Search UX & Routing**: Polished SearchScreen with leading icons, 48dp clear button, categorized result rows with artwork thumbnails and track counts, zero-result and empty prompt states, routing tracks directly through the M2 `PlaybackViewModel` -> `PlaybackPort` boundary, and routing containers to Library/Playlists tabs.
3. **Home Screen Completion**: Fully backed by real, local Room database tables. Implements:
   - **Resume Card**: Displays the persisted last-played track and position from `mobile_playback_state` with immediate resume playback, or an informative empty state (`NoResumeCard`) if no active track exists.
   - **Quick Play**: Dedicated "Resume" and "Shuffle All" actions communicating strictly through the M2 playback boundary.
   - **Category Shortcuts**: Navigates to Artists, Albums, and Playlists tabs with live catalog counts.
   - **Recently Played**: Factual history derived from `listening_events` (START / COMPLETE / SEEK events), deduplicated for presentation while preserving underlying audit records.
   - **Recently Added**: Factual catalog tracks sorted by authoritative `created_at` timestamp.
4. **Artwork Engine & Security**: Finished `ArtworkProvider` tied to Core SHA-256 hash identity. Enforces strict internal sandboxing (`context.filesDir/artwork/<hash>.jpg`), path-traversal regex validation (`^[a-fA-F0-9]{1,64}$`), in-memory `LruCache<String, Bitmap>(60)`, atomic file replacement, graceful null handling for missing/corrupt files, and bespoke acoustic gradient fallbacks. Zero arbitrary filesystem paths are exposed to Compose.
5. **Quality & Verification**:
   - **JVM Unit Tests**: 28 passing unit tests (testing normalization, debouncing, empty query, categorized aggregation, DAOs, ViewModels).
   - **Connected Android Tests**: 29 passing instrumented tests on Android Virtual Device (`medium_phone` / AVD 16), confirming real audio engine playback, Room schema migration 1->2, CRUD operations, Room search queries, and Compose UI interactions.
   - **Core Pytest Regression**: 173/173 tests passing.
   - **Zero Network Exposure**: No `INTERNET` permission, no network clients, 100% offline.

---

## 2. Search Architecture

The search flow follows pure unidirectional data flow:
```
SearchScreen (Compose UI)
  │ (User types query, debounced by 250ms)
  ▼
SearchViewModel (StateFlow<SearchUiState>)
  │ (Combines 4 distinct flows: tracks, albums, artists, playlists)
  ▼
SearchRepository (OfflineSearchRepository)
  │ (Validates and trims whitespace, returns flowOf(emptyList()) for blank)
  ▼
HarmoniaDatabase (Room SQLite WAL)
  ├── TrackDao.searchTracks(query)
  ├── AlbumDao.searchAlbums(query)
  ├── ArtistDao.searchArtists(query)
  └── PlaylistDao.searchPlaylists(query)
```

### Search Queries
Queries use normalized SQLite `LIKE` clauses with `COLLATE NOCASE` and relational subqueries without speculative full-text indexing (FTS):

- **Tracks**:
  ```sql
  SELECT * FROM mobile_tracks 
  WHERE title LIKE '%' || :query || '%'
     OR artist_id IN (SELECT id FROM mobile_artists WHERE name LIKE '%' || :query || '%')
     OR album_id IN (SELECT id FROM mobile_albums WHERE title LIKE '%' || :query || '%')
  ORDER BY title COLLATE NOCASE ASC
  ```
- **Albums**:
  ```sql
  SELECT * FROM mobile_albums 
  WHERE title LIKE '%' || :query || '%' 
     OR artist_id IN (SELECT id FROM mobile_artists WHERE name LIKE '%' || :query || '%')
  ORDER BY title COLLATE NOCASE ASC
  ```
- **Artists**:
  ```sql
  SELECT * FROM mobile_artists 
  WHERE name LIKE '%' || :query || '%' 
     OR (sort_name IS NOT NULL AND sort_name LIKE '%' || :query || '%')
  ORDER BY sort_name COLLATE NOCASE ASC
  ```
- **Playlists**:
  ```sql
  SELECT * FROM mobile_playlists 
  WHERE name LIKE '%' || :query || '%' 
     OR (description IS NOT NULL AND description LIKE '%' || :query || '%')
  ORDER BY name COLLATE NOCASE ASC
  ```

---

## 3. Home Screen Data Sources

All sections on `HomeScreen` use genuine local database flows via `HomeViewModel`:

| Section | Data Source | Behavior |
| :--- | :--- | :--- |
| **Resume** | `PlaybackStateDao.observePlaybackState()` | Reads `singleton_id = 1`. If `current_track_id` is non-null, loads track metadata and renders `ResumeCard`. If null, renders `NoResumeCard`. |
| **Quick Play** | `PlaybackViewModel.shuffleAll()` / `onResumeClick` | Submits all library tracks shuffled to `PlaybackPort.setQueue(tracks, 0)` and plays. No UI-level playback logic. |
| **Shortcuts** | `TrackDao`, `AlbumDao`, `ArtistDao`, `PlaylistDao` | Displays live counts for Artists, Albums, and Playlists. Clicks switch `NavigationTab`. |
| **Recently Played** | `ListeningEventDao.observeRecentListeningEvents(limit = 20)` | Factual listening history. Groups and deduplicates distinct track IDs for display while leaving underlying immutable events untouched. |
| **Recently Added** | `TrackDao.observeAllTracks()` | Sorted by `TrackEntity.createdAt DESC` limit 10. |
| **Empty State** | `uiState.totalTrackCount == 0` | Renders "Welcome to Harmonia" prompt with an "Open Library" shortcut button. |

---

## 4. Offline Artwork Architecture

Artwork handling is strictly decoupled, sandboxed, and offline:
1. **Hash Identity**: Relies exclusively on Harmonia Core's SHA-256 `artworkHash` (lowercase hex).
2. **Sandboxed Storage**: Artwork files reside strictly inside `context.filesDir/artwork/<hash>.jpg`.
3. **Path Traversal Protection**: All inputs are sanitized using regex `^[a-fA-F0-9]{1,64}$`. Directory traversal sequences (`../`, `/`, `\`) are rejected immediately, returning `null` or `false`.
4. **Caching Strategy**:
   - Level 1: In-memory `LruCache<String, Bitmap>` holding up to 60 decoded bitmaps.
   - Level 2: Local sandboxed disk cache (`.jpg` files written atomically via temp files).
   - Level 3: Graceful fallback. If file is missing, corrupt, or unreadable, `loadBitmap` safely catches `Throwable` and returns `null`.
5. **Presentation**: `HarmoniaArtwork` composable loads bitmaps asynchronously on `Dispatchers.IO` while checking the in-memory LRU cache synchronously for zero-flicker re-renders. On missing or null artwork, it renders a procedural acoustic gradient with an embedded music icon matching Harmonia's dark obsidian theme.

---

## 5. Verification & Test Matrix

All tests executed and passed on local development environment:

### JVM Unit Tests (28 passing)
- `OfflineSearchRepositoryTest`:
  - `search tracks queries track dao and returns domain tracks`
  - `case-insensitive search matches mixed casing`
  - `partial matching finds substring anywhere in title`
  - `blank or whitespace query returns empty list without calling dao`
  - `whitespace normalized query trims before querying`
  - `search albums queries album dao with title and artist join`
  - `search artists queries artist dao by name and sort name`
  - `search playlists queries playlist dao by name and description`
  - `search results maintain deterministic ordering`
- `SearchViewModelTest`:
  - `initial search state is empty and not searching`
  - `onQueryChanged updates query state and clearQuery resets it`
  - `search returns categorized results across all four entities`
  - `search returns empty collections when no entities match`
- `OfflineTrackRepositoryTest`, `OfflinePlaylistRepositoryTest`, `OfflinePlaybackStateRepositoryTest`, `OfflineListeningEventRepositoryTest`, `HarmoniaDomainModelTest`, `AudioLufsNormalizerTest`, etc.

### Connected Android Tests (29 passing on AVD `medium_phone` / Android 16)
- **`ArtworkProviderTest`** (4 tests):
  - `store_and_load_valid_artwork_image_succeeds_and_populates_lru_cache`
  - `store_and_load_corrupt_artwork_safely_returns_null_without_crashing`
  - `path_traversal_and_invalid_artwork_hashes_are_safely_rejected`
  - `nonexistent_artwork_returns_null`
- **`RoomMigrationAndPlaylistDaoTest`** (10 tests):
  - `migration_1_to_2_preserves_m1_m2_data_and_creates_playlist_tables`
  - `playlist_crud_operations_persist_and_update`
  - `playlist_items_maintain_deterministic_position_ordering`
  - `unique_constraint_rejects_duplicate_positions`
  - `deleting_playlist_cascades_and_removes_playlist_items`
  - `track_dao_search_matches_title_artist_and_album_case_insensitively`
  - `album_dao_search_matches_title_and_artist_case_insensitively`
  - `artist_dao_search_matches_name_and_sort_name_case_insensitively`
  - `playlist_dao_search_matches_name_and_description_case_insensitively`
  - `offline_search_repository_executes_categorized_queries_against_room`
- **`HarmoniaNavigationAndLibraryUiTest`** (8 tests):
  - `main_navigation_displays_five_destinations_and_switches_screens`
  - `home_screen_renders_shortcuts_and_empty_state`
  - `home_screen_renders_quick_play_controls_and_shortcuts`
  - `library_screen_renders_tabs_and_sorting_dialog`
  - `playlists_screen_renders_empty_state_and_create_button`
  - `search_screen_renders_search_field_and_local_indicator`
  - `search_screen_typing_query_shows_clear_button_and_clearing_works`
  - `search_screen_no_results_displays_helpful_empty_state`
- **`RealAndroidPlaybackTest`** (7 tests):
  - M2 native audio output and playback lifecycle tests verifying decoder and audio track routing.

### Core Regression Test Suite
- `uv run pytest tests/ -q`: **173 passed, 0 failed**.

---

## 6. Forensic Audit Checklist

- [x] **Domain Purity**: Zero `android.*` or `androidx.*` imports in `org.harmonia.player.domain`.
- [x] **Data Isolation**: Zero DAO or database queries executed inside `@Composable` functions.
- [x] **Playback Boundary**: Zero `ExoPlayer` or `HarmoniaPlayerController` access from presentation UI; all actions route via `PlaybackViewModel` / `PlaybackPort`.
- [x] **Zero Network Footprint**: No `android.permission.INTERNET` declared in manifest; no HTTP client libraries linked.
- [x] **No Arbitrary File Paths in UI**: Composables receive only `artworkHash: String?`, resolved safely via `ArtworkProvider`.
- [x] **No Speculative Complexities**: Standard Room SQLite query engine utilized without unneeded FTS tables.
- [x] **Clean Index Progression**: No manual `currentIndex + 1` or `currentIndex - 1` manipulation.
- [x] **Zero Production Placeholders**: No `TODO` or `FIXME` comments in mobile production Kotlin code.

---

## 7. Limitations & Device Context

- **Test Environment**: Connected testing was conducted on an Android Virtual Device (AVD: `medium_phone` running API 36 / Android 16 x86_64) with native audio output enabled. No physical handset testing was performed or claimed.
- **Scope Boundary**: Now Playing modal sheet redesign, queue drag-and-drop reordering, and playlist track mutation UI are deferred to subsequent phases per the M3 master roadmap.
