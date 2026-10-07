"""Unit tests for DuplicateDetector domain service."""

from uuid import uuid4

from harmonia.core.types import DuplicateConfidence
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.services.duplicate_detector import DuplicateDetector
from harmonia.domain.values.audio_format import AudioFormat


def test_has_variant_suffix() -> None:
    assert DuplicateDetector.has_variant_suffix("Comfortably Numb (Live)") is True
    assert DuplicateDetector.has_variant_suffix("Layla (Acoustic)") is True
    assert DuplicateDetector.has_variant_suffix("Blue Monday (Remix)") is True
    assert DuplicateDetector.has_variant_suffix("Song - Live") is True
    assert DuplicateDetector.has_variant_suffix("Track (Radio Edit)") is True
    assert DuplicateDetector.has_variant_suffix("Track (Clean)") is True
    assert DuplicateDetector.has_variant_suffix("Track (Remastered 2011)") is True
    assert DuplicateDetector.has_variant_suffix("Track (Demo)") is True
    assert DuplicateDetector.has_variant_suffix("Track (Bonus Track)") is True
    assert DuplicateDetector.has_variant_suffix("Comfortably Numb") is False
    assert DuplicateDetector.has_variant_suffix(None) is False


def test_tier_a_same_file_path() -> None:
    track_id = uuid4()
    existing_file = TrackFile(
        track_id=track_id,
        file_path="/music/song.mp3",
        file_size=5000,
        format=AudioFormat.MP3,
        file_hash="hash123",
    )
    match = DuplicateDetector.check_content_hash(
        file_hash="hash123",
        candidate_path="/music/song.mp3",
        existing_files=[existing_file],
    )
    assert match is not None
    assert match.tier == "A"
    assert match.confidence == DuplicateConfidence.CERTAIN
    assert match.existing_track_id == track_id
    assert "already imported" in match.reason


def test_tier_a_different_file_path() -> None:
    track_id = uuid4()
    existing_file = TrackFile(
        track_id=track_id,
        file_path="/music/album1/song.mp3",
        file_size=5000,
        format=AudioFormat.MP3,
        file_hash="hash123",
    )
    match = DuplicateDetector.check_content_hash(
        file_hash="hash123",
        candidate_path="/music/album2/song_copy.mp3",
        existing_files=[existing_file],
    )
    assert match is not None
    assert match.tier == "A"
    assert match.confidence == DuplicateConfidence.CERTAIN
    assert match.existing_track_id == track_id
    assert "physical duplicate" in match.reason


def test_tier_b_probable_match() -> None:
    existing_track = Track(
        title="Time",
        duration_seconds=425.0,
        disc_number=1,
    )
    match = DuplicateDetector.evaluate_track_candidate(
        incoming_title="Time",
        incoming_artist="Pink Floyd",
        incoming_album="The Dark Side of the Moon",
        incoming_duration=426.0,  # 1.0s difference <= 2.0s
        incoming_disc=1,
        existing_track=existing_track,
        existing_artist_name="Pink Floyd",
        existing_album_title="The Dark Side of the Moon",
    )
    assert match.tier == "B"
    assert match.confidence == DuplicateConfidence.PROBABLE
    assert match.existing_track_id == existing_track.id


def test_tier_b_disqualified_by_variant_suffix() -> None:
    existing_track = Track(
        title="Time",
        duration_seconds=425.0,
    )
    match = DuplicateDetector.evaluate_track_candidate(
        incoming_title="Time (Live)",
        incoming_artist="Pink Floyd",
        incoming_album="The Dark Side of the Moon",
        incoming_duration=425.0,
        incoming_disc=1,
        existing_track=existing_track,
        existing_artist_name="Pink Floyd",
        existing_album_title="The Dark Side of the Moon",
    )
    assert match.tier == "C"
    assert match.confidence == DuplicateConfidence.UNCERTAIN
    assert "Variant suffix detected" in match.reason


def test_tier_b_disqualified_by_duration() -> None:
    existing_track = Track(
        title="Time",
        duration_seconds=425.0,
    )
    match = DuplicateDetector.evaluate_track_candidate(
        incoming_title="Time",
        incoming_artist="Pink Floyd",
        incoming_album="The Dark Side of the Moon",
        incoming_duration=429.0,  # 4s difference > 2.0s
        incoming_disc=1,
        existing_track=existing_track,
        existing_artist_name="Pink Floyd",
        existing_album_title="The Dark Side of the Moon",
    )
    assert match.tier == "C"
    assert match.confidence == DuplicateConfidence.UNCERTAIN
    assert "Duration difference" in match.reason


def test_tier_b_disqualified_by_different_disc() -> None:
    existing_track = Track(
        title="In the Flesh",
        disc_number=1,
        duration_seconds=200.0,
    )
    match = DuplicateDetector.evaluate_track_candidate(
        incoming_title="In the Flesh",
        incoming_artist="Pink Floyd",
        incoming_album="The Wall",
        incoming_duration=200.0,
        incoming_disc=2,  # Disc 2 vs Disc 1
        existing_track=existing_track,
        existing_artist_name="Pink Floyd",
        existing_album_title="The Wall",
    )
    assert match.tier == "C"
    assert match.confidence == DuplicateConfidence.UNCERTAIN
    assert "disc" in match.reason.lower()


def test_tier_c_different_artist_or_album() -> None:
    existing_track = Track(
        title="Yesterday",
        duration_seconds=125.0,
    )
    match = DuplicateDetector.evaluate_track_candidate(
        incoming_title="Yesterday",
        incoming_artist="Boyz II Men",  # Different artist
        incoming_album="II",
        incoming_duration=125.0,
        incoming_disc=1,
        existing_track=existing_track,
        existing_artist_name="The Beatles",
        existing_album_title="Help!",
    )
    assert match.tier == "C"
    assert match.confidence == DuplicateConfidence.UNCERTAIN
