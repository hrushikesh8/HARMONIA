"""Unit tests for Harmonia catalog export and import-catalog CLI commands."""

from __future__ import annotations

import json
from typing import TYPE_CHECKING
from uuid import uuid4

from typer.testing import CliRunner

from harmonia.application.catalog_dto import (
    ArtistBundleItem,
    CatalogBundle,
    CatalogData,
    CatalogManifest,
    TrackBundleItem,
    compute_data_checksum,
)
from harmonia.cli.app import app
from harmonia.core.database import create_database_engine, create_session_factory
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from pathlib import Path

runner = CliRunner()


def test_cli_export_help() -> None:
    result = runner.invoke(app, ["export", "--help"])
    assert result.exit_code == 0
    assert "Export the library catalog" in result.stdout
    assert "--output" in result.stdout
    assert "--force" in result.stdout
    assert "--pretty" in result.stdout


def test_cli_import_catalog_help() -> None:
    result = runner.invoke(app, ["import-catalog", "--help"])
    assert result.exit_code == 0
    assert "Import a portable JSON catalog bundle" in result.stdout
    assert "--on-conflict" in result.stdout
    assert "--dry-run" in result.stdout


def test_cli_export_and_overwrite_protection(tmp_path: Path) -> None:
    test_db = tmp_path / "test.db"
    runner.invoke(app, ["init-db", "--db", str(test_db)])

    # Seed 1 track
    engine = create_database_engine(test_db)
    try:
        session_factory = create_session_factory(engine)
        with session_factory() as session:
            artist_repo = SqliteArtistRepository(session)
            track_repo = SqliteTrackRepository(session)
            artist = Artist(name="Slowdive")
            artist_repo.add(artist)
            track_repo.add(Track(title="Alison", artist_id=artist.id))
            session.commit()
    finally:
        engine.dispose()

    export_path = tmp_path / "slowdive_catalog.json"

    # 1. Successful export
    res1 = runner.invoke(app, ["export", "--db", str(test_db), "-o", str(export_path)])
    assert res1.exit_code == 0
    assert "Catalog bundle successfully exported" in res1.stdout
    assert export_path.exists()

    # Verify JSON content
    data = json.loads(export_path.read_text(encoding="utf-8"))
    assert data["manifest"]["entity_counts"]["tracks"] == 1
    assert data["data"]["tracks"][0]["title"] == "Alison"

    # 2. Existing file without --force fails with exit code 1
    res2 = runner.invoke(app, ["export", "--db", str(test_db), "-o", str(export_path)])
    assert res2.exit_code == 1
    assert "already exists" in " ".join(res2.output.split())

    # 3. Existing file with --force succeeds
    res3 = runner.invoke(app, ["export", "--db", str(test_db), "-o", str(export_path), "-f"])
    assert res3.exit_code == 0
    assert "Catalog bundle successfully exported" in res3.stdout


def test_cli_import_catalog_flow(tmp_path: Path) -> None:
    test_db = tmp_path / "import_test.db"
    runner.invoke(app, ["init-db", "--db", str(test_db)])

    # Create bundle file
    artist_id = uuid4()
    track_id = uuid4()
    data = CatalogData(
        artists=[ArtistBundleItem(id=artist_id, name="My Bloody Valentine")],
        tracks=[TrackBundleItem(id=track_id, title="Soon", artist_id=artist_id)],
    )
    manifest = CatalogManifest(
        schema_version="1.0.0",
        harmonia_version="0.1.0",
        exported_at="2026-10-17T10:00:00Z",  # type: ignore[arg-type]
        source_system="harmonia-desktop",
        entity_counts={"artists": 1, "tracks": 1},
    )
    checksum = compute_data_checksum(data)
    bundle = CatalogBundle(manifest=manifest, data=data, checksum=checksum)

    bundle_file = tmp_path / "mbv_bundle.json"
    bundle_file.write_text(json.dumps(bundle.model_dump(mode="json")), encoding="utf-8")

    # 1. Dry run
    res_dry = runner.invoke(app, ["import-catalog", str(bundle_file), "--db", str(test_db), "--dry-run"])
    assert res_dry.exit_code == 0
    assert "Dry-Run" in res_dry.stdout
    assert "Dry-run complete" in res_dry.stdout

    # Verify nothing was added
    engine = create_database_engine(test_db)
    try:
        session_factory = create_session_factory(engine)
        with session_factory() as session:
            assert SqliteTrackRepository(session).count() == 0
    finally:
        engine.dispose()

    # 2. Real import
    res_real = runner.invoke(app, ["import-catalog", str(bundle_file), "--db", str(test_db)])
    assert res_real.exit_code == 0
    assert "Import complete" in res_real.stdout
    assert "Successfully created 2 records" in res_real.stdout

    # Verify records in database
    engine2 = create_database_engine(test_db)
    try:
        session_factory2 = create_session_factory(engine2)
        with session_factory2() as session:
            assert SqliteTrackRepository(session).count() == 1
            t = SqliteTrackRepository(session).get_by_id(track_id)
            assert t is not None
            assert t.title == "Soon"
    finally:
        engine2.dispose()


def test_cli_import_invalid_conflict_policy(tmp_path: Path) -> None:
    bundle_file = tmp_path / "dummy.json"
    bundle_file.write_text("{}", encoding="utf-8")
    result = runner.invoke(app, ["import-catalog", str(bundle_file), "--on-conflict", "invalid_policy"])
    assert result.exit_code == 1
    assert "Invalid --on-conflict policy" in result.output


def test_cli_import_nonexistent_file(tmp_path: Path) -> None:
    non_file = tmp_path / "nonexistent.json"
    result = runner.invoke(app, ["import-catalog", str(non_file)])
    assert result.exit_code == 1
    assert "does not exist" in result.output
