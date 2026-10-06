"""Harmonia exception hierarchy.

All Harmonia-specific exceptions inherit from HarmoniaError so callers
can catch broad or narrow as appropriate.
"""

from __future__ import annotations

from uuid import UUID


class HarmoniaError(Exception):
    """Base exception for all Harmonia errors."""


# ── Entity errors ─────────────────────────────────────────────────────────────


class EntityNotFoundError(HarmoniaError):
    """Requested entity does not exist."""

    def __init__(self, entity_type: str, entity_id: UUID | str) -> None:
        self.entity_type = entity_type
        self.entity_id = entity_id
        super().__init__(f"{entity_type} not found: {entity_id}")


class DuplicateEntityError(HarmoniaError):
    """Entity already exists (unique constraint violation)."""

    def __init__(self, entity_type: str, detail: str = "") -> None:
        self.entity_type = entity_type
        msg = f"Duplicate {entity_type}"
        if detail:
            msg += f": {detail}"
        super().__init__(msg)


# ── Import / file errors ─────────────────────────────────────────────────────


class FileImportError(HarmoniaError):
    """Failed to import a music file."""

    def __init__(self, file_path: str, reason: str) -> None:
        self.file_path = file_path
        self.reason = reason
        super().__init__(f"Import failed for '{file_path}': {reason}")


class UnsupportedFormatError(HarmoniaError):
    """File format is not supported."""

    def __init__(self, file_path: str, extension: str) -> None:
        self.file_path = file_path
        self.extension = extension
        super().__init__(f"Unsupported format '{extension}': {file_path}")


# ── Metadata errors ──────────────────────────────────────────────────────────


class MetadataError(HarmoniaError):
    """Failed to read or resolve metadata."""

    def __init__(self, detail: str) -> None:
        super().__init__(f"Metadata error: {detail}")


class MetadataExtractionError(MetadataError):
    """Failed to extract metadata from a file."""

    def __init__(self, file_path: str, reason: str) -> None:
        self.file_path = file_path
        super().__init__(f"Cannot extract metadata from '{file_path}': {reason}")


# ── Audio analysis errors ────────────────────────────────────────────────────


class AudioAnalysisError(HarmoniaError):
    """Failed to analyze audio properties."""

    def __init__(self, file_path: str, reason: str) -> None:
        self.file_path = file_path
        super().__init__(f"Audio analysis failed for '{file_path}': {reason}")


# ── Configuration errors ─────────────────────────────────────────────────────


class ConfigurationError(HarmoniaError):
    """Invalid or missing configuration."""


# ── Validation errors ────────────────────────────────────────────────────────


class ValidationError(HarmoniaError):
    """Domain validation failure."""

    def __init__(self, field: str, reason: str) -> None:
        self.field = field
        self.reason = reason
        super().__init__(f"Validation error on '{field}': {reason}")
