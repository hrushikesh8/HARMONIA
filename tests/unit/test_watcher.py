"""Unit tests for filesystem watcher debouncing, filtering, and coalescing."""

from __future__ import annotations

import time
from pathlib import Path

from watchdog.events import (
    FileCreatedEvent,
    FileModifiedEvent,
    FileMovedEvent,
)

from harmonia.infrastructure.filesystem.watcher import DebouncedEventHandler


def test_debounce_coalescing_burst() -> None:
    received_batches: list[set[Path]] = []

    def on_batch(paths: set[Path]) -> None:
        received_batches.append(paths)

    handler = DebouncedEventHandler(
        on_batch_ready=on_batch,
        supported_formats=["flac", "mp3"],
        debounce_delay=0.15,  # Short debounce window for testing
    )

    try:
        test_file = Path("/music/album/song.flac")

        # Simulate burst of 5 rapid write/modify events
        for _ in range(5):
            handler.on_any_event(FileModifiedEvent(str(test_file)))
            time.sleep(0.02)

        # Wait for debounce window to settle
        time.sleep(0.3)

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

        # Ignore unsupported formats
        handler.on_any_event(FileCreatedEvent("/music/cover.jpg"))
        handler.on_any_event(FileCreatedEvent("/music/notes.txt"))

        time.sleep(0.2)
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
    )

    try:
        src = Path("/music/old_name.flac")
        dest = Path("/music/new_name.flac")

        handler.on_any_event(FileMovedEvent(str(src), str(dest)))
        time.sleep(0.25)

        assert len(received_batches) == 1
        assert dest in received_batches[0]
    finally:
        handler.stop()
