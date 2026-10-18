"""Unit tests for SQLite performance configuration and query plans (D5-D)."""

from pathlib import Path
from uuid import uuid4

from sqlalchemy import text

from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


def test_sqlite_pragmas_configured(temp_dir: Path) -> None:
    """Verify that all tuned SQLite pragmas are actively applied to connections."""
    db_file = temp_dir / "pragmas_test.db"
    engine = create_database_engine(db_file)
    init_database(engine)

    try:
        with engine.connect() as conn:
            journal_mode = conn.execute(text("PRAGMA journal_mode")).scalar()
            assert str(journal_mode).lower() == "wal"

            foreign_keys = conn.execute(text("PRAGMA foreign_keys")).scalar()
            assert foreign_keys == 1

            busy_timeout = conn.execute(text("PRAGMA busy_timeout")).scalar()
            assert busy_timeout == 5000

            synchronous = conn.execute(text("PRAGMA synchronous")).scalar()
            assert synchronous == 1  # 1 = NORMAL

            cache_size = conn.execute(text("PRAGMA cache_size")).scalar()
            assert cache_size == -64000  # 64MB

            temp_store = conn.execute(text("PRAGMA temp_store")).scalar()
            assert temp_store == 2  # 2 = MEMORY

            mmap_size = conn.execute(text("PRAGMA mmap_size")).scalar()
            assert mmap_size == 268435456  # 256MB
    finally:
        engine.dispose()


def test_query_plans_use_composite_indexes_without_temp_sort(temp_dir: Path) -> None:
    """Verify that high-frequency queries utilize the new composite and expression indexes."""
    db_file = temp_dir / "plans_test.db"
    engine = create_database_engine(db_file)
    init_database(engine)

    try:
        with engine.connect() as conn:
            # 1. Artist case-insensitive lookup
            plan_artist = conn.execute(
                text("EXPLAIN QUERY PLAN SELECT * FROM artists WHERE lower(name) = :name"),
                {"name": "the beatles"},
            ).fetchall()
            artist_plan_str = " ".join(str(row) for row in plan_artist)
            assert "ix_artists_name_lower" in artist_plan_str
            assert "SCAN artists" not in artist_plan_str

            # 2. Album case-insensitive title and artist lookup
            plan_album_title = conn.execute(
                text("EXPLAIN QUERY PLAN SELECT * FROM albums WHERE lower(title) = :title AND artist_id = :artist_id"),
                {"title": "abbey road", "artist_id": str(uuid4())},
            ).fetchall()
            album_title_plan_str = " ".join(str(row) for row in plan_album_title)
            assert "ix_albums_artist_title_lower" in album_title_plan_str

            # 3. Album ordered listing by artist and year DESC
            plan_album_year = conn.execute(
                text("EXPLAIN QUERY PLAN SELECT * FROM albums WHERE artist_id = :artist_id ORDER BY year DESC"),
                {"artist_id": str(uuid4())},
            ).fetchall()
            album_year_plan_str = " ".join(str(row) for row in plan_album_year)
            assert "ix_albums_artist_year" in album_year_plan_str
            assert "TEMP B-TREE" not in album_year_plan_str

            # 4. Tracks by album ordered by disc and track number
            plan_tracks_album = conn.execute(
                text("EXPLAIN QUERY PLAN SELECT * FROM tracks WHERE album_id = :album_id ORDER BY disc_number, track_number"),
                {"album_id": str(uuid4())},
            ).fetchall()
            tracks_album_plan_str = " ".join(str(row) for row in plan_tracks_album)
            assert "ix_tracks_album_disc_track" in tracks_album_plan_str
            assert "TEMP B-TREE" not in tracks_album_plan_str

            # 5. Tracks by artist ordered by title
            plan_tracks_artist = conn.execute(
                text("EXPLAIN QUERY PLAN SELECT * FROM tracks WHERE artist_id = :artist_id ORDER BY title"),
                {"artist_id": str(uuid4())},
            ).fetchall()
            tracks_artist_plan_str = " ".join(str(row) for row in plan_tracks_artist)
            assert "ix_tracks_artist_title" in tracks_artist_plan_str
            assert "TEMP B-TREE" not in tracks_artist_plan_str

            # 6. TrackFiles ordered by quality score
            plan_files_quality = conn.execute(
                text("EXPLAIN QUERY PLAN SELECT * FROM track_files WHERE track_id = :track_id ORDER BY quality_score DESC"),
                {"track_id": str(uuid4())},
            ).fetchall()
            files_quality_plan_str = " ".join(str(row) for row in plan_files_quality)
            assert "ix_track_files_track_quality" in files_quality_plan_str
            assert "TEMP B-TREE" not in files_quality_plan_str

            # 7. Listening events ordered by timestamp DESC
            plan_events_timestamp = conn.execute(
                text("EXPLAIN QUERY PLAN SELECT * FROM listening_events WHERE track_id = :track_id ORDER BY timestamp_utc DESC"),
                {"track_id": str(uuid4())},
            ).fetchall()
            events_plan_str = " ".join(str(row) for row in plan_events_timestamp)
            assert "ix_listening_events_track_timestamp" in events_plan_str
            assert "TEMP B-TREE" not in events_plan_str

            # 8. Metadata fields ordered by priority DESC and created_at
            plan_meta_priority = conn.execute(
                text("EXPLAIN QUERY PLAN SELECT * FROM metadata_fields WHERE entity_type = :t AND entity_id = :id AND field_name = :f ORDER BY priority DESC, created_at"),
                {"t": "track", "id": str(uuid4()), "f": "title"},
            ).fetchall()
            meta_plan_str = " ".join(str(row) for row in plan_meta_priority)
            assert "ix_metadata_fields_entity_field_priority" in meta_plan_str
            assert "TEMP B-TREE" not in meta_plan_str
    finally:
        engine.dispose()


def test_directory_prefix_filtering_behavior(temp_dir: Path) -> None:
    """Verify that get_files_by_directory_prefix isolates directories correctly and rapidly."""
    db_file = temp_dir / "prefix_test.db"
    engine = create_database_engine(db_file)
    init_database(engine)
    session_factory = create_session_factory(engine)

    try:
        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            track_id = uuid4()
            track = Track(
                id=track_id,
                title="Test Track",
            )
            track_repo.add(track)

            tf1 = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(temp_dir / "music" / "album_a" / "track1.flac"),
                file_size=100,
                format=AudioFormat.FLAC,
            )
            tf2 = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(temp_dir / "music" / "album_b" / "track2.flac"),
                file_size=100,
                format=AudioFormat.FLAC,
            )
            tf_sibling = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(temp_dir / "music" / "album_a_extra" / "track3.flac"),
                file_size=100,
                format=AudioFormat.FLAC,
            )
            track_repo.add_file(tf1)
            track_repo.add_file(tf2)
            track_repo.add_file(tf_sibling)
            session.commit()

            # Query album_a only
            files_a = track_repo.get_files_by_directory_prefix(str(temp_dir / "music" / "album_a"))
            paths_a = [f.file_path for f in files_a]
            assert str(tf1.file_path) in paths_a
            assert str(tf2.file_path) not in paths_a
            assert str(tf_sibling.file_path) not in paths_a

            # Query parent music dir
            files_all = track_repo.get_files_by_directory_prefix(str(temp_dir / "music"))
            paths_all = [f.file_path for f in files_all]
            assert len(paths_all) == 3
    finally:
        engine.dispose()


def test_directory_prefix_unicode_case_fallback(temp_dir: Path) -> None:
    """Verify that non-ASCII Unicode directory prefixes do not suffer from SQLite LIKE false negatives.

    SQLite's built-in LIKE is strictly ASCII case-insensitive. Non-ASCII characters such as 'É' vs 'é'
    must not cause valid files to be omitted when queries have case variations.
    """
    db_file = temp_dir / "unicode_prefix_test.db"
    engine = create_database_engine(db_file)
    init_database(engine)
    session_factory = create_session_factory(engine)

    try:
        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            track_id = uuid4()
            track_repo.add(Track(id=track_id, title="Chanson Track"))

            # 1. Non-ASCII directory with mixed case: Édith_Piaf
            dir_edith = temp_dir / "Music" / "Édith_Piaf"
            file_edith = dir_edith / "la_vie_en_rose.flac"
            tf_edith = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(file_edith),
                file_size=200,
                format=AudioFormat.FLAC,
            )
            track_repo.add_file(tf_edith)

            # 2. Non-ASCII parent and child directories: Música / Björk
            dir_bjork = temp_dir / "Música" / "Björk"
            file_bjork = dir_bjork / "hyperballad.flac"
            tf_bjork = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(file_bjork),
                file_size=200,
                format=AudioFormat.FLAC,
            )
            track_repo.add_file(tf_bjork)

            # 3. Control file in unrelated directory
            dir_other = temp_dir / "Music" / "Other_Artist"
            file_other = dir_other / "track.flac"
            tf_other = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(file_other),
                file_size=200,
                format=AudioFormat.FLAC,
            )
            track_repo.add_file(tf_other)
            session.commit()

            # Query 1: Lowercase query prefix for Édith_Piaf ('édith_piaf')
            # In prior implementation, SQLite's LIKE dropped this because 'é' != 'É' in ASCII LIKE
            query_edith_lower = str(temp_dir / "music" / "édith_piaf")
            results_edith = track_repo.get_files_by_directory_prefix(query_edith_lower)
            assert len(results_edith) == 1
            assert results_edith[0].id == tf_edith.id
            assert results_edith[0].file_path == str(file_edith)

            # Query 2: Exact case query prefix for Édith_Piaf
            query_edith_exact = str(dir_edith)
            results_edith_exact = track_repo.get_files_by_directory_prefix(query_edith_exact)
            assert len(results_edith_exact) == 1
            assert results_edith_exact[0].id == tf_edith.id

            # Query 3: Lowercase query for non-ASCII parent directory ('música')
            query_musica_lower = str(temp_dir / "música")
            results_musica = track_repo.get_files_by_directory_prefix(query_musica_lower)
            assert len(results_musica) == 1
            assert results_musica[0].id == tf_bjork.id
            assert results_musica[0].file_path == str(file_bjork)

            # Query 4: Lowercase query for non-ASCII child directory ('björk')
            query_bjork_lower = str(temp_dir / "Música" / "björk")
            results_bjork = track_repo.get_files_by_directory_prefix(query_bjork_lower)
            assert len(results_bjork) == 1
            assert results_bjork[0].id == tf_bjork.id

            # Query 5: Pure ASCII parent directory ('Music') should match tf_edith and tf_other via fast path
            query_ascii_parent = str(temp_dir / "Music")
            results_ascii = track_repo.get_files_by_directory_prefix(query_ascii_parent)
            assert len(results_ascii) == 2
            ascii_ids = {r.id for r in results_ascii}
            assert tf_edith.id in ascii_ids
            assert tf_other.id in ascii_ids
            assert tf_bjork.id not in ascii_ids
    finally:
        engine.dispose()


def test_directory_prefix_ascii_edge_cases(temp_dir: Path) -> None:
    """Verify ASCII edge cases: drive letters, separators, collisions, wildcards, empty strings."""
    db_file = temp_dir / "ascii_edge_cases.db"
    engine = create_database_engine(db_file)
    init_database(engine)
    session_factory = create_session_factory(engine)

    try:
        with session_factory() as session:
            track_repo = SqliteTrackRepository(session)
            track_id = uuid4()
            track_repo.add(Track(id=track_id, title="Edge Case Track"))

            base_dir = temp_dir / "Library"
            dir_artist = base_dir / "Artist"
            dir_artist_2 = base_dir / "Artist 2"
            dir_wildcard_under = base_dir / "Top_100"
            dir_wildcard_hyphen = base_dir / "Top-100"
            dir_wildcard_pct = base_dir / "100%_Pure"

            tf_artist = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(dir_artist / "album" / "track1.flac"),
                file_size=100,
                format=AudioFormat.FLAC,
            )
            tf_artist_2 = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(dir_artist_2 / "album" / "track2.flac"),
                file_size=100,
                format=AudioFormat.FLAC,
            )
            tf_under = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(dir_wildcard_under / "track3.flac"),
                file_size=100,
                format=AudioFormat.FLAC,
            )
            tf_hyphen = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(dir_wildcard_hyphen / "track4.flac"),
                file_size=100,
                format=AudioFormat.FLAC,
            )
            tf_pct = TrackFile(
                id=uuid4(),
                track_id=track_id,
                file_path=str(dir_wildcard_pct / "track5.flac"),
                file_size=100,
                format=AudioFormat.FLAC,
            )

            for tf in [tf_artist, tf_artist_2, tf_under, tf_hyphen, tf_pct]:
                track_repo.add_file(tf)
            session.commit()

            # 1. Collision test: 'Artist' must NOT match 'Artist 2'
            res_artist = track_repo.get_files_by_directory_prefix(str(dir_artist))
            assert len(res_artist) == 1
            assert res_artist[0].id == tf_artist.id

            # 2. Wildcard '_' in folder name: 'Top_100' must NOT return 'Top-100'
            res_under = track_repo.get_files_by_directory_prefix(str(dir_wildcard_under))
            assert len(res_under) == 1
            assert res_under[0].id == tf_under.id

            # 3. Wildcard '%' in folder name: '100%_Pure'
            res_pct = track_repo.get_files_by_directory_prefix(str(dir_wildcard_pct))
            assert len(res_pct) == 1
            assert res_pct[0].id == tf_pct.id

            # 4. Mixed forward and backward separators
            slash_path = str(dir_artist).replace("\\", "/")
            res_slash = track_repo.get_files_by_directory_prefix(slash_path)
            assert len(res_slash) == 1
            assert res_slash[0].id == tf_artist.id

            # 5. Case variations in ASCII path (lowercase query)
            lower_path = str(dir_artist).lower()
            res_lower = track_repo.get_files_by_directory_prefix(lower_path)
            assert len(res_lower) == 1
            assert res_lower[0].id == tf_artist.id

            # 6. Empty and whitespace prefixes return empty list
            assert track_repo.get_files_by_directory_prefix("") == []
            assert track_repo.get_files_by_directory_prefix("   ") == []
            assert track_repo.get_files_by_directory_prefix("\t\n") == []
    finally:
        engine.dispose()

