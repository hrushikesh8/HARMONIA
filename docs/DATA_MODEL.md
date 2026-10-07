# Harmonia Data Model

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

