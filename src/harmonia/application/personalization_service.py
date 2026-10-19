"""Personalization application service for playlists, favorites, and ratings.

Coordinates domain entities, repository adapters, transaction boundaries,
deterministic clock timestamps, and best-effort domain event publishing.
"""

from __future__ import annotations

import unicodedata
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import select

from harmonia.application.personalization_dto import (
    FavoriteSummary,
    PlaylistCreate,
    PlaylistDetail,
    PlaylistItemCreate,
    PlaylistItemSummary,
    PlaylistSummary,
    PlaylistUpdate,
    RatingSummary,
)
from harmonia.core.clock import Clock, SystemClock
from harmonia.core.events import (
    Event,
    EventBus,
    PlaylistItemAdded,
    PlaylistItemMoved,
    PlaylistItemRemoved,
    PlaylistCreated,
    PlaylistDeleted,
    PlaylistUpdated,
    TrackFavorited,
    TrackRated,
    TrackRatingRemoved,
    TrackUnfavorited,
)
from harmonia.core.events import event_bus as global_event_bus
from harmonia.core.exceptions import EntityNotFoundError, ValidationError
from harmonia.core.logging import get_logger
from harmonia.domain.entities.favorite import Favorite
from harmonia.domain.entities.playlist import Playlist, PlaylistItem
from harmonia.domain.entities.rating import Rating
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.favorite_repo import SqliteFavoriteRepository
from harmonia.infrastructure.persistence.models import PlaylistItemModel
from harmonia.infrastructure.persistence.playlist_repo import SqlitePlaylistRepository
from harmonia.infrastructure.persistence.rating_repo import SqliteRatingRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

    from harmonia.domain.entities.track import Track
    from harmonia.domain.ports.artist_repository import ArtistRepository
    from harmonia.domain.ports.favorite_repository import FavoriteRepository
    from harmonia.domain.ports.playlist_repository import PlaylistRepository
    from harmonia.domain.ports.rating_repository import RatingRepository
    from harmonia.domain.ports.track_repository import TrackRepository

logger = get_logger("harmonia.personalization")


class PersonalizationService:
    """Application use cases for playlist curation, track favorites, and ratings.

    Transaction ownership:
        The service operates within the caller-provided SQLAlchemy ``Session``.
        When ``autocommit=True`` (the default, consistent with Harmonia's state-mutating
        application services), each atomic use case commits its unit of work on success
        before publishing domain events. If any step fails, changes remain uncommitted
        and can be rolled back.
        When ``autocommit=False``, the service only flushes mutations to validate constraints,
        allowing the caller to coordinate broader transactions; events are deferred to
        prevent false success notifications before the caller's commit.
    """

    def __init__(
        self,
        session: Session,
        track_repo: TrackRepository | None = None,
        playlist_repo: PlaylistRepository | None = None,
        favorite_repo: FavoriteRepository | None = None,
        rating_repo: RatingRepository | None = None,
        artist_repo: ArtistRepository | None = None,
        clock: Clock | None = None,
        event_bus: EventBus | None = None,
        autocommit: bool = True,
    ) -> None:
        self._session = session
        self._track_repo = track_repo or SqliteTrackRepository(session)
        self._playlist_repo = playlist_repo or SqlitePlaylistRepository(session)
        self._favorite_repo = favorite_repo or SqliteFavoriteRepository(session)
        self._rating_repo = rating_repo or SqliteRatingRepository(session)
        self._artist_repo = artist_repo or SqliteArtistRepository(session)
        self._clock = clock or SystemClock()
        self._event_bus = event_bus if event_bus is not None else global_event_bus
        self._autocommit = autocommit

    def _commit_and_publish(self, event: Event | None = None) -> None:
        """Commit the current unit of work and emit the corresponding domain event.

        Events are strictly emitted only after persistence commits successfully.
        """
        if self._autocommit:
            self._session.commit()
            if event is not None and self._event_bus is not None:
                self._event_bus.publish(event)
        else:
            self._session.flush()

    # ── Playlist Use Cases ────────────────────────────────────────────────────

    def create_playlist(self, data: PlaylistCreate) -> PlaylistDetail:
        """Create a new playlist.

        Validates name constraints, normalizes text to Unicode NFC, and persists
        the empty playlist. Duplicate playlist names are permitted by schema.
        """
        clean_name = unicodedata.normalize("NFC", data.name).strip()
        if not clean_name:
            raise ValidationError("name", "Playlist name cannot be empty or whitespace only.")
        if len(clean_name) > 200:
            raise ValidationError("name", "Playlist name must not exceed 200 characters.")

        clean_desc = None
        if data.description is not None:
            clean_desc = unicodedata.normalize("NFC", data.description).strip() or None

        now = self._clock.now()
        pl = Playlist(
            name=clean_name,
            description=clean_desc,
            created_at=now,
            updated_at=now,
        )
        self._playlist_repo.add(pl)
        self._commit_and_publish(PlaylistCreated(playlist_id=pl.id, name=pl.name))
        return self._to_playlist_detail(pl)

    def get_playlist(self, playlist_id: UUID) -> PlaylistDetail | None:
        """Retrieve full playlist details including ordered items and resolved track metadata."""
        pl = self._playlist_repo.get_by_id(playlist_id)
        if pl is None:
            return None
        return self._to_playlist_detail(pl)

    def list_playlists(self, limit: int = 100, offset: int = 0) -> list[PlaylistSummary]:
        """List playlists with pagination ordered by updated_at descending."""
        if limit <= 0:
            raise ValidationError("limit", "Limit must be a positive integer.")
        if offset < 0:
            raise ValidationError("offset", "Offset must be non-negative.")

        playlists = self._playlist_repo.list_all(limit=limit, offset=offset)
        return [self._to_playlist_summary(p) for p in playlists]

    def update_playlist(self, playlist_id: UUID, data: PlaylistUpdate) -> PlaylistDetail:
        """Update an existing playlist's name and/or description."""
        pl = self._playlist_repo.get_by_id(playlist_id)
        if pl is None:
            raise EntityNotFoundError("Playlist", playlist_id)

        if data.name is not None:
            clean_name = unicodedata.normalize("NFC", data.name).strip()
            if not clean_name:
                raise ValidationError("name", "Playlist name cannot be empty or whitespace only.")
            if len(clean_name) > 200:
                raise ValidationError("name", "Playlist name must not exceed 200 characters.")
            pl.name = clean_name

        if data.description is not None:
            clean_desc = unicodedata.normalize("NFC", data.description).strip()
            pl.description = clean_desc if clean_desc else None

        pl.updated_at = self._clock.now()
        self._playlist_repo.update(pl)
        self._commit_and_publish(PlaylistUpdated(playlist_id=pl.id, name=pl.name))
        return self._to_playlist_detail(pl)

    def delete_playlist(self, playlist_id: UUID) -> bool:
        """Delete a playlist and all its items (cascade). Returns True if deleted."""
        pl = self._playlist_repo.get_by_id(playlist_id)
        if pl is None:
            return False

        deleted = self._playlist_repo.delete(playlist_id)
        if deleted:
            self._commit_and_publish(PlaylistDeleted(playlist_id=playlist_id))
        return deleted

    # ── Playlist Item Use Cases ───────────────────────────────────────────────

    def add_playlist_item(
        self,
        playlist_id: UUID,
        data: PlaylistItemCreate,
    ) -> PlaylistItemSummary:
        """Add a track item to a playlist.

        Verifies playlist and track existence. If position is unspecified, appends
        at max(position) + 1 (or 0 if empty). If an occupied position is specified,
        existing items at and above that position are collision-safely shifted up.
        Duplicate tracks within the same playlist are preserved with distinct item IDs.
        """
        pl = self._playlist_repo.get_by_id(playlist_id)
        if pl is None:
            raise EntityNotFoundError("Playlist", playlist_id)

        track = self._track_repo.get_by_id(data.track_id)
        if track is None:
            raise EntityNotFoundError("Track", data.track_id)

        existing_items = self._playlist_repo.get_items(playlist_id)
        if data.position is None:
            target_pos = max((item.position for item in existing_items), default=-1) + 1
        else:
            if data.position < 0:
                raise ValidationError("position", "Position must be non-negative.")
            target_pos = data.position
            if any(item.position == target_pos for item in existing_items):
                self._shift_items_for_insert(playlist_id, target_pos)

        item = PlaylistItem(
            playlist_id=playlist_id,
            track_id=data.track_id,
            position=target_pos,
            added_at=self._clock.now(),
        )
        self._playlist_repo.add_item(item)
        event = PlaylistItemAdded(
            playlist_id=playlist_id,
            item_id=item.id,
            track_id=item.track_id,
            position=item.position,
        )
        self._commit_and_publish(event)
        return self._to_item_summary(item, track)

    def get_playlist_items(self, playlist_id: UUID) -> list[PlaylistItemSummary]:
        """List all items in a playlist ordered by position ascending."""
        pl = self._playlist_repo.get_by_id(playlist_id)
        if pl is None:
            raise EntityNotFoundError("Playlist", playlist_id)

        items = self._playlist_repo.get_items(playlist_id)
        return [self._to_item_summary(item) for item in items]

    def remove_playlist_item(self, playlist_id: UUID, item_id: UUID) -> bool:
        """Remove a specific item from a playlist. Returns True if removed."""
        pl = self._playlist_repo.get_by_id(playlist_id)
        if pl is None:
            raise EntityNotFoundError("Playlist", playlist_id)

        removed = self._playlist_repo.remove_item(playlist_id, item_id)
        if removed:
            self._commit_and_publish(
                PlaylistItemRemoved(playlist_id=playlist_id, item_id=item_id)
            )
        return removed

    def move_playlist_item(
        self,
        playlist_id: UUID,
        item_id: UUID,
        new_position: int,
    ) -> None:
        """Move an item to a new position, collision-safely adjusting intervening items."""
        pl = self._playlist_repo.get_by_id(playlist_id)
        if pl is None:
            raise EntityNotFoundError("Playlist", playlist_id)

        if new_position < 0:
            raise ValidationError("new_position", "Position must be non-negative.")

        self._playlist_repo.move_item(playlist_id, item_id, new_position)
        event = PlaylistItemMoved(
            playlist_id=playlist_id,
            item_id=item_id,
            new_position=new_position,
        )
        self._commit_and_publish(event)

    def _shift_items_for_insert(self, playlist_id: UUID, at_position: int) -> None:
        """Shift items at position >= at_position forward by 1 collision-safely."""
        stmt = (
            select(PlaylistItemModel)
            .where(
                PlaylistItemModel.playlist_id == playlist_id,
                PlaylistItemModel.position >= at_position,
            )
            .order_by(PlaylistItemModel.position.asc())
        )
        models = list(self._session.scalars(stmt).all())
        if not models:
            return

        target_positions = {m: m.position + 1 for m in models}

        # Phase 1: assign temporary negative positions to eliminate unique collision
        for idx, m in enumerate(models):
            m.position = -2000 - idx
        self._session.flush()

        # Phase 2: assign shifted positions and update position_key
        for m in models:
            m.position = target_positions[m]
            m.position_key = f"{m.position:08d}"
        self._session.flush()

    # ── Favorite Use Cases ────────────────────────────────────────────────────

    def add_favorite(self, track_id: UUID) -> FavoriteSummary:
        """Mark a track as a user favorite (idempotent)."""
        track = self._track_repo.get_by_id(track_id)
        if track is None:
            raise EntityNotFoundError("Track", track_id)

        client_updated_at = int(self._clock.now().timestamp() * 1000)
        fav = Favorite(track_id=track_id, client_updated_at=client_updated_at)
        self._favorite_repo.add(fav)
        self._commit_and_publish(TrackFavorited(track_id=track_id))
        return self._to_favorite_summary(fav, track)

    def remove_favorite(self, track_id: UUID) -> bool:
        """Remove a track favorite. Returns True if removed, False if absent."""
        removed = self._favorite_repo.remove(track_id)
        if removed:
            self._commit_and_publish(TrackUnfavorited(track_id=track_id))
        return removed

    def is_favorite(self, track_id: UUID) -> bool:
        """Check whether a track is currently favorited."""
        return self._favorite_repo.is_favorite(track_id)

    def list_favorites(self, limit: int = 100, offset: int = 0) -> list[FavoriteSummary]:
        """List favorite tracks with pagination ordered by client_updated_at descending."""
        if limit <= 0:
            raise ValidationError("limit", "Limit must be a positive integer.")
        if offset < 0:
            raise ValidationError("offset", "Offset must be non-negative.")

        favorites = self._favorite_repo.list_all(limit=limit, offset=offset)
        return [self._to_favorite_summary(f) for f in favorites]

    # ── Rating Use Cases ──────────────────────────────────────────────────────

    def set_rating(self, track_id: UUID, rating: int) -> RatingSummary:
        """Set or update a track rating (1-5 stars)."""
        if not (1 <= rating <= 5):
            raise ValidationError(
                "rating", f"Rating must be an integer between 1 and 5, got {rating}."
            )

        track = self._track_repo.get_by_id(track_id)
        if track is None:
            raise EntityNotFoundError("Track", track_id)

        client_updated_at = int(self._clock.now().timestamp() * 1000)
        r = Rating(track_id=track_id, rating=rating, client_updated_at=client_updated_at)
        self._rating_repo.set_rating(r)
        self._commit_and_publish(TrackRated(track_id=track_id, rating=rating))
        return self._to_rating_summary(r, track)

    def get_rating(self, track_id: UUID) -> RatingSummary | None:
        """Retrieve a track rating, or None if unrated."""
        r = self._rating_repo.get_by_track_id(track_id)
        if r is None:
            return None
        return self._to_rating_summary(r)

    def remove_rating(self, track_id: UUID) -> bool:
        """Remove a track rating. Returns True if removed, False if absent."""
        removed = self._rating_repo.remove(track_id)
        if removed:
            self._commit_and_publish(TrackRatingRemoved(track_id=track_id))
        return removed

    def list_ratings(
        self,
        limit: int = 100,
        offset: int = 0,
        min_rating: int | None = None,
    ) -> list[RatingSummary]:
        """List ratings with pagination and optional min_rating filter."""
        if limit <= 0:
            raise ValidationError("limit", "Limit must be a positive integer.")
        if offset < 0:
            raise ValidationError("offset", "Offset must be non-negative.")
        if min_rating is not None and not (1 <= min_rating <= 5):
            raise ValidationError(
                "min_rating",
                f"min_rating must be an integer between 1 and 5, got {min_rating}.",
            )

        ratings = self._rating_repo.list_all(
            limit=limit, offset=offset, min_rating=min_rating
        )
        return [self._to_rating_summary(r) for r in ratings]

    # ── DTO Conversion Helpers ────────────────────────────────────────────────

    def _to_playlist_summary(self, pl: Playlist) -> PlaylistSummary:
        return PlaylistSummary(
            id=pl.id,
            name=pl.name,
            description=pl.description,
            created_at=pl.created_at,
            updated_at=pl.updated_at,
            track_count=pl.track_count,
        )

    def _to_playlist_detail(self, pl: Playlist) -> PlaylistDetail:
        items = [self._to_item_summary(it) for it in pl.items]
        total_duration = sum(it.duration_seconds or 0.0 for it in items)
        return PlaylistDetail(
            id=pl.id,
            name=pl.name,
            description=pl.description,
            created_at=pl.created_at,
            updated_at=pl.updated_at,
            track_count=len(items),
            total_duration_seconds=total_duration,
            items=items,
        )

    def _to_item_summary(
        self,
        item: PlaylistItem,
        track: Track | None = None,
    ) -> PlaylistItemSummary:
        if track is None:
            track = self._track_repo.get_by_id(item.track_id)

        track_title = track.title if track else None
        track_artist = None
        duration_seconds = track.duration_seconds if track else None

        if track and track.artist_id and self._artist_repo:
            artist = self._artist_repo.get_by_id(track.artist_id)
            if artist:
                track_artist = artist.name

        return PlaylistItemSummary(
            id=item.id,
            playlist_id=item.playlist_id,
            track_id=item.track_id,
            position=item.position,
            position_key=item.position_key,
            added_at=item.added_at,
            track_title=track_title,
            track_artist=track_artist,
            duration_seconds=duration_seconds,
        )

    def _to_favorite_summary(
        self,
        fav: Favorite,
        track: Track | None = None,
    ) -> FavoriteSummary:
        if track is None:
            track = self._track_repo.get_by_id(fav.track_id)

        track_title = track.title if track else None
        track_artist = None
        if track and track.artist_id and self._artist_repo:
            artist = self._artist_repo.get_by_id(track.artist_id)
            if artist:
                track_artist = artist.name

        return FavoriteSummary(
            track_id=fav.track_id,
            client_updated_at=fav.client_updated_at,
            track_title=track_title,
            track_artist=track_artist,
        )

    def _to_rating_summary(
        self,
        rating: Rating,
        track: Track | None = None,
    ) -> RatingSummary:
        if track is None:
            track = self._track_repo.get_by_id(rating.track_id)

        track_title = track.title if track else None
        track_artist = None
        if track and track.artist_id and self._artist_repo:
            artist = self._artist_repo.get_by_id(track.artist_id)
            if artist:
                track_artist = artist.name

        return RatingSummary(
            track_id=rating.track_id,
            rating=rating.rating,
            client_updated_at=rating.client_updated_at,
            track_title=track_title,
            track_artist=track_artist,
        )
