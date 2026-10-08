# Harmonia Mobile — Phase M3-D Implementation Plan & Architecture
**Local-First User Playlists**

## 1. Forensic Inspection of Existing M3-A Baseline

### 1. Existing Playlist Schema
- **Tables**:
  - `mobile_playlists`:
    - Columns: `id TEXT PRIMARY KEY`, `name TEXT NOT NULL`, `description TEXT`, `artwork_hash TEXT`, `created_at INTEGER NOT NULL`, `updated_at INTEGER NOT NULL`.
  - `mobile_playlist_items`:
    - Columns: `id TEXT PRIMARY KEY`, `playlist_id TEXT NOT NULL`, `track_id TEXT NOT NULL`, `position INTEGER NOT NULL`, `added_at INTEGER NOT NULL`.
    - Foreign Keys:
      - `playlist_id` references `mobile_playlists(id)` with `ON DELETE CASCADE`.
      - `track_id` references `mobile_tracks(id)` with `ON DELETE CASCADE`.
    - Indices:
      - `index_mobile_playlist_items_playlist_id` on `playlist_id`
      - `index_mobile_playlist_items_track_id` on `track_id`
      - `index_mobile_playlist_items_playlist_id_position` on `(playlist_id, position)` with `UNIQUE = true`.
- **Database Safety Invariant**:
  - Deleting a playlist removes its items via SQLite cascading foreign key.
  - Deleting a playlist **never** deletes records from `mobile_tracks` or physical audio files.
  - The schema was established in M3-A (Room schema version 2). No new migration is needed for M3-D.

### 2. Existing Playlist DAO (`PlaylistDao`)
- `observeAllPlaylists(): Flow<List<PlaylistEntity>>` (ordered by `updated_at DESC`).
- `observePlaylistsWithCount(): Flow<List<PlaylistWithCount>>` (reactive left join emitting live track counts).
- `getPlaylistById(id: String): PlaylistEntity?`.
- `searchPlaylists(query: String): Flow<List<PlaylistEntity>>`.
- `insertPlaylist(playlist: PlaylistEntity)`.
- `updatePlaylist(playlist: PlaylistEntity)`.
- `deletePlaylist(id: String)`.
- `deleteAllPlaylists()`.
- `observePlaylistItems(playlistId: String): Flow<List<PlaylistItemEntity>>` (ordered by `position ASC`).
- `getItemsForPlaylist(playlistId: String): List<PlaylistItemEntity>`.
- `insertItem(item: PlaylistItemEntity)`.
- `insertItems(items: List<PlaylistItemEntity>)`.
- `removeItem(playlistId: String, trackId: String)`.
- `deleteItemById(id: String)`.
- `clearPlaylist(playlistId: String)`.
- `getItemCount(playlistId: String): Int`.
- `getMaxPosition(playlistId: String): Int?`.
- `replacePlaylistItems(playlistId: String, items: List<PlaylistItemEntity>)` (atomic transaction for ordering normalization).

### 3. Existing Playlist Domain Model
- `Playlist`:
  - `id: String`, `name: String`, `description: String?`, `artworkHash: String?`, `createdAt: Long`, `updatedAt: Long`, `trackCount: Int = 0`.
- `PlaylistItem`:
  - `id: String`, `playlistId: String`, `trackId: String`, `position: Int`, `addedAt: Long`, `track: Track?`.

### 4. Existing Playlist Repository (`PlaylistRepository` & `OfflinePlaylistRepository`)
- `observeAllPlaylists(): Flow<List<Playlist>>`.
- `getPlaylistById(id: String): Playlist?`.
- `createPlaylist(name: String, description: String?): Playlist`.
- `renamePlaylist(id: String, newName: String)`.
- `updatePlaylist(playlist: Playlist)`.
- `deletePlaylist(id: String)`.
- `observePlaylistItems(playlistId: String): Flow<List<PlaylistItem>>` (joins `trackDao.getTrackById`).
- `addTrackToPlaylist(playlistId: String, trackId: String): PlaylistItem`.
- `addTracksToPlaylist(playlistId: String, trackIds: List<String>): List<PlaylistItem>`.
- `removeTrackFromPlaylist(playlistId: String, trackId: String)`.
- `reorderPlaylistItem(playlistId: String, fromPosition: Int, toPosition: Int)`.

### 5. Existing Navigation
- `HarmoniaNavDestination.Playlists`: Root tab in 5-destination bottom bar.
- `HarmoniaNavDestination.PlaylistDetail(playlistId: String)`: Dedicated screen destination.
- Selecting a playlist replaces the list view with `PlaylistDetailScreen`, preserving bottom bar and mini-player.
- Back navigation returns to the playlist list.

### 6. Playback Integration
- In `PlaylistDetailScreen`:
  - "Play": Resolves ordered tracks and invokes `playbackViewModel.playTrackList(tracks, 0)`.
  - "Shuffle": Resolves tracks, applies Kotlin shuffle, and invokes `playbackViewModel.playTrackList(tracks.shuffled(), 0)`.
- `playbackViewModel.playTrackList` sets the authoritative queue on `PlaybackPort` and triggers playback.
- The playlist system never creates a second queue or audio player; ExoPlayer is managed exclusively by `HarmoniaPlaybackService` via Media3.

### 7. What M3-D Adds
1. **Full Playlist Management UI**:
   - `PlaylistsScreen`: Dynamic cards showing name, track count, description, artwork, and overflow actions (Rename, Delete).
   - Create Playlist dialog: Input validation (trimming, rejecting blank names).
   - Rename Playlist dialog: Validated rename updating Room state immediately.
   - Delete Playlist confirmation dialog: Confirms removal and preserves all tracks/files.
2. **Dedicated Playlist Detail Screen (`PlaylistDetailScreen`)**:
   - Hero header with derived cover artwork, title, description, track count, total duration in minutes.
   - Transport controls: "Play" and "Shuffle" buttons.
   - "+ Add" button opening the multi-track catalog selector dialog.
   - Track items displaying position, artwork thumbnail, title, artist, duration.
   - Deterministic reordering controls: Move Up and Move Down buttons with transactional normalization.
   - Track removal button (removes item row without touching library track).
   - Empty state with "+ Add Tracks" call-to-action.
3. **Add Tracks Selector Dialog**:
   - Searchable catalog list with live filtering.
   - Multi-select checkboxes.
   - Batch appends selected tracks to the playlist in deterministic position order.
4. **Repository & DAO Hardening**:
   - Atomic `@Transaction replacePlaylistItems` preventing transient `(playlist_id, position)` uniqueness collisions.
   - Batch insertion `addTracksToPlaylist`.
   - Reactive `observePlaylistsWithCount` left-join query preventing N+1 Room queries.

### 8. What M3-D Deliberately Does NOT Add
- AI recommendations / smart playlists / mood playlists.
- Cloud sync / remote playlists / Spotify / YouTube / Jellyfin import.
- Android Auto / iOS / KMP.
- DSP / equalizer / visualizers.
- Second playback or queue engine.

---

## 2. Architecture & Data Authority

Data authority flows unidirectionally:
```
Room (mobile_playlists, mobile_playlist_items)
  ↓
PlaylistDao
  ↓
OfflinePlaylistRepository
  ↓
PlaylistDetailViewModel / PlaylistsViewModel
  ↓
Compose UI (PlaylistsScreen, PlaylistDetailScreen)
```

Playback delegation flows to the established M2/M3-C authority:
```
PlaylistDetailScreen ("Play" / "Shuffle")
  ↓
PlaybackViewModel (playTrackList)
  ↓
PlaybackPort (setQueue, play)
  ↓
HarmoniaPlayerController / HarmoniaPlaybackService (ExoPlayer Media3)
```

No competing playback queue or engine exists.

---

## 3. Database Behavior & Constraint Safety

1. **Foreign Keys**:
   - `mobile_playlist_items.playlist_id` references `mobile_playlists(id)` with `ON DELETE CASCADE`.
   - `mobile_playlist_items.track_id` references `mobile_tracks(id)` with `ON DELETE CASCADE`.
2. **Track Preservation Invariant**:
   - Deleting a playlist deletes rows from `mobile_playlists` and cascaded rows from `mobile_playlist_items`.
   - Never deletes rows from `mobile_tracks` or `mobile_local_files`.
3. **Unique Position Constraint**:
   - `(playlist_id, position)` has a unique index.
   - To safely reorder without transient collision, `replacePlaylistItems` runs `clearPlaylist(playlistId)` followed by `insertItems(updatedList)` within a single `@Transaction`.

---

## 4. Reordering Algorithm

When an item at `fromPosition` moves to `toPosition`:
1. Current playlist items are queried ordered by `position ASC`.
2. If `fromPosition` and `toPosition` are within bounds:
   - Item is removed from `fromPosition`.
   - Item is inserted at `toPosition`.
   - All items are re-indexed `0 .. (size - 1)`.
3. `replacePlaylistItems` updates Room atomically.
4. The Room Flow observer triggers UI recomposition with updated ordering.

Tested boundary cases:
- First item to last position
- Last item to first position
- Adjacent swaps
- Middle movements
- Single-item playlist
- Empty playlist
- Repeated sequential reorders

---

## 5. Shuffle Behavior

When "Shuffle" is pressed in `PlaylistDetailScreen`:
- The playlist's tracks are resolved in their current persisted order.
- Kotlin's `shuffled()` randomizes the playback list in memory.
- `playbackViewModel.playTrackList(shuffledList, 0)` passes the sequence to `PlaybackPort`.
- Room database ordering for the playlist remains strictly untouched.

---

## 6. Missing / Unavailable Tracks

- If a physical file for a track is unavailable (`availability == UNAVAILABLE`), the track entry remains preserved in `mobile_playlist_items`.
- Track domain identity is separate from bitstream availability (ADR-025).
- Playback engine handles missing files via standard error reporting without corrupting playlist contents.

---

## 7. Artwork Representation

- Playlists derive their visual cover from the first contained track that possesses an artwork hash (`track.artworkHash`).
- Fallback: Uses `HarmoniaArtwork` with the `HarmoniaIcons.Playlists` vector icon.
- No network requests, remote URLs, or arbitrary filesystem paths are introduced.

---

## 8. Verification & Test Metrics

### Test Suite Summary
- **JVM Tests**: 37 → 68 passed (all 68 green across 13 test suites, 0 failures, 0 errors, 0 skipped)
  - `OfflineListeningEventRepositoryTest`: 2 tests
  - `OfflinePlaybackStateRepositoryTest`: 2 tests
  - `OfflinePlaylistRepositoryTest`: 11 tests
  - `OfflineSearchRepositoryTest`: 9 tests
  - `OfflineTrackRepositoryTest`: 3 tests
  - `PlaylistDomainTest`: 2 tests
  - `TrackDomainTest`: 4 tests
  - `PlaybackQueueLogicTest`: 6 tests
  - `LibrarySortingTest`: 8 tests
  - `NavigationDestinationTest`: 2 tests
  - `PlaybackViewModelTest`: 9 tests
  - `PlaylistViewModelTest`: 6 tests
  - `SearchViewModelTest`: 4 tests
  - Total: 2 + 2 + 11 + 9 + 3 + 2 + 4 + 6 + 8 + 2 + 9 + 6 + 4 = 68
- **Connected Android Tests**: 33 → 39 passed (all 39 green across 6 test suites on `medium_phone(AVD)` with audio enabled, 0 failures, 0 errors, 0 skipped)
  - `PlaylistsFlowUiTest`: 4 tests (`playlists_screen_renders_and_creates_new_playlist`, `playlist_rename_flow_updates_title`, `playlist_detail_add_tracks_reorder_and_play`, `playlist_delete_with_confirmation_preserves_underlying_tracks`)
  - `RoomMigrationAndPlaylistDaoTest`: 12 tests
  - `HarmoniaNavigationAndLibraryUiTest`: 8 tests
  - `RealAndroidPlaybackTest`: 7 tests
  - `NowPlayingAndMiniPlayerUiTest`: 4 tests
  - `ArtworkProviderTest`: 4 tests
  - Total: 4 + 12 + 8 + 7 + 4 + 4 = 39
- **Core Tests**: 173 / 173 passed (`uv run pytest tests/ -q`).

---

## 9. Known Limitations
- Drag-and-drop reordering is not natively built into Compose Foundation without external third-party libraries; deterministic Move Up / Move Down controls are used to ensure zero external dependencies and guaranteed stability.
- Smart playlists, mood generation, cloud sync, and AI curation are strictly reserved for post-M3 milestones.
