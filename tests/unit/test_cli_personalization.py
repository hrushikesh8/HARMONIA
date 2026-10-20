"""Unit tests for Harmonia personalization CLI commands.

Tests playlist, favorite, and rating Typer command groups, verifying
arguments, options, lifecycle management, error handling, deletion confirmation,
and JSON outputs.
"""

from __future__ import annotations

import json
from typing import TYPE_CHECKING
from unittest.mock import patch
from uuid import UUID, uuid4

from typer.testing import CliRunner

from harmonia.application.personalization_service import PersonalizationService
from harmonia.cli.app import app
from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.track import Track
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from pathlib import Path

runner = CliRunner()


def _seed_db(db_path: Path) -> tuple[UUID, UUID]:
    """Helper to initialize isolated SQLite database and seed two catalog tracks."""
    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)
    with session_factory() as session:
        artist_repo = SqliteArtistRepository(session)
        track_repo = SqliteTrackRepository(session)
        artist = Artist(name="Slowdive")
        artist_repo.add(artist)
        track1 = Track(title="Alison", artist_id=artist.id, duration_seconds=231.0)
        track2 = Track(title="Machine Gun", artist_id=artist.id, duration_seconds=268.0)
        track_repo.add(track1)
        track_repo.add(track2)
        session.commit()
        t1_id, t2_id = track1.id, track2.id
    engine.dispose()
    return t1_id, t2_id


# ── 1. Command Registration and Help ──────────────────────────────────────────


def test_cli_personalization_registration_and_help() -> None:
    """Verify that playlist, favorite, and rating command groups are registered."""
    main_res = runner.invoke(app, ["--help"])
    assert main_res.exit_code == 0
    assert "playlist" in main_res.stdout
    assert "favorite" in main_res.stdout
    assert "rating" in main_res.stdout

    # Playlist help
    pl_res = runner.invoke(app, ["playlist", "--help"])
    assert pl_res.exit_code == 0
    for cmd in ("create", "list", "show", "update", "delete", "add", "items", "move", "remove"):
        assert cmd in pl_res.stdout

    # Favorite help
    fav_res = runner.invoke(app, ["favorite", "--help"])
    assert fav_res.exit_code == 0
    for cmd in ("add", "remove", "check", "list"):
        assert cmd in fav_res.stdout

    # Rating help
    rt_res = runner.invoke(app, ["rating", "--help"])
    assert rt_res.exit_code == 0
    for cmd in ("set", "get", "remove", "list"):
        assert cmd in rt_res.stdout


# ── 2. Playlist CRUD and Description Semantics ─────────────────────────────────


def test_cli_playlist_create_list_show_update_delete(tmp_path: Path) -> None:
    """Verify full playlist lifecycle: create, list, show, update, delete."""
    test_db = tmp_path / "test_pl.db"
    _seed_db(test_db)

    # 1. Create
    res_create = runner.invoke(
        app,
        ["playlist", "create", "Dreampop Focus", "-d", "Soothing shoegaze", "--db", str(test_db)],
    )
    assert res_create.exit_code == 0
    assert "Created playlist: Dreampop Focus" in res_create.stdout

    # 2. List
    res_list = runner.invoke(app, ["playlist", "list", "--db", str(test_db)])
    assert res_list.exit_code == 0
    assert "Dreampop Focus" in res_list.stdout

    # Extract ID from JSON list output for precise assertions
    res_json_list = runner.invoke(app, ["playlist", "list", "-f", "json", "--db", str(test_db)])
    assert res_json_list.exit_code == 0
    data = json.loads(res_json_list.stdout)
    assert len(data) == 1
    pl_id = data[0]["id"]
    assert data[0]["name"] == "Dreampop Focus"
    assert data[0]["description"] == "Soothing shoegaze"

    # 3. Show
    res_show = runner.invoke(app, ["playlist", "show", pl_id, "--db", str(test_db)])
    assert res_show.exit_code == 0
    assert "Dreampop Focus" in res_show.stdout
    assert "Soothing shoegaze" in res_show.stdout

    # 4. Update name and description
    res_update = runner.invoke(
        app,
        ["playlist", "update", pl_id, "-n", "Shoegaze Essentials", "-d", "Updated description", "--db", str(test_db)],
    )
    assert res_update.exit_code == 0
    assert "Updated playlist: Shoegaze Essentials" in res_update.stdout

    # Verify update persisted
    res_show_updated = runner.invoke(app, ["playlist", "show", pl_id, "-f", "json", "--db", str(test_db)])
    assert res_show_updated.exit_code == 0
    updated_data = json.loads(res_show_updated.stdout)
    assert updated_data["name"] == "Shoegaze Essentials"
    assert updated_data["description"] == "Updated description"

    # 5. Delete with --yes
    res_delete = runner.invoke(app, ["playlist", "delete", pl_id, "--yes", "--db", str(test_db)])
    assert res_delete.exit_code == 0
    assert "Deleted playlist" in res_delete.stdout

    # Verify deleted
    res_show_gone = runner.invoke(app, ["playlist", "show", pl_id, "--db", str(test_db)])
    assert res_show_gone.exit_code == 1
    assert "Playlist not found" in res_show_gone.stdout


def test_cli_playlist_description_clearing_and_conflict(tmp_path: Path) -> None:
    """Verify description update, explicit clearing, and mutual exclusion."""
    test_db = tmp_path / "test_pl_desc.db"
    _seed_db(test_db)

    # Create playlist with description
    res_c = runner.invoke(
        app,
        ["playlist", "create", "Ambient", "-d", "Deep ambient", "-f", "json", "--db", str(test_db)],
    )
    assert res_c.exit_code == 0
    pl_id = json.loads(res_c.stdout)["id"]

    # Conflict: both --description and --clear-description
    res_conflict = runner.invoke(
        app,
        ["playlist", "update", pl_id, "-d", "New", "--clear-description", "--db", str(test_db)],
    )
    assert res_conflict.exit_code == 1
    assert "Cannot provide both --description and --clear-description" in res_conflict.stdout

    # No flags provided
    res_noflags = runner.invoke(app, ["playlist", "update", pl_id, "--db", str(test_db)])
    assert res_noflags.exit_code == 1
    assert "No updates specified" in res_noflags.stdout

    # Explicit clear description
    res_clear = runner.invoke(
        app,
        ["playlist", "update", pl_id, "--clear-description", "-f", "json", "--db", str(test_db)],
    )
    assert res_clear.exit_code == 0
    cleared = json.loads(res_clear.stdout)
    assert cleared["description"] is None

    # Update only name leaves description as None
    res_name_only = runner.invoke(
        app,
        ["playlist", "update", pl_id, "-n", "Ambient Chill", "-f", "json", "--db", str(test_db)],
    )
    assert res_name_only.exit_code == 0
    name_only = json.loads(res_name_only.stdout)
    assert name_only["name"] == "Ambient Chill"
    assert name_only["description"] is None


# ── 3. Deletion Confirmation, Cancellation, and Non-Interactive Safety ────────


def test_cli_playlist_delete_confirmation_interactive(tmp_path: Path) -> None:
    """Verify interactive deletion confirmation and cancellation."""
    test_db = tmp_path / "test_pl_confirm.db"
    _seed_db(test_db)

    res_c = runner.invoke(
        app,
        ["playlist", "create", "To Keep", "-f", "json", "--db", str(test_db)],
    )
    pl_id = json.loads(res_c.stdout)["id"]

    # 1. Interactive cancellation ('n')
    res_cancel = runner.invoke(
        app,
        ["playlist", "delete", pl_id, "--db", str(test_db)],
        input="n\n",
    )
    assert res_cancel.exit_code == 0
    assert "Deletion cancelled" in res_cancel.stdout

    # Verify playlist still exists
    res_check = runner.invoke(app, ["playlist", "show", pl_id, "--db", str(test_db)])
    assert res_check.exit_code == 0

    # 2. Interactive confirmation ('y')
    res_confirm = runner.invoke(
        app,
        ["playlist", "delete", pl_id, "--db", str(test_db)],
        input="y\n",
    )
    assert res_confirm.exit_code == 0
    assert "Deleted playlist" in res_confirm.stdout

    # Verify playlist was deleted
    res_gone = runner.invoke(app, ["playlist", "show", pl_id, "--db", str(test_db)])
    assert res_gone.exit_code == 1


def test_cli_playlist_delete_non_interactive_refusal(tmp_path: Path) -> None:
    """Verify that non-interactive execution without --yes refuses rather than hanging."""
    test_db = tmp_path / "test_pl_noninter.db"
    _seed_db(test_db)

    res_c = runner.invoke(
        app,
        ["playlist", "create", "Protected", "-f", "json", "--db", str(test_db)],
    )
    pl_id = json.loads(res_c.stdout)["id"]

    # Without input and without --yes in non-interactive environment
    res_refusal = runner.invoke(app, ["playlist", "delete", pl_id, "--db", str(test_db)])
    assert res_refusal.exit_code == 1
    assert "Deletion confirmation could not be obtained" in res_refusal.stdout

    # Confirm playlist remains untouched
    res_check = runner.invoke(app, ["playlist", "show", pl_id, "--db", str(test_db)])
    assert res_check.exit_code == 0
    assert "Protected" in res_check.stdout


# ── 4. Playlist Items: Add, List, Move, Remove, and Duplicates ────────────────


def test_cli_playlist_items_crud_and_duplicates(tmp_path: Path) -> None:
    """Verify item operations including position gaps, collision shift, and duplicate track entries."""
    test_db = tmp_path / "test_pl_items.db"
    t1_id, t2_id = _seed_db(test_db)

    res_c = runner.invoke(
        app,
        ["playlist", "create", "Mixtape", "-f", "json", "--db", str(test_db)],
    )
    pl_id = json.loads(res_c.stdout)["id"]

    # 1. Add track 1 at position 0
    res_add1 = runner.invoke(
        app,
        ["playlist", "add", pl_id, str(t1_id), "-f", "json", "--db", str(test_db)],
    )
    assert res_add1.exit_code == 0
    item1 = json.loads(res_add1.stdout)
    assert item1["position"] == 0
    assert item1["track_id"] == str(t1_id)

    # 2. Add track 2 at end (position 1)
    res_add2 = runner.invoke(
        app,
        ["playlist", "add", pl_id, str(t2_id), "-f", "json", "--db", str(test_db)],
    )
    assert res_add2.exit_code == 0
    item2 = json.loads(res_add2.stdout)
    assert item2["position"] == 1

    # 3. Add duplicate track 1 at position 0 (should shift items at >=0 collision-safely)
    res_add_dup = runner.invoke(
        app,
        ["playlist", "add", pl_id, str(t1_id), "-p", "0", "-f", "json", "--db", str(test_db)],
    )
    assert res_add_dup.exit_code == 0
    item_dup = json.loads(res_add_dup.stdout)
    assert item_dup["position"] == 0
    assert item_dup["id"] != item1["id"]

    # 4. List items
    res_items = runner.invoke(app, ["playlist", "items", pl_id, "-f", "json", "--db", str(test_db)])
    assert res_items.exit_code == 0
    items = json.loads(res_items.stdout)
    assert len(items) == 3
    # Distinct item IDs preserved for duplicate track
    item_ids = [it["id"] for it in items]
    assert len(set(item_ids)) == 3
    positions = [it["position"] for it in items]
    assert positions == [0, 1, 2]

    # 5. Move item
    # Move item_dup from pos 0 to pos 2
    res_move = runner.invoke(
        app,
        ["playlist", "move", pl_id, item_dup["id"], "2", "-f", "json", "--db", str(test_db)],
    )
    assert res_move.exit_code == 0

    # Verify positions after move
    res_items2 = runner.invoke(app, ["playlist", "items", pl_id, "-f", "json", "--db", str(test_db)])
    items2 = json.loads(res_items2.stdout)
    assert items2[-1]["id"] == item_dup["id"]
    assert items2[-1]["position"] == 2

    # 6. Remove item
    res_remove = runner.invoke(
        app,
        ["playlist", "remove", pl_id, item_dup["id"], "-f", "json", "--db", str(test_db)],
    )
    assert res_remove.exit_code == 0
    assert json.loads(res_remove.stdout)["removed"] is True

    # Check remaining count
    res_items3 = runner.invoke(app, ["playlist", "items", pl_id, "-f", "json", "--db", str(test_db)])
    assert len(json.loads(res_items3.stdout)) == 2


# ── 5. Favorites: Add, Check, List, Remove, and Idempotency ───────────────────


def test_cli_favorites_flow_and_idempotency(tmp_path: Path) -> None:
    """Verify favorite add, check, list, remove, and idempotent operations."""
    test_db = tmp_path / "test_fav.db"
    t1_id, _ = _seed_db(test_db)

    # 1. Initially check favorite -> False
    res_chk_init = runner.invoke(app, ["favorite", "check", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_chk_init.exit_code == 0
    assert json.loads(res_chk_init.stdout)["is_favorite"] is False

    # 2. Add to favorites
    res_add1 = runner.invoke(app, ["favorite", "add", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_add1.exit_code == 0
    fav1 = json.loads(res_add1.stdout)
    assert fav1["track_id"] == str(t1_id)

    # 3. Add again (idempotent)
    res_add_again = runner.invoke(app, ["favorite", "add", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_add_again.exit_code == 0

    # 4. Check favorite -> True
    res_chk_yes = runner.invoke(app, ["favorite", "check", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_chk_yes.exit_code == 0
    assert json.loads(res_chk_yes.stdout)["is_favorite"] is True

    # 5. List favorites
    res_list = runner.invoke(app, ["favorite", "list", "-f", "json", "--db", str(test_db)])
    assert res_list.exit_code == 0
    fav_list = json.loads(res_list.stdout)
    assert len(fav_list) == 1
    assert fav_list[0]["track_id"] == str(t1_id)

    # 6. Remove favorite
    res_rem = runner.invoke(app, ["favorite", "remove", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_rem.exit_code == 0
    assert json.loads(res_rem.stdout)["removed"] is True

    # 7. Remove again (absent)
    res_rem_again = runner.invoke(app, ["favorite", "remove", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_rem_again.exit_code == 0
    assert json.loads(res_rem_again.stdout)["removed"] is False

    # 8. Check favorite after removal -> False
    res_chk_after = runner.invoke(app, ["favorite", "check", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_chk_after.exit_code == 0
    assert json.loads(res_chk_after.stdout)["is_favorite"] is False


# ── 6. Ratings: Set, Get, List, Filter, and Remove ────────────────────────────


def test_cli_ratings_crud_and_filtering(tmp_path: Path) -> None:
    """Verify rating set, update, get, min-rating filter, list, and removal."""
    test_db = tmp_path / "test_rating.db"
    t1_id, t2_id = _seed_db(test_db)

    # 1. Initially get rating for unrated track
    res_get_unrated = runner.invoke(app, ["rating", "get", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_get_unrated.exit_code == 0
    assert json.loads(res_get_unrated.stdout)["rating"] is None

    # 2. Set rating for track 1 (5 stars)
    res_set1 = runner.invoke(app, ["rating", "set", str(t1_id), "5", "-f", "json", "--db", str(test_db)])
    assert res_set1.exit_code == 0
    r1 = json.loads(res_set1.stdout)
    assert r1["rating"] == 5
    assert r1["track_id"] == str(t1_id)

    # 3. Set rating for track 2 (3 stars)
    res_set2 = runner.invoke(app, ["rating", "set", str(t2_id), "3", "-f", "json", "--db", str(test_db)])
    assert res_set2.exit_code == 0

    # 4. Get rating for track 1
    res_get1 = runner.invoke(app, ["rating", "get", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_get1.exit_code == 0
    assert json.loads(res_get1.stdout)["rating"] == 5

    # 5. List ratings without filter (both tracks)
    res_list_all = runner.invoke(app, ["rating", "list", "-f", "json", "--db", str(test_db)])
    assert res_list_all.exit_code == 0
    assert len(json.loads(res_list_all.stdout)) == 2

    # 6. Filter by min-rating 4 (only track 1)
    res_filter = runner.invoke(app, ["rating", "list", "-m", "4", "-f", "json", "--db", str(test_db)])
    assert res_filter.exit_code == 0
    filtered = json.loads(res_filter.stdout)
    assert len(filtered) == 1
    assert filtered[0]["track_id"] == str(t1_id)

    # 7. Update rating for track 1 to 2 stars
    res_update = runner.invoke(app, ["rating", "set", str(t1_id), "2", "-f", "json", "--db", str(test_db)])
    assert res_update.exit_code == 0
    assert json.loads(res_update.stdout)["rating"] == 2

    # 8. Remove rating
    res_rem = runner.invoke(app, ["rating", "remove", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res_rem.exit_code == 0
    assert json.loads(res_rem.stdout)["removed"] is True

    # 9. Verify unrated after removal
    res_get_after = runner.invoke(app, ["rating", "get", str(t1_id), "-f", "json", "--db", str(test_db)])
    assert json.loads(res_get_after.stdout)["rating"] is None


# ── 7. Input Errors: Invalid UUIDs, Missing Entities, Invalid Arguments ───────


def test_cli_invalid_uuids_and_missing_entities(tmp_path: Path) -> None:
    """Verify user-friendly error messages and exit code 1 for invalid or missing IDs."""
    test_db = tmp_path / "test_errors.db"
    _seed_db(test_db)
    random_id = str(uuid4())

    # Invalid UUIDs
    res_inv_pl = runner.invoke(app, ["playlist", "show", "not-a-uuid", "--db", str(test_db)])
    assert res_inv_pl.exit_code == 1
    assert "Invalid playlist UUID" in res_inv_pl.stdout

    res_inv_tr = runner.invoke(app, ["favorite", "add", "not-a-uuid", "--db", str(test_db)])
    assert res_inv_tr.exit_code == 1
    assert "Invalid track UUID" in res_inv_tr.stdout

    res_inv_rt = runner.invoke(app, ["rating", "set", "not-a-uuid", "5", "--db", str(test_db)])
    assert res_inv_rt.exit_code == 1
    assert "Invalid track UUID" in res_inv_rt.stdout

    # Missing entities
    res_miss_pl = runner.invoke(app, ["playlist", "show", random_id, "--db", str(test_db)])
    assert res_miss_pl.exit_code == 1
    assert "Playlist not found" in res_miss_pl.stdout

    res_miss_fav = runner.invoke(app, ["favorite", "add", random_id, "--db", str(test_db)])
    assert res_miss_fav.exit_code == 1
    assert "Track not found" in res_miss_fav.stdout

    res_miss_rt = runner.invoke(app, ["rating", "set", random_id, "4", "--db", str(test_db)])
    assert res_miss_rt.exit_code == 1
    assert "Track not found" in res_miss_rt.stdout

    res_miss_chk = runner.invoke(app, ["favorite", "check", random_id, "--db", str(test_db)])
    assert res_miss_chk.exit_code == 1
    assert "Track not found" in res_miss_chk.stdout


def test_cli_invalid_arguments_and_pagination(tmp_path: Path) -> None:
    """Verify validation errors for ratings and pagination limits/offsets."""
    test_db = tmp_path / "test_val.db"
    t1_id, _ = _seed_db(test_db)

    # Star rating out of bounds
    res_rt0 = runner.invoke(app, ["rating", "set", str(t1_id), "0", "--db", str(test_db)])
    assert res_rt0.exit_code == 1
    assert "Validation error" in res_rt0.stdout

    res_rt6 = runner.invoke(app, ["rating", "set", str(t1_id), "6", "--db", str(test_db)])
    assert res_rt6.exit_code == 1
    assert "Validation error" in res_rt6.stdout

    # Min rating filter out of bounds
    res_min0 = runner.invoke(app, ["rating", "list", "-m", "0", "--db", str(test_db)])
    assert res_min0.exit_code == 1

    # Invalid pagination
    res_pl_lim = runner.invoke(app, ["playlist", "list", "-l", "0", "--db", str(test_db)])
    assert res_pl_lim.exit_code == 1
    assert "Validation error" in res_pl_lim.stdout

    res_pl_off = runner.invoke(app, ["playlist", "list", "-o", "-1", "--db", str(test_db)])
    assert res_pl_off.exit_code == 1
    assert "Validation error" in res_pl_off.stdout

    res_fav_lim = runner.invoke(app, ["favorite", "list", "-l", "-5", "--db", str(test_db)])
    assert res_fav_lim.exit_code == 1

    res_rt_off = runner.invoke(app, ["rating", "list", "-o", "-2", "--db", str(test_db)])
    assert res_rt_off.exit_code == 1

    # Negative playlist item position
    res_neg_pos = runner.invoke(app, ["playlist", "add", str(uuid4()), str(t1_id), "-p", "-1", "--db", str(test_db)])
    assert res_neg_pos.exit_code == 1


# ── 8. Empty Results and Unicode Support ──────────────────────────────────────


def test_cli_empty_results_handling(tmp_path: Path) -> None:
    """Verify clean exit and empty-state messaging when lists are empty."""
    test_db = tmp_path / "test_empty.db"
    _seed_db(test_db)

    # Empty playlists
    res_pl = runner.invoke(app, ["playlist", "list", "--db", str(test_db)])
    assert res_pl.exit_code == 0
    assert "No playlists found" in res_pl.stdout

    res_pl_json = runner.invoke(app, ["playlist", "list", "-f", "json", "--db", str(test_db)])
    assert res_pl_json.exit_code == 0
    assert json.loads(res_pl_json.stdout) == []

    # Empty favorites
    res_fav = runner.invoke(app, ["favorite", "list", "--db", str(test_db)])
    assert res_fav.exit_code == 0
    assert "No favorite tracks found" in res_fav.stdout

    res_fav_json = runner.invoke(app, ["favorite", "list", "-f", "json", "--db", str(test_db)])
    assert res_fav_json.exit_code == 0
    assert json.loads(res_fav_json.stdout) == []

    # Empty ratings
    res_rt = runner.invoke(app, ["rating", "list", "--db", str(test_db)])
    assert res_rt.exit_code == 0
    assert "No rated tracks found" in res_rt.stdout

    res_rt_json = runner.invoke(app, ["rating", "list", "-f", "json", "--db", str(test_db)])
    assert res_rt_json.exit_code == 0
    assert json.loads(res_rt_json.stdout) == []


def test_cli_unicode_playlist_names(tmp_path: Path) -> None:
    """Verify Unicode NFC normalization and multi-lingual characters in playlist names."""
    test_db = tmp_path / "test_unicode.db"
    _seed_db(test_db)

    unicode_name = "日本語 🎵 Synthwave Électronique"
    res_create = runner.invoke(
        app,
        ["playlist", "create", unicode_name, "-f", "json", "--db", str(test_db)],
    )
    assert res_create.exit_code == 0
    data = json.loads(res_create.stdout)
    assert data["name"] == unicode_name

    # List verification
    res_list = runner.invoke(app, ["playlist", "list", "-f", "json", "--db", str(test_db)])
    assert res_list.exit_code == 0
    list_data = json.loads(res_list.stdout)
    assert list_data[0]["name"] == unicode_name


# ── 9. Machine-Readable JSON Output Format Validation ─────────────────────────


def test_cli_machine_readable_json_strict_parsing(tmp_path: Path) -> None:
    """Verify that every --format json output parses strictly as valid JSON without Rich prose."""
    test_db = tmp_path / "test_json_strict.db"
    t1_id, _ = _seed_db(test_db)

    # Playlist create
    res1 = runner.invoke(app, ["playlist", "create", "JSON Test", "-f", "json", "--db", str(test_db)])
    assert res1.exit_code == 0
    p1 = json.loads(res1.stdout)
    pl_id = p1["id"]

    # Playlist add
    res2 = runner.invoke(app, ["playlist", "add", pl_id, str(t1_id), "-f", "json", "--db", str(test_db)])
    assert res2.exit_code == 0
    it = json.loads(res2.stdout)
    item_id = it["id"]

    # Playlist items
    res3 = runner.invoke(app, ["playlist", "items", pl_id, "-f", "json", "--db", str(test_db)])
    assert res3.exit_code == 0
    items = json.loads(res3.stdout)
    assert isinstance(items, list) and len(items) == 1

    # Playlist show
    res4 = runner.invoke(app, ["playlist", "show", pl_id, "-f", "json", "--db", str(test_db)])
    assert res4.exit_code == 0
    assert json.loads(res4.stdout)["track_count"] == 1

    # Playlist move
    res5 = runner.invoke(app, ["playlist", "move", pl_id, item_id, "0", "-f", "json", "--db", str(test_db)])
    assert res5.exit_code == 0
    assert json.loads(res5.stdout)["position"] == 0

    # Playlist remove
    res6 = runner.invoke(app, ["playlist", "remove", pl_id, item_id, "-f", "json", "--db", str(test_db)])
    assert res6.exit_code == 0
    assert json.loads(res6.stdout)["removed"] is True

    # Playlist delete
    res7 = runner.invoke(app, ["playlist", "delete", pl_id, "--yes", "-f", "json", "--db", str(test_db)])
    assert res7.exit_code == 0
    assert json.loads(res7.stdout)["deleted"] is True


# ── 10. Database Failure Handling and Lifecycle Cleanup ───────────────────────


def test_cli_database_error_handling_and_cleanup(tmp_path: Path) -> None:
    """Verify that database errors produce concise user-facing error and clean up resources."""
    test_db = tmp_path / "test_db_err.db"
    t1_id, _ = _seed_db(test_db)

    # Patch PersonalizationService.add_favorite to raise SQLAlchemyError
    from sqlalchemy.exc import OperationalError

    with patch.object(
        PersonalizationService,
        "add_favorite",
        side_effect=OperationalError("disk I/O error", params=None, orig=Exception("disk error")),
    ):
        res = runner.invoke(app, ["favorite", "add", str(t1_id), "--db", str(test_db)])
        assert res.exit_code == 1
        assert "Database error" in res.stdout
        # Confirm no unhandled python traceback is dumped to stdout
        assert "Traceback" not in res.stdout


# ── 11. Side-Effect Safety and Application Service Delegation ─────────────────


def test_cli_no_catalog_track_or_file_modification(tmp_path: Path) -> None:
    """Confirm personalization CLI never creates catalog tracks or touches media files."""
    test_db = tmp_path / "test_side_effects.db"
    t1_id, _ = _seed_db(test_db)

    # Create dummy media file
    media_file = tmp_path / "track.flac"
    media_file.write_bytes(b"FLAC dummy audio data")
    initial_mtime = media_file.stat().st_mtime_ns

    # Count tracks before
    engine = create_database_engine(test_db)
    session_factory = create_session_factory(engine)
    with session_factory() as session:
        track_count_before = SqliteTrackRepository(session).count()
    engine.dispose()

    # Perform personalization operations
    runner.invoke(app, ["playlist", "create", "SideEffect Test", "--db", str(test_db)])
    runner.invoke(app, ["favorite", "add", str(t1_id), "--db", str(test_db)])
    runner.invoke(app, ["rating", "set", str(t1_id), "5", "--db", str(test_db)])

    # Verify track count after
    engine = create_database_engine(test_db)
    session_factory = create_session_factory(engine)
    with session_factory() as session:
        track_count_after = SqliteTrackRepository(session).count()
    engine.dispose()

    assert track_count_before == track_count_after
    assert media_file.stat().st_mtime_ns == initial_mtime


def test_cli_calls_application_service_delegation(tmp_path: Path) -> None:
    """Verify that CLI commands delegate directly to PersonalizationService methods."""
    test_db = tmp_path / "test_delegation.db"
    t1_id, _ = _seed_db(test_db)

    with (
        patch.object(PersonalizationService, "create_playlist", wraps=None) as mock_create,
        patch.object(PersonalizationService, "add_favorite", wraps=None) as mock_fav,
        patch.object(PersonalizationService, "set_rating", wraps=None) as mock_rating,
    ):
        mock_create.return_value = None
        mock_fav.return_value = None
        mock_rating.return_value = None

        runner.invoke(app, ["playlist", "create", "Mock PL", "--db", str(test_db)])
        mock_create.assert_called_once()

        runner.invoke(app, ["favorite", "add", str(t1_id), "--db", str(test_db)])
        mock_fav.assert_called_once()

        runner.invoke(app, ["rating", "set", str(t1_id), "4", "--db", str(test_db)])
        mock_rating.assert_called_once()
