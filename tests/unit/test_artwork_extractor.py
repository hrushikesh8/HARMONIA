"""Unit tests for ArtworkExtractor."""

import hashlib
from pathlib import Path
from unittest.mock import patch

from harmonia.infrastructure.audio.artwork_extractor import (
    ArtworkExtractor,
    detect_image_extension,
)


def test_detect_image_extension() -> None:
    jpeg_bytes = b"\xff\xd8\xff\xe0\x00\x10JFIF"
    png_bytes = b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR"
    gif_bytes = b"GIF89a\x01\x00\x01\x00"
    webp_bytes = b"RIFF\x1a\x00\x00\x00WEBPVP8 "

    assert detect_image_extension(jpeg_bytes) == ".jpg"
    assert detect_image_extension(png_bytes) == ".png"
    assert detect_image_extension(gif_bytes) == ".gif"
    assert detect_image_extension(webp_bytes) == ".webp"
    assert detect_image_extension(b"randomdata", fallback_mime="image/png") == ".png"
    assert detect_image_extension(b"randomdata", fallback_mime="image/jpeg") == ".jpg"


def test_artwork_extractor_saves_and_deduplicates(temp_dir: Path) -> None:
    artwork_dir = temp_dir / "artwork"
    extractor = ArtworkExtractor(artwork_dir=artwork_dir, data_dir=temp_dir)

    fake_jpeg = b"\xff\xd8\xff\xe0" + b"fake-image-content-123456"
    expected_hash = hashlib.sha256(fake_jpeg).hexdigest()

    with patch(
        "harmonia.infrastructure.audio.artwork_extractor.extract_raw_image",
        return_value=(fake_jpeg, "image/jpeg"),
    ):
        result1 = extractor.extract_from_file(Path("fake_song.flac"))
        assert result1 is not None
        assert result1.art_hash == expected_hash
        assert result1.full_path.exists()
        assert result1.full_path.read_bytes() == fake_jpeg
        assert result1.relative_path == f"artwork/{expected_hash[:2]}/{expected_hash}.jpg"

        # Second extraction with same content should reuse the file
        result2 = extractor.extract_from_file(Path("another_song.flac"))
        assert result2 is not None
        assert result2.art_hash == expected_hash
        assert result2.full_path == result1.full_path


def test_artwork_extractor_no_image(temp_dir: Path) -> None:
    extractor = ArtworkExtractor(artwork_dir=temp_dir / "artwork", data_dir=temp_dir)
    with patch(
        "harmonia.infrastructure.audio.artwork_extractor.extract_raw_image",
        return_value=None,
    ):
        result = extractor.extract_from_file(Path("no_art.mp3"))
        assert result is None
