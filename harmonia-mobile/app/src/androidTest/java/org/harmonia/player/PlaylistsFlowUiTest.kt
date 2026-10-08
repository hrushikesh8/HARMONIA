package org.harmonia.player

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.MainAppContainer
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistsFlowUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: HarmoniaDatabase
    private lateinit var testPort: TestPort
    private lateinit var testTrackRepo: TestTrackRepo
    private lateinit var playbackViewModel: PlaybackViewModel

    private val trackAlpha = Track(
        id = "trk-alpha",
        title = "Solaris",
        artistName = "Photay",
        durationSec = 240.0
    )

    private val trackBeta = Track(
        id = "trk-beta",
        title = "Breathe",
        artistName = "Telepopmusik",
        durationSec = 280.0
    )

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = HarmoniaDatabase.getInstance(context)
        db.playlistDao().observeAllPlaylists() // ensure initialized
        // Clean playlists, tracks and queue
        db.trackDao().deleteAllTracks()
        db.playlistDao().deleteAllPlaylists()
        db.playbackStateDao().clearQueue()

        // Insert tracks into database
        db.trackDao().upsertTrack(TrackEntity(id = trackAlpha.id, title = trackAlpha.title, durationSec = trackAlpha.durationSec, createdAt = 0L, updatedAt = 0L))
        db.trackDao().upsertTrack(TrackEntity(id = trackBeta.id, title = trackBeta.title, durationSec = trackBeta.durationSec, createdAt = 0L, updatedAt = 0L))

        testPort = TestPort()
        testTrackRepo = TestTrackRepo()
        testTrackRepo.tracks[trackAlpha.id] = trackAlpha
        testTrackRepo.tracks[trackBeta.id] = trackBeta
        playbackViewModel = PlaybackViewModel(testPort, testTrackRepo)
    }

    @After
    fun tearDown() = runBlocking {
        db.trackDao().deleteAllTracks()
        db.playlistDao().deleteAllPlaylists()
        db.playbackStateDao().clearQueue()
    }

    @Test
    fun playlists_screen_renders_and_creates_new_playlist() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Playlists tab
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYLISTS").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("PLAYLISTS").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Create new playlist").assertIsDisplayed()

        // Click New to open create dialog
        composeTestRule.onNodeWithContentDescription("Create new playlist").performClick()

        // Enter playlist name
        composeTestRule.onNodeWithText("Playlist Name").performTextInput("Ambient Journeys")
        composeTestRule.onNodeWithText("Create").performClick()

        // Verify playlist card appears
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Ambient Journeys").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Ambient Journeys").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("0 tracks", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun playlist_rename_flow_updates_title() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Playlists tab
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYLISTS").fetchSemanticsNodes().isNotEmpty()
        }

        // Create initial playlist
        composeTestRule.onNodeWithContentDescription("Create new playlist").performClick()
        composeTestRule.onNodeWithText("Playlist Name").performTextInput("Draft Vibe")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Draft Vibe").fetchSemanticsNodes().isNotEmpty()
        }

        // Open options and click Rename
        composeTestRule.onNodeWithContentDescription("Options for Draft Vibe").performClick()
        composeTestRule.onNodeWithText("Rename").performClick()

        composeTestRule.onNodeWithContentDescription("Playlist Name Field").performTextReplacement("Draft Vibe (Polished)")
        composeTestRule.onNodeWithContentDescription("Confirm Rename").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Draft Vibe (Polished)").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Draft Vibe (Polished)").assertIsDisplayed()
    }

    @Test
    fun playlist_detail_add_tracks_reorder_and_play() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Playlists tab
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYLISTS").fetchSemanticsNodes().isNotEmpty()
        }

        // Create playlist
        composeTestRule.onNodeWithContentDescription("Create new playlist").performClick()
        composeTestRule.onNodeWithText("Playlist Name").performTextInput("Listening Session")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Listening Session").fetchSemanticsNodes().isNotEmpty()
        }

        // Open playlist detail by clicking the playlist card
        composeTestRule.onNodeWithText("Listening Session").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("This playlist has no tracks yet").fetchSemanticsNodes().isNotEmpty()
        }

        // Click "+ Add" button
        composeTestRule.onNodeWithContentDescription("Add tracks to playlist").performClick()

        // Select Solaris and Breathe
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Solaris").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("Solaris").performClick()
        composeTestRule.onNodeWithText("Breathe").performClick()

        // Click Add (2)
        composeTestRule.onNodeWithText("Add (2)").performClick()

        // Verify tracks appear in playlist
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("2 tracks", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Solaris").assertIsDisplayed()
        composeTestRule.onNodeWithText("Breathe").assertIsDisplayed()

        // Move first track down
        composeTestRule.onAllNodesWithContentDescription("Move track down").onFirst().performClick()

        // Verify Play button initiates playback through PlaybackPort
        composeTestRule.onNodeWithContentDescription("Play Playlist").performClick()
        assertTrue(testPort.playCalled)
        assertEquals(2, testPort._queue.value.size)

        // MiniPlayer appears and reflects active playback
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun playlist_delete_with_confirmation_preserves_underlying_tracks() = runBlocking {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Playlists tab
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYLISTS").fetchSemanticsNodes().isNotEmpty()
        }

        // Create playlist
        composeTestRule.onNodeWithContentDescription("Create new playlist").performClick()
        composeTestRule.onNodeWithText("Playlist Name").performTextInput("Disposable Mix")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Disposable Mix").fetchSemanticsNodes().isNotEmpty()
        }

        // Open options and delete
        composeTestRule.onNodeWithContentDescription("Options for Disposable Mix").performClick()
        composeTestRule.onNodeWithText("Delete").performClick()

        // Confirmation dialog appears
        composeTestRule.onNodeWithText("Delete Playlist").assertIsDisplayed()
        composeTestRule.onNodeWithText("Delete").performClick()

        // Verify playlist is gone from UI
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Disposable Mix").fetchSemanticsNodes().isEmpty()
        }

        // Invariant check: Underlying tracks in Room must NOT be deleted
        val trackAlphaInDb = db.trackDao().getTrackById(trackAlpha.id)
        assertNotNull(trackAlphaInDb)
        val trackBetaInDb = db.trackDao().getTrackById(trackBeta.id)
        assertNotNull(trackBetaInDb)
        assertEquals(2, db.trackDao().getTrackCount())
    }
}
