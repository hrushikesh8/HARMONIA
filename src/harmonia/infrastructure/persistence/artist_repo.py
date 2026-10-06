"""SQLite repository implementation for artists."""

from __future__ import annotations

from uuid import UUID

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from harmonia.domain.entities.artist import Artist
from harmonia.infrastructure.persistence.models import ArtistModel


class SqliteArtistRepository:
    """SQLite implementation of the ArtistRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, artist: Artist) -> None:
        model = _artist_to_model(artist)
        self._session.add(model)
        self._session.flush()

    def get_by_id(self, artist_id: UUID) -> Artist | None:
        model = self._session.get(ArtistModel, artist_id)
        return _model_to_artist(model) if model else None

    def get_all(self, limit: int = 100, offset: int = 0) -> list[Artist]:
        stmt = (
            select(ArtistModel)
            .order_by(ArtistModel.sort_name)
            .limit(limit)
            .offset(offset)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_artist(m) for m in models]

    def find_by_name(self, name: str) -> Artist | None:
        stmt = select(ArtistModel).where(
            func.lower(ArtistModel.name) == name.lower()
        )
        model = self._session.scalar(stmt)
        return _model_to_artist(model) if model else None

    def update(self, artist: Artist) -> None:
        model = self._session.get(ArtistModel, artist.id)
        if model is None:
            return
        model.name = artist.name
        model.sort_name = artist.sort_name
        model.mbid = artist.mbid
        model.updated_at = artist.updated_at
        self._session.flush()

    def delete(self, artist_id: UUID) -> None:
        model = self._session.get(ArtistModel, artist_id)
        if model:
            self._session.delete(model)
            self._session.flush()

    def count(self) -> int:
        result = self._session.scalar(select(func.count(ArtistModel.id)))
        return result or 0


# ── Mapping ───────────────────────────────────────────────────────────────────


def _artist_to_model(artist: Artist) -> ArtistModel:
    return ArtistModel(
        id=artist.id,
        name=artist.name,
        sort_name=artist.sort_name,
        mbid=artist.mbid,
        created_at=artist.created_at,
        updated_at=artist.updated_at,
    )


def _model_to_artist(model: ArtistModel) -> Artist:
    return Artist(
        id=model.id,
        name=model.name,
        sort_name=model.sort_name,
        mbid=model.mbid,
        created_at=model.created_at,
        updated_at=model.updated_at,
    )
