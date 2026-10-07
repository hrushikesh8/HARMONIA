"""Conservative metadata normalizer domain service.

Preserves text integrity while providing deterministic comparison and sort keys.
Zero external I/O dependencies (pure domain service).
"""

from __future__ import annotations

import re
import unicodedata


class MetadataNormalizer:
    """Pure domain service for normalizing metadata text and computing keys."""

    @staticmethod
    def normalize_text(text: str | None) -> str | None:
        """Conservative text normalization.

        - Unicode NFC normalization
        - Strips leading and trailing whitespace
        - Collapses internal multiple whitespace to single space
        - Preserves casing, punctuation, and diacritics
        """
        if text is None:
            return None
        # Normalize to Unicode NFC
        normalized = unicodedata.normalize("NFC", str(text))
        # Collapse multiple whitespace characters into single space
        normalized = re.sub(r"\s+", " ", normalized).strip()
        return normalized if normalized else None

    @classmethod
    def comparison_key(cls, text: str | None) -> str:
        """Lossy key used ONLY for internal entity comparisons and dedup matching.

        Never stored as a display value.
        """
        if text is None:
            return ""
        norm = cls.normalize_text(text)
        if not norm:
            return ""
        return norm.lower()

    @classmethod
    def sort_key(cls, text: str | None) -> str:
        """Sort key for alphabetical ordering, moving leading articles to the end.

        Handles 'the ', 'a ', and 'an '.
        """
        key = cls.comparison_key(text)
        if not key:
            return ""
        for article in ("the ", "a ", "an "):
            if key.startswith(article):
                key = key[len(article) :].strip() + ", " + article.strip()
                break
        return key

    @staticmethod
    def validate_year(year: int | str | None) -> int | None:
        """Validate and return 4-digit year, or None if invalid."""
        if year is None:
            return None
        try:
            val = int(year)
            if 1000 <= val <= 9999:
                return val
            return None
        except (ValueError, TypeError):
            return None
