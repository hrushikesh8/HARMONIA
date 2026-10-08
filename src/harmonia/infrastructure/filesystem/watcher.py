"""Filesystem watcher with debouncing, burst coalescing, and background import worker."""

from __future__ import annotations

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

logger = get_logger("harmonia.filesystem.watcher")

IGNORE_EXTENSIONS = {".tmp", ".temp", ".crdownload", ".part", ".swp"}


class DebouncedEventHandler(FileSystemEventHandler):
    """Watchdog event handler that debounces and coalesces filesystem write bursts.

    Prevents multiple events from rapid file tag writes, downloads, or moves
    from triggering duplicate import storms.
    """

    def __init__(
        self,
        on_batch_ready: Callable[[set[Path]], None],
        supported_formats: list[str],
        debounce_delay: float = 2.0,
    ) -> None:
        super().__init__()
        self._callback = on_batch_ready
        self._supported_formats = {
            fmt if fmt.startswith(".") else f".{fmt}" for fmt in supported_formats
        }
        self._debounce_delay = debounce_delay
        self._lock = threading.Lock()
        self._pending_paths: dict[Path, float] = {}
        self._stop_event = threading.Event()
        self._worker_thread = threading.Thread(
            target=self._debounce_worker,
            daemon=True,
            name="harmonia-watcher-debouncer",
        )
        self._worker_thread.start()

    def on_any_event(self, event: FileSystemEvent) -> None:
        if event.is_directory:
            return

        # Handle source path
        src_path = Path(event.src_path)
        self._record_path_if_relevant(src_path)

        # Handle destination path for moves/renames
        dest_path_str = getattr(event, "dest_path", None)
        if dest_path_str:
            self._record_path_if_relevant(Path(dest_path_str))

    def _record_path_if_relevant(self, path: Path) -> None:
        name = path.name.lower()
        if name.startswith(".") or name.startswith("~"):
            return

        ext = path.suffix.lower()
        if ext in IGNORE_EXTENSIONS:
            return

        if ext not in self._supported_formats:
            return

        with self._lock:
            self._pending_paths[path] = time.monotonic()
            logger.debug("Filesystem change recorded for %s", path)

    def _debounce_worker(self) -> None:
        """Background thread checking for paths that have settled past debounce_delay."""
        while not self._stop_event.is_set():
            time.sleep(0.1)
            ready_batch: set[Path] = set()
            now = time.monotonic()

            with self._lock:
                for path, last_seen in list(self._pending_paths.items()):
                    if now - last_seen >= self._debounce_delay:
                        ready_batch.add(path)
                        del self._pending_paths[path]

            if ready_batch:
                try:
                    self._callback(ready_batch)
                except Exception as exc:
                    logger.error("Error executing debounce callback: %s", exc)

    def stop(self) -> None:
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
    ) -> None:
        self._directories = [Path(d) for d in watch_directories]
        self._session_factory = session_factory
        self._supported_formats = supported_formats
        self._debounce_delay = debounce_delay
        self._observer: Observer | None = None
        self._handler: DebouncedEventHandler | None = None
        self._running = False

    def start(self) -> None:
        """Start directory observers."""
        if self._running:
            return

        self._handler = DebouncedEventHandler(
            on_batch_ready=self._process_coalesced_batch,
            supported_formats=self._supported_formats,
            debounce_delay=self._debounce_delay,
        )
        self._observer = Observer()

        for directory in self._directories:
            if directory.is_dir():
                self._observer.schedule(self._handler, str(directory), recursive=True)
                logger.info("Watching library directory: %s", directory)

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

        self._running = False
        logger.info("Library watcher stopped")

    def _process_coalesced_batch(self, paths: set[Path]) -> None:
        """Background worker executing incremental import on coalesced paths.

        Uses an independent database session so the playback thread is never blocked.
        """
        from harmonia.application.import_service import ImportService

        logger.info("Processing coalesced batch of %d filesystem changes", len(paths))
        with self._session_factory() as session:
            import_service = ImportService(
                session=session,
                supported_formats=self._supported_formats,
            )
            track_repo = SqliteTrackRepository(session)

            for path in paths:
                try:
                    if path.is_file():
                        # Create or modified file: run incremental import
                        import_service.import_file(path, rescan=True)
                    elif not path.exists():
                        # Deleted file: remove physical TrackFile record
                        existing = track_repo.get_file_by_path(str(path))
                        if existing:
                            track_repo.delete_file(existing.id)
                            session.commit()
                            logger.info("Removed deleted file from library: %s", path)
                except Exception as exc:
                    logger.warning("Error processing watcher path %s: %s", path, exc)
