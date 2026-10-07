"""Unit and smoke tests for Harmonia Typer CLI."""

from __future__ import annotations

from typing import TYPE_CHECKING
from unittest.mock import patch

from typer.testing import CliRunner

from harmonia import __version__
from harmonia.cli.app import app
from harmonia.infrastructure.audio.mutagen_reader import ExtractedMetadata

if TYPE_CHECKING:
    from pathlib import Path

runner = CliRunner()


def test_cli_version() -> None:
    result = runner.invoke(app, ["--version"])
    assert result.exit_code == 0
    assert __version__ in result.stdout


def test_cli_help() -> None:
    result = runner.invoke(app, ["--help"])
    assert result.exit_code == 0
    assert "Harmonia" in result.stdout
    assert "import" in result.stdout
    assert "list" in result.stdout


def test_cli_init_db(temp_dir: Path) -> None:
    test_db = temp_dir / "test_harmonia.db"
    result = runner.invoke(app, ["init-db", "--db", str(test_db)])
    assert result.exit_code == 0
    assert "Database initialized successfully" in result.stdout
    assert test_db.exists()


def test_cli_info_and_list_empty(temp_dir: Path) -> None:
    test_db = temp_dir / "test_harmonia.db"
    # Init first
    runner.invoke(app, ["init-db", "--db", str(test_db)])

    info_res = runner.invoke(app, ["info", "--db", str(test_db)])
    assert info_res.exit_code == 0
    assert "Tracks" in info_res.stdout

    list_res = runner.invoke(app, ["list", "tracks", "--db", str(test_db)])
    assert list_res.exit_code == 0
    assert "No tracks found" in list_res.stdout


def test_cli_import_and_query_flow(temp_dir: Path) -> None:
    test_db = temp_dir / "test_harmonia.db"
    music_dir = temp_dir / "music"
    music_dir.mkdir()
    song = music_dir / "01 - Test.mp3"
    song.write_bytes(b"dummy mp3 data")

    fake_meta = ExtractedMetadata(
        title="Test Song",
        artist="Test Artist",
        album="Test Album",
        track_number=1,
        year=2024,
        genre="Synthwave",
        duration_seconds=210.0,
        bitrate=320,
        sample_rate=44100,
        codec="mp3",
    )

    with patch("harmonia.application.import_service.extract_metadata", return_value=fake_meta):
        import_res = runner.invoke(app, ["import", str(music_dir), "--db", str(test_db)])
        assert import_res.exit_code == 0
        assert "Import Summary" in import_res.stdout

    # Now verify with list
    list_res = runner.invoke(app, ["list", "tracks", "--db", str(test_db)])
    assert list_res.exit_code == 0
    assert "Test Song" in list_res.stdout
    assert "Artist" in list_res.stdout
