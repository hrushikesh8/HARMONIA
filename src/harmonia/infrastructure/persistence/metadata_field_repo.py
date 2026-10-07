"""SQLite repository implementation for metadata fields provenance."""

from __future__ import annotations

from typing import TYPE_CHECKING

from sqlalchemy import select

from harmonia.domain.entities.metadata_field import MetadataFieldRecord
from harmonia.infrastructure.persistence.models import MetadataFieldModel

if TYPE_CHECKING:
    from uuid import UUID

    from sqlalchemy.orm import Session


class SqliteMetadataFieldRepository:
    """SQLite implementation of the MetadataFieldRepository port."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def add(self, record: MetadataFieldRecord) -> None:
        """Add a single metadata field record."""
        model = _record_to_model(record)
        self._session.add(model)
        self._session.flush()

    def add_all(self, records: list[MetadataFieldRecord]) -> None:
        """Add multiple metadata field records."""
        for record in records:
            self._session.add(_record_to_model(record))
        self._session.flush()

    def get_by_entity(
        self, entity_type: str, entity_id: UUID
    ) -> list[MetadataFieldRecord]:
        """Retrieve all metadata field records for an entity."""
        stmt = select(MetadataFieldModel).where(
            MetadataFieldModel.entity_type == entity_type.lower().strip(),
            MetadataFieldModel.entity_id == entity_id,
        ).order_by(MetadataFieldModel.created_at)
        models = self._session.scalars(stmt).all()
        return [_model_to_record(m) for m in models]

    def get_by_entity_field(
        self, entity_type: str, entity_id: UUID, field_name: str
    ) -> list[MetadataFieldRecord]:
        """Retrieve records for a specific field on an entity."""
        stmt = select(MetadataFieldModel).where(
            MetadataFieldModel.entity_type == entity_type.lower().strip(),
            MetadataFieldModel.entity_id == entity_id,
            MetadataFieldModel.field_name == field_name.lower().strip(),
        ).order_by(MetadataFieldModel.priority.desc(), MetadataFieldModel.created_at)
        models = self._session.scalars(stmt).all()
        return [_model_to_record(m) for m in models]

    def get_active(
        self, entity_type: str, entity_id: UUID
    ) -> list[MetadataFieldRecord]:
        """Retrieve all currently active winning records for an entity."""
        stmt = select(MetadataFieldModel).where(
            MetadataFieldModel.entity_type == entity_type.lower().strip(),
            MetadataFieldModel.entity_id == entity_id,
            MetadataFieldModel.is_active == True,  # noqa: E712
        )
        models = self._session.scalars(stmt).all()
        return [_model_to_record(m) for m in models]

    def set_active(self, record_id: UUID, is_active: bool) -> None:
        """Update active status of a record."""
        model = self._session.get(MetadataFieldModel, record_id)
        if model is not None:
            model.is_active = is_active
            self._session.flush()


def _record_to_model(record: MetadataFieldRecord) -> MetadataFieldModel:
    return MetadataFieldModel(
        id=record.id,
        entity_type=record.entity_type,
        entity_id=record.entity_id,
        field_name=record.field_name,
        provider=record.provider,
        raw_value=record.raw_value,
        normalized_value=record.normalized_value,
        is_active=record.is_active,
        priority=record.priority,
        created_at=record.created_at,
    )


def _model_to_record(model: MetadataFieldModel) -> MetadataFieldRecord:
    return MetadataFieldRecord(
        id=model.id,
        entity_type=model.entity_type,
        entity_id=model.entity_id,
        field_name=model.field_name,
        provider=model.provider,
        raw_value=model.raw_value,
        normalized_value=model.normalized_value,
        is_active=model.is_active,
        priority=model.priority,
        created_at=model.created_at,
    )
