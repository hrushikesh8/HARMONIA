package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.storage.StorageSource

/**
 * Domain repository boundary for managing persisted storage roots.
 */
interface StorageSourceRepository {
    fun observeAllSources(): Flow<List<StorageSource>>
    fun observeEnabledSources(): Flow<List<StorageSource>>
    suspend fun getSourceById(id: String): StorageSource?
    suspend fun saveSource(source: StorageSource)
    suspend fun setSourceEnabled(id: String, isEnabled: Boolean)
    suspend fun deleteSource(id: String)
}
