# ADR-032: Android Auto MediaLibraryService Architecture & Hierarchical Browsing

## 1. Metadata & Status
- **Identifier:** `ADR-032`
- **Title:** Android Auto MediaLibraryService Architecture & Hierarchical Browsing
- **Status:** **ACCEPTED** `[REPORT-BACKED]` / **VALIDATION PENDING** for vehicle head units `[VALIDATION-PENDING]`
- **Date:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Subsystem:** Harmonia Mobile (`harmonia-mobile/app/`)
- **Preceding Decisions:** [`ADR-020`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs), [`ADR-022`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs), [`ADR-026`](../../MOBILE_M4_ARCHITECTURE.md#27-architectural-decision-records-adrs)
- **Phase Traceability:** [`mobile_m5a_automotive.md`](../phases/mobile_m5a_automotive.md), [`mobile_m5b_media_library.md`](../phases/mobile_m5b_media_library.md)

---

## 2. Context & Problem Formulation
Milestone M4 established sovereign offline storage and decoupled catalog ingestion. However, when connecting to Android Auto dashboard head units, Android Automotive OS, or Google Assistant, client systems require an external browsing bridge following the Android Media Library specification.

Key technical challenges included:
1. **Responsiveness Window:** Android Auto monitors client responsiveness over IPC and disconnects if queries stall beyond approximately 3 seconds.
2. **IPC Transaction Limits:** Returning unbound collections over Android IPC binders risks throwing fatal `TransactionTooLargeException`.
3. **Empty / Unindexed Libraries:** If a device has unindexed folders or no storage permissions granted, vehicle media browsers must not crash or display an error screen.
4. **Content Identity Mapping:** External clients query using string identifiers; these must deterministically resolve to local content-addressed URIs without exposing physical filesystem paths.

---

## 3. Decision
1. **Service Specialization:** Refactor [`HarmoniaPlaybackService.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/HarmoniaPlaybackService.kt) to inherit from `androidx.media3.session.MediaLibraryService` (Media3 1.9.2 verified) rather than basic `MediaSessionService`.
2. **Dedicated Hierarchy Resolver:** Introduce [`MediaItemTreeResolver.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/tree/MediaItemTreeResolver.kt) to encapsulate tree node assembly, search routing, and media resolution outside the service lifecycle.
3. **Deterministic Node Namespacing:** Enforce rigid node ID prefixes:
   - Root: `"root"`
   - Categories: `"root_albums"`, `"root_artists"`, `"root_playlists"`, `"root_tracks"`
   - Containers: `"album:{id}"`, `"artist:{id}"`, `"playlist:{id}"`
   - Playable items: `"track:{id}"`
4. **Bounded Pagination:** Enforce a default page size of 50 items (`pageSize = 50`) with offset calculations (`page * pageSize`) to prevent binder buffer exhaustion.
5. **Asynchronous Dispatcher Isolation:** Execute all repository SQLite queries strictly on `Dispatchers.IO`, converting Kotlin coroutine results to Guava `ListenableFuture` via `future { ... }`.
6. **Graceful Empty Degradation:** Empty libraries or absent search results return `LibraryResult.ofItemList(emptyList(), params)` with `RESULT_SUCCESS` instead of error codes.

---

## 4. Alternatives Considered
- **Alternative A: Synchronous MediaBrowserService (Legacy compat-v4):**  
  *Rejected:* Uses deprecated Android Support APIs, lacks seamless Media3 ExoPlayer integration, and increases binder threading complexity.
- **Alternative B: In-Memory Pre-materialized Hierarchy Cache:**  
  *Rejected:* Caching the entire media tree in memory risks high garbage collection churn and becomes stale during background SAF scans. Reactive Room queries with offset paging deliver sub-50ms query times without memory bloat.

---

## 5. Consequences & Trade-offs
### Positive Consequences:
- Enables hands-free Google Assistant voice queries and car head-unit browsing.
- Protects the Android main thread from disk I/O latency.
- Maintains strict offline-first boundaries: zero remote network calls occur during media browsing.

### Negative Consequences:
- Media3 1.9.2 generic typing on `LibraryResult<ImmutableList<MediaItem>>` requires strict Guava immutable list conversions.
- Voice search queries depend on accurate text normalization (NFC) in SQLite FTS5.

---

## 6. Durable Architectural Invariants
1. `onGetChildren` must **never** throw unhandled exceptions or return error codes for empty catalogs; it must always return an empty list with `RESULT_SUCCESS`.
2. Unmounted storage tracks must either be filtered out or marked `FLAG_PLAYABLE = false`.
3. The service must declare both `androidx.media3.session.MediaLibraryService` and `android.media.browse.MediaBrowserService` intent-filters in [`AndroidManifest.xml`](../../../harmonia-mobile/app/src/main/AndroidManifest.xml) for backwards compatibility.

---

## 7. Evidence & Traceability
- **Implementation Source:** [`HarmoniaPlaybackService.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/HarmoniaPlaybackService.kt), [`MediaItemTreeResolver.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/tree/MediaItemTreeResolver.kt) `[VERIFIED-SRC]`
- **Test Evidence:** [`MediaItemTreeResolverTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/service/tree/MediaItemTreeResolverTest.kt) (24 `@Test` methods passed) `[REPORT-BACKED]`
- **Hardware Limitation:** Vehicle head-unit validation is `[VALIDATION-PENDING]`.
