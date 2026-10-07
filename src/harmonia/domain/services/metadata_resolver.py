"""Metadata resolver domain service.

Performs field-level conflict resolution across providers based on priority
and provenance history.
"""

from __future__ import annotations

from collections import defaultdict
from typing import TYPE_CHECKING, Any

from harmonia.core.types import MetadataPriority, MetadataProviderName
from harmonia.domain.entities.metadata_field import MetadataFieldRecord
from harmonia.domain.services.normalizer import MetadataNormalizer

if TYPE_CHECKING:
    from uuid import UUID

PROVIDER_PRIORITIES: dict[str, int] = {
    MetadataProviderName.USER.value: MetadataPriority.USER,
    MetadataProviderName.MUSICBRAINZ.value: MetadataPriority.MUSICBRAINZ,
    MetadataProviderName.FILE.value: MetadataPriority.FILE,
    MetadataProviderName.PATH.value: MetadataPriority.PATH,
}


class MetadataResolver:
    """Domain service for deterministic field-level metadata conflict resolution."""

    @staticmethod
    def get_default_priority(provider: str) -> int:
        """Return the default priority score for a provider name."""
        return PROVIDER_PRIORITIES.get(provider.lower().strip(), MetadataPriority.FILE)

    @classmethod
    def create_record(
        cls,
        entity_type: str,
        entity_id: UUID,
        field_name: str,
        provider: str,
        raw_value: Any,
        priority: int | None = None,
    ) -> MetadataFieldRecord:
        """Create a new MetadataFieldRecord with normalized value and priority."""
        raw_str = "" if raw_value is None else str(raw_value)
        normalized = MetadataNormalizer.normalize_text(raw_str) or ""
        prio = priority if priority is not None else cls.get_default_priority(provider)

        return MetadataFieldRecord(
            entity_type=entity_type,
            entity_id=entity_id,
            field_name=field_name,
            provider=provider,
            raw_value=raw_str,
            normalized_value=normalized,
            priority=prio,
        )

    @staticmethod
    def resolve_field(records: list[MetadataFieldRecord]) -> MetadataFieldRecord | None:
        """Resolve the winning record for a single field from multiple sources.

        Rules:
        1. Higher priority wins.
        2. Non-empty normalized_value is required to win.
        3. Tiebreak: older creation date (first created) wins.
        4. Mutates is_active: winner is True, all others are False.

        Returns:
            The winning MetadataFieldRecord, or None if no valid candidate exists.
        """
        if not records:
            return None

        # Filter to records with valid non-empty normalized values
        valid_records = [
            r for r in records if r.normalized_value and r.normalized_value.strip()
        ]

        if not valid_records:
            for r in records:
                r.is_active = False
            return None

        # Sort: priority DESC, then created_at ASC (older = more stable)
        # Using negative timestamp for secondary key if sorting reverse
        sorted_candidates = sorted(
            valid_records,
            key=lambda r: (r.priority, -r.created_at.timestamp()),
            reverse=True,
        )

        winner = sorted_candidates[0]

        for r in records:
            r.is_active = (r.id == winner.id)

        return winner

    @classmethod
    def resolve_entity_fields(
        cls, records: list[MetadataFieldRecord]
    ) -> dict[str, MetadataFieldRecord]:
        """Resolve all fields for an entity.

        Returns:
            Dictionary mapping field_name to winning MetadataFieldRecord.
        """
        by_field: dict[str, list[MetadataFieldRecord]] = defaultdict(list)
        for r in records:
            by_field[r.field_name].append(r)

        winners: dict[str, MetadataFieldRecord] = {}
        for field_name, field_records in by_field.items():
            winner = cls.resolve_field(field_records)
            if winner is not None:
                winners[field_name] = winner

        return winners
