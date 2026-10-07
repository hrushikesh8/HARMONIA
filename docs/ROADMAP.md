# Harmonia Development Roadmap

The Harmonia ecosystem is built across 8 sequential phases:

---

### Phase 1: Foundation (Current Status: COMPLETE)
- [x] Project setup (`pyproject.toml`, dependencies, directory structure).
- [x] Core infrastructure (config, logging, database WAL mode, events, exceptions).
- [x] Domain entities (`Track`, `TrackFile`, `Album`, `Artist`, `Tag`).
- [x] Value objects (`AudioFormat`) and pure domain service (`compute_quality_score`).
- [x] SQLAlchemy 2.0 ORM models and Alembic initial migration (`001_initial_schema`).
- [x] Filesystem scanner (`scan_directory`, path metadata parser).
- [x] Mutagen audio metadata reader & SHA-256 file stream hashing.
- [x] Application use cases (`ImportService`, `LibraryService`, DTOs).
- [x] Typer + Rich CLI (`import`, `info`, `list`, `track`, `album`, `artist`, `init-db`).
- [x] Comprehensive test suite (unit and integration tests passing).

---

### Phase 2: Metadata & Discovery (Status: COMPLETE)
- [x] Metadata normalization pipeline (Unicode NFC, whitespace, sort key generation).
- [x] `MetadataFieldRecord` entity and field-level provenance tracking.
- [x] Conflict resolution engine with strict priority rules (User > MusicBrainz > File > Path).
- [x] MusicBrainz provider integration (rate-limited lookup port, offline-safe).
- [x] Full-Text Search (SQLite FTS5 indexing for tracks, albums, artists, genres).
- [x] Artwork extraction, SHA-256 deduplicated storage, and linking.
- [x] Duplicate detection with explicit confidence tiers (Tier A physical, Tier B candidate review).

---

### Phase 3: Audio Intelligence & Local HTTP API (Status: COMPLETE)
- [x] `AudioAnalysis` domain entity, lifecycle states, and repository port.
- [x] FFmpeg & FFprobe technical stream properties extraction (`channel_layout`, `encoder`).
- [x] ITU-R BS.1770-4 signal analysis (integrated LUFS, loudness range LU, True Peak dBTP, clipping, silence).
- [x] Bounded ThreadPool worker queue with crash recovery and short database transactions.
- [x] Local-first FastAPI REST API (tracks, albums, artists, search, artwork, duplicates, analysis).
- [x] Secure audio streaming via `GET /api/v1/stream/{file_id}` with HTTP 206 Partial Content Range support.
- [x] Strict host filesystem path concealment and library root traversal protection.

---

### Phase 4: Playback & Events
- Playback engine port and mpv integration adapter.
- Queue management and transport controls.
- Telemetry: `ListeningEvent` recording (factual play tracking).
- User ratings and favorites storage.
- Filesystem watcher for automatic import of new files.

---

### Phase 5: Harmonia AI Foundation
- Harmonia AI separate project establishment and independent SQLite database (`harmonia_ai.db`).
- `HarmoniaClient` port & adapter for calling Harmonia's API.
- `UserProfile` and `TasteProfile` intelligence entities.
- Listening history intelligence (affinity analysis, skip rates, completion percentages).
- Content-based recommendation engine.
- AI provider abstraction (local LLMs, Gemini, OpenAI).

---

### Phase 6: Context & Intelligence
- Multi-dimensional Context Engine (time of day, activity, situation).
- Natural Language Understanding (NLU) for music requests.
- Intelligent playlist generation algorithms.
- Conversational and preference memory store.

---

### Phase 7: Jarvis Integration
- Jarvis personal assistant protocol adapter.
- Natural language intent handling and now-playing status.
- End-to-end voice/text query flow: Jarvis -> Harmonia AI -> Harmonia -> Playback.

---

### Phase 8: Hardening & Polish
- JSON/CSV export and restore utilities.
- Suspicious transcode detection (lossy upconverts masquerading as lossless).
- Performance optimizations and index tuning.
