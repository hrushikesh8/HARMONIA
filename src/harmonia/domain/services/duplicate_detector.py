"""Tiered duplicate detection domain service.

Classifies incoming audio files into:
- Tier A (CERTAIN): Exact SHA-256 match.
- Tier B (PROBABLE): Matching artist, title, album, and duration <= 2s without variant suffixes.
- Tier C (UNCERTAIN): Distinct track.

Guarantees ZERO false merges by ensuring Tier B never automatically merges tracks.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import TYPE_CHECKING

from harmonia.core.types import DuplicateConfidence
from harmonia.domain.services.normalizer import MetadataNormalizer

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.track import Track
    from harmonia.domain.entities.track_file import TrackFile

VARIANT_PATTERNS = [
    re.compile(r"\(live\b", re.IGNORECASE),
    re.compile(r"\(acoustic\b", re.IGNORECASE),
    re.compile(r"\(remix\b", re.IGNORECASE),
    re.compile(r"\(radio edit\b", re.IGNORECASE),
    re.compile(r"\(clean\b", re.IGNORECASE),
    re.compile(r"\(explicit\b", re.IGNORECASE),
    re.compile(r"\(instrumental\b", re.IGNORECASE),
    re.compile(r"\(demo\b", re.IGNORECASE),
    re.compile(r"\(remaster", re.IGNORECASE),
    re.compile(r"\(deluxe\b", re.IGNORECASE),
    re.compile(r"\(bonus track\b", re.IGNORECASE),
    re.compile(r"\(extended\b", re.IGNORECASE),
    re.compile(r"\(single version\b", re.IGNORECASE),
    re.compile(r"\(album version\b", re.IGNORECASE),
    re.compile(r"\(original mix\b", re.IGNORECASE),
    re.compile(r"-\s*live\b", re.IGNORECASE),
    re.compile(r"-\s*acoustic\b", re.IGNORECASE),
    re.compile(r"-\s*remix\b", re.IGNORECASE),
]


@dataclass
class DuplicateMatch:
    """Result of duplicate evaluation."""

    tier: str  # "A", "B", or "C"
    confidence: DuplicateConfidence
    existing_track_id: UUID | None = None
    existing_file_path: str | None = None
    reason: str = ""


class DuplicateDetector:
    """Domain service for tiered duplicate detection."""

    @staticmethod
    def has_variant_suffix(title: str | None) -> bool:
        """Check if a track title contains a known variant pattern."""
        if not title:
            return False
        clean = title.strip()
        return any(pattern.search(clean) for pattern in VARIANT_PATTERNS)

    @classmethod
    def check_content_hash(
        cls,
        file_hash: str | None,
        candidate_path: str,
        existing_files: list[TrackFile],
    ) -> DuplicateMatch | None:
        """Evaluate Tier A (CERTAIN) via SHA-256 content hash."""
        if not file_hash:
            return None

        for existing in existing_files:
            if existing.file_hash == file_hash:
                if existing.file_path == candidate_path:
                    return DuplicateMatch(
                        tier="A",
                        confidence=DuplicateConfidence.CERTAIN,
                        existing_track_id=existing.track_id,
                        existing_file_path=existing.file_path,
                        reason="Identical SHA-256 hash at identical file path (already imported)",
                    )
                else:
                    return DuplicateMatch(
                        tier="A",
                        confidence=DuplicateConfidence.CERTAIN,
                        existing_track_id=existing.track_id,
                        existing_file_path=existing.file_path,
                        reason="Identical SHA-256 hash at different file path (physical duplicate)",
                    )
        return None

    @classmethod
    def evaluate_track_candidate(
        cls,
        incoming_title: str | None,
        incoming_artist: str | None,
        incoming_album: str | None,
        incoming_duration: float | None,
        incoming_disc: int | None,
        existing_track: Track,
        existing_artist_name: str | None = None,
        existing_album_title: str | None = None,
    ) -> DuplicateMatch:
        """Evaluate if an incoming track is a probable duplicate (Tier B) of an existing track.

        Disqualifiers (force Tier C):
        - Variant suffix in either title
        - Duration difference > 2.0s
        - Different disc number (when disc numbers are present)
        - Different artist, album, or title
        """
        # 1. Variant suffix disqualifier
        if cls.has_variant_suffix(incoming_title) or cls.has_variant_suffix(existing_track.title):
            return DuplicateMatch(
                tier="C",
                confidence=DuplicateConfidence.UNCERTAIN,
                reason="Variant suffix detected in track title",
            )

        # 2. Key matching
        title_key_in = MetadataNormalizer.comparison_key(incoming_title)
        title_key_ex = MetadataNormalizer.comparison_key(existing_track.title)
        if not title_key_in or title_key_in != title_key_ex:
            return DuplicateMatch(
                tier="C",
                confidence=DuplicateConfidence.UNCERTAIN,
                reason="Title does not match",
            )

        artist_key_in = MetadataNormalizer.comparison_key(incoming_artist)
        artist_key_ex = MetadataNormalizer.comparison_key(existing_artist_name)
        if artist_key_in != artist_key_ex:
            return DuplicateMatch(
                tier="C",
                confidence=DuplicateConfidence.UNCERTAIN,
                reason="Artist does not match",
            )

        album_key_in = MetadataNormalizer.comparison_key(incoming_album)
        album_key_ex = MetadataNormalizer.comparison_key(existing_album_title)
        if album_key_in != album_key_ex:
            return DuplicateMatch(
                tier="C",
                confidence=DuplicateConfidence.UNCERTAIN,
                reason="Album does not match",
            )

        # 3. Disc number check (if both provided and different -> Tier C)
        if (
            incoming_disc is not None
            and existing_track.disc_number is not None
            and incoming_disc != existing_track.disc_number
        ):
            return DuplicateMatch(
                tier="C",
                confidence=DuplicateConfidence.UNCERTAIN,
                reason="Different disc numbers on album",
            )

        # 4. Duration check (within 2s)
        if incoming_duration is not None and existing_track.duration_seconds is not None:
            diff = abs(incoming_duration - existing_track.duration_seconds)
            if diff > 2.0:
                return DuplicateMatch(
                    tier="C",
                    confidence=DuplicateConfidence.UNCERTAIN,
                    reason=f"Duration difference ({diff:.1f}s) exceeds 2.0s threshold",
                )

        # All criteria met and no disqualifiers -> Tier B (PROBABLE)
        return DuplicateMatch(
            tier="B",
            confidence=DuplicateConfidence.PROBABLE,
            existing_track_id=existing_track.id,
            reason="Normalized artist, album, and title match with duration within 2s",
        )
