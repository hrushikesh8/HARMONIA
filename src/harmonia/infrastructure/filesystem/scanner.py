"""Filesystem scanner — discovers music files in directories.

Walks directory trees and yields paths to supported audio files,
respecting configuration for supported formats and symlink following.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from harmonia.core.logging import get_logger

logger = get_logger(__name__)


@dataclass(frozen=True)
class ScannedFile:
    """A music file discovered during a directory scan."""

    path: Path
    size: int  # Bytes
    extension: str  # Lowercase, with leading dot (e.g., ".mp3")


def scan_directory(
    directory: Path,
    supported_formats: list[str],
    follow_symlinks: bool = False,
) -> list[ScannedFile]:
    """Recursively scan a directory for music files.

    Args:
        directory: Root directory to scan.
        supported_formats: List of extensions to include (e.g., [".mp3", ".flac"]).
        follow_symlinks: Whether to follow symbolic links.

    Returns:
        List of ScannedFile objects for all discovered music files,
        sorted by path for deterministic ordering.
    """
    if not directory.is_dir():
        logger.warning("Scan target is not a directory: %s", directory)
        return []

    # Normalize extensions to lowercase with leading dot.
    formats = {ext.lower() if ext.startswith(".") else f".{ext.lower()}" for ext in supported_formats}

    results: list[ScannedFile] = []
    skipped = 0

    logger.info("Scanning directory: %s", directory)

    visited_dirs: set[tuple[int, int]] = set()
    if follow_symlinks:
        try:
            root_stat = directory.stat()
            visited_dirs.add((root_stat.st_dev, root_stat.st_ino))
        except OSError:
            pass

    for root, dirs, files in os.walk(str(directory), followlinks=follow_symlinks):
        # Skip hidden directories.
        dirs[:] = [d for d in dirs if not d.startswith(".")]

        # Loop protection for recursive symlinks
        if follow_symlinks:
            pruned_dirs: list[str] = []
            for d in dirs:
                d_path = Path(root) / d
                try:
                    st = d_path.stat()
                    key = (st.st_dev, st.st_ino)
                    if key in visited_dirs:
                        logger.debug("Skipping recursive symlink loop in directory: %s", d_path)
                        continue
                    visited_dirs.add(key)
                    pruned_dirs.append(d)
                except OSError:
                    continue
            dirs[:] = pruned_dirs

        for filename in files:
            # Skip hidden files.
            if filename.startswith("."):
                continue

            file_path = Path(root) / filename
            extension = file_path.suffix.lower()

            if extension not in formats:
                skipped += 1
                continue

            try:
                stat = file_path.stat()
                results.append(
                    ScannedFile(
                        path=file_path,
                        size=stat.st_size,
                        extension=extension,
                    )
                )
            except OSError as e:
                logger.warning("Cannot stat file %s: %s", file_path, e)

    results.sort(key=lambda f: f.path)

    logger.info(
        "Scan complete: %d music files found, %d non-music files skipped",
        len(results),
        skipped,
    )
    return results


def parse_path_metadata(file_path: Path) -> dict[str, str | None]:
    """Extract metadata hints from a file's path.

    Tries common conventions:
    - ``Artist/Album/01 - Title.ext``
    - ``Artist/Album/01. Title.ext``
    - ``Artist - Album/01 - Title.ext``

    Returns:
        Dict with keys: "artist", "album", "title", "track_number".
        Values are None if not determinable.
    """
    result: dict[str, str | None] = {
        "artist": None,
        "album": None,
        "title": None,
        "track_number": None,
    }

    parts = file_path.parts
    stem = file_path.stem  # Filename without extension

    # Try to extract track number and title from filename.
    import re

    # Pattern: "01 - Title" or "01. Title" or "01 Title"
    match = re.match(r"^(\d{1,3})[\s.\-_]+(.+)$", stem)
    if match:
        result["track_number"] = match.group(1)
        result["title"] = match.group(2).strip()
    else:
        result["title"] = stem

    # Try to get album from parent directory.
    if len(parts) >= 2:
        result["album"] = parts[-2]

    # Try to get artist from grandparent directory.
    if len(parts) >= 3:
        result["artist"] = parts[-3]

    return result
