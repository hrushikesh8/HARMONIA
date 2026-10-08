# Harmonia Mobile Phase M1: Native Android Foundation Implementation Report

**Document Status:** Complete  
**Date:** October 8, 2026  
**Milestone:** Phase M1 — Native Android Foundation  
**Package:** `org.harmonia.player`  
**Application ID:** `org.harmonia.player`  

---

## 1. Executive Summary

Phase M1 establishes the production-grade, native Android foundation for **Harmonia Mobile v0.1**. In strict adherence to the finalized architectural blueprint ([MOBILE_ARCHITECTURE_v0.1.md](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/MOBILE_ARCHITECTURE_v0.1.md) and [MOBILE_ARCHITECTURE_RECONCILIATION.md](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/docs/MOBILE_ARCHITECTURE_RECONCILIATION.md)), Phase M1 introduces a modern, high-performance native Kotlin/Jetpack Compose architecture without premature cross-platform compromises or unneeded runtime layers.

All core foundational objectives of Phase M1 have been successfully implemented, compiled, and verified against unit tests and build targets.

---

## 2. Toolchain & Dependency Matrix

| Component | Selected Version / Specification | Rationale |
| :--- | :--- | :--- |
| **Operating System** | Android 7.0 (API 26) through Android 16 (API 36) | Wide device coverage with modern audio hardware APIs |
| **Target / Compile SDK** | Android SDK 36 (Vanilla) | Latest platform targets, edge-to-edge support |
| **Programming Language** | Kotlin 2.3.20 | Native performance, expressive types, coroutines Flow |
| **Android Gradle Plugin** | AGP 9.0.1 | Official modern Android build pipeline |
| **Gradle** | Gradle 9.1.0 | Fast incremental builds, configuration caching |
| **JVM Toolchain** | Java 17 | Stable LTS standard for Android compilation |
| **UI Framework** | Jetpack Compose (BOM 2026.03.01) | Declarative UI, reactive state binding |
| **Persistence Engine** | Android Room 2.8.4 (SQLite 3 WAL) | Type-safe SQL, WAL concurrency, Paging 3 readiness |
| **Dependency Injection** | Manual `ServiceLocator` | Zero reflection overhead, instantaneous startup |

---

## 3. Architecture & Package Structure

The mobile project is organized into clean architectural layers located in `harmonia-mobile/app/src/main/java/org/harmonia/player/`:

```
harmonia-mobile/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/org/harmonia/player/
│   │   │   │   ├── HarmoniaApp.kt                 # Application subclass initializing ServiceLocator
│   │   │   │   ├── core/
│   │   │   │   │   └── di/
│   │   │   │   │       └── ServiceLocator.kt      # Explicit singleton DI locator
│   │   │   │   ├── domain/                        # Pure Kotlin (Zero Android framework imports)
│   │   │   │   │   ├── model/
│   │   │   │   │   │   ├── Track.kt               # Canonical track metadata
│   │   │   │   │   │   ├── Album.kt               # Album entity
│   │   │   │   │   │   ├── Artist.kt              # Artist entity
│   │   │   │   │   │   ├── LocalFile.kt           # Physical bitstream model
│   │   │   │   │   │   ├── LocalFileAvailability.kt # Availability enum (ADR-025)
│   │   │   │   │   │   ├── PlaybackState.kt       # Persistent playback state
│   │   │   │   │   │   ├── QueueItem.kt           # Persistent playback queue item
│   │   │   │   │   │   ├── Playlist.kt            # Playlist models
│   │   │   │   │   │   └── ListeningEvent.kt      # Immutable telemetry event
│   │   │   │   │   └── repository/
│   │   │   │   │       ├── TrackRepository.kt     # Domain interface
│   │   │   │   │       ├── LocalFileRepository.kt # Domain interface
│   │   │   │   │       └── PlaybackStateRepository.kt # Domain interface
│   │   │   │   ├── data/local/                    # Room SQLite Persistence Layer
│   │   │   │   │   ├── HarmoniaDatabase.kt        # Room database with SQLite WAL mode
│   │   │   │   │   ├── OfflineTrackRepository.kt  # Concrete TrackRepository
│   │   │   │   │   ├── OfflineLocalFileRepository.kt # Concrete LocalFileRepository
│   │   │   │   │   ├── OfflinePlaybackStateRepository.kt # Concrete PlaybackStateRepository
│   │   │   │   │   ├── dao/
│   │   │   │   │   │   ├── TrackDao.kt
│   │   │   │   │   │   ├── LocalFileDao.kt
│   │   │   │   │   │   ├── PlaybackStateDao.kt
│   │   │   │   │   │   ├── ArtistDao.kt
│   │   │   │   │   │   └── AlbumDao.kt
│   │   │   │   │   └── entity/
│   │   │   │   │       ├── TrackEntity.kt
│   │   │   │   │       ├── LocalFileEntity.kt
│   │   │   │   │       ├── PlaybackStateEntity.kt
│   │   │   │   │       ├── QueueItemEntity.kt
│   │   │   │   │       ├── ArtistEntity.kt
│   │   │   │   │       ├── AlbumEntity.kt
│   │   │   │   │       └── TrackWithDetails.kt    # Composite relation projection
│   │   │   │   ├── design/theme/                  # Harmonia Design System
│   │   │   │   │   ├── Color.kt                   # Obsidian, Acoustic Charcoal, Acoustic Teal
│   │   │   │   │   ├── Typography.kt              # Clean sans-serif and monospace telemetry
│   │   │   │   │   ├── Shape.kt                   # Rounded corner tokens
│   │   │   │   │   ├── HarmoniaIcons.kt           # Bespoke vector icons
│   │   │   │   │   └── Theme.kt                   # Material3 Dark Theme setup
│   │   │   │   └── presentation/                  # MVVM / MVI Presentation Layer
│   │   │   │       ├── MainActivity.kt            # Entry activity with Bottom Navigation
│   │   │   │       ├── navigation/
│   │   │   │       │   └── HarmoniaNavDestination.kt # Destinations: Home, Library, Settings
│   │   │   │       ├── home/
│   │   │   │       │   ├── HomeScreen.kt
│   │   │   │       │   └── HomeViewModel.kt
│   │   │   │       ├── library/
│   │   │   │       │   ├── LibraryScreen.kt       # Catalog with Offline Only filter & LUFS tags
│   │   │   │       │   └── LibraryViewModel.kt
│   │   │   │       └── settings/
│   │   │   │           ├── SettingsScreen.kt      # Persistence, storage usage & engine info
│   │   │   │           └── SettingsViewModel.kt
│   │   └── test/java/org/harmonia/player/
│   │       ├── domain/
│   │       │   └── TrackDomainTest.kt             # ADR-025 availability invariant tests
│   │       └── data/
│   │           ├── OfflineTrackRepositoryTest.kt  # Repository query & mapping tests
│   │           └── OfflinePlaybackStateRepositoryTest.kt # Queue & state persistence tests
```

---

## 4. Architectural Decisions Adherence

### ADR-020: Android-First Native Implementation
- **Implemented:** Pure native Kotlin with Jetpack Compose Material3, Room SQLite, and manual DI.
- **Verification:** Built natively with zero web views, zero cross-platform wrappers, and zero third-party UI runtimes.

### ADR-021: Local Persistence via Android Room in WAL Mode
- **Implemented:** `HarmoniaDatabase` configured with `JournalMode.WRITE_AHEAD_LOGGING`.
- **Foreign Keys & Indices:** Implemented foreign keys with cascading delete for queue and local files, alongside indexed queries for album, artist, and case-insensitive title search.

### ADR-023: On-Device Tag Reading Strategy
- **Implemented:** Tag reader boundary established. Domain models incorporate audio telemetry (`integratedLufs`, `truePeakDbtp`, bit depth, sample rate). Platform `android.media.MediaMetadataRetriever` is ready for Phase M2 local file ingestion without external dependencies.

### ADR-025: Two-Tier Identity Model (Decoupled Metadata vs. Local Bitstream)
- **Implemented:** `Track` represents canonical catalog metadata, while `LocalFile` represents the physical file on device storage.
- **Status Classification:** `LocalFileAvailability` tracks `AVAILABLE_VERIFIED`, `UNAVAILABLE`, `MISSING`, `UNVERIFIED`, and `CORRUPT`.
- **UI Integration:** The `LibraryScreen` includes an instant "Offline Only" toggle, filtering tracks while keeping catalog browsing intact.

---

## 5. Verification & Testing

### 5.1 Unit Test Suite
The unit test suite runs completely isolated on the JVM:
- `TrackDomainTest`: Verifies domain entity creation, availability status derivation, and offline readiness flags.
- `OfflineTrackRepositoryTest`: Tests track upserts, queries, deletions, and offline-only bitstream filtering.
- `OfflinePlaybackStateRepositoryTest`: Tests persistent playback state serialization, volume scalars, repeat modes, and active queue replacement.

**Command Executed:**
```bash
./gradlew.bat test
```
**Result:** `BUILD SUCCESSFUL` (all tests passing).

### 5.2 Build & Packaging
The Android application builds cleanly to a signed debug APK:
**Command Executed:**
```bash
./gradlew.bat assembleDebug
```
**Output Artifact:** `harmonia-mobile/app/build/outputs/apk/debug/app-debug.apk` (12.3 MB).  
**Result:** `BUILD SUCCESSFUL` with clean DEX generation and resource processing.

---

## 6. Phase M2 Roadmap Transition

Phase M1 provides the solid base required for **Phase M2 (Offline Media Import & Local Storage)**:
1. **Scoped Storage & SAF Scanner:** Integrate `DocumentFile` and Android Storage Access Framework (SAF) folder picker.
2. **Platform Tag Extraction:** Implement `MediaMetadataRetriever` scanner to ingest MP3, FLAC, M4A, and WAV metadata into Room `mobile_tracks` and `mobile_local_files`.
3. **Local Library Synchronization:** Scan local music directory, populate Room database, and emit live catalog updates.
