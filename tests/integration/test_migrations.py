"""Test database migrations."""

from pathlib import Path

import sqlalchemy as sa
from alembic import command
from alembic.config import Config


def test_alembic_migrations(temp_dir: Path) -> None:
    db_file = temp_dir / "migration_test.db"
    db_url = f"sqlite:///{db_file}"

    alembic_cfg = Config("alembic.ini")
    alembic_cfg.set_main_option("sqlalchemy.url", db_url)

    # 1. Upgrade to head
    command.upgrade(alembic_cfg, "head")

    # Verify tables and columns
    engine = sa.create_engine(db_url)
    inspector = sa.inspect(engine)
    tables = inspector.get_table_names()

    assert "artists" in tables
    assert "albums" in tables
    assert "tracks" in tables
    assert "track_files" in tables
    assert "tags" in tables
    assert "metadata_fields" in tables
    assert "duplicate_candidates" in tables
    assert "audio_analyses" in tables

    # Verify added columns
    album_cols = [c["name"] for c in inspector.get_columns("albums")]
    assert "art_hash" in album_cols

    track_file_cols = [c["name"] for c in inspector.get_columns("track_files")]
    assert "file_mtime" in track_file_cols
    assert "channel_layout" in track_file_cols
    assert "encoder" in track_file_cols

    # Verify FTS virtual table
    with engine.connect() as conn:
        res = conn.execute(sa.text("SELECT count(*) FROM tracks_fts")).scalar()
        assert res == 0

    # 2. Downgrade to 001_initial_schema
    command.downgrade(alembic_cfg, "001_initial_schema")
    inspector = sa.inspect(engine)
    tables = inspector.get_table_names()
    assert "metadata_fields" not in tables
    assert "duplicate_candidates" not in tables

    # 3. Upgrade back to head
    command.upgrade(alembic_cfg, "head")
    inspector = sa.inspect(engine)
    tables = inspector.get_table_names()
    assert "metadata_fields" in tables
    assert "duplicate_candidates" in tables

    engine.dispose()
