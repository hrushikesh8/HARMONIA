"""Unit tests for Harmonia Phase 3 CLI commands (analyze, serve)."""

from __future__ import annotations

from typing import TYPE_CHECKING
from unittest.mock import patch

from typer.testing import CliRunner

from harmonia.cli.app import app

if TYPE_CHECKING:
    from pathlib import Path

runner = CliRunner()


def test_cli_analyze_help() -> None:
    result = runner.invoke(app, ["analyze", "--help"])
    assert result.exit_code == 0
    assert "Measure objective audio signal properties" in result.stdout
    assert "--all" in result.stdout
    assert "--force" in result.stdout


def test_cli_serve_help() -> None:
    result = runner.invoke(app, ["serve", "--help"])
    assert result.exit_code == 0
    assert "Launch the local HTTP REST API server" in result.stdout
    assert "--port" in result.stdout
    assert "--host" in result.stdout


def test_cli_analyze_empty_library(temp_dir: Path) -> None:
    test_db = temp_dir / "test_harmonia.db"
    runner.invoke(app, ["init-db", "--db", str(test_db)])

    result = runner.invoke(app, ["analyze", "--all", "--db", str(test_db)])
    assert result.exit_code == 0
    assert "No audio files found to analyze" in result.stdout


def test_cli_serve_mock_uvicorn() -> None:
    with patch("uvicorn.run") as mock_uvicorn:
        result = runner.invoke(app, ["serve", "--port", "8484"])
        assert result.exit_code == 0
        mock_uvicorn.assert_called_once()
