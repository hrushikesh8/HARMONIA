# Harmonia Data Model — Desktop Relational Schema

**Document Classification:** Desktop Relational Data Model (SQLAlchemy / SQLite WAL)
**Status:** ACTIVE & AUTHORITATIVE (Desktop Subsystem)
**Related Schemas:**
- Mobile Room Database Schema: See [`docs/MOBILE_M5_ARCHITECTURE.md`](MOBILE_M5_ARCHITECTURE.md), [`docs/MOBILE_ARCHITECTURE_v0.1.md:§11.2`](MOBILE_ARCHITECTURE_v0.1.md), and [`ADR-036`](knowledge/adr/ADR-036-personalization-persistence-schema-v5.md)
- Master Roadmap: [`docs/ROADMAP.md`](ROADMAP.md)

> [!NOTE]
> **Subsystem Scope:** This document defines the relational entity-relationship schema implemented in Python with SQLAlchemy 2.0 and Alembic for the **Harmonia Desktop & Core Server** library database (`src/harmonia/infrastructure/database/`).

## Entity Relationship Overview

```
┌──────────────┐          ┌──────────────┐          ┌──────────────┐
│    Artist    │ 1      * │    Album     │ 1      * │    Track     │
│──────────────│──────────│──────────────│──────────│──────────────│
│ id (UUID)    │          │ id (UUID)    │          │ id (UUID)    │
│ name         │          │ title        │          │ title        │
│ sort_name    │          │ artist_id    │          │ artist_id    │
│ mbid         │          │ year         │          │ album_id     │
│ created_at   │          │ total_tracks │          │ track_number │
│ updated_at   │          │ total_discs  │          │ disc_number  │
└──────────────┘          │ mbid         │          │ duration_sec │
                          │ art_path     │          │ year         │
                          │ created_at   │          │ genre        │
                          │ updated_at   │          │ mbid         │
                          └──────────────┘          │ lyrics       │
                                                    │ created_at   │
                                                    │ updated_at   │
                                                    └──────┬───────┘
                                                           │ 1
                                             ┌─────────────┴─────────────┐
                                           * │                           │ *
                                    ┌────────┴─────────┐        ┌────────┴─────────┐
                                    │    TrackFile     │        │       Tag        │
                                    │──────────────────│        │──────────────────│
                                    │ id (UUID)        │        │ id (UUID)        │
                                    │ track_id         │        │ track_id         │
                                    │ file_path (uniq) │        │ name             │
                                    │ file_hash        │        │ value            │
                                    │ file_size        │        │ source           │
                                    │ format           │        │  (file|user|prov)│
                                    │ codec            │        │ created_at       │
                                    │ bitrate          │        └──────────────────┘
                                    │ sample_rate      │
                                    │ bit_depth        │
                                    │ channels         │
                                    │ channel_layout   │
                                    │ encoder          │
                                    │ duration_sec     │
                                    │ is_lossless      │
                                    │ quality_score    │
                                    │ added_at         │
                                    │ verified_at      │
                                    └────────┬─────────┘
                                             │ 1
                                             ▼ 0..1
                                    ┌──────────────────┐
                                    │  AudioAnalysis   │
                                    │──────────────────│
                                    │ id (UUID)        │
                                    │ track_file_id (U)│
                                    │ file_hash        │
                                    │ status           │
                                    │ integrated_lufs  │
                                    │ loudness_range_lu│
                                    │ true_peak_dbtp   │
                                    │ sample_peak_dbfs │
                                    │ peak_to_loudness │
                                    │ clipping_samples │
                                    │ is_clipped       │
                                    │ duration_analyzed│
                                    │ silence_start_sec│
                                    │ silence_end_sec  │
                                    │ analyzer_name    │
                                    │ analyzer_version │
                                    │ config_version   │
                                    │ error_message    │
                                    │ started_at       │
                                    │ completed_at     │
                                    │ execution_time   │
                                    └──────────────────┘
```

---

## Core Entities

### 1. `Track` vs `TrackFile` (One-to-Many)
A fundamental design decision (**ADR-002**) is decoupling the logical track recording from its physical representation on disk:
- **`Track`**: Represents the abstract musical work (e.g., *"Bohemian Rhapsody"* by Queen). Holds canonical metadata like title, artist, album, year, and genre.
- **`TrackFile`**: Represents a specific audio file on disk (e.g., a 24-bit/96kHz FLAC file in high-res storage, or a 320kbps MP3 on a portable device). Contains file size, path, format, bitrate, sample rate, bit depth, channel layout, encoder, and technical quality score.

**Benefits**:
- Multiple encodings of the same track can coexist without duplicating canonical metadata.
- Enables automated upgrade to higher quality versions when found.
- Supports duplicate detection without file loss.

### 2. `AudioAnalysis` (Derived Signal Intelligence)
`AudioAnalysis` (**ADR-013**) represents objective technical measurements computed directly from decoding a physical `TrackFile`:
- Associates 1:1 with `TrackFile` (each physical encoding has distinct psychoacoustic properties).
- Tracks `file_hash`, `analyzer_version`, and `config_version` to provide deterministic freshness validation (`is_fresh`).
- Persists ITU-R BS.1770-4 integrated LUFS, loudness range (LU), True Peak (dBTP), sample peak (dBFS), peak-to-loudness ratio, clipping sample counts, and leading/trailing silence boundaries.

### 3. `Artist` & `Album`
- **`Artist`**: Stores artist name, sort name (e.g. "Beatles, The"), and MusicBrainz Artist ID. Case-insensitive lookup enables deduplication during imports.
- **`Album`**: Tracks album title, release year, track/disc counts, and album cover art path. Linked to `Artist` with `ondelete="SET NULL"`.

### 4. `Tag` (EAV Pattern)
- Flexible entity-attribute-value storage associated with `Track`.
- Tag entries carry a `source` enum:
  - `FILE`: Embedded file tags extracted during scanning.
  - `USER`: User-applied tags.
  - `PROVIDER`: Tags fetched from external providers (MusicBrainz, Discogs).

---

## 5. Playback Telemetry & Large-Library Tuning (Migrations 004 & 005)

### `ListeningEvent` & `PlaybackQueueSnapshot` (Migration 004)
Introduced in Phase 4 (**D4.4**, commit `1972ddfc`) to track factual playback telemetry without retaining subjective state:
- **`listening_events`**: Append-only log recording timestamped playback sessions. Captures `track_id`, `track_file_id`, `duration_listened_sec`, `completed` (boolean indicating $\ge 80\%$ completion threshold), and optional playback `context`. Indexed on `track_id` and `timestamp`.
- **`playback_queue_snapshots`**: Persistent snapshots of the headless playback queue state machine, recording serialised JSON items, active index, and timestamp.

### SQLite Performance Tuning (Migration 005)
Introduced in Phase 5D (**D5-D**, commit `b510b60d`) for large-library query optimisation ($\ge 100{,}000$ tracks):
- Migration `005_phase5d_sqlite_tuning.py` is intentionally an **index-only migration** introducing 0 new tables and 9 composite/partial covering indexes:
  - `ix_artists_name_lower`
  - `ix_albums_artist_title_lower`
  - `ix_albums_artist_year`
  - `ix_tracks_album_disc_track`
  - `ix_tracks_title_lower`
  - `ix_tracks_artist_title_lower`
  - `ix_track_files_track_quality`
  - `ix_track_files_format_lossless`
  - `ix_tags_track_source`

---

## 6. Personalization Domain Schema (Migration 006)

Introduced during Desktop Personalization Phase 2 (**D-MIG006**, commit `a96ba7e7`) to achieve parity with the Harmonia Mobile v5 Room schema:

### 1. `favorites`
- Stores user-favorited entities polymorphically:
  - `id`: UUID primary key.
  - `entity_type`: String (`TRACK`, `ALBUM`, `ARTIST`).
  - `entity_id`: UUID referencing the target entity.
  - `source`: String (`MANUAL_DESKTOP`, `LOCALSYNC`).
  - `created_at`: Timezone-aware UTC timestamp.
- Unique constraint: `uq_favorites_entity (entity_type, entity_id)`.

### 2. `ratings` (User Ratings)
- Stores explicit user ratings (1 to 5 stars or fractional points):
  - `id`: UUID primary key.
  - `entity_type`: String (`TRACK`, `ALBUM`, `ARTIST`).
  - `entity_id`: UUID referencing the target entity.
  - `rating_value`: Float / SmallInteger representing rating score.
  - `created_at`, `updated_at`: Timezone-aware UTC timestamps.
- Unique constraint: `uq_ratings_entity (entity_type, entity_id)`.

### 3. `playlists` & `playlist_items`
- Relational playlist storage:
  - **`playlists`**: `id`, `name`, `description`, `is_smart` (boolean), `rules_json` (for dynamic smart query criteria), `created_at`, `updated_at`.
  - **`playlist_items`**: `id`, `playlist_id` (FK to `playlists.id` ondelete `CASCADE`), `track_id` (FK to `tracks.id` ondelete `CASCADE`), `position` (integer ordering coordinate), `added_at`.
  - Unique constraint: `uq_playlist_item_position (playlist_id, position)`.

> [!NOTE]
> **Implementation Status Disclosure:**
> - Migration `006_personalization_schema.py`, domain models, and repositories are **committed** to `master` (commit `c46be003`).
> - Personalization REST API routes (`/api/v1/favorites/*`, `/api/v1/ratings/*`, `/api/v1/playlists/*`) and integration tests reside in the **local uncommitted working tree** (subphase `D-P2-D.1`).

---

## 7. Alembic Migration History & Declarative Chain

The repository maintains an authoritative, strictly linear migration chain in `migrations/versions/`:

| Revision ID | Down Revision | Subphase / Milestone | Commit Hash | Tables Created | Indexes Created | Status |
|---|---|---|---|:---:|:---:|---|
| `001_initial_schema` | `None` | `D1.1` | `7ee88e10` | 5 | 14 | Committed |
| `002_phase2_metadata_provenance_search` | `001_initial_schema` | `D2.1` | `d69046f8` | 2 | 6 | Committed |
| `003_phase3_audio_analysis` | `002_phase2_metadata_provenance_search` | `D3.1` | `5c709404` | 1 | 4 | Committed |
| `004_phase4_playback_and_telemetry` | `003_phase3_audio_analysis` | `D4.4` | `1972ddfc` | 2 | 3 | Committed |
| `005_phase5d_sqlite_tuning` | `004_phase4_playback_and_telemetry` | `D5-D` | `b510b60d` | 0 | 9 | Committed |
| `006_personalization_schema` | `005_phase5d_sqlite_tuning` | `D-MIG006` | `a96ba7e7` | 4 | 8 | Committed |

> [!IMPORTANT]
> **Verification Boundary:** The Alembic migration chain was verified declaratively via AST source parsing. In accordance with read-only audit rules, no live database upgrade (`alembic upgrade head`) was executed on a physical database during documentation closeout.

