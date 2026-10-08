package org.harmonia.player.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.PlaybackStatus
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.playback.PlaybackPort
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePlaybackPort : PlaybackPort {
    val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState = _playbackState

    val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue = _queue

    var playCalled = false
    var pauseCalled = false
    var stopCalled = false
    var lastSeekPosition: Long? = null
    var nextCalled = false
    var previousCalled = false
    var lastQueueTracks: List<Track>? = null
    var lastQueueStartIndex: Int? = null
    var lastJumpIndex: Int? = null
    var lastShuffleEnabled: Boolean? = null
    var lastRepeatMode: RepeatMode? = null
    var lastVolume: Float? = null

    override fun play() {
        playCalled = true
        _playbackState.value = _playbackState.value.copy(isPlaying = true, status = PlaybackStatus.PLAYING)
    }

    override fun pause() {
        pauseCalled = true
        _playbackState.value = _playbackState.value.copy(isPlaying = false, status = PlaybackStatus.PAUSED)
    }

    override fun stop() {
        stopCalled = true
        _playbackState.value = _playbackState.value.copy(isPlaying = false, status = PlaybackStatus.IDLE)
    }

    override fun seekTo(positionMs: Long) {
        lastSeekPosition = positionMs
        _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
    }

    override fun next() {
        nextCalled = true
    }

    override fun previous() {
        previousCalled = true
    }

    override fun setQueue(tracks: List<Track>, startIndex: Int) {
        lastQueueTracks = tracks
        lastQueueStartIndex = startIndex
        _queue.value = tracks.mapIndexed { idx, trk ->
            QueueItem(position = idx, trackId = trk.id, track = trk, originalIndex = idx)
        }
        val currentTrack = tracks.getOrNull(startIndex)
        _playbackState.value = _playbackState.value.copy(
            currentTrackId = currentTrack?.id,
            activeQueueIndex = startIndex,
            durationMs = ((currentTrack?.durationSec ?: 0.0) * 1000).toLong()
        )
    }

    override fun appendToQueue(tracks: List<Track>) {
        val current = _queue.value
        val appended = tracks.mapIndexed { idx, trk ->
            val pos = current.size + idx
            QueueItem(position = pos, trackId = trk.id, track = trk, originalIndex = pos)
        }
        _queue.value = current + appended
    }

    override fun jumpToQueueIndex(index: Int) {
        lastJumpIndex = index
        val item = _queue.value.getOrNull(index)
        _playbackState.value = _playbackState.value.copy(
            currentTrackId = item?.trackId,
            activeQueueIndex = index,
            durationMs = ((item?.track?.durationSec ?: 0.0) * 1000).toLong()
        )
    }

    override fun setShuffleMode(enabled: Boolean) {
        lastShuffleEnabled = enabled
        _playbackState.value = _playbackState.value.copy(shuffleMode = enabled)
    }

    override fun setRepeatMode(mode: RepeatMode) {
        lastRepeatMode = mode
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
    }

    override fun setVolume(volume: Float) {
        lastVolume = volume
    }
}

class FakeTrackRepository : TrackRepository {
    val tracks = mutableMapOf<String, Track>()

    override fun observeAllTracks(): Flow<List<Track>> = flowOf(tracks.values.toList())
    override fun observeOfflineTracks(): Flow<List<Track>> = flowOf(tracks.values.toList())
    override suspend fun getTrackById(id: String): Track? = tracks[id]
    override fun searchTracks(query: String): Flow<List<Track>> = flowOf(tracks.values.filter { it.title.contains(query, ignoreCase = true) })
    override suspend fun upsertTracks(tracks: List<Track>) {
        tracks.forEach { this.tracks[it.id] = it }
    }
    override suspend fun deleteTrack(id: String) {
        tracks.remove(id)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakePlaybackPort: FakePlaybackPort
    private lateinit var fakeTrackRepo: FakeTrackRepository
    private lateinit var viewModel: PlaybackViewModel

    private val trackA = Track(
        id = "trk-a",
        title = "Everything in Its Right Place",
        artistName = "Radiohead",
        albumTitle = "Kid A",
        durationSec = 251.0,
        artworkHash = "art-kid-a"
    )
    private val trackB = Track(
        id = "trk-b",
        title = "Idioteque",
        artistName = "Radiohead",
        albumTitle = "Kid A",
        durationSec = 309.0,
        artworkHash = "art-kid-a"
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        fakePlaybackPort = FakePlaybackPort()
        fakeTrackRepo = FakeTrackRepository()
        fakeTrackRepo.tracks[trackA.id] = trackA
        fakeTrackRepo.tracks[trackB.id] = trackB
        viewModel = PlaybackViewModel(fakePlaybackPort, fakeTrackRepo)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial ui state matches idle playback port`() = runTest(testDispatcher) {
        val state = viewModel.uiState.value
        assertEquals(PlaybackStatus.IDLE, state.playbackState.status)
        assertFalse(state.playbackState.isPlaying)
        assertNull(state.currentTrack)
        assertTrue(state.queue.isEmpty())
    }

    @Test
    fun `playback UI state mapping reflects playback port updates`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        fakePlaybackPort.setQueue(listOf(trackA, trackB), startIndex = 0)
        advanceUntilIdle()

        val state = viewModel.uiState.first { it.queue.isNotEmpty() }
        assertEquals(2, state.queue.size)
        assertEquals(trackA.id, state.currentTrack?.id)
        assertEquals("Everything in Its Right Place", state.currentTrack?.title)
        assertEquals("art-kid-a", state.currentTrack?.artworkHash)
        assertEquals(251000L, state.playbackState.durationMs)
    }

    @Test
    fun `play and pause delegate correctly to playback port`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        viewModel.play()
        advanceUntilIdle()
        assertTrue(fakePlaybackPort.playCalled)
        assertTrue(viewModel.uiState.value.playbackState.isPlaying)
        assertEquals(PlaybackStatus.PLAYING, viewModel.uiState.value.playbackState.status)

        viewModel.pause()
        advanceUntilIdle()
        assertTrue(fakePlaybackPort.pauseCalled)
        assertFalse(viewModel.uiState.value.playbackState.isPlaying)
        assertEquals(PlaybackStatus.PAUSED, viewModel.uiState.value.playbackState.status)
    }

    @Test
    fun `position and duration mapping updates accurately`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        fakePlaybackPort.setQueue(listOf(trackA), startIndex = 0)
        advanceUntilIdle()

        viewModel.seekTo(45000L)
        advanceUntilIdle()

        assertEquals(45000L, fakePlaybackPort.lastSeekPosition)
        assertEquals(45000L, viewModel.uiState.value.playbackState.currentPositionMs)
        assertEquals(251000L, viewModel.uiState.value.playbackState.durationMs)
    }

    @Test
    fun `next and previous delegate directly to authoritative playback port`() = runTest(testDispatcher) {
        viewModel.next()
        assertTrue(fakePlaybackPort.nextCalled)

        viewModel.previous()
        assertTrue(fakePlaybackPort.previousCalled)
    }

    @Test
    fun `jumpToQueueIndex delegates to playback port and updates current track`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        fakePlaybackPort.setQueue(listOf(trackA, trackB), startIndex = 0)
        advanceUntilIdle()

        viewModel.jumpToQueueIndex(1)
        advanceUntilIdle()

        assertEquals(1, fakePlaybackPort.lastJumpIndex)
        val state = viewModel.uiState.value
        assertEquals(trackB.id, state.playbackState.currentTrackId)
        assertEquals("Idioteque", state.currentTrack?.title)
    }

    @Test
    fun `toggleShuffle cycles boolean on playback port`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertFalse(viewModel.uiState.value.playbackState.shuffleMode)

        viewModel.toggleShuffle()
        advanceUntilIdle()
        assertTrue(fakePlaybackPort.lastShuffleEnabled == true)
        assertTrue(viewModel.uiState.value.playbackState.shuffleMode)

        viewModel.toggleShuffle()
        advanceUntilIdle()
        assertFalse(fakePlaybackPort.lastShuffleEnabled == true)
        assertFalse(viewModel.uiState.value.playbackState.shuffleMode)
    }

    @Test
    fun `cycleRepeat cycles OFF to ALL to ONE to OFF`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        assertEquals(RepeatMode.OFF, viewModel.uiState.value.playbackState.repeatMode)

        viewModel.cycleRepeat()
        advanceUntilIdle()
        assertEquals(RepeatMode.ALL, fakePlaybackPort.lastRepeatMode)
        assertEquals(RepeatMode.ALL, viewModel.uiState.value.playbackState.repeatMode)

        viewModel.cycleRepeat()
        advanceUntilIdle()
        assertEquals(RepeatMode.ONE, fakePlaybackPort.lastRepeatMode)
        assertEquals(RepeatMode.ONE, viewModel.uiState.value.playbackState.repeatMode)

        viewModel.cycleRepeat()
        advanceUntilIdle()
        assertEquals(RepeatMode.OFF, fakePlaybackPort.lastRepeatMode)
        assertEquals(RepeatMode.OFF, viewModel.uiState.value.playbackState.repeatMode)
    }

    @Test
    fun `unavailable or error state maps correctly into presentation`() = runTest(testDispatcher) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect() }
        fakePlaybackPort._playbackState.value = PlaybackState(
            status = PlaybackStatus.ERROR,
            errorMessage = "Playback decoder initialization failed",
            isPlaying = false,
            currentTrackId = null
        )
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(PlaybackStatus.ERROR, state.playbackState.status)
        assertEquals("Playback decoder initialization failed", state.playbackState.errorMessage)
        assertFalse(state.playbackState.isPlaying)
        assertNull(state.currentTrack)
    }
}
