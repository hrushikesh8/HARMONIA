# Harmonia Desktop Filesystem Watcher (Milestone D5-C)

## 1. Overview & Architecture

The Harmonia filesystem watcher provides automated, reliable monitoring of user music library roots. It bridges operating system filesystem events (`watchdog`) with Harmonia's existing scanner, `ImportService`, and SQLite catalog infrastructure without modifying established catalog semantics, file safety guarantees, or local-first architecture.

```
                      OS Filesystem Events
                               │
                               ▼
                   ┌───────────────────────┐
                   │ DebouncedEventHandler │
                   └───────────┬───────────┘
                               │ (Debounce 2.0s + Stability Check)
                               ▼
                    ┌─────────────────────┐
                    │   LibraryWatcher    │
                    └──────────┬──────────┘
                               │
               ┌───────────────┴───────────────┐
               ▼                               ▼
       Existing Files                  Missing / Deleted
  (Created, Modified, Moves)                (Deletions)
               │                               │
               ▼                               ▼
    ImportService.import_file       SqliteTrackRepository
  (Hash matching re-associates      (Verified root accessibility
    moved files before delete)       before record deletion)
```

### Key Architectural Tenets
- **Single Ingestion Pipeline:** Reuses `ImportService` directly. No secondary or divergent ingestion logic.
- **Ephemeral State:** Watcher state (pending queues, file timestamps, stability counters) is completely in-memory and ephemeral. No new database tables or schema migrations are created.
- **Safe Transaction Boundaries:** All catalog mutations execute through scoped database sessions protected by an internal process lock to prevent concurrent database write conflicts.

---

## 2. CLI Interface & Usage

The filesystem watcher is exposed via the standalone `harmonia watch` command:

```bash
harmonia watch [DIRECTORIES...] [OPTIONS]
```

### Options

| Flag | Type | Default | Description |
|---|---|---|---|
| `DIRECTORIES` | `Path...` (args) | `config.music_dirs` | One or more directory paths to watch. |
| `--once` | `bool` | `False` | Run a one-shot scan and reconciliation on library roots, then exit. |
| `--debounce` | `float` | `2.0` | Debounce window in seconds for coalescing filesystem event bursts. |
| `--rescan` | `bool` | `False` | Force re-checking of all files, bypassing fast mtime/size skip. |
| `--config`, `-c` | `Path` | `config/default.toml` | Path to custom configuration file. |
| `--db` | `Path` | `data/harmonia.db` | Path to target SQLite database. |
| `--symlinks / --no-symlinks` | `bool` | `False` | Follow symbolic links during watch and scan operations. |

### Modes of Operation

1. **Continuous Watch Mode (Default):**
   ```bash
   harmonia watch /path/to/music
   ```
   Monitors the specified directories recursively. New, modified, renamed, and deleted tracks are automatically reconciled in the background. Press `Ctrl+C` to stop; signal handlers (`SIGINT`, `SIGTERM`) ensure clean termination of all threads and database sessions.

2. **One-Shot Reconciliation Mode (`--once`):**
   ```bash
   harmonia watch /path/to/music --once
   ```
   Performs a complete library reconciliation across configured roots: imports new/modified files and cleans up orphaned records for files that have been deleted from disk. Returns a summary table and exits with code 0.

---

## 3. Event Processing & Resilience

### Bounded Stability Checks
To prevent partially written or actively copied files (e.g. slow network transfers, browser downloads, or large FLAC copies) from corrupting the catalog:
- Files must remain unchanged in both `size` and `mtime` across a stability delay (default `0.5s`) after the initial debounce period expires.
- Read lock verification (`open(path, 'rb')`) verifies that another process does not hold an exclusive write lock.
- Bounded retries (default 10 checks) prevent indefinite stalls while allowing actively written files to settle.

### Safe Directory Moves & Deletions
- **Existing Files Processed First:** In any coalesced batch, existing files (creates, modifications, move destinations) are processed before missing files. When a track is moved or renamed, `ImportService` computes the SHA-256 hash, matches the existing `TrackFile` record whose file no longer exists, and re-associates the path in-place—preserving track UUIDs, listening history, and playlist entries.
- **Disconnected Root Protection:** If a watched root becomes inaccessible (e.g., an external USB drive or network share is disconnected), the watcher refuses to delete catalog records belonging to that root.
- **Directory Event Discovery:** Directory creation and move events automatically enumerate child audio files and schedule them for ingestion.

### Noise & Temporary File Filtering
The handler ignores:
- Hidden files and folders (names starting with `.` or `~`, such as `.git`, `.DS_Store`).
- Temporary download and editor files (`.tmp`, `.temp`, `.crdownload`, `.part`, `.swp`, `.bak`).
- Non-audio file extensions not present in `config.import.supported_formats`.

### Queue Bounding
The pending event queue is bounded (`max_pending_paths = 10,000`). If a catastrophic event burst exceeds this threshold, incoming events are throttled and a full reconciliation scan is scheduled once the queue settles.

---

## 4. Operational Limitations

1. **Non-Notifying Network Filesystems:** Some network shares (e.g., certain NFS/SMB mounts) do not emit kernel-level filesystem notifications. On such filesystems, run `harmonia watch --once` or scheduled periodic imports.
2. **Metadata Tag Writes in Place:** Editing tags on audio files generates in-place file modification events. The watcher debounces these to prevent multiple redundant imports.
3. **OS File Descriptor Limits:** On Linux, `watchdog` uses `inotify`. Monitoring massive directory hierarchies (e.g., 500,000+ folders) may require increasing `fs.inotify.max_user_watches`.
