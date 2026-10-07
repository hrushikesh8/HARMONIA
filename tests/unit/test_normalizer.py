"""Unit tests for MetadataNormalizer domain service."""

import unicodedata

from harmonia.domain.services.normalizer import MetadataNormalizer


def test_normalize_text_unicode_nfc() -> None:
    # Decomposed "e" + acute accent
    decomposed = "Beyon" + "e\u0301"
    composed = "Beyon\u00e9"
    assert decomposed != composed
    assert MetadataNormalizer.normalize_text(decomposed) == composed
    assert unicodedata.is_normalized("NFC", MetadataNormalizer.normalize_text(decomposed))


def test_normalize_text_whitespace() -> None:
    raw = "   Led    \t  Zeppelin  \n "
    assert MetadataNormalizer.normalize_text(raw) == "Led Zeppelin"


def test_normalize_text_preserves_casing_and_punctuation() -> None:
    raw = "  Guns N' Roses - Live!  "
    assert MetadataNormalizer.normalize_text(raw) == "Guns N' Roses - Live!"
    assert MetadataNormalizer.normalize_text("AC/DC") == "AC/DC"


def test_normalize_text_none_and_empty() -> None:
    assert MetadataNormalizer.normalize_text(None) is None
    assert MetadataNormalizer.normalize_text("") is None
    assert MetadataNormalizer.normalize_text("   ") is None


def test_comparison_key() -> None:
    assert MetadataNormalizer.comparison_key("The   Beatles  ") == "the beatles"
    assert MetadataNormalizer.comparison_key("AC/DC") == "ac/dc"
    assert MetadataNormalizer.comparison_key(None) == ""
    assert MetadataNormalizer.comparison_key("") == ""


def test_sort_key_articles() -> None:
    assert MetadataNormalizer.sort_key("The Beatles") == "beatles, the"
    assert MetadataNormalizer.sort_key("A Tribe Called Quest") == "tribe called quest, a"
    assert MetadataNormalizer.sort_key("An Endless Night") == "endless night, an"


def test_sort_key_no_article() -> None:
    assert MetadataNormalizer.sort_key("Pink Floyd") == "pink floyd"
    assert MetadataNormalizer.sort_key("Then Comes Silence") == "then comes silence"
    assert MetadataNormalizer.sort_key(None) == ""


def test_validate_year() -> None:
    assert MetadataNormalizer.validate_year(1973) == 1973
    assert MetadataNormalizer.validate_year("1999") == 1999
    assert MetadataNormalizer.validate_year(2024) == 2024
    assert MetadataNormalizer.validate_year(999) is None
    assert MetadataNormalizer.validate_year(10000) is None
    assert MetadataNormalizer.validate_year("invalid") is None
    assert MetadataNormalizer.validate_year(None) is None
