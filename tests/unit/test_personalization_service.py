"""Unit tests for PersonalizationService and Personalization DTOs.

Tests playlist, playlist item, favorite, and rating application use cases
under isolated SQLite engines with PRAGMA foreign_keys = ON.
"""

from __future__ import annotations

from datetime import UTC, datetime
from uuid import UUID, uuid4

import pytest
from pydantic import ValidationError as PydanticValidationError
from sqlalchemy import create_engine, event
from sqlalchemy.orm import Session

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
from harmonia.application.personalization_service import PersonalizationService
from harmonia.core.clock import FakeClock
from harmonia.core.database import _enable_sqlite_pragmas, init_database
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
from harmonia.core.exceptions import EntityNotFoundError, ValidationError
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.favorite_repo import SqliteFavoriteRepository
from harmonia.infrastructure.persistence.playlist_repo import SqlitePlaylistRepository
from harmonia.infrastructure.persistence.rating_repo import SqliteRatingRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def isolated_db():
    """Provide an isolated in-memory SQLite database session with foreign keys enabled."""
    engine = create_engine("sqlite:///:memory:", echo=False)
    event.listen(engine, "connect", _enable_sqlite_pragmas)
    init_database(engine)
    with Session(engine, expire_on_commit=False) as session:
        yield session
    engine.dispose()


@pytest.fixture
def test_bus() -> tuple[EventBus, list[Event]]:
    """Provide an isolated EventBus and an event collector."""
    bus = EventBus()
    received: list[Event] = []

    for event_cls in (
        PlaylistCreated,
        PlaylistUpdated,
        PlaylistDeleted,
        PlaylistItemAdded,
        PlaylistItemRemoved,
        PlaylistItemMoved,
        TrackFavorited,
        TrackUnfavorited,
        TrackRated,
        TrackRatingRemoved,
    ):
        bus.subscribe(event_cls, lambda ev: received.append(ev))

    return bus, received


def _seed_track(
    session: Session,
    title: str = "Test Track",
    artist_name: str | None = "Test Artist",
    duration_seconds: float = 210.0,
) -> Track:
    """Helper to persist a catalog track with optional artist for foreign-key references."""
    artist_id = None
    if artist_name:
        artist_repo = SqliteArtistRepository(session)
        artist = Artist(name=artist_name)
        artist_repo.add(artist)
        artist_id = artist.id

    track_repo = SqliteTrackRepository(session)
    track = Track(
        title=title,
        artist_id=artist_id,
        duration_seconds=duration_seconds,
    )
    track_repo.add(track)
    session.flush()
    return track


# ── Playlist CRUD Tests ───────────────────────────────────────────────────────


def test_playlist_create_get_update_delete(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)

    # 1. Create
    dto = PlaylistCreate(name="Electronic Focus", description="Ambient beats")
    detail = service.create_playlist(dto)

    assert isinstance(detail, PlaylistDetail)
    assert detail.name == "Electronic Focus"
    assert detail.description == "Ambient beats"
    assert detail.track_count == 0
    assert detail.total_duration_seconds == 0.0
    assert detail.items == []

    # 2. Get
    fetched = service.get_playlist(detail.id)
    assert fetched is not None
    assert fetched.id == detail.id
    assert fetched.name == "Electronic Focus"

    # 3. Update
    updated = service.update_playlist(
        detail.id,
        PlaylistUpdate(name="Electronic Deep Focus", description="Updated description"),
    )
    assert updated.name == "Electronic Deep Focus"
    assert updated.description == "Updated description"

    # 4. List
    playlists = service.list_playlists()
    assert len(playlists) == 1
    assert playlists[0].id == detail.id
    assert playlists[0].name == "Electronic Deep Focus"

    # 5. Delete
    deleted = service.delete_playlist(detail.id)
    assert deleted is True
    assert service.get_playlist(detail.id) is None

    # Delete non-existent
    assert service.delete_playlist(detail.id) is False


def test_empty_playlists_and_duplicate_playlist_names(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)

    # Schema allows duplicate playlist names
    pl1 = service.create_playlist(PlaylistCreate(name="Workout Mix"))
    pl2 = service.create_playlist(PlaylistCreate(name="Workout Mix"))

    assert pl1.id != pl2.id
    assert pl1.name == "Workout Mix"
    assert pl2.name == "Workout Mix"

    summaries = service.list_playlists()
    assert len(summaries) == 2
    assert all(s.name == "Workout Mix" for s in summaries)


def test_playlist_validation(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)

    # Empty name
    with pytest.raises(ValidationError):
        service.create_playlist(PlaylistCreate.model_construct(name="   "))

    # Name too long (>200 chars)
    with pytest.raises(ValidationError):
        service.create_playlist(PlaylistCreate.model_construct(name="A" * 201))

    # Update non-existent
    with pytest.raises(EntityNotFoundError):
        service.update_playlist(uuid4(), PlaylistUpdate(name="New Name"))

    # Update with empty name
    pl = service.create_playlist(PlaylistCreate(name="Valid"))
    with pytest.raises(ValidationError):
        service.update_playlist(pl.id, PlaylistUpdate.model_construct(name="   "))


# ── Playlist Items Tests ──────────────────────────────────────────────────────


def test_playlist_items_crud_and_duplicates(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)
    pl = service.create_playlist(PlaylistCreate(name="Road Trip"))
    track = _seed_track(isolated_db, title="Highway Star", duration_seconds=365.0)

    # Add track item
    item1 = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=track.id))
    assert isinstance(item1, PlaylistItemSummary)
    assert item1.playlist_id == pl.id
    assert item1.track_id == track.id
    assert item1.position == 0
    assert item1.track_title == "Highway Star"
    assert item1.track_artist == "Test Artist"
    assert item1.duration_seconds == 365.0

    # Add duplicate track to same playlist at next position
    item2 = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=track.id))
    assert item2.id != item1.id
    assert item2.track_id == track.id
    assert item2.position == 1

    # Check playlist detail aggregates
    detail = service.get_playlist(pl.id)
    assert detail is not None
    assert detail.track_count == 2
    assert detail.total_duration_seconds == 730.0
    assert len(detail.items) == 2

    # Remove item1
    removed = service.remove_playlist_item(pl.id, item1.id)
    assert removed is True

    items_after = service.get_playlist_items(pl.id)
    assert len(items_after) == 1
    assert items_after[0].id == item2.id

    # Remove non-existent item
    assert service.remove_playlist_item(pl.id, item1.id) is False


def test_playlist_items_gapped_positions(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)
    pl = service.create_playlist(PlaylistCreate(name="Gapped Playlist"))
    t1 = _seed_track(isolated_db, title="Track 1")
    t2 = _seed_track(isolated_db, title="Track 2")

    # Insert with explicit gapped positions 10 and 20
    item1 = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t1.id, position=10))
    item2 = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t2.id, position=20))

    assert item1.position == 10
    assert item2.position == 20

    items = service.get_playlist_items(pl.id)
    assert [i.position for i in items] == [10, 20]

    # Append without position attaches after max (position 21)
    t3 = _seed_track(isolated_db, title="Track 3")
    item3 = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t3.id))
    assert item3.position == 21


def test_playlist_item_collision_safe_shift_on_insert(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)
    pl = service.create_playlist(PlaylistCreate(name="Shifting Playlist"))
    t0 = _seed_track(isolated_db, title="Track 0")
    t1 = _seed_track(isolated_db, title="Track 1")
    t2 = _seed_track(isolated_db, title="Track 2")
    t_new = _seed_track(isolated_db, title="Inserted Track")

    service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t0.id, position=0))
    service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t1.id, position=1))
    service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t2.id, position=2))

    # Insert at position 1: items at 1 and 2 must shift to 2 and 3 collision-safely
    new_item = service.add_playlist_item(
        pl.id, PlaylistItemCreate(track_id=t_new.id, position=1)
    )
    assert new_item.position == 1

    items = service.get_playlist_items(pl.id)
    positions_tracks = [(i.position, i.track_id) for i in items]
    assert positions_tracks == [
        (0, t0.id),
        (1, t_new.id),
        (2, t1.id),
        (3, t2.id),
    ]


def test_playlist_item_move(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)
    pl = service.create_playlist(PlaylistCreate(name="Move Playlist"))
    t0 = _seed_track(isolated_db, title="T0")
    t1 = _seed_track(isolated_db, title="T1")
    t2 = _seed_track(isolated_db, title="T2")

    i0 = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t0.id))
    i1 = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t1.id))
    i2 = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t2.id))

    # Move item 0 to position 2
    service.move_playlist_item(pl.id, i0.id, 2)
    items = service.get_playlist_items(pl.id)
    assert [i.id for i in items] == [i1.id, i2.id, i0.id]
    assert [i.position for i in items] == [0, 1, 2]

    # Move with negative position raises ValidationError
    with pytest.raises(ValidationError):
        service.move_playlist_item(pl.id, i0.id, -1)


def test_playlist_items_missing_entities(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)
    pl = service.create_playlist(PlaylistCreate(name="Valid Playlist"))
    track = _seed_track(isolated_db)

    # Missing playlist
    with pytest.raises(EntityNotFoundError) as exc_info:
        service.add_playlist_item(uuid4(), PlaylistItemCreate(track_id=track.id))
    assert exc_info.value.entity_type == "Playlist"

    # Missing track
    with pytest.raises(EntityNotFoundError) as exc_info:
        service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=uuid4()))
    assert exc_info.value.entity_type == "Track"

    # Missing playlist for get_items, remove_item, move_item
    missing_pl_id = uuid4()
    with pytest.raises(EntityNotFoundError):
        service.get_playlist_items(missing_pl_id)
    with pytest.raises(EntityNotFoundError):
        service.remove_playlist_item(missing_pl_id, uuid4())
    with pytest.raises(EntityNotFoundError):
        service.move_playlist_item(missing_pl_id, uuid4(), 0)


# ── Favorite Tests ────────────────────────────────────────────────────────────


def test_favorite_idempotency_and_timestamps(isolated_db: Session):
    fixed_time = datetime(2026, 6, 15, 10, 30, 0, tzinfo=UTC)
    clock = FakeClock(fixed_time)
    service = PersonalizationService(session=isolated_db, clock=clock)

    track = _seed_track(isolated_db, title="Favorite Song")

    # Add favorite
    fav1 = service.add_favorite(track.id)
    expected_ms = int(fixed_time.timestamp() * 1000)
    assert fav1.track_id == track.id
    assert fav1.client_updated_at == expected_ms
    assert fav1.track_title == "Favorite Song"
    assert fav1.track_artist == "Test Artist"
    assert service.is_favorite(track.id) is True

    # Advance fake clock and add again (idempotent update)
    clock.advance(minutes=15)
    updated_ms = int(clock.now().timestamp() * 1000)
    fav2 = service.add_favorite(track.id)
    assert fav2.client_updated_at == updated_ms

    # List favorites
    fav_list = service.list_favorites()
    assert len(fav_list) == 1
    assert fav_list[0].track_id == track.id

    # Remove favorite
    assert service.remove_favorite(track.id) is True
    assert service.is_favorite(track.id) is False

    # Second removal returns False
    assert service.remove_favorite(track.id) is False


def test_favorite_missing_track(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)
    with pytest.raises(EntityNotFoundError) as exc_info:
        service.add_favorite(uuid4())
    assert exc_info.value.entity_type == "Track"


# ── Rating Tests ──────────────────────────────────────────────────────────────


def test_rating_validation_update_filter_removal(isolated_db: Session):
    fixed_time = datetime(2026, 7, 20, 14, 0, 0, tzinfo=UTC)
    clock = FakeClock(fixed_time)
    service = PersonalizationService(session=isolated_db, clock=clock)

    t1 = _seed_track(isolated_db, title="Track 5-star")
    t2 = _seed_track(isolated_db, title="Track 3-star")

    # 1. Validation of rating range (1-5)
    with pytest.raises(ValidationError):
        service.set_rating(t1.id, 0)
    with pytest.raises(ValidationError):
        service.set_rating(t1.id, 6)

    # 2. Set rating
    r1 = service.set_rating(t1.id, 5)
    assert isinstance(r1, RatingSummary)
    assert r1.rating == 5
    assert r1.track_title == "Track 5-star"
    assert r1.client_updated_at == int(fixed_time.timestamp() * 1000)

    # 3. Update rating
    clock.advance(hours=1)
    r1_updated = service.set_rating(t1.id, 4)
    assert r1_updated.rating == 4
    assert r1_updated.client_updated_at == int(clock.now().timestamp() * 1000)

    # 4. Set rating on second track
    service.set_rating(t2.id, 3)

    # 5. Get rating
    got1 = service.get_rating(t1.id)
    assert got1 is not None
    assert got1.rating == 4

    # 6. Filter ratings
    all_ratings = service.list_ratings()
    assert len(all_ratings) == 2

    filtered_4 = service.list_ratings(min_rating=4)
    assert len(filtered_4) == 1
    assert filtered_4[0].track_id == t1.id

    filtered_5 = service.list_ratings(min_rating=5)
    assert len(filtered_5) == 0

    # 7. Remove rating
    assert service.remove_rating(t1.id) is True
    assert service.get_rating(t1.id) is None
    assert service.remove_rating(t1.id) is False


def test_rating_missing_track(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)
    with pytest.raises(EntityNotFoundError) as exc_info:
        service.set_rating(uuid4(), 5)
    assert exc_info.value.entity_type == "Track"


# ── Pagination and Input Validation Tests ─────────────────────────────────────


def test_invalid_pagination_and_rating_filters(isolated_db: Session):
    service = PersonalizationService(session=isolated_db)

    # Playlist pagination
    with pytest.raises(ValidationError):
        service.list_playlists(limit=0)
    with pytest.raises(ValidationError):
        service.list_playlists(offset=-1)

    # Favorite pagination
    with pytest.raises(ValidationError):
        service.list_favorites(limit=-1)
    with pytest.raises(ValidationError):
        service.list_favorites(offset=-1)

    # Rating pagination and filters
    with pytest.raises(ValidationError):
        service.list_ratings(limit=0)
    with pytest.raises(ValidationError):
        service.list_ratings(offset=-1)
    with pytest.raises(ValidationError):
        service.list_ratings(min_rating=0)
    with pytest.raises(ValidationError):
        service.list_ratings(min_rating=6)


# ── Transaction Rollback Tests ────────────────────────────────────────────────


def test_transaction_rollback_multi_step_operations(isolated_db: Session):
    """Verify that multi-step operations are atomic and can be rolled back."""
    service = PersonalizationService(session=isolated_db, autocommit=False)
    track = _seed_track(isolated_db, title="Atomic Track")
    isolated_db.commit()

    # Perform multi-step operations without committing
    pl = service.create_playlist(PlaylistCreate(name="To Rollback"))
    service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=track.id))
    service.add_favorite(track.id)
    service.set_rating(track.id, 5)

    # Verify visible within the uncommitted transaction
    assert service.get_playlist(pl.id) is not None
    assert service.is_favorite(track.id) is True
    assert service.get_rating(track.id) is not None

    # Roll back session
    isolated_db.rollback()

    # After rollback, none of the personalization mutations persist
    assert service.get_playlist(pl.id) is None
    assert service.is_favorite(track.id) is False
    assert service.get_rating(track.id) is None


# ── Domain Event Behavior Tests ───────────────────────────────────────────────


def test_domain_events_published_on_success_and_suppressed_on_failure(
    isolated_db: Session,
    test_bus: tuple[EventBus, list[Event]],
):
    bus, received_events = test_bus
    service = PersonalizationService(session=isolated_db, event_bus=bus, autocommit=True)
    track = _seed_track(isolated_db, title="Event Track")

    # 1. Playlist creation -> PlaylistCreated
    pl = service.create_playlist(PlaylistCreate(name="Events Test"))
    assert any(isinstance(e, PlaylistCreated) and e.playlist_id == pl.id for e in received_events)

    # 2. Add item -> PlaylistItemAdded
    item = service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=track.id))
    assert any(isinstance(e, PlaylistItemAdded) and e.item_id == item.id for e in received_events)

    # 3. Move item -> PlaylistItemMoved
    service.move_playlist_item(pl.id, item.id, 5)
    assert any(isinstance(e, PlaylistItemMoved) and e.new_position == 5 for e in received_events)

    # 4. Remove item -> PlaylistItemRemoved
    service.remove_playlist_item(pl.id, item.id)
    assert any(isinstance(e, PlaylistItemRemoved) and e.item_id == item.id for e in received_events)

    # 5. Favorite add/remove -> TrackFavorited / TrackUnfavorited
    service.add_favorite(track.id)
    assert any(isinstance(e, TrackFavorited) and e.track_id == track.id for e in received_events)
    service.remove_favorite(track.id)
    assert any(isinstance(e, TrackUnfavorited) and e.track_id == track.id for e in received_events)

    # 6. Rating set/remove -> TrackRated / TrackRatingRemoved
    service.set_rating(track.id, 4)
    assert any(isinstance(e, TrackRated) and e.rating == 4 for e in received_events)
    service.remove_rating(track.id)
    assert any(isinstance(e, TrackRatingRemoved) and e.track_id == track.id for e in received_events)

    # 7. Playlist update/delete -> PlaylistUpdated / PlaylistDeleted
    service.update_playlist(pl.id, PlaylistUpdate(name="Renamed"))
    assert any(isinstance(e, PlaylistUpdated) and e.name == "Renamed" for e in received_events)
    service.delete_playlist(pl.id)
    assert any(isinstance(e, PlaylistDeleted) and e.playlist_id == pl.id for e in received_events)

    # Count events before failure tests
    event_count_before = len(received_events)

    # 8. Failure suppression: failed operation must NOT emit event
    with pytest.raises(EntityNotFoundError):
        service.add_playlist_item(uuid4(), PlaylistItemCreate(track_id=track.id))
    assert len(received_events) == event_count_before

    with pytest.raises(ValidationError):
        service.set_rating(track.id, 99)
    assert len(received_events) == event_count_before


# ── DTO Validation and Conversion Tests ───────────────────────────────────────


def test_dto_validation_and_conversion():
    # Valid playlist create
    pc = PlaylistCreate(name="  Ambient Focus  ", description="  Smooth soundscapes  ")
    assert pc.name == "Ambient Focus"
    assert pc.description == "Smooth soundscapes"

    # Empty playlist create name raises Pydantic error
    with pytest.raises(PydanticValidationError):
        PlaylistCreate(name="")

    # Negative position in item create raises Pydantic error
    with pytest.raises(PydanticValidationError):
        PlaylistItemCreate(track_id=uuid4(), position=-1)

    # Invalid rating range raises Pydantic error
    with pytest.raises(PydanticValidationError):
        RatingSummary(track_id=uuid4(), rating=6, client_updated_at=12345)


# ── Catalog Safety Invariant Tests ────────────────────────────────────────────


def test_no_media_deletion_or_catalog_track_creation(isolated_db: Session):
    """Confirm personalization service never creates or deletes catalog tracks."""
    track_repo = SqliteTrackRepository(isolated_db)
    t = _seed_track(isolated_db, title="Invariant Song")
    initial_count = track_repo.count()

    service = PersonalizationService(session=isolated_db)
    pl = service.create_playlist(PlaylistCreate(name="Invariant Playlist"))
    service.add_playlist_item(pl.id, PlaylistItemCreate(track_id=t.id))
    service.add_favorite(t.id)
    service.set_rating(t.id, 5)
    service.delete_playlist(pl.id)

    # Catalog track count must be completely unchanged
    assert track_repo.count() == initial_count
    assert track_repo.get_by_id(t.id) is not None
