"""Integration tests for Harmonia Personalization REST API endpoints: playlists, favorites, ratings."""

from __future__ import annotations

from pathlib import Path
from uuid import uuid4

import pytest
from fastapi.testclient import TestClient

from harmonia.api.app import create_app
from harmonia.core.config import HarmoniaConfig
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


@pytest.fixture
def test_setup(tmp_path: Path):
    """Set up database, test tracks, and FastAPI TestClient."""
    db_path = tmp_path / "personalization_api_test.db"
    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)

    config = HarmoniaConfig()
    config.database.path = db_path
    config.music_dirs = [tmp_path]

    # Seed an artist, album, and two tracks
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        album_repo = SqliteAlbumRepository(session)
        track_repo = SqliteTrackRepository(session)

        artist = Artist(name="Radiohead", sort_name="Radiohead")
        artist_repo.add(artist)

        album = Album(title="OK Computer", artist_id=artist.id, year=1997)
        album_repo.add(album)

        track1 = Track(
            title="Airbag",
            artist_id=artist.id,
            album_id=album.id,
            track_number=1,
            duration_seconds=284.0,
        )
        track2 = Track(
            title="Paranoid Android",
            artist_id=artist.id,
            album_id=album.id,
            track_number=2,
            duration_seconds=383.0,
        )
        track_repo.add(track1)
        track_repo.add(track2)

        audio_file1 = tmp_path / "airbag.flac"
        audio_file1.write_bytes(b"dummy audio data 1")
        tf1 = TrackFile(
            track_id=track1.id,
            file_path=str(audio_file1),
            file_size=len(audio_file1.read_bytes()),
            format=AudioFormat.FLAC,
            file_hash="hash_airbag",
            duration_seconds=284.0,
        )
        track_repo.add_file(tf1)

        audio_file2 = tmp_path / "paranoid.flac"
        audio_file2.write_bytes(b"dummy audio data 2")
        tf2 = TrackFile(
            track_id=track2.id,
            file_path=str(audio_file2),
            file_size=len(audio_file2.read_bytes()),
            format=AudioFormat.FLAC,
            file_hash="hash_paranoid",
            duration_seconds=383.0,
        )
        track_repo.add_file(tf2)

        session.commit()

        track1_id = track1.id
        track2_id = track2.id

    app = create_app(config=config, session_factory=session_factory)
    client = TestClient(app)

    return {
        "client": client,
        "config": config,
        "session_factory": session_factory,
        "track1_id": track1_id,
        "track2_id": track2_id,
        "audio_file1": audio_file1,
        "audio_file2": audio_file2,
    }


# ── Playlist API Tests ────────────────────────────────────────────────────────


def test_playlist_lifecycle_crud(test_setup) -> None:
    """Test full playlist CRUD: create, list, retrieve, update, and delete."""
    client: TestClient = test_setup["client"]

    # 1. Empty list
    res = client.get("/api/v1/playlists")
    assert res.status_code == 200
    data = res.json()
    assert data["items"] == []
    assert data["total"] == 0
    assert data["page"] == 1
    assert data["total_pages"] == 1

    # 2. Create playlist
    create_res = client.post(
        "/api/v1/playlists",
        json={"name": "Late Night Drive", "description": "Atmospheric tracks"},
    )
    assert create_res.status_code == 201
    created = create_res.json()
    playlist_id = created["id"]
    assert created["name"] == "Late Night Drive"
    assert created["description"] == "Atmospheric tracks"
    assert created["track_count"] == 0
    assert created["total_duration_seconds"] == 0.0
    assert created["items"] == []

    # 3. Retrieve by ID
    get_res = client.get(f"/api/v1/playlists/{playlist_id}")
    assert get_res.status_code == 200
    assert get_res.json()["id"] == playlist_id

    # 4. Update playlist
    update_res = client.put(
        f"/api/v1/playlists/{playlist_id}",
        json={"name": "Midnight Drive", "description": "Updated description"},
    )
    assert update_res.status_code == 200
    updated = update_res.json()
    assert updated["name"] == "Midnight Drive"
    assert updated["description"] == "Updated description"

    # 5. List playlists shows created playlist
    list_res = client.get("/api/v1/playlists")
    assert list_res.status_code == 200
    assert list_res.json()["total"] == 1
    assert list_res.json()["items"][0]["name"] == "Midnight Drive"

    # 6. Delete playlist
    del_res = client.delete(f"/api/v1/playlists/{playlist_id}")
    assert del_res.status_code == 204

    # 7. Retrieve after deletion -> 404
    assert client.get(f"/api/v1/playlists/{playlist_id}").status_code == 404


def test_playlist_validation_and_errors(test_setup) -> None:
    """Test validation errors for playlist creation and retrieval."""
    client: TestClient = test_setup["client"]

    # Empty or whitespace name -> 422
    assert client.post("/api/v1/playlists", json={"name": ""}).status_code == 422
    assert client.post("/api/v1/playlists", json={"name": "   "}).status_code == 422

    # Name exceeding 200 chars -> 422
    assert client.post("/api/v1/playlists", json={"name": "a" * 201}).status_code == 422

    # Malformed UUID -> 422
    assert client.get("/api/v1/playlists/not-a-uuid").status_code == 422

    # Nonexistent UUID -> 404
    assert client.get(f"/api/v1/playlists/{uuid4()}").status_code == 404

    # Delete nonexistent -> 404
    assert client.delete(f"/api/v1/playlists/{uuid4()}").status_code == 404

    # Update nonexistent -> 404
    assert client.put(f"/api/v1/playlists/{uuid4()}", json={"name": "New Name"}).status_code == 404


def test_playlist_items_management_and_ordering(test_setup) -> None:
    """Test adding, duplicate entries, moving, and removing playlist items."""
    client: TestClient = test_setup["client"]
    t1 = test_setup["track1_id"]
    t2 = test_setup["track2_id"]

    # Create playlist
    pl_res = client.post("/api/v1/playlists", json={"name": "Test Queue"})
    playlist_id = pl_res.json()["id"]

    # 1. Add track1 (appends at position 0)
    item1_res = client.post(f"/api/v1/playlists/{playlist_id}/items", json={"track_id": str(t1)})
    assert item1_res.status_code == 201
    item1 = item1_res.json()
    assert item1["track_id"] == str(t1)
    assert item1["position"] == 0
    assert item1["track_title"] == "Airbag"
    assert item1["track_artist"] == "Radiohead"

    # 2. Add track2 (appends at position 1)
    item2_res = client.post(f"/api/v1/playlists/{playlist_id}/items", json={"track_id": str(t2)})
    assert item2_res.status_code == 201
    item2 = item2_res.json()
    assert item2["position"] == 1

    # 3. Add duplicate track1 at position 1 (causes collision shift of item2 to position 2)
    item3_res = client.post(
        f"/api/v1/playlists/{playlist_id}/items",
        json={"track_id": str(t1), "position": 1},
    )
    assert item3_res.status_code == 201
    item3 = item3_res.json()
    assert item3["position"] == 1
    assert item3["id"] != item1["id"]  # distinct item ID

    # Verify playlist detail has 3 items in order
    detail_res = client.get(f"/api/v1/playlists/{playlist_id}")
    items = detail_res.json()["items"]
    assert len(items) == 3
    assert [i["position"] for i in items] == [0, 1, 2]
    assert items[0]["id"] == item1["id"]
    assert items[1]["id"] == item3["id"]
    assert items[2]["id"] == item2["id"]
    assert detail_res.json()["track_count"] == 3
    assert detail_res.json()["total_duration_seconds"] == 284.0 + 284.0 + 383.0

    # 4. Move item2 from position 2 to position 0
    move_res = client.post(
        f"/api/v1/playlists/{playlist_id}/items/{item2['id']}/move",
        json={"new_position": 0},
    )
    assert move_res.status_code == 200
    moved_items = move_res.json()["items"]
    assert moved_items[0]["id"] == item2["id"]
    assert moved_items[0]["position"] == 0

    # 5. Remove an item
    del_item_res = client.delete(f"/api/v1/playlists/{playlist_id}/items/{item3['id']}")
    assert del_item_res.status_code == 204

    # Remove again -> 404
    assert client.delete(f"/api/v1/playlists/{playlist_id}/items/{item3['id']}").status_code == 404

    # Add missing track -> 404
    assert (
        client.post(
            f"/api/v1/playlists/{playlist_id}/items",
            json={"track_id": str(uuid4())},
        ).status_code
        == 404
    )

    # Negative position -> 422
    assert (
        client.post(
            f"/api/v1/playlists/{playlist_id}/items",
            json={"track_id": str(t1), "position": -1},
        ).status_code
        == 422
    )


# ── Favorite API Tests ────────────────────────────────────────────────────────


def test_favorites_api_lifecycle(test_setup) -> None:
    """Test favorite add, list, idempotency, and removal."""
    client: TestClient = test_setup["client"]
    t1 = test_setup["track1_id"]
    t2 = test_setup["track2_id"]

    # 1. Initial list is empty
    empty_res = client.get("/api/v1/favorites")
    assert empty_res.status_code == 200
    assert empty_res.json()["total"] == 0
    assert empty_res.json()["items"] == []

    # 2. Add favorite for track1
    fav1_res = client.post(f"/api/v1/favorites/{t1}")
    assert fav1_res.status_code == 201
    fav1 = fav1_res.json()
    assert fav1["track_id"] == str(t1)
    assert fav1["track_title"] == "Airbag"
    assert fav1["track_artist"] == "Radiohead"
    assert fav1["client_updated_at"] > 0

    # 3. Repeated add (idempotent)
    fav1_repeat = client.post(f"/api/v1/favorites/{t1}")
    assert fav1_repeat.status_code == 201
    assert fav1_repeat.json()["track_id"] == str(t1)

    # 4. Add favorite for track2
    fav2_res = client.post(f"/api/v1/favorites/{t2}")
    assert fav2_res.status_code == 201

    # 5. List favorites
    list_res = client.get("/api/v1/favorites?page=1&page_size=10")
    assert list_res.status_code == 200
    data = list_res.json()
    assert data["total"] == 2
    assert len(data["items"]) == 2

    # 6. Remove favorite
    del_res = client.delete(f"/api/v1/favorites/{t1}")
    assert del_res.status_code == 204

    # Verify count is now 1
    assert client.get("/api/v1/favorites").json()["total"] == 1

    # Nonexistent track favorite -> 404
    assert client.post(f"/api/v1/favorites/{uuid4()}").status_code == 404
    assert client.delete(f"/api/v1/favorites/{uuid4()}").status_code == 404


# ── Rating API Tests ──────────────────────────────────────────────────────────


def test_ratings_api_lifecycle(test_setup) -> None:
    """Test setting, getting (rated and unrated), filtering, and removing ratings."""
    client: TestClient = test_setup["client"]
    t1 = test_setup["track1_id"]
    t2 = test_setup["track2_id"]

    # 1. Unrated existing track returns 200 with is_rated=False
    unrated_res = client.get(f"/api/v1/ratings/{t1}")
    assert unrated_res.status_code == 200
    unrated = unrated_res.json()
    assert unrated["track_id"] == str(t1)
    assert unrated["is_rated"] is False
    assert unrated["rating"] is None
    assert unrated["track_title"] == "Airbag"

    # 2. Set rating to 5 stars
    set5_res = client.put(f"/api/v1/ratings/{t1}", json={"rating": 5})
    assert set5_res.status_code == 200
    r5 = set5_res.json()
    assert r5["rating"] == 5
    assert r5["is_rated"] is True
    assert r5["track_title"] == "Airbag"
    assert r5["track_artist"] == "Radiohead"

    # 3. Get rating now returns 5 stars
    get5_res = client.get(f"/api/v1/ratings/{t1}")
    assert get5_res.status_code == 200
    assert get5_res.json()["rating"] == 5
    assert get5_res.json()["is_rated"] is True

    # 4. Set rating for track2 to 3 stars
    assert client.put(f"/api/v1/ratings/{t2}", json={"rating": 3}).status_code == 200

    # 5. List ratings without filter -> 2 items
    list_all = client.get("/api/v1/ratings")
    assert list_all.status_code == 200
    assert list_all.json()["total"] == 2

    # 6. List ratings with min_rating=4 -> 1 item (track1 only)
    list_filtered = client.get("/api/v1/ratings?min_rating=4")
    assert list_filtered.status_code == 200
    assert list_filtered.json()["total"] == 1
    assert list_filtered.json()["items"][0]["track_id"] == str(t1)

    # 7. Rating validation outside 1-5 -> 422
    assert client.put(f"/api/v1/ratings/{t1}", json={"rating": 0}).status_code == 422
    assert client.put(f"/api/v1/ratings/{t1}", json={"rating": 6}).status_code == 422
    assert client.get("/api/v1/ratings?min_rating=6").status_code == 422

    # 8. Nonexistent track rating -> 404
    assert client.get(f"/api/v1/ratings/{uuid4()}").status_code == 404
    assert client.put(f"/api/v1/ratings/{uuid4()}", json={"rating": 4}).status_code == 404
    assert client.delete(f"/api/v1/ratings/{uuid4()}").status_code == 404

    # 9. Delete rating
    assert client.delete(f"/api/v1/ratings/{t1}").status_code == 204
    # Track is now unrated again
    assert client.get(f"/api/v1/ratings/{t1}").json()["is_rated"] is False


# ── Safety & Integrity Tests ──────────────────────────────────────────────────


def test_personalization_data_safety_and_catalog_invariance(test_setup) -> None:
    """Ensure personalization mutations do not alter catalog tracks or audio files."""
    client: TestClient = test_setup["client"]
    t1 = test_setup["track1_id"]
    audio_file1: Path = test_setup["audio_file1"]

    audio_bytes_before = audio_file1.read_bytes()

    # Create playlist, add item, favorite, and rate track1
    pl_res = client.post("/api/v1/playlists", json={"name": "Safety Test"})
    pl_id = pl_res.json()["id"]
    item_res = client.post(f"/api/v1/playlists/{pl_id}/items", json={"track_id": str(t1)})
    item_id = item_res.json()["id"]

    client.post(f"/api/v1/favorites/{t1}")
    client.put(f"/api/v1/ratings/{t1}", json={"rating": 5})

    # Delete playlist, item, favorite, rating
    client.delete(f"/api/v1/playlists/{pl_id}/items/{item_id}")
    client.delete(f"/api/v1/playlists/{pl_id}")
    client.delete(f"/api/v1/favorites/{t1}")
    client.delete(f"/api/v1/ratings/{t1}")

    # Track in catalog must remain intact
    track_res = client.get(f"/api/v1/tracks/{t1}")
    assert track_res.status_code == 200
    assert track_res.json()["title"] == "Airbag"

    # Audio file on disk must be identical
    assert audio_file1.exists()
    assert audio_file1.read_bytes() == audio_bytes_before


def test_security_auth_token_enforcement(test_setup) -> None:
    """Verify that personalization routes enforce API token when configured."""
    config: HarmoniaConfig = test_setup["config"]
    session_factory = test_setup["session_factory"]
    t1 = test_setup["track1_id"]

    config.api.token = "secure-harmonia-token-999"
    auth_app = create_app(config=config, session_factory=session_factory)
    client = TestClient(auth_app)

    # Missing token -> 401
    assert client.get("/api/v1/playlists").status_code == 401
    assert client.post("/api/v1/playlists", json={"name": "Auth PL"}).status_code == 401
    assert client.get("/api/v1/favorites").status_code == 401
    assert client.post(f"/api/v1/favorites/{t1}").status_code == 401
    assert client.get("/api/v1/ratings").status_code == 401
    assert client.put(f"/api/v1/ratings/{t1}", json={"rating": 5}).status_code == 401

    # Invalid token -> 401
    assert (
        client.get("/api/v1/playlists", headers={"X-Harmonia-Token": "wrong-token"}).status_code
        == 401
    )

    # Valid X-Harmonia-Token header -> 200
    assert (
        client.get(
            "/api/v1/playlists",
            headers={"X-Harmonia-Token": "secure-harmonia-token-999"},
        ).status_code
        == 200
    )

    # Valid Authorization: Bearer token -> 200
    assert (
        client.get(
            "/api/v1/playlists",
            headers={"Authorization": "Bearer secure-harmonia-token-999"},
        ).status_code
        == 200
    )


def test_cors_loopback_policy(test_setup) -> None:
    """Verify loopback CORS enforcement on personalization routes."""
    client: TestClient = test_setup["client"]

    # Allowed loopback origin
    res_loopback = client.options(
        "/api/v1/playlists",
        headers={
            "Origin": "http://localhost:3000",
            "Access-Control-Request-Method": "POST",
        },
    )
    assert res_loopback.headers.get("access-control-allow-origin") == "http://localhost:3000"

    # Disallowed external origin
    res_disallowed = client.options(
        "/api/v1/playlists",
        headers={
            "Origin": "http://evil-attacker.com",
            "Access-Control-Request-Method": "POST",
        },
    )
    assert res_disallowed.headers.get("access-control-allow-origin") is None


def test_cross_origin_mutation_protection_hostile_origins(test_setup) -> None:
    """Verify that hostile origins are rejected on all state-changing endpoints with no mutations."""
    client: TestClient = test_setup["client"]
    t1 = test_setup["track1_id"]

    def _is_fav(tid) -> bool:
        return any(i["track_id"] == str(tid) for i in client.get("/api/v1/favorites").json()["items"])

    # 1. Hostile simple form-style POST to favorites
    res_form = client.post(
        f"/api/v1/favorites/{t1}",
        headers={
            "Origin": "https://evil.com",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    assert res_form.status_code == 403
    assert "Forbidden" in res_form.json()["detail"]

    # Assert underlying state unchanged: track is NOT a favorite
    assert _is_fav(t1) is False

    # 2. Hostile text/plain POST to favorites
    res_text = client.post(
        f"/api/v1/favorites/{t1}",
        headers={
            "Origin": "https://evil.com",
            "Content-Type": "text/plain",
        },
        content=b"malicious-payload",
    )
    assert res_text.status_code == 403
    assert _is_fav(t1) is False

    # 3. Opaque 'null' origin POST (e.g. sandboxed iframe or data: URI)
    res_null = client.post(
        f"/api/v1/favorites/{t1}",
        headers={"Origin": "null"},
    )
    assert res_null.status_code == 403
    assert _is_fav(t1) is False

    # 4. Hostile Origin on playback route families
    res_play = client.post(
        "/api/v1/playback/play",
        headers={"Origin": "https://evil-hacker.com"},
    )
    assert res_play.status_code == 403
    # Verify playback state remains idle (unstarted)
    pb_state = client.get("/api/v1/playback/state")
    assert pb_state.status_code == 200
    assert pb_state.json()["status"] == "idle"

    # Other playback mutation endpoints
    for endpoint in [
        "/api/v1/playback/pause",
        "/api/v1/playback/resume",
        "/api/v1/playback/stop",
        "/api/v1/playback/next",
        "/api/v1/playback/previous",
    ]:
        res = client.post(endpoint, headers={"Origin": "https://evil-hacker.com"})
        assert res.status_code == 403, f"Failed for {endpoint}"

    # 5. Hostile Origin on queue mutation endpoints
    res_queue_set = client.post(
        "/api/v1/queue/set",
        json={"track_ids": [str(t1)]},
        headers={"Origin": "https://evil-hacker.com"},
    )
    assert res_queue_set.status_code == 403
    # Verify queue remains empty
    queue_res = client.get("/api/v1/queue")
    assert queue_res.status_code == 200
    assert queue_res.json() == []

    res_queue_clear = client.delete(
        "/api/v1/queue",
        headers={"Origin": "https://evil-hacker.com"},
    )
    assert res_queue_clear.status_code == 403

    # 6. Hostile Origin on playlist creation & deletion
    res_pl_create = client.post(
        "/api/v1/playlists",
        json={"name": "Attacker Playlist"},
        headers={"Origin": "https://evil-hacker.com"},
    )
    assert res_pl_create.status_code == 403
    # Verify no playlist created
    assert client.get("/api/v1/playlists").json()["total"] == 0

    # 7. Hostile Origin on ratings
    res_rating = client.put(
        f"/api/v1/ratings/{t1}",
        json={"rating": 5},
        headers={"Origin": "https://evil-hacker.com"},
    )
    assert res_rating.status_code == 403
    # Verify rating was not created
    rating_check = client.get(f"/api/v1/ratings/{t1}")
    assert rating_check.status_code == 200
    assert rating_check.json()["is_rated"] is False
    assert rating_check.json()["rating"] is None

    # 8. GET endpoints are NOT blocked by Origin check (read-only safe)
    get_res = client.get(
        "/api/v1/playlists",
        headers={"Origin": "https://some-website.com"},
    )
    assert get_res.status_code == 200


def test_cross_origin_mutation_referer_and_absent_headers(test_setup) -> None:
    """Verify Referer header checks and allowlist behavior for absent headers."""
    client: TestClient = test_setup["client"]
    t1 = test_setup["track1_id"]

    def _is_fav(tid) -> bool:
        return any(i["track_id"] == str(tid) for i in client.get("/api/v1/favorites").json()["items"])

    # 1. Untrusted Referer with absent Origin -> 403
    res_untrusted_ref = client.post(
        f"/api/v1/favorites/{t1}",
        headers={"Referer": "https://malicious.org/csrf-page.html"},
    )
    assert res_untrusted_ref.status_code == 403
    assert _is_fav(t1) is False

    # 2. Malformed Referer -> 403
    res_malformed_ref = client.post(
        f"/api/v1/favorites/{t1}",
        headers={"Referer": "http://:::invalid"},
    )
    assert res_malformed_ref.status_code == 403

    # 3. Trusted loopback Referer -> allowed (201)
    res_loopback_ref = client.post(
        f"/api/v1/favorites/{t1}",
        headers={"Referer": "http://localhost:5173/library"},
    )
    assert res_loopback_ref.status_code == 201
    assert _is_fav(t1) is True

    # Trusted 127.0.0.1 Referer for DELETE
    res_delete_ref = client.delete(
        f"/api/v1/favorites/{t1}",
        headers={"Referer": "http://127.0.0.1:8000/app"},
    )
    assert res_delete_ref.status_code == 204
    assert _is_fav(t1) is False

    # 4. Absent Origin and absent Referer (standard CLI / native desktop app) -> allowed
    res_absent = client.post(f"/api/v1/favorites/{t1}")
    assert res_absent.status_code == 201
    assert _is_fav(t1) is True

    res_absent_del = client.delete(f"/api/v1/favorites/{t1}")
    assert res_absent_del.status_code == 204
    assert _is_fav(t1) is False


def test_cross_origin_mutation_valid_loopback_origins(test_setup) -> None:
    """Verify legitimate loopback origins are permitted for state mutations."""
    client: TestClient = test_setup["client"]
    t1 = test_setup["track1_id"]

    def _is_fav(tid) -> bool:
        return any(i["track_id"] == str(tid) for i in client.get("/api/v1/favorites").json()["items"])

    for origin in [
        "http://localhost:3000",
        "http://localhost:8000",
        "http://127.0.0.1:5173",
        "http://[::1]:8080",
        "https://localhost",
    ]:
        res = client.post(
            f"/api/v1/favorites/{t1}",
            headers={"Origin": origin},
        )
        assert res.status_code in (200, 201), f"Origin {origin} failed with {res.status_code}"
        assert _is_fav(t1) is True

        res_del = client.delete(
            f"/api/v1/favorites/{t1}",
            headers={"Origin": origin},
        )
        assert res_del.status_code == 204, f"Delete with origin {origin} failed"
        assert _is_fav(t1) is False


def test_host_header_validation(test_setup) -> None:
    """Verify Host-header validation blocks hostile hosts and permits legitimate local hosts."""
    client: TestClient = test_setup["client"]
    t1 = test_setup["track1_id"]

    def _is_fav(tid) -> bool:
        return any(i["track_id"] == str(tid) for i in client.get("/api/v1/favorites").json()["items"])

    # 1. Hostile Host header -> 400 Invalid host header
    res_evil_host = client.get(
        "/api/v1/playlists",
        headers={"Host": "evil.com"},
    )
    assert res_evil_host.status_code == 400
    assert "Invalid host header" in res_evil_host.text

    # State-changing with hostile host -> 400, no state change
    res_evil_post = client.post(
        f"/api/v1/favorites/{t1}",
        headers={"Host": "evil.com"},
    )
    assert res_evil_post.status_code == 400
    assert _is_fav(t1) is False

    # Subdomain spoofing
    res_spoof = client.get(
        "/api/v1/playlists",
        headers={"Host": "localhost.evil.com"},
    )
    assert res_spoof.status_code == 400

    # 2. Legitimate local hosts
    for valid_host in [
        "localhost",
        "localhost:8000",
        "127.0.0.1",
        "127.0.0.1:8000",
        "[::1]",
        "[::1]:8000",
        "testserver",
        "sub.localhost",
    ]:
        res = client.get(
            "/api/v1/playlists",
            headers={"Host": valid_host},
        )
        assert res.status_code == 200, f"Host {valid_host} failed with status {res.status_code}"


def test_auth_token_with_cross_origin_interaction(test_setup) -> None:
    """Verify that even with valid API token, hostile origins are rejected on mutation routes."""
    config: HarmoniaConfig = test_setup["config"]
    session_factory = test_setup["session_factory"]
    t1 = test_setup["track1_id"]

    config.api.token = "secure-token-123"
    auth_app = create_app(config=config, session_factory=session_factory)
    client = TestClient(auth_app)

    # Valid token + hostile origin -> 403 Forbidden
    res = client.post(
        f"/api/v1/favorites/{t1}",
        headers={
            "X-Harmonia-Token": "secure-token-123",
            "Origin": "https://evil.com",
        },
    )
    assert res.status_code == 403

    # Invalid token + hostile origin -> 401 Unauthorized (token checked first)
    res_bad = client.post(
        f"/api/v1/favorites/{t1}",
        headers={
            "X-Harmonia-Token": "bad-token",
            "Origin": "https://evil.com",
        },
    )
    assert res_bad.status_code == 401


def test_playlist_description_clearing_contract(test_setup) -> None:
    """Verify the playlist description contract: omitted, string, null/clear, empty-string, and name-only updates."""
    client: TestClient = test_setup["client"]
    session_factory = test_setup["session_factory"]

    # 1. Create playlist with initial name and description
    created = client.post(
        "/api/v1/playlists",
        json={"name": "Initial Name", "description": "Initial Description"},
    ).json()
    pl_id = created["id"]
    assert created["name"] == "Initial Name"
    assert created["description"] == "Initial Description"

    # 2. Omitted description (name-only update) -> keep existing description
    up1 = client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"name": "Updated Name 1"},
    )
    assert up1.status_code == 200
    assert up1.json()["name"] == "Updated Name 1"
    assert up1.json()["description"] == "Initial Description"

    # 3. String description supplied -> replace description
    up2 = client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"description": "Replaced Description"},
    )
    assert up2.status_code == 200
    assert up2.json()["name"] == "Updated Name 1"
    assert up2.json()["description"] == "Replaced Description"

    # 4. Explicit null description -> persist None
    up3 = client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"description": None},
    )
    assert up3.status_code == 200
    assert up3.json()["description"] is None

    # Verify directly from database via session
    from uuid import UUID

    from harmonia.infrastructure.persistence.playlist_repo import SqlitePlaylistRepository
    with session_factory() as session:
        repo = SqlitePlaylistRepository(session)
        persisted = repo.get_by_id(UUID(pl_id))
        assert persisted is not None
        assert persisted.description is None

    # 5. Restore description, then clear with empty string
    client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"description": "Temporary Description"},
    )
    up4 = client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"description": ""},
    )
    assert up4.status_code == 200
    assert up4.json()["description"] is None
    with session_factory() as session:
        persisted = SqlitePlaylistRepository(session).get_by_id(UUID(pl_id))
        assert persisted is not None
        assert persisted.description is None

    # 6. Restore description, then clear with whitespace-only string
    client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"description": "Temporary Description 2"},
    )
    up5 = client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"description": "   \t\n  "},
    )
    assert up5.status_code == 200
    assert up5.json()["description"] is None

    # 7. Restore description, then clear with clear_description=True
    client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"description": "Temporary Description 3"},
    )
    up6 = client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"clear_description": True},
    )
    assert up6.status_code == 200
    assert up6.json()["description"] is None

    # 8. Both description string and clear_description=True -> 422 Unprocessable Entity
    up7 = client.put(
        f"/api/v1/playlists/{pl_id}",
        json={"description": "Valid string", "clear_description": True},
    )
    assert up7.status_code == 422
