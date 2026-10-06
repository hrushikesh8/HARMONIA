# Harmonia Architecture

## Overview

**Harmonia** is the deterministic music infrastructure and personal music library system. It is designed to be:
- **Local-first & offline**: Operates completely without network access or cloud dependencies.
- **Deterministic**: Pure logic with no probabilistic or AI components.
- **Modular**: Adheres strictly to pragmatic Clean Architecture principles.
- **Auditable & Provenance-aware**: Separates physical audio files (`TrackFile`) from logical recordings (`Track`), and tracks metadata sources.

---

## Clean Architecture Layers

```
┌────────────────────────────────────────────────────────┐
│                      Presentation                      │
│                  (Typer CLI, HTTP API)                 │
├────────────────────────────────────────────────────────┤
│                      Application                       │
│             (ImportService, LibraryService, DTOs)      │
├────────────────────────────────────────────────────────┤
│                        Domain                          │
│        (Entities, Value Objects, Repository Ports)     │
├────────────────────────────────────────────────────────┤
│                    Infrastructure                      │
│      (SQLite Repositories, Mutagen Reader, Scanner)    │
└────────────────────────────────────────────────────────┘
```

### 1. Domain Layer (`harmonia.domain`)
- **Entities**:
  - `Track`: Logical music recording with normalized metadata.
  - `TrackFile`: Physical audio file on disk, codec parameters, technical properties.
  - `Album`: Collection of tracks with release metadata and cover art references.
  - `Artist`: Creator/performer with canonical and sort names.
  - `Tag`: Key-value tags classified by origin (`file`, `user`, `provider`).
- **Values**:
  - `AudioFormat`: Enum of recognized file formats and lossless heuristics.
- **Ports (Interfaces)**:
  - `TrackRepository`: Protocol contract for track, track file, and tag persistence.
  - `AlbumRepository`: Protocol contract for album queries and deduplication.
  - `ArtistRepository`: Protocol contract for artist queries and deduplication.
- **Services**:
  - `compute_quality_score`: Deterministic 0-100 technical quality evaluation function.

### 2. Application Layer (`harmonia.application`)
- **Use Cases & Workflows**:
  - `ImportService`: Scans filesystem, parses metadata, links or creates artists/albums, inserts tracks, computes hashes, and publishes lifecycle events.
  - `LibraryService`: Read queries with pagination, formatting, summary metrics, and detail resolution.
- **DTOs**:
  - Pydantic models for validated, serializable data transfer across presentation boundaries (`TrackSummary`, `TrackDetail`, `AlbumSummary`, `ArtistSummary`, `ImportResult`).

### 3. Infrastructure Layer (`harmonia.infrastructure`)
- **Persistence**:
  - `models.py`: SQLAlchemy 2.0 Declarative ORM models with SQLite WAL mode and foreign key constraints.
  - Repositories (`SqliteTrackRepository`, `SqliteAlbumRepository`, `SqliteArtistRepository`).
- **Audio**:
  - `mutagen_reader.py`: Embedded tag extraction across ID3v2, Vorbis Comments, MP4/AAC atoms, and Opus tags.
  - SHA-256 stream hashing for file deduplication.
- **Filesystem**:
  - `scanner.py`: Recursive directory walker with hidden-file filtering and path metadata parsing fallback.

### 4. Presentation Layer (`harmonia.cli`)
- Built with **Typer** and **Rich** for responsive, readable terminal interaction.
- Subcommands: `harmonia import`, `harmonia info`, `harmonia list [tracks|albums|artists]`, `harmonia track`, `harmonia album`, `harmonia artist`, and `harmonia init-db`.

---

## Harmonia vs Harmonia AI Boundary

Harmonia is strictly deterministic infrastructure. Harmonia AI is a separate system that consumes Harmonia's API:
- Harmonia owns music files, metadata, and factual listening telemetry.
- Harmonia AI owns taste profiling, user preference modeling, mood/context mapping, and playlist generation logic.
- Harmonia AI communicates with Harmonia via API and maintains its own separate SQLite database.
