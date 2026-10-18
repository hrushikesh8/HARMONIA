"""Phase 5D SQLite performance and query optimization indexes.

Revision ID: 005_phase5d_sqlite_tuning
Revises: 004_phase4_playback_and_telemetry
Create Date: 2026-10-18 09:30:00.000000

Adds targeted composite and expression indexes for high-frequency queries:
- ix_artists_name_lower: functional index on lower(name) for case-insensitive lookup
- ix_albums_artist_title_lower: composite expression index on (artist_id, lower(title))
- ix_albums_artist_year: composite index on (artist_id, year DESC)
- ix_tracks_album_disc_track: composite index on (album_id, disc_number, track_number)
- ix_tracks_artist_title: composite index on (artist_id, title)
- ix_track_files_track_quality: composite index on (track_id, quality_score DESC)
- ix_listening_events_track_timestamp: composite index on (track_id, timestamp_utc DESC)
- ix_metadata_fields_entity_field_priority: composite index on (entity_type, entity_id, field_name, priority DESC, created_at)
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "005_phase5d_sqlite_tuning"
down_revision: str | None = "004_phase4_playback_and_telemetry"
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    # 1. Update artists name lower index to expression index lower(name)
    op.drop_index("ix_artists_name_lower", table_name="artists")
    op.create_index(
        "ix_artists_name_lower",
        "artists",
        [sa.text("lower(name)")],
        unique=False,
    )

    # 2. Add composite album indexes for resolution and ordered listing
    op.create_index(
        "ix_albums_artist_title_lower",
        "albums",
        ["artist_id", sa.text("lower(title)")],
        unique=False,
    )
    op.create_index(
        "ix_albums_artist_year",
        "albums",
        ["artist_id", sa.text("year DESC")],
        unique=False,
    )

    # 3. Add composite track indexes for album tracklist and artist track listing
    op.create_index(
        "ix_tracks_album_disc_track",
        "tracks",
        ["album_id", "disc_number", "track_number"],
        unique=False,
    )
    op.create_index(
        "ix_tracks_artist_title",
        "tracks",
        ["artist_id", "title"],
        unique=False,
    )

    # 4. Add composite track_files index for preferred quality score ordering
    op.create_index(
        "ix_track_files_track_quality",
        "track_files",
        ["track_id", sa.text("quality_score DESC")],
        unique=False,
    )

    # 5. Add composite listening_events index for telemetry history ordering
    op.create_index(
        "ix_listening_events_track_timestamp",
        "listening_events",
        ["track_id", sa.text("timestamp_utc DESC")],
        unique=False,
    )

    # 6. Add composite metadata_fields index for provenance resolution ordering
    op.create_index(
        "ix_metadata_fields_entity_field_priority",
        "metadata_fields",
        ["entity_type", "entity_id", "field_name", sa.text("priority DESC"), "created_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index("ix_metadata_fields_entity_field_priority", table_name="metadata_fields")
    op.drop_index("ix_listening_events_track_timestamp", table_name="listening_events")
    op.drop_index("ix_track_files_track_quality", table_name="track_files")
    op.drop_index("ix_tracks_artist_title", table_name="tracks")
    op.drop_index("ix_tracks_album_disc_track", table_name="tracks")
    op.drop_index("ix_albums_artist_year", table_name="albums")
    op.drop_index("ix_albums_artist_title_lower", table_name="albums")

    op.drop_index("ix_artists_name_lower", table_name="artists")
    op.create_index(
        "ix_artists_name_lower",
        "artists",
        ["name"],
        unique=False,
    )
