"""Integration tests for Phase 2 import pipeline."""

from pathlib import Path
from unittest.mock import patch

from sqlalchemy.orm import Session

from harmonia.application.import_service import ImportService
from harmonia.infrastructure.audio.mutagen_reader import ExtractedMetadata
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)
from harmonia.infrastructure.persistence.metadata_field_repo import (
    SqliteMetadataFieldRepository,
)
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


def test_import_pipeline_fast_skip_and_mtime_update(session: Session, temp_dir: Path) -> None:
    track_repo = SqliteTrackRepository(session)
    search_repo = SqliteSearchRepository(session)
    field_repo = SqliteMetadataFieldRepository(session)

    music_dir = temp_dir / "music"
    music_dir.mkdir()
    file_path = music_dir / "track1.mp3"
    file_path.write_bytes(b"mock audio bytes for track 1")

    service = ImportService(
        session=session,
        supported_formats=[".mp3"],
        extract_artwork=False,
    )

    mock_meta = ExtractedMetadata(
        title="First Run",
        artist="Artist A",
        album="Album A",
        duration_seconds=180.0,
        bitrate=320,
    )

    with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
        res1 = service.import_directory(music_dir)
        assert res1.imported == 1
        assert res1.skipped == 0

        # Fast skip: re-importing without changes skips file
        res2 = service.import_directory(music_dir)
        assert res2.imported == 0
        assert res2.skipped == 1

        # Check provenance recorded
        tracks = track_repo.get_all()
        assert len(tracks) == 1
        track = tracks[0]
        fields = field_repo.get_by_entity("track", track.id)
        assert len(fields) > 0

        # Check searchable
        search_results = search_repo.search("First Run")
        assert len(search_results) == 1
        assert search_results[0] == track.id


def test_import_pipeline_moved_and_copied_files(session: Session, temp_dir: Path) -> None:
    track_repo = SqliteTrackRepository(session)
    music_dir = temp_dir / "music"
    music_dir.mkdir()

    file1 = music_dir / "song1.mp3"
    file1.write_bytes(b"audio stream 12345")

    service = ImportService(
        session=session,
        supported_formats=[".mp3"],
        extract_artwork=False,
    )

    mock_meta = ExtractedMetadata(
        title="Unique Song",
        artist="Artist B",
        album="Album B",
        duration_seconds=200.0,
    )

    with patch("harmonia.application.import_service.extract_metadata", return_value=mock_meta):
        # 1. Import original
        res = service.import_directory(music_dir)
        assert res.imported == 1

        tf1 = track_repo.get_file_by_path(str(file1))
        assert tf1 is not None
        original_track_id = tf1.track_id

        # 2. Copied file (Tier A2): copy file to song2.mp3
        file2 = music_dir / "song2.mp3"
        file2.write_bytes(file1.read_bytes())

        res_copy = service.import_directory(music_dir)
        assert res_copy.imported == 1  # Attached as TrackFile on same Track

        # Track count remains 1, files count is 2
        tracks = track_repo.get_all()
        assert len(tracks) == 1
        files = track_repo.get_files(original_track_id)
        assert len(files) == 2

        # 3. Moved file: rename song1 to song3
        file3 = music_dir / "song3.mp3"
        file1.rename(file3)

        res_move = service.import_directory(music_dir)
        assert res_move.imported == 1  # Detected move

        tf_moved = track_repo.get_file_by_path(str(file3))
        assert tf_moved is not None
        assert tf_moved.track_id == original_track_id


def test_import_pipeline_tier_b_probable_duplicate(session: Session, temp_dir: Path) -> None:
    track_repo = SqliteTrackRepository(session)
    dup_repo = SqliteDuplicateCandidateRepository(session)

    music_dir = temp_dir / "music"
    music_dir.mkdir()

    file1 = music_dir / "track_v1.mp3"
    file1.write_bytes(b"version 1 bytes")

    service = ImportService(
        session=session,
        supported_formats=[".mp3"],
        extract_artwork=False,
    )

    meta1 = ExtractedMetadata(
        title="Echoes",
        artist="Pink Floyd",
        album="Meddle",
        duration_seconds=1400.0,
        disc_number=1,
    )
    with patch("harmonia.application.import_service.extract_metadata", return_value=meta1):
        service.import_file(file1)

    assert track_repo.count() == 1
    assert dup_repo.count_unresolved() == 0

    # Import second file with different content bytes, but matching metadata (Tier B)
    file2 = music_dir / "track_v2.mp3"
    file2.write_bytes(b"version 2 different encoded bytes")

    meta2 = ExtractedMetadata(
        title="Echoes",
        artist="Pink Floyd",
        album="Meddle",
        duration_seconds=1401.0,  # 1s difference <= 2.0s
        disc_number=1,
    )
    with patch("harmonia.application.import_service.extract_metadata", return_value=meta2):
        service.import_file(file2)

    # In Harmonia Phase 2, Tier B creates a SEPARATE track (zero false merges!)
    # and records a DuplicateCandidate for review.
    assert track_repo.count() == 2
    assert dup_repo.count_unresolved() == 1

    candidates = dup_repo.list_unresolved()
    assert len(candidates) == 1
    assert candidates[0].confidence == "probable"
    assert "duration within 2s" in candidates[0].reason


def test_import_pipeline_savepoint_isolation(session: Session, temp_dir: Path) -> None:
    music_dir = temp_dir / "music"
    music_dir.mkdir()

    file_ok1 = music_dir / "good1.mp3"
    file_ok1.write_bytes(b"good audio 1")

    file_bad = music_dir / "bad.mp3"
    file_bad.write_bytes(b"corrupt audio")

    file_ok2 = music_dir / "good2.mp3"
    file_ok2.write_bytes(b"good audio 2")

    service = ImportService(
        session=session,
        supported_formats=[".mp3"],
        extract_artwork=False,
    )

    def side_effect(path: Path) -> ExtractedMetadata:
        if "bad" in path.name:
            raise ValueError("Corrupted tag headers")
        return ExtractedMetadata(title=path.stem, artist="Artist", album="Album", duration_seconds=100.0)

    with patch("harmonia.application.import_service.extract_metadata", side_effect=side_effect):
        result = service.import_directory(music_dir)
        assert result.imported == 2
        assert result.failed == 1
        assert len(result.errors) == 1
        assert "bad.mp3" in result.errors[0].file_path
