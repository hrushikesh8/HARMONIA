package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.LocalFileDao
import org.harmonia.player.data.local.entity.LocalFileEntity
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.repository.LocalFileRepository

/**
 * Concrete implementation of LocalFileRepository backed by Room SQLite.
 */
class OfflineLocalFileRepository(
    private val localFileDao: LocalFileDao
) : LocalFileRepository {

    override fun observeLocalFilesForTrack(trackId: String): Flow<List<LocalFile>> {
        return localFileDao.observeLocalFilesForTrack(trackId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getLocalFileById(id: String): LocalFile? {
        return localFileDao.getLocalFileById(id)?.toDomain()
    }

    override suspend fun getLocalFileByUri(fileUri: String): LocalFile? {
        return localFileDao.getLocalFileByUri(fileUri)?.toDomain()
    }

    override suspend fun upsertLocalFile(localFile: LocalFile) {
        val entity = LocalFileEntity(
            id = localFile.id,
            trackId = localFile.trackId,
            storageType = localFile.storageType.name,
            fileUri = localFile.fileUri,
            fileSize = localFile.fileSize,
            fileHash = localFile.fileHash,
            format = localFile.format,
            bitrate = localFile.bitrate,
            sampleRate = localFile.sampleRate,
            bitDepth = localFile.bitDepth,
            channels = localFile.channels,
            isLossless = localFile.isLossless,
            availabilityStatus = if (localFile.isVerified) "AVAILABLE" else "UNVERIFIED",
            verifiedAt = localFile.verifiedAt,
            addedAt = localFile.addedAt
        )
        localFileDao.upsertLocalFile(entity)
    }

    override suspend fun updateVerificationStatus(id: String, isVerified: Boolean, verifiedAt: Long) {
        val status = if (isVerified) "AVAILABLE" else "MISSING"
        localFileDao.updateAvailabilityStatus(id, status, verifiedAt)
    }

    override suspend fun deleteLocalFile(id: String) {
        localFileDao.deleteLocalFileById(id)
    }

    private fun LocalFileEntity.toDomain(): LocalFile {
        return LocalFile(
            id = id,
            trackId = trackId,
            storageType = try {
                StorageType.valueOf(storageType)
            } catch (e: Exception) {
                StorageType.INTERNAL_SYNC
            },
            fileUri = fileUri,
            fileSize = fileSize,
            fileHash = fileHash,
            format = format,
            bitrate = bitrate,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels,
            isLossless = isLossless,
            isVerified = availabilityStatus == "AVAILABLE",
            verifiedAt = verifiedAt,
            addedAt = addedAt
        )
    }
}
