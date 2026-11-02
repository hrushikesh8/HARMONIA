package org.harmonia.player.presentation.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.harmonia.player.data.local.preferences.InMemoryUserPreferencesRepository
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.audio.SleepTimerState
import org.harmonia.player.domain.model.storage.ScanCheckpoint
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.playback.PlaybackPort
import org.harmonia.player.domain.repository.ScanCheckpointRepository
import org.harmonia.player.domain.repository.StorageSourceRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelPreferencesTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var userPrefs: InMemoryUserPreferencesRepository
    private lateinit var fakePlaybackPort: FakeSettingsPlaybackPort
    private lateinit var fakeCheckpointRepo: FakeSettingsScanCheckpointRepository
    private lateinit var fakeStorageRepo: FakeSettingsStorageSourceRepository
    private lateinit var fakeTrackRepo: FakeSettingsTrackRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        userPrefs = InMemoryUserPreferencesRepository()
        fakePlaybackPort = FakeSettingsPlaybackPort()
        fakeCheckpointRepo = FakeSettingsScanCheckpointRepository()
        fakeStorageRepo = FakeSettingsStorageSourceRepository()
        fakeTrackRepo = FakeSettingsTrackRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SettingsViewModel {
        return SettingsViewModel(
            trackRepository = fakeTrackRepo,
            storageSourceRepository = fakeStorageRepo,
            userPreferencesRepository = userPrefs,
            playbackPort = fakePlaybackPort,
            scanCheckpointRepository = fakeCheckpointRepo,
            ioDispatcher = testDispatcher
        )
    }

    private fun TestScope.subscribeUiState(viewModel: SettingsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun themeModePreferenceCanBeToggled() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertTrue("Initial theme must be dark", viewModel.uiState.value.isDarkTheme)

        viewModel.setIsDarkTheme(false)
        advanceUntilIdle()

        assertFalse("Theme must be updated to false", viewModel.uiState.value.isDarkTheme)
        assertFalse(userPrefs.isDarkTheme())
    }

    @Test
    fun defaultShuffleTogglesPreferenceAndNotifiesPlaybackPort() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.defaultShuffle)

        viewModel.setDefaultShuffle(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.defaultShuffle)
        assertTrue(userPrefs.isDefaultShuffleEnabled())
        assertTrue(fakePlaybackPort.shuffleModeEnabled)
    }

    @Test
    fun defaultRepeatModeUpdatesPreferenceAndNotifiesPlaybackPort() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertEquals(RepeatMode.OFF, viewModel.uiState.value.defaultRepeatMode)

        viewModel.setDefaultRepeatMode(RepeatMode.ALL)
        advanceUntilIdle()

        assertEquals(RepeatMode.ALL, viewModel.uiState.value.defaultRepeatMode)
        assertEquals(RepeatMode.ALL, userPrefs.getDefaultRepeatMode())
        assertEquals(RepeatMode.ALL, fakePlaybackPort.activeRepeatMode)
    }

    @Test
    fun keepScreenOnPreferenceCanBeUpdated() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.keepScreenOn)

        viewModel.setKeepScreenOn(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.keepScreenOn)
        assertTrue(userPrefs.isKeepScreenOn())
    }

    @Test
    fun resetScanCheckpointsClearsSourceCheckpoints() = runTest(testDispatcher) {
        val src = StorageSource.MediaStorePrimary()
        fakeStorageRepo.sourcesFlow.value = listOf(src)
        fakeCheckpointRepo.savedCheckpoints[src.id] = ScanCheckpoint(
            sourceId = src.id,
            volumeName = "external_primary",
            lastGeneration = 1000L,
            scannedFileCount = 10
        )

        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.resetScanCheckpoints()
        advanceUntilIdle()

        assertTrue(fakeCheckpointRepo.resetSourceIds.contains(src.id))
        assertNotNull(viewModel.uiState.value.scanStatusMessage)
        assertTrue(viewModel.uiState.value.scanStatusMessage!!.contains("Checkpoints reset"))
    }

    @Test
    fun startSleepTimerPresetSetsDurationOnPlaybackPort() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.startSleepTimer(org.harmonia.player.domain.model.audio.SleepTimerPreset.MINUTES_15)
        advanceUntilIdle()

        assertEquals(15 * 60 * 1000L, fakePlaybackPort.startedDurationMs)
    }

    @Test
    fun startSleepTimerPresetEndOfTrackCallsPort() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.startSleepTimer(org.harmonia.player.domain.model.audio.SleepTimerPreset.END_OF_TRACK)
        advanceUntilIdle()

        assertTrue(fakePlaybackPort.startedEndOfTrack)
    }

    @Test
    fun cancelSleepTimerDelegatesToPlaybackPort() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        subscribeUiState(viewModel)
        advanceUntilIdle()

        viewModel.cancelSleepTimer()
        advanceUntilIdle()

        assertTrue(fakePlaybackPort.cancelledSleepTimer)
    }
}

private class FakeSettingsPlaybackPort : PlaybackPort {
    val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: MutableStateFlow<PlaybackState> = _playbackState
    override val queue: MutableStateFlow<List<QueueItem>> = MutableStateFlow(emptyList())
    override val sleepTimerState: MutableStateFlow<SleepTimerState> = MutableStateFlow(SleepTimerState())

    var shuffleModeEnabled = false
    var activeRepeatMode = RepeatMode.OFF
    var cancelledSleepTimer = false
    var startedDurationMs: Long? = null
    var startedEndOfTrack = false

    override fun play() {}
    override fun pause() {}
    override fun stop() {}
    override fun seekTo(positionMs: Long) {}
    override fun next() {}
    override fun previous() {}
    override fun setQueue(tracks: List<Track>, startIndex: Int) {}
    override fun appendToQueue(tracks: List<Track>) {}
    override fun jumpToQueueIndex(index: Int) {}
    override fun setShuffleMode(enabled: Boolean) { shuffleModeEnabled = enabled }
    override fun setRepeatMode(mode: RepeatMode) { activeRepeatMode = mode }
    override fun setVolume(volume: Float) {}
    override fun startSleepTimer(durationMs: Long) { startedDurationMs = durationMs }
    override fun startSleepTimerEndOfTrack() { startedEndOfTrack = true }
    override fun cancelSleepTimer() { cancelledSleepTimer = true }
}

private class FakeSettingsScanCheckpointRepository : ScanCheckpointRepository {
    val savedCheckpoints = mutableMapOf<String, ScanCheckpoint>()
    val resetSourceIds = mutableListOf<String>()

    override fun observeCheckpoint(sourceId: String): Flow<ScanCheckpoint?> = flowOf(savedCheckpoints[sourceId])
    override suspend fun getCheckpoint(sourceId: String): ScanCheckpoint? = savedCheckpoints[sourceId]
    override suspend fun saveCheckpoint(checkpoint: ScanCheckpoint) { savedCheckpoints[checkpoint.sourceId] = checkpoint }
    override suspend fun resetCheckpoint(sourceId: String) {
        savedCheckpoints.remove(sourceId)
        resetSourceIds.add(sourceId)
    }
}

private class FakeSettingsStorageSourceRepository : StorageSourceRepository {
    val sourcesFlow = MutableStateFlow<List<StorageSource>>(emptyList())
    override fun observeAllSources(): Flow<List<StorageSource>> = sourcesFlow
    override fun observeEnabledSources(): Flow<List<StorageSource>> = sourcesFlow
    override suspend fun getSourceById(id: String): StorageSource? = sourcesFlow.value.find { it.id == id }
    override suspend fun saveSource(source: StorageSource) {}
    override suspend fun setSourceEnabled(id: String, isEnabled: Boolean) {}
    override suspend fun deleteSource(id: String) {}
}

private class FakeSettingsTrackRepository : TrackRepository {
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    override fun observeAllTracks(): Flow<List<Track>> = tracks
    override fun observeOfflineTracks(): Flow<List<Track>> = tracks
    override suspend fun getTrackById(id: String): Track? = null
    override fun searchTracks(query: String): Flow<List<Track>> = tracks
    override suspend fun upsertTracks(tracks: List<Track>) {}
    override suspend fun deleteTrack(id: String) {}
}
