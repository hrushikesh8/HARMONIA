"""Import service — orchestrates the music import pipeline.

The import pipeline:
1. Scan directories for music files
2. Skip files already in the library (by path)
3. Extract metadata from new files (mutagen)
4. Compute file hashes (optional, for deduplication)
5. Resolve or create Artist and Album entities
6. Create Track and TrackFile entities
7. Compute quality scores
8. Store tags
9. Publish events
"""

from __future__ import annotations

from pathlib import Path

from sqlalchemy.orm import Session

from harmonia.application.dto import ImportError_, ImportResult
from harmonia.core.events import ImportCompleted, TrackImported, event_bus
from harmonia.core.logging import get_logger
from harmonia.core.types import TagSource
from harmonia.domain.entities.album import Album
from harmonia.domain.entities.artist import Artist
from harmonia.domain.entities.tag import Tag
from harmonia.domain.entities.track import Track
from harmonia.domain.entities.track_file import TrackFile
from harmonia.domain.services.quality_scorer import compute_quality_score
from harmonia.domain.values.audio_format import AudioFormat
from harmonia.infrastructure.audio.mutagen_reader import (
    ExtractedMetadata,
    compute_file_hash,
    extract_metadata,
)
from harmonia.infrastructure.filesystem.scanner import ScannedFile, scan_directory
from harmonia.infrastructure.persistence.album_repo import SqliteAlbumRepository
from harmonia.infrastructure.persistence.artist_repo import SqliteArtistRepository
from harmonia.infrastructure.persistence.track_repo import SqliteTrackRepository

logger = get_logger(__name__)


class ImportService:
    """Orchestrates the music file import pipeline."""

    def __init__(
        self,
        session: Session,
        supported_formats: list[str],
        follow_symlinks: bool = False,
        compute_hashes: bool = True,
    ) -> None:
        self._session = session
        self._supported_formats = supported_formats
        self._follow_symlinks = follow_symlinks
        self._compute_hashes = compute_hashes
        self._track_repo = SqliteTrackRepository(session)
        self._album_repo = SqliteAlbumRepository(session)
        self._artist_repo = SqliteArtistRepository(session)

    def import_directory(self, directory: Path) -> ImportResult:
        """Scan and import all music files from a directory.

        Args:
            directory: Root directory to scan.

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

        # 2. Process each file.
        for scanned in scanned_files:
            try:
                imported = self._import_file(scanned)
                if imported:
                    result.imported += 1
                else:
                    result.skipped += 1
            except Exception as e:
                result.failed += 1
                result.errors.append(
                    ImportError_(file_path=str(scanned.path), reason=str(e))
                )
                logger.warning("Failed to import %s: %s", scanned.path, e)

        # 3. Commit the transaction.
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

    def import_file(self, file_path: Path) -> bool:
        """Import a single music file.

        Args:
            file_path: Path to the music file.

        Returns:
            True if the file was imported, False if skipped.
        """
        ext = file_path.suffix.lower()
        if ext not in {fmt if fmt.startswith(".") else f".{fmt}" for fmt in self._supported_formats}:
            return False

        scanned = ScannedFile(
            path=file_path,
            size=file_path.stat().st_size,
            extension=ext,
        )
        imported = self._import_file(scanned)
        if imported:
            self._session.commit()
        return imported

    def _import_file(self, scanned: ScannedFile) -> bool:
        """Internal: process a single scanned file.

        Returns True if imported, False if skipped (already exists).
        """
        file_path_str = str(scanned.path)

        # Skip if file is already in the library.
        existing = self._track_repo.get_file_by_path(file_path_str)
        if existing is not None:
            logger.debug("Skipping already-imported file: %s", scanned.path)
            return False

        # Extract metadata.
        meta = extract_metadata(scanned.path)

        # Use file tags as title fallback, then filename.
        title = meta.title or scanned.path.stem

        # Resolve artist.
        artist = self._resolve_artist(meta)

        # Resolve album.
        album = self._resolve_album(meta, artist)

        # Create track.
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

        # Compute file hash.
        file_hash = None
        if self._compute_hashes:
            try:
                file_hash = compute_file_hash(scanned.path)
            except OSError as e:
                logger.warning("Cannot hash file %s: %s", scanned.path, e)

        # Determine audio format.
        audio_format = AudioFormat.from_extension(scanned.extension)
        if audio_format is None:
            # Should not happen — scanner already filtered by extension.
            audio_format = AudioFormat.MP3  # Fallback

        # Create track file.
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
        )

        # Compute quality score.
        track_file.quality_score = compute_quality_score(track_file)

        self._track_repo.add_file(track_file)

        # Store genre as a tag.
        if meta.genre:
            tag = Tag(
                track_id=track.id,
                name="genre",
                value=meta.genre,
                source=TagSource.FILE,
            )
            self._track_repo.add_tag(tag)

        # Publish event.
        event_bus.publish(TrackImported(track_id=track.id, file_path=file_path_str))

        return True

    def _resolve_artist(self, meta: ExtractedMetadata) -> Artist | None:
        """Find or create an artist from extracted metadata."""
        artist_name = meta.artist or meta.album_artist
        if not artist_name:
            return None

        # Try to find existing artist.
        artist = self._artist_repo.find_by_name(artist_name)
        if artist is not None:
            return artist

        # Create new artist.
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

        # Try to find existing album.
        album = self._album_repo.find_by_title_and_artist(meta.album, artist_id)
        if album is not None:
            return album

        # Create new album.
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
