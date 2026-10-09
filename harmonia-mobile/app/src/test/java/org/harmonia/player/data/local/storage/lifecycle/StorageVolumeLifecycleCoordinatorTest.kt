package org.harmonia.player.data.local.storage.lifecycle

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.harmonia.player.data.local.dao.StorageSourceDao
import org.harmonia.player.data.local.entity.StorageSourceEntity
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.scanner.MediaReconciliationEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageVolumeLifecycleCoordinatorTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val fakeSources = mutableListOf<StorageSourceEntity>()
    private val scannedSources = mutableListOf<Pair<StorageSource, ScanMode>>()

    private val fakeDao = object : StorageSourceDao {
        override fun observeAllSources() = flowOf(fakeSources.toList())
        override fun observeEnabledSources() = flowOf(fakeSources.filter { it.isEnabled })
        override suspend fun getSourceById(id: String) = fakeSources.find { it.id == id }
        override suspend fun upsertSource(source: StorageSourceEntity) {
            fakeSources.removeAll { it.id == source.id }
            fakeSources.add(source)
        }
        override suspend fun upsertSources(sources: List<StorageSourceEntity>) {
            for (s in sources) upsertSource(s)
        }
        override suspend fun updateSource(source: StorageSourceEntity) { upsertSource(source) }
        override suspend fun setSourceEnabled(id: String, isEnabled: Boolean) {
            val s = getSourceById(id)
            if (s != null) upsertSource(s.copy(isEnabled = isEnabled))
        }
        override suspend fun deleteSourceById(id: String) { fakeSources.removeAll { it.id == id } }
    }

    private val fakeReconciliationEngine = object : MediaReconciliationEngine {
        override fun scanSource(source: StorageSource, mode: ScanMode) = kotlinx.coroutines.flow.flow {
            scannedSources.add(source to mode)
            emit(ScanProgress(sourceId = source.id))
        }
    }

    private val fakePlatformBridge = object : StoragePlatformBridge {
        val mountedVolumes = mutableSetOf("1234")
        val grantedTrees = mutableSetOf("content://com.android.externalstorage.documents/tree/primary%3AMusic")
        override fun isPlatformVolumeMounted(volumeUuid: String) = mountedVolumes.contains(volumeUuid)
        override fun isSafPermissionGranted(treeUri: String) = grantedTrees.contains(treeUri)
        override suspend fun checkVolumeReadiness(source: StorageSourceEntity) = true
    }

    private lateinit var coordinator: DefaultStorageVolumeLifecycleCoordinator

    @Before
    fun setup() {
        fakeSources.clear()
        scannedSources.clear()
        fakePlatformBridge.mountedVolumes.clear()
        fakePlatformBridge.mountedVolumes.add("1234")
        fakePlatformBridge.grantedTrees.clear()
        fakePlatformBridge.grantedTrees.add("content://com.android.externalstorage.documents/tree/primary%3AMusic")

        fakeSources.add(
            StorageSourceEntity(
                id = "mediastore_vol_1234",
                type = "MEDIA_STORE_VOLUME",
                uri = "external_1234:1234",
                displayName = "SanDisk SD Card",
                isRemovable = true,
                isEnabled = true
            )
        )
        fakeSources.add(
            StorageSourceEntity(
                id = "saf_tree_music",
                type = "SAF_DOCUMENT_TREE",
                uri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
                displayName = "Music Folder",
                isRemovable = false,
                isEnabled = true
            )
        )

        coordinator = DefaultStorageVolumeLifecycleCoordinator(
            storageSourceDao = fakeDao,
            reconciliationEngine = fakeReconciliationEngine,
            platformBridge = fakePlatformBridge,
            scope = testScope
        )
    }

    @Test
    fun unmount_marks_source_unavailable_without_changing_user_is_enabled() = testScope.runTest {
        // Eject SD Card
        coordinator.onVolumeUnmounted("1234")
        advanceUntilIdle()

        val state = coordinator.runtimeStates.value["mediastore_vol_1234"]
        assertEquals(false, state?.isAvailable)
        assertEquals(false, state?.isMounted)

        // Invariant: User configuration is NOT mutated
        val persistedSource = fakeDao.getSourceById("mediastore_vol_1234")
        assertEquals(true, persistedSource?.isEnabled)
    }

    @Test
    fun repeated_or_out_of_order_unmount_events_are_idempotent() = testScope.runTest {
        coordinator.onVolumeUnmounted("1234")
        advanceUntilIdle()
        val firstState = coordinator.runtimeStates.value["mediastore_vol_1234"]

        // Repeated unmount / eject
        coordinator.onVolumeUnmounted("1234")
        advanceUntilIdle()
        val secondState = coordinator.runtimeStates.value["mediastore_vol_1234"]

        assertEquals(firstState?.isAvailable, secondState?.isAvailable)
        assertEquals(firstState?.isMounted, secondState?.isMounted)
    }

    @Test
    fun mount_restores_availability_and_triggers_incremental_scan_for_enabled_source() = testScope.runTest {
        // Unmount first
        coordinator.onVolumeUnmounted("1234")
        advanceUntilIdle()

        // Mount volume
        coordinator.onVolumeMounted("1234")
        advanceUntilIdle()

        val state = coordinator.runtimeStates.value["mediastore_vol_1234"]
        assertEquals(true, state?.isAvailable)
        assertEquals(true, state?.isMounted)

        // Verified: Incremental scan was launched for this source
        assertEquals(1, scannedSources.size)
        assertEquals("mediastore_vol_1234", scannedSources[0].first.id)
        assertEquals(ScanMode.INCREMENTAL, scannedSources[0].second)
    }

    @Test
    fun mount_for_user_disabled_source_restores_availability_without_scanning() = testScope.runTest {
        // User explicitly disabled this source in settings
        fakeDao.setSourceEnabled("mediastore_vol_1234", false)

        coordinator.onVolumeMounted("1234")
        advanceUntilIdle()

        val state = coordinator.runtimeStates.value["mediastore_vol_1234"]
        assertEquals(true, state?.isAvailable)

        // Invariant: Disabled source does NOT automatically scan or play
        assertEquals(0, scannedSources.size)
    }

    @Test
    fun saf_permission_revocation_is_distinct_from_physical_removal() = testScope.runTest {
        val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        coordinator.onSafPermissionRevoked(treeUri)
        advanceUntilIdle()

        val state = coordinator.runtimeStates.value["saf_tree_music"]
        assertEquals(false, state?.isAvailable)
        assertEquals(false, state?.hasPermission)

        // User preference remains enabled
        val persisted = fakeDao.getSourceById("saf_tree_music")
        assertEquals(true, persisted?.isEnabled)

        // When permission is re-granted
        coordinator.onSafPermissionGranted(treeUri)
        advanceUntilIdle()
        val restored = coordinator.runtimeStates.value["saf_tree_music"]
        assertEquals(true, restored?.hasPermission)
        assertEquals(true, restored?.isAvailable)
    }

    @Test
    fun revalidate_all_sources_computes_fresh_runtime_states() = testScope.runTest {
        val states = coordinator.revalidateAllSources()

        assertTrue(states.containsKey("mediastore_vol_1234"))
        assertTrue(states.containsKey("saf_tree_music"))
        assertTrue(states["mediastore_vol_1234"]?.isMounted == true)
        assertTrue(states["saf_tree_music"]?.hasPermission == true)
    }
}
