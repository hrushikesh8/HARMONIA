# HARMONIA MOBILE — M4-B IMPLEMENTATION DOCUMENTATION

## Metadata Reader & Content-Addressed Artwork Engine

**Document Version:** 1.0.0  
**Phase:** M4-B (Metadata & Artwork Engine)  
**Status:** COMPLETED  
**Commit:** `feat(mobile): implement M4B metadata and artwork engine`  
**Restore Point:** `restore-point/mobile-m4b-complete`  
**Approved Baseline Commit:** `f389fbd` (`restore-point/mobile-m4a-complete`)  
**Frozen M3 Baseline:** `c952e15` (`restore-point/mobile-m3-complete`)

---

## 1. Executive Summary

Milestone **M4-B** implements the factual, platform-isolated audio metadata extraction and content-addressed embedded artwork engine for Harmonia Mobile.

### Scope Implemented
- **Pure Domain Metadata Contract:** `ExtractedAudioMetadata` and `AudioMetadataReader` interface in `org.harmonia.player.domain.metadata`, completely decoupled from Android platform types.
- **Strictly Nullable Architecture:** All extracted fields (`title`, `artist`, `album`, `albumArtist`, `genre`, `trackNumber`, `discNumber`, `year`, `durationSec`, `bitrateKbps`, `sampleRateHz`, `mimeType`, `artworkHash`, `rawArtworkBytes`) are modeled as nullable. No synthetic or guessed metadata is fabricated.
- **Normalization Utilities:** Pure JVM `MetadataNormalizer` with Unicode NFC normalization, trimming, bounded length limits, compound track/disc number parsing (`"4/12"` -> 4), 4-digit calendar year extraction, and duration/bitrate/sample rate conversions.
- **Multi-Artist Preservation:** Composite artist strings (e.g., `"Artist A; Artist B"`, `"Queen / David Bowie"`) are preserved intact without premature splitting.
- **Android MediaMetadataRetriever Boundary:** `AndroidAudioMetadataReader` safely encapsulates native Android extraction inside the data infrastructure boundary with guaranteed `retriever.release()` execution and graceful fallback.
- **Content-Addressed Artwork Pipeline:** Reuses the verified M3 `ArtworkProvider`: extracts embedded picture bytes, hashes to SHA-256 (`MetadataNormalizer.computeArtworkHash`), checks local availability, and stores in the existing application sandbox cache (`filesDir/artwork/$artworkHash.jpg`).
- **Memory Safety:** Imposes strict bounds (max 15MB) on embedded artwork byte arrays and isolates corrupt image payloads without crashing.

### Explicitly Deferred Scope
Per absolute scope requirements, the following are **NOT** implemented in M4-B:
- **MediaStore scanning is NOT implemented in M4-B.**
- **SAF recursive scanning is NOT implemented in M4-B.**
- **Generation checkpoints are NOT modified in M4-B.**
- **Volume lifecycle & SD card handling are NOT implemented in M4-B.**
- **Audio focus & becoming noisy receiver are NOT implemented in M4-B.**
- **Sync, AI, cloud, tag writing, MusicBrainz, and AcoustID are NOT implemented in M4-B.**

---

## 2. Architecture & Contracts

### Domain Model: `ExtractedAudioMetadata`
```kotlin
data class ExtractedAudioMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val genre: String? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val year: Int? = null,
    val durationSec: Double? = null,
    val bitrateKbps: Int? = null,
    val sampleRateHz: Int? = null,
    val mimeType: String? = null,
    val artworkHash: String? = null,
    val rawArtworkBytes: ByteArray? = null
)
```

### Domain Boundary: `AudioMetadataReader`
```kotlin
interface AudioMetadataReader {
    suspend fun readMetadata(
        sourceLocator: String,
        extractArtwork: Boolean = true,
        includeRawArtworkBytes: Boolean = false
    ): ExtractedAudioMetadata
}
```

### Platform Implementation: `AndroidAudioMetadataReader`
- Package: `org.harmonia.player.data.local.metadata`
- Consumes platform locators (`content://` URIs, `file://` URIs, and local file paths).
- Accesses `contentResolver.openFileDescriptor(uri, "r")` for maximum provider compatibility across MediaStore and SAF.
- Extracts standard keys from `android.media.MediaMetadataRetriever`.
- Releases retriever deterministically in a `finally` block.
- Catches all `Throwable` errors to guarantee that unreadable, corrupt, or unsupported audio files return safe fallback (`ExtractedAudioMetadata()`) without crashing.

---

## 3. Metadata Normalization Engine

`MetadataNormalizer` provides pure JVM utilities:
1. **Text Normalization:**
   - Trims whitespace.
   - Applies `Normalizer.normalize(trimmed, Normalizer.Form.NFC)` (e.g. decomposed `e\u0301` -> composed `\u00e9`).
   - Converts empty or whitespace-only strings to `null`.
   - Bounded by maximum string length (default 500 characters) to prevent unbounded memory usage from malicious or corrupted ID3 tags.
2. **Track & Disc Numbers:**
   - Splits on compound delimiters (`/`, `-`, ` `, `,`).
   - Parses first segment as Int (`"4/12"` -> 4, `"04"` -> 4).
   - Validates ranges: track `1..9999`, disc `1..99`.
   - Rejects leading negative signs or invalid non-numeric strings safely as `null`.
3. **Calendar Year:**
   - Uses regex `\b(18|19|20|21)\d{2}\b` to parse 4-digit years from ISO date strings (`"2022-05-17"` -> 2022).
   - Bounded within `1800..2100`.
4. **Duration & Bitrate:**
   - Duration ms -> seconds (`ms / 1000.0`).
   - Bitrate bps -> kbps (`bps / 1000`). Handles already-kbps values.

---

## 4. Artwork Pipeline & M3 `ArtworkProvider` Integration

Harmonia Mobile maintains exactly ONE artwork authority: the verified M3 [`ArtworkProvider`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/ArtworkProvider.kt).

### Ingestion Flow:
```
retriever.embeddedPicture (raw bytes)
       ↓
Memory check (size <= 15MB)
       ↓
SHA-256 hash = MetadataNormalizer.computeArtworkHash(bytes)
       ↓
isArtworkAvailable(hash) check
       ↓ (if missing)
ArtworkProvider.storeArtwork(hash, bytes)
       ↓
Saved to context.filesDir/artwork/$hash.jpg
       ↓
In-memory LruCache<String, Bitmap>(60) warmed
```

### Safety & Invariants:
- Zero duplicate artwork caching systems.
- Zero `.bin` scheme.
- Deduplication: multiple tracks sharing the same embedded cover share the identical SHA-256 hash and skip redundant disk writes.
- Corrupted picture payloads are caught safely without aborting the metadata extraction process.

---

## 5. Verification & Test Evidence

### JVM Unit Tests (19 Classes, 91 Tests)
| Test Class | Tests | Status |
|:---|:---:|:---:|
| `MetadataNormalizerTest` (NEW) | 8 | PASS |
| `ExtractedAudioMetadataTest` (NEW) | 2 | PASS |
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
| **Total JVM Unit Tests** | **91** | **ALL PASS (0 Failures, 0 Skipped)** |

### Connected Android Tests on Emulator (`emulator-5554`, API 36, 56 Tests)
| Test Class | Tests | Status |
|:---|:---:|:---:|
| `AndroidAudioMetadataReaderTest` (NEW) | 7 | PASS |
| `RoomMigrationV2ToV3AndStorageDaoTest` | 5 | PASS |
| `RoomMigrationAndPlaylistDaoTest` | 12 | PASS |
| `RealAndroidPlaybackTest` | 7 | PASS |
| `HarmoniaNavigationAndLibraryUiTest` | 8 | PASS |
| `M3EndToEndIntegrationTest` | 5 | PASS |
| `NowPlayingAndMiniPlayerUiTest` | 4 | PASS |
| `PlaylistsFlowUiTest` | 4 | PASS |
| `ArtworkProviderTest` | 4 | PASS |
| **Total Connected Tests** | **56** | **ALL PASS (0 Failures, 0 Skipped)** |

### Core Regression Tests
- `uv run pytest tests/ -q`
- **173 passed**, 0 failures, 8 deprecation warnings.

---

## 6. Security Audit

- `INTERNET` permission: **ABSENT**
- `MANAGE_EXTERNAL_STORAGE` permission: **ABSENT**
- Domain layer purity: **100% JVM pure** (0 `android.*` imports).
- Compose UI layer: **0** `MediaMetadataRetriever` imports, **0** Room DAO calls.
- ExoPlayer: **0** modifications; existing native Media3 architecture preserved.
- Untrusted input defense: Bounded string lengths (500 chars), bounded artwork payloads (15MB), safe swallow of corrupt audio bitstreams.

---

## 7. Conclusion & Readiness

M4-B establishes a robust, factual metadata and content-addressed artwork engine ready to be orchestrated by the M4-C scanner.

**Gate Status:** M4-B COMPLETE — READY FOR M4-C.
