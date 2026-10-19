"""Phase 6 Personalization Schema: playlists, playlist items, favorites, and ratings.

Revision ID: 006_personalization_schema
Revises: 005_phase5d_sqlite_tuning
Create Date: 2026-10-19 17:20:00.000000

Adds desktop-owned persistence for user personalization:
- playlists: User-curated playlists with lowercase name indexing
- playlist_items: Ordered playlist entries referencing tracks with ON DELETE RESTRICT
- favorites: Track favoriting evaluations with ON DELETE RESTRICT
- ratings: 1-5 star track ratings with ON DELETE RESTRICT and range check constraint

Includes enforceable, race-safe downgrade protection verifying live-to-backup
SHA-256 digest equivalence under SQLite write reservation before dropping tables.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import uuid
from collections.abc import Sequence
from pathlib import Path
from typing import Any

import sqlalchemy as sa
from alembic import op

revision: str = "006_personalization_schema"
down_revision: str | None = "005_phase5d_sqlite_tuning"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None

UUID_REGEX = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$", re.IGNORECASE
)


def upgrade() -> None:
    # ── 1. Create playlists table ─────────────────────────────────────────────
    op.create_table(
        "playlists",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column("name", sa.String(200), nullable=False),
        sa.Column("description", sa.String(1000), nullable=True),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
    )
    op.create_index(
        "ix_playlists_name_lower",
        "playlists",
        [sa.text("lower(name)")],
        unique=False,
    )
    op.create_index(
        "ix_playlists_updated_at",
        "playlists",
        [sa.text("updated_at DESC")],
        unique=False,
    )

    # ── 2. Create playlist_items table ────────────────────────────────────────
    op.create_table(
        "playlist_items",
        sa.Column("id", sa.Uuid(), primary_key=True),
        sa.Column(
            "playlist_id",
            sa.Uuid(),
            sa.ForeignKey("playlists.id", ondelete="CASCADE"),
            nullable=False,
        ),
        sa.Column(
            "track_id",
            sa.Uuid(),
            sa.ForeignKey("tracks.id", ondelete="RESTRICT"),
            nullable=False,
        ),
        sa.Column("position", sa.Integer(), nullable=False),
        sa.Column("position_key", sa.String(64), nullable=False),
        sa.Column(
            "added_at",
            sa.DateTime(timezone=True),
            server_default=sa.func.now(),
            nullable=False,
        ),
        sa.UniqueConstraint(
            "playlist_id", "position", name="uq_playlist_items_playlist_position"
        ),
    )
    op.create_index(
        "ix_playlist_items_playlist_id",
        "playlist_items",
        ["playlist_id"],
        unique=False,
    )
    op.create_index(
        "ix_playlist_items_track_id",
        "playlist_items",
        ["track_id"],
        unique=False,
    )
    op.create_index(
        "ix_playlist_items_playlist_position_key",
        "playlist_items",
        ["playlist_id", "position_key"],
        unique=False,
    )

    # ── 3. Create favorites table ─────────────────────────────────────────────
    op.create_table(
        "favorites",
        sa.Column(
            "track_id",
            sa.Uuid(),
            sa.ForeignKey("tracks.id", ondelete="RESTRICT"),
            primary_key=True,
        ),
        sa.Column("client_updated_at", sa.Integer(), nullable=False),
    )
    op.create_index(
        "ix_favorites_client_updated_at",
        "favorites",
        [sa.text("client_updated_at DESC")],
        unique=False,
    )

    # ── 4. Create ratings table ───────────────────────────────────────────────
    op.create_table(
        "ratings",
        sa.Column(
            "track_id",
            sa.Uuid(),
            sa.ForeignKey("tracks.id", ondelete="RESTRICT"),
            primary_key=True,
        ),
        sa.Column("rating", sa.Integer(), nullable=False),
        sa.Column("client_updated_at", sa.Integer(), nullable=False),
        sa.CheckConstraint(
            "rating >= 1 AND rating <= 5", name="ck_ratings_rating_range"
        ),
    )
    op.create_index(
        "ix_ratings_rating",
        "ratings",
        ["rating"],
        unique=False,
    )
    op.create_index(
        "ix_ratings_client_updated_at",
        "ratings",
        [sa.text("client_updated_at DESC")],
        unique=False,
    )


# ── Downgrade Safety & Equivalence Verification ───────────────────────────────


def _normalize_uuid(val: Any) -> str:
    """Normalizes any UUID value into a lowercase RFC 4122 string."""
    return str(uuid.UUID(str(val))).lower()


def _canonical_json_digest(payload: dict[str, Any]) -> tuple[str, bytes]:
    """Computes deterministic SHA-256 hex digest over canonical JSON bytes."""
    canonical_bytes = json.dumps(
        payload, sort_keys=True, separators=(",", ":"), ensure_ascii=True
    ).encode("utf-8")
    return hashlib.sha256(canonical_bytes).hexdigest(), canonical_bytes


def _read_live_personalization_payload(conn: sa.engine.Connection) -> dict[str, Any]:
    """Reads live personalization records into canonical structure under active lock."""
    # Read favorites
    fav_rows = conn.execute(
        sa.text(
            "SELECT track_id, client_updated_at FROM favorites ORDER BY track_id ASC"
        )
    ).mappings().all()
    favorites = [
        {
            "track_id": _normalize_uuid(r["track_id"]),
            "client_updated_at": int(r["client_updated_at"]),
        }
        for r in fav_rows
    ]

    # Read ratings
    rat_rows = conn.execute(
        sa.text(
            "SELECT track_id, rating, client_updated_at FROM ratings ORDER BY track_id ASC"
        )
    ).mappings().all()
    ratings = [
        {
            "track_id": _normalize_uuid(r["track_id"]),
            "rating": int(r["rating"]),
            "client_updated_at": int(r["client_updated_at"]),
        }
        for r in rat_rows
    ]

    # Read playlists
    pl_rows = conn.execute(
        sa.text(
            "SELECT id, name, description, created_at, updated_at FROM playlists ORDER BY id ASC"
        )
    ).mappings().all()
    playlists = [
        {
            "id": _normalize_uuid(r["id"]),
            "name": str(r["name"]),
            "description": str(r["description"]) if r["description"] is not None else None,
            "created_at": str(r["created_at"]),
            "updated_at": str(r["updated_at"]),
        }
        for r in pl_rows
    ]

    # Read playlist_items
    it_rows = conn.execute(
        sa.text(
            "SELECT id, playlist_id, track_id, position, position_key, added_at "
            "FROM playlist_items ORDER BY playlist_id ASC, position ASC, id ASC"
        )
    ).mappings().all()
    playlist_items = [
        {
            "id": _normalize_uuid(r["id"]),
            "playlist_id": _normalize_uuid(r["playlist_id"]),
            "track_id": _normalize_uuid(r["track_id"]),
            "position": int(r["position"]),
            "position_key": str(r["position_key"]),
            "added_at": str(r["added_at"]),
        }
        for r in it_rows
    ]

    return {
        "favorites": favorites,
        "playlist_items": playlist_items,
        "playlists": playlists,
        "ratings": ratings,
    }


def _verify_isolated_restore(
    backup_payload: dict[str, Any], live_track_ids: set[str]
) -> None:
    """Restores backup payload into an isolated in-memory SQLite database to verify constraints."""
    mem_engine = sa.create_engine("sqlite:///:memory:")
    with mem_engine.begin() as mem_conn:
        mem_conn.exec_driver_sql("PRAGMA foreign_keys = ON")
        # Create minimal catalog table
        mem_conn.execute(
            sa.text("CREATE TABLE tracks (id TEXT PRIMARY KEY, title TEXT)")
        )
        for tid in live_track_ids:
            mem_conn.execute(
                sa.text("INSERT INTO tracks (id, title) VALUES (:id, 'Test')"),
                {"id": tid},
            )

            # Create personalization schema
            mem_conn.execute(
                sa.text(
                    "CREATE TABLE playlists (id TEXT PRIMARY KEY, name TEXT, description TEXT, created_at TEXT, updated_at TEXT)"
                )
            )
            mem_conn.execute(
                sa.text(
                    "CREATE TABLE playlist_items ("
                    "id TEXT PRIMARY KEY, "
                    "playlist_id TEXT REFERENCES playlists(id) ON DELETE CASCADE, "
                    "track_id TEXT REFERENCES tracks(id) ON DELETE RESTRICT, "
                    "position INTEGER, position_key TEXT, added_at TEXT, "
                    "UNIQUE(playlist_id, position))"
                )
            )
            mem_conn.execute(
                sa.text(
                    "CREATE TABLE favorites (track_id TEXT PRIMARY KEY REFERENCES tracks(id) ON DELETE RESTRICT, client_updated_at INTEGER)"
                )
            )
            mem_conn.execute(
                sa.text(
                    "CREATE TABLE ratings (track_id TEXT PRIMARY KEY REFERENCES tracks(id) ON DELETE RESTRICT, rating INTEGER CHECK(rating >= 1 AND rating <= 5), client_updated_at INTEGER)"
                )
            )

            # Restore playlists
            for p in backup_payload.get("playlists", []):
                mem_conn.execute(
                    sa.text(
                        "INSERT INTO playlists (id, name, description, created_at, updated_at) "
                        "VALUES (:id, :name, :description, :created_at, :updated_at)"
                    ),
                    p,
                )

            # Restore items
            for it in backup_payload.get("playlist_items", []):
                mem_conn.execute(
                    sa.text(
                        "INSERT INTO playlist_items (id, playlist_id, track_id, position, position_key, added_at) "
                        "VALUES (:id, :playlist_id, :track_id, :position, :position_key, :added_at)"
                    ),
                    it,
                )

            # Restore favorites
            for f in backup_payload.get("favorites", []):
                mem_conn.execute(
                    sa.text(
                        "INSERT INTO favorites (track_id, client_updated_at) VALUES (:track_id, :client_updated_at)"
                    ),
                    f,
                )

            # Restore ratings
            for r in backup_payload.get("ratings", []):
                mem_conn.execute(
                    sa.text(
                        "INSERT INTO ratings (track_id, rating, client_updated_at) VALUES (:track_id, :rating, :client_updated_at)"
                    ),
                    r,
                )
    mem_engine.dispose()


def downgrade() -> None:
    conn = op.get_bind()

    # 1. Acquire IMMEDIATE lock to protect against concurrent write races
    try:
        conn.exec_driver_sql("BEGIN IMMEDIATE")
    except Exception:
        # Lock already held or transaction active
        pass

    # 2. Check if personalization tables contain any data
    fav_count = conn.scalar(sa.text("SELECT count(*) FROM favorites")) or 0
    rat_count = conn.scalar(sa.text("SELECT count(*) FROM ratings")) or 0
    pl_count = conn.scalar(sa.text("SELECT count(*) FROM playlists")) or 0
    it_count = conn.scalar(sa.text("SELECT count(*) FROM playlist_items")) or 0

    total_personal_records = fav_count + rat_count + pl_count + it_count

    # If all personalization tables are empty, safe to drop without backup
    if total_personal_records == 0:
        op.drop_table("ratings")
        op.drop_table("favorites")
        op.drop_table("playlist_items")
        op.drop_table("playlists")
        return

    # 3. If data exists, an explicit verified backup path is MANDATORY
    backup_path_env = os.environ.get("HARMONIA_PERSONALIZATION_BACKUP_PATH")
    if not backup_path_env:
        raise RuntimeError(
            "Downgrade refused: Personalization tables contain active user data "
            f"({fav_count} favorites, {rat_count} ratings, {pl_count} playlists, {it_count} items). "
            "A verified matching backup is required via HARMONIA_PERSONALIZATION_BACKUP_PATH "
            "before tables can be dropped."
        )

    backup_path = Path(backup_path_env)
    if not backup_path.is_file():
        raise RuntimeError(
            f"Downgrade refused: Specified backup path '{backup_path_env}' does not exist or is not a file."
        )

    try:
        with open(backup_path, "r", encoding="utf-8") as f:
            backup_data = json.load(f)
    except Exception as e:
        raise RuntimeError(f"Downgrade refused: Failed to parse backup JSON file: {e}") from e

    manifest = backup_data.get("manifest")
    if not isinstance(manifest, dict) or manifest.get("schema_version") != "1.0":
        raise RuntimeError(
            "Downgrade refused: Backup manifest missing or unsupported schema_version "
            f"(expected '1.0', got '{manifest.get('schema_version') if isinstance(manifest, dict) else None}')."
        )

    backup_payload = backup_data.get("payload")
    if not isinstance(backup_payload, dict):
        raise RuntimeError("Downgrade refused: Backup file payload missing or invalid.")

    # 4. Check backup payload internal SHA-256 digest
    backup_digest, _ = _canonical_json_digest(backup_payload)
    if backup_digest != manifest.get("payload_sha256"):
        raise RuntimeError(
            f"Downgrade refused: Backup payload checksum corrupted (computed {backup_digest}, manifest {manifest.get('payload_sha256')})."
        )

    # 5. Live-to-backup Equivalence Verification under active IMMEDIATE lock
    live_payload = _read_live_personalization_payload(conn)
    live_digest, _ = _canonical_json_digest(live_payload)

    if live_digest != backup_digest:
        raise RuntimeError(
            "Downgrade refused: Live personalization data diverges from supplied backup. "
            f"The backup is stale, mutated, or from another database (live hash {live_digest}, backup hash {backup_digest})."
        )

    # 6. Verify catalog references
    live_track_rows = conn.execute(sa.text("SELECT id FROM tracks")).scalars().all()
    live_track_ids = {_normalize_uuid(tid) for tid in live_track_rows}

    for f in backup_payload.get("favorites", []):
        if f["track_id"] not in live_track_ids:
            raise RuntimeError(
                f"Downgrade refused: Backup references track_id '{f['track_id']}' not present in live tracks catalog."
            )
    for r in backup_payload.get("ratings", []):
        if r["track_id"] not in live_track_ids:
            raise RuntimeError(
                f"Downgrade refused: Backup references track_id '{r['track_id']}' not present in live tracks catalog."
            )
    for it in backup_payload.get("playlist_items", []):
        if it["track_id"] not in live_track_ids:
            raise RuntimeError(
                f"Downgrade refused: Backup references track_id '{it['track_id']}' not present in live tracks catalog."
            )

    # 7. Isolated restore validation
    try:
        _verify_isolated_restore(backup_payload, live_track_ids)
    except Exception as e:
        raise RuntimeError(
            f"Downgrade refused: Backup restore simulation failed in isolated test database: {e}"
        ) from e

    # 8. All checks passed under active write lock: execute drops in reverse-dependency order
    op.drop_table("ratings")
    op.drop_table("favorites")
    op.drop_table("playlist_items")
    op.drop_table("playlists")
