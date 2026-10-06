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
from dataclasses import dataclass, field
from datetime import datetime, UTC
from typing import Any, Callable
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
