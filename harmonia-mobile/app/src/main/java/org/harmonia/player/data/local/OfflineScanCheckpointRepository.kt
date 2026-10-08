package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.ScanCheckpointDao
import org.harmonia.player.data.local.entity.ScanCheckpointEntity
import org.harmonia.player.domain.model.storage.ScanCheckpoint
import org.harmonia.player.domain.model.storage.ScanStatus
import org.harmonia.player.domain.repository.ScanCheckpointRepository

/**
 * Concrete implementation of ScanCheckpointRepository backed by Room SQLite.
 */
class OfflineScanCheckpointRepository(
    private val scanCheckpointDao: ScanCheckpointDao
) : ScanCheckpointRepository {

    override fun observeCheckpoint(sourceId: String): Flow<ScanCheckpoint?> {
        return scanCheckpointDao.observeCheckpoint(sourceId).map { entity ->
            entity?.toDomain()
        }
    }

    override suspend fun getCheckpoint(sourceId: String): ScanCheckpoint? {
        return scanCheckpointDao.getCheckpoint(sourceId)?.toDomain()
    }

    override suspend fun saveCheckpoint(checkpoint: ScanCheckpoint) {
        val entity = ScanCheckpointEntity(
            sourceId = checkpoint.sourceId,
            volumeName = checkpoint.volumeName,
            lastGeneration = checkpoint.lastGeneration,
            lastScanTimestamp = checkpoint.lastScanTimestamp,
            scannedFileCount = checkpoint.scannedFileCount,
            status = checkpoint.status.name
        )
        scanCheckpointDao.upsertCheckpoint(entity)
    }

    override suspend fun resetCheckpoint(sourceId: String) {
        scanCheckpointDao.deleteCheckpoint(sourceId)
    }

    private fun ScanCheckpointEntity.toDomain(): ScanCheckpoint {
        val scanStatus = try {
            ScanStatus.valueOf(status)
        } catch (e: Exception) {
            ScanStatus.IDLE
        }
        return ScanCheckpoint(
            sourceId = sourceId,
            volumeName = volumeName,
            lastGeneration = lastGeneration,
            lastScanTimestamp = lastScanTimestamp,
            scannedFileCount = scannedFileCount,
            status = scanStatus
        )
    }
}
