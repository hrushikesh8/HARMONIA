"""SQLite repository implementation for playlists and playlist items."""

from __future__ import annotations

from datetime import UTC, datetime
from typing import TYPE_CHECKING
from uuid import UUID

from sqlalchemy import desc, func, select

from harmonia.core.exceptions import EntityNotFoundError
from harmonia.domain.entities.playlist import Playlist, PlaylistItem
from harmonia.infrastructure.persistence.models import PlaylistItemModel, PlaylistModel

if TYPE_CHECKING:
    from sqlalchemy.orm import Session


class SqlitePlaylistRepository:
    """SQLite implementation of the PlaylistRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    # ── Playlist CRUD ─────────────────────────────────────────────────────────

    def add(self, playlist: Playlist) -> None:
        """Persist a new playlist."""
        model = _playlist_to_model(playlist)
        self._session.add(model)
        self._session.flush()

    def get_by_id(self, playlist_id: UUID) -> Playlist | None:
        """Retrieve a playlist by its ID, including its items ordered by position."""
        model = self._session.get(PlaylistModel, playlist_id)
        return _model_to_playlist(model) if model else None

    def get_by_name(self, name: str) -> list[Playlist]:
        """Retrieve all playlists matching a given name (case-insensitive)."""
        stmt = (
            select(PlaylistModel)
            .where(func.lower(PlaylistModel.name) == name.lower().strip())
            .order_by(desc(PlaylistModel.updated_at), PlaylistModel.id)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_playlist(m) for m in models]

    def list_all(self, limit: int = 100, offset: int = 0) -> list[Playlist]:
        """Retrieve playlists with pagination, ordered by updated_at descending."""
        stmt = (
            select(PlaylistModel)
            .order_by(desc(PlaylistModel.updated_at), PlaylistModel.id)
            .limit(limit)
            .offset(offset)
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_playlist(m) for m in models]

    def update(self, playlist: Playlist) -> None:
        """Update an existing playlist's name and description."""
        model = self._session.get(PlaylistModel, playlist.id)
        if model is None:
            raise EntityNotFoundError("Playlist", playlist.id)
        model.name = playlist.name
        model.description = playlist.description
        model.updated_at = datetime.now(UTC)
        self._session.flush()

    def delete(self, playlist_id: UUID) -> bool:
        """Remove a playlist and its items (cascade). Returns True if deleted."""
        model = self._session.get(PlaylistModel, playlist_id)
        if model is not None:
            self._session.delete(model)
            self._session.flush()
            return True
        return False

    def count(self) -> int:
        """Return the total number of playlists in the library."""
        stmt = select(func.count()).select_from(PlaylistModel)
        return self._session.scalar(stmt) or 0

    # ── Playlist items ────────────────────────────────────────────────────────

    def add_item(self, item: PlaylistItem) -> None:
        """Add an item to a playlist at its designated position."""
        model = _item_to_model(item)
        self._session.add(model)
        # Update parent playlist updated_at
        pl = self._session.get(PlaylistModel, item.playlist_id)
        if pl is not None:
            pl.updated_at = datetime.now(UTC)
        self._session.flush()

    def remove_item(self, playlist_id: UUID, item_id: UUID) -> bool:
        """Remove a specific item by item ID. Returns True if removed."""
        model = self._session.get(PlaylistItemModel, item_id)
        if model is not None and (
            model.playlist_id == playlist_id
            or (isinstance(model.playlist_id, UUID) and model.playlist_id == playlist_id)
            or UUID(str(model.playlist_id)) == playlist_id
        ):
            self._session.delete(model)
            pl = self._session.get(PlaylistModel, playlist_id)
            if pl is not None:
                pl.updated_at = datetime.now(UTC)
            self._session.flush()
            return True
        return False

    def get_items(self, playlist_id: UUID) -> list[PlaylistItem]:
        """Get all items in a playlist ordered by position ascending."""
        stmt = (
            select(PlaylistItemModel)
            .where(PlaylistItemModel.playlist_id == playlist_id)
            .order_by(PlaylistItemModel.position.asc(), PlaylistItemModel.id.asc())
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_item(m) for m in models]

    def count_items(self, playlist_id: UUID) -> int:
        """Return the total number of items in a playlist."""
        stmt = (
            select(func.count())
            .select_from(PlaylistItemModel)
            .where(PlaylistItemModel.playlist_id == playlist_id)
        )
        return self._session.scalar(stmt) or 0

    def move_item(self, playlist_id: UUID, item_id: UUID, new_position: int) -> None:
        """Move an item to new_position, adjusting other positions collision-safely.

        Uses a two-phase negative-offset update to prevent transient
        collisions with SQLite's UNIQUE(playlist_id, position) constraint.
        """
        stmt = (
            select(PlaylistItemModel)
            .where(PlaylistItemModel.playlist_id == playlist_id)
            .order_by(PlaylistItemModel.position.asc(), PlaylistItemModel.id.asc())
        )
        items = list(self._session.scalars(stmt).all())
        target_item = next(
            (
                m
                for m in items
                if (m.id == item_id or (isinstance(m.id, UUID) and m.id == item_id) or UUID(str(m.id)) == item_id)
            ),
            None,
        )
        if target_item is None:
            raise EntityNotFoundError("PlaylistItem", item_id)

        old_position = target_item.position
        if old_position == new_position:
            return

        # Determine affected items and their target positions
        affected_items: list[PlaylistItemModel] = []
        final_positions: dict[PlaylistItemModel, int] = {}

        if old_position < new_position:
            # Moving item down/forward: shift intervening items down by 1
            for m in items:
                if old_position < m.position <= new_position:
                    affected_items.append(m)
                    final_positions[m] = m.position - 1
            affected_items.append(target_item)
            final_positions[target_item] = new_position
        else:
            # Moving item up/backward: shift intervening items up by 1
            for m in items:
                if new_position <= m.position < old_position:
                    affected_items.append(m)
                    final_positions[m] = m.position + 1
            affected_items.append(target_item)
            final_positions[target_item] = new_position

        # Phase 1: Set temporary negative positions to guarantee zero collisions
        for idx, m in enumerate(affected_items):
            m.position = -1000 - idx
            m.position_key = f"tmp_{idx:04d}"
        self._session.flush()

        # Phase 2: Set final positive positions and matching position_keys
        for m in affected_items:
            final_pos = final_positions[m]
            m.position = final_pos
            m.position_key = f"{final_pos:08d}"
        self._session.flush()

        # Update parent playlist updated_at
        pl = self._session.get(PlaylistModel, playlist_id)
        if pl is not None:
            pl.updated_at = datetime.now(UTC)
        self._session.flush()


def _playlist_to_model(entity: Playlist) -> PlaylistModel:
    return PlaylistModel(
        id=entity.id,
        name=entity.name,
        description=entity.description,
        created_at=entity.created_at,
        updated_at=entity.updated_at,
    )


def _model_to_playlist(model: PlaylistModel) -> Playlist:
    items = [_model_to_item(m) for m in model.items] if model.items else []
    return Playlist(
        id=model.id if isinstance(model.id, UUID) else UUID(str(model.id)),
        name=str(model.name),
        description=str(model.description) if model.description is not None else None,
        created_at=model.created_at,
        updated_at=model.updated_at,
        items=items,
    )


def _item_to_model(entity: PlaylistItem) -> PlaylistItemModel:
    pos_key = entity.position_key or f"{entity.position:08d}"
    return PlaylistItemModel(
        id=entity.id,
        playlist_id=entity.playlist_id,
        track_id=entity.track_id,
        position=entity.position,
        position_key=pos_key,
        added_at=entity.added_at,
    )


def _model_to_item(model: PlaylistItemModel) -> PlaylistItem:
    return PlaylistItem(
        id=model.id if isinstance(model.id, UUID) else UUID(str(model.id)),
        playlist_id=model.playlist_id if isinstance(model.playlist_id, UUID) else UUID(str(model.playlist_id)),
        track_id=model.track_id if isinstance(model.track_id, UUID) else UUID(str(model.track_id)),
        position=int(model.position),
        position_key=str(model.position_key),
        added_at=model.added_at,
    )
