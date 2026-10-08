package org.harmonia.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.PlaybackStatus
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.playback.PlaybackPort
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.presentation.MainAppContainer
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class TestPort : PlaybackPort {
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

class TestTrackRepo : TrackRepository {
    val tracks = mutableMapOf<String, Track>()
    override fun observeAllTracks(): Flow<List<Track>> = flowOf(tracks.values.toList())
    override fun observeOfflineTracks(): Flow<List<Track>> = flowOf(tracks.values.toList())
    override suspend fun getTrackById(id: String): Track? = tracks[id]
    override fun searchTracks(query: String): Flow<List<Track>> = flowOf(tracks.values.filter { it.title.contains(query, ignoreCase = true) })
    override suspend fun upsertTracks(tracks: List<Track>) { tracks.forEach { this.tracks[it.id] = it } }
    override suspend fun deleteTrack(id: String) { tracks.remove(id) }
}

@RunWith(AndroidJUnit4::class)
class NowPlayingAndMiniPlayerUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var testPort: TestPort
    private lateinit var testRepo: TestTrackRepo
    private lateinit var viewModel: PlaybackViewModel

    private val track1 = Track(
        id = "track-101",
        title = "Pyramid Song",
        artistName = "Radiohead",
        albumTitle = "Amnesiac",
        durationSec = 289.0,
        artworkHash = "hash-amnesiac"
    )

    private val track2 = Track(
        id = "track-102",
        title = "You and Whose Army?",
        artistName = "Radiohead",
        albumTitle = "Amnesiac",
        durationSec = 191.0,
        artworkHash = "hash-amnesiac"
    )

    @Before
    fun setup() {
        testPort = TestPort()
        testRepo = TestTrackRepo()
        testRepo.tracks[track1.id] = track1
        testRepo.tracks[track2.id] = track2
        viewModel = PlaybackViewModel(testPort, testRepo)
    }

    @Test
    fun mini_player_appears_when_playback_starts_and_toggles_play_pause() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = viewModel)
            }
        }

        // Set queue and start playback
        testPort.setQueue(listOf(track1, track2), 0)
        testPort.play()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pyramid Song").fetchSemanticsNodes().isNotEmpty()
        }

        // MiniPlayer appears and displays track info
        composeTestRule.onNodeWithText("Pyramid Song").assertIsDisplayed()
        composeTestRule.onNodeWithText("Radiohead", substring = true).assertIsDisplayed()

        // Toggle pause from MiniPlayer
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Pause").performClick()

        assertTrue(testPort.pauseCalled)
    }

    @Test
    fun mini_player_next_button_delegates_to_playback_port() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = viewModel)
            }
        }

        testPort.setQueue(listOf(track1, track2), 0)
        testPort.play()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pyramid Song").fetchSemanticsNodes().isNotEmpty()
        }

        // Click next on MiniPlayer
        composeTestRule.onNodeWithContentDescription("Next Track").performClick()
        assertTrue(testPort.nextCalled)
    }

    @Test
    fun now_playing_sheet_opens_and_renders_controls_and_queue() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = viewModel)
            }
        }

        testPort.setQueue(listOf(track1, track2), 0)
        testPort.play()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pyramid Song").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap MiniPlayer body to open Now Playing bottom sheet
        composeTestRule.onNodeWithText("Pyramid Song").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("NOW PLAYING").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Now Playing elements
        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Previous Track").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Next Track").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Show Queue").assertIsDisplayed()

        // Test previous track click
        composeTestRule.onNodeWithContentDescription("Previous Track").performClick()
        assertTrue(testPort.previousCalled)

        // Toggle queue view
        composeTestRule.onNodeWithContentDescription("Show Queue").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYING QUEUE (2)").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("PLAYING QUEUE (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("You and Whose Army?").assertIsDisplayed()

        // Click second queue track to jump
        composeTestRule.onNodeWithText("You and Whose Army?").performClick()
        assertEquals(1, testPort.lastJumpIndex)
    }

    @Test
    fun navigation_between_tabs_preserves_mini_player_state() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = viewModel)
            }
        }

        testPort.setQueue(listOf(track1), 0)
        testPort.play()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pyramid Song").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Pyramid Song").assertIsDisplayed()

        // Switch to Library tab
        composeTestRule.onNodeWithText("Library").performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("LIBRARY").fetchSemanticsNodes().isNotEmpty()
        }

        // MiniPlayer remains visible
        composeTestRule.onNodeWithText("Pyramid Song").assertIsDisplayed()

        // Switch to Search tab
        composeTestRule.onNodeWithText("Search").performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("SEARCH").fetchSemanticsNodes().isNotEmpty()
        }

        // MiniPlayer remains visible
        composeTestRule.onNodeWithText("Pyramid Song").assertIsDisplayed()

        // Switch back to Home tab
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("HARMONIA").fetchSemanticsNodes().isNotEmpty()
        }

        // MiniPlayer remains visible
        composeTestRule.onNodeWithText("Pyramid Song").assertIsDisplayed()
    }
}
