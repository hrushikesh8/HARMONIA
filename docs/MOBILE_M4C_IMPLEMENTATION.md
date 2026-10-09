# Harmonia Mobile — Milestone M4-C Implementation Report
**Local Media Scanner & Incremental Generation Engine**

**Document Classification:** Engineering Release Report & Verification Evidence  
**Status:** COMPLETE & FROZEN  
**Baseline Anchor:**
- M3 Complete Tag: `restore-point/mobile-m3-complete` (`c952e15`)
- M4-A Complete Tag: `restore-point/mobile-m4a-complete` (`f389fbd`)
- M4-B Complete Tag: `restore-point/mobile-m4b-complete` (`91447c7`)
- M4-C Restore Point Tag: `restore-point/mobile-m4c-complete`

---

## 1. Executive Summary

Milestone M4-C completes the local media discovery and deterministic reconciliation subsystem for Harmonia Mobile. It introduces provider-neutral discovery contracts, concrete platform scanners for Android MediaStore and the Storage Access Framework (SAF), a multi-tier identity model preventing metadata collisions, streaming SHA-256 content hashing, safe incremental freshness reconciliation, and integration with M4-B metadata extraction and M3 content-addressed artwork caching.

---

## 2. Architecture & Design

### 2.1 Component Flow

```
Android MediaStore / SAF Tree
             │
             ▼
   MediaSourceScanner (MediaStoreScanner / SafTreeScanner)
             │
             ▼
   DiscoveredMedia (Provider-Neutral Domain Candidates)
             │
             ▼
   MediaReconciliationEngine (DefaultMediaReconciliationEngine)
      ├─ Freshness Check (size + lastModified + lastGeneration)
      ├─ ContentHasher (Streaming 16KB SHA-256 outside Room transactions)
      ├─ AudioMetadataReader (M4-B AndroidAudioMetadataReader outside Room transactions)
      └─ Room v3 Persistence (Short atomic DB transactions)
             │
             ▼
   Track / LocalFile / Album / Artist Persistence & ScanCheckpoint Advancement
```

### 2.2 Strict Invariants Enforced
1. **0 MediaStore imports in domain:** Pure domain contracts (`DiscoveredMedia`, `MediaSourceScanner`, `MediaReconciliationEngine`).
2. **0 Android Uri / DocumentFile in domain:** Runtime locators represented as platform-neutral strings.
3. **No Metadata-Only Track Identity:** Durable track identity is decoupled from metadata tags; physical files are identified by cryptographic content identity (`fileHash` + `fileSize`) and location identity (`SourceIdentity`).
4. **MediaStore `_ID` Demotion:** MediaStore `_ID` is used purely as an ephemeral discovery locator, never as a durable primary key.
5. **Single Artwork Authority:** Reuses existing M3 `ArtworkProvider`; no secondary cache or disk storage.
6. **Non-Destructive Scanning:** Disappeared files in full reconciliation transition to `MISSING` availability; database records and user state are never deleted on scan failures.
7. **Room Transaction Safety:** Heavy I/O (bitstream reads, hashing, decoding) occurs strictly on `Dispatchers.IO` outside database transactions.

---

## 3. Identity Model & Reconciliation State Machine

### 3.1 Multi-Tier Identity Architecture
- **Content Identity:** `SHA-256` digest of audio bitstream + file size. Authoritative identity of the audio content.
- **Source Identity:** Length-framed composite of `sourceType`, `volumeId`, and `relativePathOrDocId` (`SourceIdentity.toSerializedKey()`).
- **Runtime Locator:** The actual platform URI string used by ExoPlayer for bitstream access (`content://...` or `content://.../document/...`).
- **Discovery Locator:** Provider-ephemeral identifier (`MediaStore._ID` or SAF document ID) used only during discovery query iterations.
- **Freshness State:** `sizeBytes` + `lastModifiedEpochSec` + `lastGeneration`. Evaluates whether a file has changed without reading bitstream.

### 3.2 Candidate Reconciliation State Transitions
- **`NEW`**: File URI and content hash not in library -> Extract metadata via M4-B, calculate SHA-256, create `TrackEntity`, `LocalFileEntity`, and link `AlbumEntity`/`ArtistEntity`.
- **`UNCHANGED`**: Matching file URI, identical size, and verified modification timestamp -> Skip bitstream and metadata extraction; mark `AVAILABLE` if previously unverified.
- **`CHANGED`**: Matching file URI, but modified timestamp or file size altered -> Re-read metadata and recompute SHA-256 outside transaction; update factual attributes and freshness.
- **`MOVED` / `RELOCATED`**: New URI, but content hash matches existing `LocalFile` -> Update `file_uri`, `storage_type`, and set status to `AVAILABLE` without creating duplicate tracks.
- **`MISSING`**: In `FULL_RECONCILIATION`, existing files absent from discovered candidate set -> Mark `availabilityStatus = "MISSING"`. Records are preserved intact.
- **`FAILED`**: Corrupt file or unreadable bitstream -> Isolated per file; scan continues and completes remaining items.

---

## 4. Platform Scanners

### 4.1 MediaStoreScanner
- Queries `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` (or volume-specific content URI on API 29+).
- Projection limited to: `_ID`, `DISPLAY_NAME`, `MIME_TYPE`, `SIZE`, `DATE_MODIFIED`, `IS_MUSIC`, `RELATIVE_PATH` (API 29+), `GENERATION_MODIFIED` (API 30+).
- Audio filter: Bounded MIME and extension check (`MP3`, `FLAC`, `M4A`, `AAC`, `WAV`).
- Incremental mode queries: `GENERATION_MODIFIED > ?` (API 30+) or `DATE_MODIFIED > ?` (API < 30).
- Permission-aware: Yields immediately if `StoragePermissionChecker.canScanAudioMedia()` returns false.

### 4.2 SafTreeScanner
- Recursively traverses user-granted directory tree via native `DocumentsContract.buildChildDocumentsUriUsingTree` without third-party library dependencies.
- Bounded recursion depth: Maximum 15 levels to prevent symlink and virtual hierarchy infinite loops.
- Error isolation: Catching `SecurityException` and traversal errors per directory level.

---

## 5. Test Evidence & Regression Status

### 5.1 Authoritative Test Summary
| Test Suite | Total Tests | Passed | Failed | Skipped | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Mobile JVM Unit Tests** | 101 | 101 | 0 | 0 | **PASS** |
| **Android Connected Tests (API 36)** | 71 | 71 | 0 | 0 | **PASS** |
| **Core Regression Suite (Python)** | 173 | 173 | 0 | 0 | **PASS** |
| **Debug APK Assembly** | 1 | 1 | 0 | 0 | **PASS** |

### 5.2 Mobile JVM Test Breakdown (21 Classes / 101 Tests)
- `AudioFileFilterTest`: 2 tests
- `DiscoveredMediaAndIdentityTest`: 8 tests
- `ExtractedAudioMetadataTest`: 2 tests
- `MetadataNormalizerTest`: 8 tests
- `OfflineListeningEventRepositoryTest`: 2 tests
- `OfflinePlaybackStateRepositoryTest`: 2 tests
- `OfflinePlaylistRepositoryTest`: 11 tests
- `OfflineSearchRepositoryTest`: 9 tests
- `OfflineTrackRepositoryTest`: 3 tests
- `PlaybackQueueLogicTest`: 6 tests
- `PlaybackViewModelTest`: 9 tests
- `PlaylistDomainTest`: 2 tests
- `PlaylistViewModelTest`: 6 tests
- `ScanCheckpointTest`: 2 tests
- `SearchViewModelTest`: 4 tests
- `StoragePermissionStateTest`: 1 test
- `StorageSourceAndCheckpointRepositoryTest`: 2 tests
- `StorageSourceAndIdentityTest`: 8 tests
- `TrackDomainTest`: 4 tests
- `LibrarySortingTest`: 8 tests
- `NavigationDestinationTest`: 2 tests

### 5.3 Android Connected Test Breakdown (10 Classes / 71 Tests on `medium_phone(AVD) - 16`)
- `AndroidAudioMetadataReaderTest`: 7 tests
- `ArtworkProviderTest`: 4 tests
- `HarmoniaNavigationAndLibraryUiTest`: 8 tests
- `M3EndToEndIntegrationTest`: 5 tests
- `MediaReconciliationEngineTest`: 15 tests
  - `initial_scan_discovers_and_imports_new_tracks_and_local_files`
  - `incremental_scan_recognizes_unchanged_files_and_skips_reprocessing`
  - `scan_with_embedded_artwork_stores_artwork_in_m3_artwork_provider`
  - `full_reconciliation_marks_missing_file_without_deleting_database_records`
  - `permission_denied_aborts_without_modifying_library_or_checkpoint`
  - `moved_or_renamed_file_reconciles_by_content_hash_without_creating_duplicate_track`
  - `duplicate_byte_identical_files_at_distinct_locations_preserve_both_physical_instances`
  - `corrupt_or_unreadable_file_is_isolated_and_scan_continues`
  - `fatal_media_store_query_failure_aborts_without_advancing_checkpoint`
  - `content_hasher_computes_deterministic_sha256_hash`
  - `real_media_store_runtime_discovery_and_reconciliation`
  - `successful_mediastore_incremental_scan_advances_generation_watermark_using_correct_value`
  - `later_mediastore_scan_discovers_newly_inserted_item_with_greater_provider_generation`
  - `partial_scan_does_not_advance_checkpoint_past_unprocessed_candidates`
  - `saf_checkpoint_handling_does_not_confuse_timestamps_with_mediastore_generations`
- `NowPlayingAndMiniPlayerUiTest`: 4 tests
- `PlaylistsFlowUiTest`: 4 tests
- `RealAndroidPlaybackTest`: 7 tests
- `RoomMigrationAndPlaylistDaoTest`: 12 tests
- `RoomMigrationV2ToV3AndStorageDaoTest`: 5 tests

### 5.4 Platform Runtime vs Synthetic Classification & Invariants
1. **Real MediaStore Discovery & Reconciliation:** Authoritatively verified on Android API 36 emulator via `real_media_store_runtime_discovery_and_reconciliation`. Inserts audio to Android MediaStore, discovers it via `MediaStoreScanner`, resolves `content://` URI, hashes bitstream, extracts M4-B metadata, reconciles into Room, and verifies generation watermark advancement.
2. **SAF DocumentTree Traversal:** Structurally verified via native `DocumentsContract` implementation; covered by unit/synthetic tests (`SafTreeScanner`, `saf_checkpoint_handling_does_not_confuse_timestamps_with_mediastore_generations`). Establishing real user-granted SAF document trees on the emulator requires interactive system picker UI, which cannot be invoked headlessly in CI without UI interaction.
3. **Genuine Move / Rename Reconciliation:** Authoritatively verified via `moved_or_renamed_file_reconciles_by_content_hash_without_creating_duplicate_track` using a genuine filesystem move (`renameTo`). The old path ceases to exist (`exists() == false`), the reconciliation engine checks prior locator accessibility, updates the existing `LocalFileEntity` in-place (same `id`, new `fileUri`, updated `verifiedAt`), and preserves the single logical `TrackEntity` without creating duplicates.
4. **Legitimate Duplicate File Protection:** Authoritatively verified via `duplicate_byte_identical_files_at_distinct_locations_preserve_both_physical_instances`. When two distinct physical locations contain byte-identical audio files and both exist simultaneously, the engine does NOT conflate or overwrite them; it maintains 1 logical `TrackEntity` representing the musical work and 2 distinct physical `LocalFileEntity` rows with unique IDs and paths.
5. **Checkpoint Semantics & Safety Invariants:**
   - **Provider Generation vs Freshness Timestamps:** MediaStore incremental query relies on provider monotonic `GENERATION_MODIFIED` on API 30+ (`DATE_MODIFIED` on API < 30). `DiscoveredMedia` holds `generation: Long?` distinctly from `lastModifiedEpochSec: Long`.
   - **SAF Checkpoint Separation:** SAF document trees do not possess provider generation counters. SAF checkpoints record scan wall-clock time in `lastScanTimestamp` and set `lastGeneration = 0L`, completely preventing cross-provider contamination or timestamp confusion.
   - **Partial Scan Non-Advancement:** When `failed > 0` (e.g. unreadable candidates), `lastGeneration` retains `previousCheckpoint.lastGeneration`, preventing watermarks from advancing past unindexed candidates.
   - **Fatal Query Non-Advancement:** Exceptions during scan queries abort without mutating or recording checkpoints.
   - **Complete Successful Scan:** Advances `lastGeneration` to the maximum candidate generation (`maxOf(previousGen, candidateGens.max())`) and marks status `COMPLETED`.

---

## 6. Static Forensic & Security Audit

- **0 MediaStore imports in domain:** Verified.
- **0 Android Uri / DocumentFile in domain:** Verified.
- **0 direct MediaMetadataRetriever outside data/platform:** Verified.
- **0 duplicate ArtworkProvider / Cache:** Verified.
- **0 playback / queue / ExoPlayer regressions:** Single playback authority intact.
- **0 INTERNET permission:** Preserved.
- **0 MANAGE_EXTERNAL_STORAGE permission:** Preserved.
- **0 metadata-only track identities:** Preserved.
- **0 MediaStore `_ID` used as durable PK:** Preserved.
- **0 TODO/FIXME comments introduced:** Clean implementation.

---

## 7. Deferred M4 Scope
- **M4-D:** Volume lifecycle events, broadcast receivers (`ACTION_MEDIA_MOUNTED`, `ACTION_MEDIA_EJECT`), and peripheral audio ergonomics (`AudioFocusCoordinator`, `BecomingNoisyReceiver`).
- **M4-E:** Final M4 integration, settings/storage management UI, end-to-end scanner-to-playback verification gate.
