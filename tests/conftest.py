"""Shared pytest fixtures for Harmonia test suite."""

from __future__ import annotations

import tempfile
from collections.abc import Generator
from pathlib import Path

import pytest
from sqlalchemy import Engine, create_engine, event
from sqlalchemy.orm import Session, sessionmaker

from harmonia.core.database import _enable_sqlite_pragmas, init_database


@pytest.fixture
def in_memory_engine() -> Generator[Engine, None, None]:
    """Provide an in-memory SQLite database engine with foreign keys enabled."""
    engine = create_engine("sqlite:///:memory:", echo=False)
    event.listen(engine, "connect", _enable_sqlite_pragmas)
    init_database(engine)
    yield engine
    engine.dispose()


@pytest.fixture
def session(in_memory_engine: Engine) -> Generator[Session, None, None]:
    """Provide a database session bound to the in-memory engine."""
    session_factory = sessionmaker(bind=in_memory_engine, expire_on_commit=False)
    with session_factory() as sess:
        yield sess
        sess.rollback()


@pytest.fixture
def temp_dir() -> Generator[Path, None, None]:
    """Provide a temporary directory."""
    with tempfile.TemporaryDirectory() as td:
        yield Path(td)
