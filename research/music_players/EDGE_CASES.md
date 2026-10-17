# Harmonia Research: Edge Cases & Failure Recovery Strategies

**Document Classification:** Architectural Research Specification  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Exhaustive catalog of real-world edge cases, container quirks, filesystem failures, and recovery strategies discovered across 20 media projects.

---

## 1. Summary of Discovered Edge Cases & Solutions

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                             EDGE CASE FAILURE TAXONOMY                                      │
├────────────────────┬───────────────────────────────────────┬────────────────────────────────┤
│ Category           │ Real-World Failure Mode               │ Proven Defense Mechanism       │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 1. Storage Disconn.│ SD card or USB drive ejected while    │ Strawberry `unavailable=1`     │
│                    │ app is open; tracks vanish.           │ soft-delete; do NOT delete row.│
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 2. File Moved/Ren. │ User reorganizes folder structure;    │ fooyin `TrackHash` key;        │
│                    │ play history & ratings wiped.         │ re-link file by SHA-256 hash.  │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 3. Broken Decoders │ Android vendor SoC hardware ALAC      │ Booming `AlacWorkaroundCodec-  │
│                    │ decoder corrupts high-bitrate M4A.    │ Selector` forces SW decoding.  │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 4. Metadata Bombs  │ ID3 tag contains 50MB embedded JPEG   │ Mutagen header slice; cap tag  │
│                    │ or malformed UTF-16 surrogate pairs.  │ reading at 1MB; extract image. │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 5. Crash in Scan   │ Phone reboot / crash mid-scan leaves  │ Resonate `LibraryReplacer.kt`  │
│                    │ library half-empty or blank.          │ atomic staging table swap.     │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 6. Non-Latin Names │ Japanese Kanji or Chinese Pinyin      │ Wavio `pinyinIndex.ts` &       │
│                    │ artist cannot be found in English ABC.│ `kanaSearchFallback.ts`.       │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 7. Audio Loudness  │ High positive ReplayGain clips on     │ 32-bit float pipeline +        │
│                    │ 16-bit PCM integer buffers.           │ lookahead True Peak limiter.   │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 8. Gapless Failure │ MP3/AAC encoder delay & padding       │ Media3 iTunSMPB & LAME header  │
│                    │ causes audible click/gap at track end.│ trimming; pre-buffer decoder.  │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 9. Path Traversal  │ Symlink pointing to /etc or C:\Windows│ Harmonia Phase 3 library root  │
│                    │ exposes system files via API.         │ boundary check (Path.resolve). │
├────────────────────┼───────────────────────────────────────┼────────────────────────────────┤
│ 10. Memory Bloat   │ 100,000 tracks held in RAM causes     │ Paged Room/SQLite cursors;     │
│                    │ mobile OOM crash on startup.          │ avoid Auxio in-memory graph.   │
└────────────────────┴───────────────────────────────────────┴────────────────────────────────┘
```

---

## 2. Deep Dive: Edge Cases & Defense Strategies

### 2.1 Storage Disconnection & Removable Media
- **Failure:** On Android, users frequently store lossless music on microSD cards or OTG USB flash drives. If the user unplugs the drive while the app is running or scans the library while the drive is unmounted:
  - Naive players see the files are missing and execute `DELETE FROM songs WHERE path ...`, wiping all playlists, ratings, and play history!
- **Proven Solution ([Strawberry](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/strawberry-master/data/schema/schema-13.sql#L45)):**
  - Track files have an `is_available` boolean (or `unavailable INTEGER DEFAULT 0`).
  - During incremental scanning, if a root path or volume UUID is absent, all its tracks are marked `is_available = false`.
  - In the UI, unavailable tracks appear slightly dimmed or with a "disconnected" badge. Playlists retain the tracks.
  - When the card is reinserted, `is_available` flips back to `true` instantly without losing any user state.

---

### 2.2 Broken Vendor Hardware Decoders
- **Failure ([Booming Music](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/BoomingMusic-master/app/src/main/java/com/mardous/booming/playback/renderer/AlacWorkaroundCodecSelector.kt)) `[SOURCE VERIFIED]`:**
  - Android device manufacturers (especially certain MediaTek and Qualcomm chipsets) include hardware-accelerated MediaCodec decoders for Apple Lossless (`audio/alac`).
  - Many of these vendor implementations contain bugs when parsing high-bitrate M4A files (>1000 kbps), resulting in stuttering, extreme static, or native process crashes.
- **Proven Solution:**
  - Implement a custom `MediaCodecSelector` in ExoPlayer.
  - When `mimeType == MimeTypes.AUDIO_ALAC`, filter out any codec marked `hardwareAccelerated = true`.
  - Force software decoding (via Media3 built-in decoder or FFmpeg extension).

---

### 2.3 Metadata Bombs & Corrupted Audio Headers
- **Failure:**
  - **Embedded Artwork Bombs:** Some audiophile SACD rips contain uncompressed 50MB TIFF or 24-megapixel PNG files embedded inside the ID3 `APIC` frame. Calling `mutagen.File()` or Android's `MediaMetadataRetriever` on hundreds of such files exhausts JVM/V8 heap memory within seconds.
  - **Truncated Frames:** Downloads interrupted midway produce zero-byte or half-written audio files that cause decoders to hang or enter infinite loops.
  - **Invalid Character Encodings:** ID3v2.3 tags written with non-standard ISO-8859-1 containing raw UTF-8 or Windows-1252 strings produce mojibake (e.g. `Ã©` instead of `é`).
- **Proven Solution:**
  - **Header Streaming Limit:** Read only the first 256KB of the file to determine container and audio format headers.
  - **Subprocess Isolation:** In Harmonia Core Phase 3, FFmpeg analysis runs in a strictly isolated subprocess with timeout kill groups (`timeout_sec=30`). A corrupted audio stream that crashes or hangs FFmpeg cannot crash the main Harmonia process or lock the SQLite database.
  - **Fallback Encoding Ladder:** When decoding tag byte arrays: try UTF-8 $\to$ try UTF-16 with BOM $\to$ try Windows-1252 / ISO-8859-1 $\to$ fall back to raw ASCII with replacement characters.

---

### 2.4 Non-Latin & Multilingual Search
- **Failure ([Wavio](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/wavio-main/apps/mobile/services/pinyinIndex.ts) & [beets](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/beets-master/beets/autotag/distance.py)):**
  - A user with Japanese anime tracks or Chinese classical music searches for `"Joe Hisaishi"` or `"Hayao Miyazaki"`, but the tags are stored in Kanji (`久石 譲`).
  - Standard substring match or SQLite FTS5 returns 0 results.
- **Proven Solution:**
  - **Dual Search Index:** Store both the original Unicode string (in NFC normalized form) and a transliterated ASCII/Romaji/Pinyin sort key.
  - Wavio generates phonetic indexes on library import. Search queries match against both representations simultaneously.

---

### 2.5 Extremely Long Paths & Windows MAX_PATH (260 Characters)
- **Failure:**
  - On Windows, paths exceeding 260 characters (`MAX_PATH`) fail with `FileNotFoundException` or `PathTooLongException` when using standard Win32 APIs (common with classical music: `C:\Music\Ludwig van Beethoven\Symphony No. 9 in D minor, Op. 125 "Choral"\04 - Presto - Allegro assai - Choral finale on Schiller's Ode to Joy.flac`).
- **Proven Solution:**
  - In Harmonia Core (Python), always use `os.fspath()` and prepend `\\?\` prefix on Windows paths if `len(path) > 240`.
  - In the REST API, completely redact physical paths and identify all files solely via UUID (`/api/v1/stream/{file_id}`).

---

### 2.6 Audio Focus & Headphone Disconnection ("Becoming Noisy")
- **Failure:**
  - User is listening to loud music on headphones. The headphone cable is accidentally yanked out or Bluetooth headphones run out of battery. The phone switches to internal speakers, blasting music at maximum volume in an office or library.
- **Proven Solution ([Media3 Standards](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/LocalTracklist-master/app/src/main/java/com/libreplayer/media/service/PlaybackService.kt)):**
  - Register an `AudioBecomingNoisyReceiver` listening for `android.media.AUDIO_BECOMING_NOISY`.
  - On receipt, immediately issue `player.pause()`.

---

### 2.7 Atomic Library Replacement & Process Crash
- **Failure:**
  - Full library rescan wipes existing tracks table. If app crashes or device battery dies before scan completes, the library is left in a corrupted or empty state.
- **Proven Solution ([Resonate `LibraryReplacer.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/resonate-main/app/src/main/kotlin/com/resonate/player/data/db/LibraryReplacer.kt)):**
  - Execute full scan into a temporary staging table (`tracks_staging`).
  - Perform an atomic SQLite table swap inside a single database transaction.
  - If a crash occurs at any point before commit, SQLite rolls back to the previous intact library automatically.
