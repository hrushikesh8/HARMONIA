"""Integration tests for LibraryWatcher with SQLite and ImportService."""

from __future__ import annotations

from pathlib import Path

import pytest

from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.infrastructure.filesystem.watcher import LibraryWatcher
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def test_setup(tmp_path: Path):
    db_path = tmp_path / "test_watcher.db"
    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    watch_dir = tmp_path / "music"
    watch_dir.mkdir()

    return session_factory, watch_dir


def test_library_watcher_file_deletion_and_lifecycle(test_setup) -> None:
    session_factory, watch_dir = test_setup

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=session_factory,
        supported_formats=["flac", "mp3"],
        debounce_delay=0.1,
    )

    try:
        watcher.start()
        assert watcher._running is True

        # Directly invoke batch processing with a nonexistent file path
        # to test deletion handling deterministically
        missing_file = watch_dir / "removed_song.flac"
        watcher._process_coalesced_batch({missing_file})

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            assert track_repo.get_file_by_path(str(missing_file)) is None
    finally:
        watcher.stop()
        assert watcher._running is False
