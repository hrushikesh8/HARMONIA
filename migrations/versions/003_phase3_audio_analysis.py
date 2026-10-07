"""Phase 3 audio intelligence and analysis.

Revision ID: 003_phase3_audio_analysis
Revises: 002_phase2_metadata_provenance_search
Create Date: 2026-10-07 10:30:00.000000

"""
from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "003_phase3_audio_analysis"
down_revision: str | None = "002_phase2_metadata_provenance_search"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    # 1. Add channel_layout and encoder to track_files
    with op.batch_alter_table("track_files") as batch_op:
        batch_op.add_column(sa.Column("channel_layout", sa.String(length=50), nullable=True))
        batch_op.add_column(sa.Column("encoder", sa.String(length=100), nullable=True))

    # 2. Create audio_analyses table
    op.create_table(
        "audio_analyses",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("track_file_id", sa.Uuid(), nullable=False),
        sa.Column("status", sa.String(length=20), nullable=False, server_default="PENDING"),
        sa.Column("integrated_lufs", sa.Float(), nullable=True),
        sa.Column("loudness_range_lu", sa.Float(), nullable=True),
        sa.Column("true_peak_dbtp", sa.Float(), nullable=True),
        sa.Column("sample_peak_dbfs", sa.Float(), nullable=True),
        sa.Column("peak_to_loudness_db", sa.Float(), nullable=True),
        sa.Column("clipping_samples", sa.Integer(), nullable=True),
        sa.Column("is_clipped", sa.Boolean(), nullable=False, server_default=sa.text("0")),
        sa.Column("duration_analyzed", sa.Float(), nullable=True),
        sa.Column("silence_start_sec", sa.Float(), nullable=True),
        sa.Column("silence_end_sec", sa.Float(), nullable=True),
        sa.Column("analyzer_name", sa.String(length=50), nullable=False, server_default="ffmpeg_ebur128"),
        sa.Column("analyzer_version", sa.String(length=20), nullable=False, server_default="1.0.0"),
        sa.Column("config_version", sa.String(length=20), nullable=False, server_default="1.0.0"),
        sa.Column("file_hash", sa.String(length=64), nullable=False),
        sa.Column("error_message", sa.Text(), nullable=True),
        sa.Column("started_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("execution_time_sec", sa.Float(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["track_file_id"], ["track_files.id"], ondelete="CASCADE"),
        sa.PrimaryKeyConstraint("id"),
        sa.UniqueConstraint("track_file_id", name="uq_audio_analyses_track_file_id"),
    )
    op.create_index(
        "ix_audio_analyses_track_file_id",
        "audio_analyses",
        ["track_file_id"],
        unique=True,
    )
    op.create_index(
        "ix_audio_analyses_status",
        "audio_analyses",
        ["status"],
        unique=False,
    )
    op.create_index(
        "ix_audio_analyses_file_hash",
        "audio_analyses",
        ["file_hash"],
        unique=False,
    )
    op.create_index(
        "ix_audio_analyses_started_at",
        "audio_analyses",
        ["started_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_table("audio_analyses")
    with op.batch_alter_table("track_files") as batch_op:
        batch_op.drop_column("encoder")
        batch_op.drop_column("channel_layout")
