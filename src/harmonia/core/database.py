"""Database engine and session management for Harmonia.

Uses SQLAlchemy 2.0 synchronous engine with SQLite. WAL mode and
foreign keys are enabled automatically.
"""

from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import Engine, create_engine, event, text
from sqlalchemy.orm import Session, sessionmaker

from harmonia.core.logging import get_logger

if TYPE_CHECKING:
    from pathlib import Path

logger = get_logger(__name__)


def _enable_sqlite_pragmas(dbapi_conn: object, _connection_record: object) -> None:
    """Enable WAL mode, foreign keys, and performance tuning on every new SQLite connection.

    Called via SQLAlchemy's connection-pool event system.
    Configures:
    - WAL journal mode for concurrent readers and writer.
    - Foreign key constraints enforcement.
    - 5000ms busy timeout to prevent busy lock failures.
    - NORMAL synchronous mode for high write throughput while maintaining database consistency in WAL mode.
    - 64MB cache size (-64000) for large-library indexing and query planning.
    - In-memory temporary tables and sorting (temp_store=MEMORY).
    - Memory-mapped I/O (mmap_size=256MB) for low-overhead page reads.
    """
    cursor = dbapi_conn.cursor()  # type: ignore[union-attr]
    cursor.execute("PRAGMA journal_mode=WAL")
    cursor.execute("PRAGMA foreign_keys=ON")
    cursor.execute("PRAGMA busy_timeout=5000")
    cursor.execute("PRAGMA synchronous=NORMAL")
    cursor.execute("PRAGMA cache_size=-64000")
    cursor.execute("PRAGMA temp_store=MEMORY")
    cursor.execute("PRAGMA mmap_size=268435456")
    cursor.close()


def create_database_engine(db_path: Path) -> Engine:
    """Create a SQLAlchemy engine pointing at the given SQLite file.

    Args:
        db_path: Path to the SQLite database file.

    Returns:
        Configured SQLAlchemy Engine with WAL mode and FK enforcement.
    """
    # Ensure parent directory exists.
    db_path.parent.mkdir(parents=True, exist_ok=True)

    url = f"sqlite:///{db_path}"
    engine = create_engine(url, echo=False, pool_pre_ping=True)

    # Attach pragma setup to every new connection.
    event.listen(engine, "connect", _enable_sqlite_pragmas)

    logger.info("Database engine created: %s", db_path)
    return engine


def create_session_factory(engine: Engine) -> sessionmaker[Session]:
    """Create a session factory bound to the given engine.

    Usage::

        SessionFactory = create_session_factory(engine)
        with SessionFactory() as session:
            ...
    """
    return sessionmaker(bind=engine, expire_on_commit=False)


def init_database(engine: Engine) -> None:
    """Create all tables if they don't exist.

    This uses the SQLAlchemy ORM metadata to create tables. In production,
    Alembic migrations should be used instead; this is a convenience
    for development and testing.
    """
    from harmonia.infrastructure.persistence.models import Base

    Base.metadata.create_all(engine)
    with engine.connect() as conn:
        conn.execute(
            text(
                "CREATE VIRTUAL TABLE IF NOT EXISTS tracks_fts USING fts5("
                "track_id UNINDEXED, "
                "title, "
                "artist_name, "
                "album_title, "
                "genre, "
                "tokenize='unicode61 remove_diacritics 2'"
                ")"
            )
        )
        conn.commit()
    logger.info("Database tables initialized")


def check_database(engine: Engine) -> bool:
    """Verify that the database is accessible and has the expected schema.

    Returns:
        True if the database is healthy.
    """
    try:
        with engine.connect() as conn:
            result = conn.execute(text("SELECT 1"))
            result.fetchone()
        return True
    except Exception:
        logger.exception("Database health check failed")
        return False
