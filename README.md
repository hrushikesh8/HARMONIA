# Harmonia

**Deterministic music infrastructure and personal music library system.**

Harmonia acquires, imports, organizes, analyzes, indexes, searches, and manages your music collection. It works entirely offline, is local-first, and requires no AI or cloud services.

> **Harmonia owns the music. [Harmonia AI](https://github.com/...) understands the person.**

## Features

- **Import** — Scan directories, extract metadata, organize your library
- **Metadata** — ID3, Vorbis, MP4/M4A tag extraction with normalization and conflict resolution
- **Search** — Full-text search across your entire library
- **Audio Analysis** — Codec, bitrate, sample rate, loudness, quality scoring
- **Playlists** — Create and manage playlists
- **Playback** — Integrate with external playback engines (mpv, etc.)
- **API** — HTTP REST API and Python API for external consumers
- **CLI** — Complete command-line interface

## Quick Start

```bash
# Install with uv
uv pip install -e ".[dev]"

# Import your music library
harmonia import ~/Music

# List your library
harmonia list

# Search for tracks
harmonia search "bohemian rhapsody"
```

## Development

```bash
# Clone and install
git clone <repo-url>
cd harmonia
uv venv
uv pip install -e ".[dev]"

# Run tests
pytest

# Lint and format
ruff check src/ tests/
ruff format src/ tests/

# Type check
mypy src/
```

## Architecture

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the full system design.

Harmonia follows **Clean Architecture** with four layers:

```
Presentation  →  CLI, HTTP API
Application   →  Use cases / services
Domain        →  Entities, value objects, ports
Infrastructure →  Database, file I/O, audio tools
```

## Project Status

Phase 1 — Foundation. See [docs/ROADMAP.md](docs/ROADMAP.md).

## License

MIT
