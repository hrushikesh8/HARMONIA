"""Catalog import application service for Harmonia.

Validates catalog bundles against schema, version, and integrity rules, enforces
referential consistency, and applies records within an atomic database transaction.
"""

from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING, Any, Literal
from uuid import UUID

from pydantic import ValidationError as PydanticValidationError
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError as SqlAlchemyIntegrityError

from harmonia.application.catalog_dto import (
    SUPPORTED_CATALOG_SCHEMA_MAJOR_VERSIONS,
    CatalogBundle,
    CatalogImportResult,
    compute_data_checksum,
)
from harmonia.core.exceptions import (
    CatalogConflictError,
    CatalogIntegrityError,
    CatalogValidationError,
    CatalogVersionError,
)
from harmonia.core.logging import get_logger
from harmonia.infrastructure.audio.mutagen_reader import compute_file_hash
from harmonia.infrastructure.persistence.models import (
    AlbumModel,
    ArtistModel,
    ListeningEventModel,
    MetadataFieldModel,
    TagModel,
    TrackFileModel,
    TrackModel,
)
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

logger = get_logger("harmonia.catalog.import")

ConflictPolicy = Literal["skip", "replace", "fail"]


def sanitize_unlinked_filename(raw_path: str | None) -> str:
    """Sanitize filename to prevent directory traversal or invalid path characters."""
    if not raw_path:
        return "audio"
    normalized = raw_path.replace("\\", "/").rstrip("/")
    base = normalized.split("/")[-1].strip()
    if not base or base in (".", ".."):
        return "audio"
    clean = "".join(c for c in base if c not in '<>:"/\\|?*')
    return clean.strip() or "audio"


class CatalogImportService:
    """Imports validated CatalogBundle documents into the Harmonia library."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def load_bundle_from_file(self, file_path: Path) -> dict[str, Any]:
        """Read and parse raw JSON from a file with syntax error handling."""
        if not file_path.exists():
            raise FileNotFoundError(f"Catalog file not found: {file_path}")

        try:
            raw_text = file_path.read_text(encoding="utf-8")
            return json.loads(raw_text)
        except json.JSONDecodeError as exc:
            raise CatalogValidationError(f"Malformed JSON in catalog file '{file_path}': {exc}") from exc

    def validate_bundle(self, raw_bundle: dict[str, Any]) -> CatalogBundle:
        """Validate structure, schema version, integrity checksum, and entity references."""
        # 1. Required top-level keys
        required_keys = {"manifest", "data", "checksum"}
        missing_keys = required_keys - set(raw_bundle.keys())
        if missing_keys:
            raise CatalogValidationError(
                f"Invalid catalog bundle envelope. Missing required keys: {sorted(missing_keys)}"
            )

        manifest_data = raw_bundle.get("manifest")
        if not isinstance(manifest_data, dict) or "schema_version" not in manifest_data:
            raise CatalogValidationError("Bundle manifest is missing or lacks 'schema_version'.")

        schema_version = str(manifest_data["schema_version"])
        major_version = schema_version.split(".")[0]
        if major_version not in SUPPORTED_CATALOG_SCHEMA_MAJOR_VERSIONS:
            raise CatalogVersionError(
                f"Unsupported catalog schema version '{schema_version}'. "
                f"Supported major versions: {sorted(SUPPORTED_CATALOG_SCHEMA_MAJOR_VERSIONS)}"
            )

        # 2. Integrity Checksum Verification
        provided_checksum = raw_bundle.get("checksum")
        if not provided_checksum:
            raise CatalogIntegrityError("Bundle is missing required integrity 'checksum'.")

        computed_checksum = compute_data_checksum(raw_bundle["data"])
        if computed_checksum.lower() != str(provided_checksum).lower():
            raise CatalogIntegrityError(
                f"Catalog integrity check failed. Expected checksum '{provided_checksum}', "
                f"but computed '{computed_checksum}'."
            )

        # 3. Pydantic structural validation
        try:
            bundle = CatalogBundle.model_validate(raw_bundle)
        except PydanticValidationError as exc:
            raise CatalogValidationError(f"Catalog bundle structure validation failed: {exc}") from exc

        # 4. Intra-bundle duplicate validation (pre-flight)
        self._check_intra_bundle_duplicates(bundle)

        # 5. Referential Integrity Validation
        self._validate_referential_integrity(bundle)

        return bundle

    def _check_intra_bundle_duplicates(self, bundle: CatalogBundle) -> None:
        """Ensure no duplicate primary IDs or duplicate composite keys exist within the bundle payload."""
        collections: list[tuple[list[Any], str]] = [
            (bundle.data.artists, "artists"),
            (bundle.data.albums, "albums"),
            (bundle.data.tracks, "tracks"),
            (bundle.data.track_files, "track_files"),
            (bundle.data.tags, "tags"),
            (bundle.data.metadata_fields, "metadata_fields"),
            (bundle.data.listening_events, "listening_events"),
        ]
        for items, name in collections:
            seen_ids: set[UUID] = set()
            for item in items:
                if item.id in seen_ids:
                    raise CatalogValidationError(
                        f"Duplicate ID '{item.id}' found in '{name}' collection within bundle payload."
                    )
                seen_ids.add(item.id)

        # Check duplicate composite keys for tags: (track_id, name, value, source)
        seen_tag_keys: set[tuple[UUID, str, str, str]] = set()
        for tag in bundle.data.tags:
            tag_key = (
                tag.track_id,
                tag.name.lower().strip(),
                tag.value.lower().strip(),
                tag.source.lower().strip(),
            )
            if tag_key in seen_tag_keys:
                raise CatalogValidationError(
                    f"Duplicate tag definition in bundle payload: track_id='{tag.track_id}', "
                    f"name='{tag.name}', value='{tag.value}', source='{tag.source}'"
                )
            seen_tag_keys.add(tag_key)

        # Check duplicate composite keys for metadata fields: (entity_type, entity_id, field_name, provider)
        seen_meta_keys: set[tuple[str, UUID, str, str]] = set()
        for mf in bundle.data.metadata_fields:
            meta_key = (
                mf.entity_type.lower().strip(),
                mf.entity_id,
                mf.field_name.lower().strip(),
                mf.provider.lower().strip(),
            )
            if meta_key in seen_meta_keys:
                raise CatalogValidationError(
                    f"Duplicate metadata field record in bundle payload: entity_type='{mf.entity_type}', "
                    f"entity_id='{mf.entity_id}', field_name='{mf.field_name}', provider='{mf.provider}'"
                )
            seen_meta_keys.add(meta_key)

        # Check duplicate MusicBrainz IDs within bundle
        seen_artist_mbids: dict[str, UUID] = {}
        for a in bundle.data.artists:
            if a.mbid:
                if a.mbid in seen_artist_mbids:
                    raise CatalogValidationError(
                        f"Duplicate MusicBrainz ID '{a.mbid}' found across multiple artists in bundle: "
                        f"'{seen_artist_mbids[a.mbid]}' and '{a.id}'"
                    )
                seen_artist_mbids[a.mbid] = a.id

        seen_album_mbids: dict[str, UUID] = {}
        for al in bundle.data.albums:
            if al.mbid:
                if al.mbid in seen_album_mbids:
                    raise CatalogValidationError(
                        f"Duplicate MusicBrainz ID '{al.mbid}' found across multiple albums in bundle: "
                        f"'{seen_album_mbids[al.mbid]}' and '{al.id}'"
                    )
                seen_album_mbids[al.mbid] = al.id

        seen_track_mbids: dict[str, UUID] = {}
        for t in bundle.data.tracks:
            if t.mbid:
                if t.mbid in seen_track_mbids:
                    raise CatalogValidationError(
                        f"Duplicate MusicBrainz ID '{t.mbid}' found across multiple tracks in bundle: "
                        f"'{seen_track_mbids[t.mbid]}' and '{t.id}'"
                    )
                seen_track_mbids[t.mbid] = t.id

    def _validate_referential_integrity(self, bundle: CatalogBundle) -> None:
        """Ensure all foreign key references point to entities in the bundle or database."""
        existing_artist_ids = set(self._session.scalars(select(ArtistModel.id)).all())
        existing_album_ids = set(self._session.scalars(select(AlbumModel.id)).all())
        existing_track_ids = set(self._session.scalars(select(TrackModel.id)).all())

        all_artist_ids = existing_artist_ids | {a.id for a in bundle.data.artists}
        all_album_ids = existing_album_ids | {al.id for al in bundle.data.albums}
        all_track_ids = existing_track_ids | {t.id for t in bundle.data.tracks}

        for album in bundle.data.albums:
            if album.artist_id is not None and album.artist_id not in all_artist_ids:
                raise CatalogValidationError(
                    f"Album '{album.title}' ({album.id}) references non-existent artist_id {album.artist_id}"
                )

        for track in bundle.data.tracks:
            if track.artist_id is not None and track.artist_id not in all_artist_ids:
                raise CatalogValidationError(
                    f"Track '{track.title}' ({track.id}) references non-existent artist_id {track.artist_id}"
                )
            if track.album_id is not None and track.album_id not in all_album_ids:
                raise CatalogValidationError(
                    f"Track '{track.title}' ({track.id}) references non-existent album_id {track.album_id}"
                )

        for tag in bundle.data.tags:
            if tag.track_id not in all_track_ids:
                raise CatalogValidationError(
                    f"Tag '{tag.name}' ({tag.id}) references non-existent track_id {tag.track_id}"
                )

        for le in bundle.data.listening_events:
            if le.track_id not in all_track_ids:
                raise CatalogValidationError(
                    f"Listening event ({le.id}) references non-existent track_id {le.track_id}"
                )

        for tf in bundle.data.track_files:
            if tf.track_id not in all_track_ids:
                raise CatalogValidationError(
                    f"Track file ({tf.id}) references non-existent track_id {tf.track_id}"
                )

        # Fix 2: Validate metadata_fields entity_type and entity_id
        supported_entity_types = {"track", "album", "artist"}
        for mf in bundle.data.metadata_fields:
            etype = mf.entity_type.lower().strip()
            if etype not in supported_entity_types:
                raise CatalogValidationError(
                    f"MetadataField '{mf.field_name}' ({mf.id}) has unsupported entity_type '{mf.entity_type}'. "
                    f"Supported types: {sorted(supported_entity_types)}"
                )
            target_ids = (
                all_track_ids if etype == "track"
                else all_album_ids if etype == "album"
                else all_artist_ids
            )
            if mf.entity_id not in target_ids:
                raise CatalogValidationError(
                    f"MetadataField '{mf.field_name}' ({mf.id}) references non-existent {etype}_id {mf.entity_id}"
                )

    def import_bundle(
        self,
        bundle_source: Path | str | dict[str, Any],
        on_conflict: ConflictPolicy = "skip",
        dry_run: bool = False,
    ) -> CatalogImportResult:
        """Import a catalog bundle with configurable conflict resolution and dry-run safety."""
        if isinstance(bundle_source, Path):
            raw_bundle = self.load_bundle_from_file(bundle_source)
        elif isinstance(bundle_source, str):
            try:
                raw_bundle = json.loads(bundle_source)
            except json.JSONDecodeError as exc:
                raise CatalogValidationError(f"Malformed JSON string: {exc}") from exc
        elif isinstance(bundle_source, dict):
            raw_bundle = bundle_source
        else:
            raise TypeError(f"Unsupported bundle source type: {type(bundle_source)}")

        bundle = self.validate_bundle(raw_bundle)

        if dry_run:
            logger.info("Executing catalog import dry-run; no persistent mutations will be applied.")
            return self._execute_import(bundle, on_conflict=on_conflict, dry_run=True)

        # Atomic transaction execution: rollback on any error
        try:
            with self._session.begin_nested():
                result = self._execute_import(bundle, on_conflict=on_conflict, dry_run=False)
                self._session.flush()
            self._session.commit()
            return result
        except SqlAlchemyIntegrityError as exc:
            self._session.rollback()
            err_str = str(exc.orig) if hasattr(exc, "orig") else str(exc)
            err_lower = err_str.lower()
            if "unique constraint" in err_lower or "unique" in err_lower:
                raise CatalogConflictError(
                    f"Database unique constraint conflict during import: {err_str}"
                ) from exc
            raise CatalogValidationError(
                f"Database integrity constraint violation during import: {err_str}"
            ) from exc
        except Exception:
            self._session.rollback()
            raise

    def _execute_import(
        self,
        bundle: CatalogBundle,
        on_conflict: ConflictPolicy,
        dry_run: bool,
    ) -> CatalogImportResult:
        result = CatalogImportResult(dry_run=dry_run)
        search_repo = SqliteSearchRepository(self._session)

        # Cache existing IDs for quick conflict detection
        existing_artist_map = {m.id: m for m in self._session.scalars(select(ArtistModel)).all()}
        existing_artist_mbids = {m.mbid: m.id for m in existing_artist_map.values() if m.mbid}
        existing_album_map = {m.id: m for m in self._session.scalars(select(AlbumModel)).all()}
        existing_album_mbids = {m.mbid: m.id for m in existing_album_map.values() if m.mbid}
        existing_track_map = {m.id: m for m in self._session.scalars(select(TrackModel)).all()}
        existing_track_mbids = {m.mbid: m.id for m in existing_track_map.values() if m.mbid}
        existing_tf_map = {m.id: m for m in self._session.scalars(select(TrackFileModel)).all()}
        existing_tf_paths = {m.file_path: m for m in existing_tf_map.values()}
        existing_tag_keys = {
            (m.track_id, m.name.lower(), m.value.lower(), m.source): m
            for m in self._session.scalars(select(TagModel)).all()
        }
        existing_meta_keys = {
            (m.entity_type.lower(), m.entity_id, m.field_name.lower(), m.provider.lower()): m
            for m in self._session.scalars(select(MetadataFieldModel)).all()
        }
        existing_le_ids = set(self._session.scalars(select(ListeningEventModel.id)).all())

        # ── 1. Artists ────────────────────────────────────────────────────────
        for artist in bundle.data.artists:
            # Check secondary unique constraint (MusicBrainz ID)
            if (
                artist.mbid
                and artist.mbid in existing_artist_mbids
                and existing_artist_mbids[artist.mbid] != artist.id
                and on_conflict == "fail"
            ):
                raise CatalogConflictError(
                    f"Artist '{artist.name}' ({artist.id}) has MusicBrainz ID '{artist.mbid}' "
                    f"which already exists on artist {existing_artist_mbids[artist.mbid]} in database."
                )

            if artist.id in existing_artist_map:
                if on_conflict == "fail":
                    raise CatalogConflictError(f"Artist with id {artist.id} already exists in database.")
                elif on_conflict == "replace":
                    if not dry_run:
                        m = existing_artist_map[artist.id]
                        m.name = artist.name
                        m.sort_name = artist.sort_name
                        m.mbid = artist.mbid
                        if artist.updated_at:
                            m.updated_at = artist.updated_at
                    result.artists_updated += 1
                else:  # skip
                    result.artists_skipped += 1
            else:
                if not dry_run:
                    new_artist = ArtistModel(
                        id=artist.id,
                        name=artist.name,
                        sort_name=artist.sort_name,
                        mbid=artist.mbid,
                        created_at=artist.created_at,
                        updated_at=artist.updated_at,
                    )
                    self._session.add(new_artist)
                    existing_artist_map[artist.id] = new_artist
                    if artist.mbid:
                        existing_artist_mbids[artist.mbid] = artist.id
                result.artists_created += 1

        # ── 2. Albums ─────────────────────────────────────────────────────────
        for album in bundle.data.albums:
            # Check secondary unique constraint (MusicBrainz ID)
            if (
                album.mbid
                and album.mbid in existing_album_mbids
                and existing_album_mbids[album.mbid] != album.id
                and on_conflict == "fail"
            ):
                raise CatalogConflictError(
                    f"Album '{album.title}' ({album.id}) has MusicBrainz ID '{album.mbid}' "
                    f"which already exists on album {existing_album_mbids[album.mbid]} in database."
                )

            if album.id in existing_album_map:
                if on_conflict == "fail":
                    raise CatalogConflictError(f"Album with id {album.id} already exists in database.")
                elif on_conflict == "replace":
                    if not dry_run:
                        m = existing_album_map[album.id]
                        m.title = album.title
                        m.artist_id = album.artist_id
                        m.year = album.year
                        m.total_tracks = album.total_tracks
                        m.total_discs = album.total_discs
                        m.mbid = album.mbid
                        m.art_hash = album.art_hash
                        if album.updated_at:
                            m.updated_at = album.updated_at
                    result.albums_updated += 1
                else:  # skip
                    result.albums_skipped += 1
            else:
                if not dry_run:
                    new_album = AlbumModel(
                        id=album.id,
                        title=album.title,
                        artist_id=album.artist_id,
                        year=album.year,
                        total_tracks=album.total_tracks,
                        total_discs=album.total_discs,
                        mbid=album.mbid,
                        art_hash=album.art_hash,
                        created_at=album.created_at,
                        updated_at=album.updated_at,
                    )
                    self._session.add(new_album)
                    existing_album_map[album.id] = new_album
                    if album.mbid:
                        existing_album_mbids[album.mbid] = album.id
                result.albums_created += 1

        # ── 3. Tracks ─────────────────────────────────────────────────────────
        tracks_to_index: list[tuple[TrackModel, str | None, str | None]] = []

        for track in bundle.data.tracks:
            # Check secondary unique constraint (MusicBrainz ID)
            if (
                track.mbid
                and track.mbid in existing_track_mbids
                and existing_track_mbids[track.mbid] != track.id
                and on_conflict == "fail"
            ):
                raise CatalogConflictError(
                    f"Track '{track.title}' ({track.id}) has MusicBrainz ID '{track.mbid}' "
                    f"which already exists on track {existing_track_mbids[track.mbid]} in database."
                )

            artist_name = (
                existing_artist_map[track.artist_id].name if track.artist_id in existing_artist_map else None
            )
            album_title = (
                existing_album_map[track.album_id].title if track.album_id in existing_album_map else None
            )

            if track.id in existing_track_map:
                if on_conflict == "fail":
                    raise CatalogConflictError(f"Track with id {track.id} already exists in database.")
                elif on_conflict == "replace":
                    if not dry_run:
                        m = existing_track_map[track.id]
                        m.title = track.title
                        m.artist_id = track.artist_id
                        m.album_id = track.album_id
                        m.track_number = track.track_number
                        m.disc_number = track.disc_number
                        m.duration_seconds = track.duration_seconds
                        m.year = track.year
                        m.genre = track.genre
                        m.mbid = track.mbid
                        m.lyrics = track.lyrics
                        if track.updated_at:
                            m.updated_at = track.updated_at
                        tracks_to_index.append((m, artist_name, album_title))
                    result.tracks_updated += 1
                else:  # skip
                    result.tracks_skipped += 1
            else:
                if not dry_run:
                    new_track = TrackModel(
                        id=track.id,
                        title=track.title,
                        artist_id=track.artist_id,
                        album_id=track.album_id,
                        track_number=track.track_number,
                        disc_number=track.disc_number,
                        duration_seconds=track.duration_seconds,
                        year=track.year,
                        genre=track.genre,
                        mbid=track.mbid,
                        lyrics=track.lyrics,
                        created_at=track.created_at,
                        updated_at=track.updated_at,
                    )
                    self._session.add(new_track)
                    existing_track_map[track.id] = new_track
                    if track.mbid:
                        existing_track_mbids[track.mbid] = track.id
                    tracks_to_index.append((new_track, artist_name, album_title))
                result.tracks_created += 1

        # ── 4. TrackFiles ─────────────────────────────────────────────────────
        for tf in bundle.data.track_files:
            candidate_path = Path(tf.file_path) if tf.file_path else None
            linked_path: str | None = None

            if candidate_path and candidate_path.is_file():
                # Strict content-hash verification before linking local file
                if tf.file_hash:
                    local_hash = compute_file_hash(candidate_path)
                    if local_hash.lower() == tf.file_hash.lower():
                        linked_path = str(candidate_path.resolve())
                    else:
                        result.warnings.append(
                            f"TrackFile {tf.id}: local file '{tf.file_path}' content hash ({local_hash[:12]}...) "
                            f"does not match imported stream hash ({tf.file_hash[:12]}...); not associating."
                        )
                else:
                    result.warnings.append(
                        f"TrackFile {tf.id}: local file '{tf.file_path}' exists but bundle lacks file_hash; "
                        "refusing to auto-link unverified physical media."
                    )
            elif tf.file_path:
                result.warnings.append(
                    f"TrackFile {tf.id}: physical audio file '{tf.file_path}' not found on this host; "
                    "imported metadata in unlinked state."
                )

            # Assign effective path (unique relative path if unlinked, never inventing unsupported URI schemes)
            if linked_path:
                effective_path = linked_path
                verified_at = datetime.now(UTC)
            else:
                filename_hint = sanitize_unlinked_filename(tf.file_path)
                effective_path = f"unlinked/{tf.id}/{filename_hint}"
                verified_at = None

            id_conflict = tf.id in existing_tf_map
            path_conflict = effective_path in existing_tf_paths

            if id_conflict or path_conflict:
                if on_conflict == "fail":
                    raise CatalogConflictError(
                        f"TrackFile with id {tf.id} or path '{effective_path}' already exists in database."
                    )
                elif on_conflict == "replace":
                    if not dry_run:
                        m = existing_tf_map.get(tf.id) or existing_tf_paths.get(effective_path)
                        assert m is not None
                        m.file_path = effective_path
                        m.file_hash = tf.file_hash
                        m.file_size = tf.file_size
                        m.format = tf.format.lower() if tf.format else "mp3"
                        m.codec = tf.codec
                        m.bitrate = tf.bitrate
                        m.sample_rate = tf.sample_rate
                        m.bit_depth = tf.bit_depth
                        m.channels = tf.channels
                        m.channel_layout = tf.channel_layout
                        m.encoder = tf.encoder
                        m.duration_seconds = tf.duration_seconds
                        m.is_lossless = tf.is_lossless
                        m.quality_score = tf.quality_score
                        m.verified_at = verified_at
                    result.track_files_updated += 1
                else:  # skip
                    result.track_files_skipped += 1
            else:
                if not dry_run:
                    new_tf = TrackFileModel(
                        id=tf.id,
                        track_id=tf.track_id,
                        file_path=effective_path,
                        file_hash=tf.file_hash,
                        file_size=tf.file_size,
                        format=tf.format.lower() if tf.format else "mp3",
                        codec=tf.codec,
                        bitrate=tf.bitrate,
                        sample_rate=tf.sample_rate,
                        bit_depth=tf.bit_depth,
                        channels=tf.channels,
                        channel_layout=tf.channel_layout,
                        encoder=tf.encoder,
                        duration_seconds=tf.duration_seconds,
                        is_lossless=tf.is_lossless,
                        quality_score=tf.quality_score,
                        added_at=tf.added_at,
                        verified_at=verified_at,
                    )
                    self._session.add(new_tf)
                    existing_tf_map[tf.id] = new_tf
                    existing_tf_paths[effective_path] = new_tf
                result.track_files_created += 1

        # ── 5. Tags ───────────────────────────────────────────────────────────
        for tag in bundle.data.tags:
            tag_key = (tag.track_id, tag.name.lower(), tag.value.lower(), tag.source)
            if tag_key in existing_tag_keys:
                if on_conflict == "fail":
                    raise CatalogConflictError(
                        f"Tag '{tag.name}={tag.value}' on track {tag.track_id} already exists."
                    )
                # Tags are value objects; duplicate skip
                result.tags_skipped += 1
            else:
                if not dry_run:
                    new_tag = TagModel(
                        id=tag.id,
                        track_id=tag.track_id,
                        name=tag.name,
                        value=tag.value,
                        source=tag.source,
                        created_at=tag.created_at,
                    )
                    self._session.add(new_tag)
                    existing_tag_keys[tag_key] = new_tag
                result.tags_created += 1

        # ── 6. MetadataFields ─────────────────────────────────────────────────
        for mf in bundle.data.metadata_fields:
            meta_key = (mf.entity_type.lower(), mf.entity_id, mf.field_name.lower(), mf.provider.lower())
            if meta_key in existing_meta_keys:
                if on_conflict == "fail":
                    raise CatalogConflictError(
                        f"MetadataField '{mf.field_name}' for {mf.entity_type} {mf.entity_id} already exists."
                    )
                elif on_conflict == "replace":
                    if not dry_run:
                        m = existing_meta_keys[meta_key]
                        m.raw_value = mf.raw_value
                        m.normalized_value = mf.normalized_value
                        m.is_active = mf.is_active
                        m.priority = mf.priority
                    result.metadata_fields_updated += 1
                else:  # skip
                    result.metadata_fields_skipped += 1
            else:
                if not dry_run:
                    new_mf = MetadataFieldModel(
                        id=mf.id,
                        entity_type=mf.entity_type,
                        entity_id=mf.entity_id,
                        field_name=mf.field_name,
                        provider=mf.provider,
                        raw_value=mf.raw_value,
                        normalized_value=mf.normalized_value,
                        is_active=mf.is_active,
                        priority=mf.priority,
                        created_at=mf.created_at,
                    )
                    self._session.add(new_mf)
                    existing_meta_keys[meta_key] = new_mf
                result.metadata_fields_created += 1

        # ── 7. ListeningEvents ────────────────────────────────────────────────
        for le in bundle.data.listening_events:
            if le.id in existing_le_ids:
                if on_conflict == "fail":
                    raise CatalogConflictError(f"ListeningEvent with id {le.id} already exists.")
                result.listening_events_skipped += 1
            else:
                if not dry_run:
                    # If track_file_id is set but does not exist in DB, set to None for safe foreign key
                    tf_id = le.track_file_id if le.track_file_id in existing_tf_map else None
                    new_le = ListeningEventModel(
                        id=le.id,
                        track_id=le.track_id,
                        track_file_id=tf_id,
                        timestamp_utc=le.timestamp_utc,
                        started_at_sec=le.started_at_sec,
                        ended_at_sec=le.ended_at_sec,
                        duration_listened_seconds=le.duration_listened_seconds,
                        track_duration_seconds=le.track_duration_seconds,
                        was_completed=le.was_completed,
                        was_skipped=le.was_skipped,
                        playback_source=le.playback_source,
                        device_id=le.device_id,
                        created_at=le.created_at,
                    )
                    self._session.add(new_le)
                    existing_le_ids.add(le.id)
                result.listening_events_created += 1

        # ── 8. Search Index Updates ───────────────────────────────────────────
        if not dry_run:
            for track_m, artist_name, album_title in tracks_to_index:
                search_repo.index_track(
                    track_id=track_m.id,
                    title=track_m.title,
                    artist_name=artist_name,
                    album_title=album_title,
                    genre=track_m.genre,
                )

        return result
