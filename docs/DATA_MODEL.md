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
                                    │ duration_sec     │
                                    │ is_lossless      │
                                    │ quality_score    │
                                    │ added_at         │
                                    │ verified_at      │
                                    └──────────────────┘
```

---

## Core Entities

### 1. `Track` vs `TrackFile` (One-to-Many)
A fundamental design decision (**ADR-002**) is decoupling the logical track recording from its physical representation on disk:
- **`Track`**: Represents the abstract musical work (e.g., *"Bohemian Rhapsody"* by Queen). Holds canonical metadata like title, artist, album, year, and genre.
- **`TrackFile`**: Represents a specific audio file on disk (e.g., a 24-bit/96kHz FLAC file in high-res storage, or a 320kbps MP3 on a portable device). Contains file size, path, format, bitrate, sample rate, bit depth, and technical quality score.

**Benefits**:
- Multiple encodings of the same track can coexist without duplicating canonical metadata.
- Enables automated upgrade to higher quality versions when found.
- Supports duplicate detection without file loss.

### 2. `Artist` & `Album`
- **`Artist`**: Stores artist name, sort name (e.g. "Beatles, The"), and MusicBrainz Artist ID. Case-insensitive lookup enables deduplication during imports.
- **`Album`**: Tracks album title, release year, track/disc counts, and album cover art path. Linked to `Artist` with `ondelete="SET NULL"`.

### 3. `Tag` (EAV Pattern)
- Flexible entity-attribute-value storage associated with `Track`.
- Tag entries carry a `source` enum:
  - `FILE`: Embedded file tags extracted during scanning.
  - `USER`: User-applied tags.
  - `PROVIDER`: Tags fetched from external providers (MusicBrainz, Discogs).
