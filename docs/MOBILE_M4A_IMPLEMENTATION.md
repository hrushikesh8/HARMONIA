# HARMONIA MOBILE — M4-A IMPLEMENTATION DOCUMENTATION

## Storage Sources, Permissions & Room Migration (V2 → V3)

**Document Version:** 1.0.0  
**Phase:** M4-A (Storage Foundation)  
**Status:** COMPLETED  
**Commit:** `feat(mobile): implement M4A storage foundation`  
**Restore Point:** `restore-point/mobile-m4a-complete`  
**Approved Baseline Commit:** `650326a` (docs(mobile): apply review 2 corrections to M4 architecture specification)  
**Frozen M3 Baseline:** `c952e15` (`restore-point/mobile-m3-complete`)

---

## 1. Executive Summary

Milestone **M4-A** establishes the architectural, persistence, identity, and platform permission foundation for local media ingestion in Harmonia Mobile.

### Scope Implemented
- **Provider-Neutral Storage Model:** Pure domain representations for `StorageSource` (`MediaStorePrimary`, `MediaStoreVolume`, `SafDocumentTree`, `InternalSyncStorage`).
- **Deterministic Identity Engine:** Distinct separation of `ContentIdentity`, `SourceIdentity`, location identity, runtime locator, and discovery locator. Delimiter-safe, length-framed serialization for `SourceIdentity`.
- **Database Schema & Migration V2 → V3:** 
  - Table `mobile_storage_sources` for persistent storage source roots.
  - Table `mobile_scan_checkpoints` with foreign key cascade to `mobile_storage_sources`.
  - Column `is_provisional INTEGER NOT NULL DEFAULT 1` added to `mobile_tracks`.
  - Non-destructive SQLite migration preserving 100% of M1-M3 data.
- **Repository & DAO Boundaries:** Clean unidirectional architecture (`UI -> ViewModel -> Repository -> DAO -> Room`) through `StorageSourceRepository` and `ScanCheckpointRepository`.
- **Permission Foundation:** `StoragePermissionChecker` domain abstraction with `AndroidStoragePermissionChecker` platform implementation (`READ_MEDIA_AUDIO` on API 33+, legacy `READ_EXTERNAL_STORAGE` on API <=32, zero prompt on app startup).
- **Security Invariants:** Zero `INTERNET` permission, zero `MANAGE_EXTERNAL_STORAGE`, zero runtime network capabilities. Single ExoPlayer and single `PlaybackPort` authority preserved without alteration.

### Explicitly Deferred Functionality
Per absolute scope instructions, the following are **NOT** implemented in M4-A:
- **MediaStore scanning is NOT implemented in M4-A.**
- **SAF recursive scanning is NOT implemented in M4-A.**
- **Metadata extraction is NOT implemented in M4-A.**
- **Artwork extraction is NOT implemented in M4-A.**
- **Volume lifecycle is NOT implemented in M4-A.**
- **Audio focus is NOT implemented in M4-A.**
- Becoming-noisy receiver, SD card broadcast receivers, sync, AI, cloud, Android Auto, iOS, KMP/CMP, and DSP are deferred.

---

## 2. Storage Source Domain Model

Located in `org.harmonia.player.domain.model.storage`:

- **Pure JVM Domain:** Free of any `android.*`, `androidx.room.*`, `androidx.media3.*`, or `androidx.compose.*` imports.
- **`StorageSource` Sealed Class:**
  - `StorageSource.MediaStorePrimary`: Represents the device's internal primary shared audio collection. Defaults to enabled and auto-rescan enabled.
  - `StorageSource.MediaStoreVolume`: Represents secondary shared storage volumes (e.g., adopted or removable SD cards accessible via MediaStore volume name).
  - `StorageSource.SafDocumentTree`: Represents user-granted folder trees via Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`). Stores persistent URI string, display name, and persistable permission grant status.
  - `StorageSource.InternalSyncStorage`: Represents app-private synchronized storage root (`/data/user/0/org.harmonia.player/files/sync_storage`) for future local desktop-to-mobile sync.

---

## 3. Identity Model & Semantics

M4-A strictly distinguishes five identity facets:

1. **Content Identity (`ContentIdentity`):**
   - Cryptographic hash (e.g. SHA-256) of the raw audio stream or file bytes.
   - Independent of file name, tag metadata, directory path, or storage volume.
2. **Source Identity (`SourceIdentity`):**
   - Uniquely identifies where a file lives across physical and virtual storage providers: `(sourceType, volumeIdentifier, relativePathOrDocumentId)`.
   - Uses length-framed serialization: `v1:{type}:{volLen}:{vol}:{pathLen}:{path}`.
   - Delimiter-safe against colons in tree URIs (`content://...`) and directory paths.
3. **Location Identity:**
   - Evaluated as `(sourceIdentity, fileSizeBytes, lastModifiedMs)`.
   - Enables fast stat-based change detection without reading audio bytes.
4. **Runtime Locator:**
   - Platform URI string or canonical path used by ExoPlayer / Media3 to stream audio bytes.
5. **Discovery Locator:**
   - Ephemeral MediaStore `_ID` or document tree cursor row used only during discovery; **never used as a durable primary key**.
6. **Track Identity (`is_provisional`):**
   - In M4-A, discovered or un-reconciled tracks default to `isProvisional = true`.
   - Tracks are **never** keyed by metadata alone.

---

## 4. Room Migration V2 → V3

### Schema Additions

```sql
-- 1. Storage Sources Table
CREATE TABLE IF NOT EXISTS `mobile_storage_sources` (
    `id` TEXT NOT NULL,
    `source_type` TEXT NOT NULL,
    `display_name` TEXT NOT NULL,
    `volume_identifier` TEXT,
    `root_uri_or_path` TEXT,
    `is_enabled` INTEGER NOT NULL,
    `auto_rescan` INTEGER NOT NULL,
    `created_at_ms` INTEGER NOT NULL,
    `last_scanned_at_ms` INTEGER,
    `has_persistable_permission` INTEGER NOT NULL,
    PRIMARY KEY(`id`)
);

-- 2. Scan Checkpoints Table
CREATE TABLE IF NOT EXISTS `mobile_scan_checkpoints` (
    `source_id` TEXT NOT NULL,
    `generation_version` INTEGER NOT NULL,
    `scan_state` TEXT NOT NULL,
    `last_successful_scan_ms` INTEGER NOT NULL,
    `scanned_items_count` INTEGER NOT NULL,
    `last_error_message` TEXT,
    PRIMARY KEY(`source_id`),
    FOREIGN KEY(`source_id`) REFERENCES `mobile_storage_sources`(`id`) ON DELETE CASCADE
);

-- Index for checkpoint foreign key
CREATE INDEX IF NOT EXISTS `index_mobile_scan_checkpoints_source_id` 
ON `mobile_scan_checkpoints` (`source_id`);

-- 3. Provisional flag on mobile_tracks
ALTER TABLE `mobile_tracks` ADD COLUMN `is_provisional` INTEGER NOT NULL DEFAULT 1;
```

### Data Preservation Verification
- The automated migration test `RoomMigrationV2ToV3AndStorageDaoTest` inserts pre-existing V2 data into `mobile_tracks`, `mobile_albums`, `mobile_artists`, `mobile_local_files`, and `mobile_playlists`.
- Upon migration execution, all 5 entities are queried and verified to have retained 100% of their data with no schema corruption or lost records.

---

## 5. Permission Foundation

### Contract: `StoragePermissionChecker`
- Domain enum `StoragePermissionState`: `GRANTED`, `DENIED`, `PERMANENTLY_DENIED`.
- Platform implementation: `AndroidStoragePermissionChecker`.
  - Inspects API level:
    - API >= 33 (`TIRAMISU`): Checks `android.permission.READ_MEDIA_AUDIO`.
    - API < 33: Checks `android.permission.READ_EXTERNAL_STORAGE`.
  - Inspects `StorageSource`:
    - `MediaStorePrimary` / `MediaStoreVolume`: Checks platform audio read permission.
    - `SafDocumentTree`: Checks `context.contentResolver.persistedUriPermissions`.
    - `InternalSyncStorage`: App-private storage; always `GRANTED`.
- **Zero startup prompt:** No permission dialogs or requests are triggered during `HarmoniaApp.onCreate()` or `MainActivity.onCreate()`. Permissions are checked on-demand when requested by scanning workflows.

---

## 6. Architecture & Dependency Wiring

- `ServiceLocator` provides singleton instances for:
  - `storageSourceRepository`: `OfflineStorageSourceRepository` (seeds default `MediaStorePrimary` source).
  - `scanCheckpointRepository`: `OfflineScanCheckpointRepository`.
  - `storagePermissionChecker`: `AndroidStoragePermissionChecker`.
- Compose UI layer does not have direct access to DAOs or Room databases. All operations pass through repository boundaries.

---

## 7. Verification & Test Evidence

### JVM Unit Tests (17 Classes, 81 Tests)
| Test Class | Tests | Status |
|:---|:---:|:---:|
| `StorageSourceAndIdentityTest` | 8 | PASS |
| `ScanCheckpointTest` | 2 | PASS |
| `StoragePermissionStateTest` | 1 | PASS |
| `StorageSourceAndCheckpointRepositoryTest` | 2 | PASS |
| `TrackDomainTest` | 4 | PASS |
| `PlaylistDomainTest` | 2 | PASS |
| `OfflinePlaylistRepositoryTest` | 11 | PASS |
| `OfflineSearchRepositoryTest` | 9 | PASS |
| `OfflineTrackRepositoryTest` | 3 | PASS |
| `OfflineListeningEventRepositoryTest` | 2 | PASS |
| `OfflinePlaybackStateRepositoryTest` | 2 | PASS |
| `PlaybackQueueLogicTest` | 6 | PASS |
| `PlaybackViewModelTest` | 9 | PASS |
| `PlaylistViewModelTest` | 6 | PASS |
| `SearchViewModelTest` | 4 | PASS |
| `LibrarySortingTest` | 8 | PASS |
| `NavigationDestinationTest` | 2 | PASS |
| **Total JVM Tests** | **81** | **ALL PASS (0 Failures, 0 Skipped)** |

### Android Connected Tests on Emulator (`emulator-5554`, API 36, 49 Tests)
| Test Class | Tests | Status |
|:---|:---:|:---:|
| `RoomMigrationV2ToV3AndStorageDaoTest` (NEW) | 5 | PASS |
| `RoomMigrationAndPlaylistDaoTest` | 12 | PASS |
| `RealAndroidPlaybackTest` | 7 | PASS |
| `HarmoniaNavigationAndLibraryUiTest` | 8 | PASS |
| `M3EndToEndIntegrationTest` | 5 | PASS |
| `NowPlayingAndMiniPlayerUiTest` | 4 | PASS |
| `PlaylistsFlowUiTest` | 4 | PASS |
| `ArtworkProviderTest` | 4 | PASS |
| **Total Connected Tests** | **49** | **ALL PASS (0 Failures, 0 Skipped)** |

### Core Regression Tests
- `uv run pytest tests/ -q`
- **173 passed**, 0 failures, 8 deprecation warnings.

---

## 8. Security & Static Audit

- `INTERNET` permission: **ABSENT**
- `MANAGE_EXTERNAL_STORAGE` permission: **ABSENT**
- Domain layer purity: **100% JVM pure** (0 `android.*`, 0 `androidx.room.*`, 0 `androidx.media3.*`, 0 `androidx.compose.*`).
- MediaStore scanner calls: **0** in M4-A.
- Playback authority: **Single ExoPlayer**, single `PlaybackPort`, single service.
- Artwork architecture: M3 `$validHash.jpg` in `filesDir/artwork` with LRU cache preserved.

---

## 9. Conclusion & Readiness

M4-A is fully validated and verified across unit, database, integration, and platform runtime suites. The storage source and identity foundation is in place for metadata extraction (M4-B) and scanning (M4-C).

**Gate Status:** M4-A COMPLETE — READY FOR M4-B.
