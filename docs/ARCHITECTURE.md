# Harmonia Architecture

## Overview

**Harmonia** is the deterministic music infrastructure and personal music library system. It is designed to be:
- **Local-first & offline**: Operates completely without network access or cloud dependencies.
- **Deterministic**: Pure logic with no probabilistic or AI components.
- **Modular**: Adheres strictly to pragmatic Clean Architecture principles.
- **Auditable & Provenance-aware**: Separates physical audio files (`TrackFile`) from logical recordings (`Track`), and tracks metadata sources.
- **Objective Audio Intelligence**: Measures technical container and psychoacoustic signal properties (ITU-R BS.1770-4 LUFS, True Peak, dynamic range) without subjective scoring.
- **Local HTTP API & Secure Streaming**: Exposes factual REST endpoints and HTTP 206 Range streaming with strict host filesystem path concealment.

---

## Clean Architecture Layers

```
┌────────────────────────────────────────────────────────┐
│                      Presentation                      │
│                  (Typer CLI, HTTP REST API)            │
├────────────────────────────────────────────────────────┤
│                      Application                       │
│      (ImportService, LibraryService, AnalysisService,  │
│       StreamingService, SearchService, DTOs)           │
├────────────────────────────────────────────────────────┤
│                        Domain                          │
│     (Entities, Value Objects, Ports, Quality Scorer)   │
├────────────────────────────────────────────────────────┤
│                    Infrastructure                      │
│      (SQLite Repositories, FFmpeg Analyzer, Mutagen)   │
└────────────────────────────────────────────────────────┘
```

### 1. Domain Layer (`harmonia.domain`)
- **Entities**:
  - `Track`: Logical music recording with normalized metadata.
  - `TrackFile`: Physical audio file on disk, codec parameters, technical properties (`channel_layout`, `encoder`).
  - `AudioAnalysis`: Derived signal measurements associated with a `TrackFile` (`integrated_lufs`, `true_peak_dbtp`, `loudness_range_lu`, `peak_to_loudness_db`, clipping detection, silence boundaries).
  - `ListeningEvent`: Immutable factual playback telemetry event with dual pointers (`track_id`, `track_file_id`), duration listened, completion status, and nullable `device_id`.
  - `QueueItem`, `PlaybackState`, `QueueSnapshot`: Deterministic playback state and persistent queue snapshot models.
  - `Album`: Collection of tracks with release metadata and cover art references.
  - `Artist`: Creator/performer with canonical and sort names.
  - `Tag`: Key-value tags classified by origin (`file`, `user`, `provider`).
  - `DuplicateCandidate`: Probable-duplicate candidate pairs for human review.
- **Values**:
  - `AudioFormat`: Enum of recognized file formats and lossless heuristics.
  - `AnalysisStatus`: Enum lifecycle states (`PENDING`, `RUNNING`, `COMPLETED`, `FAILED`).
  - `PlaybackStatus`: Enum playback states (`STOPPED`, `PLAYING`, `PAUSED`).
  - `RepeatMode`: Enum repeat modes (`OFF`, `ALL`, `ONE`).
  - `PlaybackSource`: Polymorphic audio source (`LocalFileSource`, `StreamUriSource`, `PlatformUriSource`).
- **Ports (Interfaces)**:
  - `PlaybackPort`: Protocol defining transport controls, queue operations, and state inspection.
  - `ListeningEventRepository`: Protocol for immutable listening telemetry persistence and querying.
  - `PlaybackSnapshotRepository`: Protocol for active playback session snapshot persistence and restart restoration.
  - `TrackRepository`: Protocol contract for track, track file, and tag persistence.
  - `AudioAnalysisRepository`: Protocol contract for analysis lifecycle persistence and stale job recovery.
  - `AudioAnalyzer`: Protocol contract for technical probe and psychoacoustic signal decoding.
  - `AlbumRepository`: Protocol contract for album queries and deduplication.
  - `ArtistRepository`: Protocol contract for artist queries and deduplication.
  - `SearchRepository`: Protocol contract for full-text search indexing.

### 2. Application Layer (`harmonia.application`)
- **Use Cases & Workflows**:
  - `PlaybackService`: Implements `PlaybackPort`. Coordinates transport operations, dual-layer queue (FIFO Up Next + Context queue), Auxio contiguous shuffle insertion, factual session deduplication, and snapshot persistence.
  - `ImportService`: Scans filesystem, parses metadata, extracts artwork, links entities, and records field-level provenance.
  - `LibraryService`: Read queries with pagination, formatting, summary metrics, and detail resolution.
  - `AudioAnalysisService`: Coordinates `TrackFile` -> Freshness validation -> Bounded ThreadPool queue -> Worker execution -> Short DB transaction persistence.
  - `StreamingService`: Validates file identity, resolves disk paths against library roots, verifies regular file presence, and formats HTTP 206 Range stream responses.
  - `SearchService`: Full-text search with sanitization and query rank ordering.
- **DTOs**:
  - Pydantic models for validated, serializable data transfer across presentation boundaries (`TrackSummary`, `TrackDetail`, `TrackFileInfo`, `AlbumSummary`, `ArtistSummary`, `LibrarySummary`, `PlaybackStateResponse`, `QueueItemResponse`, `ListeningEventResponse`).

### 3. Infrastructure Layer (`harmonia.infrastructure`)
- **Persistence**:
  - SQLite WAL mode with foreign keys enabled, busy timeouts, and indexed foreign keys.
  - `ListeningEventModel`, `PlaybackQueueSnapshotModel`, `AudioAnalysisModel`, `TrackModel`, `TrackFileModel`, `AlbumModel`, `ArtistModel`, `TagModel`, `DuplicateCandidateModel`, and SQLite FTS5 search virtual tables.
- **Audio Output**:
  - `VirtualAudioBackend`: Deterministic, clock-driven audio backend for test execution and server-side simulation.
  - `DesktopAudioBackend`: Desktop audio backend interfacing with system audio.
- **Filesystem Watcher**:
  - `LibraryWatcher`: Background watchdog observer monitoring library directories.
  - `DebouncedEventHandler`: Sliding 2-second debounce buffer coalescing filesystem bursts and dispatching incremental import tasks to an isolated database session.
- **Audio Intelligence**:
  - `FFmpegAudioAnalyzer`: Isolated subprocess execution of `ffprobe` for stream parsing and `ffmpeg` with `ebur128`, `volumedetect`, and `silencedetect` audio filters.
- **Audio Metadata**:
  - `mutagen_reader.py`: Embedded tag extraction across ID3v2, Vorbis Comments, MP4/AAC atoms, and Opus tags.
  - SHA-256 stream hashing for file deduplication.

### 4. Presentation Layer (`harmonia.cli` & `harmonia.api`)
- **CLI (`harmonia.cli`)**:
  - Built with **Typer** and **Rich**.
  - Subcommands: `import`, `search`, `reindex`, `info`, `list`, `track`, `album`, `artist`, `duplicates`, `analyze`, `serve`, `ui`, `init-db`.
- **Local HTTP API & Web UI (`harmonia.api`)**:
  - Built with **FastAPI** bound exclusively to loopback (`127.0.0.1:8000`).
  - Strict host path concealment: physical paths (`C:\...`, `/home/...`) are completely redacted from all API response models. Clients interact solely via stable UUID identifiers.
  - Playback API: `GET/POST /api/v1/playback/*` (play, pause, resume, stop, seek, next, previous, volume).
  - Queue API: `GET/POST /api/v1/queue/*` (get queue, play-next, up-next, remove, clear, shuffle, repeat).
  - Telemetry API: `GET /api/v1/history/listening-events` (paginated factual listening history).
  - Standard REST envelopes with pagination (`page`, `page_size`, `total`, `total_pages`).
  - Audio streaming via `GET /api/v1/stream/{file_id}` supporting byte ranges (RFC 7233).
  - **Embedded Desktop Web UI**: Served directly from FastAPI at `/` and `/ui`, providing a responsive dark audiophile interface for library management, real-time track inspection, Audio Intelligence orchestration, transport dock playback controls, seek slider, and volume regulation.

---

## Audio Analysis Architecture & Worker Model

### 1. Analysis Lifecycle
```
[TrackFile Added/Updated]
          │
          ▼
   Freshness Check (file_hash == current_hash AND analyzer_version == current_version)
          ├── Fresh ────────► Return Cached Analysis (idempotent skip)
          └── Stale / Missing
                    │
                    ▼
              Status: PENDING
                    │
                    ▼
          Bounded Local Queue
                    │
                    ▼
          Worker ThreadPool (min(4, max(1, CPU - 1)))
                    │
                    ▼
              Status: RUNNING (timestamped)
                    │
                    ▼
          FFmpeg/ebur128 Subprocess (outside DB transaction)
                    ├── Success ──► Status: COMPLETED (short DB commit)
                    └── Error ────► Status: FAILED (error message stored)
```

### 2. Transaction Invariants
Database connections are never held open across long-running FFmpeg subprocess execution. Transactions are strictly scoped:
1. Mark job `RUNNING` in a quick transaction (<5ms).
2. Release database connection and decode audio in subprocess (seconds).
3. Open a fresh transaction to store measurements and mark `COMPLETED` (<5ms).

### 3. Crash Recovery
On service startup, `AudioAnalysisService.recover_stale_jobs()` identifies any jobs left in `RUNNING` status beyond the stale threshold (e.g., from power loss or abrupt shutdown) and resets them to `PENDING` for automatic worker reprocessing.

---

## Security Model & Streaming Boundary

1. **Loopback Binding**: The REST API binds to `127.0.0.1` by default. Binding to non-loopback interfaces requires explicit configuration and emits a clear console warning.
2. **Path Concealment**: No internal filesystem paths are ever exposed in public API models or headers.
3. **No Arbitrary Path Inputs**: Endpoints accept exclusively UUID identifiers. No path parameters exist.
4. **Library Root Validation**: Prior to opening any audio file for streaming or analysis, the resolved real path must be confirmed to exist, be a regular file, and reside within the registered library root directories. Path traversal attacks (`..`, symlinks pointing outside roots) are strictly rejected with 404/403.
5. **HTTP 206 Partial Content**: The streaming endpoint parses `Range: bytes=start-end`, returns chunks using efficient file generators, and handles invalid ranges with `416 Range Not Satisfiable`.

---

## Harmonia vs Harmonia AI Boundary

Harmonia is strictly deterministic infrastructure:
- **Harmonia owns**: Physical audio files, embedded/path metadata, library database, objective audio signal telemetry (LUFS, True Peak, duration, bitrate, clipping), playback engine, queue state, factual listening events, and secure streaming delivery.
- **Harmonia AI owns**: Subjective taste models, semantic embeddings, user preference profiles, mood/context mapping, natural language interaction, and dynamic playlists.
- **Integration**: Harmonia AI connects to Harmonia as a local client over `http://127.0.0.1:8000/api/v1/`, consuming factual data transfer objects and requesting audio streams by file UUID.

