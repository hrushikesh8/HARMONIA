"""Unit tests for Phase 2 CLI commands (search, reindex, duplicates)."""

from pathlib import Path
from unittest.mock import patch

from typer.testing import CliRunner

from harmonia.cli.app import app
from harmonia.core.database import create_database_engine, create_session_factory
from harmonia.infrastructure.audio.mutagen_reader import ExtractedMetadata
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)

runner = CliRunner()


def test_cli_search_and_reindex(temp_dir: Path) -> None:
    test_db = temp_dir / "harmonia_test.db"
    music_dir = temp_dir / "music"
    music_dir.mkdir()
    song = music_dir / "01 - Shine.mp3"
    song.write_bytes(b"dummy mp3 data shine")

    meta = ExtractedMetadata(
        title="Diamond",
        artist="Pink Floyd",
        album="Wish",
        genre="Prog Rock",
        duration_seconds=800.0,
    )

    # Initialize DB
    runner.invoke(app, ["init-db", "--db", str(test_db)])

    # Import
    with patch("harmonia.application.import_service.extract_metadata", return_value=meta):
        runner.invoke(app, ["import", str(music_dir), "--db", str(test_db)])

    # Search by title
    res_search = runner.invoke(app, ["search", "Diamond", "--db", str(test_db)])
    assert res_search.exit_code == 0
    assert "Diamond" in res_search.stdout
    assert "Pink Floyd" in res_search.stdout

    # Search with JSON format
    res_json = runner.invoke(app, ["search", "Diamond", "--format", "json", "--db", str(test_db)])
    assert res_json.exit_code == 0
    assert '"total_matches": 1' in res_json.stdout
    assert '"Diamond"' in res_json.stdout

    # Search with no matches
    res_none = runner.invoke(app, ["search", "Nonexistent", "--db", str(test_db)])
    assert res_none.exit_code == 0
    assert "No tracks found matching" in res_none.stdout

    # Reindex
    res_reindex = runner.invoke(app, ["reindex", "--db", str(test_db)])
    assert res_reindex.exit_code == 0
    assert "Rebuilt search index" in res_reindex.stdout


def test_cli_duplicates_workflow(temp_dir: Path) -> None:
    test_db = temp_dir / "harmonia_test.db"
    music_dir = temp_dir / "music"
    music_dir.mkdir()

    file1 = music_dir / "echoes1.mp3"
    file1.write_bytes(b"echoes mp3 version 1")

    file2 = music_dir / "echoes2.mp3"
    file2.write_bytes(b"echoes mp3 version 2")

    meta1 = ExtractedMetadata(
        title="Echoes",
        artist="Pink Floyd",
        album="Meddle",
        duration_seconds=1400.0,
    )
    meta2 = ExtractedMetadata(
        title="Echoes",
        artist="Pink Floyd",
        album="Meddle",
        duration_seconds=1401.0,
    )

    runner.invoke(app, ["init-db", "--db", str(test_db)])

    with patch("harmonia.application.import_service.extract_metadata", side_effect=[meta1, meta2]):
        runner.invoke(app, ["import", str(music_dir), "--db", str(test_db)])

    # List duplicates
    res_list = runner.invoke(app, ["duplicates", "list", "--db", str(test_db)])
    assert res_list.exit_code == 0
    assert "Probable Duplicate Candidates" in res_list.stdout
    assert "Echoes" in res_list.stdout

    engine = create_database_engine(test_db)
    with create_session_factory(engine)() as session:
        repo = SqliteDuplicateCandidateRepository(session)
        cands = repo.list_unresolved()
        assert len(cands) == 1
        cand_id_str = str(cands[0].id)
    engine.dispose()

    # Resolve as distinct
    res_resolve = runner.invoke(
        app, ["duplicates", "resolve", cand_id_str[:8], "--action", "distinct", "--db", str(test_db)]
    )
    assert res_resolve.exit_code == 0
    assert "marked as distinct" in res_resolve.stdout

    # Check list again is empty
    res_list_after = runner.invoke(app, ["duplicates", "list", "--db", str(test_db)])
    assert res_list_after.exit_code == 0
    assert "No unresolved duplicate candidates found" in res_list_after.stdout
