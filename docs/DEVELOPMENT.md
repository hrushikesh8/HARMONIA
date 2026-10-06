# Harmonia Development Guide

## Prerequisites

- **Python**: 3.11 or 3.12+
- **Database**: SQLite (built into Python)
- **External tools (optional for Phase 1)**: FFmpeg / FFprobe (for Phase 3 audio analysis)

---

## Setup

1. **Clone repository and enter directory**:
   ```powershell
   cd c:\Users\Hrushikesh Bunni\Downloads\H\PROJECTS\HARMONIA
   ```

2. **Install dependencies (or activate environment)**:
   ```powershell
   pip install -e ".[dev]"
   ```

3. **Configure environment**:
   Copy `.env.example` to `.env` if custom configurations are needed:
   ```powershell
   cp .env.example .env
   ```

---

## Running the CLI

Run Harmonia via Python module or executable entry point:

```powershell
# In PowerShell (ensuring src is in PYTHONPATH if not installed with -e)
$env:PYTHONPATH="src"

# Show help
python -m harmonia --help

# Initialize SQLite database
python -m harmonia init-db

# Check library overview
python -m harmonia info

# Import music directory
python -m harmonia import "C:\path\to\music"

# List tracks, albums, or artists
python -m harmonia list tracks
python -m harmonia list albums
python -m harmonia list artists
```

---

## Running Migrations

Harmonia uses **Alembic** to manage database schema migrations.

```powershell
# Show current head
$env:PYTHONPATH="src"; alembic heads

# Apply migrations
$env:PYTHONPATH="src"; alembic upgrade head
```

---

## Running Tests

Pytest is configured with `strict` mypy typing and ruff linting rules.

```powershell
# Run all unit and integration tests
$env:PYTHONPATH="src"; pytest

# Run with test coverage
$env:PYTHONPATH="src"; pytest --cov=harmonia
```
