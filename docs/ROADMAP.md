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

### Phase 2: Metadata & Search
- Metadata normalization pipeline (Unicode NFC, whitespace, date standardization).
- `MetadataSource` entity and provenance tracking.
- Conflict resolution engine with strict priority rules (User > MusicBrainz > File > Path).
- MusicBrainz provider integration (rate-limited lookup port).
- Full-Text Search (SQLite FTS5 indexing for tracks, albums, artists, tags).
- Artwork extraction, storage (`data/artwork/`), and linking.
- Duplicate detection (SHA-256 hashes + metadata similarity).

---

### Phase 3: Audio Analysis & Quality
- FFprobe & FFmpeg integration for deep stream properties.
- Loudness analysis (integrated LUFS, loudness range, true peak dBTP).
- Audio fingerprinting (Chromaprint / AcoustID).
- Background job processing for deferred expensive analysis.
- FastAPI HTTP API (REST endpoints for tracks, albums, artists, search).
- Playlist CRUD domain model and service.

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
