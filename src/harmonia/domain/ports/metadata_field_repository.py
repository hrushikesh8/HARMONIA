"""Port definition for MetadataFieldRepository."""

from __future__ import annotations

from typing import TYPE_CHECKING, Protocol

if TYPE_CHECKING:
    from uuid import UUID

    from harmonia.domain.entities.metadata_field import MetadataFieldRecord


class MetadataFieldRepository(Protocol):
    """Repository port for MetadataFieldRecord entities."""

    def add(self, record: MetadataFieldRecord) -> None:
        """Add a single metadata field record."""
        ...

    def add_all(self, records: list[MetadataFieldRecord]) -> None:
        """Add multiple metadata field records."""
        ...

    def get_by_entity(
        self, entity_type: str, entity_id: UUID
    ) -> list[MetadataFieldRecord]:
        """Retrieve all metadata field records for an entity."""
        ...

    def get_by_entity_field(
        self, entity_type: str, entity_id: UUID, field_name: str
    ) -> list[MetadataFieldRecord]:
        """Retrieve records for a specific field on an entity."""
        ...

    def get_active(
        self, entity_type: str, entity_id: UUID
    ) -> list[MetadataFieldRecord]:
        """Retrieve all currently active winning records for an entity."""
        ...

    def set_active(self, record_id: UUID, is_active: bool) -> None:
        """Update active status of a record."""
        ...
