"""SQLAlchemy ORM models for Harmonia.

These models define the database schema. They map to domain entities
but are distinct objects — the domain layer does not depend on SQLAlchemy.
Conversion between domain entities and ORM models happens in the
repository implementations.
"""

from __future__ import annotations

from datetime import UTC, datetime
from uuid import uuid4

from sqlalchemy import (
    Boolean,
    CheckConstraint,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    String,
    Text,
    UniqueConstraint,
    Uuid,
    desc,
    func,
)
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    """Base class for all SQLAlchemy models."""

    pass


# ── Artist ────────────────────────────────────────────────────────────────────


class ArtistModel(Base):
    __tablename__ = "artists"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(500), nullable=False)
    sort_name: Mapped[str | None] = mapped_column(String(500))
    mbid: Mapped[str | None] = mapped_column(String(36), unique=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(UTC),
        onupdate=lambda: datetime.now(UTC),
    )

    # Relationships
    albums: Mapped[list[AlbumModel]] = relationship(back_populates="artist")
    tracks: Mapped[list[TrackModel]] = relationship(back_populates="artist")

    __table_args__ = (
        Index("ix_artists_name_lower", func.lower(name)),
        Index("ix_artists_sort_name", "sort_name"),
    )


# ── Album ─────────────────────────────────────────────────────────────────────


class AlbumModel(Base):
    __tablename__ = "albums"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    title: Mapped[str] = mapped_column(String(500), nullable=False)
    artist_id: Mapped[str | None] = mapped_column(
        Uuid(), ForeignKey("artists.id", ondelete="SET NULL")
    )
    year: Mapped[int | None] = mapped_column(Integer)
    total_tracks: Mapped[int | None] = mapped_column(Integer)
    total_discs: Mapped[int | None] = mapped_column(Integer)
    mbid: Mapped[str | None] = mapped_column(String(36), unique=True)
    art_path: Mapped[str | None] = mapped_column(String(1000))
    art_hash: Mapped[str | None] = mapped_column(String(64))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(UTC),
        onupdate=lambda: datetime.now(UTC),
    )

    # Relationships
    artist: Mapped[ArtistModel | None] = relationship(back_populates="albums")
    tracks: Mapped[list[TrackModel]] = relationship(back_populates="album")

    __table_args__ = (
        Index("ix_albums_title", "title"),
        Index("ix_albums_artist_id", "artist_id"),
        Index("ix_albums_year", "year"),
        Index("ix_albums_artist_title_lower", "artist_id", func.lower(title)),
        Index("ix_albums_artist_year", "artist_id", desc("year")),
    )


# ── Track ─────────────────────────────────────────────────────────────────────


class TrackModel(Base):
    __tablename__ = "tracks"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    title: Mapped[str] = mapped_column(String(500), nullable=False)
    artist_id: Mapped[str | None] = mapped_column(
        Uuid(), ForeignKey("artists.id", ondelete="SET NULL")
    )
    album_id: Mapped[str | None] = mapped_column(
        Uuid(), ForeignKey("albums.id", ondelete="SET NULL")
    )
    track_number: Mapped[int | None] = mapped_column(Integer)
    disc_number: Mapped[int | None] = mapped_column(Integer)
    duration_seconds: Mapped[float | None] = mapped_column(Float)
    year: Mapped[int | None] = mapped_column(Integer)
    genre: Mapped[str | None] = mapped_column(String(200))
    mbid: Mapped[str | None] = mapped_column(String(36), unique=True)
    lyrics: Mapped[str | None] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(UTC),
        onupdate=lambda: datetime.now(UTC),
    )

    # Relationships
    artist: Mapped[ArtistModel | None] = relationship(back_populates="tracks")
    album: Mapped[AlbumModel | None] = relationship(back_populates="tracks")
    files: Mapped[list[TrackFileModel]] = relationship(
        back_populates="track", cascade="all, delete-orphan"
    )
    tags: Mapped[list[TagModel]] = relationship(
        back_populates="track", cascade="all, delete-orphan"
    )
    listening_events: Mapped[list[ListeningEventModel]] = relationship(
        back_populates="track", cascade="all, delete-orphan"
    )

    __table_args__ = (
        Index("ix_tracks_title", "title"),
        Index("ix_tracks_artist_id", "artist_id"),
        Index("ix_tracks_album_id", "album_id"),
        Index("ix_tracks_genre", "genre"),
        Index("ix_tracks_album_disc_track", "album_id", "disc_number", "track_number"),
        Index("ix_tracks_artist_title", "artist_id", "title"),
    )


# ── TrackFile ─────────────────────────────────────────────────────────────────


class TrackFileModel(Base):
    __tablename__ = "track_files"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    track_id: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("tracks.id", ondelete="CASCADE"), nullable=False
    )
    file_path: Mapped[str] = mapped_column(String(2000), nullable=False, unique=True)
    file_hash: Mapped[str | None] = mapped_column(String(64))  # SHA-256
    file_size: Mapped[int] = mapped_column(Integer, nullable=False)
    format: Mapped[str] = mapped_column(String(10), nullable=False)  # AudioFormat value
    codec: Mapped[str | None] = mapped_column(String(20))
    bitrate: Mapped[int | None] = mapped_column(Integer)  # kbps
    sample_rate: Mapped[int | None] = mapped_column(Integer)  # Hz
    bit_depth: Mapped[int | None] = mapped_column(Integer)
    channels: Mapped[int | None] = mapped_column(Integer)
    duration_seconds: Mapped[float | None] = mapped_column(Float)
    is_lossless: Mapped[bool] = mapped_column(Boolean, default=False)
    quality_score: Mapped[int | None] = mapped_column(Integer)
    file_mtime: Mapped[float | None] = mapped_column(Float)
    channel_layout: Mapped[str | None] = mapped_column(String(50))
    encoder: Mapped[str | None] = mapped_column(String(100))
    added_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
    verified_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    # Relationships
    track: Mapped[TrackModel] = relationship(back_populates="files")
    audio_analysis: Mapped[AudioAnalysisModel | None] = relationship(
        back_populates="track_file", uselist=False, cascade="all, delete-orphan"
    )

    __table_args__ = (
        Index("ix_track_files_track_id", "track_id"),
        Index("ix_track_files_file_hash", "file_hash"),
        Index("ix_track_files_format", "format"),
        Index("ix_track_files_track_quality", "track_id", desc("quality_score")),
    )


# ── Tag ───────────────────────────────────────────────────────────────────────


class TagModel(Base):
    __tablename__ = "tags"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    track_id: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("tracks.id", ondelete="CASCADE"), nullable=False
    )
    name: Mapped[str] = mapped_column(String(100), nullable=False)
    value: Mapped[str] = mapped_column(String(500), nullable=False)
    source: Mapped[str] = mapped_column(String(20), nullable=False)  # TagSource value
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )

    # Relationships
    track: Mapped[TrackModel] = relationship(back_populates="tags")

    __table_args__ = (
        Index("ix_tags_track_id", "track_id"),
        Index("ix_tags_name_value", "name", "value"),
    )


# ── MetadataField ─────────────────────────────────────────────────────────────


class MetadataFieldModel(Base):
    __tablename__ = "metadata_fields"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    entity_type: Mapped[str] = mapped_column(String(20), nullable=False)
    entity_id: Mapped[str] = mapped_column(Uuid(), nullable=False)
    field_name: Mapped[str] = mapped_column(String(100), nullable=False)
    provider: Mapped[str] = mapped_column(String(50), nullable=False)
    raw_value: Mapped[str] = mapped_column(Text, nullable=False)
    normalized_value: Mapped[str] = mapped_column(Text, nullable=False)
    is_active: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    priority: Mapped[int] = mapped_column(Integer, nullable=False)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )

    __table_args__ = (
        Index("ix_metadata_fields_entity", "entity_type", "entity_id"),
        Index("ix_metadata_fields_entity_field", "entity_type", "entity_id", "field_name"),
        Index("ix_metadata_fields_provider", "provider"),
        Index(
            "ix_metadata_fields_entity_field_priority",
            "entity_type",
            "entity_id",
            "field_name",
            desc("priority"),
            "created_at",
        ),
    )


# ── DuplicateCandidate ────────────────────────────────────────────────────────


class DuplicateCandidateModel(Base):
    __tablename__ = "duplicate_candidates"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    track_id_a: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("tracks.id", ondelete="CASCADE"), nullable=False
    )
    track_id_b: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("tracks.id", ondelete="CASCADE"), nullable=False
    )
    confidence: Mapped[str] = mapped_column(String(20), nullable=False)
    reason: Mapped[str] = mapped_column(Text, nullable=False)
    resolved: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    resolution: Mapped[str | None] = mapped_column(String(20))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
    resolved_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))

    __table_args__ = (
        Index("ix_duplicate_candidates_track_a", "track_id_a"),
        Index("ix_duplicate_candidates_track_b", "track_id_b"),
        Index("ix_duplicate_candidates_unresolved", "resolved"),
    )


# ── AudioAnalysis ─────────────────────────────────────────────────────────────


class AudioAnalysisModel(Base):
    __tablename__ = "audio_analyses"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    track_file_id: Mapped[str] = mapped_column(
        Uuid(),
        ForeignKey("track_files.id", ondelete="CASCADE"),
        nullable=False,
        unique=True,
    )
    status: Mapped[str] = mapped_column(String(20), nullable=False, default="PENDING")
    integrated_lufs: Mapped[float | None] = mapped_column(Float)
    loudness_range_lu: Mapped[float | None] = mapped_column(Float)
    true_peak_dbtp: Mapped[float | None] = mapped_column(Float)
    sample_peak_dbfs: Mapped[float | None] = mapped_column(Float)
    peak_to_loudness_db: Mapped[float | None] = mapped_column(Float)
    clipping_samples: Mapped[int | None] = mapped_column(Integer)
    is_clipped: Mapped[bool] = mapped_column(Boolean, default=False, nullable=False)
    duration_analyzed: Mapped[float | None] = mapped_column(Float)
    silence_start_sec: Mapped[float | None] = mapped_column(Float)
    silence_end_sec: Mapped[float | None] = mapped_column(Float)
    analyzer_name: Mapped[str] = mapped_column(String(50), nullable=False, default="ffmpeg_ebur128")
    analyzer_version: Mapped[str] = mapped_column(String(20), nullable=False, default="1.0.0")
    config_version: Mapped[str] = mapped_column(String(20), nullable=False, default="1.0.0")
    file_hash: Mapped[str] = mapped_column(String(64), nullable=False)
    error_message: Mapped[str | None] = mapped_column(Text)
    started_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    completed_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    execution_time_sec: Mapped[float | None] = mapped_column(Float)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(UTC),
        onupdate=lambda: datetime.now(UTC),
    )

    track_file: Mapped[TrackFileModel] = relationship(back_populates="audio_analysis")

    __table_args__ = (
        Index("ix_audio_analyses_track_file_id", "track_file_id", unique=True),
        Index("ix_audio_analyses_status", "status"),
        Index("ix_audio_analyses_file_hash", "file_hash"),
        Index("ix_audio_analyses_started_at", "started_at"),
    )


# ── ListeningEvent ────────────────────────────────────────────────────────────


class ListeningEventModel(Base):
    __tablename__ = "listening_events"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    track_id: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("tracks.id", ondelete="CASCADE"), nullable=False
    )
    track_file_id: Mapped[str | None] = mapped_column(
        Uuid(), ForeignKey("track_files.id", ondelete="SET NULL"), nullable=True
    )
    timestamp_utc: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), nullable=False, default=lambda: datetime.now(UTC)
    )
    started_at_sec: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    ended_at_sec: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    duration_listened_seconds: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    track_duration_seconds: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    was_completed: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    was_skipped: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    playback_source: Mapped[str] = mapped_column(String(50), nullable=False, default="queue")
    device_id: Mapped[str | None] = mapped_column(Uuid(), nullable=True)
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )

    track: Mapped[TrackModel] = relationship(back_populates="listening_events")
    track_file: Mapped[TrackFileModel | None] = relationship()

    __table_args__ = (
        Index("ix_listening_events_track_id", "track_id"),
        Index("ix_listening_events_timestamp", "timestamp_utc"),
        Index("ix_listening_events_track_file_id", "track_file_id"),
        Index(
            "ix_listening_events_track_timestamp",
            "track_id",
            desc("timestamp_utc"),
        ),
    )


# ── PlaybackQueueSnapshot ─────────────────────────────────────────────────────


class PlaybackQueueSnapshotModel(Base):
    __tablename__ = "playback_queue_snapshots"

    id: Mapped[int] = mapped_column(Integer, primary_key=True, default=1)
    current_track_id: Mapped[str | None] = mapped_column(Uuid(), nullable=True)
    current_track_file_id: Mapped[str | None] = mapped_column(Uuid(), nullable=True)
    position_seconds: Mapped[float] = mapped_column(Float, nullable=False, default=0.0)
    volume: Mapped[float] = mapped_column(Float, nullable=False, default=1.0)
    repeat_mode: Mapped[str] = mapped_column(String(20), nullable=False, default="off")
    shuffle_enabled: Mapped[bool] = mapped_column(Boolean, nullable=False, default=False)
    queue_json: Mapped[str] = mapped_column(Text, nullable=False, default="[]")
    current_queue_index: Mapped[int] = mapped_column(Integer, nullable=False, default=-1)
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(UTC),
        onupdate=lambda: datetime.now(UTC),
    )


# ── Personalization (Playlists, PlaylistItems, Favorites, Ratings) ─────────────


class PlaylistModel(Base):
    __tablename__ = "playlists"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    name: Mapped[str] = mapped_column(String(200), nullable=False)
    description: Mapped[str | None] = mapped_column(String(1000))
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )
    updated_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True),
        default=lambda: datetime.now(UTC),
        onupdate=lambda: datetime.now(UTC),
    )

    # Relationships
    items: Mapped[list[PlaylistItemModel]] = relationship(
        back_populates="playlist",
        cascade="all, delete-orphan",
        order_by="PlaylistItemModel.position",
    )

    __table_args__ = (
        Index("ix_playlists_name_lower", func.lower(name)),
        Index("ix_playlists_updated_at", desc("updated_at")),
    )


class PlaylistItemModel(Base):
    __tablename__ = "playlist_items"

    id: Mapped[str] = mapped_column(Uuid(), primary_key=True, default=uuid4)
    playlist_id: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("playlists.id", ondelete="CASCADE"), nullable=False
    )
    track_id: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("tracks.id", ondelete="RESTRICT"), nullable=False
    )
    position: Mapped[int] = mapped_column(Integer, nullable=False)
    position_key: Mapped[str] = mapped_column(String(64), nullable=False)
    added_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), default=lambda: datetime.now(UTC)
    )

    # Relationships
    playlist: Mapped[PlaylistModel] = relationship(back_populates="items")
    track: Mapped[TrackModel] = relationship()

    __table_args__ = (
        UniqueConstraint("playlist_id", "position", name="uq_playlist_items_playlist_position"),
        Index("ix_playlist_items_playlist_id", "playlist_id"),
        Index("ix_playlist_items_track_id", "track_id"),
        Index("ix_playlist_items_playlist_position_key", "playlist_id", "position_key"),
    )


class FavoriteModel(Base):
    __tablename__ = "favorites"

    track_id: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("tracks.id", ondelete="RESTRICT"), primary_key=True
    )
    client_updated_at: Mapped[int] = mapped_column(Integer, nullable=False)

    # Relationships
    track: Mapped[TrackModel] = relationship()

    __table_args__ = (
        Index("ix_favorites_client_updated_at", desc("client_updated_at")),
    )


class RatingModel(Base):
    __tablename__ = "ratings"

    track_id: Mapped[str] = mapped_column(
        Uuid(), ForeignKey("tracks.id", ondelete="RESTRICT"), primary_key=True
    )
    rating: Mapped[int] = mapped_column(Integer, nullable=False)
    client_updated_at: Mapped[int] = mapped_column(Integer, nullable=False)

    # Relationships
    track: Mapped[TrackModel] = relationship()

    __table_args__ = (
        CheckConstraint("rating >= 1 AND rating <= 5", name="ck_ratings_rating_range"),
        Index("ix_ratings_rating", "rating"),
        Index("ix_ratings_client_updated_at", desc("client_updated_at")),
    )
