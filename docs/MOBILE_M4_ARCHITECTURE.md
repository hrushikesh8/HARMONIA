# Harmonia Mobile — Milestone M4 Architecture Specification
**Local Media Ingestion & Device Storage Engine**

**Document Classification:** Engineering Architecture Specification & Pre-Implementation Gate  
**Status:** COMPLETE & FROZEN — AWAITING IMPLEMENTATION APPROVAL  
**Author:** Harmonia Lead Research Architect  
**Accepted Baseline:** Harmonia Mobile M3-E (Commit `c952e15`, Tag `restore-point/mobile-m3-complete`)  
**Scope Directive:** Zero production code modifications. Architectural blueprint, data contracts, identity models, storage lifecycles, and subphase definitions only.

---

## 1. Approved Direction

Following the forensic evaluation in `MOBILE_M4_RESEARCH.md`, the approved direction for Milestone 4 is:

> **M4: LOCAL MEDIA INGESTION & DEVICE STORAGE ENGINE**  
> *(incorporating essential peripheral audio ergonomics: Audio Focus & Becoming Noisy)*

### Core Objectives:
1. Transform Harmonia Mobile into a completely self-sufficient, autonomous offline player capable of indexing and playing music existing on the user's Android phone (internal storage, SD card, custom folders).
2. Establish the Android Scoped Storage and volume lifecycle architecture (`StorageSource`, `LocalFileRepository`, `VolumeManager`) that will act as the stable landing pad for future desktop-to-mobile synchronization (LocalSync).
3. Implement clean-room, zero-NDK metadata and embedded artwork extraction using Android's native platform APIs.
4. Integrate critical real-world audio lifecycle safety hooks (`AudioFocusCoordinator` and `BecomingNoisyReceiver`) so playback automatically ducks during navigation, pauses on incoming phone calls, and immediately halts when headphones or Bluetooth disconnect.
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
  - Offline SHA-256 content-addressed artwork cache.
  - Zero `INTERNET` permission in `AndroidManifest.xml`.

---

## 3. Architectural Principles

1. **Harmonia Core Owns the Music:** Core remains the canonical authority for global music truth, metadata consensus, and audio analysis. Mobile is an offline personal player and client.
2. **Mobile Does Not Duplicate Core:** Mobile does not perform heavy acoustic fingerprinting (Chromaprint/AcoustID), Web metadata scraping, or subjective tag reconciliation. Mobile records factual file properties and provisional tags.
3. **Harmonia AI Understands the Person:** AI modeling runs independently of Mobile and Core. Mobile provides factual, unpolluted telemetry (`ListeningEvent` records); AI derives recommendations.
4. **Offline-First & Sovereign:** Ingestion operates 100% locally on the device without internet access, accounts, telemetry SDKs, or cloud dependencies.
5. **Read-Only Media Policy:** Harmonia Mobile NEVER modifies, overwrites, renames, or writes tags back to physical audio files on disk. The user's files are sacred.
6. **Ejection Invariant:** Ejecting an SD card or revoking folder permissions marks files `UNAVAILABLE`, but NEVER deletes playlists, listening history, or track records.

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
Mobile extracts factual technical properties (format, duration, bitrate, sample rate) and embedded tags (title, artist, album, track number, year) using `MediaMetadataRetriever`. It computes a content digest (file size + header/sample hash or SHA-256), writes a `LocalFile` entity to Room SQLite (`mobile_local_files`), and creates or links to a **Provisional local `Track`** (`mobile_tracks`).

#### 2. Does Mobile create a local Track?
**YES.** Mobile must create a local `Track` entity immediately. If Mobile only created a raw file record without a `Track`, the entire M3 presentation layer (Library, Search, Playlists, Album detail, Artist detail, Now Playing) would be unable to display or navigate the music without a connection to Desktop Core.

#### 3. Is that Track authoritative or provisional?
**PROVISIONAL.** The track's provenance is explicitly flagged as `PROVISIONAL_LOCAL`. It represents a local working model of the musical work. It is authoritative for on-device playback, but provisional with respect to the global Harmonia catalog.

#### 4. How is Track identity determined locally?
Locally on Mobile, Track identity is generated deterministically:
- If an embedded MusicBrainz Recording ID (`mbid`) exists, `track.id = mbid`.
- Otherwise, a deterministic UUIDv5 is generated from a normalized slug:  
  `UUID.nameUUIDFromBytes("harmonia:track:${normalizedArtist}:${normalizedAlbum}:${normalizedTitle}:${trackNo}:${discNo}".toByteArray())`.  
  This ensures deterministic deduplication: rescanning the same file or discovering identical tracks across volumes maps to the identical local `Track.id`.

#### 5. Relationship between Track, TrackFile, LocalFile, MediaStore ID, URI, and SHA-256:
- **`Track` (Domain/Room):** Abstract musical recording. Holds presentation metadata.
- **`TrackFile` (Core Entity):** Desktop Core's bitstream model. Not present in Mobile.
- **`LocalFile` (Mobile Entity):** Android device's physical bitstream model. Foreign-keyed to `Track.id`.
- **`MediaStore ID` (OS Handle):** Integer primary key in Android `MediaStore.Audio.Media._ID`. Volatile across OS scans; used solely as an ephemeral query cursor, NEVER as a foreign key.
- **`URI` (Platform Locator):** Content URI (`content://media/external/audio/media/1234`) or Document URI (`content://.../document/...`). Used by Media3 ExoPlayer to open audio streams. Stored as `mobile_local_files.file_uri` (unique index).
- **`SHA-256` (Universal Content Digest):** Cryptographic digest of the audio bitstream bytes. Persisted in `mobile_local_files.file_hash`. Universally maps Mobile's `LocalFile` to Core's `TrackFile` during sync.

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

### Three-Tier Identity Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    THREE-TIER ANDROID IDENTITY MODEL                        │
├─────────────────┬───────────────────────────────────────────────────────────┤
│ Tier 1:         │ SHA-256 Digest (Audio Bitstream Hash)                     │
│ Content Identity│ • Universal, permanent, platform-agnostic                 │
│                 │ • Survives renames, moves, SD card transfers, OS re-index │
├─────────────────┼───────────────────────────────────────────────────────────┤
│ Tier 2:         │ SourceIdentity Key                                        │
│ Location Identity│ • Format: "sourceType:volumeIdentifier:relativePath"     │
│                 │ • Stable across app restarts; detects file renames/moves  │
├─────────────────┼───────────────────────────────────────────────────────────┤
│ Tier 3:         │ Platform URI Handle                                       │
│ Runtime Locator │ • content:// or document:// URI                           │
│                 │ • Ephemeral; passed to ExoPlayer for byte-stream access   │
└─────────────────┴───────────────────────────────────────────────────────────┘
```

### Edge Case Invariants

| Scenario | Behavior & Resolution |
| :--- | :--- |
| **File Renamed in Same Folder** | Same `fileHash` + same volume + same file size. `SourceIdentity` detects name change; `LocalFile.file_uri` updated; track identity unchanged. |
| **File Moved to Another Folder** | Same `fileHash`. Scanner matches existing `fileHash`; updates `file_uri` and location without creating duplicate `Track` or breaking playlist links. |
| **SD Card Ejected** | Volume UUID absent. `LocalFile.availability_status` transitions to `STORAGE_UNMOUNTED`. Records preserved. |
| **SD Card Reinserted** | Volume UUID returns. `LocalFile.availability_status` transitions back to `AVAILABLE`. No re-ingestion needed. |
| **MediaStore Cleared / OS Re-indexed**| MediaStore integer `_ID` changes. `fileHash` matches existing `LocalFile`; `file_uri` updated smoothly without duplicating library. |
| **Identical Audio Copied to 2 Folders**| Same `fileHash`, different URIs. Ingested as secondary `LocalFile` pointing to the **same** underlying `Track.id`. |
| **File Edited in Place (Tags Modified)**| `fileHash` and `dateModified` change. Scanner reads new tags, updates provisional metadata; track identity preserved. |

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

### Storage Source Authority & Access Matrix

| Storage Source | Authority | Permission Required | Scan Mechanism | Byte Stream Access |
| :--- | :--- | :--- | :--- | :--- |
| **`MediaStorePrimary`** | Android OS | `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (API <33) | `ContentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)` | `ContentResolver.openFileDescriptor(uri, "r")` |
| **`MediaStoreVolume`** | StorageManager | Same as primary | `MediaStore.Audio.Media.getContentUri(volumeName)` | `ContentResolver.openFileDescriptor(uri, "r")` |
| **`SafDocumentTree`** | DocumentProvider | `takePersistableUriPermission` on `ACTION_OPEN_DOCUMENT_TREE` | `DocumentsContract.buildChildDocumentsUriUsingTree` | `ContentResolver.openInputStream(docUri)` |
| **`InternalSyncStorage`** | Harmonia App | None (App-scoped sandboxed storage) | Filesystem `java.io.File` traversal | Direct `FileInputStream` |

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

### Clean-Room `AudioMetadataReader` Pipeline

Metadata extraction is executed exclusively via Android's native `android.media.MediaMetadataRetriever` (clean-room, zero native JNI/NDK dependencies, zero GPL copyleft).

```kotlin
data class RawExtractedMetadata(
    val title: String?,
    val artist: String?,
    val album: String?,
    val albumArtist: String?,
    val genre: String?,
    val trackNumberRaw: String?,
    val discNumberRaw: String?,
    val yearRaw: String?,
    val durationMs: Long?,
    val bitrateKbps: Int?,
    val mimeType: String?,
    val embeddedArtwork: ByteArray?
)

data class NormalizedMetadata(
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int?,
    val discNumber: Int,
    val year: Int?,
    val genre: String?,
    val durationSec: Double,
    val bitrate: Int?,
    val artworkHash: String?
)
```

### Normalization Rules (ADR-004 Aligned)
1. **Unicode NFC Normalization:** All text strings are normalized via `Normalizer.normalize(str, Normalizer.Form.NFC)`.
2. **Ordinal Parsing:** Track and disc numbers parsed from compound strings (`"4/12"` → track `4`).
3. **Year Extraction:** ISO dates parsed (`"2022-05-17"` → year `2022`).
4. **Fallback Fallthrough:** If title is missing or blank, fall back to filename without extension. If artist is blank, fall back to `"Unknown Artist"`. If album is blank, fall back to `"Unknown Album"`.

---

## 10. Artwork Architecture

### Content-Addressed Artwork Invariants
1. **SHA-256 Authority:** Embedded artwork bytes extracted via `retriever.embeddedPicture` are digested with SHA-256.
2. **Deduplication:** Multiple tracks from the same album sharing identical image bytes yield identical SHA-256 hashes. Exactly ONE image file is written to disk at `filesDir/artwork/{sha256}.bin`.
3. **Cache Lookup:** If `File(context.filesDir, "artwork/$hash.bin").exists()`, disk write is skipped entirely.
4. **Dimension & Memory Clamping:** Images exceeding 1024x1024 are downsampled during file write using `BitmapFactory.Options.inSampleSize` to prevent Out-Of-Memory (OOM) errors during notification or UI rendering.
5. **Deterministic Fallback:** Missing or corrupt artwork emits `null` hash, seamlessly handled by M3's `ArtworkProvider` placeholder renderer.

---

## 11. Scanner Architecture

### Deterministic Scanning Pipeline

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       LOCAL MEDIA SCANNER PIPELINE                          │
└─────────────────────────────────────────────────────────────────────────────┘

 [ Storage Source Discovery ]  Query MediaStore / traverse SAF DocumentTree
             │
             ▼
   [ Batch Partitioning ]      Partition URIs into chunks of 50 items
             │
             ▼
    [ Identity Check ]         Compare (SourceIdentity, dateModified, size)
             │                 against existing Room records
             ▼
  ┌───────────────────────┐
  │ Was item modified?    │
  │ • NO  ─► Skip extract │
  │ • YES ─► Proceed      │
  └───────────────────────┘
             │
             ▼
   [ Metadata & Artwork ]      Extract via MediaMetadataRetriever on Dispatchers.IO
             │                 Compute SHA-256 hash & save artwork to cache
             ▼
   [ Room Batch Upsert ]       Execute atomic transaction inserting/updating:
             │                 • mobile_tracks (Provisional)
             │                 • mobile_albums
             │                 • mobile_artists
             │                 • mobile_local_files (AVAILABLE)
             ▼
  [ Advance Checkpoint ]       Update last generation / scan timestamp
             │
             ▼
    [ Emit Progress ]          Flow<ScanProgress> (scanned, total, currentTitle)
```

### Scanner Error & Failure Modes
- **Corrupt Audio File:** `MediaMetadataRetriever` throws `RuntimeException`. Caught in `runCatching`; logs warning; falls back to MediaStore cursor columns; scanner continues without crashing.
- **Inaccessible File (Permission Revoked mid-scan):** `SecurityException` caught; marks file `UNAVAILABLE`; scanner continues.
- **Coroutine Cancellation:** User leaves screen or triggers cancel. Scanner cooperatively checks `coroutineContext.isActive` between batches, commits processed batches, and halts cleanly.

---

## 12. Incremental Scanning Model

### Generation Checkpoint Engine (`MediaStore.getGeneration()`)
On Android 11+ (API 30+), MediaStore maintains a monotonically increasing `generation` counter per volume.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    GENERATION CHECKPOINT ENGINE                             │
├─────────────────────────────────────────────────────────────────────────────┤
│ 1. Read last committed generation from `mobile_scan_checkpoints`.           │
│ 2. Query MediaStore with: `GENERATION_MODIFIED > lastGeneration`.           │
│ 3. If count == 0: Incremental scan finishes in < 50ms without disk I/O.     │
│ 4. If count > 0: Process ONLY modified/added rows.                          │
│ 5. Advance checkpoint: Persist `newGeneration = MediaStore.getGeneration()`.│
│ 6. Fallback (API < 30): Compare `DATE_MODIFIED > lastScanTimestamp`.        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Deleted File Reconciliation
To detect files deleted outside Harmonia:
1. Fast reconciliation query: select all `_ID` values from MediaStore.
2. Compare with existing `mobile_local_files` for that volume.
3. Missing rows are flagged `availability_status = MISSING`. Records are preserved; playlists remain intact.

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

### `AudioFocusCoordinator` Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        AUDIO FOCUS STATE MACHINE                            │
└─────────────────────────────────────────────────────────────────────────────┘

 [ AudioFocusRequest ]
         │
         ├──► AUDIOFOCUS_GAIN
         │    • Full playback volume (1.0f)
         │
         ├──► AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK (e.g. Navigation prompt)
         │    • Temporarily reduce ExoPlayer volume to 0.2f
         │    • Restore volume to 1.0f upon regain
         │
         ├──► AUDIOFOCUS_LOSS_TRANSIENT (e.g. Incoming phone call / Siri/Assistant)
         │    • Pause ExoPlayer
         │    • Automatically resume playback when call ends
         │
         └──► AUDIOFOCUS_LOSS (Permanent - e.g. User opened Spotify / YouTube)
              • Pause ExoPlayer
              • Abandon audio focus
              • Do NOT auto-resume
```

### `BecomingNoisyReceiver` Architecture
- Registers a dynamic `BroadcastReceiver` in `HarmoniaPlaybackService` listening for `AudioManager.ACTION_AUDIO_BECOMING_NOISY`.
- Triggers when wired headphones are unplugged or Bluetooth audio device disconnects.
- Action: Dispatches `pause()` to `PlaybackPort`. Protects the user from embarrassing audio blasts in public.

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

### Harmonious Alignment with LocalSync
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

### Targets & Measurement Methodology
- **Incremental Rescan:** Target <100ms for 10,000 tracks via MediaStore Generation checkpoints.
- **Initial Scan Throughput:** Target >100 files/sec on modern Android devices with hardware storage encryption.
- **Memory Overhead:** Constant $O(1)$ memory footprint using 50-item batch chunking on `Dispatchers.IO`.
- **UI Responsiveness:** 60/120fps Compose UI maintained; zero disk or ContentResolver queries on the Main thread.

---

## 21. Test Architecture

### Comprehensive Multi-Tier Testing Strategy
- **Unit Tests:**
  - `SourceIdentityTest`: Test injective composite key generation and volume path decoding.
  - `AudioMetadataReaderTest`: Test tag normalization, ordinal parsing, and corrupt file fallback.
  - `GenerationCheckpointTest`: Test checkpoint advancement and query generation.
  - `VolumeManagerTest`: Test mount/unmount state transitions.
- **Instrumented Connected Tests:**
  - `MediaStoreScannerTest`: Seed real test audio files in emulator shared storage; verify ingestion into Room.
  - `ArtworkExtractionTest`: Verify SHA-256 content-addressed artwork writing and deduplication.
  - `RemovableStorageEjectionTest`: Simulate volume unmount; verify playlist records survive intact.
  - `AudioFocusTest`: Verify ducking and pause behavior under audio focus changes.
- **Regression Invariant:**
  - Mobile JVM: 68+ tests passing.
  - Android Connected: 44+ tests passing.
  - Core Regression: 173 tests passing.

---

## 22. M4 Subphases Specification

### Subphase Breakdown & Gates

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
│           │ • AudioMetadataReader (MediaMetadataRetriever)             │
│           │ • Tag normalization (Unicode NFC, ordinals, dates)         │
│           │ • SHA-256 artwork extractor & cache integration            │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-C      │ Local Media Scanner & Incremental Generation Engine        │
│           │ • MediaStoreAudioScanner & SAF tree crawler                │
│           │ • MediaStore.getGeneration() incremental checkpoint engine │
│           │ • Batch Room upsert transaction & duplicate deduplication  │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-D      │ Volume Lifecycle & Disconnected Storage Protection         │
│           │ • VolumeManager & SD Card unmount broadcast receiver       │
│           │ • Ejection Invariant (availability = STORAGE_UNMOUNTED)    │
│           │ • Remount restoration & missing file reconciliation        │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-E      │ Audio Ergonomics, Full Integration & Forensic Release Gate │
│           │ • AudioFocusCoordinator & BecomingNoisyReceiver            │
│           │ • Full End-to-End User Flow (Permission → Scan → Play)     │
│           │ • Complete regression suite (68 JVM + 44 Connected + Core) │
│           │ • Hard Forensic Release Gate & Restore Point               │
└───────────┴────────────────────────────────────────────────────────────┘
```

---

## 23. Acceptance Criteria

- **M4-A Gate:** Permissions requested cleanly; Room schema migrated to v3; `StorageSource` models verified in unit tests.
- **M4-B Gate:** `AudioMetadataReader` parses MP3, FLAC, M4A, and WAV tags accurately; artwork extracted to SHA-256 cache without leaks.
- **M4-C Gate:** Local audio files scanned and visible in Library, Search, and Playlists; incremental scans complete sub-second via generation checkpoints.
- **M4-D Gate:** Unmounting storage marks files `UNAVAILABLE` without dropping database records; remounting restores availability.
- **M4-E Gate:** Headphone unplug auto-pauses; audio focus ducks/pauses; full regression suite (JVM + Connected + Core) passes with 100% success; zero production defects.

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
3. **Risk:** Slow scans on massive 100,000-track SD cards.  
   *Mitigation:* Chunked 50-item batches on `Dispatchers.IO`; `MediaStore.getGeneration()` checkpoints for all subsequent scans.
4. **Risk:** Memory bloat from embedded high-res artwork.  
   *Mitigation:* Automatic downsampling to 1024x1024 during file write.

---

## 26. Open Questions & Architectural Decisions

1. **Question:** Should scanning run automatically on application startup?  
   *Decision:* Yes, execute an ultra-fast generation check (`MediaStore.getGeneration()`) at startup. If generation hasn't changed, scan terminates in <50ms with zero user disruption. Provide manual "Rescan Library" button in Settings.
2. **Question:** Should Harmonia split multi-artist tags (e.g. `"Artist A feat. Artist B"`) in M4?  
   *Decision:* No, preserve composite string for M4 local ingestion to maintain clean 1:1 parity with the audio file tags. Core will perform advanced multi-artist parsing during sync.

---

## 27. Architectural Decision Records (ADRs)

The following ADRs are established for Milestone M4, numbered sequentially following existing records ADR-001 through ADR-025:

### **ADR-026: Android Media Ingestion Identity Model & Provisional Track Reification**
- **Decision:** When an unindexed audio file is discovered on Android storage, Mobile generates a content-addressed `LocalFile` and creates a provisional `Track` identified by a deterministic UUIDv5 derived from normalized metadata slugs.
- **Reason:** Enables instant, fully functional offline library browsing and playback without requiring a connection to Desktop Core, while maintaining a clean reconciliation path for future sync.

### **ADR-027: Scoped Storage Dual-Provider Architecture (`MEDIA_STORE` + `SAF_DOCUMENT_TREE`)**
- **Decision:** Ingest media via Android's `MediaStore` by default, while supporting user-selected custom directories via Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) with persisted URI permissions.
- **Reason:** Complies with Android 13–16 Scoped Storage guidelines without requesting invasive `MANAGE_EXTERNAL_STORAGE` permissions, while supporting audiophiles with custom SD card directory structures.

### **ADR-028: Incremental MediaStore Discovery via Generation Checkpoints (`getGeneration`)**
- **Decision:** Persist the latest `MediaStore.getGeneration()` value in Room SQLite, querying exclusively rows with `GENERATION_MODIFIED > lastGeneration` during incremental scans.
- **Reason:** Eliminates expensive full-library file-by-file rescanning, reducing incremental scan durations from tens of seconds to milliseconds.

### **ADR-029: Zero-NDK Clean-Room Metadata Extraction via `MediaMetadataRetriever`**
- **Decision:** Extract audio metadata and embedded artwork exclusively using Android platform `MediaMetadataRetriever`, rejecting NDK-compiled TagLib binaries.
- **Reason:** Guarantees clean-room compliance, eliminates 12MB of native C++ `.so` binaries, and avoids GPL/LGPL copyleft licensing contagion.

### **ADR-030: Removable Storage Volume Lifecycle & Ejection Invariant**
- **Decision:** When an external SD card or USB volume is unmounted, associated `LocalFile` entities are marked `STORAGE_UNMOUNTED`, but ZERO records are deleted from playlists, tracks, or listening history.
- **Reason:** Protects user library curation and playlist integrity from accidental data destruction when removable storage is temporarily detached.

### **ADR-031: Peripheral Audio Ergonomics & Audio Focus State Machine**
- **Decision:** Integrate `AudioFocusCoordinator` (managing transient loss, ducking, and permanent loss) and `BecomingNoisyReceiver` (auto-pausing on headphone unplug or Bluetooth disconnect) directly into `HarmoniaPlaybackService`.
- **Reason:** Enforces standard mobile audio etiquette, preventing disruptive audio playback in public environments and during phone calls.

---
