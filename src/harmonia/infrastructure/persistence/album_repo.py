"""SQLite repository implementation for albums."""

from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import func, select

from harmonia.domain.entities.album import Album
from harmonia.infrastructure.persistence.models import AlbumModel

if TYPE_CHECKING:
    from uuid import UUID

    from sqlalchemy.orm import Session


class SqliteAlbumRepository:
    """SQLite implementation of the AlbumRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, album: Album) -> None:
        model = _album_to_model(album)
        self._session.add(model)
        self._session.flush()

    def get_by_id(self, album_id: UUID) -> Album | None:
        model = self._session.get(AlbumModel, album_id)
        return _model_to_album(model) if model else None

    def get_all(
        self, limit: int = 100, offset: int = 0, artist_id: UUID | None = None
    ) -> list[Album]:
        stmt = select(AlbumModel)
        if artist_id is not None:
            stmt = stmt.where(AlbumModel.artist_id == artist_id)
        stmt = stmt.order_by(AlbumModel.title).limit(limit).offset(offset)
        models = self._session.scalars(stmt).all()
        return [_model_to_album(m) for m in models]

    def get_by_artist(self, artist_id: UUID) -> list[Album]:
        stmt = (
            select(AlbumModel)
            .where(AlbumModel.artist_id == artist_id)
            .order_by(AlbumModel.year.desc().nullslast())
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_album(m) for m in models]

    def find_by_title_and_artist(
        self, title: str, artist_id: UUID | None
    ) -> Album | None:
        stmt = select(AlbumModel).where(
            func.lower(AlbumModel.title) == title.lower()
        )
        if artist_id is not None:
            stmt = stmt.where(AlbumModel.artist_id == artist_id)
        else:
            stmt = stmt.where(AlbumModel.artist_id.is_(None))
        model = self._session.scalar(stmt)
        return _model_to_album(model) if model else None

    def update(self, album: Album) -> None:
        model = self._session.get(AlbumModel, album.id)
        if model is None:
            return
        model.title = album.title
        model.artist_id = album.artist_id
        model.year = album.year
        model.total_tracks = album.total_tracks
        model.total_discs = album.total_discs
        model.mbid = album.mbid
        model.art_path = album.art_path
        model.art_hash = album.art_hash
        model.updated_at = album.updated_at
        self._session.flush()

    def delete(self, album_id: UUID) -> None:
        model = self._session.get(AlbumModel, album_id)
        if model:
            self._session.delete(model)
            self._session.flush()

    def count(self, artist_id: UUID | None = None) -> int:
        stmt = select(func.count(AlbumModel.id))
        if artist_id is not None:
            stmt = stmt.where(AlbumModel.artist_id == artist_id)
        result = self._session.scalar(stmt)
        return result or 0


# ── Mapping ───────────────────────────────────────────────────────────────────


def _album_to_model(album: Album) -> AlbumModel:
    return AlbumModel(
        id=album.id,
        title=album.title,
        artist_id=album.artist_id,
        year=album.year,
        total_tracks=album.total_tracks,
        total_discs=album.total_discs,
        mbid=album.mbid,
        art_path=album.art_path,
        art_hash=album.art_hash,
        created_at=album.created_at,
        updated_at=album.updated_at,
    )


def _model_to_album(model: AlbumModel) -> Album:
    return Album(
        id=model.id,
        title=model.title,
        artist_id=model.artist_id,
        year=model.year,
        total_tracks=model.total_tracks,
        total_discs=model.total_discs,
        mbid=model.mbid,
        art_path=model.art_path,
        art_hash=model.art_hash,
        created_at=model.created_at,
        updated_at=model.updated_at,
    )

