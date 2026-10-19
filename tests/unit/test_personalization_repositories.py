"""Unit tests for Desktop Personalization repositories.

Tests SqlitePlaylistRepository, SqliteFavoriteRepository, and SqliteRatingRepository
under isolated SQLite engines with PRAGMA foreign_keys = ON.
"""

from __future__ import annotations

import uuid
from datetime import UTC, datetime

import pytest
import sqlalchemy as sa
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from harmonia.core.database import _enable_sqlite_pragmas, init_database
from harmonia.core.exceptions import EntityNotFoundError, ValidationError
from harmonia.domain.entities.favorite import Favorite
from harmonia.domain.entities.playlist import Playlist, PlaylistItem
from harmonia.domain.entities.rating import Rating
from harmonia.domain.entities.track import Track
from harmonia.infrastructure.persistence.favorite_repo import SqliteFavoriteRepository
from harmonia.infrastructure.persistence.playlist_repo import SqlitePlaylistRepository
from harmonia.infrastructure.persistence.rating_repo import SqliteRatingRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def isolated_db():
    """Create an isolated in-memory SQLite database with foreign keys and tables initialized."""
    engine = sa.create_engine("sqlite:///:memory:", echo=False)
    sa.event.listen(engine, "connect", _enable_sqlite_pragmas)
    init_database(engine)
    with Session(engine) as session:
        yield session
    engine.dispose()


def _create_test_track(session: Session, title: str = "Test Track") -> Track:
    """Helper to persist a catalog track for foreign-key references."""
    track_repo = SqliteTrackRepository(session)
    track = Track(title=title)
    track_repo.add(track)
    session.flush()
    return track


# ── Playlist Repository Tests ─────────────────────────────────────────────────


def test_playlist_create_get_and_empty(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    pl = Playlist(name="Chill Beats", description="Lofi vibes")
    repo.add(pl)

    fetched = repo.get_by_id(pl.id)
    assert fetched is not None
    assert fetched.id == pl.id
    assert fetched.name == "Chill Beats"
    assert fetched.description == "Lofi vibes"
    assert fetched.track_count == 0
    assert fetched.items == []
    assert repo.count() == 1


def test_playlist_name_normalization_and_lookup(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    pl = Playlist(name="  Jazz Classics  ")
    repo.add(pl)
    assert pl.name == "Jazz Classics"

    # Case-insensitive lookup by name
    results = repo.get_by_name("jazz classics")
    assert len(results) == 1
    assert results[0].id == pl.id

    results_upper = repo.get_by_name("JAZZ CLASSICS")
    assert len(results_upper) == 1


def test_playlist_update_and_delete(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    pl = Playlist(name="Old Name", description="Old Desc")
    repo.add(pl)

    pl.name = "New Name"
    pl.description = "New Desc"
    repo.update(pl)

    updated = repo.get_by_id(pl.id)
    assert updated is not None
    assert updated.name == "New Name"
    assert updated.description == "New Desc"

    deleted = repo.delete(pl.id)
    assert deleted is True
    assert repo.get_by_id(pl.id) is None
    assert repo.delete(pl.id) is False


def test_playlist_update_nonexistent_raises(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    pl = Playlist(name="Ghost", id=uuid.uuid4())
    with pytest.raises(EntityNotFoundError):
        repo.update(pl)


def test_playlist_items_crud_and_duplicates(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    t1 = _create_test_track(isolated_db, "Track 1")
    t2 = _create_test_track(isolated_db, "Track 2")

    pl = Playlist(name="Road Trip")
    repo.add(pl)

    item1 = PlaylistItem(playlist_id=pl.id, track_id=t1.id, position=0)
    item2 = PlaylistItem(playlist_id=pl.id, track_id=t2.id, position=1)
    # Permitted: duplicate track entry in same playlist at distinct position
    item3 = PlaylistItem(playlist_id=pl.id, track_id=t1.id, position=2)

    repo.add_item(item1)
    repo.add_item(item2)
    repo.add_item(item3)

    assert repo.count_items(pl.id) == 3
    items = repo.get_items(pl.id)
    assert len(items) == 3
    assert [it.position for it in items] == [0, 1, 2]
    assert items[0].track_id == t1.id
    assert items[1].track_id == t2.id
    assert items[2].track_id == t1.id

    # Remove item2
    removed = repo.remove_item(pl.id, item2.id)
    assert removed is True
    assert repo.count_items(pl.id) == 2

    # Remove non-existent
    assert repo.remove_item(pl.id, uuid.uuid4()) is False


def test_playlist_items_gapped_positions(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    t1 = _create_test_track(isolated_db, "Track 1")
    t2 = _create_test_track(isolated_db, "Track 2")
    pl = Playlist(name="Sparse")
    repo.add(pl)

    repo.add_item(PlaylistItem(playlist_id=pl.id, track_id=t1.id, position=10))
    repo.add_item(PlaylistItem(playlist_id=pl.id, track_id=t2.id, position=50))

    items = repo.get_items(pl.id)
    assert [it.position for it in items] == [10, 50]
    assert items[0].position_key == f"{10:08d}"
    assert items[1].position_key == f"{50:08d}"


def test_playlist_move_item_collision_safe_forward_and_backward(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    t_ids = [_create_test_track(isolated_db, f"T{i}").id for i in range(5)]
    pl = Playlist(name="Ordering Test")
    repo.add(pl)

    items = [PlaylistItem(playlist_id=pl.id, track_id=t_ids[i], position=i) for i in range(5)]
    for it in items:
        repo.add_item(it)

    # Current order of track_ids: [T0, T1, T2, T3, T4]
    # 1. Move item 0 (T0) forward to position 3
    # Expected order: [T1, T2, T3, T0, T4] with positions [0, 1, 2, 3, 4]
    repo.move_item(pl.id, items[0].id, 3)

    reordered = repo.get_items(pl.id)
    assert [it.track_id for it in reordered] == [t_ids[1], t_ids[2], t_ids[3], t_ids[0], t_ids[4]]
    assert [it.position for it in reordered] == [0, 1, 2, 3, 4]
    assert [it.position_key for it in reordered] == [f"{i:08d}" for i in range(5)]

    # 2. Move item (now at pos 3, which is items[0]) backward to position 1
    # Expected order: [T1, T0, T2, T3, T4] with positions [0, 1, 2, 3, 4]
    repo.move_item(pl.id, items[0].id, 1)

    reordered2 = repo.get_items(pl.id)
    assert [it.track_id for it in reordered2] == [t_ids[1], t_ids[0], t_ids[2], t_ids[3], t_ids[4]]
    assert [it.position for it in reordered2] == [0, 1, 2, 3, 4]


def test_playlist_move_item_nonexistent_raises(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    pl = Playlist(name="Empty")
    repo.add(pl)
    with pytest.raises(EntityNotFoundError):
        repo.move_item(pl.id, uuid.uuid4(), 0)


def test_playlist_cascade_deletes_items(isolated_db: Session):
    repo = SqlitePlaylistRepository(isolated_db)
    t = _create_test_track(isolated_db, "Track")
    pl = Playlist(name="To Delete")
    repo.add(pl)
    repo.add_item(PlaylistItem(playlist_id=pl.id, track_id=t.id, position=0))
    assert repo.count_items(pl.id) == 1

    repo.delete(pl.id)
    assert repo.count_items(pl.id) == 0


def test_track_deletion_restricted_by_playlist_item(isolated_db: Session):
    pl_repo = SqlitePlaylistRepository(isolated_db)
    t = _create_test_track(isolated_db, "Track")
    pl = Playlist(name="Protected")
    pl_repo.add(pl)
    pl_repo.add_item(PlaylistItem(playlist_id=pl.id, track_id=t.id, position=0))

    # Attempting to delete track must fail with IntegrityError (ON DELETE RESTRICT)
    track_repo = SqliteTrackRepository(isolated_db)
    with pytest.raises(IntegrityError):
        track_repo.delete(t.id)


# ── Favorite Repository Tests ─────────────────────────────────────────────────


def test_favorite_add_and_is_favorite(isolated_db: Session):
    repo = SqliteFavoriteRepository(isolated_db)
    t = _create_test_track(isolated_db, "Song")

    assert repo.is_favorite(t.id) is False
    assert repo.get_by_track_id(t.id) is None

    fav = Favorite(track_id=t.id)
    repo.add(fav)

    assert repo.is_favorite(t.id) is True
    fetched = repo.get_by_track_id(t.id)
    assert fetched is not None
    assert fetched.track_id == t.id
    assert fetched.client_updated_at == fav.client_updated_at
    assert repo.count() == 1


def test_favorite_idempotent_repeated_add(isolated_db: Session):
    repo = SqliteFavoriteRepository(isolated_db)
    t = _create_test_track(isolated_db, "Song")

    fav1 = Favorite(track_id=t.id, client_updated_at=1000)
    repo.add(fav1)

    # Repeated add updates client_updated_at without duplicate row error
    fav2 = Favorite(track_id=t.id, client_updated_at=2000)
    repo.add(fav2)

    assert repo.count() == 1
    fetched = repo.get_by_track_id(t.id)
    assert fetched is not None
    assert fetched.client_updated_at == 2000


def test_favorite_remove_and_absent_removal(isolated_db: Session):
    repo = SqliteFavoriteRepository(isolated_db)
    t = _create_test_track(isolated_db, "Song")
    repo.add(Favorite(track_id=t.id))

    assert repo.remove(t.id) is True
    assert repo.is_favorite(t.id) is False
    assert repo.count() == 0

    # Absent removal returns False idempotently
    assert repo.remove(t.id) is False


def test_favorite_missing_track_foreign_key_violation(isolated_db: Session):
    repo = SqliteFavoriteRepository(isolated_db)
    ghost_id = uuid.uuid4()
    with pytest.raises(IntegrityError):
        repo.add(Favorite(track_id=ghost_id))


def test_favorite_track_deletion_restricted(isolated_db: Session):
    fav_repo = SqliteFavoriteRepository(isolated_db)
    t = _create_test_track(isolated_db, "Song")
    fav_repo.add(Favorite(track_id=t.id))

    track_repo = SqliteTrackRepository(isolated_db)
    with pytest.raises(IntegrityError):
        track_repo.delete(t.id)


def test_favorite_list_pagination_and_ordering(isolated_db: Session):
    repo = SqliteFavoriteRepository(isolated_db)
    t1 = _create_test_track(isolated_db, "T1")
    t2 = _create_test_track(isolated_db, "T2")
    t3 = _create_test_track(isolated_db, "T3")

    repo.add(Favorite(track_id=t1.id, client_updated_at=100))
    repo.add(Favorite(track_id=t2.id, client_updated_at=300))
    repo.add(Favorite(track_id=t3.id, client_updated_at=200))

    favs = repo.list_all(limit=2, offset=0)
    assert len(favs) == 2
    # Ordered by client_updated_at desc: T2 (300), T3 (200)
    assert favs[0].track_id == t2.id
    assert favs[1].track_id == t3.id

    favs_page2 = repo.list_all(limit=2, offset=2)
    assert len(favs_page2) == 1
    assert favs_page2[0].track_id == t1.id


# ── Rating Repository Tests ───────────────────────────────────────────────────


def test_rating_set_get_and_update(isolated_db: Session):
    repo = SqliteRatingRepository(isolated_db)
    t = _create_test_track(isolated_db, "Song")

    assert repo.get_by_track_id(t.id) is None
    r1 = Rating(track_id=t.id, rating=4, client_updated_at=1000)
    repo.set_rating(r1)

    fetched = repo.get_by_track_id(t.id)
    assert fetched is not None
    assert fetched.rating == 4
    assert fetched.client_updated_at == 1000
    assert repo.count() == 1

    # Idempotent update
    r2 = Rating(track_id=t.id, rating=5, client_updated_at=2000)
    repo.set_rating(r2)

    fetched2 = repo.get_by_track_id(t.id)
    assert fetched2 is not None
    assert fetched2.rating == 5
    assert fetched2.client_updated_at == 2000
    assert repo.count() == 1


def test_rating_boundaries_and_invalid_values(isolated_db: Session):
    repo = SqliteRatingRepository(isolated_db)
    t = _create_test_track(isolated_db, "Song")

    # Valid boundaries: 1 and 5
    repo.set_rating(Rating(track_id=t.id, rating=1))
    assert repo.get_by_track_id(t.id).rating == 1
    repo.set_rating(Rating(track_id=t.id, rating=5))
    assert repo.get_by_track_id(t.id).rating == 5

    # Invalid values raise ValidationError
    with pytest.raises(ValidationError):
        Rating(track_id=t.id, rating=0)
    with pytest.raises(ValidationError):
        Rating(track_id=t.id, rating=6)


def test_rating_remove_and_absent_removal(isolated_db: Session):
    repo = SqliteRatingRepository(isolated_db)
    t = _create_test_track(isolated_db, "Song")
    repo.set_rating(Rating(track_id=t.id, rating=3))

    assert repo.remove(t.id) is True
    assert repo.get_by_track_id(t.id) is None
    assert repo.remove(t.id) is False


def test_rating_list_all_and_filtering(isolated_db: Session):
    repo = SqliteRatingRepository(isolated_db)
    t1 = _create_test_track(isolated_db, "T1")
    t2 = _create_test_track(isolated_db, "T2")
    t3 = _create_test_track(isolated_db, "T3")

    repo.set_rating(Rating(track_id=t1.id, rating=2, client_updated_at=100))
    repo.set_rating(Rating(track_id=t2.id, rating=5, client_updated_at=300))
    repo.set_rating(Rating(track_id=t3.id, rating=4, client_updated_at=200))

    assert repo.count() == 3
    assert repo.count(min_rating=4) == 2

    # Filter min_rating=4 ordered by client_updated_at desc: T2 (5 stars, 300), T3 (4 stars, 200)
    filtered = repo.list_all(min_rating=4)
    assert len(filtered) == 2
    assert filtered[0].track_id == t2.id
    assert filtered[1].track_id == t3.id


def test_rating_track_deletion_restricted(isolated_db: Session):
    rate_repo = SqliteRatingRepository(isolated_db)
    t = _create_test_track(isolated_db, "Song")
    rate_repo.set_rating(Rating(track_id=t.id, rating=4))

    track_repo = SqliteTrackRepository(isolated_db)
    with pytest.raises(IntegrityError):
        track_repo.delete(t.id)


# ── Transaction Rollback Tests ────────────────────────────────────────────────


def test_transaction_rollback_reverts_repository_mutations(isolated_db: Session):
    """Verify that uncommitted repository operations are cleanly rolled back by the session."""
    pl_repo = SqlitePlaylistRepository(isolated_db)
    fav_repo = SqliteFavoriteRepository(isolated_db)
    rate_repo = SqliteRatingRepository(isolated_db)

    t = _create_test_track(isolated_db, "Base Track")
    isolated_db.commit()

    # Begin operations inside a nested savepoint or transaction block
    with isolated_db.begin_nested():
        pl = Playlist(name="To be rolled back")
        pl_repo.add(pl)
        fav_repo.add(Favorite(track_id=t.id))
        rate_repo.set_rating(Rating(track_id=t.id, rating=5))

        # Check they exist in uncommitted session
        assert pl_repo.get_by_id(pl.id) is not None
        assert fav_repo.is_favorite(t.id) is True
        assert rate_repo.get_by_track_id(t.id) is not None

        # Roll back savepoint
        isolated_db.rollback()

    # After rollback, none of the mutations should persist
    assert pl_repo.get_by_name("To be rolled back") == []
    assert fav_repo.is_favorite(t.id) is False
    assert rate_repo.get_by_track_id(t.id) is None
