# Harmonia Architecture Validation: Identity, Provenance & History

**Document Classification:** Pre-Implementation Freeze — Finalized Identity Specification  
**Status:** FROZEN & APPROVED  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Principle:** *"Harmonia owns the music. Harmonia AI understands the person."*

---

## 1. Executive Identity Decision

Harmonia strictly decouples the **logical musical recording** from the **physical container on disk**:

- **`Track.id` (RFC 4122 UUID):** The abstract musical work (e.g. *Bohemian Rhapsody* by Queen). It is the authoritative identity target for:
  - User Playlists
  - Favorites & Star Ratings
  - Aggregated Play Counts & Completion Metrics
  - Harmonia AI Taste Profiling
- **`TrackFile.id` (RFC 4122 UUID):** A specific physical file instance on disk. It holds container metadata (`file_path`, `format`, `codec`, `bitrate`, `sample_rate`, `bit_depth`, `quality_score`) and links 1:1 to `AudioAnalysis`.
- **`TrackFile.file_hash` (SHA-256 Hex Digest):** The byte-level stream hash used for bit-rot detection, deduplication, and tracking renamed or moved files.
- **`TrackFile.fingerprint` (Chromaprint):** The perceptual acoustic waveform fingerprint used for audio identification independent of container or tag metadata.

---

## 2. Finalized ListeningEvent Identity & Scope

### 2.1 The Phase 4 ListeningEvent Model

To keep Phase 4 simple, deterministic, and free of over-engineered synchronization baggage, `ListeningEvent` is defined as:

```python
@dataclass
class ListeningEvent:
    """Immutable factual record of an audio playback session."""

    id: UUID  # Generated via uuid.uuid4()
    track_id: UUID  # Logical musical work (authoritative link for user analytics)
    track_file_id: UUID | None  # Specific physical file played (technical fidelity link)
    timestamp_utc: datetime  # Exact playback start time (UTC)
    duration_listened_seconds: float  # Total continuous seconds listened
    track_duration_seconds: float  # Total duration of track at playback time
    was_completed: bool  # True if duration_listened >= 0.8 * track_duration (or >= 240s)
    was_skipped: bool  # True if stopped/skipped before 30 seconds
    playback_source: str  # "queue", "album", "playlist", "search"
    device_id: UUID | None = None  # None in Phase 4; reserved for Phase 5 LocalSync
```

### 2.2 Device Identity Resolution (`device_id`)
- **Phase 4 Reality:** Harmonia runs on a single desktop workstation/server instance. Device identity is **NOT required for Phase 4 correctness**.
- **Specification:** The `device_id` column is added to the schema as **nullable (`UUID | None`, default `None`)**. 
- **Future LocalSync Rule:** In Phase 5, when Harmonia Mobile pairs with Harmonia Core, each installation generates a stable `device_id` in its configuration. Mobile playback events will carry `device_id = mobile_uuid`. Desktop events will carry `device_id = desktop_uuid`.

### 2.3 Event Deduplication & Unique Identifiers
- **Identifier Strategy:** Every listening event receives an immutable `id: UUID` generated via `uuid.uuid4()`.
- **Why UUIDv4 over ULID/UUIDv7 for Phase 4:** Standard Python stdlib `uuid.uuid4()` requires zero third-party dependencies, works natively with SQLAlchemy `Uuid()` and SQLite, and provides complete collision immunity across distributed devices. Temporal ordering is handled by indexing `timestamp_utc`.
- **Session Deduplication (Within a Single Player):**
  - An active playback session holds an ephemeral `session_id: UUID` in memory.
  - Seeking, pausing, and resuming within the same track updates the active playback session's cumulative listen time without committing duplicate rows.
  - A `ListeningEvent` row is committed **exactly once** when the track completes, is skipped, or playback stops.
- **Deduplication Key for Future Cross-Device Sync (Phase 5):**
  - Primary deduplication key: `(id)`.
  - Secondary idempotency guard: `(device_id, timestamp_utc, track_id)`.

---

## 3. The 10 Lifecycle Scenarios Validated

```
┌────────────────────────────────────────────────────────────────────────┐
│                        LIFECYCLE SCENARIO BEHAVIOR                     │
├────────────────────┬───────────────────────────────────────────────────┤
│ Scenario           │ Harmonia System Action                            │
├────────────────────┼───────────────────────────────────────────────────┤
│ 1. File Renamed    │ Scanner matches SHA-256 stream hash. Updates      │
│                    │ TrackFile.file_path. Track.id, history, and       │
│                    │ playlists are 100% untouched.                     │
├────────────────────┼───────────────────────────────────────────────────┤
│ 2. File Moved      │ Scanner matches SHA-256. Updates TrackFile.path.  │
│                    │ Provenance logs record path update.               │
├────────────────────┼───────────────────────────────────────────────────┤
│ 3. File Copied     │ Second file links to same Track.id or is marked   │
│                    │ as duplicate candidate. Track is NOT duplicated.  │
├────────────────────┼───────────────────────────────────────────────────┤
│ 4. FLAC + MP3 of   │ Both exist as distinct TrackFiles linked to single│
│    same song       │ Track.id. Single library entry; quality selector. │
├────────────────────┼───────────────────────────────────────────────────┤
│ 5. Transcode       │ New TrackFile created with format=OPUS linked to  │
│    (FLAC -> Opus)  │ Track.id. Historical stats unified on Track.id.   │
├────────────────────┼───────────────────────────────────────────────────┤
│ 6. Metadata Edit   │ Updates MetadataFieldRecord provenance. User-     │
│                    │ locked fields (USER priority) are NEVER overwritten.│
├────────────────────┼───────────────────────────────────────────────────┤
│ 7. Audio Upgrade   │ Old TrackFile replaced by new lossless TrackFile. │
│    (MP3 -> FLAC)   │ Playlists, ratings, and history point to Track.id,│
│                    │ so ALL user history seamlessly survives upgrade!  │
├────────────────────┼───────────────────────────────────────────────────┤
│ 8. Storage         │ Storage volume marked STORAGE_UNMOUNTED. Tracks   │
│    Disconnected    │ remain in library with offline badge. Zero loss.  │
├────────────────────┼───────────────────────────────────────────────────┤
│ 9. File Missing,   │ Acoustic fingerprint matches missing track. Auto- │
│    Twin Appears    │ proposes re-linking new file to existing Track.id.│
├────────────────────┼───────────────────────────────────────────────────┤
│ 10. Different      │ Separate Release MBIDs -> separate Album/Track    │
│     Masterings     │ rows. User can optionally merge TrackFiles.       │
└────────────────────┴───────────────────────────────────────────────────┘
```

---

## 4. Final Classification

- **Track vs. TrackFile Separation:** **CONFIRMED & FROZEN**.
- **ListeningEvent Dual Pointer:** **CONFIRMED & FROZEN** (`track_id` + `track_file_id`).
- **Phase 4 `device_id`:** **CONFIRMED OPTIONAL** (`UUID | None = None`).
- **Playlist & Rating Targets:** **CONFIRMED & FROZEN** (Target `Track.id` strictly).
