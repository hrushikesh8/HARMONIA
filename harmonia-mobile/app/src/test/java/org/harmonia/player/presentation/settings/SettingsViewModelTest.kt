package org.harmonia.player.presentation.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.harmonia.player.domain.model.storage.permission.SafPermissionDelegate
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.domain.repository.StorageSourceRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.domain.scanner.MediaReconciliationEngine
import org.harmonia.player.domain.scanner.SourceScanCoordinator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeTrackRepository : TrackRepository {
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    override fun observeAllTracks(): Flow<List<Track>> = tracks
    override fun observeOfflineTracks(): Flow<List<Track>> = tracks
    override suspend fun getTrackById(id: String): Track? = tracks.value.find { it.id == id }
    override fun searchTracks(query: String): Flow<List<Track>> = tracks.map { list ->
        list.filter { it.title.contains(query, ignoreCase = true) }
    }
    override suspend fun upsertTracks(tracks: List<Track>) { this.tracks.value = tracks }
    override suspend fun deleteTrack(id: String) { this.tracks.value = this.tracks.value.filter { it.id != id } }
}

private class FakeStorageSourceRepository : StorageSourceRepository {
    val sourcesFlow = MutableStateFlow<Map<String, StorageSource>>(emptyMap())
    var throwOnSetEnabled: Boolean = false
    var throwOnSave: Boolean = false
    var onSaveSource: (suspend (StorageSource) -> Unit)? = null

    override fun observeAllSources(): Flow<List<StorageSource>> = sourcesFlow.map { it.values.toList() }

    override fun observeEnabledSources(): Flow<List<StorageSource>> = sourcesFlow.map { map ->
        map.values.filter { it.isAvailable }
    }

    override suspend fun getSourceById(id: String): StorageSource? = sourcesFlow.value[id]

    override suspend fun saveSource(source: StorageSource) {
        onSaveSource?.invoke(source)
        if (throwOnSave) {
            throw IllegalStateException("Database write failed")
        }
        val current = sourcesFlow.value.toMutableMap()
        // Duplicate detection by canonical ID or tree URI for SAF
        val existingKey = current.keys.firstOrNull { key ->
            val existing = current[key] ?: return@firstOrNull false
            if (source is StorageSource.SafDocumentTree && existing is StorageSource.SafDocumentTree) {
                StorageSource.SafDocumentTree.normalizeTreeUri(existing.treeUri) ==
                        StorageSource.SafDocumentTree.normalizeTreeUri(source.treeUri)
            } else {
                key == source.id
            }
        }
        val targetId = existingKey ?: source.id
        current[targetId] = source
        sourcesFlow.value = current
    }

    override suspend fun setSourceEnabled(id: String, isEnabled: Boolean) {
        if (throwOnSetEnabled) {
            throw IllegalStateException("Failed to update database record")
        }
        val current = sourcesFlow.value.toMutableMap()
        val s = current[id] ?: return
        current[id] = when (s) {
            is StorageSource.MediaStorePrimary -> s.copy(isAvailable = isEnabled)
            is StorageSource.MediaStoreVolume -> s.copy(isAvailable = isEnabled)
            is StorageSource.SafDocumentTree -> s.copy(isAvailable = isEnabled)
            is StorageSource.InternalSyncStorage -> s.copy(isAvailable = isEnabled)
        }
        sourcesFlow.value = current
    }

    override suspend fun deleteSource(id: String) {
        val current = sourcesFlow.value.toMutableMap()
        current.remove(id)
        sourcesFlow.value = current
    }
}

private class FakeStorageVolumeLifecycleCoordinator : StorageVolumeLifecycleCoordinator {
    val _runtimeStates = MutableStateFlow<Map<String, SourceRuntimeState>>(emptyMap())
    override val runtimeStates: StateFlow<Map<String, SourceRuntimeState>> = _runtimeStates

    val grantedSafUris = mutableListOf<String>()
    var defaultSourceAvailability = true

    override fun isSourceAvailable(sourceId: String): Boolean {
        val state = _runtimeStates.value[sourceId]
        return state?.isAvailable ?: defaultSourceAvailability
    }

    override fun onVolumeMounted(volumeId: String) {
        val current = _runtimeStates.value.toMutableMap()
        val existing = current[volumeId]
        current[volumeId] = SourceRuntimeState(
            sourceId = volumeId,
            isAvailable = true,
            isMounted = true,
            hasPermission = existing?.hasPermission ?: true
        )
        _runtimeStates.value = current
    }

    override fun onVolumeUnmounted(volumeId: String) {
        val current = _runtimeStates.value.toMutableMap()
        val existing = current[volumeId]
        current[volumeId] = SourceRuntimeState(
            sourceId = volumeId,
            isAvailable = false,
            isMounted = false,
            hasPermission = existing?.hasPermission ?: true
        )
        _runtimeStates.value = current
    }

    override fun onSafPermissionRevoked(treeUri: String) {
        val id = StorageSource.SafDocumentTree.deriveCanonicalId(treeUri)
        val current = _runtimeStates.value.toMutableMap()
        current[id] = SourceRuntimeState(
            sourceId = id,
            isAvailable = false,
            isMounted = true,
            hasPermission = false
        )
        _runtimeStates.value = current
    }

    override fun onSafPermissionGranted(treeUri: String) {
        grantedSafUris.add(treeUri)
        val id = StorageSource.SafDocumentTree.deriveCanonicalId(treeUri)
        val current = _runtimeStates.value.toMutableMap()
        current[id] = SourceRuntimeState(
            sourceId = id,
            isAvailable = true,
            isMounted = true,
            hasPermission = true
        )
        _runtimeStates.value = current
    }

    override suspend fun revalidateAllSources(): Map<String, SourceRuntimeState> = _runtimeStates.value
    override suspend fun revalidateSource(source: StorageSource): SourceRuntimeState {
        return _runtimeStates.value[source.id] ?: SourceRuntimeState(
            sourceId = source.id,
            isAvailable = true,
            isMounted = true,
            hasPermission = true
        )
    }
    override fun startMonitoring() {}
    override fun stopMonitoring() {}
}

private class FakeSafPermissionDelegate : SafPermissionDelegate {
    val persistedPermissions = mutableSetOf<String>()
    val takenPermissions = mutableListOf<String>()
    val releasedPermissions = mutableListOf<String>()
    var throwOnTake: Boolean = false
    var throwOnRelease: Boolean = false

    override fun takePersistableReadPermission(treeUri: String) {
        if (throwOnTake) {
            throw SecurityException("Permission denied by platform")
        }
        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(treeUri)
        takenPermissions.add(normalized)
        persistedPermissions.add(normalized)
    }

    override fun releasePersistableReadPermission(treeUri: String) {
        if (throwOnRelease) {
            throw IllegalStateException("Failed to release permission grant")
        }
        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(treeUri)
        releasedPermissions.add(normalized)
        persistedPermissions.remove(normalized)
    }

    override fun hasPersistedReadPermission(treeUri: String): Boolean {
        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(treeUri)
        return persistedPermissions.contains(normalized)
    }
}

private class FakeMediaReconciliationEngine(
    val coordinator: SourceScanCoordinator = SourceScanCoordinator()
) : MediaReconciliationEngine {
    val scannedSources = mutableListOf<StorageSource>()
    var scanModeUsed: ScanMode? = null
    var shouldFail = false
    var failureErrorMessage: String? = null
    var simulatePartialFailure = false
    var delayScan: Boolean = false

    override fun scanSource(source: StorageSource, mode: ScanMode): Flow<ScanProgress> = flow {
        coordinator.withSourceLock(source.id) {
            scannedSources.add(source)
            scanModeUsed = mode
            emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.IDLE))

            if (shouldFail) {
                emit(
                    ScanProgress(
                        sourceId = source.id,
                        status = ScanProgressStatus.FAILED,
                        errorMessage = failureErrorMessage ?: "Scan engine failure"
                    )
                )
                return@withSourceLock
            }

            emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.DISCOVERING))

            if (simulatePartialFailure) {
                emit(
                    ScanProgress(
                        sourceId = source.id,
                        status = ScanProgressStatus.PARTIAL,
                        importedCount = 5,
                        failedCount = 2,
                        errorMessage = "2 files could not be indexed"
                    )
                )
            } else {
                emit(
                    ScanProgress(
                        sourceId = source.id,
                        status = ScanProgressStatus.COMPLETED,
                        importedCount = 10,
                        updatedCount = 2,
                        unchangedCount = 8
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var trackRepo: FakeTrackRepository
    private lateinit var sourceRepo: FakeStorageSourceRepository
    private lateinit var coordinator: FakeStorageVolumeLifecycleCoordinator
    private lateinit var engine: FakeMediaReconciliationEngine
    private lateinit var safDelegate: FakeSafPermissionDelegate

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        trackRepo = FakeTrackRepository()
        sourceRepo = FakeStorageSourceRepository()
        coordinator = FakeStorageVolumeLifecycleCoordinator()
        engine = FakeMediaReconciliationEngine()
        safDelegate = FakeSafPermissionDelegate()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        delegate: SafPermissionDelegate = safDelegate
    ): SettingsViewModel {
        return SettingsViewModel(
            trackRepository = trackRepo,
            storageSourceRepository = sourceRepo,
            storageVolumeLifecycleCoordinator = coordinator,
            mediaReconciliationEngine = engine,
            safPermissionDelegate = delegate,
            ioDispatcher = testDispatcher
        )
    }

    private fun TestScope.subscribeUiState(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `1 existing sources are exposed reactively`() = runTest(testDispatcher) {
        val primary = StorageSource.MediaStorePrimary()
        val saf = StorageSource.SafDocumentTree(
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            displayName = "Music Folder"
        )
        sourceRepo.saveSource(primary)
        sourceRepo.saveSource(saf)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.storageSources.size)
        assertEquals(primary.id, state.storageSources[0].id)
        assertEquals(saf.id, state.storageSources[1].id)

        // Adding a third source reactively updates the UI state
        val removable = StorageSource.MediaStoreVolume(
            volumeName = "SD_CARD",
            volumeUuid = "1234-5678",
            displayName = "SanDisk SD"
        )
        sourceRepo.saveSource(removable)
        advanceUntilIdle()

        assertEquals(3, viewModel.uiState.value.storageSources.size)
    }

    @Test
    fun `2 persistent enabled preference is displayed correctly`() = runTest(testDispatcher) {
        val enabledSource = StorageSource.MediaStorePrimary(isAvailable = true)
        val disabledSource = StorageSource.SafDocumentTree(
            treeUri = "content://documents/tree/audio",
            displayName = "Audio Folder",
            isAvailable = false
        )
        sourceRepo.saveSource(enabledSource)
        sourceRepo.saveSource(disabledSource)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val items = viewModel.uiState.value.storageSources
        assertTrue(items.first { it.id == enabledSource.id }.isEnabled)
        assertFalse(items.first { it.id == disabledSource.id }.isEnabled)
    }

    @Test
    fun `3 toggling a source persists the correct value`() = runTest(testDispatcher) {
        val source = StorageSource.MediaStorePrimary(isAvailable = true)
        sourceRepo.saveSource(source)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        // Toggle to disabled
        viewModel.toggleSource(source.id, false)
        advanceUntilIdle()

        assertFalse(sourceRepo.sourcesFlow.value[source.id]!!.isAvailable)
        assertFalse(viewModel.uiState.value.storageSources.first { it.id == source.id }.isEnabled)

        // Toggle back to enabled
        viewModel.toggleSource(source.id, true)
        advanceUntilIdle()

        assertTrue(sourceRepo.sourcesFlow.value[source.id]!!.isAvailable)
        assertTrue(viewModel.uiState.value.storageSources.first { it.id == source.id }.isEnabled)
    }

    @Test
    fun `4 runtime availability remains distinct from the enabled preference`() = runTest(testDispatcher) {
        val volume = StorageSource.MediaStoreVolume(
            volumeName = "USB",
            volumeUuid = "abcd-1234",
            displayName = "USB Drive",
            isAvailable = true // User preference: ENABLED
        )
        sourceRepo.saveSource(volume)

        // Runtime state: DISCONNECTED (not mounted, not available)
        coordinator._runtimeStates.value = mapOf(
            volume.id to SourceRuntimeState(
                sourceId = volume.id,
                isAvailable = false,
                isMounted = false,
                hasPermission = true
            )
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val item = viewModel.uiState.value.storageSources.first { it.id == volume.id }
        assertTrue("User preference must remain enabled", item.isEnabled)
        assertFalse("Runtime availability must be false when unmounted", item.isRuntimeAvailable)
        assertEquals("Disconnected", item.runtimeStatusDescription)
    }

    @Test
    fun `5 an enabled but disconnected source is displayed truthfully`() = runTest(testDispatcher) {
        val volume = StorageSource.MediaStoreVolume(
            volumeName = "SD_CARD",
            volumeUuid = "9999-0000",
            displayName = "MicroSD",
            isAvailable = true
        )
        sourceRepo.saveSource(volume)
        coordinator.onVolumeUnmounted(volume.id)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val item = viewModel.uiState.value.storageSources.first { it.id == volume.id }
        assertTrue(item.isEnabled)
        assertEquals("Disconnected", item.runtimeStatusDescription)
        assertFalse(item.isRuntimeAvailable)
    }

    @Test
    fun `6 a mounted but disabled source remains disabled`() = runTest(testDispatcher) {
        val volume = StorageSource.MediaStoreVolume(
            volumeName = "SD_CARD",
            volumeUuid = "9999-0000",
            displayName = "MicroSD",
            isAvailable = false // User disabled
        )
        sourceRepo.saveSource(volume)
        // Physically mounted
        coordinator.onVolumeMounted(volume.id)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val item = viewModel.uiState.value.storageSources.first { it.id == volume.id }
        assertFalse("User preference must remain disabled", item.isEnabled)
        assertTrue("Physical volume is mounted and available", item.isRuntimeAvailable)
        assertEquals("Connected", item.runtimeStatusDescription)
    }

    @Test
    fun `7 toggle failures are surfaced correctly`() = runTest(testDispatcher) {
        val source = StorageSource.MediaStorePrimary()
        sourceRepo.saveSource(source)
        sourceRepo.throwOnSetEnabled = true

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.toggleSource(source.id, false)
        advanceUntilIdle()

        val error = viewModel.uiState.value.scanErrorMessage
        assertNotNull(error)
        assertTrue(error!!.contains("Failed to update source"))
    }

    @Test
    fun `8 manual scan invokes the appropriate enabled and accessible sources`() = runTest(testDispatcher) {
        val enabledAndMounted = StorageSource.MediaStorePrimary(isAvailable = true)
        val disabledSource = StorageSource.SafDocumentTree(
            treeUri = "content://saf/tree/disabled",
            displayName = "Disabled SAF",
            isAvailable = false
        )
        val enabledButDisconnected = StorageSource.MediaStoreVolume(
            volumeName = "USB",
            volumeUuid = "usb-1",
            displayName = "Disconnected USB",
            isAvailable = true
        )

        sourceRepo.saveSource(enabledAndMounted)
        sourceRepo.saveSource(disabledSource)
        sourceRepo.saveSource(enabledButDisconnected)

        // Coordinator: USB is disconnected
        coordinator._runtimeStates.value = mapOf(
            enabledAndMounted.id to SourceRuntimeState(enabledAndMounted.id, isAvailable = true, isMounted = true, hasPermission = true),
            enabledButDisconnected.id to SourceRuntimeState(enabledButDisconnected.id, isAvailable = false, isMounted = false, hasPermission = true)
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.scanLibrary()
        advanceUntilIdle()

        // Only the enabled and accessible source should have been scanned
        assertEquals(1, engine.scannedSources.size)
        assertEquals(enabledAndMounted.id, engine.scannedSources[0].id)
        assertEquals(ScanMode.INCREMENTAL, engine.scanModeUsed)
    }

    @Test
    fun `9 duplicate scan requests do not cause overlapping scans`() = runTest(testDispatcher) {
        val source = StorageSource.MediaStorePrimary(isAvailable = true)
        sourceRepo.saveSource(source)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        // Trigger two scans concurrently
        viewModel.scanLibrary()
        viewModel.scanLibrary()
        advanceUntilIdle()

        // Engine should only have received 1 scan invocation for this source
        assertEquals(1, engine.scannedSources.size)
    }

    @Test
    fun `10 scan success, partial failure, and total failure are represented correctly`() = runTest(testDispatcher) {
        val source = StorageSource.MediaStorePrimary(isAvailable = true)
        sourceRepo.saveSource(source)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        // 10A: Scan success
        viewModel.scanLibrary()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isScanning)
        assertNotNull(viewModel.uiState.value.scanStatusMessage)
        assertTrue(viewModel.uiState.value.scanStatusMessage!!.contains("Scan complete"))
        assertNull(viewModel.uiState.value.scanErrorMessage)

        // 10B: Partial failure
        engine.simulatePartialFailure = true
        viewModel.scanLibrary()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isScanning)
        assertTrue(viewModel.uiState.value.scanStatusMessage!!.contains("Scan complete"))

        // 10C: Total failure
        engine.simulatePartialFailure = false
        engine.shouldFail = true
        engine.failureErrorMessage = "MediaStore content provider died"
        viewModel.scanLibrary()
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isScanning)
        assertNotNull(viewModel.uiState.value.scanErrorMessage)
        assertTrue(viewModel.uiState.value.scanErrorMessage!!.contains("MediaStore content provider died"))
    }

    @Test
    fun `11 scan failure does not delete library data or alter playback state`() = runTest(testDispatcher) {
        val existingTracks = listOf(
            Track(id = "trk1", title = "Everything In Its Right Place", durationSec = 251.0),
            Track(id = "trk2", title = "Kid A", durationSec = 284.0)
        )
        trackRepo.tracks.value = existingTracks

        val source = StorageSource.MediaStorePrimary(isAvailable = true)
        sourceRepo.saveSource(source)

        engine.shouldFail = true
        engine.failureErrorMessage = "Storage read error"

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.scanLibrary()
        advanceUntilIdle()

        // Verify tracks were not deleted
        assertEquals(2, trackRepo.tracks.value.size)
        assertEquals("trk1", trackRepo.tracks.value[0].id)
        assertEquals("trk2", trackRepo.tracks.value[1].id)
    }

    @Test
    fun `12 user cancellation is a no-op`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        // When user cancels OpenDocumentTree, launcher receives null Uri, registerSafFolder is not called
        val initialSources = viewModel.uiState.value.storageSources.size
        assertEquals(0, initialSources)
        assertEquals(0, sourceRepo.sourcesFlow.value.size)
    }

    @Test
    fun `13 a successful selection registers the source only after permission persistence succeeds`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val rawUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic"
        viewModel.registerSafFolder(rawUri, "Music")
        advanceUntilIdle()

        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(rawUri)
        val canonicalId = StorageSource.SafDocumentTree.deriveCanonicalId(rawUri)

        // Verifies permission was taken before source was saved
        assertTrue(safDelegate.takenPermissions.contains(normalized))
        val registered = sourceRepo.sourcesFlow.value[canonicalId]
        assertNotNull(registered)
        assertTrue(registered is StorageSource.SafDocumentTree)
        assertEquals("Music", registered!!.displayName)
        assertTrue(registered.isAvailable)
        assertTrue(coordinator.grantedSafUris.contains(normalized))
    }

    @Test
    fun `14 duplicate folder selection does not create duplicate records`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val uri = "content://com.android.externalstorage.documents/tree/primary%3APodcasts"
        viewModel.registerSafFolder(uri, "Podcasts")
        advanceUntilIdle()
        assertEquals(1, sourceRepo.sourcesFlow.value.size)

        // Select the same folder again
        viewModel.registerSafFolder(uri, "Podcasts Renamed")
        advanceUntilIdle()

        // No duplicate record added
        assertEquals(1, sourceRepo.sourcesFlow.value.size)
        val source = sourceRepo.sourcesFlow.value.values.first()
        assertEquals("Podcasts Renamed", source.displayName)
    }

    @Test
    fun `15 persisted permission failures are handled`() = runTest(testDispatcher) {
        safDelegate.throwOnTake = true
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val rawUri = "content://com.android.externalstorage.documents/tree/primary%3APics"
        viewModel.registerSafFolder(rawUri, "Pictures")
        advanceUntilIdle()

        // When permission persistence throws SecurityException, no source is saved and error is surfaced
        assertEquals(0, sourceRepo.sourcesFlow.value.size)
        assertEquals(0, safDelegate.persistedPermissions.size)
        assertNotNull(viewModel.uiState.value.scanErrorMessage)
        assertTrue(viewModel.uiState.value.scanErrorMessage!!.contains("Permission denied by platform"))
    }

    @Test
    fun `16 registration failure triggers rollback of newly acquired permission`() = runTest(testDispatcher) {
        sourceRepo.throwOnSave = true

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val uri = "content://valid/tree"
        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(uri)
        viewModel.registerSafFolder(uri, "My Tree")
        advanceUntilIdle()

        assertEquals(0, sourceRepo.sourcesFlow.value.size)
        assertTrue("Permission was taken initially", safDelegate.takenPermissions.contains(normalized))
        assertTrue("Permission was rolled back on failure", safDelegate.releasedPermissions.contains(normalized))
        assertFalse("Permission is not retained", safDelegate.hasPersistedReadPermission(normalized))
        val error = viewModel.uiState.value.scanErrorMessage
        assertNotNull(error)
        assertTrue(error!!.contains("Failed to register folder"))
    }

    @Test
    fun `17 source list and status are rendered correctly`() = runTest(testDispatcher) {
        val primary = StorageSource.MediaStorePrimary()
        val volume = StorageSource.MediaStoreVolume("EXT", "ext-vol", "External SD")
        val saf = StorageSource.SafDocumentTree("content://saf/tree", "SAF Folder")

        sourceRepo.saveSource(primary)
        sourceRepo.saveSource(volume)
        sourceRepo.saveSource(saf)

        coordinator._runtimeStates.value = mapOf(
            primary.id to SourceRuntimeState(primary.id, isAvailable = true, isMounted = true, hasPermission = true),
            volume.id to SourceRuntimeState(volume.id, isAvailable = false, isMounted = false, hasPermission = true),
            saf.id to SourceRuntimeState(saf.id, isAvailable = false, isMounted = true, hasPermission = false)
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val items = viewModel.uiState.value.storageSources
        assertEquals(3, items.size)

        val primaryUi = items.first { it.id == primary.id }
        assertEquals("Internal MediaStore", primaryUi.typeDescription)
        assertEquals("Ready", primaryUi.runtimeStatusDescription)
        assertTrue(primaryUi.isRuntimeAvailable)

        val volumeUi = items.first { it.id == volume.id }
        assertEquals("Removable Volume", volumeUi.typeDescription)
        assertEquals("Disconnected", volumeUi.runtimeStatusDescription)
        assertFalse(volumeUi.isRuntimeAvailable)

        val safUi = items.first { it.id == saf.id }
        assertEquals("SAF Folder Tree", safUi.typeDescription)
        assertEquals("Access revoked", safUi.runtimeStatusDescription)
        assertFalse(safUi.isRuntimeAvailable)
    }

    @Test
    fun `18 enabled-disabled controls expose accessible state`() = runTest(testDispatcher) {
        val source = StorageSource.MediaStorePrimary(isAvailable = true)
        sourceRepo.saveSource(source)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val item = viewModel.uiState.value.storageSources.first { it.id == source.id }
        assertEquals("Internal Audio Storage", item.displayName)
        assertTrue(item.isEnabled)
    }

    @Test
    fun `19 scan progress and completion-error states render correctly`() = runTest(testDispatcher) {
        val source = StorageSource.MediaStorePrimary(isAvailable = true)
        sourceRepo.saveSource(source)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isScanning)
        assertNull(viewModel.uiState.value.scanStatusMessage)

        viewModel.scanLibrary()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isScanning)
        assertNotNull(viewModel.uiState.value.scanStatusMessage)
        assertTrue(viewModel.uiState.value.scanStatusMessage!!.contains("Scan complete"))
    }

    @Test
    fun `20 add folder and scan library actions are wired to the correct callbacks`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        // Calling registerSafFolder triggers repository save & coordinator callback
        val uri = "content://com.android.externalstorage.documents/tree/music"
        viewModel.registerSafFolder(uri, "Music Folder")
        advanceUntilIdle()

        assertEquals(1, sourceRepo.sourcesFlow.value.size)
        assertEquals(1, coordinator.grantedSafUris.size)

        // Calling scanLibrary triggers reconciliation engine
        viewModel.scanLibrary()
        advanceUntilIdle()

        assertEquals(1, engine.scannedSources.size)
        assertEquals(sourceRepo.sourcesFlow.value.values.first().id, engine.scannedSources.first().id)
    }

    @Test
    fun `21 registration failure does not release pre-existing grant`() = runTest(testDispatcher) {
        val uri = "content://preexisting/tree"
        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(uri)
        // Simulate pre-existing permission grant
        safDelegate.persistedPermissions.add(normalized)

        sourceRepo.throwOnSave = true
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.registerSafFolder(uri, "Pre-existing Folder")
        advanceUntilIdle()

        // Registration failed, but pre-existing grant must NOT be rolled back
        assertFalse("Must not release pre-existing grant", safDelegate.releasedPermissions.contains(normalized))
        assertTrue("Pre-existing grant remains intact", safDelegate.hasPersistedReadPermission(normalized))
    }

    @Test
    fun `22 registration failure does not release already registered source grant`() = runTest(testDispatcher) {
        val uri = "content://existing/source"
        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(uri)
        val existingSource = StorageSource.SafDocumentTree(
            treeUri = normalized,
            displayName = "Already Registered"
        )
        sourceRepo.saveSource(existingSource)
        safDelegate.persistedPermissions.add(normalized)

        sourceRepo.throwOnSave = true
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.registerSafFolder(uri, "Renamed Folder")
        advanceUntilIdle()

        // Pre-existing source's grant must NOT be released
        assertFalse("Must not release permission for already registered source", safDelegate.releasedPermissions.contains(normalized))
        assertTrue(safDelegate.hasPersistedReadPermission(normalized))
    }

    @Test
    fun `23 rollback failure does not swallow original registration exception`() = runTest(testDispatcher) {
        sourceRepo.throwOnSave = true
        safDelegate.throwOnRelease = true

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.registerSafFolder("content://fail/tree", "Failing Tree")
        advanceUntilIdle()

        val error = viewModel.uiState.value.scanErrorMessage
        assertNotNull(error)
        // Original error must be preserved
        assertTrue(error!!.contains("Database write failed"))
    }

    @Test
    fun `24 shared scan coordination serializes concurrent scans of the same source`() = runTest(testDispatcher) {
        val source = StorageSource.MediaStorePrimary(isAvailable = true)
        sourceRepo.saveSource(source)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.scanLibrary()
        advanceUntilIdle()

        assertEquals(1, engine.scannedSources.size)
        assertFalse("Lock must be released when scan finishes", engine.coordinator.isSourceScanning(source.id))
    }

    @Test
    fun `25 cancellation during registration rolls back newly acquired grant and propagates CancellationException`() = runTest(testDispatcher) {
        val uri = "content://new/tree"
        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(uri)

        sourceRepo.onSaveSource = {
            throw kotlinx.coroutines.CancellationException("Registration job cancelled")
        }

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.registerSafFolder(uri, "New Tree")
        advanceUntilIdle()

        // Newly acquired grant must be rolled back on cancellation
        assertTrue("Newly acquired permission was taken", safDelegate.takenPermissions.contains(normalized))
        assertTrue("Newly acquired permission was rolled back on cancellation", safDelegate.releasedPermissions.contains(normalized))
        assertFalse("Permission is not retained", safDelegate.hasPersistedReadPermission(normalized))
        // Cancellation must not be converted into scanErrorMessage
        assertNull("Cancellation must not set scanErrorMessage", viewModel.uiState.value.scanErrorMessage)
    }

    @Test
    fun `26 cancellation during registration does not release pre-existing grant`() = runTest(testDispatcher) {
        val uri = "content://existing/grant"
        val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(uri)
        safDelegate.persistedPermissions.add(normalized)

        sourceRepo.onSaveSource = {
            throw kotlinx.coroutines.CancellationException("Registration job cancelled")
        }

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.registerSafFolder(uri, "Existing Tree")
        advanceUntilIdle()

        // Pre-existing grant must NOT be rolled back on cancellation
        assertFalse("Must not release pre-existing grant on cancellation", safDelegate.releasedPermissions.contains(normalized))
        assertTrue("Pre-existing grant remains intact", safDelegate.hasPersistedReadPermission(normalized))
        assertNull("Cancellation must not set scanErrorMessage", viewModel.uiState.value.scanErrorMessage)
    }

    @Test
    fun `27 indexedTrackCount accurately tallies tracks matching primary local file sourceId`() = runTest(testDispatcher) {
        val srcPrimary = StorageSource.MediaStorePrimary()
        val srcSaf = StorageSource.SafDocumentTree(
            id = "saf_tree_1",
            treeUri = "content://saf/tree/1",
            displayName = "SAF Music"
        )
        val srcEmpty = StorageSource.MediaStoreVolume(
            id = "vol_empty",
            volumeName = "empty",
            volumeUuid = "empty",
            displayName = "Empty SD"
        )
        sourceRepo.sourcesFlow.value = mapOf(
            srcPrimary.id to srcPrimary,
            srcSaf.id to srcSaf,
            srcEmpty.id to srcEmpty
        )

        trackRepo.tracks.value = listOf(
            // 2 tracks for primary source
            Track(
                id = "t1", title = "Track 1", durationSec = 100.0,
                localFile = LocalFile(
                    id = "f1", trackId = "t1", sourceId = srcPrimary.id,
                    storageType = StorageType.MEDIA_STORE_PRIMARY,
                    fileUri = "uri1", fileSize = 1000L, fileHash = "h1", format = "mp3"
                )
            ),
            Track(
                id = "t2", title = "Track 2", durationSec = 120.0,
                localFile = LocalFile(
                    id = "f2", trackId = "t2", sourceId = srcPrimary.id,
                    storageType = StorageType.MEDIA_STORE_PRIMARY,
                    fileUri = "uri2", fileSize = 2000L, fileHash = "h2", format = "mp3"
                )
            ),
            // 1 track for SAF source
            Track(
                id = "t3", title = "Track 3", durationSec = 140.0,
                localFile = LocalFile(
                    id = "f3", trackId = "t3", sourceId = srcSaf.id,
                    storageType = StorageType.SAF_IMPORTED,
                    fileUri = "uri3", fileSize = 3000L, fileHash = "h3", format = "flac"
                )
            ),
            // 1 track with null local file (excluded)
            Track(id = "t4", title = "Track 4", durationSec = 160.0, localFile = null),
            // 1 track with null sourceId (excluded)
            Track(
                id = "t5", title = "Track 5", durationSec = 180.0,
                localFile = LocalFile(
                    id = "f5", trackId = "t5", sourceId = null,
                    storageType = StorageType.INTERNAL_SYNC,
                    fileUri = "uri5", fileSize = 5000L, fileHash = "h5", format = "mp3"
                )
            )
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val sources = viewModel.uiState.value.storageSources
        val primaryItem = sources.find { it.id == srcPrimary.id }
        val safItem = sources.find { it.id == srcSaf.id }
        val emptyItem = sources.find { it.id == srcEmpty.id }

        assertNotNull(primaryItem)
        assertNotNull(safItem)
        assertNotNull(emptyItem)

        assertEquals(2, primaryItem?.indexedTrackCount)
        assertEquals(1, safItem?.indexedTrackCount)
        assertEquals(0, emptyItem?.indexedTrackCount)
    }

    @Test
    fun `28 indexedTrackCount updates reactively when tracks change in repository`() = runTest(testDispatcher) {
        val srcPrimary = StorageSource.MediaStorePrimary()
        sourceRepo.sourcesFlow.value = mapOf(srcPrimary.id to srcPrimary)
        trackRepo.tracks.value = emptyList()

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.storageSources.first().indexedTrackCount)

        // Add 1 track
        trackRepo.tracks.value = listOf(
            Track(
                id = "t1", title = "Track 1", durationSec = 100.0,
                localFile = LocalFile(
                    id = "f1", trackId = "t1", sourceId = srcPrimary.id,
                    storageType = StorageType.MEDIA_STORE_PRIMARY,
                    fileUri = "uri1", fileSize = 1000L, fileHash = "h1", format = "mp3"
                )
            )
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.storageSources.first().indexedTrackCount)
    }
}
