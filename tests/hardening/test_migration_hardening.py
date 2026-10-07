"""Hardening Tests #1 & #2: Database Migration Integrity and Round-Trip.

Validates:
- Migration chain starts from Phase 1 and applies cleanly to empty database
- All tables, columns, indexes, foreign keys, and unique constraints are created
- Downgrade to 002 succeeds and removes audio_analyses table and columns
- Re-upgrade to 003 succeeds cleanly without schema corruption or duplicates
"""

from __future__ import annotations

from pathlib import Path

from alembic import command
from alembic.config import Config
from sqlalchemy import inspect

from harmonia.core.database import create_database_engine, create_session_factory
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


def get_alembic_config(db_path: Path) -> Config:
    repo_root = Path(__file__).resolve().parents[2]
    alembic_ini = repo_root / "alembic.ini"
    cfg = Config(str(alembic_ini))
    cfg.set_main_option("script_location", str(repo_root / "migrations"))
    db_uri = f"sqlite:///{db_path.as_posix()}"
    cfg.set_main_option("sqlalchemy.url", db_uri)
    return cfg


def test_clean_database_migration(tmp_path: Path) -> None:
    """TEST #1: Clean database migration from empty database to head."""
    db_path = tmp_path / "fresh_clean.db"
    cfg = get_alembic_config(db_path)

    # Apply all migrations from zero
    command.upgrade(cfg, "head")

    engine = create_database_engine(db_path)
    inspector = inspect(engine)

    # 1. Verify tables exist
    table_names = set(inspector.get_table_names())
    expected_tables = {
        "artists",
        "albums",
        "tracks",
        "track_files",
        "tags",
        "metadata_fields",
        "duplicate_candidates",
        "audio_analyses",
    }
    assert expected_tables.issubset(table_names), f"Missing tables: {expected_tables - table_names}"

    # 2. Verify track_files columns (including Phase 3 additions)
    tf_cols = {col["name"]: col for col in inspector.get_columns("track_files")}
    assert "channel_layout" in tf_cols
    assert "encoder" in tf_cols
    assert "file_path" in tf_cols
    assert "file_hash" in tf_cols

    # 3. Verify audio_analyses table structure
    aa_cols = {col["name"]: col for col in inspector.get_columns("audio_analyses")}
    required_aa_cols = [
        "id",
        "track_file_id",
        "file_hash",
        "status",
        "integrated_lufs",
        "loudness_range_lu",
        "true_peak_dbtp",
        "sample_peak_dbfs",
        "peak_to_loudness_db",
        "clipping_samples",
        "is_clipped",
        "duration_analyzed",
        "silence_start_sec",
        "silence_end_sec",
        "analyzer_name",
        "analyzer_version",
        "config_version",
        "error_message",
        "started_at",
        "completed_at",
        "execution_time_sec",
    ]
    for col_name in required_aa_cols:
        assert col_name in aa_cols, f"Column {col_name} missing from audio_analyses table"

    # 4. Verify foreign key from audio_analyses.track_file_id to track_files.id
    fks = inspector.get_foreign_keys("audio_analyses")
    assert len(fks) >= 1
    fk = next(f for f in fks if "track_file_id" in f["constrained_columns"])
    assert fk["referred_table"] == "track_files"
    assert fk["referred_columns"] == ["id"]

    # 5. Verify indexes on audio_analyses
    indexes = inspector.get_indexes("audio_analyses")
    index_names = {idx["name"] for idx in indexes}
    assert any("status" in name for name in index_names)
    assert any("file_hash" in name for name in index_names)

    # 6. Verify unique constraint or index on track_file_id
    uqs = inspector.get_unique_constraints("audio_analyses")
    uq_cols = [col for uq in uqs for col in uq["column_names"]]
    # Also check unique indexes (SQLite represents unique constraints as unique indexes)
    unique_indexes = [idx for idx in indexes if idx.get("unique")]
    has_uq = "track_file_id" in uq_cols or any("track_file_id" in idx["column_names"] for idx in unique_indexes)
    assert has_uq, "Unique constraint or unique index on audio_analyses.track_file_id is required"


def test_migration_round_trip_with_data_preservation(tmp_path: Path) -> None:
    """TEST #2: Migration round trip: upgrade head -> insert data -> downgrade -> upgrade."""
    db_path = tmp_path / "round_trip.db"
    cfg = get_alembic_config(db_path)

    # Step 1: Upgrade to head
    command.upgrade(cfg, "head")

    # Step 2: Seed Phase 1 and 2 records
    engine = create_database_engine(db_path)
    session_factory = create_session_factory(engine)
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Brian Eno")
        artist_repo.add(artist)

        track = Track(title="1/1", artist_id=artist.id, duration_seconds=1000.0)
        track_repo.add(track)
        session.commit()
        artist_id = artist.id
        track_id = track.id

    # Step 3: Downgrade to 002 (Phase 2 schema)
    command.downgrade(cfg, "002_phase2_metadata_provenance_search")

    inspector = inspect(engine)
    assert "audio_analyses" not in inspector.get_table_names()
    tf_cols_down = {col["name"] for col in inspector.get_columns("track_files")}
    assert "channel_layout" not in tf_cols_down
    assert "encoder" not in tf_cols_down

    # Step 4: Verify Phase 1 & Phase 2 data survived downgrade
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)
        assert artist_repo.get_by_id(artist_id) is not None
        assert track_repo.get_by_id(track_id) is not None

    # Step 5: Upgrade back to head (Phase 3)
    command.upgrade(cfg, "head")

    inspector_after = inspect(engine)
    assert "audio_analyses" in inspector_after.get_table_names()
    tf_cols_up = {col["name"] for col in inspector_after.get_columns("track_files")}
    assert "channel_layout" in tf_cols_up
    assert "encoder" in tf_cols_up

    # Step 6: Verify Phase 1 & 2 data remains intact after re-upgrade
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)
        saved_artist = artist_repo.get_by_id(artist_id)
        saved_track = track_repo.get_by_id(track_id)
        assert saved_artist is not None and saved_artist.name == "Brian Eno"
        assert saved_track is not None and saved_track.title == "1/1"
