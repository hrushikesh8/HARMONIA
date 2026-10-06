# Security Policy & Privacy Architecture

## Local-First Security Model

**Harmonia** is designed from the ground up as a private, local-first music management system:

1. **No External Telemetry or Phone-Home**:
   Harmonia does not collect, transmit, or report usage data, library metrics, or listening habits to any external servers or telemetry services.

2. **Isolated Storage**:
   All database records, metadata, audio files, and extracted artwork reside locally on the host machine.
   The SQLite database is kept under `data/harmonia.db` with WAL mode enabled.

3. **Separation from AI Intelligence**:
   Harmonia holds deterministic music library facts. No user profiling, psychological modeling, or preference heuristics are executed inside Harmonia. The intelligence layer (Harmonia AI) maintains a separate SQLite database (`harmonia_ai.db`).

4. **External API Requests (Future Phases)**:
   Any future external metadata lookups (e.g. MusicBrainz) will only transmit search query strings (artist/album/title) and will not share personal listening histories, IP tracking, or user identities.

5. **Secrets and Credentials**:
   Configuration parameters are loaded strictly from local `.env` files or environment variables. No secrets or tokens are checked into version control.
