"""Unit tests for filesystem scanner and path parser."""

from __future__ import annotations

from pathlib import Path

from harmonia.infrastructure.filesystem.scanner import parse_path_metadata, scan_directory


def test_scan_directory_finds_files(temp_dir: Path) -> None:
    # Create test directory structure:
    # temp_dir/
    #   Artist/
    #     Album/
    #       01 - Song.mp3
    #       02 - Track.flac
    #       cover.jpg (non-music)
    #   .hidden/
    #     hidden.mp3 (hidden directory)
    #   .hidden.mp3 (hidden file)

    album_dir = temp_dir / "Pink Floyd" / "The Dark Side of the Moon"
    album_dir.mkdir(parents=True)
    (album_dir / "01 - Speak to Me.mp3").write_bytes(b"dummy mp3")
    (album_dir / "02 - Breathe.flac").write_bytes(b"dummy flac")
    (album_dir / "cover.jpg").write_bytes(b"dummy jpg")

    hidden_dir = temp_dir / ".hidden"
    hidden_dir.mkdir()
    (hidden_dir / "hidden.mp3").write_bytes(b"secret")
    (temp_dir / ".hidden.mp3").write_bytes(b"secret2")

    scanned = scan_directory(temp_dir, supported_formats=[".mp3", ".flac"])

    assert len(scanned) == 2
    filenames = [f.path.name for f in scanned]
    assert "01 - Speak to Me.mp3" in filenames
    assert "02 - Breathe.flac" in filenames
    assert "cover.jpg" not in filenames


def test_scan_directory_nonexistent(temp_dir: Path) -> None:
    non_existent = temp_dir / "nowhere"
    scanned = scan_directory(non_existent, supported_formats=[".mp3"])
    assert scanned == []


def test_parse_path_metadata() -> None:
    path = Path("/Music/Radiohead/OK Computer/01 - Airbag.mp3")
    meta = parse_path_metadata(path)
    assert meta["artist"] == "Radiohead"
    assert meta["album"] == "OK Computer"
    assert meta["title"] == "Airbag"
    assert meta["track_number"] == "01"


def test_parse_path_metadata_no_track_number() -> None:
    path = Path("/Music/Artist/Album/Bohemian Rhapsody.flac")
    meta = parse_path_metadata(path)
    assert meta["artist"] == "Artist"
    assert meta["album"] == "Album"
    assert meta["title"] == "Bohemian Rhapsody"
    assert meta["track_number"] is None
