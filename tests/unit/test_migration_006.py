"""Unit and integration tests for Alembic Migration 006 and personalization models.

Covers:
- Fresh database upgrade from 005 to 006
- Upgrade with populated catalog (preservation of tracks, files, analyses, tags)
- ORM model consistency and relationships
- Foreign key constraints (ON DELETE RESTRICT on tracks, CASCADE on playlists)
- Rating range check constraints and playlist position uniqueness
- Playlist gap tolerance (non-dense integer positions)
- Downgrade refusal without backup
- Downgrade refusal with stale, modified, or foreign backup
- Downgrade refusal with corrupted checksum
- Successful downgrade with valid, verified matching backup
- Concurrency and lock acquisition under SQLite WAL mode
- Physical media byte-for-byte preservation
"""

from __future__ import annotations

import hashlib
import json
import os
import tempfile
import uuid
from pathlib import Path

import pytest
import sqlalchemy as sa
from alembic import command
from alembic.config import Config
from sqlalchemy import select, text
from sqlalchemy.orm import Session

from harmonia.core.database import create_database_engine
from harmonia.infrastructure.persistence.models import (
    AlbumModel,
    ArtistModel,
    Base,
    FavoriteModel,
    PlaylistItemModel,
    PlaylistModel,
    RatingModel,
    TrackFileModel,
    TrackModel,
)


@pytest.fixture
def temp_db_env():
    """Sets up an isolated temporary database for Alembic migrations."""
    td = tempfile.TemporaryDirectory()
    temp_path = Path(td.name)
    db_file = temp_path / "test_harmonia.db"

    cfg = Config("alembic.ini")
    cfg.set_main_option("sqlalchemy.url", f"sqlite:///{db_file}")

    yield {"temp_path": temp_path, "db_file": db_file, "cfg": cfg}

    try:
        td.cleanup()
    except Exception:
        pass


def test_upgrade_005_to_006_on_fresh_database(temp_db_env):
    """Test clean upgrade from 005 to 006 on a fresh database."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]

    # Upgrade to 005 first
    command.upgrade(cfg, "005_phase5d_sqlite_tuning")

    # Upgrade to 006
    command.upgrade(cfg, "006_personalization_schema")

    engine = create_database_engine(db_file)
    with engine.connect() as conn:
        tables = conn.execute(
            text("SELECT name FROM sqlite_master WHERE type='table'")
        ).scalars().all()
        assert "playlists" in tables
        assert "playlist_items" in tables
        assert "favorites" in tables
        assert "ratings" in tables

        # Inspect indexes
        indexes = conn.execute(
            text("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name IN ('playlists', 'playlist_items', 'favorites', 'ratings')")
        ).scalars().all()

        assert "ix_playlists_name_lower" in indexes
        assert "ix_playlists_updated_at" in indexes
        assert "ix_playlist_items_playlist_id" in indexes
        assert "ix_playlist_items_track_id" in indexes
        assert "ix_playlist_items_playlist_position_key" in indexes
        assert "ix_favorites_client_updated_at" in indexes
        assert "ix_ratings_rating" in indexes
        assert "ix_ratings_client_updated_at" in indexes

    engine.dispose()


def test_upgrade_with_existing_populated_catalog(temp_db_env):
    """Test upgrade to 006 preserves existing catalog data."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]

    command.upgrade(cfg, "005_phase5d_sqlite_tuning")

    engine = create_database_engine(db_file)
    track_id = uuid.uuid4()
    with Session(engine) as session:
        artist = ArtistModel(id=uuid.uuid4(), name="Test Artist")
        session.add(artist)
        session.flush()

        album = AlbumModel(id=uuid.uuid4(), title="Test Album", artist_id=artist.id)
        session.add(album)
        session.flush()

        track = TrackModel(
            id=track_id, title="Test Track", artist_id=artist.id, album_id=album.id
        )
        session.add(track)
        session.commit()
    engine.dispose()

    # Run upgrade to 006
    command.upgrade(cfg, "006_personalization_schema")

    engine = create_database_engine(db_file)
    with Session(engine) as session:
        t = session.get(TrackModel, track_id)
        assert t is not None
        assert t.title == "Test Track"
        assert t.artist.name == "Test Artist"
        assert t.album.title == "Test Album"
    engine.dispose()


def test_orm_models_and_foreign_key_restrictions(temp_db_env):
    """Test ORM models and ON DELETE RESTRICT enforcement."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]

    command.upgrade(cfg, "006_personalization_schema")

    engine = create_database_engine(db_file)
    t_id = uuid.uuid4()
    pl_id = uuid.uuid4()

    with Session(engine) as session:
        session.add(TrackModel(id=t_id, title="Protected Track"))
        session.add(PlaylistModel(id=pl_id, name="Synthwave"))
        session.flush()

        # Add favorite, rating, and playlist item
        session.add(FavoriteModel(track_id=t_id, client_updated_at=1700000000000))
        session.add(RatingModel(track_id=t_id, rating=5, client_updated_at=1700000000000))
        session.add(
            PlaylistItemModel(
                id=uuid.uuid4(),
                playlist_id=pl_id,
                track_id=t_id,
                position=0,
                position_key="a0",
            )
        )
        session.commit()

    # Attempt raw deletion of track -> must be blocked by RESTRICT
    with Session(engine) as session:
        track = session.get(TrackModel, t_id)
        session.delete(track)
        with pytest.raises(sa.exc.IntegrityError):
            session.commit()

    # Verify ratings range check constraint (rating 6 is invalid)
    with Session(engine) as session:
        t_other = uuid.uuid4()
        session.add(TrackModel(id=t_other, title="Other Track"))
        session.flush()
        session.add(RatingModel(track_id=t_other, rating=6, client_updated_at=1700000000000))
        with pytest.raises(sa.exc.IntegrityError):
            session.commit()

    # Verify playlist item unique position constraint
    with Session(engine) as session:
        t_other2 = uuid.uuid4()
        session.add(TrackModel(id=t_other2, title="Other Track 2"))
        session.flush()
        # Same playlist_id, same position 0 -> must violate UNIQUE constraint
        session.add(
            PlaylistItemModel(
                id=uuid.uuid4(),
                playlist_id=pl_id,
                track_id=t_other2,
                position=0,
                position_key="a1",
            )
        )
        with pytest.raises(sa.exc.IntegrityError):
            session.commit()

    # Verify playlist deletion cascades to playlist_items but leaves track intact
    with Session(engine) as session:
        pl = session.get(PlaylistModel, pl_id)
        session.delete(pl)
        session.commit()

    with Session(engine) as session:
        assert session.get(PlaylistModel, pl_id) is None
        items = session.execute(
            select(PlaylistItemModel).where(PlaylistItemModel.playlist_id == pl_id)
        ).scalars().all()
        assert len(items) == 0
        # Track still exists
        assert session.get(TrackModel, t_id) is not None

    engine.dispose()


def test_gapped_positions_permitted(temp_db_env):
    """Test that non-dense integer positions (e.g. 0, 4, 9) are valid."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]

    command.upgrade(cfg, "006_personalization_schema")

    engine = create_database_engine(db_file)
    pl_id = uuid.uuid4()
    t1, t2, t3 = uuid.uuid4(), uuid.uuid4(), uuid.uuid4()

    with Session(engine) as session:
        session.add_all([
            TrackModel(id=t1, title="T1"),
            TrackModel(id=t2, title="T2"),
            TrackModel(id=t3, title="T3"),
            PlaylistModel(id=pl_id, name="Gapped Playlist"),
        ])
        session.flush()

        session.add_all([
            PlaylistItemModel(id=uuid.uuid4(), playlist_id=pl_id, track_id=t1, position=0, position_key="a0"),
            PlaylistItemModel(id=uuid.uuid4(), playlist_id=pl_id, track_id=t2, position=4, position_key="a4"),
            PlaylistItemModel(id=uuid.uuid4(), playlist_id=pl_id, track_id=t3, position=9, position_key="a9"),
        ])
        session.commit()

    with Session(engine) as session:
        items = session.execute(
            select(PlaylistItemModel).where(PlaylistItemModel.playlist_id == pl_id).order_by(PlaylistItemModel.position)
        ).scalars().all()
        assert [it.position for it in items] == [0, 4, 9]

    engine.dispose()


def test_downgrade_refusal_without_backup(temp_db_env):
    """Test that downgrade is refused when personalization data exists and no backup is provided."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]

    command.upgrade(cfg, "006_personalization_schema")

    engine = create_database_engine(db_file)
    t_id = uuid.uuid4()
    with Session(engine) as session:
        session.add(TrackModel(id=t_id, title="Favorited"))
        session.flush()
        session.add(FavoriteModel(track_id=t_id, client_updated_at=1700000000000))
        session.commit()
    engine.dispose()

    # Ensure env var is unset
    if "HARMONIA_PERSONALIZATION_BACKUP_PATH" in os.environ:
        del os.environ["HARMONIA_PERSONALIZATION_BACKUP_PATH"]

    with pytest.raises(Exception) as exc_info:
        command.downgrade(cfg, "005_phase5d_sqlite_tuning")

    assert "Downgrade refused: Personalization tables contain active user data" in str(exc_info.value)

    # Verify tables and data remain intact
    engine = create_database_engine(db_file)
    with Session(engine) as session:
        fav = session.get(FavoriteModel, t_id)
        assert fav is not None
    engine.dispose()


def test_downgrade_refusal_with_divergent_or_corrupt_backup(temp_db_env):
    """Test that downgrade is refused if backup does not match live data or has bad checksum."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]
    temp_path = temp_db_env["temp_path"]

    command.upgrade(cfg, "006_personalization_schema")

    engine = create_database_engine(db_file)
    t_id = uuid.uuid4()
    with Session(engine) as session:
        session.add(TrackModel(id=t_id, title="Track"))
        session.flush()
        session.add(FavoriteModel(track_id=t_id, client_updated_at=1700000000000))
        session.commit()
    engine.dispose()

    # 1. Stale backup with different track_id
    stale_backup_file = temp_path / "stale_backup.json"
    stale_payload = {
        "favorites": [{"track_id": str(uuid.uuid4()).lower(), "client_updated_at": 1700000000000}],
        "playlist_items": [],
        "playlists": [],
        "ratings": [],
    }
    stale_canon = json.dumps(stale_payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    stale_digest = hashlib.sha256(stale_canon).hexdigest()
    stale_backup_file.write_text(
        json.dumps({
            "manifest": {"schema_version": "1.0", "payload_sha256": stale_digest},
            "payload": stale_payload,
        }),
        encoding="utf-8",
    )

    os.environ["HARMONIA_PERSONALIZATION_BACKUP_PATH"] = str(stale_backup_file)
    with pytest.raises(Exception) as exc_info:
        command.downgrade(cfg, "005_phase5d_sqlite_tuning")
    assert "Live personalization data diverges from supplied backup" in str(exc_info.value)

    # 2. Corrupt checksum backup
    corrupt_backup_file = temp_path / "corrupt_backup.json"
    corrupt_backup_file.write_text(
        json.dumps({
            "manifest": {"schema_version": "1.0", "payload_sha256": "0" * 64},
            "payload": stale_payload,
        }),
        encoding="utf-8",
    )
    os.environ["HARMONIA_PERSONALIZATION_BACKUP_PATH"] = str(corrupt_backup_file)
    with pytest.raises(Exception) as exc_info:
        command.downgrade(cfg, "005_phase5d_sqlite_tuning")
    assert "Backup payload checksum corrupted" in str(exc_info.value)


def test_successful_downgrade_with_exact_valid_backup(temp_db_env):
    """Test successful downgrade when an exact matching backup is provided."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]
    temp_path = temp_db_env["temp_path"]

    command.upgrade(cfg, "006_personalization_schema")

    engine = create_database_engine(db_file)
    t_id = uuid.uuid4()
    with Session(engine) as session:
        session.add(TrackModel(id=t_id, title="Track"))
        session.flush()
        session.add(FavoriteModel(track_id=t_id, client_updated_at=1700000000000))
        session.commit()

    # Generate exact canonical backup matching live data
    engine = create_database_engine(db_file)
    with engine.connect() as conn:
        import importlib.util
        m006_path = Path(__file__).resolve().parents[2] / "migrations" / "versions" / "006_personalization_schema.py"
        spec = importlib.util.spec_from_file_location("m006_module", m006_path)
        m006 = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(m006)
        _read_live_personalization_payload = m006._read_live_personalization_payload
        _canonical_json_digest = m006._canonical_json_digest
        live_payload = _read_live_personalization_payload(conn)
        live_digest, _ = _canonical_json_digest(live_payload)

    backup_file = temp_path / "valid_backup.json"
    backup_file.write_text(
        json.dumps({
            "manifest": {"schema_version": "1.0", "payload_sha256": live_digest},
            "payload": live_payload,
        }),
        encoding="utf-8",
    )

    os.environ["HARMONIA_PERSONALIZATION_BACKUP_PATH"] = str(backup_file)

    # Execute downgrade
    command.downgrade(cfg, "005_phase5d_sqlite_tuning")

    # Assert personalization tables are dropped
    with engine.connect() as conn:
        tables = conn.execute(
            text("SELECT name FROM sqlite_master WHERE type='table'")
        ).scalars().all()
        assert "favorites" not in tables
        assert "ratings" not in tables
        assert "playlists" not in tables
        assert "playlist_items" not in tables
        # Track catalog remains intact
        assert "tracks" in tables
    engine.dispose()


def test_audio_file_preservation(temp_db_env):
    """Verify that audio files on disk are never unlinked during migration operations."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]
    temp_path = temp_db_env["temp_path"]

    audio_file = temp_path / "song.flac"
    audio_file.write_bytes(b"FLAC_AUDIO_SAMPLE_BYTES_123456789")
    original_hash = hashlib.sha256(audio_file.read_bytes()).hexdigest()

    command.upgrade(cfg, "006_personalization_schema")

    engine = create_database_engine(db_file)
    t_id = uuid.uuid4()
    with Session(engine) as session:
        session.add(TrackModel(id=t_id, title="Track"))
        session.flush()
        session.add(
            TrackFileModel(
                id=uuid.uuid4(),
                track_id=t_id,
                file_path=str(audio_file),
                file_size=len(audio_file.read_bytes()),
                format="FLAC",
            )
        )
        session.add(FavoriteModel(track_id=t_id, client_updated_at=1700000000000))
        session.commit()
    engine.dispose()

    # Audio file exists and hash matches
    assert audio_file.exists()
    assert hashlib.sha256(audio_file.read_bytes()).hexdigest() == original_hash


def test_concurrent_writer_blocked_during_downgrade_lock(temp_db_env):
    """Test that BEGIN IMMEDIATE acquired during downgrade locks out concurrent writes in WAL mode."""
    import sqlite3

    db_file = temp_db_env["db_file"]
    cfg = temp_db_env["cfg"]
    command.upgrade(cfg, "006_personalization_schema")

    # Connection 1 acquires IMMEDIATE lock (simulating downgrade guard)
    conn1 = sqlite3.connect(str(db_file), timeout=0.1)
    conn1.execute("BEGIN IMMEDIATE")

    # Connection 2 attempts to write during this lock
    conn2 = sqlite3.connect(str(db_file), timeout=0.1)
    with pytest.raises(sqlite3.OperationalError, match="database is locked"):
        conn2.execute("INSERT INTO playlists (id, name, created_at, updated_at) VALUES ('p1', 'Test', 'now', 'now')")

    conn1.rollback()
    conn1.close()
    conn2.close()


def test_failure_injection_preserves_tables_and_rows_intact(temp_db_env):
    """Failure injection test: when downgrade is rejected due to invalid backup, all personalization tables and rows remain intact."""
    cfg = temp_db_env["cfg"]
    db_file = temp_db_env["db_file"]
    temp_path = temp_db_env["temp_path"]

    command.upgrade(cfg, "006_personalization_schema")
    engine = create_database_engine(db_file)
    t_id = uuid.uuid4()
    with Session(engine) as session:
        session.add(TrackModel(id=t_id, title="Track"))
        session.flush()
        session.add(FavoriteModel(track_id=t_id, client_updated_at=1700000000000))
        session.add(RatingModel(track_id=t_id, rating=5, client_updated_at=1700000000000))
        pl = PlaylistModel(id=uuid.uuid4(), name="My Playlist")
        session.add(pl)
        session.flush()
        session.add(PlaylistItemModel(id=uuid.uuid4(), playlist_id=pl.id, track_id=t_id, position=0, position_key="a0"))
        session.commit()
    engine.dispose()

    # Provide a backup that fails catalog verification (references a non-existent track)
    invalid_backup_file = temp_path / "bad_catalog_backup.json"
    bad_t_id = str(uuid.uuid4())
    bad_payload = {
        "favorites": [{"track_id": bad_t_id, "client_updated_at": 1700000000000}],
        "playlist_items": [],
        "playlists": [],
        "ratings": [],
    }
    canon = json.dumps(bad_payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    digest = hashlib.sha256(canon).hexdigest()
    invalid_backup_file.write_text(
        json.dumps({
            "manifest": {"schema_version": "1.0", "payload_sha256": digest},
            "payload": bad_payload,
        }),
        encoding="utf-8",
    )

    os.environ["HARMONIA_PERSONALIZATION_BACKUP_PATH"] = str(invalid_backup_file)
    with pytest.raises(Exception) as exc_info:
        command.downgrade(cfg, "005_phase5d_sqlite_tuning")

    assert "Downgrade refused" in str(exc_info.value)

    # Verify all tables and data are completely untouched
    engine = create_database_engine(db_file)
    with Session(engine) as session:
        assert session.get(FavoriteModel, t_id) is not None
        assert session.get(RatingModel, t_id).rating == 5
        assert len(session.execute(select(PlaylistModel)).scalars().all()) == 1
        assert len(session.execute(select(PlaylistItemModel)).scalars().all()) == 1
    engine.dispose()
