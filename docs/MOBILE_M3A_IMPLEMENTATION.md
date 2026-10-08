# Harmonia Mobile — M3-A Implementation Report
## Database, Navigation, and Library Foundation

**Date**: October 8, 2026  
**Phase**: Mobile M3-A (Database + Navigation + Library Foundation)  
**Parent Architecture**: `docs/MOBILE_M3_ARCHITECTURE.md` (Approved at checkpoint `44f4d7e`)  
**Verified Baseline**: `restore-point/mobile-m2-complete` (`3578e58`)  
**Tag**: `restore-point/mobile-m3a-complete`

---

## 1. Executive Summary

Phase M3-A establishes the complete persistent data foundation, navigation topology, and catalog browsing architecture for Harmonia Mobile. 
All capabilities adhere strictly to the offline-first requirement (zero network access, zero `android.permission.INTERNET`).
M2 native Media3 playback authority (`HarmoniaPlaybackService`, `PlaybackPort`, queue progression, playback persistence, and listening event telemetry) is 100% preserved and untouched.

---

## 2. Room Schema Migration 1 → 2

The Room SQLite persistent store has been upgraded from version 1 to version 2 via `HarmoniaDatabase.MIGRATION_1_2`.

### Entities Introduced
1. **`mobile_playlists` (`PlaylistEntity`)**:
   - `id: TEXT NOT NULL PRIMARY KEY`
   - `name: TEXT NOT NULL`
   - `description: TEXT NULL`
   - `artwork_hash: TEXT NULL`
   - `created_at: INTEGER NOT NULL`
   - `updated_at: INTEGER NOT NULL`

2. **`mobile_playlist_items` (`PlaylistItemEntity`)**:
   - `id: TEXT NOT NULL PRIMARY KEY`
   - `playlist_id: TEXT NOT NULL`
   - `track_id: TEXT NOT NULL`
   - `position: INTEGER NOT NULL`
   - `added_at: INTEGER NOT NULL`
   - **Foreign Keys**:
     - `playlist_id` references `mobile_playlists(id)` `ON DELETE CASCADE`
     - `track_id` references `mobile_tracks(id)` `ON DELETE CASCADE`
   - **Indices**:
     - `index_mobile_playlist_items_playlist_id` on `(playlist_id)`
     - `index_mobile_playlist_items_track_id` on `(track_id)`
     - `index_mobile_playlist_items_playlist_id_position` (UNIQUE) on `(playlist_id, position)`

### Migration Safety & Integrity
- All existing M1 and M2 data (`mobile_tracks`, `mobile_albums`, `mobile_artists`, `mobile_local_files`, `playback_state`, `listening_events`) survives migration intact.
- Migration verification was executed against live SQLite instances on Android Virtual Device (`emulator-5554`), confirming column preservation, cascade deletions, and rejection of duplicate positions via `SQLiteConstraintException`.

---

## 3. Pure Kotlin Domain Layer

Per Harmonia architectural constraints, the domain layer contains **zero Android, AndroidX, Room, Media3, or Compose imports**:

- **Models**:
  - `Playlist`: Immutable domain representation with title alias and timestamp metadata.
  - `PlaylistItem`: Represents a track within an ordered playlist, tracking 0-based position and optional joined `Track`.
  - `SearchResults`: Composite aggregate holding matched tracks, albums, artists, and playlists.
- **Repository Contracts**:
  - `PlaylistRepository`: Observe playlists, get by ID, create, update, delete, observe items, add item, remove item, reorder items.
  - `AlbumRepository`: Observe all albums, get by ID, observe albums by artist.
  - `ArtistRepository`: Observe all artists, get by ID.
  - `SearchRepository`: Foundation search interface querying tracks, albums, artists, and playlists.

---

## 4. Repository & Data Layer

1. **`OfflinePlaylistRepository`**:
   - Coordinates Room queries via `PlaylistDao` and joins track details via `TrackDao`.
   - Enforces deterministic 0-based positions upon addition and normalizes remaining items upon removal.
   - Atomic reordering re-indexes items to strictly honor the unique index on `(playlist_id, position)`.
2. **`OfflineAlbumRepository` & `OfflineArtistRepository`**:
   - Deliver reactive `Flow<List<Album>>` and `Flow<List<Artist>>` backed by Room DAOs.
3. **`OfflineSearchRepository`**:
   - Executes parameterized case-insensitive `LIKE` queries against local tables, establishing the query foundation for M3-B search without artificial or fabricated benchmarks.
4. **`ArtworkProvider`**:
   - Encapsulates Core SHA-256 artwork identity (`artworkHash`).
   - Reads exclusively from the app-private local storage cache (`context.filesDir/artwork/`).
   - Resolves bitmaps via Android `BitmapFactory` without exposing raw filesystem paths to Composables.
   - Provides a gradient acoustic fallback when artwork bytes are unavailable.

---

## 5. Navigation Architecture

Implemented native Jetpack Compose 5-destination bottom navigation bar:
- **Home**: Quick actions (Resume, Shuffle All), Shortcuts (Artists, Albums, Playlists), factual Recently Played (sourced from `ListeningEventDao`), and empty state.
- **Library**: Tabs for Tracks, Albums, and Artists.
- **Playlists**: User playlists list, creation modal, and empty state.
- **Search**: Debounced local index search input field and aggregate query results.
- **Settings**: Preserved M1/M2 offline settings controls.

---

## 6. Library UI & Sorting

- **Tracks**: Displays artwork fallback/thumbnail, title, artist, album, duration formatting, and offline availability status.
- **Albums**: 2-column adaptive grid displaying artwork, title, artist, and year/track count.
- **Artists**: List displaying avatar/fallback, artist name, and catalog metadata.
- **Deterministic Sorting**:
  - **Tracks**: Title (A-Z), Title (Z-A), Artist (A-Z), Date Added (Newest), Duration (Longest).
  - **Albums**: Title (A-Z), Artist (A-Z), Year (Newest).
  - **Artists**: Name (A-Z), Name (Z-A).
  - Executed exclusively in `LibraryViewModel` via reactive Kotlin StateFlow combinators. No sort logic in Composables.

---

## 7. Verification Evidence

### JVM Unit Tests (`app:testDebugUnitTest`)
- `TrackDomainTest`: Verified domain availability mapping and bitstream verification.
- `PlaylistDomainTest`: Verified playlist domain model attributes and item position ordering.
- `LibrarySortingTest`: Verified deterministic sorting for tracks, albums, artists, and tab transitions.
- `OfflineTrackRepositoryTest`: Verified track upsert, offline bitstream filtering, and deletion.
- `OfflinePlaylistRepositoryTest`: Verified playlist CRUD, sequential position assignment, reordering, and removal normalization.
- `OfflineSearchRepositoryTest`: Verified blank query handling and multi-entity search results.
- `OfflineListeningEventRepositoryTest`: Verified factual telemetry event recording and pruning.
- `OfflinePlaybackStateRepositoryTest`: Verified state persistence and queue retrieval.
- `NavigationDestinationTest`: Verified navigation destination contracts.

**Result**: 100% Passed (Clean build in 22s).

### Connected Android Tests (`app:connectedDebugAndroidTest` on `medium_phone(AVD) - 16`)
- `RoomMigrationAndPlaylistDaoTest` (5 tests):
  - `migration_1_to_2_preserves_m1_m2_data_and_creates_playlist_tables` — Passed.
  - `playlist_crud_operations_persist_and_update` — Passed.
  - `playlist_items_maintain_deterministic_position_ordering` — Passed.
  - `unique_constraint_rejects_duplicate_positions` — Passed (`SQLiteConstraintException` verified).
  - `deleting_playlist_cascades_and_removes_playlist_items` — Passed (`ON DELETE CASCADE` verified).
- `HarmoniaNavigationAndLibraryUiTest` (5 tests):
  - `main_navigation_displays_five_destinations_and_switches_screens` — Passed.
  - `home_screen_renders_shortcuts_and_empty_state` — Passed.
  - `library_screen_renders_tabs_and_sorting_dialog` — Passed.
  - `playlists_screen_renders_empty_state_and_create_button` — Passed.
  - `search_screen_renders_search_field_and_local_indicator` — Passed.
- `RealAndroidPlaybackTest` (7 tests):
  - MP3 / FLAC / M4A / WAV live audio HAL playback, seek, queue progression, and missing file handling — All Passed.

**Result**: 17/17 tests passed on live Android emulator (0 skipped, 0 failed).

### Core Pytest Regression (`tests/`)
- Command: `uv run pytest tests/ -q`
- Result: **173 passed, 8 warnings in 25.69s**. Exactly 173/173 tests passing. Zero regressions.

---

## 8. Forensic Architecture Check

- **Android Imports in Domain**: 0 (`grep -rn "import android"` in `domain/` -> empty).
- **Direct DAO Usage in Presentation**: 0.
- **Direct ExoPlayer Usage in Presentation**: 0.
- **Network Libraries / Permissions**: No new network dependencies; `android.permission.INTERNET` is absent.
- **Hardcoded / Mock Library Data**: None. All data is dynamically resolved through Room DAOs.
- **Scope Creep Exclusions**:
  - Full Now Playing redesign: Deferred to M3-B.
  - Queue drag-and-drop editing: Deferred to M3-B.
  - Playlist drag-and-drop / track editing UI: Deferred to M3-B.
  - Cloud / AI / Sync / Android Auto: Out of scope.

---

## 9. Conclusion

Harmonia Mobile Phase M3-A is complete and verified. Ready for Phase M3-B.
