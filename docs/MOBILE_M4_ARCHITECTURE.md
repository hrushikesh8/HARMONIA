# Harmonia Mobile — Milestone M4 Architecture Specification
**Local Media Ingestion & Device Storage Engine**

**Document Classification:** Engineering Architecture Specification & Pre-Implementation Gate  
**Status:** REVISED & FROZEN (REVIEW 2) — AWAITING IMPLEMENTATION APPROVAL  
**Author:** Harmonia Lead Research Architect  
**Accepted Baseline:** Harmonia Mobile M3-E (Commit `c952e15`, Tag `restore-point/mobile-m3-complete`)  
**Scope Directive:** Zero production code modifications. Architectural blueprint, data contracts, identity models, storage lifecycles, and subphase definitions only.

---

## Architecture Corrections — Review 2

Following the architectural review pass, the specification incorporates the following mandatory corrections:

1. **Rejection of Metadata-Only Provisional Track Identity:** Metadata-only slugs (UUIDv5) are explicitly rejected as a collision-free identity mechanism. A multi-tier identity model is established: Content Identity (`fileHash` + `fileSize`), Location Identity (`SourceIdentity`), Runtime Locator (`contentUri` / `documentUri`), and Discovery Locator (`MediaStore._ID`). Two distinct physical files with identical metadata (e.g. alternate masterings, remasters, live vs studio, or duplicate tags) remain completely distinct. Two copies of identical bytes are recognized via Content Identity SHA-256 and linked to the same track.
2. **MediaStore ID Demotion to Discovery Locator:** Android MediaStore integer `_ID` values are formally classified as ephemeral platform discovery handles, NEVER as universal content identities or database foreign keys.
3. **Decoupling of Incremental Scanning from Full Reconciliation:** Incremental change detection via `MediaStore.getGeneration()` is decoupled from Full Source Reconciliation. Incremental scans process only newly added/modified rows without enumerating the whole library. Full reconciliation (which detects disappeared/deleted files) is triggered strictly on explicit user request, generation counter resets, or storage volume remounting.
4. **Provider-Neutral Scanner Boundary (`MediaSourceScanner`):** Introduced a unified scanner boundary supporting both `MediaStoreScanner` (for internal and external volumes) and `SafTreeScanner` (for user-selected SAF document trees with recursive traversal and persisted URI permissions).
5. **Artwork Reconciliation with Existing M3 `ArtworkProvider`:** Eliminated the proposed `.bin` format. M4 metadata extraction strictly integrates with the verified M3 `ArtworkProvider`, writing content-addressed `$artworkHash.jpg` files to `filesDir/artwork` and warming the existing in-memory LRU cache. Exactly one artwork system exists.
6. **Removal of Unsupported Performance Claims:** Removed speculative claims such as `"<100ms for 10,000 tracks"` and blanket `"O(1) memory"` guarantees. Replaced with bounded 50-item batch streaming, $O(\text{batch size})$ memory consumption, and an empirical device benchmarking methodology.
7. **Qualified Best-Effort Metadata Extraction:** All extracted metadata fields are explicitly modeled as nullable (`title?`, `artist?`, `album?`, `durationSec?`, `bitrate?`, `sampleRate?`). The scanner does not invent or fabricate metadata.
8. **Audio Focus State Machine Refinement:** Strict separation of `PLAYING`, `PAUSED_BY_USER`, `PAUSED_BY_FOCUS`, and `STOPPED`. Auto-resume on focus regain occurs ONLY if playback was actively running before transient focus loss. Becoming Noisy (headphone unplug) transitions to `PAUSED_BY_USER`, strictly requiring manual user interaction to resume.
9. **M4 Scope Purity Enforcement:** Reaffirmed that M4 introduces zero new playback engines, DSP, equalizers, AI, cloud, or sync networking. M4 is strictly local media ingestion, storage lifecycle, and essential audio ergonomics.
10. **Updated ADR Baseline:** ADR-026 through ADR-031 updated to reflect these corrected decisions.

---

## 1. Approved Direction

Following the forensic evaluation in `MOBILE_M4_RESEARCH.md` and the Review 2 corrections, the approved direction for Milestone 4 is:

> **M4: LOCAL MEDIA INGESTION & DEVICE STORAGE ENGINE**  
> *(incorporating essential peripheral audio ergonomics: Audio Focus & Becoming Noisy)*

### Core Objectives:
1. Transform Harmonia Mobile into a completely self-sufficient, autonomous offline player capable of indexing and playing music existing on the user's Android phone (internal storage, SD card, custom folders).
2. Establish the Android Scoped Storage and volume lifecycle architecture (`StorageSource`, `LocalFileRepository`, `VolumeManager`, `MediaSourceScanner`) that will act as the stable landing pad for future desktop-to-mobile synchronization (LocalSync).
3. Implement clean-room, zero-NDK metadata and embedded artwork extraction using Android's native platform APIs integrated with the existing M3 `ArtworkProvider`.
4. Integrate critical real-world audio lifecycle safety hooks (`AudioFocusCoordinator` and `BecomingNoisyReceiver`) so playback automatically ducks during navigation, pauses on incoming phone calls (auto-resuming ONLY if actively playing beforehand), and immediately halts when headphones or Bluetooth disconnect.
5. Strictly preserve existing architectural authorities (single ExoPlayer, single `PlaybackPort`, single authoritative queue, decoupled Room persistence, zero network dependencies).

---

## 2. M3 Baseline Anchor

Harmonia Mobile M3-E represents the verified, frozen baseline:
- **Git Commit:** `c952e15`
- **Git Tag:** `restore-point/mobile-m3-complete`
- **Regression Baseline:**
  - Mobile JVM Unit Tests: **68/68** passing
  - Android Connected Instrumentation Tests: **44/44** passing (on API 36 with native audio pipeline active)
  - Core Regression Tests: **173/173** passing
  - APK Build: `assembleDebug` clean
- **Architectural Status:**
  - Room schema version 2 (`mobile_tracks`, `mobile_albums`, `mobile_artists`, `mobile_local_files`, `mobile_listening_events`, `mobile_playback_state`, `mobile_playlists`, `mobile_playlist_items`).
  - Single `ExoPlayer` in `HarmoniaPlaybackService` (`MediaSessionService`).
  - Single `PlaybackPort` implementation in `HarmoniaPlayerController`.
  - Multi-tab Jetpack Compose navigation (`Home`, `Library`, `Search`, `Playlists`).
  - Offline SHA-256 content-addressed artwork cache (`ArtworkProvider`).
  - Zero `INTERNET` permission in `AndroidManifest.xml`.

---

## 3. Architectural Principles

1. **Harmonia Core Owns the Music:** Core remains the canonical authority for global music truth, metadata consensus, and audio analysis. Mobile is an offline personal player and client.
2. **Mobile Does Not Duplicate Core:** Mobile does not perform heavy acoustic fingerprinting (Chromaprint/AcoustID), Web metadata scraping, or subjective tag reconciliation. Mobile records factual file properties and provisional tags.
3. **Harmonia AI Understands the Person:** AI modeling runs independently of Mobile and Core. Mobile provides factual, unpolluted telemetry (`ListeningEvent` records); AI derives recommendations.
4. **Offline-First & Sovereign:** Ingestion operates 100% locally on the device without internet access, accounts, telemetry SDKs, or cloud dependencies.
5. **Read-Only Media Policy:** Harmonia Mobile NEVER modifies, overwrites, renames, or writes tags back to physical audio files on disk. The user's files are sacred.
6. **Ejection Invariant:** Ejecting an SD card or revoking folder permissions marks files `UNAVAILABLE` or `STORAGE_UNMOUNTED`, but NEVER deletes playlists, listening history, or track records.
7. **Scope Purity:** M4 introduces no DSP, equalizers, gapless playback redesign, cloud sync, or recommendation systems.

---

## 4. Ownership Model

### Master System Ownership Matrix

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    HARMONIA MASTER OWNERSHIP MATRIX                                     │
├──────────────────────────────┬──────────────────┬─────────────────┬──────────────────┬──────────────────┤
│ Attribute / Responsibility   │ Harmonia Core    │ Harmonia Mobile │ Harmonia AI      │ Future LocalSync │
├──────────────────────────────┼──────────────────┼─────────────────┼──────────────────┼──────────────────┤
│ Physical Media Storage       │ Desktop Storage  │ Android Storage │ None             │ Local P2P Wire   │
│ Canonical Track Identity     │ AUTHORITATIVE    │ Provisional     │ Consumer         │ Reconciler       │
│ Track Metadata Consensus     │ AUTHORITATIVE    │ Local Read-Only │ Consumer         │ Downstream Push  │
│ Device Ingestion / Scanning  │ Desktop Watcher  │ AUTHORITATIVE   │ None             │ None             │
│ Playback Execution           │ libmpv (Desktop) │ Media3/ExoPlayer│ None             │ None             │
│ Playback State / Active Queue│ None             │ AUTHORITATIVE   │ None             │ Client Session   │
│ Factual Listening Telemetry  │ Aggregator       │ Capture / Cache │ Consumer         │ Append-Only Push │
│ Personal Playlists           │ Master Store     │ Local-First     │ Smart Generator  │ 3-Way Rebase     │
│ User Taste / Context Models  │ None             │ None            │ AUTHORITATIVE    │ None             │
└──────────────────────────────┴──────────────────┴─────────────────┴──────────────────┴──────────────────┘
```

### The 9 Critical Architectural Boundary Decisions

#### 1. What happens when Mobile discovers a completely new MP3?
Mobile extracts factual technical properties (format, duration, bitrate, sample rate) and embedded tags (title, artist, album, track number, year) using `MediaMetadataRetriever` on a best-effort basis. It computes the cryptographic Content Identity (`fileHash` = SHA-256 of the audio bitstream + `fileSize`), writes a `LocalFile` entity to Room SQLite (`mobile_local_files`), and creates or links to a **Provisional local `Track`** in `mobile_tracks`.

#### 2. Does Mobile create a local Track?
**YES.** Mobile must create a local `Track` entity immediately. If Mobile only created a raw file record without a `Track`, the entire M3 presentation layer (Library, Search, Playlists, Album detail, Artist detail, Now Playing) would be unable to display or navigate the music without a connection to Desktop Core.

#### 3. Is that Track authoritative or provisional?
**PROVISIONAL.** The track's provenance is explicitly flagged as `is_provisional = 1` (`PROVISIONAL_LOCAL`). It represents a local working model of the musical work. It is authoritative for on-device playback, but provisional with respect to the global Harmonia catalog.

#### 4. How is Track identity determined locally?
Locally on Mobile, Track identity is rooted in Content Identity and verified metadata:
- If an embedded MusicBrainz Recording ID (`mbid`) exists, `track.id = mbid`.
- Otherwise, a deterministic provisional ID is generated:  
  `track.id = "local:" + sha256(fileHash + normalizedTitle + normalizedArtist + normalizedAlbum)`.  
  **Why this prevents collisions:** Because `fileHash` is a component of the provisional track key, distinct physical recordings with identical metadata (e.g. remastered editions, studio vs live bonus tracks) produce distinct provisional tracks and never collide into a single confused record.

#### 5. Relationship between Track, TrackFile, LocalFile, MediaStore ID, URI, and SHA-256:
- **`Track` (Domain/Room):** Abstract musical recording. Holds presentation metadata.
- **`TrackFile` (Core Entity):** Desktop Core's bitstream model. Not present in Mobile.
- **`LocalFile` (Mobile Entity):** Android device's physical bitstream model. Foreign-keyed to `Track.id`.
- **`MediaStore ID` (Discovery Locator):** Ephemeral integer primary key in Android `MediaStore.Audio.Media._ID`. Volatile across OS scans; used solely as an ephemeral discovery cursor, NEVER as a foreign key or universal identity.
- **`URI` (Runtime Locator):** Content URI (`content://media/external/audio/media/1234`) or Document URI (`content://.../document/...`). Used by Media3 ExoPlayer to open audio streams. Stored as `mobile_local_files.file_uri` (unique index).
- **`SHA-256` (Content Identity):** Cryptographic digest of the audio bitstream bytes. Persisted in `mobile_local_files.file_hash`. Universally maps Mobile's `LocalFile` to Core's `TrackFile` during sync.

#### 6. Metadata Divergence & Priority Hierarchy:
When metadata differs between sources, the following strict resolution hierarchy applies:
1. **Core Canonical Metadata (Highest Authority):** User-curated on Desktop, enriched with MusicBrainz/AcoustID consensus. Once received via sync, it permanently overrides local provisional tags.
2. **Future Synced Metadata:** Pushed from Desktop Core during LocalSync session.
3. **Embedded File Tags:** Extracted locally by `AudioMetadataReader` during on-device scanning. Used as initial provisional metadata.
4. **MediaStore OS Columns (Lowest Authority):** Often corrupted, truncated, or localized by Android OS media scanner. Used strictly as a fallback if embedded tags cannot be parsed.

#### 7. Classification of Fields:
- **Factual File Facts:** `fileSize`, `fileHash`, `format`, `codec`, `durationSec`, `bitrate`, `sampleRate`, `bitDepth`, `channels`, `isLossless`.
- **Canonical Music Metadata:** `title`, `artistName`, `albumTitle`, `trackNumber`, `discNumber`, `year`, `genre`, `mbid`, `artworkHash`.
- **Device-Local State:** `fileUri`, `storageType`, `availabilityStatus` (`AVAILABLE`, `UNAVAILABLE`, `STORAGE_UNMOUNTED`), `verifiedAt`, `playbackPosition`, `currentQueue`, `localPlaylists`.

#### 8. Future Desktop ↔ Mobile Sync Reconciliation:
- When Mobile connects to Desktop Core via LocalSync:
  - Mobile transmits an inventory of its `LocalFile` records (`fileHash`, `fileSize`, `fileUri`, provisional metadata).
  - Desktop matches files by `fileHash`:
    - **Match Found:** Core provides the canonical `Track.id` and canonical metadata. Mobile updates its Room database, updating the provisional track to the canonical track.
    - **Match Not Found (New Music on Phone):** Core registers the file in its Ingestion Inbox, computes canonical metadata, and optionally requests file transfer over Wi-Fi.

#### 9. Future Harmonia AI Consumption:
- Harmonia AI consumes **factual, unpolluted telemetry**:
  - `mobile_listening_events` table rows: `track_id`, `duration_listened_seconds`, `was_completed`, `was_skipped`, `playback_source = "LOCAL_STORAGE"`.
  - Real-time bitstream availability (AI will not queue unmounted tracks).
  - Clean metadata vectors (artist, album, year, duration).
- Harmonia AI never receives unvalidated heuristics or subjective scanner guesses.

---

## 5. Local File Identity Model

### Classification of Identifiers

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                            IDENTIFIER CLASSIFICATION MATRIX                                 │
├────────────────────┬─────────────────────────────┬──────────────────────────────────────────┤
│ Identifier         │ Classification Category     │ Scope & Invariants                       │
├────────────────────┼─────────────────────────────┼──────────────────────────────────────────┤
│ SHA-256            │ Content Identity            │ Universal, cryptographic, immutable.     │
│                    │                             │ Survives renames, moves, SD swaps, sync. │
├────────────────────┼─────────────────────────────┼──────────────────────────────────────────┤
│ SourceIdentity     │ Location Identity           │ Session-stable composite key             │
│                    │                             │ "sourceType:volumeIdentifier:path".      │
├────────────────────┼─────────────────────────────┼──────────────────────────────────────────┤
│ Content URI        │ Runtime Locator             │ Ephemeral stream handle passed to        │
│ / Document URI     │                             │ Media3 ExoPlayer.                        │
├────────────────────┼─────────────────────────────┼──────────────────────────────────────────┤
│ MediaStore _ID     │ Discovery Locator           │ Ephemeral OS cursor handle. Volatile     │
│                    │                             │ across media database rebuilds.          │
├────────────────────┼─────────────────────────────┼──────────────────────────────────────────┤
│ Relative Path      │ Location Attribute          │ Human-readable directory location.       │
├────────────────────┼─────────────────────────────┼──────────────────────────────────────────┤
│ Generation         │ Freshness / Checkpoint Meta │ Monotonically increasing change counter. │
└────────────────────┴─────────────────────────────┴──────────────────────────────────────────┘
```

### Collision Handling & Multi-File Invariants

1. **Two Distinct Files with Identical Metadata:**
   - Example: A studio version and a live acoustic bonus track with identical title, artist, and album tags.
   - `fileHash_A != fileHash_B`.
   - Their Content Identities differ.
   - Mobile generates two distinct `LocalFile` entities and two distinct provisional tracks:  
     `track_A.id = "local:" + sha256(fileHash_A + tags)`  
     `track_B.id = "local:" + sha256(fileHash_B + tags)`  
   - They remain completely separate in the library and cannot collide or overwrite each other.
2. **Two Copies of Identical Audio Bytes:**
   - Example: An MP3 copied to both internal `/Music` and external SD card `/Music/Backup`.
   - `fileHash_A == fileHash_B`.
   - Both files have identical Content Identity.
   - Mobile creates two distinct `LocalFile` records (reflecting their separate Location Identities and Runtime Locators), but links both to the **same single `Track`** record.
   - If the SD card is removed, the remaining `LocalFile` maintains `availabilityStatus = AVAILABLE`, and the track remains playable seamlessly.

---

## 6. Storage Sources

### Domain Abstraction: `StorageSource`

`StorageSource` is a **domain-level sealed interface** modeling storage locations on the device:

```kotlin
sealed interface StorageSource {
    val id: String
    val displayName: String
    val isRemovable: Boolean
    val isAvailable: Boolean

    data class MediaStorePrimary(
        override val id: String = "mediastore_primary",
        override val displayName: String = "Internal Audio Storage",
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean = true
    ) : StorageSource

    data class MediaStoreVolume(
        override val id: String, // e.g. "mediastore_vol_1234-5678"
        val volumeName: String,
        val volumeUuid: String,
        override val displayName: String,
        override val isRemovable: Boolean = true,
        override val isAvailable: Boolean
    ) : StorageSource

    data class SafDocumentTree(
        override val id: String, // e.g. "saf_tree_encoded_uri"
        val treeUri: String,
        override val displayName: String,
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean
    ) : StorageSource

    data class InternalSyncStorage(
        override val id: String = "internal_sync_store",
        val rootPath: String,
        override val displayName: String = "Harmonia Synced Storage",
        override val isRemovable: Boolean = false,
        override val isAvailable: Boolean = true
    ) : StorageSource
}
```

---

## 7. Scoped Storage Architecture

Harmonia Mobile targets API 36 with minimum supported API 26 (Android 8.0 Oreo).

### Permission Matrix Across Android Versions

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       ANDROID PERMISSION MATRIX                             │
├───────────────────┬───────────────────────────────────┬─────────────────────┤
│ Platform Version  │ Required Permissions              │ Ingestion Method    │
├───────────────────┼───────────────────────────────────┼─────────────────────┤
│ Android 8–12      │ android.permission.               │ MediaStore Audio +  │
│ (API 26–32)       │ READ_EXTERNAL_STORAGE             │ Optional SAF        │
├───────────────────┼───────────────────────────────────┼─────────────────────┤
│ Android 13+       │ android.permission.               │ MediaStore Audio +  │
│ (API 33–36)       │ READ_MEDIA_AUDIO                  │ Optional SAF        │
├───────────────────┼───────────────────────────────────┼─────────────────────┤
│ All Versions      │ android.permission.INTERNET       │ STRICTLY FORBIDDEN  │
│                   │ (Must remain ABSENT)              │ (Zero Network)      │
└───────────────────┴───────────────────────────────────┴─────────────────────┘
```

### Scoped Storage Invariants
1. **Zero Legacy Storage Requests:** Harmonia will NEVER request `MANAGE_EXTERNAL_STORAGE` (All Files Access). Google Play rejects music players requesting broad file manager permissions when `READ_MEDIA_AUDIO` suffices.
2. **Persistable SAF Permissions:** When the user picks a folder via SAF (`ACTION_OPEN_DOCUMENT_TREE`), the permission is immediately persisted:  
   `context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)`.
3. **Permission Denial Resilience:** If the user denies storage permission, the app does not crash or loop. It displays a clean educational empty state with a "Grant Permission" action button.

---

## 8. MediaStore Architecture

### Query Projection & Selection

```kotlin
object MediaStoreAudioContract {
    val PROJECTION = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.SIZE,
        MediaStore.Audio.Media.DATE_MODIFIED,
        MediaStore.Audio.Media.MIME_TYPE,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.YEAR,
        MediaStore.Audio.Media.TRACK,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.VOLUME_NAME else MediaStore.Audio.Media._ID,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) MediaStore.Audio.Media.GENERATION_MODIFIED else MediaStore.Audio.Media._ID
    )

    // Exclude ringtones, notifications, alarms, and podcasts
    const val SELECTION = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.SIZE} > 1024"
    const val SORT_ORDER = "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
}
```

### Supported Formats & MIME Filtering
- **MP3:** `audio/mpeg`, `audio/mp3`, `.mp3`
- **FLAC:** `audio/flac`, `audio/x-flac`, `.flac`
- **M4A / AAC:** `audio/mp4`, `audio/aac`, `audio/x-m4a`, `.m4a`, `.aac`
- **WAV:** `audio/wav`, `audio/x-wav`, `audio/wave`, `.wav`
- **Ogg / Opus:** `audio/ogg`, `audio/opus`, `.ogg`, `.opus`

---

## 9. Metadata Architecture

### Best-Effort, Qualified `AudioMetadataReader` Pipeline

Metadata extraction is executed exclusively via Android's native `android.media.MediaMetadataRetriever` (clean-room, zero native JNI/NDK dependencies, zero GPL copyleft).

**All fields are explicitly modeled as nullable:**

```kotlin
data class ExtractedAudioMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val year: Int?,
    val durationSec: Double?,
    val bitrateKbps: Int?,
    val sampleRateHz: Int?,
    val mimeType: String?,
    val rawArtworkBytes: ByteArray?
)
```

### Normalization & Fallback Rules
1. **Unicode NFC Normalization:** All text strings are normalized via `Normalizer.normalize(str, Normalizer.Form.NFC)`.
2. **Ordinal Parsing:** Track and disc numbers parsed from compound strings (`"4/12"` → track `4`).
3. **Year Extraction:** ISO dates parsed (`"2022-05-17"` → year `2022`).
4. **No Fabricated Metadata:** If artist, album, or title are missing from file tags, they are kept as `null` in the domain entity. In the UI presentation layer, appropriate localized fallback labels (`"Unknown Artist"`, `"Unknown Album"`, or the filename) are rendered dynamically without corrupting the database with fake strings.

---

## 10. Artwork Architecture

### Reconciliation with Existing M3 `ArtworkProvider`

Harmonia possesses exactly ONE authoritative artwork system: the verified M3 [`ArtworkProvider`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/ArtworkProvider.kt).

### Invariants & Workflow:
1. **Extraction:** `retriever.embeddedPicture` returns raw image bytes.
2. **Hashing:** SHA-256 digest is computed on the raw bytes: `artworkHash = sha256(bytes)`.
3. **Storage:** The scanner delegates directly to the existing provider:  
   `ArtworkProvider.getInstance(context).storeArtwork(artworkHash, bytes)`.
4. **File Format:** Files are saved strictly as `$artworkHash.jpg` in `context.filesDir/artwork` via atomic temporary file renaming (`${hash}_tmp` -> `$hash.jpg`).
5. **Deduplication:** Multiple tracks from the same album sharing identical artwork bytes compute the same SHA-256 hash. If `$artworkHash.jpg` already exists, disk writes are skipped.
6. **In-Memory Cache:** `ArtworkProvider` maintains an in-memory `LruCache<String, Bitmap>(60)`. Storing artwork automatically warms this cache.
7. **Downsampling:** Embedded pictures exceeding 1024x1024 are downsampled during write to protect memory during notification and UI display.

---

## 11. Scanner Architecture

### Provider-Neutral Discovery Boundary (`MediaSourceScanner`)

```kotlin
sealed interface ScanMode {
    data object Incremental : ScanMode
    data object FullReconciliation : ScanMode
}

data class DiscoveredMediaItem(
    val sourceId: String,
    val locationKey: String,
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
    val dateModifiedEpochSec: Long
)

interface MediaSourceScanner {
    val supportedSourceType: String
    suspend fun discoverItems(source: StorageSource, mode: ScanMode): Flow<List<DiscoveredMediaItem>>
    suspend fun reconcileSource(source: StorageSource): Flow<ReconciliationResult>
}
```

### Concrete Scanner Implementations:
1. **`MediaStoreScanner`:**
   - Queries `ContentResolver` for `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI` or volume URI.
   - For `ScanMode.Incremental`: filters strictly by `GENERATION_MODIFIED > lastGeneration`.
   - Emits batches of discovered items.
2. **`SafTreeScanner`:**
   - Recursively traverses SAF document trees using `DocumentsContract.buildChildDocumentsUriUsingTree`.
   - Queries directory children using `ContentResolver.query` with projection `[DOCUMENT_ID, DISPLAY_NAME, MIME_TYPE, SIZE, LAST_MODIFIED]`.
   - Filters by audio MIME types and extensions.
   - Persists verified URI permissions; catches `SecurityException` if permission was revoked.
   - Cooperatively checks `coroutineContext.isActive` at each directory level.

### Processing Pipeline (Bounded Batch Streaming):
1. **Discovery Stream:** Items are emitted in bounded chunks of 50 (`ScanBatch`).
2. **Change Evaluation:** If `(sizeBytes, dateModified)` match existing `LocalFile` records, item is skipped without opening file descriptors.
3. **Extraction & Ingestion:** New/modified items processed via `AudioMetadataReader` on `Dispatchers.IO`.
4. **Room Batch Transaction:** Atomic transaction updates `mobile_tracks`, `mobile_albums`, `mobile_artists`, and `mobile_local_files`.
5. **Memory Discipline:** Batched chunking guarantees memory consumption remains bounded to the size of the current batch ($O(\text{batch size})$), preventing whole-library materialization.

---

## 12. Incremental Scanning vs Full Reconciliation

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                 INCREMENTAL SCANNING vs FULL SOURCE RECONCILIATION                          │
├──────────────────────────┬─────────────────────────────┬────────────────────────────────────┤
│ Dimension                │ Incremental Scan            │ Full Source Reconciliation         │
├──────────────────────────┼─────────────────────────────┼────────────────────────────────────┤
│ Trigger                  │ App startup / background    │ Explicit "Rescan Library" button,  │
│                          │ resume / on-demand trigger  │ volume remount, generation reset.  │
├──────────────────────────┼─────────────────────────────┼────────────────────────────────────┤
│ Query Strategy           │ GENERATION_MODIFIED > last  │ Full inventory enumeration of all  │
│                          │ (or dateModified > last)    │ active IDs / Document IDs.         │
├──────────────────────────┼─────────────────────────────┼────────────────────────────────────┤
│ Scope                    │ Newly added / touched files │ Complete source inventory.         │
├──────────────────────────┼─────────────────────────────┼────────────────────────────────────┤
│ Disappeared Files        │ Does NOT detect deletions   │ Flags missing files as             │
│                          │ without OS tombstones       │ availability_status = MISSING.     │
├──────────────────────────┼─────────────────────────────┼────────────────────────────────────┤
│ Frequency                │ Frequent / Lightweight      │ Infrequent / Maintenance-only      │
└──────────────────────────┴─────────────────────────────┴────────────────────────────────────┘
```

---

## 13. Volume & SD Card Lifecycle

### Removable Storage State Machine

```
               ┌───────────────────────┐
               │    VOLUME_MOUNTED     │
               │ (Files AVAILABLE)     │
               └──────────┬────────────┘
                          │
            StorageManager broadcast unmounted /
            Ejection / USB OTG detached
                          │
                          ▼
               ┌───────────────────────┐
               │   VOLUME_UNMOUNTED    │
               │ (Files UNAVAILABLE)   │
               └──────────┬────────────┘
                          │
            StorageManager broadcast mounted /
            Volume UUID re-attached
                          │
                          ▼
               ┌───────────────────────┐
               │    VOLUME_MOUNTED     │
               │ (Files Restored)      │
               └───────────────────────┘
```

### The Ejection Invariant
When an SD card is unmounted:
1. `LocalFile.availability_status` is updated to `STORAGE_UNMOUNTED`.
2. **ZERO rows are deleted** from `mobile_tracks`, `mobile_albums`, `mobile_artists`, `mobile_playlists`, or `mobile_listening_events`.
3. In the UI, tracks on unmounted volumes display a subtle disabled/offline indicator.
4. If the user attempts playback on an unmounted track, `PlaybackPort` gracefully reports `OfflinePlaybackAvailability.UNAVAILABLE` without crashing or freezing.

---

## 14. Audio Lifecycle & Ergonomics

### `AudioFocusCoordinator` State Machine

```kotlin
enum class PlaybackFocusState {
    IDLE,
    PLAYING,
    PAUSED_BY_USER,
    PAUSED_BY_FOCUS,
    STOPPED
}
```

### State Transitions & Auto-Resume Discipline:
- **`AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK` (e.g. Navigation Prompt):**
  - If state is `PLAYING`: Temporarily duck ExoPlayer volume to `0.2f`.
  - On focus regain: Restore volume to `1.0f`.
- **`AUDIOFOCUS_LOSS_TRANSIENT` (e.g. Incoming Phone Call):**
  - If state is `PLAYING`: Pause ExoPlayer, transition to `PAUSED_BY_FOCUS`.
  - If state is `PAUSED_BY_USER`: Remain `PAUSED_BY_USER`.
  - On `AUDIOFOCUS_GAIN`:
    - If `PAUSED_BY_FOCUS`: Automatically resume playback, transition to `PLAYING`.
    - If `PAUSED_BY_USER`: **DO NOT RESUME**. User explicitly paused beforehand.
- **`AUDIOFOCUS_LOSS` (Permanent - e.g. User launches YouTube):**
  - Pause ExoPlayer, abandon audio focus, transition to `STOPPED` or `PAUSED_BY_USER`. Do not auto-resume.
- **`BecomingNoisyReceiver` (Headphone / Bluetooth Disconnect):**
  - Dynamic `BroadcastReceiver` listening for `AudioManager.ACTION_AUDIO_BECOMING_NOISY`.
  - Immediately pauses playback and transitions to `PAUSED_BY_USER`.
  - Audio device reconnection **NEVER** auto-resumes playback; requires manual user press on Play.

---

## 15. Database Impact & Schema Migration

### Room Schema Version: Migration 2 → 3

Current schema (v2) already possesses `mobile_local_files` with `storage_type` and `availability_status`. To support M4 scanning checkpoints and user storage sources, two new tables are introduced:

```sql
-- 1. Persistent Storage Sources (User-configured folders and discovered volumes)
CREATE TABLE IF NOT EXISTS `mobile_storage_sources` (
    `id` TEXT NOT NULL PRIMARY KEY,
    `type` TEXT NOT NULL,          -- 'MEDIA_STORE_PRIMARY', 'MEDIA_STORE_VOLUME', 'SAF_DOCUMENT_TREE'
    `uri` TEXT NOT NULL,
    `display_name` TEXT NOT NULL,
    `is_removable` INTEGER NOT NULL,
    `is_enabled` INTEGER NOT NULL,
    `last_scanned_at` INTEGER NOT NULL
);

-- 2. Incremental Scan Checkpoints
CREATE TABLE IF NOT EXISTS `mobile_scan_checkpoints` (
    `source_id` TEXT NOT NULL PRIMARY KEY,
    `volume_name` TEXT NOT NULL,
    `last_generation` INTEGER NOT NULL,
    `last_scan_timestamp` INTEGER NOT NULL,
    `scanned_file_count` INTEGER NOT NULL,
    `status` TEXT NOT NULL,         -- 'IDLE', 'SCANNING', 'COMPLETED', 'FAILED'
    FOREIGN KEY (`source_id`) REFERENCES `mobile_storage_sources`(`id`) ON DELETE CASCADE
);

-- 3. Track Provenance column on mobile_tracks
ALTER TABLE `mobile_tracks` ADD COLUMN `is_provisional` INTEGER NOT NULL DEFAULT 1;
```

---

## 16. Mobile / Core Boundary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         MOBILE / CORE BOUNDARY                              │
├──────────────────────────────────────┬──────────────────────────────────────┤
│ Harmonia Core (Desktop)              │ Harmonia Mobile (Android)            │
├──────────────────────────────────────┼──────────────────────────────────────┤
│ • Canonical track metadata master    │ • Factual device file scanner        │
│ • AcoustID / Chromaprint fingerprint │ • Provisional local track generator  │
│ • MusicBrainz consensus enrichment   │ • Native Media3 audio playback engine│
│ • Raw FLAC/MP3 master library archive│ • Scoped storage & volume manager    │
│ • Authoritative event aggregator     │ • Local-first playlist authoring     │
└──────────────────────────────────────┴──────────────────────────────────────┘
```

---

## 17. Future Sync Compatibility

1. **Landing Pad Ready:** M4's `InternalSyncStorage` source (`filesDir/music/`) provides the exact directory where future LocalSync will deposit files pushed from desktop.
2. **SHA-256 Bridge:** Because M4 computes SHA-256 digests on discovered files, future LocalSync will achieve $O(1)$ duplicate matching against Core's master catalog.
3. **Event Aggregation:** M4's `mobile_listening_events` logging records factual events with `playback_source = "LOCAL_STORAGE"`, ready for append-only replication to Core in M6.

---

## 18. Future AI Compatibility

1. **Unpolluted Historical Data:** M4 records factual playback without heuristic guesses, preserving clean training signals for future taste vector modeling.
2. **Real-Time Context:** Future AI will observe `LocalFile.availability_status` to ensure recommended playlists only queue tracks currently playable on device.
3. **Zero Early AI Bloat:** M4 contains zero semantic search, zero mood tagging, and zero AI dependencies.

---

## 19. Security Model

1. **Untrusted Input Invariant:** All audio file tags (ID3, Vorbis, MP4) are treated as **untrusted user input**. String length limits and Unicode sanitization are enforced before database persistence.
2. **Sandboxed Artwork Storage:** Embedded artwork is extracted into application-private storage (`context.filesDir/artwork/`). Path traversal attempts (`../../`) are impossible because filenames are derived strictly from SHA-256 byte digests.
3. **No Network Access:** `android.permission.INTERNET` remains absent. Scanned data cannot leak outside the device.

---

## 20. Performance Model

### Measurement Methodology & Invariants
- **No Blanket Speculative Claims:** Unsubstantiated claims (such as `<100ms for 10,000 tracks`) are removed. Actual performance targets will be benchmarked on reference hardware (API 33+ real device and emulator).
- **Bounded Memory Invariant:** Streaming cursors and batching in fixed chunks of 50 items (`ScanBatch`). No whole-library array materialization in memory ($O(\text{batch size})$ memory footprint rather than $O(\text{library size})$).
- **Thread Isolation:** All file I/O, `ContentResolver` queries, and Room batch transactions run strictly on `Dispatchers.IO`. Compose UI thread is never blocked.

---

## 21. Test Architecture

### Comprehensive Multi-Tier Testing Strategy
- **Unit Tests:**
  - `SourceIdentityTest`: Test injective composite key generation and volume path decoding.
  - `AudioMetadataReaderTest`: Test tag normalization, ordinal parsing, and corrupt file fallback.
  - `GenerationCheckpointTest`: Test checkpoint advancement and query generation.
  - `VolumeManagerTest`: Test mount/unmount state transitions.
  - `AudioFocusCoordinatorTest`: Verify auto-resume occurs ONLY for `PAUSED_BY_FOCUS` and never for `PAUSED_BY_USER`.
- **Instrumented Connected Tests:**
  - `MediaStoreScannerTest`: Seed real test audio files in emulator shared storage; verify ingestion into Room.
  - `SafTreeScannerTest`: Test document tree traversal and change detection.
  - `ArtworkExtractionTest`: Verify SHA-256 content-addressed artwork writing via `ArtworkProvider` and cache warming.
  - `RemovableStorageEjectionTest`: Simulate volume unmount; verify playlist records survive intact.
  - `AudioFocusIntegrationTest`: Verify ducking and pause behavior under real audio focus changes.
- **Regression Invariant:**
  - Mobile JVM: 68+ tests passing.
  - Android Connected: 44+ tests passing.
  - Core Regression: 173 tests passing.

---

## 22. M4 Subphases Specification

```
┌────────────────────────────────────────────────────────────────────────┐
│                        FINAL M4 SUBPHASE PLAN                          │
├───────────┬────────────────────────────────────────────────────────────┤
│ M4-A      │ Storage Sources, Permissions & Room Migration v2 → v3      │
│           │ • READ_MEDIA_AUDIO & SAF DocumentTree picker               │
│           │ • StorageSource domain models & SourceIdentity             │
│           │ • Room schema migration 2 → 3                              │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-B      │ Metadata Reader & Content-Addressed Artwork Engine         │
│           │ • AudioMetadataReader (MediaMetadataRetriever, nullable)   │
│           │ • Tag normalization (Unicode NFC, ordinals, dates)         │
│           │ • SHA-256 artwork extractor & ArtworkProvider integration  │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-C      │ Provider-Neutral Media Scanner & Incremental Engine        │
│           │ • MediaSourceScanner (MediaStoreScanner & SafTreeScanner)  │
│           │ • MediaStore.getGeneration() incremental checkpoint engine │
│           │ • Bounded batch Room upsert transaction & deduplication    │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-D      │ Volume Lifecycle & Disconnected Storage Protection         │
│           │ • VolumeManager & SD Card unmount broadcast receiver       │
│           │ • Ejection Invariant (availability = STORAGE_UNMOUNTED)    │
│           │ • Full Source Reconciliation engine                        │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-E      │ Audio Ergonomics, Full Integration & Forensic Release Gate │
│           │ • AudioFocusCoordinator (strict auto-resume guard)         │
│           │ • BecomingNoisyReceiver (auto-pause on disconnect)         │
│           │ • Full End-to-End User Flow (Permission → Scan → Play)     │
│           │ • Complete regression suite (68 JVM + 44 Connected + Core) │
│           │ • Hard Forensic Release Gate & Restore Point               │
└───────────┴────────────────────────────────────────────────────────────┘
```

---

## 23. Acceptance Criteria

- **M4-A Gate:** Permissions requested cleanly; Room schema migrated to v3; `StorageSource` models verified in unit tests.
- **M4-B Gate:** `AudioMetadataReader` parses MP3, FLAC, M4A, and WAV tags accurately; artwork extracted to `ArtworkProvider` without leaks or duplicate systems.
- **M4-C Gate:** Local audio files scanned and visible in Library, Search, and Playlists; incremental scans process newly added/touched files without whole-library re-enumeration.
- **M4-D Gate:** Unmounting storage marks files `STORAGE_UNMOUNTED` without dropping database records; full reconciliation cleanly flags deleted files as `MISSING`.
- **M4-E Gate:** Headphone unplug auto-pauses (requires manual play to resume); audio focus ducks/pauses (auto-resumes ONLY if actively playing beforehand); full regression suite (JVM + Connected + Core) passes with 100% success; zero production defects.

---

## 24. Deferred Features

| Feature | Deferred Milestone | Architectural Rationale |
| :--- | :--- | :--- |
| **LocalSync P2P (mDNS / QR / TLS)** | Milestone M6 | Mobile local storage foundation must be proven before network synchronization is introduced. |
| **Android Auto (`MediaLibraryService`)**| Milestone M5 | Requires async MediaBrowser paging tree; best built on top of a fully ingested local library. |
| **System Equalizer / DSP** | Milestone M5 | Peripheral audio effects follow core ingestion. |
| **Harmonia AI & Jarvis Voice** | Phase 7 | Completely separated from offline mobile player engine. |
| **Tag Write-Back / File Renaming** | REJECTED Permanently | Harmonia Mobile enforces a strict read-only media policy. |

---

## 25. Risks & Mitigations

1. **Risk:** Android permission denial breaks application flow.  
   *Mitigation:* Educational empty state explaining why audio permission is needed, with deep-link to app system settings.
2. **Risk:** Corrupt audio files crash `MediaMetadataRetriever`.  
   *Mitigation:* Isolate extractor calls in `runCatching`; fall back to filename and MediaStore columns on failure.
3. **Risk:** Slow scans on massive SD cards.  
   *Mitigation:* Bounded 50-item batches on `Dispatchers.IO`; `MediaStore.getGeneration()` checkpoints for subsequent scans.
4. **Risk:** Memory bloat from embedded high-res artwork.  
   *Mitigation:* Automatic downsampling to 1024x1024 during write into `ArtworkProvider`.

---

## 26. Open Questions & Architectural Decisions

1. **Question:** Should scanning run automatically on application startup?  
   *Decision:* Yes, execute an ultra-fast generation check (`MediaStore.getGeneration()`) at startup. If generation hasn't changed, scan terminates with zero user disruption. Provide manual "Full Rescan" button in Settings.
2. **Question:** Should Harmonia split multi-artist tags (e.g. `"Artist A feat. Artist B"`) in M4?  
   *Decision:* No, preserve composite string for M4 local ingestion to maintain clean 1:1 parity with the audio file tags. Core will perform advanced multi-artist parsing during sync.

---

## 27. Architectural Decision Records (ADRs)

### **ADR-026: Multi-Tier Identity Model & Provisional Track Reification**
- **Decision:** Distinguish Content Identity (SHA-256 + size), Location Identity (`SourceIdentity`), Runtime Locator (`contentUri` / `documentUri`), and Discovery Locator (`MediaStore._ID`). Reify provisional tracks using composite keys `local:sha256(fileHash + metadata)` rather than metadata-only slugs.
- **Reason:** Prevents collisions between distinct masterings, remasters, or live tracks that share identical tags, while allowing byte-identical duplicates to link to the same track.

### **ADR-027: Provider-Neutral Storage Architecture (`MediaSourceScanner`)**
- **Decision:** Ingest media via a provider-neutral `MediaSourceScanner` contract with concrete implementations for `MediaStoreScanner` and `SafTreeScanner`.
- **Reason:** Provides a unified pipeline for both platform MediaStore indexing and custom user SAF folder trees with persisted URI permissions.

### **ADR-028: Decoupled Incremental Change Detection & Full Reconciliation**
- **Decision:** Decouple incremental change detection via `MediaStore.getGeneration()` from full source reconciliation.
- **Reason:** Prevents unnecessary whole-library materialization on routine scans while reserving full inventory passes for manual rescans, storage remounts, and generation counter resets.

### **ADR-029: Qualified Best-Effort Metadata Extraction & M3 ArtworkProvider Integration**
- **Decision:** Extract metadata on a best-effort basis with strictly nullable fields using platform `MediaMetadataRetriever`, and route all extracted artwork directly into the existing M3 `ArtworkProvider` as `$artworkHash.jpg`.
- **Reason:** Guarantees clean-room zero-NDK compliance, avoids fabricating metadata, and maintains a single authoritative artwork cache.

### **ADR-030: Removable Storage Volume Lifecycle & Ejection Invariant**
- **Decision:** When an external SD card or USB volume is unmounted, associated `LocalFile` entities are marked `STORAGE_UNMOUNTED`, but ZERO records are deleted from playlists, tracks, or listening history.
- **Reason:** Protects user library curation and playlist integrity from accidental data destruction when removable storage is temporarily detached.

### **ADR-031: Audio Focus State Machine with Auto-Resume Guard & Becoming Noisy**
- **Decision:** Implement `AudioFocusCoordinator` with distinct states (`PLAYING`, `PAUSED_BY_USER`, `PAUSED_BY_FOCUS`, `STOPPED`). Auto-resume on focus regain occurs ONLY if state was `PAUSED_BY_FOCUS`. Becoming Noisy transitions to `PAUSED_BY_USER`.
- **Reason:** Prevents accidental or embarrassing auto-resume when the user had intentionally paused playback beforehand or unplugged headphones.

---
