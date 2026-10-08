package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.storage.ScanCheckpoint

/**
 * Domain repository boundary for persisting and retrieving scan checkpoint state.
 */
interface ScanCheckpointRepository {
    fun observeCheckpoint(sourceId: String): Flow<ScanCheckpoint?>
    suspend fun getCheckpoint(sourceId: String): ScanCheckpoint?
    suspend fun saveCheckpoint(checkpoint: ScanCheckpoint)
    suspend fun resetCheckpoint(sourceId: String)
}
