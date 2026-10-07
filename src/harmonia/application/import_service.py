"""Import service — orchestrates the music import pipeline.

Phase 2 enhanced pipeline:
1. Scan directories for music files with mtime/size fast-skip
2. Content hash deduplication (Tier A / A2 / B / C)
3. Metadata extraction and field-level provenance tracking
4. Conservative normalization and deterministic resolution
5. Embedded artwork extraction and content-addressed storage
6. Opt-in MusicBrainz metadata enrichment (offline-safe)
7. Per-file SAVEPOINT transaction isolation
8. Automatic FTS5 search indexing
9. Event publishing
"""

from __future__ import annotations

import contextlib
from datetime import UTC, datetime
from pathlib import Path
from typing import TYPE_CHECKING

from harmonia.application.dto import ImportError_, ImportResult
from harmonia.core.events import ImportCompleted, TrackImported, event_bus
from harmonia.core.logging import get_logger
from harmonia.core.types import MetadataPriority, MetadataProviderName, TagSource
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.duplicate_candidate import DuplicateCandidate
from harmonia.domain.entities.tag import Tag
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.services.duplicate_detector import DuplicateDetector
from harmonia.domain.services.metadata_resolver import MetadataResolver
from harmonia.domain.services.quality_scorer import compute_quality_score
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.audio.artwork_extractor import ArtworkExtractor
from harmonia.infrastructure.audio.mutagen_reader import (
    ExtractedMetadata,
    compute_file_hash,
    extract_metadata,
)
from harmonia.infrastructure.filesystem.scanner import ScannedFile, scan_directory
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.duplicate_candidate_repo import (
    SqliteDuplicateCandidateRepository,
)
from harmonia.infrastructure.persistence.metadata_field_repo import (
    SqliteMetadataFieldRepository,
)
from harmonia.infrastructure.persistence.search_repo import SqliteSearchRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

if TYPE_CHECKING:
    from sqlalchemy.orm import Session

    from harmonia.domain.entities.metadata_field import MetadataFieldRecord
    from harmonia.domain.ports.album_repository import AlbumRepository
    from harmonia.domain.ports.artist_repository import ArtistRepository
    from harmonia.domain.ports.duplicate_candidate_repository import DuplicateCandidateRepository
    from harmonia.domain.ports.metadata_field_repository import MetadataFieldRepository
    from harmonia.domain.ports.metadata_provider import MetadataProvider
    from harmonia.domain.ports.search_repository import SearchRepository
    from harmonia.domain.ports.track_repository import TrackRepository

logger = get_logger(__name__)


class ImportService:
    """Orchestrates the music file import pipeline."""

    def __init__(
        self,
        session: Session,
        supported_formats: list[str],
        follow_symlinks: bool = False,
        compute_hashes: bool = True,
        artwork_dir: Path | None = None,
        extract_artwork: bool = True,
        fetch_metadata: bool = False,
        metadata_provider: MetadataProvider | None = None,
        track_repo: TrackRepository | None = None,
        album_repo: AlbumRepository | None = None,
        artist_repo: ArtistRepository | None = None,
        metadata_field_repo: MetadataFieldRepository | None = None,
        duplicate_candidate_repo: DuplicateCandidateRepository | None = None,
        search_repo: SearchRepository | None = None,
    ) -> None:
        self._session = session
        self._supported_formats = supported_formats
        self._follow_symlinks = follow_symlinks
        self._compute_hashes = compute_hashes
        self._extract_artwork = extract_artwork
        self._fetch_metadata = fetch_metadata
        self._metadata_provider = metadata_provider

        # Repositories
        self._track_repo = track_repo or SqliteTrackRepository(session)
        self._album_repo = album_repo or SqliteAlbumRepository(session)
        self._artist_repo = artist_repo or SqliteArtistRepository(session)
        self._metadata_field_repo = metadata_field_repo or SqliteMetadataFieldRepository(session)
        self._duplicate_candidate_repo = duplicate_candidate_repo or SqliteDuplicateCandidateRepository(session)
        self._search_repo = search_repo or SqliteSearchRepository(session)

        # Artwork extractor
        art_path = artwork_dir or Path("data/artwork")
        self._artwork_extractor = ArtworkExtractor(artwork_dir=art_path) if extract_artwork else None

    def import_directory(self, directory: Path, rescan: bool = False) -> ImportResult:
        """Scan and import all music files from a directory with per-file SAVEPOINT isolation.

        Args:
            directory: Root directory to scan.
            rescan: If True, forces re-checking of previously imported files.

        Returns:
            ImportResult with counts and any errors.
        """
        result = ImportResult()

        # 1. Scan for music files.
        scanned_files = scan_directory(
            directory, self._supported_formats, self._follow_symlinks
        )
        result.total_scanned = len(scanned_files)

        if not scanned_files:
            logger.info("No music files found in %s", directory)
            return result

        # 2. Process each file with SAVEPOINT isolation.
        for scanned in scanned_files:
            savepoint = self._session.begin_nested()
            try:
                imported = self._import_file(scanned, rescan=rescan)
                savepoint.commit()
                if imported:
                    result.imported += 1
                else:
                    result.skipped += 1
            except Exception as e:
                savepoint.rollback()
                result.failed += 1
                result.errors.append(
                    ImportError_(file_path=str(scanned.path), reason=str(e))
                )
                logger.warning("Failed to import %s: %s", scanned.path, e)

        # 3. Commit the outer transaction.
        self._session.commit()

        # 4. Publish completion event.
        event_bus.publish(
            ImportCompleted(
                total_imported=result.imported,
                total_skipped=result.skipped,
                total_failed=result.failed,
            )
        )

        logger.info(
            "Import complete: %d imported, %d skipped, %d failed (of %d scanned)",
            result.imported,
            result.skipped,
            result.failed,
            result.total_scanned,
        )
        return result

    def import_file(self, file_path: Path, rescan: bool = False) -> bool:
        """Import a single music file.

        Args:
            file_path: Path to the music file.
            rescan: If True, force re-check if already present.

        Returns:
            True if imported or updated, False if skipped.
        """
        ext = file_path.suffix.lower()
        if ext not in {
            fmt if fmt.startswith(".") else f".{fmt}" for fmt in self._supported_formats
        }:
            return False

        scanned = ScannedFile(
            path=file_path,
            size=file_path.stat().st_size,
            extension=ext,
        )
        imported = self._import_file(scanned, rescan=rescan)
        if imported:
            self._session.commit()
        return imported

    def _import_file(self, scanned: ScannedFile, rescan: bool = False) -> bool:
        """Process a single scanned file with provenance, dedup, and FTS indexing."""
        file_path_str = str(scanned.path)

        current_mtime: float | None = None
        with contextlib.suppress(OSError):
            current_mtime = scanned.path.stat().st_mtime

        # ── Case A: Path is already in library ─────────────────────────────────
        existing_file = self._track_repo.get_file_by_path(file_path_str)
        if existing_file is not None:
            # Check fast-skip condition (mtime & size unchanged)
            if (
                not rescan
                and existing_file.file_mtime is not None
                and current_mtime is not None
                and abs(existing_file.file_mtime - current_mtime) < 1e-4
                and existing_file.file_size == scanned.size
            ):
                logger.debug("Skipping unchanged file: %s", scanned.path)
                return False

            # File modified or rescan requested with changed/missing mtime -> Update
            return self._update_existing_file(existing_file, scanned, current_mtime)

        # ── Case B: Path is new, check SHA-256 for Moved / Copied Files ──────
        file_hash: str | None = None
        if self._compute_hashes:
            try:
                file_hash = compute_file_hash(scanned.path)
            except OSError as e:
                logger.warning("Cannot hash file %s: %s", scanned.path, e)

        if file_hash:
            existing_by_hash = self._track_repo.get_file_by_hash(file_hash)
            if existing_by_hash is not None:
                # Check if the previous file still exists on disk
                if not Path(existing_by_hash.file_path).exists():
                    # File was MOVED: update file_path
                    old_path = existing_by_hash.file_path
                    existing_by_hash.file_path = file_path_str
                    existing_by_hash.file_mtime = current_mtime
                    existing_by_hash.verified_at = datetime.now(UTC)
                    self._track_repo.update_file(existing_by_hash)
                    logger.info("File moved: %s -> %s", old_path, file_path_str)
                    return True
                else:
                    # File was COPIED: Tier A2 duplicate -> attach TrackFile to same Track
                    meta = extract_metadata(scanned.path)
                    audio_format = AudioFormat.from_extension(scanned.extension) or AudioFormat.MP3
                    new_tf = TrackFile(
                        track_id=existing_by_hash.track_id,
                        file_path=file_path_str,
                        file_hash=file_hash,
                        file_size=scanned.size,
                        format=audio_format,
                        codec=meta.codec,
                        bitrate=meta.bitrate,
                        sample_rate=meta.sample_rate,
                        bit_depth=meta.bit_depth,
                        channels=meta.channels,
                        duration_seconds=meta.duration_seconds,
                        is_lossless=meta.is_lossless,
                        file_mtime=current_mtime,
                    )
                    new_tf.quality_score = compute_quality_score(new_tf)
                    self._track_repo.add_file(new_tf)
                    logger.info(
                        "Linked physical duplicate file to track %s: %s",
                        existing_by_hash.track_id,
                        file_path_str,
                    )
                    return True

        # ── Case C: Brand new file ───────────────────────────────────────────
        meta = extract_metadata(scanned.path)
        title = meta.title or scanned.path.stem

        artist = self._resolve_artist(meta)
        album = self._resolve_album(meta, artist)

        # Extract artwork
        if self._extract_artwork and self._artwork_extractor and album:
            art = self._artwork_extractor.extract_from_file(scanned.path)
            if art and album.art_path is None:
                album.art_path = art.relative_path
                album.art_hash = art.art_hash
                self._album_repo.update(album)
                rec_art = MetadataResolver.create_record(
                    "album",
                    album.id,
                    "art_path",
                    MetadataProviderName.FILE.value,
                    art.relative_path,
                    priority=MetadataPriority.FILE,
                )
                rec_art.is_active = True
                self._metadata_field_repo.add(rec_art)

        # Check for Tier B (PROBABLE) duplicate candidate among existing album tracks
        tier_b_candidate_id = None
        tier_b_reason = ""
        if album:
            existing_tracks = self._track_repo.get_by_album(album.id)
            for cand in existing_tracks:
                match = DuplicateDetector.evaluate_track_candidate(
                    incoming_title=title,
                    incoming_artist=artist.name if artist else None,
                    incoming_album=album.title,
                    incoming_duration=meta.duration_seconds,
                    incoming_disc=meta.disc_number,
                    existing_track=cand,
                    existing_artist_name=artist.name if artist else None,
                    existing_album_title=album.title,
                )
                if match.tier == "B":
                    tier_b_candidate_id = cand.id
                    tier_b_reason = match.reason
                    break

        # Create Track entity
        track = Track(
            title=title,
            artist_id=artist.id if artist else None,
            album_id=album.id if album else None,
            track_number=meta.track_number,
            disc_number=meta.disc_number,
            duration_seconds=meta.duration_seconds,
            year=meta.year,
            genre=meta.genre,
            mbid=meta.mb_track_id,
        )
        self._track_repo.add(track)

        # If Tier B match found, record candidate relationship (separate track created!)
        if tier_b_candidate_id is not None:
            candidate = DuplicateCandidate(
                track_id_a=tier_b_candidate_id,
                track_id_b=track.id,
                confidence="probable",
                reason=tier_b_reason,
            )
            self._duplicate_candidate_repo.add(candidate)
            logger.info(
                "Recorded probable duplicate candidate: track %s and %s",
                tier_b_candidate_id,
                track.id,
            )

        # Provenance: record field-level metadata records
        field_records: list[MetadataFieldRecord] = []
        file_prov = MetadataProviderName.FILE.value
        path_prov = MetadataProviderName.PATH.value

        # Title
        title_prov = file_prov if meta.title else path_prov
        title_prio = MetadataPriority.FILE if meta.title else MetadataPriority.PATH
        field_records.append(
            MetadataResolver.create_record(
                "track", track.id, "title", title_prov, title, title_prio
            )
        )

        if artist:
            field_records.append(
                MetadataResolver.create_record(
                    "track", track.id, "artist_name", file_prov, artist.name, MetadataPriority.FILE
                )
            )
        if album:
            field_records.append(
                MetadataResolver.create_record(
                    "track", track.id, "album_title", file_prov, album.title, MetadataPriority.FILE
                )
            )
        if meta.year:
            field_records.append(
                MetadataResolver.create_record(
                    "track", track.id, "year", file_prov, str(meta.year), MetadataPriority.FILE
                )
            )
        if meta.genre:
            field_records.append(
                MetadataResolver.create_record(
                    "track", track.id, "genre", file_prov, meta.genre, MetadataPriority.FILE
                )
            )
        if meta.track_number is not None:
            field_records.append(
                MetadataResolver.create_record(
                    "track", track.id, "track_number", file_prov, str(meta.track_number), MetadataPriority.FILE
                )
            )
        if meta.disc_number is not None:
            field_records.append(
                MetadataResolver.create_record(
                    "track", track.id, "disc_number", file_prov, str(meta.disc_number), MetadataPriority.FILE
                )
            )

        # Opt-in MusicBrainz metadata lookup
        if self._fetch_metadata and self._metadata_provider:
            mb_records = self._metadata_provider.lookup_track(
                entity_id=track.id,
                artist_name=artist.name if artist else None,
                album_title=album.title if album else None,
                track_title=title,
                duration_seconds=meta.duration_seconds,
            )
            field_records.extend(mb_records)

        # Resolve winning values
        winners = MetadataResolver.resolve_entity_fields(field_records)
        if "title" in winners:
            track.title = winners["title"].normalized_value
        if "year" in winners:
            with contextlib.suppress(ValueError):
                track.year = int(winners["year"].normalized_value)
        if "genre" in winners:
            track.genre = winners["genre"].normalized_value
        if "mbid" in winners:
            track.mbid = winners["mbid"].normalized_value
        self._track_repo.update(track)

        self._metadata_field_repo.add_all(field_records)

        # AudioFormat & TrackFile
        audio_format = AudioFormat.from_extension(scanned.extension) or AudioFormat.MP3
        track_file = TrackFile(
            track_id=track.id,
            file_path=file_path_str,
            file_hash=file_hash,
            file_size=scanned.size,
            format=audio_format,
            codec=meta.codec,
            bitrate=meta.bitrate,
            sample_rate=meta.sample_rate,
            bit_depth=meta.bit_depth,
            channels=meta.channels,
            duration_seconds=meta.duration_seconds,
            is_lossless=meta.is_lossless,
            file_mtime=current_mtime,
        )
        track_file.quality_score = compute_quality_score(track_file)
        self._track_repo.add_file(track_file)

        # Tag
        if track.genre:
            tag = Tag(
                track_id=track.id,
                name="genre",
                value=track.genre,
                source=TagSource.FILE,
            )
            self._track_repo.add_tag(tag)

        # FTS5 Indexing
        self._search_repo.index_track(
            track_id=track.id,
            title=track.title,
            artist_name=artist.name if artist else None,
            album_title=album.title if album else None,
            genre=track.genre,
        )

        event_bus.publish(TrackImported(track_id=track.id, file_path=file_path_str))
        return True

    def _update_existing_file(
        self,
        existing_file: TrackFile,
        scanned: ScannedFile,
        current_mtime: float | None,
    ) -> bool:
        """Update metadata and technical properties for a modified file."""
        meta = extract_metadata(scanned.path)
        file_hash = None
        if self._compute_hashes:
            with contextlib.suppress(OSError):
                file_hash = compute_file_hash(scanned.path)

        audio_format = AudioFormat.from_extension(scanned.extension) or existing_file.format

        existing_file.file_hash = file_hash
        existing_file.file_size = scanned.size
        existing_file.format = audio_format
        existing_file.codec = meta.codec
        existing_file.bitrate = meta.bitrate
        existing_file.sample_rate = meta.sample_rate
        existing_file.bit_depth = meta.bit_depth
        existing_file.channels = meta.channels
        existing_file.duration_seconds = meta.duration_seconds
        existing_file.is_lossless = meta.is_lossless
        existing_file.file_mtime = current_mtime
        existing_file.verified_at = datetime.now(UTC)
        existing_file.quality_score = compute_quality_score(existing_file)

        self._track_repo.update_file(existing_file)

        # Update track if needed
        track = self._track_repo.get_by_id(existing_file.track_id)
        if track:
            if meta.duration_seconds:
                track.duration_seconds = meta.duration_seconds
            self._track_repo.update(track)

            artist = self._artist_repo.get_by_id(track.artist_id) if track.artist_id else None
            album = self._album_repo.get_by_id(track.album_id) if track.album_id else None

            self._search_repo.index_track(
                track_id=track.id,
                title=track.title,
                artist_name=artist.name if artist else None,
                album_title=album.title if album else None,
                genre=track.genre,
            )

        logger.info("Updated modified file in library: %s", scanned.path)
        return True

    def _resolve_artist(self, meta: ExtractedMetadata) -> Artist | None:
        """Find or create an artist from extracted metadata."""
        artist_name = meta.artist or meta.album_artist
        if not artist_name:
            return None

        artist = self._artist_repo.find_by_name(artist_name)
        if artist is not None:
            return artist

        artist = Artist(name=artist_name, mbid=meta.mb_artist_id)
        self._artist_repo.add(artist)
        logger.debug("Created artist: %s", artist.name)
        return artist

    def _resolve_album(
        self, meta: ExtractedMetadata, artist: Artist | None
    ) -> Album | None:
        """Find or create an album from extracted metadata."""
        if not meta.album:
            return None

        artist_id = artist.id if artist else None

        album = self._album_repo.find_by_title_and_artist(meta.album, artist_id)
        if album is not None:
            return album

        album = Album(
            title=meta.album,
            artist_id=artist_id,
            year=meta.year,
            total_tracks=meta.total_tracks,
            total_discs=meta.total_discs,
            mbid=meta.mb_album_id,
        )
        self._album_repo.add(album)
        logger.debug("Created album: %s", album.title)
        return album
