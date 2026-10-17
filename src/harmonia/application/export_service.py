"""Catalog export application service for Harmonia.

Extracts library entities from the SQLite database, validates relational dependencies,
constructs the portable versioned CatalogBundle, and writes it with integrity hashing.
"""

from __future__ import annotations

import json
from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING

from sqlalchemy import select

from harmonia import __version__
from harmonia.application.catalog_dto import (
    CURRENT_CATALOG_SCHEMA_VERSION,
    AlbumBundleItem,
    ArtistBundleItem,
    CatalogBundle,
    CatalogData,
    CatalogManifest,
    ListeningEventBundleItem,
    MetadataFieldBundleItem,
    TagBundleItem,
    TrackBundleItem,
    TrackFileBundleItem,
    compute_data_checksum,
)
from harmonia.core.logging import get_logger
from harmonia.infrastructure.persistence.models import (
    AlbumModel,
    ArtistModel,
    ListeningEventModel,
    MetadataFieldModel,
    TagModel,
    TrackFileModel,
    TrackModel,
)

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

logger = get_logger("harmonia.catalog.export")


def extract_portable_filename(path_str: str | None) -> str | None:
    """Extract a pure filename basename from Windows or POSIX paths across all operating systems."""
    if not path_str:
        return None
    # Normalize backslashes to forward slashes to split both Windows and POSIX path separators
    normalized = path_str.replace("\\", "/").rstrip("/")
    if not normalized:
        return None
    name = normalized.split("/")[-1].strip()
    if not name or name in (".", "..") or name.endswith(":"):
        return None
    if len(name) >= 2 and name[1] == ":" and name[0].isalpha():
        name = name[2:].strip()
    return name if name and name not in (".", "..") else None


class CatalogExportService:
    """Exports Harmonia library entities to versioned, portable JSON bundles."""

    def __init__(self, session: Session) -> None:
        self._session = session

    def build_bundle(self, include_telemetry: bool = True) -> CatalogBundle:
        """Construct a validated CatalogBundle in memory from the current database."""
        # 1. Artists
        artists_stmt = select(ArtistModel).order_by(ArtistModel.name, ArtistModel.id)
        artists = [
            ArtistBundleItem(
                id=a.id,
                name=a.name,
                sort_name=a.sort_name,
                mbid=a.mbid,
                created_at=a.created_at,
                updated_at=a.updated_at,
            )
            for a in self._session.scalars(artists_stmt).all()
        ]

        # 2. Albums
        albums_stmt = select(AlbumModel).order_by(AlbumModel.title, AlbumModel.id)
        albums = [
            AlbumBundleItem(
                id=al.id,
                title=al.title,
                artist_id=al.artist_id,
                year=al.year,
                total_tracks=al.total_tracks,
                total_discs=al.total_discs,
                mbid=al.mbid,
                art_hash=al.art_hash,
                created_at=al.created_at,
                updated_at=al.updated_at,
            )
            for al in self._session.scalars(albums_stmt).all()
        ]

        # 3. Tracks
        tracks_stmt = select(TrackModel).order_by(TrackModel.title, TrackModel.id)
        tracks = [
            TrackBundleItem(
                id=t.id,
                title=t.title,
                artist_id=t.artist_id,
                album_id=t.album_id,
                track_number=t.track_number,
                disc_number=t.disc_number,
                duration_seconds=t.duration_seconds,
                year=t.year,
                genre=t.genre,
                mbid=t.mbid,
                lyrics=t.lyrics,
                created_at=t.created_at,
                updated_at=t.updated_at,
            )
            for t in self._session.scalars(tracks_stmt).all()
        ]

        # 4. TrackFiles
        track_files_stmt = select(TrackFileModel).order_by(TrackFileModel.id)
        track_files = [
            TrackFileBundleItem(
                id=tf.id,
                track_id=tf.track_id,
                file_hash=tf.file_hash,
                file_size=tf.file_size,
                format=tf.format,
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
                # Portable filename hint only (basename); never export host-specific absolute filesystem paths
                file_path=extract_portable_filename(tf.file_path),
                added_at=tf.added_at,
                verified_at=tf.verified_at,
            )
            for tf in self._session.scalars(track_files_stmt).all()
        ]

        # 5. Tags
        tags_stmt = select(TagModel).order_by(TagModel.name, TagModel.id)
        tags = [
            TagBundleItem(
                id=tag.id,
                track_id=tag.track_id,
                name=tag.name,
                value=tag.value,
                source=tag.source,
                created_at=tag.created_at,
            )
            for tag in self._session.scalars(tags_stmt).all()
        ]

        # 6. MetadataFields
        meta_stmt = select(MetadataFieldModel).order_by(MetadataFieldModel.created_at, MetadataFieldModel.id)
        metadata_fields = [
            MetadataFieldBundleItem(
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
            for mf in self._session.scalars(meta_stmt).all()
        ]

        # 7. ListeningEvents (telemetry)
        listening_events: list[ListeningEventBundleItem] = []
        if include_telemetry:
            le_stmt = select(ListeningEventModel).order_by(ListeningEventModel.timestamp_utc, ListeningEventModel.id)
            listening_events = [
                ListeningEventBundleItem(
                    id=le.id,
                    track_id=le.track_id,
                    track_file_id=le.track_file_id,
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
                for le in self._session.scalars(le_stmt).all()
            ]

        data = CatalogData(
            artists=artists,
            albums=albums,
            tracks=tracks,
            track_files=track_files,
            tags=tags,
            metadata_fields=metadata_fields,
            listening_events=listening_events,
        )

        entity_counts = {
            "artists": len(artists),
            "albums": len(albums),
            "tracks": len(tracks),
            "track_files": len(track_files),
            "tags": len(tags),
            "metadata_fields": len(metadata_fields),
            "listening_events": len(listening_events),
        }

        manifest = CatalogManifest(
            schema_version=CURRENT_CATALOG_SCHEMA_VERSION,
            harmonia_version=__version__,
            exported_at=datetime.now(UTC),
            source_system="harmonia-desktop",
            entity_counts=entity_counts,
        )

        checksum = compute_data_checksum(data)

        logger.info(
            "Constructed catalog bundle with %d tracks, %d albums, %d artists (checksum: %s)",
            len(tracks),
            len(albums),
            len(artists),
            checksum[:12],
        )

        return CatalogBundle(manifest=manifest, data=data, checksum=checksum)

    def export_to_json(self, pretty: bool = True, include_telemetry: bool = True) -> str:
        """Export the library as a formatted or compact JSON string."""
        bundle = self.build_bundle(include_telemetry=include_telemetry)
        data_dict = bundle.model_dump(mode="json")
        indent = 2 if pretty else None
        return json.dumps(data_dict, indent=indent, ensure_ascii=False)

    def export_to_file(
        self,
        destination_path: Path,
        force: bool = False,
        pretty: bool = True,
        include_telemetry: bool = True,
    ) -> Path:
        """Write the catalog bundle to disk with overwrite protection."""
        if destination_path.exists() and not force:
            raise FileExistsError(
                f"Export file '{destination_path}' already exists. Pass --force / force=True to overwrite."
            )

        destination_path.parent.mkdir(parents=True, exist_ok=True)
        content = self.export_to_json(pretty=pretty, include_telemetry=include_telemetry)

        # Write to temporary file in same directory first, then replace atomically
        tmp_path = destination_path.with_suffix(f"{destination_path.suffix}.tmp")
        try:
            tmp_path.write_text(content, encoding="utf-8")
            tmp_path.replace(destination_path)
        finally:
            if tmp_path.exists():
                tmp_path.unlink()

        logger.info("Catalog successfully exported to %s", destination_path)
        return destination_path
