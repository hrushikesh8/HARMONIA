"""Phase 2 metadata provenance, duplicate candidates, and search.

Revision ID: 002_phase2_metadata_provenance_search
Revises: 001_initial_schema
Create Date: 2026-10-07 00:00:00.000000

"""
from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "002_phase2_metadata_provenance_search"
down_revision: str | None = "001_initial_schema"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    # 1. Add file_mtime to track_files
    with op.batch_alter_table("track_files") as batch_op:
        batch_op.add_column(sa.Column("file_mtime", sa.Float(), nullable=True))

    # 2. Add art_hash to albums
    with op.batch_alter_table("albums") as batch_op:
        batch_op.add_column(sa.Column("art_hash", sa.String(length=64), nullable=True))

    # 3. Field-level metadata provenance table
    op.create_table(
        "metadata_fields",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("entity_type", sa.String(length=20), nullable=False),
        sa.Column("entity_id", sa.Uuid(), nullable=False),
        sa.Column("field_name", sa.String(length=100), nullable=False),
        sa.Column("provider", sa.String(length=50), nullable=False),
        sa.Column("raw_value", sa.Text(), nullable=False),
        sa.Column("normalized_value", sa.Text(), nullable=False),
        sa.Column("is_active", sa.Boolean(), nullable=False, server_default=sa.text("0")),
        sa.Column("priority", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_metadata_fields_entity",
        "metadata_fields",
        ["entity_type", "entity_id"],
        unique=False,
    )
    op.create_index(
        "ix_metadata_fields_entity_field",
        "metadata_fields",
        ["entity_type", "entity_id", "field_name"],
        unique=False,
    )
    op.create_index(
        "ix_metadata_fields_provider",
        "metadata_fields",
        ["provider"],
        unique=False,
    )

    # 4. Duplicate candidates table
    op.create_table(
        "duplicate_candidates",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("track_id_a", sa.Uuid(), nullable=False),
        sa.Column("track_id_b", sa.Uuid(), nullable=False),
        sa.Column("confidence", sa.String(length=20), nullable=False),
        sa.Column("reason", sa.Text(), nullable=False),
        sa.Column("resolved", sa.Boolean(), nullable=False, server_default=sa.text("0")),
        sa.Column("resolution", sa.String(length=20), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("resolved_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["track_id_a"], ["tracks.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["track_id_b"], ["tracks.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_duplicate_candidates_track_a",
        "duplicate_candidates",
        ["track_id_a"],
        unique=False,
    )
    op.create_index(
        "ix_duplicate_candidates_track_b",
        "duplicate_candidates",
        ["track_id_b"],
        unique=False,
    )
    op.create_index(
        "ix_duplicate_candidates_unresolved",
        "duplicate_candidates",
        ["resolved"],
        unique=False,
    )

    # 5. FTS5 full-text search index
    op.execute(
        "CREATE VIRTUAL TABLE IF NOT EXISTS tracks_fts USING fts5("
        "track_id UNINDEXED, "
        "title, "
        "artist_name, "
        "album_title, "
        "genre, "
        "tokenize='unicode61 remove_diacritics 2'"
        ")"
    )


def downgrade() -> None:
    op.execute("DROP TABLE IF EXISTS tracks_fts")
    op.drop_table("duplicate_candidates")
    op.drop_table("metadata_fields")

    with op.batch_alter_table("albums") as batch_op:
        batch_op.drop_column("art_hash")

    with op.batch_alter_table("track_files") as batch_op:
        batch_op.drop_column("file_mtime")
