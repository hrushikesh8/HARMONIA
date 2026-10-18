"""SQLite repository implementation for tracks, track files, and tags."""

from __future__ import annotations

from pathlib import Path
from typing import TYPE_CHECKING

from sqlalchemy import func, select

from harmonia.core.types import TagSource
from harmonia.domain.entities.tag import Tag
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.models import TagModel, TrackFileModel, TrackModel

if TYPE_CHECKING:
    from uuid import UUID

    from sqlalchemy.orm import Session


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

    def get_all(
        self,
        limit: int = 100,
        offset: int = 0,
        artist_id: UUID | None = None,
        album_id: UUID | None = None,
        genre: str | None = None,
    ) -> list[Track]:
        stmt = select(TrackModel)
        if artist_id is not None:
            stmt = stmt.where(TrackModel.artist_id == artist_id)
        if album_id is not None:
            stmt = stmt.where(TrackModel.album_id == album_id)
        if genre is not None:
            stmt = stmt.where(func.lower(TrackModel.genre) == genre.lower())

        stmt = stmt.order_by(TrackModel.title).limit(limit).offset(offset)
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

    def count(
        self,
        artist_id: UUID | None = None,
        album_id: UUID | None = None,
        genre: str | None = None,
    ) -> int:
        stmt = select(func.count(TrackModel.id))
        if artist_id is not None:
            stmt = stmt.where(TrackModel.artist_id == artist_id)
        if album_id is not None:
            stmt = stmt.where(TrackModel.album_id == album_id)
        if genre is not None:
            stmt = stmt.where(func.lower(TrackModel.genre) == genre.lower())
        result = self._session.scalar(stmt)
        return result or 0

    def count_files(self) -> int:
        result = self._session.scalar(select(func.count(TrackFileModel.id)))
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

    def get_file_by_id(self, file_id: UUID) -> TrackFile | None:
        stmt = select(TrackFileModel).where(TrackFileModel.id == file_id)
        model = self._session.scalar(stmt)
        return _model_to_track_file(model) if model else None

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

    def update_file(self, track_file: TrackFile) -> None:
        model = self._session.get(TrackFileModel, track_file.id)
        if model is None:
            return
        model.file_path = track_file.file_path
        model.file_hash = track_file.file_hash
        model.file_size = track_file.file_size
        model.format = track_file.format.value
        model.codec = track_file.codec
        model.bitrate = track_file.bitrate
        model.sample_rate = track_file.sample_rate
        model.bit_depth = track_file.bit_depth
        model.channels = track_file.channels
        model.duration_seconds = track_file.duration_seconds
        model.is_lossless = track_file.is_lossless
        model.quality_score = track_file.quality_score
        model.file_mtime = track_file.file_mtime
        model.channel_layout = track_file.channel_layout
        model.encoder = track_file.encoder
        model.verified_at = track_file.verified_at
        self._session.flush()

    def delete_file(self, file_id: UUID) -> bool:
        model = self._session.get(TrackFileModel, file_id)
        if model is None:
            return False
        self._session.delete(model)
        self._session.flush()
        return True

    def get_files_by_directory_prefix(self, prefix: str) -> list[TrackFile]:
        """Look up all track files whose file_path is within the directory prefix.

        Applies SQL-level path prefix filtering for ASCII prefixes to avoid scanning
        the entire database in Python, then verifies Path.resolve().is_relative_to() on
        matching candidates for strict safety.

        For non-ASCII prefixes, falls back to full candidate retrieval because SQLite's
        built-in LIKE operator only performs case-folding on ASCII (a-z) and would produce
        false negatives on Unicode case variations (e.g. 'É' vs 'é').
        """
        raw_str = str(prefix).strip()
        if not raw_str:
            return []

        target_dir = Path(prefix).resolve()
        resolved_str = str(target_dir)

        # Check if the query prefix contains any non-ASCII characters.
        # SQLite's built-in LIKE operator only performs case-insensitive matching for ASCII.
        # For non-ASCII prefixes, LIKE could silently drop matches where casing differs
        # (e.g. 'édith_piaf' vs 'Édith_Piaf'). In that case, we fall back to scanning
        # all track files and relying on Python's path verification.
        is_ascii = raw_str.isascii() and resolved_str.isascii()

        if is_ascii:
            posix_p = resolved_str.replace("\\", "/").rstrip("/")
            win_p = resolved_str.replace("/", "\\").rstrip("\\")
            raw_posix = raw_str.replace("\\", "/").rstrip("/")
            raw_win = raw_str.replace("/", "\\").rstrip("\\")

            stmt = select(TrackFileModel).where(
                TrackFileModel.file_path.like(posix_p + "/%")
                | TrackFileModel.file_path.like(win_p + "\\%")
                | TrackFileModel.file_path.like(raw_posix + "/%")
                | TrackFileModel.file_path.like(raw_win + "\\%")
                | (TrackFileModel.file_path == posix_p)
                | (TrackFileModel.file_path == win_p)
                | (TrackFileModel.file_path == raw_posix)
                | (TrackFileModel.file_path == raw_win)
            )
        else:
            stmt = select(TrackFileModel)

        models = self._session.scalars(stmt).all()
        results: list[TrackFile] = []
        for m in models:
            try:
                if Path(m.file_path).resolve().is_relative_to(target_dir):
                    results.append(_model_to_track_file(m))
            except (ValueError, OSError):
                pass
        return results

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
        file_path=str(tf.file_path),
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
        file_mtime=tf.file_mtime,
        channel_layout=tf.channel_layout,
        encoder=tf.encoder,
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
        file_mtime=model.file_mtime,
        channel_layout=model.channel_layout,
        encoder=model.encoder,
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
