# ADR-036: Room-Backed Track Personalization Schema v5 — Row-Presence Semantics & Deletion Tombstone Segregation

## 1. Metadata & Status
- **Identifier:** `ADR-036`
- **Title:** Room-Backed Track Personalization Schema v5 — Row-Presence Semantics & Deletion Tombstone Segregation
- **Status:** **ACCEPTED** `[REPORT-BACKED]` / **VALIDATION PENDING** for instrumented runtime execution `[VALIDATION-PENDING]`
- **Date:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Subsystem:** Harmonia Mobile (`harmonia-mobile/app/`)
- **Preceding Decisions:** [`ADR-021`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs), [`ADR-025`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs)
- **Phase Traceability:** [`mobile_personalization_p1_persistence.md`](../phases/mobile_personalization_p1_persistence.md), [`mobile_personalization_p2_repository.md`](../phases/mobile_personalization_p2_repository.md)

---

## 2. Context & Problem Formulation
The initial mobile architecture specification proposed adding personal user music attributes (favorites and 1–5 star ratings) into the Room database. However, the initial draft had several schema ambiguities:
1. **Redundant Boolean Column:** The initial proposal suggested a `mobile_favorites` table with both a `track_id` primary key and an `is_favorite` boolean column. If a row's presence meant the track was favorited, storing `is_favorite = true` was redundant, and storing rows with `is_favorite = false` would permanently bloat the database with negative markers.
2. **Sync Deletion Semantics (Local Deletion vs P2P Tombstones):** In peer-to-peer synchronization (Milestone M6 LocalSync), deleting a local record can cause a remote peer to re-insert it unless a deletion tombstone is recorded. However, overloading local operational tables with tombstone state creates complexity and complicates standard foreign key cascade deletes.
3. **Rating Boundaries:** User ratings must strictly adhere to a 1–5 star integer range (null when unrated). The database must enforce this invariant at the SQLite layer, not just in Kotlin code.

---

## 3. Decision
1. **Row-Presence Semantics for Favorites:**
   - In `mobile_favorites`, row presence indicates that a track is favorited. Un-favoriting a track is executed via SQL `DELETE FROM mobile_favorites WHERE track_id = :trackId`.
   - The redundant `is_favorite` boolean column was completely removed.
2. **SQLite Check Constraint for Ratings:**
   - In `mobile_ratings`, enforce `CHECK(rating >= 1 AND rating <= 5)` at the database DDL level, ensuring invalid integer ratings are rejected by the SQLite engine itself.
3. **Foreign Key Cascade Deletion:**
   - Both tables declare `FOREIGN KEY(track_id) REFERENCES tracks(id) ON DELETE CASCADE`. If a track is permanently purged from the library, its personalization records are cleaned up automatically.
4. **Segregation of Sync Tombstones (Deferred to M6):**
   - Documented that `client_updated_at` timestamps in `mobile_favorites` and `mobile_ratings` represent local mutation recency, **not** deletion tombstones.
   - Milestone M6 LocalSync will introduce a dedicated, isolated `mobile_sync_tombstones` mutation journal table to track remote deletions, leaving the local tables clean and normalized.
5. **Idempotent DAO Contract:**
   - Expose `setFavorite(trackId, isFavorite)` as the primary mutation contract in [`PersonalizationDao.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/dao/PersonalizationDao.kt). If toggle is needed, wrap it in a `@Transaction` block to ensure atomic read-and-mutate execution.

---

## 4. Alternatives Considered
- **Alternative A: Embedding Favorites and Ratings Directly in `tracks` Table:**  
  *Rejected:* Violates clean separation of concerns. `tracks` represents catalog metadata ingested from file tags and Desktop Core; personalization represents sovereign user curation. Storing them in separate tables allows independent migration and local mutation tracking.
- **Alternative B: Soft-Deletion Flag (`is_deleted = true`) on Personalization Rows:**  
  *Rejected:* Causes table bloat over time and complicates reactive Room queries (`WHERE is_deleted = 0` required on every query). Dedicating tombstones to M6 is cleaner.

---

## 5. Consequences & Trade-offs
### Positive Consequences:
- Zero table bloat for unfavorited tracks.
- Database-level constraint prevents corrupt rating data.
- Foreign key cascading guarantees referential integrity.
- Migration preservation: `MIGRATION_4_5` preserves 100% of existing user tracks and storage sources.

### Negative Consequences:
- Querying a track's complete personalization requires joining or combining Flow emissions from both `mobile_favorites` and `mobile_ratings`.

---

## 6. Durable Architectural Invariants
1. A row present in `mobile_favorites` means the track is active favorited; unfavoriting deletes the row.
2. Ratings in `mobile_ratings` must satisfy $1 \le \text{rating} \le 5$.
3. Deleting a track cascades deletion to its favorite and rating records.

---

## 7. Evidence & Traceability
- **Implementation Source:** [`FavoriteEntity.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/entity/FavoriteEntity.kt), [`RatingEntity.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/entity/RatingEntity.kt), [`PersonalizationDao.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/dao/PersonalizationDao.kt), [`HarmoniaDatabase.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/HarmoniaDatabase.kt), [`5.json`](../../../harmonia-mobile/app/schemas/org.harmonia.player.data.local.HarmoniaDatabase/5.json) `[VERIFIED-SRC]`
- **Architecture Documentation:** [`docs/MOBILE_ARCHITECTURE_v0.1.md:§11.2`](../../MOBILE_ARCHITECTURE_v0.1.md) `[VERIFIED-SRC]`
- **Test Evidence:** [`RoomMigrationV4ToV5AndPersonalizationDaoTest.kt`](../../../harmonia-mobile/app/src/androidTest/java/org/harmonia/player/RoomMigrationV4ToV5AndPersonalizationDaoTest.kt) (compiled in AndroidTest APK; runtime pending hardware `[VALIDATION-PENDING]`)
