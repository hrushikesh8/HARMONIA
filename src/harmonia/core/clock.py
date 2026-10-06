"""Clock abstraction for deterministic testing.

Production code uses the real clock. Tests inject a fake clock
to get deterministic timestamps without monkey-patching.
"""

from __future__ import annotations

import datetime
from typing import Protocol


class Clock(Protocol):
    """Port for time operations."""

    def now(self) -> datetime.datetime:
        """Return the current UTC time."""
        ...


class SystemClock:
    """Real system clock — used in production."""

    def now(self) -> datetime.datetime:
        return datetime.datetime.now(datetime.UTC)


class FakeClock:
    """Controllable clock for tests."""

    def __init__(self, fixed: datetime.datetime | None = None) -> None:
        self._now = fixed or datetime.datetime(2025, 1, 1, tzinfo=datetime.UTC)

    def now(self) -> datetime.datetime:
        return self._now

    def advance(self, **kwargs: float) -> None:
        """Advance time by a timedelta. Accepts timedelta keyword args."""
        self._now += datetime.timedelta(**kwargs)

    def set(self, dt: datetime.datetime) -> None:
        """Set the clock to a specific time."""
        self._now = dt
