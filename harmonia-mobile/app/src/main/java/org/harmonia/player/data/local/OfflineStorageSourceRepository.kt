package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.StorageSourceDao
import org.harmonia.player.data.local.entity.StorageSourceEntity
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.repository.StorageSourceRepository

/**
 * Concrete implementation of StorageSourceRepository backed by Room SQLite.
 */
class OfflineStorageSourceRepository(
    private val storageSourceDao: StorageSourceDao
) : StorageSourceRepository {

    override fun observeAllSources(): Flow<List<StorageSource>> {
        return storageSourceDao.observeAllSources().map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeEnabledSources(): Flow<List<StorageSource>> {
        return storageSourceDao.observeEnabledSources().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getSourceById(id: String): StorageSource? {
        return storageSourceDao.getSourceById(id)?.toDomain()
    }

    override suspend fun saveSource(source: StorageSource) {
        storageSourceDao.upsertSource(source.toEntity())
    }

    override suspend fun setSourceEnabled(id: String, isEnabled: Boolean) {
        storageSourceDao.setSourceEnabled(id, isEnabled)
    }

    override suspend fun deleteSource(id: String) {
        storageSourceDao.deleteSourceById(id)
    }

    private fun StorageSourceEntity.toDomain(): StorageSource {
        return when (type) {
            "MEDIA_STORE_PRIMARY" -> StorageSource.MediaStorePrimary(
                id = id,
                displayName = displayName,
                isRemovable = isRemovable,
                isAvailable = isEnabled
            )
            "MEDIA_STORE_VOLUME" -> {
                val parts = uri.split(":", limit = 2)
                val volumeName = parts.getOrNull(0) ?: id
                val volumeUuid = parts.getOrNull(1) ?: id
                StorageSource.MediaStoreVolume(
                    id = id,
                    volumeName = volumeName,
                    volumeUuid = volumeUuid,
                    displayName = displayName,
                    isRemovable = isRemovable,
                    isAvailable = isEnabled
                )
            }
            "SAF_DOCUMENT_TREE" -> StorageSource.SafDocumentTree(
                id = id,
                treeUri = uri,
                displayName = displayName,
                isPersisted = true,
                isRemovable = isRemovable,
                isAvailable = isEnabled
            )
            "INTERNAL_SYNC" -> StorageSource.InternalSyncStorage(
                id = id,
                rootPath = uri,
                displayName = displayName,
                isRemovable = isRemovable,
                isAvailable = isEnabled
            )
            else -> StorageSource.MediaStorePrimary(
                id = id,
                displayName = displayName,
                isRemovable = isRemovable,
                isAvailable = isEnabled
            )
        }
    }

    private fun StorageSource.toEntity(): StorageSourceEntity {
        return when (this) {
            is StorageSource.MediaStorePrimary -> StorageSourceEntity(
                id = id,
                type = "MEDIA_STORE_PRIMARY",
                uri = "content://media/external/audio/media",
                displayName = displayName,
                isRemovable = isRemovable,
                isEnabled = isAvailable
            )
            is StorageSource.MediaStoreVolume -> StorageSourceEntity(
                id = id,
                type = "MEDIA_STORE_VOLUME",
                uri = "$volumeName:$volumeUuid",
                displayName = displayName,
                isRemovable = isRemovable,
                isEnabled = isAvailable
            )
            is StorageSource.SafDocumentTree -> StorageSourceEntity(
                id = id,
                type = "SAF_DOCUMENT_TREE",
                uri = treeUri,
                displayName = displayName,
                isRemovable = isRemovable,
                isEnabled = isAvailable
            )
            is StorageSource.InternalSyncStorage -> StorageSourceEntity(
                id = id,
                type = "INTERNAL_SYNC",
                uri = rootPath,
                displayName = displayName,
                isRemovable = isRemovable,
                isEnabled = isAvailable
            )
        }
    }
}
