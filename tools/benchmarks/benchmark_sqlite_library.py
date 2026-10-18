"""Deterministic large-library benchmark suite for Harmonia SQLite performance (D5-D).

Measures operations on representative synthetic music libraries:
- Lookup by ID and file path
- Library listing and pagination
- Artist and album resolution lookups
- Ordered tracks-by-album and tracks-by-artist queries
- Track duplicate hash lookups
- Bulk ingestion throughput
- Watcher directory-prefix queries (both ASCII and non-ASCII paths)
- Full-text search (FTS5) queries

Usage:
    uv run python tools/benchmarks/benchmark_sqlite_library.py --tracks 5000 --repeat 20
"""

from __future__ import annotations

import argparse
import platform
import random
import shutil
import sqlite3
import statistics
import sys
import tempfile
import time
from datetime import UTC, datetime
from pathlib import Path
from uuid import UUID

import sqlalchemy
from sqlalchemy import text

from harmonia.core.database import create_database_engine, create_session_factory, init_database
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.listening_event_repo import SqliteListeningEventRepository
from harmonia.infrastructure.persistence.models import (
    AlbumModel,
    ArtistModel,
    ListeningEventModel,
    MetadataFieldModel,
    TrackFileModel,
    TrackModel,
)
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


def populate_benchmark_data(
    engine: sqlalchemy.Engine,
    num_tracks: int = 10000,
    seed: int = 42,
) -> tuple[list[dict], list[dict], list[dict], list[dict]]:
    """Seed synthetic library data directly into database using a deterministic generator."""
    rng = random.Random(seed)
    num_artists = max(50, num_tracks // 20)
    num_albums = max(100, num_tracks // 10)

    print(
        f"--> Populating deterministic dataset (seed={seed}): "
        f"{num_tracks} tracks, {num_albums} albums, {num_artists} artists...",
        flush=True,
    )
    t0 = time.perf_counter()

    artists = []
    for i in range(num_artists):
        a_id = UUID(int=rng.getrandbits(128))
        name = f"Artist {i:04d}"
        if i == 5:
            # Deterministic non-ASCII artist for Unicode verification
            name = "Édith Piaf"
        artists.append({
            "id": a_id,
            "name": name,
            "sort_name": name,
            "mbid": f"mbid-art-{i:06d}",
            "created_at": datetime.now(UTC),
            "updated_at": datetime.now(UTC),
        })

    albums = []
    for i in range(num_albums):
        al_id = UUID(int=rng.getrandbits(128))
        art = artists[i % num_artists]
        title = f"Album {i:04d} Title"
        if i == 5:
            title = "La Vie En Rose"
        albums.append({
            "id": al_id,
            "title": title,
            "artist_id": art["id"],
            "year": 1970 + (i % 55),
            "total_tracks": 10,
            "total_discs": 1,
            "mbid": f"mbid-alb-{i:06d}",
            "art_path": None,
            "art_hash": None,
            "created_at": datetime.now(UTC),
            "updated_at": datetime.now(UTC),
        })

    tracks = []
    track_files = []
    listening_events = []
    metadata_fields = []
    fts_rows = []

    genres = ["Rock", "Classical", "Jazz", "Electronic", "Metal", "Folk", "Chanson"]

    for i in range(num_tracks):
        tr_id = UUID(int=rng.getrandbits(128))
        album = albums[i % num_albums]
        artist_id = album["artist_id"]
        track_num = (i % 10) + 1
        genre = genres[i % len(genres)]
        title = f"Track {i:05d} Standard Title"

        tracks.append({
            "id": tr_id,
            "title": title,
            "artist_id": artist_id,
            "album_id": album["id"],
            "track_number": track_num,
            "disc_number": 1,
            "duration_seconds": 180.0 + (i % 60),
            "year": album["year"],
            "genre": genre,
            "mbid": f"mbid-trk-{i:06d}",
            "lyrics": None,
            "created_at": datetime.now(UTC),
            "updated_at": datetime.now(UTC),
        })

        file_id = UUID(int=rng.getrandbits(128))
        dir_idx = i // 100
        if i < 20:
            # Deterministic non-ASCII folder for Unicode prefix benchmarking
            file_path = f"C:/Music/Édith_Piaf/Album_{album['id']}/Track_{track_num:02d}.flac"
        else:
            file_path = f"C:/Music/Directory_{dir_idx:03d}/Artist_{artist_id}/Album_{album['id']}/Track_{track_num:02d}.flac"

        file_hash = f"{i:064x}"
        track_files.append({
            "id": file_id,
            "track_id": tr_id,
            "file_path": file_path,
            "file_hash": file_hash,
            "file_size": 25000000 + (i * 100),
            "format": "flac",
            "codec": "flac",
            "bitrate": 900,
            "sample_rate": 44100,
            "bit_depth": 16,
            "channels": 2,
            "duration_seconds": 180.0,
            "is_lossless": True,
            "quality_score": 800 + (i % 200),
            "file_mtime": 1700000000.0 + i,
            "channel_layout": "stereo",
            "encoder": "reference libFLAC",
            "added_at": datetime.now(UTC),
            "verified_at": datetime.now(UTC),
        })

        # Telemetry listening events (1 per track)
        listening_events.append({
            "id": UUID(int=rng.getrandbits(128)),
            "track_id": tr_id,
            "track_file_id": file_id,
            "timestamp_utc": datetime.now(UTC),
            "duration_played_seconds": 120.0,
            "completed": True,
            "playback_source": "library",
        })

        # Metadata provenance field
        metadata_fields.append({
            "id": UUID(int=rng.getrandbits(128)),
            "entity_type": "track",
            "entity_id": tr_id,
            "field_name": "title",
            "provider": "mutagen",
            "raw_value": title,
            "normalized_value": title.lower(),
            "is_active": True,
            "priority": 50,
            "created_at": datetime.now(UTC),
        })

        fts_rows.append({
            "track_id": str(tr_id),
            "title": title,
            "artist_name": f"Artist {i % num_artists:04d}",
            "album_title": album["title"],
            "genre": genre,
        })

    with engine.begin() as conn:
        conn.execute(ArtistModel.__table__.insert(), artists)
        conn.execute(AlbumModel.__table__.insert(), albums)
        conn.execute(TrackModel.__table__.insert(), tracks)
        conn.execute(TrackFileModel.__table__.insert(), track_files)
        conn.execute(ListeningEventModel.__table__.insert(), listening_events)
        conn.execute(MetadataFieldModel.__table__.insert(), metadata_fields)
        conn.execute(
            text(
                "INSERT INTO tracks_fts(track_id, title, artist_name, album_title, genre) "
                "VALUES (:track_id, :title, :artist_name, :album_title, :genre)"
            ),
            fts_rows,
        )

    t_elapsed = time.perf_counter() - t0
    rate = num_tracks / t_elapsed if t_elapsed > 0 else 0
    print(f"--> Seeding completed in {t_elapsed:.2f}s ({rate:.0f} tracks/sec)\n", flush=True)
    return artists, albums, tracks, track_files


def run_benchmark(db_path: Path, num_tracks: int = 5000, repetitions: int = 20, seed: int = 42) -> dict:
    """Execute the benchmark suite against a configured database engine."""
    print("=" * 70)
    print("HARMONIA SQLITE PERFORMANCE BENCHMARK SUITE (D5-D)")
    print("=" * 70)
    print(f"Python:       {platform.python_version()} ({sys.platform})")
    print(f"SQLite:       {sqlite3.sqlite_version}")
    print(f"SQLAlchemy:   {sqlalchemy.__version__}")
    print(f"Database:     {db_path}")
    print(f"Track Count:  {num_tracks}")
    print(f"Repetitions:  {repetitions}")
    print("Warm-up:      1 unmeasured iteration per operation (Warm Cache)")
    print("=" * 70)

    engine = create_database_engine(db_path)
    init_database(engine)

    try:
        artists, albums, tracks, track_files = populate_benchmark_data(
            engine, num_tracks=num_tracks, seed=seed
        )

        session_factory = create_session_factory(engine)
        results: dict[str, dict[str, float]] = {}

        def bench_op(name: str, func: object, reps: int | None = None) -> None:
            r = reps or repetitions
            # 1 warm-up iteration
            func()  # type: ignore[operator]

            timings: list[float] = []
            for _ in range(r):
                t0 = time.perf_counter()
                func()  # type: ignore[operator]
                t1 = time.perf_counter()
                timings.append((t1 - t0) * 1000.0)

            mean_ms = statistics.mean(timings)
            p95_ms = statistics.quantiles(timings, n=20)[18] if len(timings) >= 20 else max(timings)
            min_ms = min(timings)
            results[name] = {"mean_ms": mean_ms, "p95_ms": p95_ms, "min_ms": min_ms}
            print(f"  {name:<46}: {mean_ms:7.3f} ms (p95: {p95_ms:7.3f} ms, min: {min_ms:7.3f} ms)", flush=True)

        print("--- Running Operations (Warm Cache) ---", flush=True)

        sample_track_ids = [t["id"] for t in tracks[:20]]
        sample_album_ids = [a["id"] for a in albums[:20]]
        sample_artist_ids = [a["id"] for a in artists[:20]]
        sample_paths = [tf["file_path"] for tf in track_files[:20]]
        sample_hashes = [tf["file_hash"] for tf in track_files[:20]]
        sample_artist_names = [a["name"] for a in artists[:20]]
        sample_album_titles = [(a["title"], a["artist_id"]) for a in albums[:20]]

        # 1. Track lookup by ID (batch 20)
        def op_track_get_by_id() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                for tid in sample_track_ids:
                    repo.get_by_id(tid)

        bench_op("1. Track lookup by ID (batch 20)", op_track_get_by_id)

        # 2. TrackFile lookup by path (batch 20)
        def op_file_get_by_path() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                for p in sample_paths:
                    repo.get_file_by_path(p)

        bench_op("2. TrackFile lookup by path (batch 20)", op_file_get_by_path)

        # 3. TrackFile lookup by hash (batch 20)
        def op_file_get_by_hash() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                for h in sample_hashes:
                    repo.get_file_by_hash(h)

        bench_op("3. TrackFile lookup by hash (batch 20)", op_file_get_by_hash)

        # 4. Track listing (offset=0, limit=100)
        def op_track_list_p0() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                repo.get_all(limit=100, offset=0)

        bench_op("4. Track listing (offset=0, limit=100)", op_track_list_p0)

        # 5. Track listing (deep offset, limit=100)
        deep_offset = max(0, num_tracks - 200)

        def op_track_list_deep() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                repo.get_all(limit=100, offset=deep_offset)

        bench_op(f"5. Track listing (offset={deep_offset}, limit=100)", op_track_list_deep)

        # 6. Artist resolution by name (batch 20)
        def op_artist_find_by_name() -> None:
            with session_factory() as s:
                repo = SqliteArtistRepository(s)
                for name in sample_artist_names:
                    repo.find_by_name(name)

        bench_op("6. Artist resolution by name (batch 20)", op_artist_find_by_name)

        # 7. Album resolution by title/artist (batch 20)
        def op_album_find_by_title_and_artist() -> None:
            with session_factory() as s:
                repo = SqliteAlbumRepository(s)
                for title, art_id in sample_album_titles:
                    repo.find_by_title_and_artist(title, art_id)

        bench_op("7. Album resolution by title/artist (batch 20)", op_album_find_by_title_and_artist)

        # 8. Tracks by album (ordered by disc/track, batch 20)
        def op_tracks_by_album() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                for aid in sample_album_ids:
                    repo.get_by_album(aid)

        bench_op("8. Tracks by album (ordered, batch 20)", op_tracks_by_album)

        # 9. Tracks by artist (ordered by title, batch 20)
        def op_tracks_by_artist() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                for art_id in sample_artist_ids:
                    repo.get_by_artist(art_id)

        bench_op("9. Tracks by artist (ordered, batch 20)", op_tracks_by_artist)

        # 10. Albums by artist (ordered by year DESC, batch 20)
        def op_albums_by_artist() -> None:
            with session_factory() as s:
                repo = SqliteAlbumRepository(s)
                for art_id in sample_artist_ids:
                    repo.get_by_artist(art_id)

        bench_op("10. Albums by artist (ordered, batch 20)", op_albums_by_artist)

        # 11. Files by track (ordered by quality DESC, batch 20)
        def op_files_by_track() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                for tid in sample_track_ids:
                    repo.get_files(tid)

        bench_op("11. Files by track (ordered, batch 20)", op_files_by_track)

        # 12. Listening events by track (ordered by timestamp DESC, batch 20)
        def op_events_by_track() -> None:
            with session_factory() as s:
                repo = SqliteListeningEventRepository(s)
                for tid in sample_track_ids:
                    repo.list_events(track_id=tid)

        bench_op("12. Events by track (ordered, batch 20)", op_events_by_track)

        # 13. Watcher files by directory prefix (ASCII fast-path)
        def op_files_by_prefix_ascii() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                repo.get_files_by_directory_prefix("C:/Music/Directory_005")

        bench_op("13. Watcher prefix query (ASCII fast-path)", op_files_by_prefix_ascii, min(repetitions, 10))

        # 14. Watcher files by directory prefix (Unicode fallback path)
        def op_files_by_prefix_unicode() -> None:
            with session_factory() as s:
                repo = SqliteTrackRepository(s)
                repo.get_files_by_directory_prefix("C:/Music/édith_piaf")

        bench_op("14. Watcher prefix query (Unicode fallback)", op_files_by_prefix_unicode, min(repetitions, 10))

        # 15. Full-text search (FTS5)
        def op_fts_search() -> None:
            with session_factory() as s:
                repo = SqliteSearchRepository(s)
                repo.search("Standard Title", limit=50)
                repo.search("Artist 001*", limit=50)
                repo.search("Rock", limit=50)

        bench_op("15. Full-text search (3 queries)", op_fts_search)

    finally:
        engine.dispose()

    print("\nBenchmark completed successfully.")
    return results


def main() -> None:
    parser = argparse.ArgumentParser(description="Harmonia SQLite Large-Library Benchmark Suite")
    parser.add_argument("--tracks", type=int, default=5000, help="Number of tracks in synthetic library (default: 5000)")
    parser.add_argument("--repeat", type=int, default=20, help="Repetition count for measured operations (default: 20)")
    parser.add_argument("--seed", type=int, default=42, help="Deterministic random seed (default: 42)")
    parser.add_argument("--db-path", type=str, default=None, help="Custom database file path (default: temporary file)")
    args = parser.parse_args()

    if args.db_path:
        db_path = Path(args.db_path)
        run_benchmark(db_path, num_tracks=args.tracks, repetitions=args.repeat, seed=args.seed)
    else:
        temp_dir = tempfile.mkdtemp(prefix="harmonia_bench_")
        try:
            db_path = Path(temp_dir) / "benchmark.db"
            run_benchmark(db_path, num_tracks=args.tracks, repetitions=args.repeat, seed=args.seed)
        finally:
            shutil.rmtree(temp_dir, ignore_errors=True)


if __name__ == "__main__":
    main()
