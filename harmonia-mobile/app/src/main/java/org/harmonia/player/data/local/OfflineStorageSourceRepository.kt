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
        val direct = storageSourceDao.getSourceById(id)
        if (direct != null) return direct.toDomain()

        // Fallback: resolve canonical or legacy aliases across existing sources
        val all = storageSourceDao.getAllSources()
        val matched = all.firstOrNull { entity ->
            when (entity.type) {
                "SAF_DOCUMENT_TREE" -> {
                    entity.id == id ||
                    entity.uri == id ||
                    StorageSource.SafDocumentTree.deriveCanonicalId(entity.uri) == id ||
                    StorageSource.SafDocumentTree.normalizeTreeUri(entity.uri) == StorageSource.SafDocumentTree.normalizeTreeUri(id)
                }
                "MEDIA_STORE_VOLUME" -> {
                    val uuid = StorageSource.MediaStoreVolume.extractVolumeUuid(entity.uri, entity.id)
                    uuid != null && (
                        entity.id == id ||
                        StorageSource.MediaStoreVolume.deriveCanonicalId(uuid) == id ||
                        uuid == id.lowercase().removePrefix("mediastore_vol_")
                    )
                }
                else -> entity.id == id
            }
        }
        return matched?.toDomain()
    }

    override suspend fun saveSource(source: StorageSource) {
        val allSources = storageSourceDao.getAllSources()
        val existingMatch = when (source) {
            is StorageSource.SafDocumentTree -> {
                val normalizedTarget = StorageSource.SafDocumentTree.normalizeTreeUri(source.treeUri)
                val canonicalTargetId = StorageSource.SafDocumentTree.deriveCanonicalId(source.treeUri)
                allSources.firstOrNull { entity ->
                    entity.type == "SAF_DOCUMENT_TREE" && (
                        entity.id == source.id ||
                        entity.id == canonicalTargetId ||
                        entity.uri == source.treeUri ||
                        StorageSource.SafDocumentTree.normalizeTreeUri(entity.uri) == normalizedTarget
                    )
                }
            }
            is StorageSource.MediaStoreVolume -> {
                val targetUuid = StorageSource.MediaStoreVolume.normalizeVolumeUuid(source.volumeUuid)
                allSources.firstOrNull { entity ->
                    if (entity.type != "MEDIA_STORE_VOLUME") return@firstOrNull false
                    val entityUuid = StorageSource.MediaStoreVolume.extractVolumeUuid(entity.uri, entity.id)
                    entityUuid != null && entityUuid == targetUuid
                }
            }
            is StorageSource.MediaStorePrimary -> {
                allSources.firstOrNull { it.type == "MEDIA_STORE_PRIMARY" || it.id == source.id }
            }
            is StorageSource.InternalSyncStorage -> {
                allSources.firstOrNull { it.type == "INTERNAL_SYNC" || it.id == source.id }
            }
        }

        if (existingMatch != null) {
            // Reuse existing source: retain its existing ID, preventing duplicate rows
            // and preserving all LocalFile foreign-key associations
            val entityToSave = source.toEntity().copy(
                id = existingMatch.id,
                lastScannedAt = existingMatch.lastScannedAt
            )
            storageSourceDao.upsertSource(entityToSave)
        } else {
            storageSourceDao.upsertSource(source.toEntity())
        }
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
                val extractedUuid = StorageSource.MediaStoreVolume.extractVolumeUuid(uri, id) ?: id
                val volumeName = if (uri.contains(":") && !uri.startsWith("content:", ignoreCase = true) && !uri.startsWith("file:", ignoreCase = true)) {
                    uri.substringBefore(":").trim().ifBlank { displayName }
                } else {
                    displayName.ifBlank { extractedUuid }
                }
                StorageSource.MediaStoreVolume(
                    id = id,
                    volumeName = volumeName,
                    volumeUuid = extractedUuid,
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
