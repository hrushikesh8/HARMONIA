"""SQLite repository implementation for track ratings."""

from __future__ import annotations

from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import desc, func, select

from harmonia.core.exceptions import ValidationError
from harmonia.domain.entities.rating import Rating
from harmonia.infrastructure.persistence.models import RatingModel

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


class SqliteRatingRepository:
    """SQLite implementation of the RatingRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def set_rating(self, rating: Rating) -> None:
        """Set or update a track rating (idempotent)."""
        if not (1 <= rating.rating <= 5):
            raise ValidationError(
                "rating", f"Rating must be between 1 and 5, got {rating.rating}"
            )

        model = self._session.get(RatingModel, rating.track_id)
        if model is not None:
            model.rating = rating.rating
            model.client_updated_at = rating.client_updated_at
        else:
            self._session.add(_rating_to_model(rating))
        self._session.flush()

    def get_by_track_id(self, track_id: UUID) -> Rating | None:
        """Retrieve a rating record by track ID."""
        model = self._session.get(RatingModel, track_id)
        return _model_to_rating(model) if model else None

    def remove(self, track_id: UUID) -> bool:
        """Remove a rating by track ID. Returns True if removed, False if absent."""
        model = self._session.get(RatingModel, track_id)
        if model is not None:
            self._session.delete(model)
            self._session.flush()
            return True
        return False

    def list_all(
        self,
        limit: int = 100,
        offset: int = 0,
        min_rating: int | None = None,
    ) -> list[Rating]:
        """List ratings ordered by client_updated_at descending, then track_id."""
        stmt = select(RatingModel)
        if min_rating is not None:
            stmt = stmt.where(RatingModel.rating >= min_rating)
        stmt = (
            stmt.order_by(desc(RatingModel.client_updated_at), RatingModel.track_id)
            .limit(limit)
            .offset(offset)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_rating(m) for m in models]

    def count(self, min_rating: int | None = None) -> int:
        """Return total number of rated tracks, optionally filtered by min_rating."""
        stmt = select(func.count()).select_from(RatingModel)
        if min_rating is not None:
            stmt = stmt.where(RatingModel.rating >= min_rating)
        return self._session.scalar(stmt) or 0


def _rating_to_model(entity: Rating) -> RatingModel:
    return RatingModel(
        track_id=entity.track_id,
        rating=entity.rating,
        client_updated_at=entity.client_updated_at,
    )


def _model_to_rating(model: RatingModel) -> Rating:
    return Rating(
        track_id=model.track_id if isinstance(model.track_id, UUID) else UUID(str(model.track_id)),
        rating=int(model.rating),
        client_updated_at=int(model.client_updated_at),
    )
