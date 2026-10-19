"""Playlist repository port — interface for playlist persistence."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.playlist import Playlist, PlaylistItem


class PlaylistRepository(Protocol):
    """Port for persisting and querying playlists and playlist items."""

    def add(self, playlist: Playlist) -> None:
        """Persist a new playlist (without items)."""
        ...

    def get_by_id(self, playlist_id: UUID) -> Playlist | None:
        """Retrieve a playlist by its ID, including its items ordered by position."""
        ...

    def get_by_name(self, name: str) -> list[Playlist]:
        """Retrieve all playlists matching a given name (case-insensitive)."""
        ...

    def list_all(self, limit: int = 100, offset: int = 0) -> list[Playlist]:
        """Retrieve playlists with pagination, ordered by updated_at desc."""
        ...

    def update(self, playlist: Playlist) -> None:
        """Update an existing playlist's name and description."""
        ...

    def delete(self, playlist_id: UUID) -> bool:
        """Remove a playlist and its items (cascade). Returns True if deleted."""
        ...

    def count(self) -> int:
        """Return the total number of playlists in the library."""
        ...

    # ── Playlist items ────────────────────────────────────────────────────────

    def add_item(self, item: PlaylistItem) -> None:
        """Add an item to a playlist at its designated position."""
        ...

    def remove_item(self, playlist_id: UUID, item_id: UUID) -> bool:
        """Remove a specific item by item ID. Returns True if removed."""
        ...

    def get_items(self, playlist_id: UUID) -> list[PlaylistItem]:
        """Get all items in a playlist ordered by position ascending."""
        ...

    def move_item(self, playlist_id: UUID, item_id: UUID, new_position: int) -> None:
        """Move an item to new_position, adjusting other positions collision-safely."""
        ...

    def count_items(self, playlist_id: UUID) -> int:
        """Return the total number of items in a playlist."""
        ...
