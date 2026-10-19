"""In-process event bus for decoupled communication.

A simple publish/subscribe system so modules can react to events
without direct coupling.  This is NOT event sourcing — events are
fire-and-forget notifications.

Usage::

    from harmonia.core.events import event_bus, Event

    # Define an event
    class TrackImported(Event):
        track_id: UUID
        file_path: str

    # Subscribe
    def on_track_imported(event: TrackImported) -> None:
        print(f"Imported: {event.track_id}")

    event_bus.subscribe(TrackImported, on_track_imported)

    # Publish
    event_bus.publish(TrackImported(track_id=..., file_path=...))
"""

from __future__ import annotations

import logging
from collections import defaultdict
from collections.abc import Callable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from typing import TYPE_CHECKING, Any

if TYPE_CHECKING:
    from uuid import UUID

logger = logging.getLogger("harmonia.events")


# ── Base event ────────────────────────────────────────────────────────────────


@dataclass(frozen=True, kw_only=True)
class Event:
    """Base class for all domain events."""

    timestamp: datetime = field(default_factory=lambda: datetime.now(UTC))


# ── Concrete events (Phase 1) ────────────────────────────────────────────────


@dataclass(frozen=True, kw_only=True)
class TrackImported(Event):
    """Fired when a track is successfully imported into the library."""

    track_id: UUID
    file_path: str


@dataclass(frozen=True, kw_only=True)
class TrackUpdated(Event):
    """Fired when a track's metadata is updated."""

    track_id: UUID


@dataclass(frozen=True, kw_only=True)
class ImportCompleted(Event):
    """Fired when a batch import finishes."""

    total_imported: int
    total_skipped: int
    total_failed: int


# ── Personalization events (Phase 2-B) ─────────────────────────────────────────


@dataclass(frozen=True, kw_only=True)
class PlaylistCreated(Event):
    """Fired when a new playlist is created."""

    playlist_id: UUID
    name: str


@dataclass(frozen=True, kw_only=True)
class PlaylistUpdated(Event):
    """Fired when a playlist's name or description is updated."""

    playlist_id: UUID
    name: str


@dataclass(frozen=True, kw_only=True)
class PlaylistDeleted(Event):
    """Fired when a playlist is deleted."""

    playlist_id: UUID


@dataclass(frozen=True, kw_only=True)
class PlaylistItemAdded(Event):
    """Fired when a track is added to a playlist."""

    playlist_id: UUID
    item_id: UUID
    track_id: UUID
    position: int


@dataclass(frozen=True, kw_only=True)
class PlaylistItemRemoved(Event):
    """Fired when a track item is removed from a playlist."""

    playlist_id: UUID
    item_id: UUID


@dataclass(frozen=True, kw_only=True)
class PlaylistItemMoved(Event):
    """Fired when a playlist item position is changed."""

    playlist_id: UUID
    item_id: UUID
    new_position: int


@dataclass(frozen=True, kw_only=True)
class TrackFavorited(Event):
    """Fired when a track is added to favorites."""

    track_id: UUID


@dataclass(frozen=True, kw_only=True)
class TrackUnfavorited(Event):
    """Fired when a track is removed from favorites."""

    track_id: UUID


@dataclass(frozen=True, kw_only=True)
class TrackRated(Event):
    """Fired when a track is rated."""

    track_id: UUID
    rating: int


@dataclass(frozen=True, kw_only=True)
class TrackRatingRemoved(Event):
    """Fired when a track rating is removed."""

    track_id: UUID


# ── Event bus ─────────────────────────────────────────────────────────────────

EventHandler = Callable[[Any], None]


class EventBus:
    """Simple synchronous in-process event bus.

    Handlers are called immediately and synchronously when an event is
    published.  Exceptions in handlers are logged but do not propagate
    to the publisher — events are best-effort notifications.
    """

    def __init__(self) -> None:
        self._handlers: dict[type[Event], list[EventHandler]] = defaultdict(list)

    def subscribe(self, event_type: type[Event], handler: EventHandler) -> None:
        """Register a handler for an event type."""
        self._handlers[event_type].append(handler)

    def unsubscribe(self, event_type: type[Event], handler: EventHandler) -> None:
        """Remove a handler for an event type."""
        handlers = self._handlers.get(event_type, [])
        if handler in handlers:
            handlers.remove(handler)

    def publish(self, event: Event) -> None:
        """Publish an event to all registered handlers."""
        event_type = type(event)
        handlers = self._handlers.get(event_type, [])

        for handler in handlers:
            try:
                handler(event)
            except Exception:
                logger.exception(
                    "Event handler %s failed for %s",
                    handler.__name__,
                    event_type.__name__,
                )

    def clear(self) -> None:
        """Remove all handlers. Useful for testing."""
        self._handlers.clear()


# Module-level singleton — used as the default event bus.
event_bus = EventBus()
