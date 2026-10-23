# Harmonia Development Guide — Desktop & Core Server

> [!NOTE]
> **Scope Notice:** This guide covers local environment setup, CLI operations, migrations, and test execution for the **Harmonia Desktop & Core Server** Python project (`src/harmonia/`). For development on the native Android player, see the [Harmonia Mobile Subproject](#harmonia-mobile-subproject) section below. For ecosystem status, consult the master roadmap at [`docs/ROADMAP.md`](ROADMAP.md).

## Prerequisites

- **Python**: 3.11 or 3.12+
- **Database**: SQLite (built into Python)
- **External tools (optional for Phase 1)**: FFmpeg / FFprobe (for Phase 3 audio analysis)

---

## Setup

1. **Clone repository and enter directory**:
   ```powershell
   git clone <repository-url> harmonia
   cd harmonia
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

---

## Harmonia Mobile Subproject

Harmonia Mobile resides in the [`harmonia-mobile/`](../harmonia-mobile/) directory and is built using the Android Gradle plugin, Kotlin 2.0+, and Android SDK 35 (minSdk 26).

- **Project Directory:** `harmonia-mobile/`
- **Architecture & Implementation:** See [`docs/ROADMAP.md`](ROADMAP.md), [`docs/MOBILE_M4_ARCHITECTURE.md`](MOBILE_M4_ARCHITECTURE.md), [`docs/MOBILE_M5_ARCHITECTURE.md`](MOBILE_M5_ARCHITECTURE.md), and [`docs/knowledge/`](knowledge/README.md).
- **Gradle Tasks (via PowerShell / Gradle wrapper):**
  ```powershell
  cd harmonia-mobile
  # Run JVM unit tests
  .\gradlew.bat testDebugUnitTest
  # Assemble debug APK
  .\gradlew.bat assembleDebug
  # Assemble unsigned release APK (local development)
  .\gradlew.bat assembleRelease
  ```

- **Release Signing Configuration (M-REL.1):**
  Production release signing is configured via environment variables. In the absence of credentials, `./gradlew.bat assembleRelease` generates an explicitly unsigned APK (`app-release-unsigned.apk`) for local verification.

  Required environment variables for signed release packaging:
  - `HARMONIA_KEYSTORE_PATH`: Path to the `.jks` or `.keystore` file (absolute or relative to project root).
  - `HARMONIA_KEYSTORE_PASSWORD`: Password for the keystore.
  - `HARMONIA_KEY_ALIAS`: Alias of the signing key.
  - `HARMONIA_KEY_PASSWORD`: Password for the signing key.

  To build a signed release APK:
  ```powershell
  $env:HARMONIA_KEYSTORE_PATH = "C:\path\to\release.jks"
  $env:HARMONIA_KEYSTORE_PASSWORD = "keystore-password"
  $env:HARMONIA_KEY_ALIAS = "harmonia-key-alias"
  $env:HARMONIA_KEY_PASSWORD = "key-password"

  .\gradlew.bat assembleRelease
  ```

  To enforce that release signing is mandatory (failing if credentials are missing):
  ```powershell
  .\gradlew.bat assembleRelease -PrequireSigning=true
  ```

  > [!SECURITY]
  > Never hardcode or commit keystores (`*.jks`, `*.keystore`) or credential properties (`keystore.properties`) into version control. These patterns are gitignored.
