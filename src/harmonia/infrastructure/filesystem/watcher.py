"""Filesystem watcher with debouncing, stability checking, and background import integration."""

from __future__ import annotations

import os
import threading
import time
from collections.abc import Callable
from pathlib import Path
from typing import TYPE_CHECKING

from watchdog.events import FileSystemEvent, FileSystemEventHandler
from watchdog.observers import Observer

from harmonia.core.logging import get_logger
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

    from harmonia.application.dto import ImportResult

logger = get_logger("harmonia.filesystem.watcher")

IGNORE_EXTENSIONS = {
    ".tmp", ".temp", ".crdownload", ".part", ".swp",
    ".bak", ".old", ".orig", ".ds_store", ".ini",
}


def _is_file_truly_missing(path: Path) -> bool:
    """Determine if a file is truly missing, as opposed to inaccessible or restricted."""
    try:
        path.stat()
        return False
    except FileNotFoundError:
        # Check parent directories to ensure missing status is not due to parent permission error
        parent = path.parent
        while parent != parent.parent:
            try:
                parent.stat()
                break
            except FileNotFoundError:
                parent = parent.parent
            except PermissionError:
                logger.warning("Permission denied accessing parent of %s; skipping deletion", path.name)
                return False
            except OSError as exc:
                logger.warning("I/O error checking parent of %s: %s; skipping deletion", path.name, exc)
                return False
        return True
    except PermissionError:
        logger.warning("Permission denied accessing %s; skipping deletion", path.name)
        return False
    except OSError as exc:
        logger.warning("I/O error checking %s: %s; skipping deletion", path.name, exc)
        return False


class DebouncedEventHandler(FileSystemEventHandler):
    """Watchdog event handler that debounces and coalesces filesystem write bursts.

    Prevents multiple events from rapid file tag writes, downloads, or moves
    from triggering duplicate import storms. Employs bounded stability checks
    to ensure partially written or actively copied files settle before handing
    them to the scanner/import service.
    """

    def __init__(
        self,
        on_batch_ready: Callable[[set[Path]], None],
        supported_formats: list[str],
        debounce_delay: float = 2.0,
        stability_delay: float | None = None,
        max_stability_retries: int = 10,
        max_pending_paths: int = 10000,
        dir_contents_finder: Callable[[Path], list[Path]] | None = None,
        follow_symlinks: bool = False,
        roots_validator: Callable[[Path], bool] | None = None,
        on_overflow: Callable[[], None] | None = None,
    ) -> None:
        super().__init__()
        self._callback = on_batch_ready
        self._supported_formats = {
            fmt.lower() if fmt.startswith(".") else f".{fmt.lower()}" for fmt in supported_formats
        }
        self._debounce_delay = max(0.01, debounce_delay)
        if stability_delay is None:
            self._stability_delay = min(0.5, self._debounce_delay)
        else:
            self._stability_delay = max(0.0, stability_delay)
        self._max_stability_retries = max(1, max_stability_retries)
        self._max_pending_paths = max(1, max_pending_paths)
        self._dir_contents_finder = dir_contents_finder
        self._follow_symlinks = follow_symlinks
        self._roots_validator = roots_validator
        self._on_overflow = on_overflow

        self._lock = threading.Lock()
        self._pending_paths: dict[Path, float] = {}
        self._path_stats: dict[Path, tuple[int, float, int]] = {}  # path -> (size, mtime, check_count)
        self._overflow_flag = False

        self._stop_event = threading.Event()
        self._worker_thread = threading.Thread(
            target=self._debounce_worker,
            daemon=True,
            name="harmonia-watcher-debouncer",
        )
        self._worker_thread.start()

    def clear_pending(self) -> None:
        """Clear all pending paths, stats, and overflow flags."""
        with self._lock:
            self._pending_paths.clear()
            self._path_stats.clear()
            self._overflow_flag = False

    def on_any_event(self, event: FileSystemEvent) -> None:
        """Handle incoming watchdog filesystem events."""
        if event.is_directory:
            self._handle_directory_event(event)
            return

        src_path_str = getattr(event, "src_path", None)
        if src_path_str:
            self._record_path_if_relevant(Path(src_path_str))

        dest_path_str = getattr(event, "dest_path", None)
        if dest_path_str:
            self._record_path_if_relevant(Path(dest_path_str))

    def _handle_directory_event(self, event: FileSystemEvent) -> None:
        """Handle directory-level filesystem events safely."""
        event_type = getattr(event, "event_type", "")

        dest_path_str = getattr(event, "dest_path", None)
        if dest_path_str:
            dest_dir = Path(dest_path_str)
            if dest_dir.is_dir():
                self._record_directory_contents(dest_dir)

        src_path_str = getattr(event, "src_path", None)
        if not src_path_str:
            return

        src_dir = Path(src_path_str)
        if event_type in ("moved", "deleted"):
            if self._dir_contents_finder:
                try:
                    for catalog_path in self._dir_contents_finder(src_dir):
                        self._record_path_if_relevant(catalog_path)
                except Exception as exc:
                    logger.debug("Failed finding catalog paths under %s: %s", src_dir, exc)
        elif event_type in ("created", "modified") and src_dir.is_dir():
            self._record_directory_contents(src_dir)

    def _record_directory_contents(self, directory: Path) -> None:
        """Enumerate and record audio files within a discovered directory with loop protection."""
        if not directory.is_dir():
            return

        visited_dirs: set[tuple[int, int]] = set()
        try:
            root_stat = directory.stat()
            visited_dirs.add((root_stat.st_dev, root_stat.st_ino))
        except OSError:
            pass

        try:
            for root, dirs, files in os.walk(str(directory), followlinks=self._follow_symlinks):
                # Skip hidden directories
                dirs[:] = [d for d in dirs if not d.startswith(".")]

                # Symlink cycle protection
                if self._follow_symlinks:
                    pruned_dirs: list[str] = []
                    for d in dirs:
                        d_path = Path(root) / d
                        try:
                            st = d_path.stat()
                            key = (st.st_dev, st.st_ino)
                            if key in visited_dirs:
                                logger.debug("Skipping recursive symlink loop: %s", d_path)
                                continue
                            visited_dirs.add(key)
                            pruned_dirs.append(d)
                        except OSError:
                            continue
                    dirs[:] = pruned_dirs

                for filename in files:
                    if filename.startswith(".") or filename.startswith("~"):
                        continue
                    file_path = Path(root) / filename
                    if not self._follow_symlinks and file_path.is_symlink():
                        continue
                    self._record_path_if_relevant(file_path)
        except OSError as exc:
            logger.debug("Cannot enumerate directory %s: %s", directory, exc)

    def _is_relevant_path(self, path: Path) -> bool:
        """Determine if a path represents a supported, non-ignored audio file."""
        name = path.name.lower()
        if not name or name.startswith(".") or name.startswith("~"):
            return False

        # Exclude hidden directory components
        for part in path.parts[:-1]:
            if part.startswith(".") and part != ".":
                return False

        ext = path.suffix.lower()
        if not ext or ext in IGNORE_EXTENSIONS:
            return False

        if ext not in self._supported_formats:
            return False

        # Confinement check: verify path does not escape configured roots via symlinks
        if self._roots_validator:
            return self._roots_validator(path)

        return True

    def _record_path_if_relevant(self, path: Path) -> None:
        """Record path in debounce tracking if it matches target audio criteria."""
        if not self._is_relevant_path(path):
            return

        with self._lock:
            if len(self._pending_paths) >= self._max_pending_paths:
                if not self._overflow_flag:
                    self._overflow_flag = True
                    logger.warning(
                        "Filesystem watcher queue overflow (%d paths pending); throttling events",
                        len(self._pending_paths),
                    )
                    if self._on_overflow:
                        try:
                            self._on_overflow()
                        except Exception as exc:
                            logger.error("Error in on_overflow callback: %s", exc)
                return

            self._pending_paths[path] = time.monotonic()
            logger.debug("Filesystem change recorded for %s", path.name)

    def _debounce_worker(self) -> None:
        """Background thread checking for paths that have settled past debounce_delay."""
        while not self._stop_event.is_set():
            time.sleep(0.05)
            ready_batch: set[Path] = set()
            now = time.monotonic()

            with self._lock:
                for path, last_seen in list(self._pending_paths.items()):
                    if now - last_seen < self._debounce_delay:
                        continue

                    # If file no longer exists or is truly missing, ready for deletion reconciliation
                    if _is_file_truly_missing(path):
                        ready_batch.add(path)
                        del self._pending_paths[path]
                        self._path_stats.pop(path, None)
                        continue

                    # File exists on disk: perform bounded stability verification
                    try:
                        stat = path.stat()
                        current_size = stat.st_size
                        current_mtime = stat.st_mtime
                    except OSError:
                        current_size = -1
                        current_mtime = -1.0

                    # Check for exclusive write lock / unreadable state
                    is_locked = False
                    try:
                        with open(path, "rb") as f:
                            f.read(1)
                    except (PermissionError, OSError):
                        is_locked = True

                    prev_stat = self._path_stats.get(path)
                    if prev_stat is None:
                        # First observation after debounce window expired
                        self._path_stats[path] = (current_size, current_mtime, 1)
                        if self._stability_delay > 0:
                            # Keep pending for stability delay to verify size/mtime invariance
                            self._pending_paths[path] = now - self._debounce_delay + self._stability_delay
                            continue
                        # If stability_delay is 0, proceed immediately if not locked
                        if not is_locked and current_size > 0:
                            ready_batch.add(path)
                            del self._pending_paths[path]
                            del self._path_stats[path]
                            continue

                    prev_size, prev_mtime, check_count = self._path_stats.get(path, (current_size, current_mtime, 1))
                    size_or_mtime_changed = (current_size != prev_size) or (current_mtime != prev_mtime)

                    if is_locked or size_or_mtime_changed or (current_size <= 0 and check_count < self._max_stability_retries):
                        if check_count >= self._max_stability_retries:
                            logger.warning(
                                "File %s did not stabilize after %d checks; processing anyway",
                                path.name,
                                check_count,
                            )
                            ready_batch.add(path)
                            del self._pending_paths[path]
                            self._path_stats.pop(path, None)
                        else:
                            self._path_stats[path] = (current_size, current_mtime, check_count + 1)
                            # Reset last_seen to give additional time to settle
                            self._pending_paths[path] = now
                    else:
                        # File is stable, readable, and settled
                        ready_batch.add(path)
                        del self._pending_paths[path]
                        self._path_stats.pop(path, None)

                # Reset overflow flag if queue has drained below half threshold
                if self._overflow_flag and len(self._pending_paths) < (self._max_pending_paths // 2):
                    self._overflow_flag = False

            if ready_batch:
                try:
                    self._callback(ready_batch)
                except Exception as exc:
                    logger.error("Error executing debounce callback: %s", exc)

    def stop(self) -> None:
        """Stop worker thread cleanly."""
        self._stop_event.set()
        if self._worker_thread.is_alive():
            self._worker_thread.join(timeout=2.0)


class LibraryWatcher:
    """Watches configured library directories and triggers background incremental imports."""

    def __init__(
        self,
        watch_directories: list[Path],
        session_factory: Callable[[], Session],
        supported_formats: list[str],
        debounce_delay: float = 2.0,
        stability_delay: float | None = None,
        follow_symlinks: bool = False,
        observer_cls: type[Observer] | None = None,
    ) -> None:
        self._directories = [Path(d).resolve() for d in watch_directories]
        self._session_factory = session_factory
        self._supported_formats = supported_formats
        self._debounce_delay = debounce_delay
        if stability_delay is None:
            self._stability_delay = min(0.5, self._debounce_delay)
        else:
            self._stability_delay = max(0.0, stability_delay)
        self._follow_symlinks = follow_symlinks
        self._observer_cls = observer_cls or Observer
        self._observer: Observer | None = None
        self._handler: DebouncedEventHandler | None = None
        self._running = False
        self._process_lock = threading.Lock()
        self._overflow_event = threading.Event()

    @property
    def is_running(self) -> bool:
        """Return True if directory watching is currently active."""
        return self._running

    def check_roots_health(self) -> dict[Path, bool]:
        """Check accessibility of all configured watch directories."""
        return {d: d.is_dir() for d in self._directories}

    def start(self) -> None:
        """Start directory observers."""
        if self._running:
            return

        self._handler = DebouncedEventHandler(
            on_batch_ready=self._process_coalesced_batch,
            supported_formats=self._supported_formats,
            debounce_delay=self._debounce_delay,
            stability_delay=self._stability_delay,
            dir_contents_finder=self._find_catalog_paths_under_dir,
            follow_symlinks=self._follow_symlinks,
            roots_validator=self._is_within_configured_roots,
            on_overflow=self._handle_queue_overflow,
        )
        self._observer = self._observer_cls()

        watched_count = 0
        for directory in self._directories:
            if directory.is_dir():
                self._observer.schedule(self._handler, str(directory), recursive=True)
                logger.info("Watching library directory: %s", directory)
                watched_count += 1
            else:
                logger.warning("Library directory does not exist or is not a directory: %s", directory)

        if watched_count == 0 and self._directories:
            logger.warning("No valid library directories found to watch.")

        self._observer.start()
        self._running = True

    def stop(self) -> None:
        """Stop observer and background threads cleanly."""
        if not self._running:
            return

        if self._observer:
            self._observer.stop()
            self._observer.join(timeout=2.0)
            self._observer = None

        if self._handler:
            self._handler.stop()
            self._handler = None

        # Wait for any currently executing batch to complete database mutations
        acquired = self._process_lock.acquire(timeout=5.0)
        if acquired:
            self._process_lock.release()

        self._running = False
        logger.info("Library watcher stopped")

    def run_once(self, rescan: bool = False) -> ImportResult:
        """Perform a one-shot scan and reconciliation on all valid roots."""
        from harmonia.application.dto import ImportResult
        from harmonia.application.import_service import ImportService

        aggregated = ImportResult()
        with self._process_lock, self._session_factory() as session:
            import_service = ImportService(
                session=session,
                supported_formats=self._supported_formats,
                follow_symlinks=self._follow_symlinks,
            )
            track_repo = SqliteTrackRepository(session)

            # 1. Scan and import each accessible root
            for directory in self._directories:
                if not directory.is_dir():
                    logger.warning("Skipping inaccessible root during scan: %s", directory)
                    continue
                res = import_service.import_directory(directory, rescan=rescan)
                aggregated.total_scanned += res.total_scanned
                aggregated.imported += res.imported
                aggregated.skipped += res.skipped
                aggregated.failed += res.failed
                aggregated.errors.extend(res.errors)

            # 2. Reconcile missing files under accessible roots
            for directory in self._directories:
                if not directory.is_dir():
                    continue
                files_in_root = track_repo.get_files_by_directory_prefix(str(directory))
                for tf in files_in_root:
                    p = Path(tf.file_path)
                    root = self._find_root_for_path(p)
                    if root is None or not root.is_dir():
                        continue
                    if _is_file_truly_missing(p):
                        track_repo.delete_file(tf.id)
                        session.commit()
                        logger.info("Reconciliation removed missing file: %s", self._display_path(p))

        return aggregated

    def _handle_queue_overflow(self) -> None:
        """Signal queue overflow so batch worker triggers a full reconciliation."""
        self._overflow_event.set()

    def _is_within_configured_roots(self, path: Path) -> bool:
        """Check if path is contained within at least one configured library root."""
        return self._find_root_for_path(path) is not None

    def _find_catalog_paths_under_dir(self, dir_path: Path) -> list[Path]:
        """Find catalog track files located under a directory for reconciliation."""
        try:
            with self._session_factory() as session:
                track_repo = SqliteTrackRepository(session)
                files = track_repo.get_files_by_directory_prefix(str(dir_path))
                return [Path(f.file_path) for f in files]
        except Exception as exc:
            logger.debug("Error querying catalog paths under %s: %s", dir_path, exc)
            return []

    def _find_root_for_path(self, path: Path) -> Path | None:
        """Find the configured watch root containing the given path.

        Sorts configured roots by descending component count to prefer the most specific
        nested root (e.g. /music/nested over /music).
        """
        try:
            resolved = path.resolve()
        except OSError:
            resolved = path

        sorted_roots = sorted(self._directories, key=lambda r: len(r.parts), reverse=True)
        for root in sorted_roots:
            try:
                if resolved.is_relative_to(root.resolve()):
                    return root
            except (ValueError, OSError):
                pass
        return None

    def _display_path(self, path: Path) -> str:
        """Return a relative or base path string for privacy-conscious logging."""
        root = self._find_root_for_path(path)
        if root:
            try:
                return str(path.relative_to(root))
            except ValueError:
                pass
        return path.name

    def _process_coalesced_batch(self, paths: set[Path]) -> None:
        """Background worker executing incremental import on coalesced paths.

        To guarantee catalog integrity and avoid race conditions when files are moved:
        1. Separate paths into existing files and missing/deleted paths.
        2. Process existing files first via ImportService.import_file.
           When a file is moved/renamed, ImportService computes the SHA-256 hash,
           finds the previous TrackFile record whose file no longer exists,
           and updates its path in-place, preserving track ID and provenance.
        3. Process missing/deleted files second.
           Only delete a TrackFile if:
           a) It still exists in the catalog under this path (i.e. wasn't already updated to a new path in step 2).
           b) The configured root containing this path is still accessible. If the root drive is disconnected/missing, we do NOT delete catalog records.
           c) The file is truly missing (verified via _is_file_truly_missing, avoiding deletion on permission/IO errors).
        """
        from harmonia.application.import_service import ImportService

        if not paths:
            return

        with self._process_lock:
            if self._overflow_event.is_set():
                self._overflow_event.clear()
                logger.info("Queue overflow flagged; executing full library reconciliation")
                if self._handler:
                    self._handler.clear_pending()
                self.run_once(rescan=False)
                return

            logger.info("Processing coalesced batch of %d filesystem changes", len(paths))

            existing_files: list[Path] = []
            missing_paths: list[Path] = []
            for path in paths:
                if path.is_file():
                    existing_files.append(path)
                elif _is_file_truly_missing(path):
                    missing_paths.append(path)

            with self._session_factory() as session:
                import_service = ImportService(
                    session=session,
                    supported_formats=self._supported_formats,
                    follow_symlinks=self._follow_symlinks,
                )
                track_repo = SqliteTrackRepository(session)

                # 1. Process existing files first (creates, modifications, move destinations)
                for path in existing_files:
                    try:
                        import_service.import_file(path, rescan=True)
                        logger.debug("Imported/updated file: %s", self._display_path(path))
                    except Exception as exc:
                        logger.warning("Error importing watcher path %s: %s", self._display_path(path), exc)

                # 2. Process missing/deleted files second
                for path in missing_paths:
                    try:
                        root = self._find_root_for_path(path)
                        if root is None or not root.is_dir():
                            logger.warning(
                                "Skipping deletion of %s: library root is inaccessible or path outside watched roots",
                                self._display_path(path),
                            )
                            continue

                        # Verify file is still truly missing right before deletion mutation
                        if not _is_file_truly_missing(path):
                            continue

                        existing = track_repo.get_file_by_path(str(path))
                        if existing:
                            track_repo.delete_file(existing.id)
                            session.commit()
                            logger.info("Removed deleted file from library: %s", self._display_path(path))
                    except Exception as exc:
                        logger.warning("Error processing deletion for %s: %s", self._display_path(path), exc)
