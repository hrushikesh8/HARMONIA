package org.harmonia.player.presentation.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.StorageSourceRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.domain.scanner.MediaReconciliationEngine
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

private class FakeAlbumRepository : AlbumRepository {
    val albums = MutableStateFlow<List<Album>>(emptyList())
    override fun observeAllAlbums(): Flow<List<Album>> = albums
    override suspend fun getAlbumById(id: String): Album? = albums.value.find { it.id == id }
    override fun observeAlbumsByArtist(artistId: String): Flow<List<Album>> = albums.map { list ->
        list.filter { it.artistId == artistId }
    }
    override suspend fun upsertAlbums(albums: List<Album>) { this.albums.value = albums }
}

private class FakeArtistRepository : ArtistRepository {
    val artists = MutableStateFlow<List<Artist>>(emptyList())
    override fun observeAllArtists(): Flow<List<Artist>> = artists
    override suspend fun getArtistById(id: String): Artist? = artists.value.find { it.id == id }
    override suspend fun upsertArtists(artists: List<Artist>) { this.artists.value = artists }
}

private class FakeStoragePermissionChecker(
    var state: StoragePermissionState = StoragePermissionState.GRANTED
) : StoragePermissionChecker {
    override fun getPermissionState(): StoragePermissionState = state
    override fun canScanAudioMedia(): Boolean = state == StoragePermissionState.GRANTED
}

private class FakeMediaReconciliationEngine : MediaReconciliationEngine {
    var scanCount = 0
    var shouldFail = false
    var failureErrorMessage: String? = null
    var onScanBlock: (suspend (StorageSource, ScanMode) -> Unit)? = null

    override fun scanSource(source: StorageSource, mode: ScanMode): Flow<ScanProgress> = flow {
        scanCount++
        emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.IDLE))
        if (shouldFail) {
            emit(
                ScanProgress(
                    sourceId = source.id,
                    status = ScanProgressStatus.FAILED,
                    errorMessage = failureErrorMessage ?: "Media reconciliation failure"
                )
            )
            return@flow
        }
        emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.DISCOVERING))
        onScanBlock?.invoke(source, mode)
        emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.COMPLETED))
    }
}

private class FakeStorageSourceRepository : StorageSourceRepository {
    val sources = mutableMapOf<String, StorageSource>()

    override fun observeAllSources(): Flow<List<StorageSource>> = flowOf(sources.values.toList())
    override fun observeEnabledSources(): Flow<List<StorageSource>> = flowOf(sources.values.filter { it.isAvailable })
    override suspend fun getSourceById(id: String): StorageSource? = sources[id]
    override suspend fun saveSource(source: StorageSource) { sources[source.id] = source }
    override suspend fun setSourceEnabled(id: String, isEnabled: Boolean) {
        val s = sources[id] ?: return
        sources[id] = when (s) {
            is StorageSource.MediaStorePrimary -> s.copy(isAvailable = isEnabled)
            is StorageSource.MediaStoreVolume -> s.copy(isAvailable = isEnabled)
            is StorageSource.SafDocumentTree -> s.copy(isAvailable = isEnabled)
            is StorageSource.InternalSyncStorage -> s.copy(isAvailable = isEnabled)
        }
    }
    override suspend fun deleteSource(id: String) { sources.remove(id) }
}

private class FakeStorageVolumeLifecycleCoordinator : StorageVolumeLifecycleCoordinator {
    val states = MutableStateFlow<Map<String, SourceRuntimeState>>(emptyMap())
    override val runtimeStates: kotlinx.coroutines.flow.StateFlow<Map<String, SourceRuntimeState>> = states

    override fun isSourceAvailable(sourceId: String): Boolean = states.value[sourceId]?.isAvailable ?: true
    override fun onVolumeMounted(volumeId: String) {}
    override fun onVolumeUnmounted(volumeId: String) {}
    override fun onSafPermissionRevoked(treeUri: String) {}
    override fun onSafPermissionGranted(treeUri: String) {}
    override suspend fun revalidateAllSources(): Map<String, SourceRuntimeState> = states.value
    override suspend fun revalidateSource(source: StorageSource): SourceRuntimeState =
        states.value[source.id] ?: SourceRuntimeState(source.id, true, true, true)
    override fun startMonitoring() {}
    override fun stopMonitoring() {}
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var trackRepo: FakeTrackRepository
    private lateinit var albumRepo: FakeAlbumRepository
    private lateinit var artistRepo: FakeArtistRepository
    private lateinit var permissionChecker: FakeStoragePermissionChecker
    private lateinit var reconciliationEngine: FakeMediaReconciliationEngine
    private lateinit var sourceRepo: FakeStorageSourceRepository
    private lateinit var coordinator: FakeStorageVolumeLifecycleCoordinator

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        trackRepo = FakeTrackRepository()
        albumRepo = FakeAlbumRepository()
        artistRepo = FakeArtistRepository()
        permissionChecker = FakeStoragePermissionChecker(StoragePermissionState.GRANTED)
        reconciliationEngine = FakeMediaReconciliationEngine()
        sourceRepo = FakeStorageSourceRepository()
        coordinator = FakeStorageVolumeLifecycleCoordinator()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        permChecker: StoragePermissionChecker = permissionChecker,
        engine: MediaReconciliationEngine = reconciliationEngine,
        sources: StorageSourceRepository = sourceRepo,
        lifecycleCoordinator: StorageVolumeLifecycleCoordinator = coordinator
    ): LibraryViewModel {
        return LibraryViewModel(
            trackRepository = trackRepo,
            albumRepository = albumRepo,
            artistRepository = artistRepo,
            storagePermissionChecker = permChecker,
            mediaReconciliationEngine = engine,
            storageSourceRepository = sources,
            storageVolumeLifecycleCoordinator = lifecycleCoordinator,
            ioDispatcher = testDispatcher
        )
    }

    private fun TestScope.subscribeUiState(viewModel: LibraryViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `1 fresh launch with permission not granted reflects permission required and does not scan`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.DENIED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StoragePermissionState.DENIED, state.permissionState)
        assertTrue(state.isPermissionRequired)
        assertFalse(state.isScanning)
        assertNull(state.scanErrorMessage)
        assertEquals(0, reconciliationEngine.scanCount)
    }

    @Test
    fun `2 permission granted successfully triggers initial scan`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.DENIED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()
        assertEquals(0, reconciliationEngine.scanCount)

        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(StoragePermissionState.GRANTED, state.permissionState)
        assertFalse(state.isPermissionRequired)
        assertEquals(1, reconciliationEngine.scanCount)
    }

    @Test
    fun `3 permission denied updates permission state and does not trigger scan`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.DENIED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, canRequestAgain = true)
        advanceUntilIdle()

        var state = viewModel.uiState.value
        assertEquals(StoragePermissionState.DENIED, state.permissionState)
        assertTrue(state.isPermissionRequired)
        assertEquals(0, reconciliationEngine.scanCount)

        viewModel.onPermissionResult(granted = false, canRequestAgain = false)
        advanceUntilIdle()

        state = viewModel.uiState.value
        assertEquals(StoragePermissionState.SETTINGS_REQUIRED, state.permissionState)
        assertTrue(state.isPermissionRequired)
        assertEquals(0, reconciliationEngine.scanCount)
    }

    @Test
    fun `4 permission granted later after earlier denial triggers scan immediately`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.DENIED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.onPermissionResult(granted = false, canRequestAgain = true)
        advanceUntilIdle()
        assertEquals(0, reconciliationEngine.scanCount)

        // User subsequently grants permission
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(1, reconciliationEngine.scanCount)
        assertFalse(viewModel.uiState.value.isPermissionRequired)
    }

    @Test
    fun `5 permission granted externally triggers scan on foreground refresh`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.DENIED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()
        assertEquals(0, reconciliationEngine.scanCount)

        // User enabled permission in Android App Settings
        permissionChecker.state = StoragePermissionState.GRANTED
        viewModel.refreshPermissionState()
        advanceUntilIdle()

        assertEquals(1, reconciliationEngine.scanCount)
        assertEquals(StoragePermissionState.GRANTED, viewModel.uiState.value.permissionState)
    }

    @Test
    fun `6 initial scan automatically registers primary storage source`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(1, reconciliationEngine.scanCount)
        val registeredSource = sourceRepo.sources[StorageSource.MediaStorePrimary.ID]
        assertNotNull(registeredSource)
        assertTrue(registeredSource!!.isAvailable)
    }

    @Test
    fun `7 no scan triggered while permission is denied`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.DENIED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.triggerScan()
        advanceUntilIdle()

        assertEquals(0, reconciliationEngine.scanCount)
    }

    @Test
    fun `8 duplicate lifecycle events do not start duplicate scans`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()
        assertEquals(1, reconciliationEngine.scanCount)

        // Multiple ON_RESUME events with permission remaining GRANTED
        viewModel.refreshPermissionState()
        advanceUntilIdle()
        viewModel.refreshPermissionState()
        advanceUntilIdle()

        assertEquals(1, reconciliationEngine.scanCount)
    }

    @Test
    fun `9 repeated permission result delivery does not duplicate ingestion`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.DENIED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        // Rapid multiple callbacks
        viewModel.onPermissionResult(granted = true)
        viewModel.onPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(1, reconciliationEngine.scanCount)
    }

    @Test
    fun `10 successful scan with an empty library produces true empty library state`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, reconciliationEngine.scanCount)
        assertTrue(state.tracks.isEmpty())
        assertFalse(state.isLoading)
        assertFalse(state.isScanning)
        assertFalse(state.isPermissionRequired)
        assertFalse(state.isScanFailed)
        assertTrue(state.isLibraryEmpty)
    }

    @Test
    fun `11 successful scan with discovered media populates catalog and preserves non-empty state`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        reconciliationEngine.onScanBlock = { _, _ ->
            trackRepo.tracks.value = listOf(
                Track(id = "t1", title = "Reckoner", artistName = "Radiohead", durationSec = 290.0)
            )
        }

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, reconciliationEngine.scanCount)
        assertEquals(1, state.tracks.size)
        assertEquals("Reckoner", state.tracks[0].title)
        assertFalse(state.isLibraryEmpty)
    }

    @Test
    fun `12 scan exception produces recoverable error state`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        reconciliationEngine.shouldFail = true
        reconciliationEngine.failureErrorMessage = "MediaStore content provider unavailable"

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, reconciliationEngine.scanCount)
        assertFalse(state.isScanning)
        assertTrue(state.isScanFailed)
        assertEquals("MediaStore content provider unavailable", state.scanErrorMessage)
    }

    @Test
    fun `13 retry after scan failure resumes scanning and clears error`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        reconciliationEngine.shouldFail = true
        reconciliationEngine.failureErrorMessage = "I/O Error"

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isScanFailed)
        assertEquals(1, reconciliationEngine.scanCount)

        // Fix failure condition and retry
        reconciliationEngine.shouldFail = false
        viewModel.retryScan()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, reconciliationEngine.scanCount)
        assertFalse(state.isScanFailed)
        assertNull(state.scanErrorMessage)
    }

    @Test
    fun `14 existing library state is preserved during scan failure`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val existingTracks = listOf(
            Track(id = "t1", title = "Paranoid Android", artistName = "Radiohead", durationSec = 383.0),
            Track(id = "t2", title = "Karma Police", artistName = "Radiohead", durationSec = 264.0)
        )
        trackRepo.tracks.value = existingTracks

        reconciliationEngine.shouldFail = true
        reconciliationEngine.failureErrorMessage = "Reconciliation error"

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.tracks.size)
        assertEquals("Karma Police", state.tracks[0].title) // sorted title asc
        assertEquals("Paranoid Android", state.tracks[1].title)
        assertTrue(state.isScanFailed)
    }

    @Test
    fun `15 disabled source configuration prevents scanning even if permission is granted`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        // Source is explicitly disabled in user configuration
        sourceRepo.sources[StorageSource.MediaStorePrimary.ID] = StorageSource.MediaStorePrimary(isAvailable = false)

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(0, reconciliationEngine.scanCount)
    }

    @Test
    fun `16 unmounted volume in lifecycle coordinator is reflected in unmountedSourceIds only`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val sourceId = "vol-ext-sdcard"
        coordinator.states.value = mapOf(
            sourceId to SourceRuntimeState(
                sourceId = sourceId,
                isAvailable = false,
                isMounted = false,
                hasPermission = true
            )
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.unmountedSourceIds.contains(sourceId))
        assertFalse(state.disabledSourceIds.contains(sourceId))
    }

    @Test
    fun `17 disabled source in repository is reflected in disabledSourceIds only`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val sourceId = "saf-tree-music"
        val safSource = StorageSource.SafDocumentTree(
            id = sourceId,
            treeUri = "content://saf/tree",
            displayName = "Music Folder",
            isAvailable = false
        )
        sourceRepo.sources[sourceId] = safSource

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.disabledSourceIds.contains(sourceId))
        assertFalse(state.unmountedSourceIds.contains(sourceId))
    }

    @Test
    fun `18 reconnected volume clears unmountedSourceIds without resetting tracks`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val sourceId = "vol-ext-sdcard"
        coordinator.states.value = mapOf(
            sourceId to SourceRuntimeState(
                sourceId = sourceId,
                isAvailable = false,
                isMounted = false,
                hasPermission = true
            )
        )
        trackRepo.tracks.value = listOf(
            Track(id = "t1", title = "Track 1", durationSec = 180.0)
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.unmountedSourceIds.contains(sourceId))
        assertEquals(1, viewModel.uiState.value.tracks.size)

        // Volume remounts
        coordinator.states.value = mapOf(
            sourceId to SourceRuntimeState(
                sourceId = sourceId,
                isAvailable = true,
                isMounted = true,
                hasPermission = true
            )
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.unmountedSourceIds.contains(sourceId))
        assertEquals(1, viewModel.uiState.value.tracks.size)
        assertEquals("Track 1", viewModel.uiState.value.tracks[0].title)
    }

    @Test
    fun `19 permission loss alone does not add to unmountedSourceIds when volume remains physically mounted`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val sourceId = "saf-tree-revoked"
        // Source is physically mounted/present, but permission was revoked (isAvailable false, isMounted true)
        coordinator.states.value = mapOf(
            sourceId to SourceRuntimeState(
                sourceId = sourceId,
                isAvailable = false,
                isMounted = true,
                hasPermission = false
            )
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Must NOT infer physical unmount from permission loss alone
        assertFalse(state.unmountedSourceIds.contains(sourceId))
        assertFalse(state.disabledSourceIds.contains(sourceId))
    }

    @Test
    fun `20 source both disabled and marked unmounted is classified as disabled only`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val sourceId = "vol-ext-sdcard"
        val volumeSource = StorageSource.MediaStoreVolume(
            volumeName = "external_sd",
            volumeUuid = "sdcard",
            displayName = "SD Card",
            id = sourceId,
            isRemovable = true,
            isAvailable = false // User deliberately disabled it
        )
        sourceRepo.sources[sourceId] = volumeSource

        // Coordinator holds unmounted state
        coordinator.states.value = mapOf(
            sourceId to SourceRuntimeState(
                sourceId = sourceId,
                isAvailable = false,
                isMounted = false,
                hasPermission = true
            )
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.disabledSourceIds.contains(sourceId))
        assertFalse(state.unmountedSourceIds.contains(sourceId))
    }

    @Test
    fun `21 saf permission revocation does not imply physical unmount`() = runTest(testDispatcher) {
        permissionChecker.state = StoragePermissionState.GRANTED
        val sourceId = "saf_tree_music"
        val safSource = StorageSource.SafDocumentTree(
            id = sourceId,
            treeUri = "content://saf/tree/music",
            displayName = "Music Folder",
            isAvailable = true
        )
        sourceRepo.sources[sourceId] = safSource

        // Coordinator reflects SAF permission revocation (isMounted = false, hasPermission = false)
        coordinator.states.value = mapOf(
            sourceId to SourceRuntimeState(
                sourceId = sourceId,
                isAvailable = false,
                isMounted = false,
                hasPermission = false
            )
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.unmountedSourceIds.contains(sourceId))
        assertFalse(state.disabledSourceIds.contains(sourceId))
    }
}
