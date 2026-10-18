"""Unit tests for filesystem watcher debouncing, filtering, stability checks, and lifecycle."""

from __future__ import annotations

import threading
import time
from pathlib import Path
from unittest.mock import MagicMock, patch

from watchdog.events import (
    DirCreatedEvent,
    DirDeletedEvent,
    DirMovedEvent,
    FileCreatedEvent,
    FileModifiedEvent,
    FileMovedEvent,
)

from harmonia.infrastructure.filesystem.watcher import DebouncedEventHandler, LibraryWatcher


def test_debounce_coalescing_burst() -> None:
    received_batches: list[set[Path]] = []

    def on_batch(paths: set[Path]) -> None:
        received_batches.append(paths)

    handler = DebouncedEventHandler(
        on_batch_ready=on_batch,
        supported_formats=["flac", "mp3"],
        debounce_delay=0.15,
        stability_delay=0.05,
    )

    try:
        test_file = Path("/music/album/song.flac")

        # Simulate burst of 5 rapid write/modify events
        for _ in range(5):
            handler.on_any_event(FileModifiedEvent(str(test_file)))
            time.sleep(0.02)

        # Wait for debounce window to settle
        time.sleep(0.35)

        assert len(received_batches) == 1
        assert test_file in received_batches[0]
    finally:
        handler.stop()


def test_temporary_and_unsupported_file_filtering() -> None:
    received_batches: list[set[Path]] = []

    def on_batch(paths: set[Path]) -> None:
        received_batches.append(paths)

    handler = DebouncedEventHandler(
        on_batch_ready=on_batch,
        supported_formats=["flac", "mp3"],
        debounce_delay=0.1,
    )

    try:
        # Ignore temporary files
        handler.on_any_event(FileCreatedEvent("/music/song.tmp"))
        handler.on_any_event(FileCreatedEvent("/music/.hidden_song.flac"))
        handler.on_any_event(FileCreatedEvent("/music/~temp_song.mp3"))
        handler.on_any_event(FileCreatedEvent("/music/file.crdownload"))
        handler.on_any_event(FileCreatedEvent("/music/file.part"))

        # Ignore hidden directories
        handler.on_any_event(FileCreatedEvent("/music/.git/song.flac"))
        handler.on_any_event(FileCreatedEvent("/music/.cache/track.mp3"))

        # Ignore unsupported formats
        handler.on_any_event(FileCreatedEvent("/music/cover.jpg"))
        handler.on_any_event(FileCreatedEvent("/music/notes.txt"))

        time.sleep(0.25)
        assert len(received_batches) == 0
    finally:
        handler.stop()


def test_file_moved_event_handling() -> None:
    received_batches: list[set[Path]] = []

    def on_batch(paths: set[Path]) -> None:
        received_batches.append(paths)

    handler = DebouncedEventHandler(
        on_batch_ready=on_batch,
        supported_formats=["flac"],
        debounce_delay=0.1,
        stability_delay=0.02,
    )

    try:
        src = Path("/music/old_name.flac")
        dest = Path("/music/new_name.flac")

        handler.on_any_event(FileMovedEvent(str(src), str(dest)))
        time.sleep(0.3)

        assert len(received_batches) == 1
        assert dest in received_batches[0]
    finally:
        handler.stop()


def test_stability_check_active_write_waits_for_settle(tmp_path: Path) -> None:
    """An actively growing file must not be emitted until its size and mtime stabilize."""
    test_file = tmp_path / "recording.flac"
    test_file.write_bytes(b"initial chunk")

    received_batches: list[set[Path]] = []

    def on_batch(paths: set[Path]) -> None:
        received_batches.append(paths)

    handler = DebouncedEventHandler(
        on_batch_ready=on_batch,
        supported_formats=["flac"],
        debounce_delay=0.08,
        stability_delay=0.08,
    )

    try:
        handler.on_any_event(FileCreatedEvent(str(test_file)))

        # Simulate ongoing writes over 3 cycles
        for i in range(3):
            time.sleep(0.05)
            test_file.write_bytes(f"growing payload chunk {i}".encode())
            handler.on_any_event(FileModifiedEvent(str(test_file)))

        # Immediately after writing stops, should not have been emitted yet
        assert len(received_batches) == 0

        # Now wait for debounce and stability check to settle
        time.sleep(0.35)

        assert len(received_batches) == 1
        assert test_file in received_batches[0]
    finally:
        handler.stop()


def test_stability_check_locked_file_deferred(tmp_path: Path) -> None:
    """When a file is locked / unreadable, it is deferred until the lock releases."""
    test_file = tmp_path / "locked_track.flac"
    test_file.write_bytes(b"audio content")

    received_batches: list[set[Path]] = []

    def on_batch(paths: set[Path]) -> None:
        received_batches.append(paths)

    handler = DebouncedEventHandler(
        on_batch_ready=on_batch,
        supported_formats=["flac"],
        debounce_delay=0.05,
        stability_delay=0.05,
    )

    lock_active = True
    original_open = open

    def fake_open(file, *args, **kwargs):
        if str(file) == str(test_file) and lock_active:
            raise PermissionError("Simulated Windows file write-lock")
        return original_open(file, *args, **kwargs)

    try:
        with patch("builtins.open", side_effect=fake_open):
            handler.on_any_event(FileCreatedEvent(str(test_file)))
            time.sleep(0.15)
            # While locked, it must not be emitted
            assert len(received_batches) == 0

        # Now lock releases
        lock_active = False
        time.sleep(0.25)
        assert len(received_batches) == 1
        assert test_file in received_batches[0]
    finally:
        handler.stop()


def test_directory_events_handling(tmp_path: Path) -> None:
    """Directory creation and moves discover contained audio files."""
    album_dir = tmp_path / "Pink Floyd - The Wall"
    album_dir.mkdir()
    track1 = album_dir / "01 - In the Flesh.flac"
    track2 = album_dir / "02 - The Thin Ice.mp3"
    track1.write_bytes(b"data1")
    track2.write_bytes(b"data2")

    received_batches: list[set[Path]] = []

    def on_batch(paths: set[Path]) -> None:
        received_batches.append(paths)

    handler = DebouncedEventHandler(
        on_batch_ready=on_batch,
        supported_formats=["flac", "mp3"],
        debounce_delay=0.08,
        stability_delay=0.02,
    )

    try:
        # Simulate directory creation event
        handler.on_any_event(DirCreatedEvent(str(album_dir)))
        time.sleep(0.25)

        assert len(received_batches) == 1
        assert track1 in received_batches[0]
        assert track2 in received_batches[0]

        # Simulate directory move event
        renamed_album_dir = tmp_path / "Pink Floyd - The Wall Remastered"
        album_dir.rename(renamed_album_dir)
        handler.on_any_event(DirMovedEvent(str(album_dir), str(renamed_album_dir)))
        time.sleep(0.25)

        assert len(received_batches) == 2
        moved_track1 = renamed_album_dir / "01 - In the Flesh.flac"
        assert moved_track1 in received_batches[1]
    finally:
        handler.stop()


def test_directory_deleted_event_uses_catalog_finder() -> None:
    """Directory deletion queries catalog finder to queue tracks under that directory."""
    dir_path = Path("/music/DeletedAlbum")
    catalog_files = [dir_path / "song1.flac", dir_path / "song2.flac"]

    received_batches: list[set[Path]] = []

    def on_batch(paths: set[Path]) -> None:
        received_batches.append(paths)

    def mock_finder(d: Path) -> list[Path]:
        if d == dir_path:
            return catalog_files
        return []

    handler = DebouncedEventHandler(
        on_batch_ready=on_batch,
        supported_formats=["flac"],
        debounce_delay=0.08,
        stability_delay=0.02,
        dir_contents_finder=mock_finder,
    )

    try:
        handler.on_any_event(DirDeletedEvent(str(dir_path)))
        time.sleep(0.25)

        assert len(received_batches) == 1
        assert catalog_files[0] in received_batches[0]
        assert catalog_files[1] in received_batches[0]
    finally:
        handler.stop()


def test_queue_overflow_throttling() -> None:
    """Queue overflow triggers on_overflow callback and bounds memory accumulation."""
    overflow_called = []

    def on_overflow() -> None:
        overflow_called.append(True)

    handler = DebouncedEventHandler(
        on_batch_ready=lambda p: None,
        supported_formats=["flac"],
        debounce_delay=1.0,
        max_pending_paths=3,
        on_overflow=on_overflow,
    )

    try:
        for i in range(10):
            handler.on_any_event(FileCreatedEvent(f"/music/track{i}.flac"))

        assert len(handler._pending_paths) <= 3
        assert len(overflow_called) >= 1
    finally:
        handler.stop()


def test_roots_health_check(tmp_path: Path) -> None:
    """check_roots_health reports true/false for existing vs missing directories."""
    valid_dir = tmp_path / "music"
    valid_dir.mkdir()
    missing_dir = tmp_path / "unmounted_drive"

    watcher = LibraryWatcher(
        watch_directories=[valid_dir, missing_dir],
        session_factory=lambda: MagicMock(),
        supported_formats=["flac"],
    )

    health = watcher.check_roots_health()
    assert health[valid_dir.resolve()] is True
    assert health[missing_dir.resolve()] is False


def test_disconnected_root_skips_deletion(tmp_path: Path) -> None:
    """If a watch root becomes inaccessible, missing files under it are NOT deleted."""
    disconnected_root = tmp_path / "usb_drive"
    # Root does not exist (disconnected)

    session_mock = MagicMock()
    track_repo_mock = MagicMock()

    with patch("harmonia.infrastructure.filesystem.watcher.SqliteTrackRepository", return_value=track_repo_mock):
        watcher = LibraryWatcher(
            watch_directories=[disconnected_root],
            session_factory=lambda: session_mock,
            supported_formats=["flac"],
        )

        missing_file = disconnected_root / "song.flac"
        # Invoke coalesced batch on the missing file
        watcher._process_coalesced_batch({missing_file})

        # delete_file must NOT have been called because root is inaccessible
        track_repo_mock.delete_file.assert_not_called()


def test_order_of_batch_processing_existing_before_deleted(tmp_path: Path) -> None:
    """Existing files are processed before missing files to preserve moved file metadata."""
    watch_root = tmp_path / "music"
    watch_root.mkdir()

    existing_file = watch_root / "new_name.flac"
    existing_file.write_bytes(b"data")
    missing_file = watch_root / "old_name.flac"

    calls: list[str] = []

    mock_import_service = MagicMock()
    mock_import_service.import_file.side_effect = lambda p, **kw: calls.append(f"import:{p.name}")

    mock_track_repo = MagicMock()
    mock_track_repo.get_file_by_path.side_effect = lambda p: MagicMock(id="test-id")
    mock_track_repo.delete_file.side_effect = lambda fid: calls.append(f"delete:{fid}")

    session_mock = MagicMock()

    with (
        patch("harmonia.application.import_service.ImportService", return_value=mock_import_service),
        patch("harmonia.infrastructure.filesystem.watcher.SqliteTrackRepository", return_value=mock_track_repo),
    ):
        watcher = LibraryWatcher(
            watch_directories=[watch_root],
            session_factory=lambda: session_mock,
            supported_formats=["flac"],
        )

        # Batch contains both missing and existing file
        watcher._process_coalesced_batch({missing_file, existing_file})

        # Verify import occurred before delete
        assert len(calls) == 2
        assert calls[0] == "import:new_name.flac"
        assert calls[1] == "delete:test-id"


def test_watcher_lifecycle_idempotent(tmp_path: Path) -> None:
    """Calling start() and stop() multiple times is safe and idempotent."""
    watch_dir = tmp_path / "music"
    watch_dir.mkdir()

    mock_observer = MagicMock()

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=lambda: MagicMock(),
        supported_formats=["flac"],
        observer_cls=lambda: mock_observer,
    )

    watcher.start()
    assert watcher.is_running is True
    # Second start is a no-op
    watcher.start()
    assert watcher.is_running is True

    watcher.stop()
    assert watcher.is_running is False
    # Second stop is a no-op
    watcher.stop()
    assert watcher.is_running is False


def test_permission_error_skips_deletion(tmp_path: Path) -> None:
    """A file or directory raising PermissionError must not be deleted from catalog."""
    watch_dir = tmp_path / "music"
    watch_dir.mkdir()
    locked_file = watch_dir / "protected_song.flac"
    locked_file.write_bytes(b"content")

    track_repo_mock = MagicMock()
    session_mock = MagicMock()

    with (
        patch("harmonia.infrastructure.filesystem.watcher.SqliteTrackRepository", return_value=track_repo_mock),
        patch("harmonia.infrastructure.filesystem.watcher._is_file_truly_missing", return_value=False),
    ):
        watcher = LibraryWatcher(
            watch_directories=[watch_dir],
            session_factory=lambda: session_mock,
            supported_formats=["flac"],
        )

        watcher._process_coalesced_batch({locked_file})
        # Must not call delete_file because it is not truly missing
        track_repo_mock.delete_file.assert_not_called()


def test_sibling_directory_prefix_safety(tmp_path: Path) -> None:
    """Querying /music/rock must NOT match files in sibling /music/rock-old."""
    from harmonia.core.database import create_database_engine, create_session_factory, init_database
    from harmonia.domain.entities.track import Track
    from harmonia.domain.entities.track_file import TrackFile
    from harmonia.domain.values.audio_format import AudioFormat
    from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

    engine = create_database_engine(tmp_path / "test_sibling.db")
    init_database(engine)
    session_factory = create_session_factory(engine)

    rock_dir = tmp_path / "music" / "rock"
    rock_old_dir = tmp_path / "music" / "rock-old"
    rock_dir.mkdir(parents=True)
    rock_old_dir.mkdir(parents=True)

    rock_file = rock_dir / "track.flac"
    rock_old_file = rock_old_dir / "track.flac"

    with session_factory() as session:
        repo = SqliteTrackRepository(session)
        t1 = Track(title="Rock Track")
        t2 = Track(title="Old Rock Track")
        repo.add(t1)
        repo.add(t2)

        tf1 = TrackFile(track_id=t1.id, file_path=str(rock_file), format=AudioFormat.FLAC, file_size=100)
        tf2 = TrackFile(track_id=t2.id, file_path=str(rock_old_file), format=AudioFormat.FLAC, file_size=100)
        repo.add_file(tf1)
        repo.add_file(tf2)
        session.commit()

        # Query specifically for rock_dir
        matches = repo.get_files_by_directory_prefix(str(rock_dir))
        assert len(matches) == 1
        assert matches[0].file_path == str(rock_file)

        # Query specifically for rock_old_dir
        matches_old = repo.get_files_by_directory_prefix(str(rock_old_dir))
        assert len(matches_old) == 1
        assert matches_old[0].file_path == str(rock_old_file)


def test_nested_roots_prefer_most_specific_root(tmp_path: Path) -> None:
    """When nested roots are watched, the most specific root is chosen for containment."""
    parent_root = tmp_path / "music"
    nested_root = tmp_path / "music" / "external_mount"
    parent_root.mkdir()
    nested_root.mkdir()

    watcher = LibraryWatcher(
        watch_directories=[parent_root, nested_root],
        session_factory=lambda: MagicMock(),
        supported_formats=["flac"],
    )

    nested_file = nested_root / "song.flac"
    parent_file = parent_root / "song.flac"

    # Must match the deeper nested root
    assert watcher._find_root_for_path(nested_file) == nested_root.resolve()
    # Must match the parent root
    assert watcher._find_root_for_path(parent_file) == parent_root.resolve()


def test_symlink_loop_protection(tmp_path: Path) -> None:
    """A directory with cyclic symlinks completes scanning safely without infinite recursion."""
    from harmonia.infrastructure.filesystem.scanner import scan_directory

    music_dir = tmp_path / "music"
    music_dir.mkdir()
    song = music_dir / "track.flac"
    song.write_bytes(b"data")

    # Create cyclic symlink inside music_dir pointing back to music_dir
    loop_link = music_dir / "recursive_loop"
    try:
        loop_link.symlink_to(music_dir, target_is_directory=True)
    except (OSError, NotImplementedError):
        # On Windows without developer mode/admin rights, symlinks may be restricted
        return

    # Scan with follow_symlinks=True
    scanned = scan_directory(music_dir, supported_formats=["flac"], follow_symlinks=True)
    # Must find the real song without hanging or duplicating infinitely
    assert len(scanned) == 1
    assert scanned[0].path.name == "track.flac"


def test_symlink_outside_root_confinement(tmp_path: Path) -> None:
    """Symlinks pointing outside configured roots are rejected by roots_validator."""
    music_dir = tmp_path / "music"
    music_dir.mkdir()
    outside_dir = tmp_path / "outside_secret"
    outside_dir.mkdir()
    outside_file = outside_dir / "secret.flac"
    outside_file.write_bytes(b"secret audio")

    watcher = LibraryWatcher(
        watch_directories=[music_dir],
        session_factory=lambda: MagicMock(),
        supported_formats=["flac"],
    )

    # outside_file must fail confinement check
    assert watcher._is_within_configured_roots(outside_file) is False

    inside_file = music_dir / "album" / "track.flac"
    assert watcher._is_within_configured_roots(inside_file) is True


def test_callback_exception_does_not_kill_worker(tmp_path: Path) -> None:
    """An unhandled exception in batch callback does not terminate the debounce worker thread."""
    batches_received = []
    call_count = 0

    def faulty_callback(paths: set[Path]) -> None:
        nonlocal call_count
        call_count += 1
        batches_received.append(paths)
        if call_count == 1:
            raise RuntimeError("Unexpected failure during first batch processing")

    handler = DebouncedEventHandler(
        on_batch_ready=faulty_callback,
        supported_formats=["flac"],
        debounce_delay=0.05,
        stability_delay=0.02,
    )

    try:
        # First event triggers error in callback
        handler.on_any_event(FileCreatedEvent("/music/first.flac"))
        time.sleep(0.2)
        assert call_count == 1
        # Worker thread must still be alive!
        assert handler._worker_thread.is_alive() is True

        # Second event must still be processed successfully
        handler.on_any_event(FileCreatedEvent("/music/second.flac"))
        time.sleep(0.2)
        assert call_count == 2
        assert len(batches_received) == 2
    finally:
        handler.stop()


def test_repeated_overflow_handling() -> None:
    """Queue overflow triggers reconciliation and resets properly without getting stuck."""
    overflow_count = 0

    def on_overflow() -> None:
        nonlocal overflow_count
        overflow_count += 1

    handler = DebouncedEventHandler(
        on_batch_ready=lambda p: None,
        supported_formats=["flac"],
        debounce_delay=1.0,
        max_pending_paths=2,
        on_overflow=on_overflow,
    )

    try:
        # First burst -> triggers overflow
        handler.on_any_event(FileCreatedEvent("/music/1.flac"))
        handler.on_any_event(FileCreatedEvent("/music/2.flac"))
        handler.on_any_event(FileCreatedEvent("/music/3.flac"))
        assert overflow_count == 1

        # Queue cleared
        handler.clear_pending()
        assert len(handler._pending_paths) == 0
        assert handler._overflow_flag is False

        # Second burst -> triggers overflow again cleanly!
        handler.on_any_event(FileCreatedEvent("/music/4.flac"))
        handler.on_any_event(FileCreatedEvent("/music/5.flac"))
        handler.on_any_event(FileCreatedEvent("/music/6.flac"))
        assert overflow_count == 2
    finally:
        handler.stop()


def test_shutdown_waits_for_in_flight_batch(tmp_path: Path) -> None:
    """watcher.stop() blocks until an in-flight batch releases the process lock."""
    watch_dir = tmp_path / "music"
    watch_dir.mkdir()

    batch_finished = False

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=lambda: MagicMock(),
        supported_formats=["flac"],
    )

    # Acquire lock to simulate in-flight batch execution
    watcher._process_lock.acquire()

    def delayed_release():
        nonlocal batch_finished
        time.sleep(0.15)
        batch_finished = True
        watcher._process_lock.release()

    t = threading.Thread(target=delayed_release)
    t.start()

    watcher._running = True
    # stop() must wait for delayed_release to finish
    watcher.stop()
    assert batch_finished is True
    t.join()
