# Harmonia Catalog Portability & Interchange Specification

**Document Classification:** Subsystem Specification — Desktop Milestone D5-A
**Status:** ACTIVE & AUTHORITATIVE
**Schema Version:** `1.0.0`
**Guiding Architecture:** [`docs/ARCHITECTURE.md`](ARCHITECTURE.md), [`docs/DATA_MODEL.md`](DATA_MODEL.md)
**Primary Implementations:**
- Schemas: [`src/harmonia/application/catalog_dto.py`](../src/harmonia/application/catalog_dto.py)
- Export Service: [`src/harmonia/application/export_service.py`](../src/harmonia/application/export_service.py)
- Import Service: [`src/harmonia/application/catalog_import_service.py`](../src/harmonia/application/catalog_import_service.py)
- CLI Commands: [`src/harmonia/cli/catalog.py`](../src/harmonia/cli/catalog.py)

---

## 1. Overview & Architectural Boundaries

Harmonia is an offline-first, deterministic personal music ecosystem. Milestone **Desktop D5-A** introduces **Catalog Portability**: the capability to export an entire music library catalog into a portable, versioned JSON bundle and import it into another Harmonia instance or a fresh database.

### 1.1 Invariant: Portable Catalog vs. Machine-Local State

To guarantee safe portability across diverse machines, operating systems (Windows, Linux, macOS), and filesystems, the catalog interchange format strictly decouples canonical music metadata from host-specific state:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        CATALOG ENTITY BOUNDARIES                       │
├────────────────────┬───────────────────────────────────────────────────┤
│ Included in Bundle │ • Artists: Canonical name, sort name, MBID        │
│                    │ • Albums: Title, year, track count, art_hash      │
│                    │ • Tracks: Title, number, disc, duration, lyrics   │
│                    │ • TrackFiles: Audio format, bitrate, sample rate, │
│                    │   bit depth, codec, SHA-256 stream file_hash      │
│                    │ • Tags: File, provider, and user-assigned tags    │
│                    │ • MetadataFields: Provenance and override records │
│                    │ • ListeningEvents: Factual play history telemetry │
├────────────────────┼───────────────────────────────────────────────────┤
│ Strictly Excluded  │ • Host filesystem absolute paths (C:\..., /home/..)│
│                    │ • Album.art_path: Machine-specific image file path│
│                    │ • PlaybackQueueSnapshot: Transient player state   │
│                    │ • DuplicateCandidate: Local file triage pairs     │
│                    │ • Cache directories, temp files, worker queues    │
└────────────────────┴───────────────────────────────────────────────────┘
```

> [!IMPORTANT]
> **Audio Files Immutability Invariant:**
> Catalog import operations modify **only** the SQLite relational catalog. Catalog import **NEVER** mutates, deletes, moves, or re-encodes user source audio files on disk.

---

## 2. Schema Specification & Envelope

The interchange format is a structured JSON document conforming to `CatalogBundle`.

### 2.1 Envelope Structure

```json
{
  "manifest": {
    "schema_version": "1.0.0",
    "harmonia_version": "0.1.0",
    "exported_at": "2026-10-17T10:00:00Z",
    "source_system": "harmonia-desktop",
    "entity_counts": {
      "artists": 1,
      "albums": 1,
      "tracks": 1,
      "track_files": 1,
      "tags": 1,
      "metadata_fields": 1,
      "listening_events": 1
    }
  },
  "data": {
    "artists": [],
    "albums": [],
    "tracks": [],
    "track_files": [],
    "tags": [],
    "metadata_fields": [],
    "listening_events": []
  },
  "checksum": "d5a8...64-char-sha256-hex..."
}
```

### 2.2 Schema Versioning
- **Current Version:** `1.0.0`
- **Supported Major Versions:** `1`
- **Version Compatibility Rule:** Any bundle with a major version different from `"1"` is rejected with `CatalogVersionError` prior to modifying persistent storage. Minor version additions must remain backward-compatible.

---

## 3. Integrity Verification & Cryptographic Boundary

### 3.1 Checksum Calculation
The bundle `checksum` is the SHA-256 hex digest of the canonical JSON representation of the `data` payload:

$$\text{checksum} = \text{SHA-256}\Big(\text{CanonicalJSON}(\text{bundle.data})\Big)$$

Canonical JSON is defined as:
1. Keys sorted lexicographically at every object level (`sort_keys=True`).
2. Compact separators without extraneous whitespace (`separators=(',', ':')`).
3. UTF-8 character encoding without ASCII escaping (`ensure_ascii=False`).

### 3.2 Security & Threat Model
- **Structural Integrity:** The checksum detects unintentional file truncation, bit-rot, transmission corruption, or manual file edits.
- **Authenticity Boundary:** The SHA-256 checksum provides **integrity verification**, not cryptographic non-repudiation. It does not prevent an active attacker with file write access from modifying data and recomputing the checksum. Cryptographic mutual authentication (e.g. Ed25519 digital signatures) is reserved for future peer-to-peer network sync.
- **Execution Safety:** Bundles are strictly parsed using standard `json.loads` and Pydantic DTO models. No code execution, shell commands, or object deserialization (`pickle`) occurs.

---

## 4. Safe Import Semantics & Conflict Policies

### 4.1 Multi-Stage Pre-flight Validation
Before opening a database write transaction, the `CatalogImportService` executes:
1. **JSON Syntax Validation:** Rejects malformed JSON syntax.
2. **Envelope Validation:** Confirms presence of `manifest`, `data`, and `checksum`.
3. **Version Validation:** Confirms compatibility with supported schema major versions (`1.x.x`).
4. **Integrity Validation:** Recomputes canonical SHA-256 over `data` and verifies parity with `checksum`.
5. **Pydantic Validation:** Validates types, required fields, and value constraints across all entities.
6. **Intra-Bundle Duplicate Validation:** Verifies no duplicate primary IDs or duplicate composite keys (tags, metadata fields, MBIDs) exist within the bundle payload.
7. **Referential Integrity Validation:** Confirms that all foreign keys (e.g., `track.artist_id`, `track.album_id`, `tag.track_id`, `listening_event.track_id`, `track_file.track_id`) and polymorphic `metadata_field.entity_id` references (for supported types `track`, `album`, `artist`) resolve to an entity present in the bundle or already existing in the target database.

### 4.2 Atomic Transaction Boundary
All import mutations execute inside an atomic database transaction (`with session.begin_nested():`). If any record insertion fails, constraint violation occurs, or conflict policy triggers an error, the transaction rolls back 100%, leaving the database in its exact pre-import state. Database `IntegrityError` exceptions caused by unique constraint violations (e.g., MusicBrainz ID collisions) are reliably caught and translated into documented `CatalogConflictError` instances.

### 4.3 Conflict Resolution Policies (`--on-conflict`)

| Policy | Behavior when Entity ID Exists in Database | Use Case |
| :--- | :--- | :--- |
| **`skip`** *(Default)* | Leaves existing record unchanged; skips imported entity; increments skipped counter. | Safe merge into an existing library without overwriting existing edits. |
| **`replace`** | Updates existing record's non-key attributes with imported values; increments updated counter. | Updating an existing library with newer metadata from a master archive. |
| **`fail`** | Aborts import immediately with `CatalogConflictError`; rolls back all database modifications. | Strict verification where no overlapping records are expected. |

### 4.4 TrackFile Machine Path & Safe Physical Association
In a portable catalog, `TrackFile` models record technical container properties (`format`, `bitrate`, `sample_rate`, `bit_depth`, `codec`, `file_hash`).
- **Exported Representation:** Absolute directory paths are never serialized. Cross-OS normalization strips directory structures and drive letters from Windows (`C:\...`), POSIX (`/...`), and UNC paths, exporting only a pure portable filename basename hint (e.g., `teardrop.flac`) in `file_path`.
- **Safe Import Association Contract:**
  1. **Never associate physical media solely by path existence.** If a local file exists at the candidate path, Harmonia requires a verified SHA-256 stream `file_hash`. The local file is hashed using `compute_file_hash()`. Only if the computed hash strictly matches `tf.file_hash` is the physical media associated and marked with `verified_at = now()`.
  2. **Hash Mismatch or Missing Hash Protection:** If the candidate local file exists but its content hash does not match `tf.file_hash`, or if the bundle lacks a `file_hash`, Harmonia **refuses to associate the local file**. The record is imported in an unlinked state (`unlinked/<track_file_id>/<filename_hint>`) with `verified_at = None`, and a diagnostic warning is reported.
  3. **Path Traversal Sanitization & Playback Safety:** Missing or unlinked paths are sanitized via `sanitize_unlinked_filename()`, which strips path traversal sequences (`..`, `.`), drive letters, directory separators, and invalid characters. The resulting locator (`unlinked/<track_file_id>/<filename_hint>`) cannot escape its namespace or point to physical host files. Playback backends (`DesktopAudioBackend`) reject unlinked relative paths with `FileNotFoundError`, guaranteeing unlinked records cannot become accidentally playable.
  4. **Physical Media Immutability:** Catalog import operations never modify, delete, rename, or touch physical audio files on disk.
  5. **Scanner Reconciliation via Content Hash:** The existing directory scanner (`harmonia scan` / `ImportService`) natively reconciles imported unlinked `TrackFile` records with local physical files when content hashing is enabled. When the scanner encounters a physical audio file matching the SHA-256 hash of an unlinked `TrackFile` whose path does not exist on disk, it applies Tier A1 moved-file resolution, updating `file_path`, `file_mtime`, and `verified_at` in-place on the existing catalog record without creating duplicate tracks.

### 4.5 Full-Text Search (FTS5) Synchronization
Whenever tracks are imported or updated, `CatalogImportService` automatically synchronizes the SQLite FTS5 `tracks_fts` index. Search queries via `harmonia search` or `SearchService` are immediately consistent post-import.

---

## 5. CLI Command Reference

### 5.1 Export: `harmonia export`

```bash
# Export library to a formatted JSON bundle with timestamped filename
harmonia export

# Export to a custom destination path
harmonia export -o my_library_backup.json

# Export compact JSON without formatting indentation
harmonia export -o library_compact.json --compact

# Overwrite an existing export file
harmonia export -o existing_backup.json --force

# Export catalog metadata without listening event history
harmonia export -o catalog_no_history.json --no-telemetry
```

### 5.2 Import: `harmonia import-catalog`

```bash
# Validate bundle and preview changes without modifying database (Dry-Run)
harmonia import-catalog my_library_backup.json --dry-run

# Import catalog with default safe skip policy
harmonia import-catalog my_library_backup.json

# Import and overwrite existing records with imported metadata
harmonia import-catalog my_library_backup.json --on-conflict replace

# Import with strict failure on any duplicate identifier
harmonia import-catalog my_library_backup.json --on-conflict fail
```

---

## 6. Known Limitations & Deferred Capabilities

1. **Audio File Transfer:** Milestone D5-A implements **metadata catalog portability**. It does not stream, transcode, or copy physical audio files across machines. Binary audio file transfer is scoped to Mobile M6 LocalSync.
2. **Mobile Playlists:** User playlists are currently implemented in the mobile Room database (`PlaylistEntity`), not the desktop relational schema. Playlists will be integrated into the desktop schema and interchange format during Milestone D5 follow-ons or sync specifications.
3. **Real-time Peer-to-Peer Sync:** D5-A provides file-based portable bundle export/import. Automatic local network discovery (mDNS), mutual TLS, and delta synchronization are part of Stream 4 / Mobile M6.
4. **Relocation Without Content Hashes:** Automatic reconciliation of unlinked `TrackFile` records with local physical files relies on the verified SHA-256 stream `file_hash` matching local files during directory scanning. If an exported bundle lacks file hashes, or if files were re-encoded/modified, automatic reconciliation is intentionally skipped to prevent false merges. A dedicated manual relocation subsystem (e.g. interactive CLI remapping or fuzzy matching) is deferred to a future milestone.
