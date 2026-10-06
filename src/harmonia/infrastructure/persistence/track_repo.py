"""SQLite repository implementation for tracks, track files, and tags."""

from __future__ import annotations

from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from harmonia.core.types import TagSource
from harmonia.domain.entities.tag import Tag
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.models import TagModel, TrackFileModel, TrackModel


class SqliteTrackRepository:
    """SQLite implementation of the TrackRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    # ── Track CRUD ────────────────────────────────────────────────────────

    def add(self, track: Track) -> None:
        model = _track_to_model(track)
        self._session.add(model)
        self._session.flush()

    def get_by_id(self, track_id: UUID) -> Track | None:
        model = self._session.get(TrackModel, track_id)
        return _model_to_track(model) if model else None

    def get_all(self, limit: int = 100, offset: int = 0) -> list[Track]:
        stmt = (
            select(TrackModel)
            .order_by(TrackModel.title)
            .limit(limit)
            .offset(offset)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_track(m) for m in models]

    def get_by_album(self, album_id: UUID) -> list[Track]:
        stmt = (
            select(TrackModel)
            .where(TrackModel.album_id == album_id)
            .order_by(TrackModel.disc_number, TrackModel.track_number)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_track(m) for m in models]

    def get_by_artist(self, artist_id: UUID) -> list[Track]:
        stmt = (
            select(TrackModel)
            .where(TrackModel.artist_id == artist_id)
            .order_by(TrackModel.title)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_track(m) for m in models]

    def update(self, track: Track) -> None:
        model = self._session.get(TrackModel, track.id)
        if model is None:
            return
        model.title = track.title
        model.artist_id = track.artist_id
        model.album_id = track.album_id
        model.track_number = track.track_number
        model.disc_number = track.disc_number
        model.duration_seconds = track.duration_seconds
        model.year = track.year
        model.genre = track.genre
        model.mbid = track.mbid
        model.lyrics = track.lyrics
        model.updated_at = track.updated_at
        self._session.flush()

    def delete(self, track_id: UUID) -> None:
        model = self._session.get(TrackModel, track_id)
        if model:
            self._session.delete(model)
            self._session.flush()

    def count(self) -> int:
        result = self._session.scalar(select(func.count(TrackModel.id)))
        return result or 0

    # ── Track files ───────────────────────────────────────────────────────

    def add_file(self, track_file: TrackFile) -> None:
        model = _track_file_to_model(track_file)
        self._session.add(model)
        self._session.flush()

    def get_files(self, track_id: UUID) -> list[TrackFile]:
        stmt = (
            select(TrackFileModel)
            .where(TrackFileModel.track_id == track_id)
            .order_by(TrackFileModel.quality_score.desc().nullslast())
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_track_file(m) for m in models]

    def get_file_by_path(self, file_path: str) -> TrackFile | None:
        stmt = select(TrackFileModel).where(TrackFileModel.file_path == file_path)
        model = self._session.scalar(stmt)
        return _model_to_track_file(model) if model else None

    def file_exists(self, file_path: str) -> bool:
        stmt = select(func.count(TrackFileModel.id)).where(TrackFileModel.file_path == file_path)
        count = self._session.scalar(stmt)
        return bool(count and count > 0)

    def get_file_by_hash(self, file_hash: str) -> TrackFile | None:
        stmt = select(TrackFileModel).where(TrackFileModel.file_hash == file_hash)
        model = self._session.scalar(stmt)
        return _model_to_track_file(model) if model else None

    # ── Tags ──────────────────────────────────────────────────────────────

    def add_tag(self, tag: Tag) -> None:
        model = _tag_to_model(tag)
        self._session.add(model)
        self._session.flush()

    def get_tags(self, track_id: UUID) -> list[Tag]:
        stmt = (
            select(TagModel)
            .where(TagModel.track_id == track_id)
            .order_by(TagModel.name)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_tag(m) for m in models]

    def delete_tags(self, track_id: UUID, source: str | None = None) -> None:
        stmt = select(TagModel).where(TagModel.track_id == track_id)
        if source is not None:
            stmt = stmt.where(TagModel.source == source)
        tags = self._session.scalars(stmt).all()
        for tag in tags:
            self._session.delete(tag)
        self._session.flush()


# ── Mapping functions ─────────────────────────────────────────────────────────
# Convert between domain entities and ORM models. Keeps the domain layer
# independent of SQLAlchemy.


def _track_to_model(track: Track) -> TrackModel:
    return TrackModel(
        id=track.id,
        title=track.title,
        artist_id=track.artist_id,
        album_id=track.album_id,
        track_number=track.track_number,
        disc_number=track.disc_number,
        duration_seconds=track.duration_seconds,
        year=track.year,
        genre=track.genre,
        mbid=track.mbid,
        lyrics=track.lyrics,
        created_at=track.created_at,
        updated_at=track.updated_at,
    )


def _model_to_track(model: TrackModel) -> Track:
    return Track(
        id=model.id,
        title=model.title,
        artist_id=model.artist_id,
        album_id=model.album_id,
        track_number=model.track_number,
        disc_number=model.disc_number,
        duration_seconds=model.duration_seconds,
        year=model.year,
        genre=model.genre,
        mbid=model.mbid,
        lyrics=model.lyrics,
        created_at=model.created_at,
        updated_at=model.updated_at,
    )


def _track_file_to_model(tf: TrackFile) -> TrackFileModel:
    return TrackFileModel(
        id=tf.id,
        track_id=tf.track_id,
        file_path=tf.file_path,
        file_hash=tf.file_hash,
        file_size=tf.file_size,
        format=tf.format.value,
        codec=tf.codec,
        bitrate=tf.bitrate,
        sample_rate=tf.sample_rate,
        bit_depth=tf.bit_depth,
        channels=tf.channels,
        duration_seconds=tf.duration_seconds,
        is_lossless=tf.is_lossless,
        quality_score=tf.quality_score,
        added_at=tf.added_at,
        verified_at=tf.verified_at,
    )


def _model_to_track_file(model: TrackFileModel) -> TrackFile:
    return TrackFile(
        id=model.id,
        track_id=model.track_id,
        file_path=model.file_path,
        file_hash=model.file_hash,
        file_size=model.file_size,
        format=AudioFormat(model.format),
        codec=model.codec,
        bitrate=model.bitrate,
        sample_rate=model.sample_rate,
        bit_depth=model.bit_depth,
        channels=model.channels,
        duration_seconds=model.duration_seconds,
        is_lossless=model.is_lossless,
        quality_score=model.quality_score,
        added_at=model.added_at,
        verified_at=model.verified_at,
    )


def _tag_to_model(tag: Tag) -> TagModel:
    return TagModel(
        id=tag.id,
        track_id=tag.track_id,
        name=tag.name,
        value=tag.value,
        source=tag.source.value,
        created_at=tag.created_at,
    )


def _model_to_tag(model: TagModel) -> Tag:
    return Tag(
        id=model.id,
        track_id=model.track_id,
        name=model.name,
        value=model.value,
        source=TagSource(model.source),
        created_at=model.created_at,
    )
