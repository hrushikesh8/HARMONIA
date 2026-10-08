"""Phase 4 playback telemetry and queue snapshots.

Revision ID: 004_phase4_playback_and_telemetry
Revises: 003_phase3_audio_analysis
Create Date: 2026-10-08 11:45:00.000000

"""

from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa

revision: str = "004_phase4_playback_and_telemetry"
down_revision: str | None = "003_phase3_audio_analysis"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    # 1. Create listening_events table
    op.create_table(
        "listening_events",
        sa.Column("id", sa.Uuid(), nullable=False),
        sa.Column("track_id", sa.Uuid(), nullable=False),
        sa.Column("track_file_id", sa.Uuid(), nullable=True),
        sa.Column("timestamp_utc", sa.DateTime(timezone=True), nullable=False),
        sa.Column("started_at_sec", sa.Float(), nullable=False, server_default="0.0"),
        sa.Column("ended_at_sec", sa.Float(), nullable=False, server_default="0.0"),
        sa.Column("duration_listened_seconds", sa.Float(), nullable=False, server_default="0.0"),
        sa.Column("track_duration_seconds", sa.Float(), nullable=False, server_default="0.0"),
        sa.Column("was_completed", sa.Boolean(), nullable=False, server_default=sa.text("0")),
        sa.Column("was_skipped", sa.Boolean(), nullable=False, server_default=sa.text("0")),
        sa.Column("playback_source", sa.String(length=50), nullable=False, server_default="queue"),
        sa.Column("device_id", sa.Uuid(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.ForeignKeyConstraint(["track_id"], ["tracks.id"], ondelete="CASCADE"),
        sa.ForeignKeyConstraint(["track_file_id"], ["track_files.id"], ondelete="SET NULL"),
        sa.PrimaryKeyConstraint("id"),
    )
    op.create_index(
        "ix_listening_events_track_id",
        "listening_events",
        ["track_id"],
        unique=False,
    )
    op.create_index(
        "ix_listening_events_timestamp",
        "listening_events",
        ["timestamp_utc"],
        unique=False,
    )
    op.create_index(
        "ix_listening_events_track_file_id",
        "listening_events",
        ["track_file_id"],
        unique=False,
    )

    # 2. Create playback_queue_snapshots table
    op.create_table(
        "playback_queue_snapshots",
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("current_track_id", sa.Uuid(), nullable=True),
        sa.Column("current_track_file_id", sa.Uuid(), nullable=True),
        sa.Column("position_seconds", sa.Float(), nullable=False, server_default="0.0"),
        sa.Column("volume", sa.Float(), nullable=False, server_default="1.0"),
        sa.Column("repeat_mode", sa.String(length=20), nullable=False, server_default="off"),
        sa.Column("shuffle_enabled", sa.Boolean(), nullable=False, server_default=sa.text("0")),
        sa.Column("queue_json", sa.Text(), nullable=False, server_default="[]"),
        sa.Column("current_queue_index", sa.Integer(), nullable=False, server_default="-1"),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.PrimaryKeyConstraint("id"),
    )


def downgrade() -> None:
    op.drop_table("playback_queue_snapshots")
    op.drop_index("ix_listening_events_track_file_id", table_name="listening_events")
    op.drop_index("ix_listening_events_timestamp", table_name="listening_events")
    op.drop_index("ix_listening_events_track_id", table_name="listening_events")
    op.drop_table("listening_events")
