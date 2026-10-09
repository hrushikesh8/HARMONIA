package org.harmonia.player.data.local.scanner

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.entity.AlbumEntity
import org.harmonia.player.data.local.entity.ArtistEntity
import org.harmonia.player.data.local.entity.LocalFileEntity
import org.harmonia.player.data.local.entity.ScanCheckpointEntity
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.domain.metadata.AudioMetadataReader
import org.harmonia.player.domain.model.scanner.DiscoveredMedia
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.domain.scanner.MediaReconciliationEngine
import org.harmonia.player.domain.scanner.MediaSourceScanner
import java.util.UUID

/**
 * Concrete implementation of MediaReconciliationEngine for Harmonia Mobile.
 * Coordinates discovery, M4-B metadata extraction, cryptographic content hashing,
 * and transactional Room persistence.
 *
 * Guarantees:
 * 1. Expensive I/O (file decoding, hashing, metadata extraction) executes outside Room transactions.
 * 2. Unchanged files are recognized via size + lastModified and skipped without reading bitstreams.
 * 3. Relocated/renamed files are reconciled by content hash identity rather than duplicating tracks.
 * 4. Missing files in full reconciliation are marked MISSING; records are NEVER destructively deleted.
 * 5. Checkpoints advance only upon successful completion.
 * 6. Error isolation: one corrupt or unreadable file cannot abort the scan.
 */
class DefaultMediaReconciliationEngine(
    private val context: Context,
    private val database: HarmoniaDatabase,
    private val metadataReader: AudioMetadataReader,
    private val permissionChecker: StoragePermissionChecker,
    private val mediaStoreScanner: MediaSourceScanner,
    private val safTreeScanner: MediaSourceScanner
) : MediaReconciliationEngine {

    override fun scanSource(
        source: StorageSource,
        mode: ScanMode
    ): Flow<ScanProgress> = flow {
        var progress = ScanProgress(sourceId = source.id, status = ScanProgressStatus.IDLE)
        emit(progress)

        // 1. Permission Check
        val perm = permissionChecker.getPermissionState()
        if (perm != StoragePermissionState.GRANTED) {
            emit(progress.copy(status = ScanProgressStatus.PERMISSION_REQUIRED, errorMessage = "Storage permission required"))
            return@flow
        }

        // 2. Discovery Phase
        emit(progress.copy(status = ScanProgressStatus.DISCOVERING))

        val checkpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        val lastGen = if (mode == ScanMode.INCREMENTAL) checkpoint?.lastGeneration ?: 0L else 0L

        val scanner = when (source) {
            is StorageSource.SafDocumentTree -> safTreeScanner
            else -> mediaStoreScanner
        }

        val candidates: List<DiscoveredMedia> = try {
            scanner.discoverMedia(source, mode, lastGen)
        } catch (t: Throwable) {
            emit(progress.copy(status = ScanProgressStatus.FAILED, errorMessage = t.message))
            return@flow
        }

        progress = progress.copy(
            status = ScanProgressStatus.PROCESSING,
            discoveredCount = candidates.size
        )
        emit(progress)

        var imported = 0
        var updated = 0
        var unchanged = 0
        var moved = 0
        var failed = 0
        var processed = 0

        val activeUris = mutableSetOf<String>()

        // 3. Candidate Ingestion Loop
        for (candidate in candidates) {
            activeUris.add(candidate.runtimeLocator)
            try {
                if (!candidate.isAccessible) {
                    throw IllegalStateException("Candidate bitstream is not accessible: ${candidate.runtimeLocator}")
                }
                val existingFile = database.localFileDao().getLocalFileByUri(candidate.runtimeLocator)

                if (existingFile != null) {
                    // Freshness Evaluation
                    val isFresh = existingFile.fileSize == candidate.sizeBytes &&
                            existingFile.verifiedAt >= (candidate.lastModifiedEpochSec * 1000L)

                    if (isFresh) {
                        // UNCHANGED: Skip bitstream I/O
                        unchanged++
                        if (existingFile.sourceId == null || existingFile.availabilityStatus != "AVAILABLE") {
                            database.localFileDao().upsertLocalFile(
                                existingFile.copy(
                                    sourceId = existingFile.sourceId ?: source.id,
                                    availabilityStatus = "AVAILABLE",
                                    verifiedAt = System.currentTimeMillis()
                                )
                            )
                        }
                    } else {
                        // CHANGED: Re-read metadata & content hash outside transaction
                        val metadata = metadataReader.readMetadata(candidate.runtimeLocator, extractArtwork = true)
                        val newHash = ContentHasher.computeFileHash(context, candidate.runtimeLocator)
                            ?: existingFile.fileHash

                        database.localFileDao().upsertLocalFile(
                            existingFile.copy(
                                sourceId = existingFile.sourceId ?: source.id,
                                fileSize = candidate.sizeBytes,
                                fileHash = newHash,
                                bitrate = metadata.bitrateKbps,
                                sampleRate = metadata.sampleRateHz,
                                availabilityStatus = "AVAILABLE",
                                verifiedAt = System.currentTimeMillis()
                            )
                        )

                        // Update Track record
                        val currentTrack = database.trackDao().getTrackById(existingFile.trackId)
                        if (currentTrack != null) {
                            val updatedTrack = currentTrack.track.copy(
                                title = metadata.title ?: currentTrack.track.title,
                                durationSec = metadata.durationSec ?: currentTrack.track.durationSec,
                                year = metadata.year ?: currentTrack.track.year,
                                genre = metadata.genre ?: currentTrack.track.genre,
                                updatedAt = System.currentTimeMillis()
                            )
                            database.trackDao().upsertTrack(updatedTrack)
                        }
                        updated++
                    }
                } else {
                    // NEW, MOVED, or DUPLICATE: Compute content hash outside transaction
                    val contentHash = ContentHasher.computeFileHash(context, candidate.runtimeLocator)
                    val existingByHash = contentHash?.let { database.localFileDao().getLocalFileByHash(it) }

                    if (existingByHash != null) {
                        // Check if the previous location still exists within the SAME source.
                        // Invariant: Content hash alone must NEVER establish physical source ownership!
                        // A file move is ONLY inferred if the previous location explicitly belonged to this same source.
                        // If existingByHash is from a different source OR is an unresolved legacy record (sourceId == null),
                        // it MUST NOT be modified or hijacked as a "move". It must be treated as a distinct physical instance
                        // sharing logical Track identity.
                        val isSameSource = existingByHash.sourceId != null && existingByHash.sourceId == source.id
                        val oldLocationInCandidates = candidates.any { it.runtimeLocator == existingByHash.fileUri }
                        val oldLocationExists = oldLocationInCandidates || isLocatorAccessible(context, existingByHash.fileUri)

                        if (isSameSource && !oldLocationExists) {
                            // MOVED / RELOCATED: Previous location no longer exists within the SAME source.
                            // Update URI on existing physical LocalFile.
                            database.localFileDao().upsertLocalFile(
                                existingByHash.copy(
                                    fileUri = candidate.runtimeLocator,
                                    storageType = candidate.sourceType.name,
                                    availabilityStatus = "AVAILABLE",
                                    verifiedAt = System.currentTimeMillis()
                                )
                            )
                            moved++
                        } else {
                            // DISTINCT PHYSICAL INSTANCE (Duplicate, different source, or unresolved legacy):
                            // Link to same logical Track (preserving single musical work identity),
                            // but record distinct physical LocalFile for this separate location/source.
                            // The existingByHash (including any unresolved legacy row) is preserved completely untouched!
                            val duplicateLocalFile = LocalFileEntity(
                                id = UUID.randomUUID().toString(),
                                trackId = existingByHash.trackId,
                                sourceId = source.id,
                                storageType = candidate.sourceType.name,
                                fileUri = candidate.runtimeLocator,
                                fileSize = candidate.sizeBytes,
                                fileHash = contentHash,
                                format = AudioFileFilter.extractFormat(candidate.displayName, candidate.mimeType),
                                bitrate = existingByHash.bitrate,
                                sampleRate = existingByHash.sampleRate,
                                isLossless = existingByHash.isLossless,
                                availabilityStatus = "AVAILABLE",
                                verifiedAt = System.currentTimeMillis(),
                                addedAt = System.currentTimeMillis()
                            )
                            database.localFileDao().upsertLocalFile(duplicateLocalFile)
                            imported++
                        }
                    } else {
                        // NEW: Extract metadata and create library records
                        val metadata = metadataReader.readMetadata(candidate.runtimeLocator, extractArtwork = true)
                        val finalHash = contentHash ?: UUID.randomUUID().toString()

                        // Resolve or create Artist
                        val artistId = if (!metadata.artist.isNullOrBlank()) {
                            val existingArtist = database.artistDao().getArtistByName(metadata.artist)
                            existingArtist?.id ?: run {
                                val newArtist = ArtistEntity(
                                    id = UUID.randomUUID().toString(),
                                    name = metadata.artist,
                                    sortName = metadata.artist,
                                    artworkHash = metadata.artworkHash,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                database.artistDao().upsertArtist(newArtist)
                                newArtist.id
                            }
                        } else null

                        // Resolve or create Album
                        val albumTitle = when {
                            !metadata.album.isNullOrBlank() -> metadata.album
                            metadata.artworkHash != null -> metadata.title ?: candidate.displayName.substringBeforeLast('.')
                            else -> null
                        }

                        val albumId = if (albumTitle != null) {
                            val existingAlbum = database.albumDao().getAlbumByTitleAndArtist(albumTitle, artistId)
                            if (existingAlbum != null) {
                                if (existingAlbum.artworkHash == null && metadata.artworkHash != null) {
                                    database.albumDao().upsertAlbum(existingAlbum.copy(artworkHash = metadata.artworkHash))
                                }
                                existingAlbum.id
                            } else {
                                val newAlbum = AlbumEntity(
                                    id = UUID.randomUUID().toString(),
                                    title = albumTitle,
                                    artistId = artistId,
                                    year = metadata.year,
                                    artworkHash = metadata.artworkHash,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                database.albumDao().upsertAlbum(newAlbum)
                                newAlbum.id
                            }
                        } else null

                        val trackId = UUID.randomUUID().toString()
                        val trackTitle = metadata.title ?: candidate.displayName.substringBeforeLast('.')
                        val track = TrackEntity(
                            id = trackId,
                            albumId = albumId,
                            artistId = artistId,
                            title = trackTitle,
                            trackNumber = metadata.trackNumber,
                            discNumber = metadata.discNumber ?: 1,
                            durationSec = metadata.durationSec ?: 0.0,
                            year = metadata.year,
                            genre = metadata.genre,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis(),
                            isProvisional = true
                        )

                        val localFile = LocalFileEntity(
                            id = UUID.randomUUID().toString(),
                            trackId = trackId,
                            sourceId = source.id,
                            storageType = candidate.sourceType.name,
                            fileUri = candidate.runtimeLocator,
                            fileSize = candidate.sizeBytes,
                            fileHash = finalHash,
                            format = AudioFileFilter.extractFormat(candidate.displayName, candidate.mimeType),
                            bitrate = metadata.bitrateKbps,
                            sampleRate = metadata.sampleRateHz,
                            isLossless = false,
                            availabilityStatus = "AVAILABLE",
                            verifiedAt = System.currentTimeMillis(),
                            addedAt = System.currentTimeMillis()
                        )

                        database.trackDao().upsertTrack(track)
                        database.localFileDao().upsertLocalFile(localFile)
                        imported++
                    }
                }
            } catch (_: Throwable) {
                // Isolate failure for single file
                failed++
            }

            processed++
            progress = progress.copy(
                processedCount = processed,
                importedCount = imported,
                updatedCount = updated,
                unchangedCount = unchanged,
                movedCount = moved,
                failedCount = failed
            )
            emit(progress)
        }

        // 4. Missing Files Handling in Full Reconciliation
        // Invariant: Only mark missing if discovery was 100% complete (failed == 0) and strictly for this source.id
        var unavailable = 0
        if (mode == ScanMode.FULL_RECONCILIATION && failed == 0) {
            val sourceFiles = database.localFileDao().getLocalFilesBySourceId(source.id)
            for (file in sourceFiles) {
                if (file.fileUri !in activeUris && file.availabilityStatus == "AVAILABLE") {
                    database.localFileDao().updateAvailabilityStatus(
                        id = file.id,
                        status = "MISSING",
                        verifiedAt = System.currentTimeMillis()
                    )
                    unavailable++
                }
            }
        }

        // 5. Checkpoint Handling
        val previousCheckpoint = database.scanCheckpointDao().getCheckpoint(source.id)
        val previousGeneration = previousCheckpoint?.lastGeneration ?: 0L

        if (failed == 0) {
            val newGeneration = when (source) {
                is StorageSource.MediaStorePrimary,
                is StorageSource.MediaStoreVolume -> {
                    val candidateGens = candidates.mapNotNull { it.generation }
                    if (candidateGens.isNotEmpty()) {
                        maxOf(previousGeneration, candidateGens.maxOrNull() ?: previousGeneration)
                    } else {
                        previousGeneration
                    }
                }
                is StorageSource.SafDocumentTree -> {
                    // SAF does not possess provider generation counters.
                    // Scan time is recorded in lastScanTimestamp; lastGeneration remains 0L
                    // to avoid confusing timestamps with provider generations.
                    0L
                }
                is StorageSource.InternalSyncStorage -> 0L
            }

            val completedCheckpoint = ScanCheckpointEntity(
                sourceId = source.id,
                volumeName = source.id,
                lastGeneration = newGeneration,
                lastScanTimestamp = System.currentTimeMillis(),
                scannedFileCount = imported + updated + unchanged + moved,
                status = "COMPLETED"
            )
            database.scanCheckpointDao().upsertCheckpoint(completedCheckpoint)

            progress = progress.copy(
                status = ScanProgressStatus.COMPLETED,
                unavailableCount = unavailable
            )
            emit(progress)
        } else {
            // Partial scan: do NOT advance lastGeneration beyond failed scan boundary!
            val partialCheckpoint = ScanCheckpointEntity(
                sourceId = source.id,
                volumeName = source.id,
                lastGeneration = previousGeneration,
                lastScanTimestamp = System.currentTimeMillis(),
                scannedFileCount = imported + updated + unchanged + moved,
                status = "PARTIAL"
            )
            database.scanCheckpointDao().upsertCheckpoint(partialCheckpoint)

            progress = progress.copy(
                status = ScanProgressStatus.PARTIAL,
                unavailableCount = unavailable,
                errorMessage = "$failed file(s) failed during reconciliation"
            )
            emit(progress)
        }
    }.flowOn(Dispatchers.IO)

    private fun isLocatorAccessible(context: Context, locator: String): Boolean {
        return try {
            if (locator.startsWith("content://")) {
                val uri = android.net.Uri.parse(locator)
                context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
            } else {
                java.io.File(locator).exists()
            }
        } catch (_: Throwable) {
            false
        }
    }
}
