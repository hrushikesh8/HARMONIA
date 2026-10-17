package org.harmonia.player.presentation.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator
import org.harmonia.player.domain.model.equalizer.EqualizerBand
import org.harmonia.player.domain.model.equalizer.EqualizerFailureReason
import org.harmonia.player.domain.model.equalizer.EqualizerPreset
import org.harmonia.player.domain.model.equalizer.EqualizerState
import org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.repository.StorageSourceRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelEqualizerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeTrackRepo: EqFakeTrackRepository
    private lateinit var fakeStorageRepo: EqFakeStorageSourceRepository
    private lateinit var fakeIntentSource: FakeEqualizerUserIntentSource
    private lateinit var fakeCoordinator: FakeAudioEqualizerCoordinator
    private lateinit var viewModel: SettingsViewModel

    private val sampleBands = listOf(
        EqualizerBand(index = 0, centerFreqHz = 60, currentLevelMb = 0, minLevelMb = -1500, maxLevelMb = 1500),
        EqualizerBand(index = 1, centerFreqHz = 230, currentLevelMb = 100, minLevelMb = -1500, maxLevelMb = 1500),
        EqualizerBand(index = 2, centerFreqHz = 910, currentLevelMb = -200, minLevelMb = -1500, maxLevelMb = 1500),
        EqualizerBand(index = 3, centerFreqHz = 3600, currentLevelMb = 0, minLevelMb = -1500, maxLevelMb = 1500),
        EqualizerBand(index = 4, centerFreqHz = 14000, currentLevelMb = 300, minLevelMb = -1500, maxLevelMb = 1500)
    )

    private val samplePresets = listOf(
        EqualizerPreset(index = 0, name = "Flat"),
        EqualizerPreset(index = 1, name = "Bass Boost"),
        EqualizerPreset(index = 2, name = "Vocal")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeTrackRepo = EqFakeTrackRepository()
        fakeStorageRepo = EqFakeStorageSourceRepository()
        fakeIntentSource = FakeEqualizerUserIntentSource(initialEnabled = false)
        fakeCoordinator = FakeAudioEqualizerCoordinator()

        viewModel = SettingsViewModel(
            trackRepository = fakeTrackRepo,
            storageSourceRepository = fakeStorageRepo,
            equalizerUserIntentSource = fakeIntentSource,
            audioEqualizerCoordinator = fakeCoordinator,
            ioDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun defaultStateIsStrictlyDisabledWithNoSession() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val eqState = viewModel.uiState.value.equalizer
        assertFalse("Equalizer master toggle must default to false", eqState.isUserEnabled)
        assertEquals(EqualizerUiStatus.UNAVAILABLE_NO_SESSION, eqState.status)
        assertFalse(eqState.isInteractive)
        assertTrue(eqState.bands.isEmpty())
        assertTrue(eqState.presets.isEmpty())

        collectJob.cancel()
    }

    @Test
    fun toggleEqualizerEnablesPersistenceAndUpdatesCoordinator() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setEqualizerEnabled(true)
        advanceUntilIdle()

        assertTrue(fakeIntentSource.isEqualizerEnabled())
        assertEquals(true, fakeCoordinator.lastRequestedEnabled)
        assertTrue(viewModel.uiState.value.equalizer.isUserEnabled)

        viewModel.setEqualizerEnabled(false)
        advanceUntilIdle()

        assertFalse(fakeIntentSource.isEqualizerEnabled())
        assertEquals(false, fakeCoordinator.lastRequestedEnabled)
        assertFalse(viewModel.uiState.value.equalizer.isUserEnabled)

        collectJob.cancel()
    }

    @Test
    fun coordinatorStateReflectsAvailableDisabled() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        fakeCoordinator.emitState(
            EqualizerState.AvailableDisabled(
                bands = sampleBands,
                presets = samplePresets,
                bandLevelRange = -1500..1500
            )
        )
        advanceUntilIdle()

        val eqState = viewModel.uiState.value.equalizer
        assertEquals(EqualizerUiStatus.DISABLED, eqState.status)
        assertFalse(eqState.isInteractive)
        assertEquals(5, eqState.bands.size)
        assertEquals(3, eqState.presets.size)
        assertEquals(-1500..1500, eqState.bandLevelRange)

        collectJob.cancel()
    }

    @Test
    fun coordinatorStateReflectsEnabledWithControl() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        fakeIntentSource.setEqualizerEnabled(true)
        advanceUntilIdle()

        fakeCoordinator.emitState(
            EqualizerState.EnabledWithControl(
                bands = sampleBands,
                presets = samplePresets,
                currentPresetIndex = 1,
                bandLevelRange = -1500..1500
            )
        )
        advanceUntilIdle()

        val eqState = viewModel.uiState.value.equalizer
        assertEquals(EqualizerUiStatus.ENABLED, eqState.status)
        assertTrue(eqState.isInteractive)
        assertEquals(5, eqState.bands.size)
        assertEquals(1.toShort(), eqState.currentPresetIndex)

        collectJob.cancel()
    }

    @Test
    fun coordinatorStateReflectsControlLost() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        fakeCoordinator.emitState(
            EqualizerState.ControlLost(
                lastKnownEnabled = true,
                bands = sampleBands,
                presets = samplePresets,
                bandLevelRange = -1500..1500
            )
        )
        advanceUntilIdle()

        val eqState = viewModel.uiState.value.equalizer
        assertEquals(EqualizerUiStatus.CONTROL_LOST, eqState.status)
        assertFalse("Controls must be non-interactive when control is lost", eqState.isInteractive)
        assertEquals(5, eqState.bands.size)

        collectJob.cancel()
    }

    @Test
    fun coordinatorStateReflectsUnsupportedHardware() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        fakeCoordinator.emitState(
            EqualizerState.NoSession(
                failureReason = EqualizerFailureReason.UnsupportedHardwareOrRoute()
            )
        )
        advanceUntilIdle()

        val eqState = viewModel.uiState.value.equalizer
        assertEquals(EqualizerUiStatus.UNSUPPORTED, eqState.status)
        assertFalse(eqState.isInteractive)
        assertTrue(eqState.statusMessage.contains("unsupported", ignoreCase = true))

        collectJob.cancel()
    }

    @Test
    fun bandLevelAdjustmentForwardsToCoordinator() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.setEqualizerBandLevel(2, 450)
        advanceUntilIdle()

        assertEquals(2.toShort(), fakeCoordinator.lastRequestedBand)
        assertEquals(450.toShort(), fakeCoordinator.lastRequestedBandLevel)

        collectJob.cancel()
    }

    @Test
    fun invalidBandIndexIsRejectedWhenBandsAreReported() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        fakeCoordinator.emitState(
            EqualizerState.EnabledWithControl(
                bands = sampleBands,
                presets = samplePresets,
                currentPresetIndex = 0,
                bandLevelRange = -1500..1500
            )
        )
        advanceUntilIdle()

        fakeCoordinator.lastRequestedBand = null
        viewModel.setEqualizerBandLevel(99, 300)
        advanceUntilIdle()

        assertNull("Invalid band index must be rejected", fakeCoordinator.lastRequestedBand)

        collectJob.cancel()
    }

    @Test
    fun outOfRangeBandLevelIsClampedToReportedRange() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        fakeCoordinator.emitState(
            EqualizerState.EnabledWithControl(
                bands = sampleBands,
                presets = samplePresets,
                currentPresetIndex = 0,
                bandLevelRange = -1500..1500
            )
        )
        advanceUntilIdle()

        viewModel.setEqualizerBandLevel(1, 5000)
        advanceUntilIdle()
        assertEquals(1500.toShort(), fakeCoordinator.lastRequestedBandLevel)

        viewModel.setEqualizerBandLevel(1, -4000)
        advanceUntilIdle()
        assertEquals((-1500).toShort(), fakeCoordinator.lastRequestedBandLevel)

        collectJob.cancel()
    }

    @Test
    fun presetSelectionForwardsToCoordinator() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        viewModel.useEqualizerPreset(2)
        advanceUntilIdle()

        assertEquals(2.toShort(), fakeCoordinator.lastRequestedPreset)

        collectJob.cancel()
    }

    @Test
    fun invalidPresetIndexIsRejectedWhenPresetsAreReported() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        fakeCoordinator.emitState(
            EqualizerState.EnabledWithControl(
                bands = sampleBands,
                presets = samplePresets,
                currentPresetIndex = 0,
                bandLevelRange = -1500..1500
            )
        )
        advanceUntilIdle()

        fakeCoordinator.lastRequestedPreset = null
        viewModel.useEqualizerPreset(99)
        advanceUntilIdle()

        assertNull("Invalid preset index must be rejected", fakeCoordinator.lastRequestedPreset)

        collectJob.cancel()
    }

    @Test
    fun externalPreferenceUpdateReactivelyUpdatesUiState() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.equalizer.isUserEnabled)

        fakeIntentSource.setEqualizerEnabled(true)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.equalizer.isUserEnabled)

        fakeIntentSource.setEqualizerEnabled(false)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.equalizer.isUserEnabled)

        collectJob.cancel()
    }

    @Test
    fun runtimeFailureDoesNotResetPersistedUserIntent() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        // User explicitly enables the equalizer
        viewModel.setEqualizerEnabled(true)
        advanceUntilIdle()

        assertTrue(fakeIntentSource.isEqualizerEnabled())
        assertTrue(viewModel.uiState.value.equalizer.isUserEnabled)

        // Runtime failure occurs (e.g. native binder dies or effect initialization fails)
        fakeCoordinator.emitState(
            EqualizerState.NoSession(
                failureReason = EqualizerFailureReason.UnexpectedNativeError("Binder died", "DeadObjectException")
            )
        )
        advanceUntilIdle()

        // Runtime failure MUST NOT silently reset saved user intent
        assertTrue("Saved user preference must remain authoritative across runtime failures", fakeIntentSource.isEqualizerEnabled())
        val eqState = viewModel.uiState.value.equalizer
        assertTrue("UI state must preserve user intent across runtime failures", eqState.isUserEnabled)
        assertEquals(EqualizerUiStatus.UNSUPPORTED, eqState.status)
        assertFalse("Controls must be non-interactive after runtime failure", eqState.isInteractive)

        collectJob.cancel()
    }

    @Test
    fun serviceRecreationPreservesSavedPreferenceAndRestoresControl() = testScope.runTest {
        val collectJob = launch { viewModel.uiState.collect {} }
        // Preference was saved as true
        fakeIntentSource.setEqualizerEnabled(true)
        advanceUntilIdle()

        // Service torn down: coordinator enters NoSession
        fakeCoordinator.emitState(EqualizerState.NoSession())
        advanceUntilIdle()

        var eqState = viewModel.uiState.value.equalizer
        assertTrue(eqState.isUserEnabled)
        assertEquals(EqualizerUiStatus.UNAVAILABLE_NO_SESSION, eqState.status)
        assertFalse(eqState.isInteractive)

        // Service recreated and session attaches: coordinator enters EnabledWithControl
        fakeCoordinator.emitState(
            EqualizerState.EnabledWithControl(
                bands = sampleBands,
                presets = samplePresets,
                currentPresetIndex = 0,
                bandLevelRange = -1500..1500
            )
        )
        advanceUntilIdle()

        eqState = viewModel.uiState.value.equalizer
        assertTrue(eqState.isUserEnabled)
        assertEquals(EqualizerUiStatus.ENABLED, eqState.status)
        assertTrue(eqState.isInteractive)
        assertEquals(5, eqState.bands.size)

        collectJob.cancel()
    }
}

private class FakeEqualizerUserIntentSource(initialEnabled: Boolean = false) : EqualizerUserIntentSource {
    private val flow = MutableStateFlow(initialEnabled)

    override fun isEqualizerEnabled(): Boolean = flow.value

    override fun setEqualizerEnabled(enabled: Boolean) {
        flow.value = enabled
    }

    override fun observeEqualizerEnabled(): Flow<Boolean> = flow
}

private class FakeAudioEqualizerCoordinator : AudioEqualizerCoordinator {
    private val _state = MutableStateFlow<EqualizerState>(EqualizerState.NoSession())
    override val state: StateFlow<EqualizerState> = _state

    var lastRequestedEnabled: Boolean? = null
    var lastRequestedBand: Short? = null
    var lastRequestedBandLevel: Short? = null
    var lastRequestedPreset: Short? = null

    fun emitState(newState: EqualizerState) {
        _state.value = newState
    }

    override suspend fun setEnabled(enabled: Boolean): Boolean {
        lastRequestedEnabled = enabled
        return true
    }

    override suspend fun setBandLevel(band: Short, levelMb: Short): Boolean {
        lastRequestedBand = band
        lastRequestedBandLevel = levelMb
        return true
    }

    override suspend fun usePreset(presetIndex: Short): Boolean {
        lastRequestedPreset = presetIndex
        return true
    }
}

private class EqFakeTrackRepository : TrackRepository {
    override fun observeAllTracks(): Flow<List<Track>> = flowOf(emptyList())
    override fun observeOfflineTracks(): Flow<List<Track>> = flowOf(emptyList())
    override suspend fun getTrackById(id: String): Track? = null
    override fun searchTracks(query: String): Flow<List<Track>> = flowOf(emptyList())
    override suspend fun upsertTracks(tracks: List<Track>) {}
    override suspend fun deleteTrack(id: String) {}
}

private class EqFakeStorageSourceRepository : StorageSourceRepository {
    override fun observeAllSources(): Flow<List<StorageSource>> = flowOf(emptyList())
    override fun observeEnabledSources(): Flow<List<StorageSource>> = flowOf(emptyList())
    override suspend fun getSourceById(id: String): StorageSource? = null
    override suspend fun saveSource(source: StorageSource) {}
    override suspend fun setSourceEnabled(id: String, isEnabled: Boolean) {}
    override suspend fun deleteSource(id: String) {}
}
