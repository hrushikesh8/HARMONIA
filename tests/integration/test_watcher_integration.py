"""Integration tests for LibraryWatcher with SQLite, ImportService, and Typer CLI."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import patch

import pytest
from typer.testing import CliRunner

from harmonia.cli.app import app
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.infrastructure.audio.mutagen_reader import ExtractedMetadata
from harmonia.infrastructure.filesystem.watcher import LibraryWatcher
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

runner = CliRunner()


@pytest.fixture
def test_setup(tmp_path: Path):
    db_path = tmp_path / "test_watcher.db"
    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    watch_dir = tmp_path / "music"
    watch_dir.mkdir()

    return session_factory, watch_dir, db_path


def test_library_watcher_file_deletion_and_lifecycle(test_setup) -> None:
    session_factory, watch_dir, _ = test_setup

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=session_factory,
        supported_formats=["flac", "mp3"],
        debounce_delay=0.1,
    )

    try:
        watcher.start()
        assert watcher.is_running is True

        # Directly invoke batch processing with a nonexistent file path
        # to test deletion handling deterministically
        missing_file = watch_dir / "removed_song.flac"
        watcher._process_coalesced_batch({missing_file})

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            assert track_repo.get_file_by_path(str(missing_file)) is None
    finally:
        watcher.stop()
        assert watcher.is_running is False


def test_library_watcher_file_creation_and_import(test_setup) -> None:
    session_factory, watch_dir, _ = test_setup

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=session_factory,
        supported_formats=["flac", "mp3"],
        debounce_delay=0.1,
    )

    audio_file = watch_dir / "track1.flac"
    audio_file.write_bytes(b"mock flac header and pcm data")

    mock_meta = ExtractedMetadata(
        title="Watcher Song",
        artist="Watcher Artist",
        album="Watcher Album",
        duration_seconds=210.0,
        bitrate=1000,
        sample_rate=44100,
        channels=2,
    )

    try:
        watcher.start()
        with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
            watcher._process_coalesced_batch({audio_file})

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            tf = track_repo.get_file_by_path(str(audio_file))
            assert tf is not None
            assert tf.file_size == len(b"mock flac header and pcm data")
            track = track_repo.get_by_id(tf.track_id)
            assert track is not None
            assert track.title == "Watcher Song"
    finally:
        watcher.stop()


def test_library_watcher_file_move_reassociates_track_file(test_setup) -> None:
    session_factory, watch_dir, _ = test_setup

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=session_factory,
        supported_formats=["flac", "mp3"],
        debounce_delay=0.1,
    )

    old_file = watch_dir / "original.flac"
    old_file.write_bytes(b"same audio content across moves")

    mock_meta = ExtractedMetadata(
        title="Persistent Track",
        artist="Artist",
        album="Album",
        duration_seconds=180.0,
    )

    with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
        # 1. Initial import of old_file
        watcher._process_coalesced_batch({old_file})

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            tf_initial = track_repo.get_file_by_path(str(old_file))
            assert tf_initial is not None
            original_file_id = tf_initial.id
            original_track_id = tf_initial.track_id

        # 2. Simulate file move: old_file deleted, new_file created with identical bytes
        new_file = watch_dir / "renamed.flac"
        old_file.rename(new_file)

        # Batch contains both the old missing file and new existing file
        watcher._process_coalesced_batch({old_file, new_file})

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            # Old path must no longer exist in catalog
            assert track_repo.get_file_by_path(str(old_file)) is None

            # New path exists with preserved file ID and track ID
            tf_updated = track_repo.get_file_by_path(str(new_file))
            assert tf_updated is not None
            assert tf_updated.id == original_file_id
            assert tf_updated.track_id == original_track_id


def test_library_watcher_run_once_reconciliation(test_setup) -> None:
    session_factory, watch_dir, _ = test_setup

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=session_factory,
        supported_formats=["flac"],
    )

    file1 = watch_dir / "song1.flac"
    file2 = watch_dir / "song2.flac"
    file1.write_bytes(b"audio data 1")
    file2.write_bytes(b"audio data 2")

    mock_meta = ExtractedMetadata(title="Song", artist="Artist", album="Album")

    with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
        # 1. First run_once imports both files
        result1 = watcher.run_once()
        assert result1.imported == 2
        assert result1.total_scanned == 2

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            assert track_repo.count() == 2

        # 2. Remove file1 physically from disk
        file1.unlink()

        # 3. Second run_once reconciles and deletes orphaned track file
        result2 = watcher.run_once()
        assert result2.total_scanned == 1
        assert result2.skipped == 1

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            assert track_repo.get_file_by_path(str(file1)) is None
            assert track_repo.get_file_by_path(str(file2)) is not None


def test_cli_watch_help() -> None:
    result = runner.invoke(app, ["watch", "--help"])
    assert result.exit_code == 0
    assert "Watch library directories" in result.stdout
    assert "--once" in result.stdout
    assert "--debounce" in result.stdout
    assert "--db" in result.stdout


def test_cli_watch_no_roots_error(tmp_path: Path) -> None:
    empty_config = tmp_path / "empty.toml"
    empty_config.write_text("[database]\npath='test.db'\n[library]\nmusic_dirs=[]\n")

    result = runner.invoke(app, ["watch", "--config", str(empty_config)])
    assert result.exit_code == 1
    assert "No music directories specified" in result.stdout


def test_cli_watch_invalid_roots_error(tmp_path: Path) -> None:
    nonexistent = tmp_path / "does_not_exist"
    result = runner.invoke(app, ["watch", str(nonexistent)])
    assert result.exit_code == 1
    assert "None of the specified watch directories exist" in result.stdout


def test_cli_watch_once_mode(test_setup) -> None:
    _, watch_dir, db_path = test_setup

    file1 = watch_dir / "album_song.flac"
    file1.write_bytes(b"flac data")

    mock_meta = ExtractedMetadata(title="CLI Song", artist="CLI Artist", album="CLI Album")

    with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
        result = runner.invoke(
            app,
            ["watch", str(watch_dir), "--once", "--db", str(db_path)],
        )
        assert result.exit_code == 0
        assert "Reconciliation Summary" in result.stdout
        assert "Files Scanned" in result.stdout


def test_library_watcher_move_separate_batches(test_setup) -> None:
    """When move events arrive in separate batches, track identity is preserved."""
    session_factory, watch_dir, _ = test_setup

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=session_factory,
        supported_formats=["flac"],
        debounce_delay=0.1,
    )

    old_file = watch_dir / "step1.flac"
    old_file.write_bytes(b"content across batches")

    mock_meta = ExtractedMetadata(title="Batch Move Track", artist="Artist", album="Album")

    with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
        # 1. Initial import
        watcher._process_coalesced_batch({old_file})
        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            tf = track_repo.get_file_by_path(str(old_file))
            assert tf is not None
            orig_id = tf.id
            orig_track_id = tf.track_id

        # 2. Rename on disk
        new_file = watch_dir / "step2.flac"
        old_file.rename(new_file)

        # Batch A: Destination arrives first
        watcher._process_coalesced_batch({new_file})
        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            tf_new = track_repo.get_file_by_path(str(new_file))
            assert tf_new is not None
            assert tf_new.id == orig_id
            assert tf_new.track_id == orig_track_id

        # Batch B: Source arrives second
        watcher._process_coalesced_batch({old_file})
        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            # Old path is safely absent, new path remains
            assert track_repo.get_file_by_path(str(old_file)) is None
            assert track_repo.get_file_by_path(str(new_file)) is not None


def test_library_watcher_duplicate_files_separate_moves(test_setup) -> None:
    """Moving one duplicate file does not delete or corrupt other duplicate copies."""
    session_factory, watch_dir, _ = test_setup

    watcher = LibraryWatcher(
        watch_directories=[watch_dir],
        session_factory=session_factory,
        supported_formats=["flac"],
        debounce_delay=0.1,
    )

    copy1 = watch_dir / "copy1.flac"
    copy2 = watch_dir / "copy2.flac"
    copy1.write_bytes(b"identical duplicate audio content")
    copy2.write_bytes(b"identical duplicate audio content")

    mock_meta = ExtractedMetadata(title="Dup Song", artist="Artist", album="Album")

    with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
        # Initial import of both copies
        watcher._process_coalesced_batch({copy1, copy2})

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            tf1 = track_repo.get_file_by_path(str(copy1))
            tf2 = track_repo.get_file_by_path(str(copy2))
            assert tf1 is not None
            assert tf2 is not None
            assert tf1.track_id == tf2.track_id

        # Now move copy1 to renamed_copy1
        renamed1 = watch_dir / "renamed_copy1.flac"
        copy1.rename(renamed1)

        watcher._process_coalesced_batch({copy1, renamed1})

        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            # copy1 is gone
            assert track_repo.get_file_by_path(str(copy1)) is None
            # renamed1 exists
            assert track_repo.get_file_by_path(str(renamed1)) is not None
            # copy2 remains completely intact!
            assert track_repo.get_file_by_path(str(copy2)) is not None
