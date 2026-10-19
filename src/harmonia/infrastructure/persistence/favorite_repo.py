"""SQLite repository implementation for track favorites."""

from __future__ import annotations

from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import desc, func, select

from harmonia.domain.entities.favorite import Favorite
from harmonia.infrastructure.persistence.models import FavoriteModel

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


class SqliteFavoriteRepository:
    """SQLite implementation of the FavoriteRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, favorite: Favorite) -> None:
        """Add or update a track favorite (idempotent)."""
        model = self._session.get(FavoriteModel, favorite.track_id)
        if model is not None:
            model.client_updated_at = favorite.client_updated_at
        else:
            self._session.add(_favorite_to_model(favorite))
        self._session.flush()

    def get_by_track_id(self, track_id: UUID) -> Favorite | None:
        """Retrieve a favorite record by track ID."""
        model = self._session.get(FavoriteModel, track_id)
        return _model_to_favorite(model) if model else None

    def is_favorite(self, track_id: UUID) -> bool:
        """Check if a track is favorited."""
        stmt = select(func.count()).select_from(FavoriteModel).where(FavoriteModel.track_id == track_id)
        count = self._session.scalar(stmt) or 0
        return count > 0

    def remove(self, track_id: UUID) -> bool:
        """Remove a favorite by track ID. Returns True if removed, False if absent."""
        model = self._session.get(FavoriteModel, track_id)
        if model is not None:
            self._session.delete(model)
            self._session.flush()
            return True
        return False

    def list_all(self, limit: int = 100, offset: int = 0) -> list[Favorite]:
        """List favorites ordered by client_updated_at descending, then track_id."""
        stmt = (
            select(FavoriteModel)
            .order_by(desc(FavoriteModel.client_updated_at), FavoriteModel.track_id)
            .limit(limit)
            .offset(offset)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_favorite(m) for m in models]

    def count(self) -> int:
        """Return total number of favorited tracks."""
        stmt = select(func.count()).select_from(FavoriteModel)
        return self._session.scalar(stmt) or 0


def _favorite_to_model(entity: Favorite) -> FavoriteModel:
    return FavoriteModel(
        track_id=entity.track_id,
        client_updated_at=entity.client_updated_at,
    )


def _model_to_favorite(model: FavoriteModel) -> Favorite:
    return Favorite(
        track_id=model.track_id if isinstance(model.track_id, UUID) else UUID(str(model.track_id)),
        client_updated_at=int(model.client_updated_at),
    )
