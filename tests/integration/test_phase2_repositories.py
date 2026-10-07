"""Integration tests for Phase 2 repositories."""

from uuid import uuid4

from sqlalchemy.orm import Session

from harmonia.domain.entities.duplicate_candidate import DuplicateCandidate
from harmonia.domain.entities.metadata_field import MetadataFieldRecord
from harmonia.domain.entities.track import Track
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)
from harmonia.infrastructure.persistence.metadata_field_repo import (
    SqliteMetadataFieldRepository,
)
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository


def test_metadata_field_repository_crud(session: Session) -> None:
    repo = SqliteMetadataFieldRepository(session)
    entity_id = uuid4()

    rec1 = MetadataFieldRecord(
        entity_type="track",
        entity_id=entity_id,
        field_name="title",
        provider="file",
        raw_value="Time",
        normalized_value="Time",
        priority=30,
        is_active=False,
    )
    rec2 = MetadataFieldRecord(
        entity_type="track",
        entity_id=entity_id,
        field_name="title",
        provider="user",
        raw_value="Time (Remaster)",
        normalized_value="Time (Remaster)",
        priority=100,
        is_active=True,
    )

    repo.add(rec1)
    repo.add(rec2)

    by_entity = repo.get_by_entity("track", entity_id)
    assert len(by_entity) == 2

    by_field = repo.get_by_entity_field("track", entity_id, "title")
    assert len(by_field) == 2
    assert by_field[0].priority == 100  # sorted by priority desc

    active = repo.get_active("track", entity_id)
    assert len(active) == 1
    assert active[0].id == rec2.id

    repo.set_active(rec2.id, False)
    repo.set_active(rec1.id, True)
    active_updated = repo.get_active("track", entity_id)
    assert len(active_updated) == 1
    assert active_updated[0].id == rec1.id


def test_duplicate_candidate_repository_crud(session: Session) -> None:
    track_repo = SqliteTrackRepository(session)
    track_a = Track(title="Song A")
    track_b = Track(title="Song B")
    track_repo.add(track_a)
    track_repo.add(track_b)

    repo = SqliteDuplicateCandidateRepository(session)
    candidate = DuplicateCandidate(
        track_id_a=track_a.id,
        track_id_b=track_b.id,
        confidence="probable",
        reason="Metadata match with duration <= 2s",
    )
    repo.add(candidate)

    assert repo.count_unresolved() == 1
    unresolved = repo.list_unresolved()
    assert len(unresolved) == 1
    assert unresolved[0].id == candidate.id

    fetched = repo.get_by_id(candidate.id)
    assert fetched is not None
    assert fetched.track_id_a == track_a.id

    candidate.mark_resolved("merged")
    repo.update(candidate)

    assert repo.count_unresolved() == 0
    assert len(repo.list_unresolved()) == 0

    fetched_after = repo.get_by_id(candidate.id)
    assert fetched_after is not None
    assert fetched_after.resolved is True
    assert fetched_after.resolution == "merged"
