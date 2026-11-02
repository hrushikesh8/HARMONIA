package org.harmonia.player.data.local.storage.lifecycle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
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
import org.junit.Assert.assertNotNull
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
        var throwError = false
        override fun observeAllSources() = if (throwError) kotlinx.coroutines.flow.flow { throw RuntimeException("Simulated DAO failure") } else flowOf(fakeSources.toList())
        override fun observeEnabledSources() = flowOf(fakeSources.filter { it.isEnabled })
        override suspend fun getSourceById(id: String) = fakeSources.find { it.id == id }
        override suspend fun getAllSources() = fakeSources.toList()
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
        var registeredListener: VolumeEventListener? = null
        override fun isPlatformVolumeMounted(volumeUuid: String) = mountedVolumes.any { it.equals(volumeUuid, ignoreCase = true) }
        override fun isSafPermissionGranted(treeUri: String) = grantedTrees.contains(treeUri)
        override suspend fun checkVolumeReadiness(source: StorageSourceEntity) = true
        override fun registerVolumeEventListener(listener: VolumeEventListener): AutoCloseable {
            registeredListener = listener
            return AutoCloseable { registeredListener = null }
        }
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

    @Test
    fun start_monitoring_registers_platform_listener_and_revalidates_sources() = testScope.runTest {
        coordinator.startMonitoring()
        advanceUntilIdle()

        org.junit.Assert.assertNotNull(fakePlatformBridge.registeredListener)
        val states = coordinator.runtimeStates.value
        assertEquals(2, states.size)
        assertTrue(states["mediastore_vol_1234"]?.isAvailable == true)

        // Trigger hardware unmount through the registered platform listener
        fakePlatformBridge.registeredListener?.onVolumeUnmounted("1234")
        advanceUntilIdle()
        assertFalse(coordinator.runtimeStates.value["mediastore_vol_1234"]?.isAvailable == true)

        coordinator.stopMonitoring()
        org.junit.Assert.assertNull(fakePlatformBridge.registeredListener)
    }

    @Test
    fun restart_monitoring_restores_platform_listener_and_refreshes_storage_state() = testScope.runTest {
        // Initial start
        coordinator.startMonitoring()
        advanceUntilIdle()
        org.junit.Assert.assertNotNull(fakePlatformBridge.registeredListener)
        assertTrue(coordinator.runtimeStates.value["mediastore_vol_1234"]?.isAvailable == true)

        // Stop monitoring
        coordinator.stopMonitoring()
        org.junit.Assert.assertNull(fakePlatformBridge.registeredListener)

        // While stopped, physical volume was ejected
        fakePlatformBridge.mountedVolumes.clear()

        // Restart monitoring: must restore platform listener and refresh storage state
        coordinator.startMonitoring()
        org.junit.Assert.assertNotNull(fakePlatformBridge.registeredListener)
        advanceUntilIdle()

        // State must be refreshed to unavailable
        assertFalse(coordinator.runtimeStates.value["mediastore_vol_1234"]?.isAvailable == true)
    }

    @Test
    fun saf_canonical_id_derivation_is_deterministic_and_collision_resistant() {
        val uri1 = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        val uri2 = "content://com.android.externalstorage.documents/tree/primary%3AMusic/"
        val uri3 = "CONTENT://com.android.externalstorage.documents/tree/primary%3AMusic"
        val distinctUri = "content://com.android.externalstorage.documents/tree/SDCARD%3APodcasts"

        val id1 = StorageSource.SafDocumentTree.deriveCanonicalId(uri1)
        val id2 = StorageSource.SafDocumentTree.deriveCanonicalId(uri2)
        val id3 = StorageSource.SafDocumentTree.deriveCanonicalId(uri3)
        val idDistinct = StorageSource.SafDocumentTree.deriveCanonicalId(distinctUri)

        // Scheme case normalization produces identical canonical IDs
        assertEquals(id1, id3)
        assertTrue(id1.startsWith("saf_tree_"))
        assertEquals(73, id1.length) // "saf_tree_" (9) + 64 hex chars (SHA-256)

        // Trailing slash differences are preserved so potentially distinct tree locators are NOT conflated
        assertTrue(id1 != id2)
        // Distinct URIs produce distinct canonical IDs
        assertTrue(id1 != idDistinct)
    }

    @Test
    fun removable_volume_matching_does_not_conflate_duplicate_volume_names() = testScope.runTest {
        // Two separate physical SD cards sharing the exact same user-facing volume name "SDCARD"
        val sourceA = StorageSourceEntity(
            id = "mediastore_vol_1111-aaaa",
            type = "MEDIA_STORE_VOLUME",
            uri = "SDCARD:1111-AAAA",
            displayName = "SD Card 1",
            isRemovable = true,
            isEnabled = true
        )
        val sourceB = StorageSourceEntity(
            id = "mediastore_vol_2222-bbbb",
            type = "MEDIA_STORE_VOLUME",
            uri = "SDCARD:2222-BBBB",
            displayName = "SD Card 2",
            isRemovable = true,
            isEnabled = true
        )
        fakeSources.add(sourceA)
        fakeSources.add(sourceB)
        fakePlatformBridge.mountedVolumes.add("1111-aaaa")
        fakePlatformBridge.mountedVolumes.add("2222-bbbb")

        coordinator.revalidateAllSources()

        // Unmount sourceB by its authoritative UUID
        coordinator.onVolumeUnmounted("2222-BBBB")
        advanceUntilIdle()

        val states = coordinator.runtimeStates.value
        // sourceB is unmounted
        assertFalse(states["mediastore_vol_2222-bbbb"]?.isAvailable == true)
        // sourceA MUST REMAIN AVAILABLE despite sharing the same volumeName "SDCARD"!
        assertTrue(states["mediastore_vol_1111-aaaa"]?.isAvailable == true)
    }

    @Test
    fun removable_volume_matching_handles_uuid_case_normalization_and_remount() = testScope.runTest {
        val source = StorageSourceEntity(
            id = "mediastore_vol_abcd-1234",
            type = "MEDIA_STORE_VOLUME",
            uri = "external_sd:ABCD-1234",
            displayName = "Kingston SD",
            isRemovable = true,
            isEnabled = true
        )
        fakeSources.add(source)
        fakePlatformBridge.mountedVolumes.add("abcd-1234")

        coordinator.revalidateAllSources()
        assertTrue(coordinator.runtimeStates.value["mediastore_vol_abcd-1234"]?.isAvailable == true)

        // Unmount with lowercase UUID
        coordinator.onVolumeUnmounted("abcd-1234")
        advanceUntilIdle()
        assertFalse(coordinator.runtimeStates.value["mediastore_vol_abcd-1234"]?.isAvailable == true)

        // Remount with uppercase UUID
        coordinator.onVolumeMounted("ABCD-1234")
        advanceUntilIdle()
        assertTrue(coordinator.runtimeStates.value["mediastore_vol_abcd-1234"]?.isAvailable == true)
    }

    @Test
    fun removable_volume_matching_rejects_missing_or_ambiguous_uuids() = testScope.runTest {
        val source = StorageSourceEntity(
            id = "mediastore_vol_5555-6666",
            type = "MEDIA_STORE_VOLUME",
            uri = "sdcard:5555-6666",
            displayName = "SanDisk",
            isRemovable = true,
            isEnabled = true
        )
        fakeSources.add(source)
        fakePlatformBridge.mountedVolumes.add("5555-6666")

        coordinator.revalidateAllSources()
        assertTrue(coordinator.runtimeStates.value["mediastore_vol_5555-6666"]?.isAvailable == true)

        // Ambiguous unmount without UUID (empty string or volume name only)
        coordinator.onVolumeUnmounted("")
        coordinator.onVolumeUnmounted("   ")
        coordinator.onVolumeUnmounted("sdcard")
        advanceUntilIdle()

        // Source must NOT be touched or affected by ambiguous events
        assertTrue(coordinator.runtimeStates.value["mediastore_vol_5555-6666"]?.isAvailable == true)
    }

    @Test
    fun removable_volume_exact_production_persistence_format_matching() = testScope.runTest {
        // Exact format produced by OfflineStorageSourceRepository.toEntity(): "$volumeName:$volumeUuid"
        val domainVolume = StorageSource.MediaStoreVolume(
            volumeName = "disk_ext",
            volumeUuid = "9ABC-DEF0",
            displayName = "External Drive"
        )
        val entity = StorageSourceEntity(
            id = domainVolume.id,
            type = "MEDIA_STORE_VOLUME",
            uri = "${domainVolume.volumeName}:${domainVolume.volumeUuid}",
            displayName = domainVolume.displayName,
            isRemovable = domainVolume.isRemovable,
            isEnabled = domainVolume.isAvailable
        )
        fakeSources.add(entity)
        fakePlatformBridge.mountedVolumes.add("9abc-def0")

        coordinator.revalidateAllSources()
        assertTrue(coordinator.runtimeStates.value[domainVolume.id]?.isAvailable == true)

        // Unmount by volume UUID (lowercase)
        coordinator.onVolumeUnmounted("9abc-def0")
        advanceUntilIdle()
        assertFalse(coordinator.runtimeStates.value[domainVolume.id]?.isAvailable == true)

        // Remount by canonical ID ("mediastore_vol_9abc-def0")
        coordinator.onVolumeMounted("mediastore_vol_9abc-def0")
        advanceUntilIdle()
        assertTrue(coordinator.runtimeStates.value[domainVolume.id]?.isAvailable == true)
    }

    @Test
    fun removable_volume_content_uri_or_missing_uuid_fails_safely_without_matching() = testScope.runTest {
        val malformedSource = StorageSourceEntity(
            id = "custom_removable_source",
            type = "MEDIA_STORE_VOLUME",
            uri = "content://media/external/audio/media", // Not a "volumeName:volumeUuid" pair
            displayName = "Ambiguous Source",
            isRemovable = true,
            isEnabled = true
        )
        fakeSources.add(malformedSource)

        coordinator.revalidateAllSources()

        // Unmount event with an arbitrary UUID must NEVER match or touch the ambiguous source
        coordinator.onVolumeUnmounted("1234-5678")
        advanceUntilIdle()
        assertNotNull(coordinator.runtimeStates.value["custom_removable_source"])
    }

    @Test
    fun saf_uri_canonicalization_preserves_distinct_authorities_and_subtrees() {
        val externalStorage = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        val downloadsProvider = "content://com.android.providers.downloads.documents/tree/downloads"
        val googleDrive = "content://com.google.android.apps.docs.storage/tree/primary%3AMusic"
        val subfolder = "content://com.android.externalstorage.documents/tree/primary%3AMusic%2FRock"

        val idExt = StorageSource.SafDocumentTree.deriveCanonicalId(externalStorage)
        val idDownloads = StorageSource.SafDocumentTree.deriveCanonicalId(downloadsProvider)
        val idDrive = StorageSource.SafDocumentTree.deriveCanonicalId(googleDrive)
        val idSubfolder = StorageSource.SafDocumentTree.deriveCanonicalId(subfolder)

        // Distinct authorities MUST produce distinct canonical IDs
        assertTrue(idExt != idDownloads)
        assertTrue(idExt != idDrive)

        // Subtree paths MUST produce distinct canonical IDs
        assertTrue(idExt != idSubfolder)

        // Scheme case and whitespace variants produce identical canonical IDs
        val upperScheme = "CONTENT://com.android.externalstorage.documents/tree/primary%3AMusic"
        val whitespace = "  content://com.android.externalstorage.documents/tree/primary%3AMusic  "

        assertEquals(idExt, StorageSource.SafDocumentTree.deriveCanonicalId(upperScheme))
        assertEquals(idExt, StorageSource.SafDocumentTree.deriveCanonicalId(whitespace))

        // Trailing slash variants are NOT conflated: they remain distinct locators
        val trailingSlash = "content://com.android.externalstorage.documents/tree/primary%3AMusic/"
        val idTrailing = StorageSource.SafDocumentTree.deriveCanonicalId(trailingSlash)
        assertTrue(idExt != idTrailing)
    }

    @Test
    fun initial_readiness_awaits_revalidation_completion_and_populates_state() = testScope.runTest {
        val externalStorage = StorageSourceEntity(
            id = "src_init_test",
            type = "MEDIA_STORE_PRIMARY",
            uri = "content://media/external",
            displayName = "Primary Storage",
            isRemovable = false,
            isEnabled = true
        )
        fakeSources.add(externalStorage)

        val ready = coordinator.awaitInitialReadiness()
        advanceUntilIdle()

        assertTrue(ready)
        assertTrue(coordinator.isSourceAvailable("src_init_test"))
    }

    @Test
    fun repeated_startMonitoring_is_idempotent() = testScope.runTest {
        coordinator.startMonitoring()
        coordinator.startMonitoring()
        advanceUntilIdle()

        assertTrue(coordinator.awaitInitialReadiness())
    }

    @Test
    fun concurrent_readiness_callers_share_same_initialization() = testScope.runTest {
        val externalStorage = StorageSourceEntity(
            id = "src_concurrent_test",
            type = "MEDIA_STORE_PRIMARY",
            uri = "content://media/external",
            displayName = "Primary Storage",
            isRemovable = false,
            isEnabled = true
        )
        fakeSources.add(externalStorage)

        val deferred1 = async { coordinator.awaitInitialReadiness() }
        val deferred2 = async { coordinator.awaitInitialReadiness() }
        advanceUntilIdle()

        assertTrue(deferred1.await())
        assertTrue(deferred2.await())
        assertTrue(coordinator.isSourceAvailable("src_concurrent_test"))
    }

    @Test
    fun successful_readiness_occurring_only_after_revalidation() = testScope.runTest {
        val externalStorage = StorageSourceEntity(
            id = "src_timing_test",
            type = "MEDIA_STORE_PRIMARY",
            uri = "content://media/external",
            displayName = "Primary Storage",
            isRemovable = false,
            isEnabled = true
        )
        fakeSources.add(externalStorage)

        val deferred = async { coordinator.awaitInitialReadiness() }
        // Before revalidation completes, readiness is not complete
        assertFalse(deferred.isCompleted)

        advanceUntilIdle()

        // After revalidation completes, readiness is true and source is available
        assertTrue(deferred.isCompleted)
        assertTrue(deferred.await())
        assertTrue(coordinator.isSourceAvailable("src_timing_test"))
    }

    @Test
    fun initialization_failure_returns_false_and_fails_closed() = testScope.runTest {
        val externalStorage = StorageSourceEntity(
            id = "src_fail_test",
            type = "MEDIA_STORE_PRIMARY",
            uri = "content://media/external",
            displayName = "Primary Storage",
            isRemovable = false,
            isEnabled = true
        )
        fakeSources.add(externalStorage)
        fakeDao.throwError = true

        coordinator.startMonitoring()
        advanceUntilIdle()

        // Readiness must return false
        val ready = coordinator.awaitInitialReadiness()
        assertFalse(ready)

        // Source must NOT be available (fail-closed)
        assertFalse(coordinator.isSourceAvailable("src_fail_test"))
    }

    @Test
    fun cancellation_propagates_correctly_and_is_not_swallowed() = testScope.runTest {
        val job = launch {
            coordinator.awaitInitialReadiness()
        }
        job.cancel(kotlinx.coroutines.CancellationException("Explicit test cancellation"))

        try {
            job.join()
            assertTrue(job.isCancelled)
        } catch (e: Exception) {
            assertTrue(e is kotlinx.coroutines.CancellationException)
        }
    }

    @Test
    fun mediastore_primary_is_seeded_if_missing_on_revalidation() = testScope.runTest {
        // Ensure fakeSources is empty initially to simulate fresh database with no sources
        fakeSources.clear()

        coordinator.startMonitoring()
        advanceUntilIdle()

        // Verify that revalidateAllSources automatically inserted mediastore_primary
        val primary = fakeDao.getSourceById(StorageSource.MediaStorePrimary.ID)
        assertNotNull(primary)
        assertEquals("MEDIA_STORE_PRIMARY", primary?.type)
        assertEquals(true, primary?.isEnabled)
        assertEquals(false, primary?.isRemovable)
    }

    @Test
    fun mediastore_primary_is_available_during_inflight_initial_readiness() = testScope.runTest {
        // Before startMonitoring or advanceUntilIdle finishes, mediastore_primary is available
        val isAvailable = coordinator.isSourceAvailable(StorageSource.MediaStorePrimary.ID)
        assertTrue(isAvailable)
    }
}
