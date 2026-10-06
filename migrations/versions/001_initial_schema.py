"""Initial database schema for Harmonia.

Revision ID: 001_initial_schema
Revises: None
Create Date: 2026-10-06 12:00:00.000000

"""
from typing import Sequence, Union

from alembic import op
import sqlalchemy as sa

revision: str = "001_initial_schema"
down_revision: Union[str, None] = None
branch_labels: Union[str, Sequence[str], None] = None
depends_on: Union[str, Sequence[str], None] = None


def upgrade() -> None:
    # ── Artists ───────────────────────────────────────────────────────────────
    op.create_table(
        "artists",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=500), nullable=False),
        sa.Column("sort_name", sa.String(length=500), nullable=True),
        sa.Column("mbid", sa.String(length=36), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("mbid"),
    )
    op.create_index("ix_artists_name_lower", "artists", ["name"], unique=False)
    op.create_index("ix_artists_sort_name", "artists", ["sort_name"], unique=False)

    # ── Albums ────────────────────────────────────────────────────────────────
    op.create_table(
        "albums",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("title", sa.String(length=500), nullable=False),
        sa.Column("artist_id", sa.Uuid(), nullable=True),
        sa.Column("year", sa.Integer(), nullable=True),
        sa.Column("total_tracks", sa.Integer(), nullable=True),
        sa.Column("total_discs", sa.Integer(), nullable=True),
        sa.Column("mbid", sa.String(length=36), nullable=True),
        sa.Column("art_path", sa.String(length=1000), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["artist_id"], ["artists.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("mbid"),
    )
    op.create_index("ix_albums_title", "albums", ["title"], unique=False)
    op.create_index("ix_albums_artist_id", "albums", ["artist_id"], unique=False)
    op.create_index("ix_albums_year", "albums", ["year"], unique=False)

    # ── Tracks ────────────────────────────────────────────────────────────────
    op.create_table(
        "tracks",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("title", sa.String(length=500), nullable=False),
        sa.Column("artist_id", sa.Uuid(), nullable=True),
        sa.Column("album_id", sa.Uuid(), nullable=True),
        sa.Column("track_number", sa.Integer(), nullable=True),
        sa.Column("disc_number", sa.Integer(), nullable=True),
        sa.Column("duration_seconds", sa.Float(), nullable=True),
        sa.Column("year", sa.Integer(), nullable=True),
        sa.Column("genre", sa.String(length=200), nullable=True),
        sa.Column("mbid", sa.String(length=36), nullable=True),
        sa.Column("lyrics", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["artist_id"], ["artists.id"], ondelete="SET NULL"),
        sa.ForeignKeyConstraint(["album_id"], ["albums.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("mbid"),
    )
    op.create_index("ix_tracks_title", "tracks", ["title"], unique=False)
    op.create_index("ix_tracks_artist_id", "tracks", ["artist_id"], unique=False)
    op.create_index("ix_tracks_album_id", "tracks", ["album_id"], unique=False)
    op.create_index("ix_tracks_genre", "tracks", ["genre"], unique=False)

    # ── Track Files ───────────────────────────────────────────────────────────
    op.create_table(
        "track_files",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("track_id", sa.Uuid(), nullable=False),
        sa.Column("file_path", sa.String(length=2000), nullable=False),
        sa.Column("file_hash", sa.String(length=64), nullable=True),
        sa.Column("file_size", sa.Integer(), nullable=False),
        sa.Column("format", sa.String(length=10), nullable=False),
        sa.Column("codec", sa.String(length=20), nullable=True),
        sa.Column("bitrate", sa.Integer(), nullable=True),
        sa.Column("sample_rate", sa.Integer(), nullable=True),
        sa.Column("bit_depth", sa.Integer(), nullable=True),
        sa.Column("channels", sa.Integer(), nullable=True),
        sa.Column("duration_seconds", sa.Float(), nullable=True),
        sa.Column("is_lossless", sa.Boolean(), nullable=False),
        sa.Column("quality_score", sa.Integer(), nullable=True),
        sa.Column("added_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("verified_at", sa.DateTime(timezone=True), nullable=True),
        sa.ForeignKeyConstraint(["track_id"], ["tracks.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("file_path"),
    )
    op.create_index("ix_track_files_track_id", "track_files", ["track_id"], unique=False)
    op.create_index("ix_track_files_file_hash", "track_files", ["file_hash"], unique=False)
    op.create_index("ix_track_files_format", "track_files", ["format"], unique=False)

    # ── Tags ──────────────────────────────────────────────────────────────────
    op.create_table(
        "tags",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("track_id", sa.Uuid(), nullable=False),
        sa.Column("name", sa.String(length=100), nullable=False),
        sa.Column("value", sa.String(length=500), nullable=False),
        sa.Column("source", sa.String(length=20), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["track_id"], ["tracks.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index("ix_tags_track_id", "tags", ["track_id"], unique=False)
    op.create_index("ix_tags_name_value", "tags", ["name", "value"], unique=False)


def downgrade() -> None:
    op.drop_table("tags")
    op.drop_table("track_files")
    op.drop_table("tracks")
    op.drop_table("albums")
    op.drop_table("artists")
