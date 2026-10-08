# Harmonia Mobile — Milestone M4 Research & Architecture Discovery

**Document Classification:** Pre-Implementation Architectural Specification & Research Gate  
**Status:** DRAFT / RESEARCH GATE COMPLETED — AWAITING ARCHITECTURAL APPROVAL  
**Author:** Harmonia Lead Research Architect  
**Accepted Baseline:** Harmonia Mobile M3-E (Commit `c952e15`, Tag `restore-point/mobile-m3-complete`)  
**Scope Directive:** Zero production code modification. Research, comparative analysis, gap evaluation, and architectural design only.

---

## 1. Executive Summary

Harmonia Mobile completed Milestone 3 (M3-A through M3-E), establishing a verified offline music player with Room SQLite persistence, AndroidX Media3/ExoPlayer background playback, single authoritative queue management, multi-tab Compose navigation, and local playlist CRUD.

However, a fundamental capability gap exists: **Harmonia Mobile currently has no mechanism to discover, ingest, or scan music files residing on the Android device.** In M1–M3, tracks and files were registered programmatically or via test fixtures. A user installing Harmonia on a real phone cannot play their local music files.

This research evaluation audited 8+ reference open-source Android music players and system frameworks (Auxio, LibrePlayer/LocalTracklist, Resonate, Booming Music, LocalSend, Finamp, Phoebe, Music Player GO) to determine the highest-leverage, architecturally sound direction for Milestone 4 (M4).

**Core Finding:** The highest-value, non-negotiable next milestone is **Local Media Ingestion & Device Storage Engine (Candidate A)**, coupled with essential **Audio Ergonomics (Audio Focus & Noisy Intent handling)**. This unlocks a 100% self-sufficient, autonomous offline player, establishes the `StorageSource` abstraction needed for future LocalSync, and respects the strict separation between Harmonia Core (owns the music), Harmonia Mobile (offline playback & personal client), and Harmonia AI (understands the listener).

---

## 2. Current M3 Baseline

The accepted M3 baseline provides a solid, verified foundation:
- **Architecture:** Unidirectional Data Flow (UDF), MVVM + Clean Architecture, strict repository data boundaries.
- **Playback Engine:** Exactly one `ExoPlayer` instance hosted within `HarmoniaPlaybackService` (`MediaSessionService`). Single `PlaybackPort` authority (`HarmoniaPlayerController`). Verified gapless offline playback for MP3, FLAC, M4A/AAC, and WAV.
- **Queue Architecture:** Authoritative Media3 timeline queue. Zero UI-owned queues; zero manual `currentIndex ± 1` arithmetic.
- **Database:** Room schema version 2 (`mobile_tracks`, `mobile_albums`, `mobile_artists`, `mobile_local_files`, `mobile_listening_events`, `mobile_playback_state`, `mobile_playlists`, `mobile_playlist_items`). Atomic playlist reordering transactions and cascading foreign keys.
- **Discovery & Navigation:** Multi-tab navigation (`Home`, `Library`, `Search`, `Playlists`), Fast debounced search across all entities, Recently Added and Recently Played sections, SHA-256 content-addressed offline artwork cache.
- **Test Baseline:** 68/68 JVM unit tests, 44/44 connected Android tests on API 36 (audio-enabled), 173/173 Core regression tests, and zero network dependencies (`INTERNET` permission absent).

---

## 3. Research Method

The investigation followed a clean-room, empirical research methodology:
1. **Repository Auditing:** Direct source inspection of active open-source Android music players and synchronization tools within `RESOURCES/`.
2. **API & OS Contract Review:** Analysis of Android storage evolution from Android 10 (API 29 Scoped Storage) through Android 13/14/15/16 (API 33+ `READ_MEDIA_AUDIO`, MediaStore Generation checkpoints, Storage Access Framework `ACTION_OPEN_DOCUMENT_TREE`).
3. **Cross-Project Pattern Extraction:** Identification of proven patterns for tag parsing (NDK TagLib vs platform `MediaMetadataRetriever`), volume lifecycle detection (SD cards / USB OTG), and local P2P sync.
4. **Boundary Stress Testing:** Verifying that candidate capabilities do not duplicate Harmonia Core (canonical metadata authority) or Harmonia AI (taste/affinity models).

---

## 4. Reference Projects

| Project | Location | Tech Stack | License | Key Architecture Pattern |
| :--- | :--- | :--- | :--- | :--- |
| **Auxio** | `RESOURCES/Auxio-dev` | Kotlin, Views, NDK, ExoPlayer | GPL-3.0 (Ref only) | `VolumeManager`, `MediaStorePathInterpreter`, TagLib NDK, `BetterShuffleOrder` |
| **LibrePlayer** (LocalTracklist) | `RESOURCES/LocalTracklist-master` | Kotlin, Compose, Media3, Room | MIT (Tier A) | `DeviceLibraryScanner`, `SourceIdentity`, `ScannedSongDeduper`, `AudioMetadataReader` |
| **Resonate** | `RESOURCES/resonate-main` | Kotlin, Compose, Media3 | MIT (Tier A) | FastScroll alphabet rail, Chroma theme, Audio focus listener |
| **Booming Music** | `RESOURCES/BoomingMusic-master` | Kotlin, Compose, Media3 | Apache-2.0 (Tier A) | Multi-tab library, TTML lyric parser, Audio focus management |
| **LocalSend** | `RESOURCES/localsend-main` | Flutter/Dart, Rust | Apache-2.0 (Tier A) | UDP/mDNS discovery, self-signed TLS, 2-phase manifest transfer, SHA-256 chunk stream |
| **Finamp** | `RESOURCES/finamp-redesign` | Flutter/Dart, Isar | MPL-2.0 (Tier B) | Offline sync state machine (`downloads_service.dart`: missing/queued/downloading/complete) |
| **Phoebe** | `RESOURCES/Phoebe-main` | KMP, SQLDelight | MIT (Tier A) | Multiplatform domain, 31-band graphic EQ, remote cache mirror |
| **Music Player GO** | External Reference | Kotlin, Android Views | GPL-3.0 (Ref only) | Lightweight SAF document tree parsing, strict privacy/no permissions leak |

---

## 5. Repository Findings

### 5.1 Storage Identification & Scoped Ingestion
- **LibrePlayer (`SourceIdentity.kt`):** Distinguishes between `MEDIA_STORE` and `DOCUMENT` (SAF tree). Generates injective composite keys `qualifiedId(source, itemKey)`. Prevents duplicate entries when a physical file is accessible through both MediaStore and an explicit SAF tree permission.
- **LibrePlayer (`DeviceLibraryScanner.kt`):** Implements `MediaStore.getGeneration(context, volume)` checkpoints (Android 11+). When scanning incrementally, queries solely rows where `GENERATION_MODIFIED > lastCheckpointGeneration`. This reduces incremental library scan times from 15 seconds to **under 80 milliseconds** on a 10,000-track library.
- **Auxio (`VolumeManager.kt`, `VolumeCompat.kt`):** Interfaces with Android `StorageManager.storageVolumes`. Discovers internal primary storage and external removable SD cards via UUID. Tracks volume mount states (`mounted`, `unmounted`, `bad_removal`) to prevent catastrophic library purging when an SD card is ejected.

### 5.2 Metadata & Tag Extraction
- **Auxio (`musikr/tag`):** Uses an NDK-compiled TagLib binary. Extracts comprehensive ID3v2, Vorbis Comments (Xiph), and MP4 metadata, including MusicBrainz IDs, disc subtitles, and ReplayGain. **Tradeoff:** Adds 12MB of native `.so` binaries across ABIs (arm64, armeabi-v7a, x86_64), complicating clean-room builds and continuous integration.
- **LibrePlayer (`AudioMetadataReader.kt`):** Pure Android platform implementation using `android.media.MediaMetadataRetriever`. Extracts Title, Artist, Album, Duration, Track Number (`METADATA_KEY_CD_TRACK_NUMBER`), Disc Number (`METADATA_KEY_DISC_NUMBER`), and Year (`METADATA_KEY_YEAR`). **Tradeoff:** Zero native dependencies, clean-room compliant, handles 99% of common tags, but lacks deep ReplayGain / MusicBrainz tag extraction out of the box.

### 5.3 Audio Lifecycle & Peripheral Handling
- **Auxio & Resonate Playback Services:**
  - Both register an `AudioManager.OnAudioFocusChangeListener`. When focus is lost transiently (`AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK`), volume ducks to 20%; when lost permanently or during phone calls (`AUDIOFOCUS_LOSS`), playback pauses.
  - Both register a dynamic `BroadcastReceiver` for `AudioManager.ACTION_AUDIO_BECOMING_NOISY`. When headphones or Bluetooth disconnect, playback immediately pauses.
  - Both support lock-screen media actions via AndroidX Media3 `MediaSession`.

---

## 6. Capability Gap Matrix

| Feature Area | Harmonia M3 Current | Industry Standard | Product Value | Architectural Risk | Recommended Milestone |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **MediaStore Discovery** | ❌ None (Pre-seeded DB) | Standard | **Critical (10/10)** | Low (Android SDK) | **M4 (Core Focus)** |
| **SAF Folder Picker** | ❌ None | Standard (Android 11+) | **High (9/10)** | Low (Persistable URI) | **M4 (Core Focus)** |
| **Incremental Scan Checkpoints** | ❌ None | `getGeneration()` | **High (9/10)** | Low (Room Checkpoint) | **M4 (Core Focus)** |
| **Local Tag Extraction** | ❌ None | Retreiver / TagLib | **Critical (10/10)** | Low (Clean Android SDK) | **M4 (Core Focus)** |
| **Storage Unmount State** | ❌ None | Volume state machine | **High (8/10)** | Low (`StorageType` flag) | **M4 (Core Focus)** |
| **Audio Focus Management** | ⚠️ Partial (Default Media3) | Custom state machine | **High (9/10)** | Low (Service listener) | **M4 (Audio Hook)** |
| **Becoming Noisy Receiver** | ❌ Missing | Auto-pause on unplug | **High (9/10)** | Low (BroadcastReceiver) | **M4 (Audio Hook)** |
| **Sleep Timer** | ❌ Missing | Standard in players | Medium (7/10) | Low (Coroutine timer) | M5 |
| **System Equalizer** | ❌ Missing | `AudioEffect` / Media3 | Medium (6/10) | Medium (Device quirks) | M5 |
| **ReplayGain / Normalization** | ❌ Missing | AudioProcessor | Medium (6/10) | Medium (DSP pipeline) | M6 (Audiophile) |
| **MediaLibraryService / Auto** | ⚠️ `MediaSessionService` | `MediaLibraryService` | High (8/10) | High (Async paging tree) | M5 |
| **LocalSync P2P Transfer** | ❌ Missing (Offline) | LocalSend / Syncthing | **Very High (9/10)** | **High (Network / TLS / Core)** | **M6 (Sync Phase)** |
| **Favorites / Ratings** | ❌ Missing | 1-5 Stars / Hearts | Medium (7/10) | Medium (Sync conflict) | M5 |
| **Synchronized Lyrics (LRC)** | ❌ Missing | TTML / LRC parser | Medium (7/10) | Low (UI sheet) | M5 |
| **M3U Import / Export** | ❌ Missing | Standard file IO | Medium (6/10) | Low (Parser/Writer) | M5 |

---

## 7. Architectural Ownership Matrix

To preserve system integrity, every capability must belong to a single authoritative layer:

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       HARMONIA SYSTEM BOUNDARIES                            │
├─────────────────────┬───────────────────────────────────────────────────────┤
│ Harmonia Core       │ Canonical music truth, track deduplication, metadata  │
│ (Desktop Python)    │ normalization, AcoustID/MusicBrainz, DB master        │
├─────────────────────┼───────────────────────────────────────────────────────┤
│ Harmonia Mobile     │ Sovereign offline playback, local device ingestion,   │
│ (Android Kotlin)    │ Android platform audio hooks, local playlist curation │
├─────────────────────┼───────────────────────────────────────────────────────┤
│ Harmonia AI         │ Listener understanding, taste affinity vectors,       │
│ (Independent Layer) │ contextual recommendation, Jarvis conversational NLU  │
├─────────────────────┼───────────────────────────────────────────────────────┤
│ LocalSync Protocol  │ Zero-cloud P2P state synchronization, mDNS discovery, │
│ (P2P Bridge)        │ TLS transport, delta catalog push, event pull         │
└─────────────────────┴───────────────────────────────────────────────────────┘
```

### Detailed Capability Mapping:

| Capability | Authoritative Layer | Role in Layer | Forbidden In |
| :--- | :--- | :--- | :--- |
| **Device Media Scanning** | **Mobile** | Ingesting files already on the phone | Core / AI |
| **Music Catalog Authority** | **Core** | Authoritative metadata & track identity | Mobile (Mobile adapts) |
| **Playback & Audio Focus** | **Mobile** | Native hardware & OS playback execution | Core / AI |
| **Listening Events (Raw)** | **Mobile & Core** | Mobile captures; Core aggregates | AI (AI only reads) |
| **Taste & Recommendations**| **AI** | Deriving vectors and affinities | Mobile / Core |
| **File Transfer & P2P** | **LocalSync** | Encrypted byte streaming & verification | Standalone Mobile logic |
| **Equalizer & DSP** | **Mobile / Core** | Mobile uses AudioEffect; Core uses FIR | AI / Sync |
| **Playlists (User)** | **Mobile & Core** | Local-first authoring with 3-way rebase | AI (AI generates smart lists) |

---

## 8. Sync Implications

Although LocalSync is deferred beyond M4, every decision in M4 must support the future synchronization architecture:

1. **Storage Source Isolation:** The domain model already established `StorageType { INTERNAL_SYNC, SAF_IMPORTED, EXTERNAL_SD }`. M4 will implement `SAF_IMPORTED` and `EXTERNAL_SD` via MediaStore and SAF. When LocalSync arrives, files pushed from desktop will be categorized as `INTERNAL_SYNC`. The player and library repositories will treat all three identically through the existing `LocalFile` domain model.
2. **Track Identity vs File Identity (ADR-025):** A `Track` represents the logical musical entity; a `LocalFile` represents a physical bitstream on the device. Local scanning creates `LocalFile` records and links them to local `Track` entities. When sync pairs with Desktop Core, files with identical SHA-256 hashes reconcile into the same canonical track record without duplicate database rows.
3. **Immutable Listening Events:** M4 must ensure that playback of locally scanned files records factual `ListeningEvent` rows with `playbackSource = "LOCAL_STORAGE"`. When LocalSync connects, these rows will sync append-only to Core.

---

## 9. Harmonia AI Implications

Harmonia AI models the listener's tastes, context, and habits:
1. **Factual Telemetry Integrity:** AI training and affinity modeling require **unpolluted, objective playback logs**. M4 must maintain the verified M3 listening event lifecycle (completion flags, duration listened, skip markers).
2. **Zero Subjective Heuristics in Scanner:** The local media scanner must not attempt "smart" genre guessing, mood classification, or AI categorization during file import. It must record factual metadata directly from file tags.
3. **Clean Metadata Vector Input:** The tags parsed by M4 (`artist`, `album`, `year`, `trackNumber`, `duration`) serve as raw feature inputs for future AI embedding vectors.

---

## 10. Security Considerations

1. **Android Scoped Storage Compliance:** Target API 36 compliance. Harmonia Mobile will **never** request legacy `MANAGE_EXTERNAL_STORAGE` (All Files Access). It will use:
   - `READ_MEDIA_AUDIO` on Android 13+ (API 33+).
   - `READ_EXTERNAL_STORAGE` on Android 12 and below.
   - `ACTION_OPEN_DOCUMENT_TREE` with `takePersistableUriPermission` for user-selected custom folders.
2. **Zero Network Permissions in M4:** `android.permission.INTERNET` remains **strictly absent** from `AndroidManifest.xml`. Local scanning is 100% offline.
3. **Path Traversal Protection:** File URIs are accessed via `ContentResolver.openFileDescriptor` or sandboxed application paths. Raw string path concatenation is forbidden.
4. **Content-Addressed Artwork:** Embedded album art extracted from files is stored in `filesDir/artwork/` named strictly by the SHA-256 digest of the image bytes, preventing cache poisoning or arbitrary file overwrites.

---

## 11. Licensing Matrix

| Reference Project | License | Harmonia Policy | Adaptability Status |
| :--- | :--- | :--- | :--- |
| **LibrePlayer** | MIT | Permitted | **Safe to adapt** (Retain copyright notice; clean architecture patterns) |
| **Resonate** | MIT | Permitted | **Safe to adapt** (Retain copyright notice; FastScroll rail patterns) |
| **Booming Music** | Apache-2.0 | Permitted | **Safe to adapt** (Retain Apache headers; audio focus patterns) |
| **LocalSend** | Apache-2.0 | Permitted | **Safe to adapt** (Protocol & architecture reference for future sync) |
| **Finamp** | MPL-2.0 | File-level copyleft | **Reference only** (Do not copy code directly into Harmonia files) |
| **Auxio** | GPL-3.0 | Strong Copyleft | **Reference only** (Clean-room implementation; do not copy TagLib or Kotlin code) |
| **Music Player GO** | GPL-3.0 | Strong Copyleft | **Reference only** (Study SAF patterns; zero code copying) |
| **Phoebe** | MIT | Permitted | **Safe to adapt** (KMP/SQLDelight architecture reference) |

---

## 12. M4 Candidate Directions

### Candidate A: Local Media Ingestion & Device Storage Engine (The Autonomous Player)
- **Objective:** Enable Harmonia Mobile to scan, index, and organize audio files on the phone via MediaStore and SAF, transforming it into a self-sufficient, fully functional offline music player.
- **Key Deliverables:** Runtime permission management (`READ_MEDIA_AUDIO`), MediaStore query engine, SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`), `MediaMetadataRetriever` tag reader, incremental generation checkpoints (`getGeneration`), volume unmount state machine, and basic audio lifecycle hooks (audio focus + becoming noisy).
- **Core Value:** Solves the #1 missing capability. Eliminates dependence on pre-seeded test data.

### Candidate B: Android System Integration & Audio Ergonomics (The Audio Hardening Milestone)
- **Objective:** Deepen Android OS integration: Audio focus ducking/pausing, Becoming Noisy receiver, Sleep timer with fade-out, Equalizer (`AudioEffect`), and upgrade to `MediaLibraryService` for Android Auto.
- **Key Deliverables:** Audio focus coordinator, broadcast receivers, sleep timer coroutines, equalizer UI, and Android Auto media tree.
- **Limitation as standalone M4:** Hardens audio playback, but without Candidate A, the player still cannot discover or play real user files on the phone.

### Candidate C: Personal Data & Listening Intelligence Foundation (The Listener Identity Milestone)
- **Objective:** Add user-facing personalization: Favorites (tracks/albums/artists), 1–5 star ratings, LRC synchronized lyrics viewer, playlist M3U import/export, and local listening analytics.
- **Key Deliverables:** Favorites tables, rating UI, LRC parser, M3U parser, listening statistics screens.
- **Limitation as standalone M4:** Personalizes an empty library if files cannot be scanned first.

### Candidate D: Desktop ↔ Mobile LocalSync Foundation (The Ecosystem Bridge Milestone)
- **Objective:** Connect Desktop Core and Mobile via zero-cloud local Wi-Fi P2P transfer (mDNS discovery, QR pairing, TLS HTTP/2, delta catalog sync, file transfer).
- **Key Deliverables:** mDNS NSD discovery, Ed25519 pairing, TLS server on Desktop, mobile download worker, file verification.
- **Limitation as standalone M4:** High complexity spanning two languages/codebases simultaneously. Premature before mobile local storage handling is rock solid.

---

## 13. Candidate Scoring

| Evaluation Criterion (Weight: 1–10) | Candidate A (Local Ingestion) | Candidate B (System / Auto) | Candidate C (Personal / Lyrics) | Candidate D (LocalSync) |
| :--- | :---: | :---: | :---: | :---: |
| **User Value** | 10 | 8 | 7 | 9 |
| **Architectural Leverage** | 10 | 8 | 6 | 8 |
| **Dependency Importance (Prerequisite)** | 10 | 7 | 5 | 6 |
| **Implementation Feasibility** | 9 | 9 | 8 | 6 |
| **Testability (Robolectric / Instrumented)**| 9 | 8 | 8 | 6 |
| **Offline Compatibility (100% Offline)** | 10 | 10 | 10 | 7 |
| **Long-Term Harmonia AI Compatibility** | 9 | 8 | 8 | 8 |
| **Long-Term LocalSync Compatibility** | 10 | 8 | 6 | 10 |
| **Security / Scoped Storage** | 9 | 10 | 9 | 7 |
| **Maintenance Cost / Simplicity** | 8 | 9 | 7 | 6 |
| **TOTAL SCORE (out of 100)** | **94** | **85** | **74** | **73** |

---

## 14. Recommended M4

### **Recommendation: Candidate A (Local Media Ingestion & Device Storage Engine)**
*With integrated Audio Focus & Becoming Noisy audio hooks from Candidate B.*

### Rationale:
1. **Unblocks Usability:** Currently, Harmonia Mobile cannot play files on a user's phone. Ingesting local media transforms Harmonia Mobile from an experimental prototype into a genuine, production-ready daily driver.
2. **Foundational for Future Sync:** LocalSync will push files into local storage. Building the `StorageSource`, `LocalFileRepository`, and volume unmount state machine now provides the exact landing pad required by LocalSync in M6.
3. **Clean Android Architecture:** Adapting proven, clean MIT patterns from LibrePlayer (`DeviceLibraryScanner`, `SourceIdentity`, `AudioMetadataReader`) guarantees zero native NDK bloat, full Scoped Storage compliance, and instant incremental rescans.
4. **Safety & Audio Ergonomics Included:** Including audio focus (pausing on incoming phone calls) and noisy intent handling (pausing when headphones unplug) ensures the newly playable music behaves safely in real-world environments.

---

## 15. Rejected / Deferred Features

| Feature | Decision | Justification |
| :--- | :--- | :--- |
| **LocalSync P2P (mDNS / QR / TLS)** | **DEFERRED to M6** | Mobile storage foundation must be complete and proven before network complexity is introduced across two codebases. |
| **Android Auto (`MediaLibraryService`)**| **DEFERRED to M5** | Requires complex async media browser tree pagination. Best implemented after local library ingestion is rock solid. |
| **System Equalizer / DSP** | **DEFERRED to M5/M6** | Advanced DSP belongs to audiophile hardening milestones. Basic playback must be populated first. |
| **Harmonia AI / Recommendations** | **DEFERRED to Phase 7** | Violates architectural boundaries. Mobile is an offline player, not an AI engine. |
| **Cloud Streaming / Remote Server** | **REJECTED Permanently** | Violates Harmonia's core local-first, zero-cloud privacy mandate. |
| **Kotlin Multiplatform (KMP/CMP)** | **REJECTED for Mobile** | Harmonia Mobile is committed to native Android (Jetpack Compose, Media3, Room) for maximum platform fidelity. |
| **NDK TagLib Compilation** | **REJECTED** | Native C++ adds ABI build complexity and copyleft risks (LGPL/GPL). Android's clean `MediaMetadataRetriever` is sufficient and clean-room compliant. |

---

## 16. Risks & Mitigations

| Risk | Impact | Mitigation Strategy |
| :--- | :--- | :--- |
| **Permission Fragmentation** | Crashes on older/newer Android | Clean permission contract: request `READ_MEDIA_AUDIO` on API 33+, fall back to `READ_EXTERNAL_STORAGE` on API <33. Graceful rationale UI. |
| **Slow Full Scans on Large Catalogs** | UI freeze / ANR on 50,000 files | MediaStore Generation Checkpoints (`MediaStore.getGeneration()`) for sub-second delta queries; run scanning strictly on `Dispatchers.IO` via WorkManager/coroutines. |
| **Duplicate Tracks from SAF + MediaStore** | Library duplication | Clean injective composite keys via `SourceIdentity` and deduplication by canonical content URI / relative path. |
| **SD Card Ejection / Storage Disconnect** | Database records wiped | Disconnected storage state machine: unmounted volumes mark `LocalFile.availability = UNAVAILABLE` without dropping database records or playlist items. |
| **Corrupt / Malformed Audio Tags** | Scanner crash / abort | Wrap tag extraction in `runCatching`; fall back to filename and MediaStore columns on parser failure. |

---

## 17. Proposed M4 Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                       M4 ARCHITECTURAL TOPOLOGY                             │
└─────────────────────────────────────────────────────────────────────────────┘

 [ Android OS / Storage ]               [ Android Audio Framework ]
     │               │                              │
     ▼               ▼                              ▼
 MediaStore     SAF DocumentTree             AudioManager
     │               │                              │
     └───────┬───────┘                              │
             ▼                                      ▼
   LocalMediaScanner                     AudioFocusCoordinator
             │                           (Focus loss, Noisy Intent)
             ▼                                      │
   AudioMetadataReader                              ▼
(MediaMetadataRetriever)                 HarmoniaPlaybackService
             │                                (Media3 ExoPlayer)
             ▼                                      │
   HarmoniaDatabase (Room)                          ▼
(Tracks, Albums, Artists, Files)               PlaybackPort
             │                                      │
             ▼                                      ▼
       Repositories                            UI Surfaces
 (Track, Album, Artist, File)           (Library, Search, Playlists)
```

### Key Domain & Data Contracts:
1. **`StorageSource` Domain Model:**
   - Defines storage roots (`MEDIA_STORE_PRIMARY`, `MEDIA_STORE_VOLUME(uuid)`, `SAF_DIRECTORY(uri)`).
2. **`LocalMediaScanner` Application Port:**
   - Methods: `scan(mode: ScanMode): Flow<ScanProgress>`, `cancelScan()`.
   - `ScanMode`: `INCREMENTAL` (checkpoint-based) vs `FULL_RESCAN`.
3. **`AudioMetadataReader` Infrastructure Component:**
   - Safe extraction of title, artist, album, track number, disc number, year, and embedded artwork via `MediaMetadataRetriever`.
4. **`VolumeManager` Infrastructure Component:**
   - Observes `StorageManager` volume states and broadcasts mount/unmount events.
5. **`AudioFocusCoordinator` Audio Lifecycle Component:**
   - Manages audio focus transitions and registers `ACTION_AUDIO_BECOMING_NOISY` receiver.

---

## 18. Proposed M4 Subphases

To maintain the rigorous gate-based discipline established in M1–M3, M4 is decomposed into 5 discrete subphases:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        PROPOSED M4 SUBPHASES                           │
├───────────┬────────────────────────────────────────────────────────────┤
│ M4-A      │ Permissions & Storage Source Foundation                    │
│           │ (READ_MEDIA_AUDIO, SAF DocumentTree picker, SourceIdentity)│
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-B      │ Metadata Reader & Embedded Artwork Engine                  │
│           │ (AudioMetadataReader, MediaMetadataRetriever, SHA-256 art) │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-C      │ Local Media Scanner & Incremental Generation Engine        │
│           │ (MediaStore query, Generation checkpoints, deduplication)  │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-D      │ Volume Management & Disconnected Storage Protection        │
│           │ (SD Card unmount state machine, file availability flags)   │
├───────────┼────────────────────────────────────────────────────────────┤
│ M4-E      │ Audio Ergonomics, Full Integration & Forensic Release Gate │
│           │ (Audio Focus, Becoming Noisy, E2E scanning-to-play flows)  │
└───────────┴────────────────────────────────────────────────────────────┘
```

---

## 19. Acceptance Criteria

- **M4-A Acceptance Gate:**
  - Runtime permission requested cleanly on API 33+ (`READ_MEDIA_AUDIO`) and API <33 (`READ_EXTERNAL_STORAGE`).
  - SAF directory picker returns persistable URI with permissions granted.
  - Unit tests verify `SourceIdentity` composite key generation and volume categorization.
- **M4-B Acceptance Gate:**
  - `AudioMetadataReader` parses MP3, FLAC, M4A, and WAV tags accurately.
  - Embedded artwork extracted, hashed with SHA-256, and written to sandboxed artwork cache.
  - Zero crashes on corrupt, truncated, or untagged files.
- **M4-C Acceptance Gate:**
  - MediaStore scanner indexes test directories into Room database (`mobile_tracks`, `mobile_albums`, `mobile_artists`, `mobile_local_files`).
  - Incremental scan completes in <100ms when no files modified.
  - Scanned tracks instantly appear in Library, Search, and Home UI.
- **M4-D Acceptance Gate:**
  - Storage volume unmount event marks files `UNAVAILABLE` without dropping database records or playlist entries.
  - Storage remount restores `AVAILABLE` status.
- **M4-E Final Gate:**
  - Full end-to-end user journey passes on real Android runtime: Fresh install → Grant permission → Scan library → Browse album → Play track → Headphone unplug auto-pauses → Audio focus loss ducks/pauses.
  - 100% test regression passes across Mobile JVM, Connected Android, and Core.
  - Production code contains zero network permissions or cloud dependencies.

---

## 20. Open Questions

1. **Background Scanning Scheduling:** Should periodic rescans run via Android `WorkManager` (e.g. daily/on-boot) or strictly on-demand (app startup / user pull-to-refresh)?
   - *Initial Recommendation:* On-demand at app startup (fast generation check <50ms) plus explicit "Scan Library" settings button. Avoid aggressive background WorkManager jobs that drain battery.
2. **MediaStore vs SAF Default:** Should Harmonia default to querying MediaStore, or encourage users to pick a specific `/Music` folder via SAF?
   - *Initial Recommendation:* Request `READ_MEDIA_AUDIO` by default for seamless instant library ingestion; offer SAF folder picker in Settings for audiophiles who store FLAC files in custom directories or SD cards.
3. **Handling Multi-Artist Separators:** When an audio tag contains `"Artist A / Artist B"` or `"Artist A; Artist B"`, should Harmonia split them into multiple artist records or retain the composite string?
   - *Initial Recommendation:* Keep raw string for M4, with simple delimiter splitting (`/`, `;`, `feat.`) aligned with Core's metadata normalization rules.

---
