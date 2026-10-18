"""Integration tests for spectral cutoff and suspicious transcode detection."""

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

import pytest
from sqlalchemy.orm import Session
from typer.testing import CliRunner

from harmonia.application.audio_analysis_service import AudioAnalysisService
from harmonia.cli.app import app
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.ports.audio_analyzer import TranscodeClassification
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.audio.ffmpeg_analyzer import FFmpegAudioAnalyzer
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

runner = CliRunner()


def test_real_broadband_flac_fixture_genuine() -> None:
    """Genuine broadband lossless audio fixture exhibits NO_SUSPICIOUS_CUTOFF."""
    fixture_path = Path("RESOURCES/beets-master/test/rsrc/whitenoise.flac")
    if not fixture_path.exists():
        pytest.skip(f"Fixture {fixture_path} not found.")

    analyzer = FFmpegAudioAnalyzer()
    res = analyzer.check_transcode(fixture_path)
    assert res.classification == TranscodeClassification.NO_SUSPICIOUS_CUTOFF
    assert res.is_lossless_container is True
    assert res.cutoff_frequency_hz is None
    assert res.cutoff_steepness_db_per_khz is not None
    # Broadband white noise roll-off is shallow
    assert res.cutoff_steepness_db_per_khz < 2.0


def test_real_fixtures_low_sample_rate_and_silence() -> None:
    """Genuine fixtures with low sample rate or digital silence safely yield INSUFFICIENT_EVIDENCE."""
    strawberry_path = Path("RESOURCES/strawberry-master/tests/data/audio/strawberry.flac")
    empty_path = Path("RESOURCES/beets-master/test/rsrc/empty.flac")

    analyzer = FFmpegAudioAnalyzer()

    if strawberry_path.exists():
        res_sr = analyzer.check_transcode(strawberry_path)
        assert res_sr.classification == TranscodeClassification.INSUFFICIENT_EVIDENCE
        assert "nyquist" in res_sr.details.lower() or "sample rate" in res_sr.details.lower()

    if empty_path.exists():
        res_empty = analyzer.check_transcode(empty_path)
        assert res_empty.classification == TranscodeClassification.INSUFFICIENT_EVIDENCE
        assert "negligible" in res_empty.details.lower() or "silence" in res_empty.details.lower()


def test_real_lossy_transcodes_detected(tmp_path: Path) -> None:
    """Real lossy-to-lossless transcodes (MP3->FLAC, AAC->FLAC) are flagged as SUSPICIOUS_CUTOFF."""
    source_flac = Path("RESOURCES/beets-master/test/rsrc/whitenoise.flac")
    if not source_flac.exists():
        pytest.skip("Source fixture whitenoise.flac not found.")

    analyzer = FFmpegAudioAnalyzer()

    # 1. 128k MP3 -> FLAC transcode
    mp3_128 = tmp_path / "transcode_128k.mp3"
    flac_128 = tmp_path / "fake_lossless_128k.flac"

    cmd1 = ["ffmpeg", "-y", "-i", str(source_flac), "-c:a", "libmp3lame", "-b:a", "128k", str(mp3_128)]
    cmd2 = ["ffmpeg", "-y", "-i", str(mp3_128), "-c:a", "flac", str(flac_128)]

    if subprocess.run(cmd1, capture_output=True).returncode == 0 and subprocess.run(cmd2, capture_output=True).returncode == 0:
        res128 = analyzer.check_transcode(flac_128)
        assert res128.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
        assert res128.cutoff_frequency_hz is not None
        assert res128.cutoff_steepness_db_per_khz is not None
        assert res128.cutoff_steepness_db_per_khz >= 14.0

    # 2. 192k MP3 -> FLAC transcode
    mp3_192 = tmp_path / "transcode_192k.mp3"
    flac_192 = tmp_path / "fake_lossless_192k.flac"

    cmd3 = ["ffmpeg", "-y", "-i", str(source_flac), "-c:a", "libmp3lame", "-b:a", "192k", str(mp3_192)]
    cmd4 = ["ffmpeg", "-y", "-i", str(mp3_192), "-c:a", "flac", str(flac_192)]

    if subprocess.run(cmd3, capture_output=True).returncode == 0 and subprocess.run(cmd4, capture_output=True).returncode == 0:
        res192 = analyzer.check_transcode(flac_192)
        assert res192.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
        assert res192.cutoff_frequency_hz is not None
        assert res192.cutoff_steepness_db_per_khz is not None
        assert res192.cutoff_steepness_db_per_khz >= 14.0


def test_service_check_file_transcode(session: Session, tmp_path: Path) -> None:
    """AudioAnalysisService successfully coordinates check_file_transcode without DB mutations."""
    source_flac = Path("RESOURCES/beets-master/test/rsrc/whitenoise.flac")
    if not source_flac.exists():
        pytest.skip("Fixture whitenoise.flac not found.")

    # Copy fixture to temp directory for testing
    test_track_file = tmp_path / "service_test.flac"
    shutil.copy2(source_flac, test_track_file)

    track_repo = SqliteTrackRepository(session)
    track = Track(title="White Noise Test")
    track_repo.add(track)

    tf = TrackFile(
        track_id=track.id,
        file_path=str(test_track_file.resolve()),
        format=AudioFormat.FLAC,
        file_size=test_track_file.stat().st_size,
    )
    track_repo.add_file(tf)
    session.commit()

    # Create service with session_factory
    from sqlalchemy.orm import sessionmaker

    session_factory = sessionmaker(bind=session.get_bind(), expire_on_commit=False)
    service = AudioAnalysisService(session_factory=session_factory, analyzer=FFmpegAudioAnalyzer())

    result = service.check_file_transcode(tf.id)
    assert result.classification == TranscodeClassification.NO_SUSPICIOUS_CUTOFF
    assert result.is_lossless_container is True
    assert "smoothly" in result.details or "Nyquist" in result.details


def test_cli_check_transcode_option(tmp_path: Path) -> None:
    """Harmonia CLI --check-transcode executes and prints a formatted spectral cutoff table."""
    source_flac = Path("RESOURCES/beets-master/test/rsrc/whitenoise.flac")
    if not source_flac.exists():
        pytest.skip("Fixture whitenoise.flac not found.")

    test_track_file = tmp_path / "cli_test.flac"
    shutil.copy2(source_flac, test_track_file)

    db_path = tmp_path / "test_cli.db"

    # Import track file into DB
    from harmonia.core.database import create_database_engine, create_session_factory, init_database

    engine = create_database_engine(db_path)
    init_database(engine)
    session_factory = create_session_factory(engine)
    with session_factory() as sess:
        repo = SqliteTrackRepository(sess)
        track = Track(title="CLI Spectral Test")
        repo.add(track)
        tf = TrackFile(
            track_id=track.id,
            file_path=str(test_track_file.resolve()),
            format=AudioFormat.FLAC,
            file_size=test_track_file.stat().st_size,
        )
        repo.add_file(tf)
        sess.commit()
        tf_id = tf.id
    engine.dispose()

    res = runner.invoke(
        app,
        [
            "analyze",
            str(tf_id),
            "--check-transcode",
            "--db",
            str(db_path),
        ],
    )
    assert res.exit_code == 0
    assert "Spectral Cutoff / Transcode Analysis" in res.stdout
    assert "NO_SUSPIC" in res.stdout
    assert "Nyquist" in res.stdout


def test_stereo_mp3_bitrate_dependent_cutoffs(tmp_path: Path) -> None:
    """Verify standard 44.1 kHz stereo MP3 transcodes reflect bitrate-dependent cutoff frequencies."""
    stereo_flac = tmp_path / "stereo_ref.flac"
    cmd_gen = [
        "ffmpeg",
        "-y",
        "-f",
        "lavfi",
        "-i",
        "anoisesrc=d=2.0:c=white:r=44100:a=0.5",
        "-ac",
        "2",
        "-c:a",
        "flac",
        str(stereo_flac),
    ]
    if subprocess.run(cmd_gen, capture_output=True).returncode != 0:
        pytest.skip("FFmpeg not available for stereo synth")

    analyzer = FFmpegAudioAnalyzer()

    # 1. 96k stereo -> lowpass around 15.5 kHz
    mp3_96 = tmp_path / "stereo_96k.mp3"
    flac_96 = tmp_path / "transcode_96k.flac"
    c1 = ["ffmpeg", "-y", "-i", str(stereo_flac), "-c:a", "libmp3lame", "-b:a", "96k", str(mp3_96)]
    c2 = ["ffmpeg", "-y", "-i", str(mp3_96), "-c:a", "flac", str(flac_96)]
    if subprocess.run(c1, capture_output=True).returncode == 0 and subprocess.run(c2, capture_output=True).returncode == 0:
        res_96 = analyzer.check_transcode(flac_96)
        assert res_96.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
        assert res_96.cutoff_frequency_hz is not None
        assert 15000 <= res_96.cutoff_frequency_hz <= 16500

    # 2. 128k stereo -> lowpass around 16.5-17.0 kHz
    mp3_128 = tmp_path / "stereo_128k.mp3"
    flac_128 = tmp_path / "transcode_128k.flac"
    c3 = ["ffmpeg", "-y", "-i", str(stereo_flac), "-c:a", "libmp3lame", "-b:a", "128k", str(mp3_128)]
    c4 = ["ffmpeg", "-y", "-i", str(mp3_128), "-c:a", "flac", str(flac_128)]
    if subprocess.run(c3, capture_output=True).returncode == 0 and subprocess.run(c4, capture_output=True).returncode == 0:
        res_128 = analyzer.check_transcode(flac_128)
        assert res_128.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
        assert res_128.cutoff_frequency_hz is not None
        assert 16000 <= res_128.cutoff_frequency_hz <= 17500

    # 3. 192k stereo -> lowpass around 18.5-19.5 kHz
    mp3_192 = tmp_path / "stereo_192k.mp3"
    flac_192 = tmp_path / "transcode_192k.flac"
    c5 = ["ffmpeg", "-y", "-i", str(stereo_flac), "-c:a", "libmp3lame", "-b:a", "192k", str(mp3_192)]
    c6 = ["ffmpeg", "-y", "-i", str(mp3_192), "-c:a", "flac", str(flac_192)]
    if subprocess.run(c5, capture_output=True).returncode == 0 and subprocess.run(c6, capture_output=True).returncode == 0:
        res_192 = analyzer.check_transcode(flac_192)
        assert res_192.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
        assert res_192.cutoff_frequency_hz is not None
        assert 18500 <= res_192.cutoff_frequency_hz <= 19500

    # 4. 320k stereo -> lowpass around 20.0-20.5 kHz
    mp3_320 = tmp_path / "stereo_320k.mp3"
    flac_320 = tmp_path / "transcode_320k.flac"
    c7 = ["ffmpeg", "-y", "-i", str(stereo_flac), "-c:a", "libmp3lame", "-b:a", "320k", str(mp3_320)]
    c8 = ["ffmpeg", "-y", "-i", str(mp3_320), "-c:a", "flac", str(flac_320)]
    if subprocess.run(c7, capture_output=True).returncode == 0 and subprocess.run(c8, capture_output=True).returncode == 0:
        res_320 = analyzer.check_transcode(flac_320)
        assert res_320.classification == TranscodeClassification.SUSPICIOUS_CUTOFF
        assert res_320.cutoff_frequency_hz is not None
        assert 20000 <= res_320.cutoff_frequency_hz <= 20500


