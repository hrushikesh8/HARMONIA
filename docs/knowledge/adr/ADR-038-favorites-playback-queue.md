# ADR-038: Library Favorites Playback Queue Contract — Point-in-Time Snapshot Semantics vs Dynamic Synchronization

## 1. Metadata & Status
- **Identifier:** `ADR-038`
- **Title:** Library Favorites Playback Queue Contract — Point-in-Time Snapshot Semantics vs Dynamic Synchronization
- **Status:** **ACCEPTED** `[VERIFIED-SRC]` `[REPORT-BACKED]`
- **Date:** 2026-10-16
- **Subsystem:** Harmonia Mobile (`harmonia-mobile/app/`)
- **Preceding Decisions:** [`ADR-022`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs), [`ADR-025`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs), [`ADR-036`](ADR-036-personalization-persistence-schema-v5.md), [`ADR-037`](ADR-037-personalization-presentation-architecture.md)
- **Phase Traceability:** Personalization Phase 4, Stage 5D.3 (Home & Library Screens), Milestone Consolidation C2
- **Related Documentation:** [`docs/ROADMAP.md`](../../ROADMAP.md:§Consolidation-C2), [`docs/knowledge/JOURNAL.md`](../JOURNAL.md)

---

## 2. Context & Problem Formulation
In Harmonia Mobile, users interact with track favorites across multiple surfaces:
1. **Catalog Curation:** In [`LibraryScreen.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/library/LibraryScreen.kt), the dedicated "Favorites" tab queries the Room SQLite database reactively via `PersonalizationViewModel.favoriteTracks` (`mobile_favorites` table). Tracks are presented according to user-selected sorting criteria (`FavoriteSortOrder`: Recently Favorited, Title A–Z, Artist A–Z).
2. **Session Playback:** When a user clicks a track in the Favorites tab, that track and its sibling favorited tracks are dispatched to the playback engine via [`playbackViewModel.playTrackList(tracks, clickedIndex)`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/PlaybackViewModel.kt#L74-L77), establishing the active in-memory queue and loading AndroidX Media3 `MediaItem`s.
3. **Cross-Surface Personalization Mutation:** While playback is active, the user may toggle the favorite status of any track—such as tapping the heart icon in [`NowPlayingSheet.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/NowPlayingSheet.kt), [`MiniPlayer.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/MiniPlayer.kt), or [`LibraryScreen.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/library/LibraryScreen.kt).

### The Invariant Ambiguity
This architectural interaction surfaced a fundamental question:
> **When a playback queue is instantiated from the Library Favorites collection, what is the authoritative lifecycle relationship between the Room-backed `FavoriteEntity` database collection and the active in-memory Media3 playback queue?**
>
> If a user un-favorites a track currently present in (or currently playing from) an active favorites playback queue, should that track be dynamically evicted from the active queue, or does the queue remain an immutable point-in-time snapshot?

During the Milestone Consolidation C1 audit, it was confirmed that the current implementation preserves the active queue without dynamic eviction, but this behavior was an implicit consequence of architectural decoupling rather than a formally documented contract. This ADR establishes that formal contract.

---

## 3. Options Evaluated

### Option A: Point-in-Time Snapshot Queue Semantics (Recommended & Adopted)
Under this model, clicking a track in the Library Favorites tab captures a point-in-time snapshot of `sortedFavoriteTracks` and passes it to `PlaybackPort.setQueue(tracks, startIndex)`. The active transport queue becomes an independent listening session.
- **Subsequent Unfavoriting:** Un-favoriting a track (even the currently playing track) deletes the corresponding row from `mobile_favorites` in Room, reactively updating the heart button in the UI, but does **not** alter the active queue or interrupt the audio stream.
- **Subsequent Favoriting:** Favoriting a new track elsewhere in the library updates the database and Library Favorites tab, but does **not** inject the new track into the in-flight playback queue.
- **Queue Refresh:** A new queue snapshot is loaded only when the user explicitly triggers playback again from the Favorites tab, an album, a playlist, or a search result.

### Option B: Dynamically Synchronized Queue (Rejected)
Under this model, the playback engine or `PlaybackViewModel` would actively observe `PersonalizationRepository.observeFavoriteTracks()` whenever the active queue originated from the Favorites tab, continuously reconciling the Media3 playlist against database mutations.
- **Why Rejected:**
  1. **Disruptive User Experience:** If a user un-favorites the currently playing song (e.g. deciding during playback that they no longer consider it a favorite), dynamic eviction would force ExoPlayer to immediately abort the track mid-song and skip forward. This creates an uncommanded, jarring interruption that violates core audio player ergonomics.
  2. **Media3 Timeline Instability:** Dynamic addition and deletion of `MediaItem`s shifts item indices. In shuffle mode, ExoPlayer's `ShuffleOrder` must be rebuilt on every database write, risking playback glitches, repeated tracks, or playback stalls.
  3. **Concurrency & Rapid Curation Races:** A user rapidly toggling favorite states in a list would trigger rapid asynchronous IPC calls to `MediaLibraryService` (`removeMediaItem`, `addMediaItem`), risking race conditions, index out-of-bounds exceptions, and unnecessary CPU/battery drain.
  4. **Domain Coupling Violation:** Coupling `HarmoniaPlayerController` or `HarmoniaPlaybackService` to `PersonalizationRepository` violates Clean Architecture. The media transport engine should manage audio playback, not observe user curation tables.

### Option C: Hybrid Re-Evaluation on Boundary Transitions (Rejected)
Under this model, the currently playing song would finish uninterrupted, but next-track transitions or repeat loops would re-evaluate favorite status and skip tracks that were un-favorited during the session, or display a prompt: *"Remove un-favorited track from queue?"*.
- **Why Rejected:**
  1. Non-deterministic skip behavior: The queue drawer in `NowPlayingSheet` would display tracks that the player unexpectedly skips upon reaching them.
  2. Dialog prompts during music listening break immersion and distraction-free audio player principles.
  3. Adds complex conditional branching to the core playback state machine.

---

## 4. Decision

Harmonia Mobile formally adopts **Option A: Point-in-Time Snapshot Queue Semantics**.

### Architectural Specification:
1. **Snapshot Queue Instantiation:**
   When a user clicks any track within the Library Favorites tab, the UI resolves the active list of favorited tracks (`sortedFavoriteTracks`, respecting the user's selected `FavoriteSortOrder`) and dispatches it via `playbackViewModel.playTrackList(tracks, clickedIndex)`.
2. **Complete Decoupling of Transport Queue from Database Mutation:**
   Once loaded into `HarmoniaPlayerController` via `PlaybackPort.setQueue()`, the queue of `QueueItem`s and AndroidX Media3 `MediaItem`s is completely decoupled from the Room `mobile_favorites` table.
3. **Non-Disruptive Unfavoriting:**
   Toggling a track from favorited to unfavorited state deletes the record in Room and immediately updates visual indicators across the application via reactive Flow emissions (e.g. heart icon turns un-filled in `NowPlayingSheet`, `MiniPlayer`, and `TrackRowCard`), but:
   - Does **not** pause, abort, or skip the currently playing track.
   - Does **not** remove the track from the active in-memory queue.
   - Does **not** remove the track from the upcoming queue drawer in `NowPlayingSheet`.
   The track remains fully playable for the duration of that playback session until the user replaces or clears the queue.
4. **Snapshot Re-evaluation Boundary:**
   The active queue is only updated when the user initiates a new queue-loading action (e.g. tapping another track or shuffle button in the Favorites tab, or playing from another screen).
5. **Permissible Future Enhancements:**
   Explicit queue editing actions (e.g. an explicit "Remove from Queue" button in the queue drawer, swipe-to-dismiss in the queue list, or drag-and-drop queue reordering) are explicitly permitted and do **not** violate this ADR. Such operations represent direct user transport queue modifications rather than implicit database-synchronization side effects.

---

## 5. Ecosystem & Architectural Consistency

This decision ensures complete consistency with existing queue semantics across the entire Harmonia ecosystem:

| Surface / Source | Queue Loading Behavior | Underlying Data Mutation Behavior | Architectural Contract |
| :--- | :--- | :--- | :--- |
| **Library Tracks** | Point-in-time snapshot of track list | Tag changes / scan updates do not mutate active queue | Snapshot (`setQueue`) |
| **Library Albums** | Point-in-time snapshot of album tracks | Album metadata updates do not mutate active queue | Snapshot (`setQueue`) |
| **Library Artists** | Point-in-time snapshot of artist tracks | Artist metadata updates do not mutate active queue | Snapshot (`setQueue`) |
| **Playlists** | Point-in-time snapshot of playlist tracks | Adding/removing tracks from playlist does not mutate active queue | Snapshot (`setQueue`) |
| **Library Favorites** | **Point-in-time snapshot of favorites** | **Favoriting/unfavoriting does not mutate active queue** | **Snapshot (`setQueue`)** |

Treating Favorites as a snapshot queue aligns it with every other music collection in the application, maintaining a uniform mental model for both the user and the codebase.

---

## 6. Durable Architectural Invariants

1. **Subsystem Isolation:** `HarmoniaPlayerController` and `HarmoniaPlaybackService` must **never** observe `PersonalizationRepository`, `FavoriteDao`, or `FavoriteEntity`. Playback components receive tracks exclusively through `PlaybackPort` contracts.
2. **Transport Stability:** Active playback of an audio track must **never** be interrupted, aborted, or skipped as a side-effect of a metadata or personalization mutation.
3. **Queue Independence:** An in-flight playback queue remains immutable with respect to external database state until explicitly replaced by a user-initiated queue load or explicit queue manipulation.
4. **Immediate Presentation Reactivity:** While transport playback is isolated, all UI presentation layers (`NowPlayingSheet`, `MiniPlayer`, `LibraryScreen`) must remain 100% reactive to Room database state via `PersonalizationViewModel`, updating icon states without requiring an application restart.

---

## 7. Evidence & Implementation Boundary

### A. Verified Behavior in Current Source Code `[VERIFIED-SRC]`
Inspection of the current implementation confirms that the codebase **already conforms to Option A**:
- [`LibraryScreen.kt#L426-L433`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/library/LibraryScreen.kt): `FavoritesTabContent` binds track clicks to `onTrackClick(tracks, index)` with `sortedFavoriteTracks`.
- [`MainActivity.kt#L140-L145`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/MainActivity.kt): Dispatches clicked track list directly to `playbackViewModel.playTrackList(tracks, clickedIndex)`.
- [`PlaybackViewModel.kt#L74-L77`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/playback/PlaybackViewModel.kt): Calls `playbackPort.setQueue(tracks, startIndex)` and `playbackPort.play()`.
- [`HarmoniaPlayerController.kt#L538-L563`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt): Translates `tracks` into `QueueItem` list, updates `_queue`, and invokes `mediaController.setMediaItems(mediaItems, startIndex, 0L)`. Zero references to `PersonalizationRepository` or favorites exist in the playback engine.
- [`PersonalizationViewModel.kt#L180-L194`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/personalization/PersonalizationViewModel.kt): `toggleFavorite` mutates Room independently under `Mutex.withLock`.

### B. Automated Test Coverage `[REPORT-BACKED]`
- [`LibraryFavoritesUiTest.kt`](../../../harmonia-mobile/app/src/androidTest/java/org/harmonia/player/LibraryFavoritesUiTest.kt):
  - `favorites_tab_displays_tracks_with_counter_and_sorting`: Verifies that clicking a track in `FavoritesTabContent` invokes `onTrackClick` with the snapshot list.
  - `track_row_card_displays_favorite_button_with_independent_click`: Confirms favorite button toggles without triggering row click (decoupling curation from playback).
  - `reactive_favorites_flow_updates_tab_counter_and_removes_item`: Verifies that unfavoriting immediately removes the track from the Library tab view and triggers empty state when count hits zero.
- [`PlaybackViewModelTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/presentation/PlaybackViewModelTest.kt): Confirms `playTrackList` sets the exact track snapshot to `PlaybackPort`.

### C. Implementation Status & Future Work
- **Current Status:** Conforming. Current production code already operates according to Option A. No application code changes, database migrations, or test adjustments are required to satisfy this decision.
- **Future Enhancements (Post-M6):** If explicit queue reordering or manual queue item removal is introduced, it will operate strictly within `PlaybackPort` / `HarmoniaPlayerController` as a transport control, maintaining full compliance with this ADR.
