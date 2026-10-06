"""Tag domain entity — flexible key-value metadata attached to tracks."""

from __future__ import annotations

import unicodedata
from dataclasses import dataclass, field
from datetime import UTC, datetime
from uuid import UUID, uuid4

from harmonia.core.types import TagSource


@dataclass
class Tag:
    """A key-value tag attached to a Track.

    Tags are EAV-style (Entity-Attribute-Value) to support arbitrary
    metadata.  The ``source`` field distinguishes where the tag came from:
    - FILE: Extracted from embedded file metadata
    - USER: Manually applied by the user
    - PROVIDER: Fetched from an external metadata provider

    Common tag names: "genre", "mood", "style", "theme", "era", etc.
    """

    track_id: UUID
    name: str  # Tag key (e.g., "genre")
    value: str  # Tag value (e.g., "Rock")
    source: TagSource = TagSource.FILE
    id: UUID = field(default_factory=uuid4)
    created_at: datetime = field(default_factory=lambda: datetime.now(UTC))

    def __post_init__(self) -> None:
        self.name = unicodedata.normalize("NFC", self.name).strip().lower()
        self.value = unicodedata.normalize("NFC", self.value).strip()

    def __str__(self) -> str:
        return f"{self.name}={self.value} [{self.source.value}]"
