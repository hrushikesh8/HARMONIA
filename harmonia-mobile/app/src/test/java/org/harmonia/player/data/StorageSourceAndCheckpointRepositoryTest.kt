package org.harmonia.player.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.OfflineScanCheckpointRepository
import org.harmonia.player.data.local.OfflineStorageSourceRepository
import org.harmonia.player.data.local.dao.ScanCheckpointDao
import org.harmonia.player.data.local.dao.StorageSourceDao
import org.harmonia.player.data.local.entity.ScanCheckpointEntity
import org.harmonia.player.data.local.entity.StorageSourceEntity
import org.harmonia.player.domain.model.storage.ScanCheckpoint
import org.harmonia.player.domain.model.storage.ScanStatus
import org.harmonia.player.domain.model.storage.StorageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSourceAndCheckpointRepositoryTest {

    private class FakeStorageSourceDao : StorageSourceDao {
        val sources = mutableMapOf<String, StorageSourceEntity>()

        override fun observeAllSources() = flowOf(sources.values.toList())
        override fun observeEnabledSources() = flowOf(sources.values.filter { it.isEnabled })
        override suspend fun getSourceById(id: String) = sources[id]
        override suspend fun getAllSources() = sources.values.toList()
        override suspend fun upsertSource(source: StorageSourceEntity) { sources[source.id] = source }
        override suspend fun upsertSources(sources: List<StorageSourceEntity>) { sources.forEach { this.sources[it.id] = it } }
        override suspend fun updateSource(source: StorageSourceEntity) { sources[source.id] = source }
        override suspend fun setSourceEnabled(id: String, isEnabled: Boolean) {
            sources[id]?.let { sources[id] = it.copy(isEnabled = isEnabled) }
        }
        override suspend fun deleteSourceById(id: String) { sources.remove(id) }
    }

    private class FakeScanCheckpointDao : ScanCheckpointDao {
        val checkpoints = mutableMapOf<String, ScanCheckpointEntity>()

        override fun observeCheckpoint(sourceId: String) = flowOf(checkpoints[sourceId])
        override suspend fun getCheckpoint(sourceId: String) = checkpoints[sourceId]
        override suspend fun upsertCheckpoint(checkpoint: ScanCheckpointEntity) { checkpoints[checkpoint.sourceId] = checkpoint }
        override suspend fun deleteCheckpoint(sourceId: String) { checkpoints.remove(sourceId) }
    }

    @Test
    fun `storage source repository saves, observes, and toggles sources correctly`() = runBlocking {
        val dao = FakeStorageSourceDao()
        val repo = OfflineStorageSourceRepository(dao)

        val primary = StorageSource.MediaStorePrimary()
        repo.saveSource(primary)

        val retrieved = repo.getSourceById(primary.id)
        assertNotNull(retrieved)
        assertEquals("mediastore_primary", retrieved?.id)
        assertTrue(retrieved?.isAvailable == true)

        val all = repo.observeAllSources().first()
        assertEquals(1, all.size)

        repo.setSourceEnabled(primary.id, false)
        val enabled = repo.observeEnabledSources().first()
        assertEquals(0, enabled.size)

        repo.deleteSource(primary.id)
        assertNull(repo.getSourceById(primary.id))
    }

    @Test
    fun `scan checkpoint repository persists and maps domain checkpoints accurately`() = runBlocking {
        val dao = FakeScanCheckpointDao()
        val repo = OfflineScanCheckpointRepository(dao)

        val checkpoint = ScanCheckpoint(
            sourceId = "src-1",
            volumeName = "primary",
            lastGeneration = 120L,
            lastScanTimestamp = 5000L,
            scannedFileCount = 42,
            status = ScanStatus.COMPLETED
        )

        repo.saveCheckpoint(checkpoint)

        val retrieved = repo.getCheckpoint("src-1")
        assertNotNull(retrieved)
        assertEquals(120L, retrieved?.lastGeneration)
        assertEquals(42, retrieved?.scannedFileCount)
        assertEquals(ScanStatus.COMPLETED, retrieved?.status)

        val observed = repo.observeCheckpoint("src-1").first()
        assertEquals(120L, observed?.lastGeneration)

        repo.resetCheckpoint("src-1")
        assertNull(repo.getCheckpoint("src-1"))
    }

    @Test
    fun `re-registering legacy SAF source reuses existing ID and prevents duplicate rows`() = runBlocking {
        val dao = FakeStorageSourceDao()
        val repo = OfflineStorageSourceRepository(dao)

        val legacyUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        val legacyId = "saf_tree_${legacyUri.hashCode()}"
        val legacyEntity = StorageSourceEntity(
            id = legacyId,
            type = "SAF_DOCUMENT_TREE",
            uri = legacyUri,
            displayName = "Music Folder",
            isRemovable = false,
            isEnabled = true,
            lastScannedAt = 123456L
        )
        dao.upsertSource(legacyEntity)

        // User reopens/re-registers the tree, producing a new domain object with canonical SHA-256 ID
        val newRegistration = StorageSource.SafDocumentTree(
            treeUri = legacyUri,
            displayName = "Renamed Music Folder"
        )
        assertTrue(newRegistration.id != legacyId)
        assertTrue(newRegistration.id.startsWith("saf_tree_"))

        // Save re-registration
        repo.saveSource(newRegistration)

        // Must retain EXACTLY one source row in database
        val allSources = dao.getAllSources()
        assertEquals(1, allSources.size)

        // The persisted source must retain the existing legacy ID
        val persisted = allSources.first()
        assertEquals(legacyId, persisted.id)
        assertEquals("Renamed Music Folder", persisted.displayName)
        assertEquals(123456L, persisted.lastScannedAt)

        // Lookup via existing legacy ID retrieves it
        val byLegacy = repo.getSourceById(legacyId)
        assertNotNull(byLegacy)
        assertEquals(legacyId, byLegacy?.id)

        // Lookup via canonical SHA-256 ID alias also retrieves the same existing source
        val byCanonical = repo.getSourceById(newRegistration.id)
        assertNotNull(byCanonical)
        assertEquals(legacyId, byCanonical?.id)
    }

    @Test
    fun `distinct SAF trees are not conflated or merged`() = runBlocking {
        val dao = FakeStorageSourceDao()
        val repo = OfflineStorageSourceRepository(dao)

        val uri1 = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        val uri2 = "content://com.android.externalstorage.documents/tree/primary%3APodcasts"

        val tree1 = StorageSource.SafDocumentTree(treeUri = uri1, displayName = "Music")
        val tree2 = StorageSource.SafDocumentTree(treeUri = uri2, displayName = "Podcasts")

        repo.saveSource(tree1)
        repo.saveSource(tree2)

        val allSources = dao.getAllSources()
        assertEquals(2, allSources.size)
        assertTrue(allSources.any { it.id == tree1.id })
        assertTrue(allSources.any { it.id == tree2.id })
    }
}
