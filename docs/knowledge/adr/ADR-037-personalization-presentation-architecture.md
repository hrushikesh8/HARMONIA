# ADR-037: Decoupled Presentation Architecture for Track Personalization — Follow-Mode vs Explicit Binding

## 1. Metadata & Status
- **Identifier:** `ADR-037`
- **Title:** Decoupled Presentation Architecture for Track Personalization — Follow-Mode vs Explicit Binding
- **Status:** **ACCEPTED** `[REPORT-BACKED]`
- **Date:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Subsystem:** Harmonia Mobile (`harmonia-mobile/app/`)
- **Preceding Decisions:** [`ADR-020`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs), [`ADR-036`](ADR-036-personalization-persistence-schema-v5.md)
- **Phase Traceability:** [`mobile_personalization_p3_viewmodel.md`](../phases/mobile_personalization_p3_viewmodel.md)

---

## 2. Context & Problem Formulation
In personal music playback, users interact with track favorites and star ratings in two distinct operational contexts:
1. **Now Playing / Mini-Player Context:** The displayed personalization state must dynamically follow whichever track is currently playing through the playback service. When playback advances to the next track, the favorite button and star rating must update automatically.
2. **Catalog / Detail Sheet Context:** When browsing the library, viewing an album tracklist, or opening a track context menu, the user may inspect and rate a track that is *not* currently playing. In this mode, subsequent playback state changes (e.g. current song finishing in the background) must **not** override or reset the track the user is actively inspecting.

Additionally, embedding personalization state directly inside `PlaybackViewModel` risked architectural bloat, mixing core playback controls (play/pause/seek) with user curation logic.

---

## 3. Decision
1. **Dedicated Personalization ViewModel:**
   - Create an independent [`PersonalizationViewModel.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/personalization/PersonalizationViewModel.kt) rather than expanding `PlaybackViewModel`.
2. **Dual-Mode Observation Strategy:**
   - **Playback Follow Mode (Default):** Subscribes reactively to `PlaybackPort.playbackState`. When `currentTrack?.id` changes, the ViewModel switches observation via `flatMapLatest` to the active track's personalization Flow.
   - **Manual Binding Mode:** Calling `bindTrack(trackId)` explicitly pins the ViewModel to the requested track ID, suppressing playback port follow emissions. Calling `resumeFollowingPlayback()` resets manual binding back to playback follow mode.
3. **Mutation Serialization via Mutex:**
   - User mutation actions (`setFavorite`, `toggleFavorite`, `setRating`, `clearRating`) are guarded by a coroutine `Mutex.withLock` to serialize rapid consecutive UI button clicks.
4. **Resilient Error Surfacing:**
   - Errors from repository mutations are caught and emitted via `userErrorMessage` in [`TrackPersonalizationUiState`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/personalization/PersonalizationViewModel.kt) without crashing the ViewModel or clearing existing state.

---

## 4. Alternatives Considered
- **Alternative A: Embedding Personalization in `PlaybackViewModel`:**  
-   *Rejected:* Bloats `PlaybackViewModel` and makes it difficult to reuse personalization state inside independent dialogs, bottom sheets, or library track rows that are decoupled from Now Playing.
- **Alternative B: Separate ViewModels for Now Playing vs Catalog Sheets:**  
-   *Rejected:* Duplicates repository subscription, error handling, and mutation logic. The dual-mode approach (`bindTrack` override and `resumeFollowingPlayback`) provides a unified component.

---

## 5. Consequences & Trade-offs
### Positive Consequences:
- High cohesion and separation of concerns: media playback controls remain isolated from personal curation.
- Flexible reuse across Compose Now Playing sheet, Library Favorites tab, and track context dialogs.
- Concurrency protection: button mashing in the UI cannot cause out-of-order database mutations.

### Negative Consequences:
- Requires Compose surfaces to collect from both `PlaybackViewModel` (for playback state) and `PersonalizationViewModel` (for favorites/ratings).

---

## 6. Durable Architectural Invariants
1. Calling `bindTrack(trackId)` must pause automatic playback follow mode until explicitly reset via `resumeFollowingPlayback()`.
2. User actions must be serialized via `Mutex` to prevent concurrent write races.
3. Personalization state must be derived reactively from Room as the single source of truth.

---

## 7. Evidence & Traceability
- **Implementation Source:** [`PersonalizationViewModel.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/personalization/PersonalizationViewModel.kt) `[VERIFIED-SRC]`
- **Test Evidence:** [`PersonalizationViewModelTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/presentation/personalization/PersonalizationViewModelTest.kt) (16 `@Test` methods passed) `[REPORT-BACKED]`
- **Related Incidents:** [`INC-006`](../incidents/INC-006-personalization-manual-bind-playback-reversion.md)
