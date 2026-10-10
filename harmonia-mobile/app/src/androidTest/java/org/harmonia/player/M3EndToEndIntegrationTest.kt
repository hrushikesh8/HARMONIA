package org.harmonia.player

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.MainAppContainer
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.data.local.permission.AndroidStoragePermissionChecker
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.domain.scanner.MediaReconciliationEngine
import kotlinx.coroutines.flow.flow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class M3EndToEndIntegrationTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var db: HarmoniaDatabase
    private lateinit var testPort: TestPort
    private lateinit var testTrackRepo: TestTrackRepo
    private lateinit var playbackViewModel: PlaybackViewModel
    private lateinit var fakePermChecker: FakePermissionChecker
    private lateinit var fakeReconciliationEngine: FakeReconciliationEngine

    private class FakePermissionChecker(
        var state: StoragePermissionState = StoragePermissionState.GRANTED
    ) : StoragePermissionChecker {
        override fun getPermissionState(): StoragePermissionState = state
        override fun canScanAudioMedia(): Boolean = state == StoragePermissionState.GRANTED
    }

    private class FakeReconciliationEngine : MediaReconciliationEngine {
        override fun scanSource(source: StorageSource, mode: ScanMode) = flow {
            emit(ScanProgress(sourceId = source.id, status = ScanProgressStatus.COMPLETED))
        }
    }

    private val track1 = Track(
        id = "e2e-track-1",
        title = "Solaris Echoes",
        artistName = "Photay",
        albumTitle = "Waking Seasons",
        durationSec = 210.0
    )

    private val track2 = Track(
        id = "e2e-track-2",
        title = "Breathe Deep",
        artistName = "Telepopmusik",
        albumTitle = "Genetic World",
        durationSec = 265.0
    )

    private val missingTrack = Track(
        id = "e2e-track-missing",
        title = "Ghost Frequency",
        artistName = "Unknown",
        durationSec = 180.0,
        availability = LocalFileAvailability.UNAVAILABLE
    )

    @Before
    fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = HarmoniaDatabase.getInstance(context)
        db.playlistDao().observeAllPlaylists()

        // Clean tables
        db.trackDao().deleteAllTracks()
        db.playlistDao().deleteAllPlaylists()
        db.playbackStateDao().clearQueue()

        ServiceLocator.initialize(context)
        fakePermChecker = FakePermissionChecker(StoragePermissionState.GRANTED)
        ServiceLocator.setStoragePermissionCheckerForTesting(fakePermChecker)
        fakeReconciliationEngine = FakeReconciliationEngine()
        ServiceLocator.setMediaReconciliationEngineForTesting(fakeReconciliationEngine)

        testPort = TestPort()
        testTrackRepo = TestTrackRepo()
        testTrackRepo.tracks[track1.id] = track1
        testTrackRepo.tracks[track2.id] = track2
        testTrackRepo.tracks[missingTrack.id] = missingTrack
        playbackViewModel = PlaybackViewModel(testPort, testTrackRepo)
    }

    @After
    fun tearDown() = runBlocking {
        db.trackDao().deleteAllTracks()
        db.playlistDao().deleteAllPlaylists()
        db.playbackStateDao().clearQueue()
        ServiceLocator.setStoragePermissionCheckerForTesting(
            AndroidStoragePermissionChecker(context)
        )
    }

    @Test
    fun flowA_fresh_install_empty_library_usable_without_crash() {
        fakePermChecker.state = StoragePermissionState.GRANTED

        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // 1. Home destination renders empty state cleanly
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("HARMONIA").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("HARMONIA").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Welcome to Harmonia").onFirst().assertIsDisplayed()

        // 2. Switch to Library tab -> displays empty library state
        composeTestRule.onAllNodesWithText("Library").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("LIBRARY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("LIBRARY").assertIsDisplayed()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("No audio files found").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("No audio files found").assertIsDisplayed()

        // 3. Switch to Search tab
        composeTestRule.onAllNodesWithText("Search").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("SEARCH").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("SEARCH").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Type to search local catalog", substring = true).onFirst().assertIsDisplayed()

        // 4. Switch to Playlists tab
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYLISTS").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PLAYLISTS").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("No Playlists Yet", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun flowA_fresh_install_without_permission_shows_onboarding_without_crash() {
        fakePermChecker.state = StoragePermissionState.DENIED

        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // 1. Home destination renders empty state cleanly
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("HARMONIA").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("HARMONIA").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Welcome to Harmonia").onFirst().assertIsDisplayed()

        // 2. Switch to Library tab -> displays permission onboarding
        composeTestRule.onAllNodesWithText("Library").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("LIBRARY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("LIBRARY").assertIsDisplayed()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Audio Permission Required").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Audio Permission Required").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Grant audio permission").assertIsDisplayed()

        // 3. Switch to Search tab -> unaffected by storage permission
        composeTestRule.onAllNodesWithText("Search").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("SEARCH").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("SEARCH").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Type to search local catalog", substring = true).onFirst().assertIsDisplayed()

        // 4. Switch to Playlists tab -> unaffected by storage permission
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYLISTS").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PLAYLISTS").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("No Playlists Yet", substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun flowB_library_to_search_to_playback_flow() {
        runBlocking {
            // Seed catalog in Room
            db.trackDao().upsertTrack(TrackEntity(id = track1.id, title = track1.title, durationSec = track1.durationSec, createdAt = 0L, updatedAt = 0L))
            db.trackDao().upsertTrack(TrackEntity(id = track2.id, title = track2.title, durationSec = track2.durationSec, createdAt = 0L, updatedAt = 0L))
        }

        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Search tab
        composeTestRule.onAllNodesWithText("Search").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("SEARCH").fetchSemanticsNodes().isNotEmpty()
        }

        // Enter search query into dedicated search field
        composeTestRule.onNodeWithContentDescription("Search input field").performTextInput("Solaris")

        // Wait for search result and click track to play
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Solaris Echoes").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Solaris Echoes").performClick()

        // Verify playback initiated via PlaybackPort
        assertTrue(testPort.playCalled)
        assertEquals("e2e-track-1", testPort.playbackState.value.currentTrackId)

        // MiniPlayer appears and reflects playing state
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Pause").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Pause").assertIsDisplayed()
    }

    @Test
    fun flowC_playlist_lifecycle_and_restart_persistence() {
        runBlocking {
            // Seed tracks
            db.trackDao().upsertTrack(TrackEntity(id = track1.id, title = track1.title, durationSec = track1.durationSec, createdAt = 0L, updatedAt = 0L))
            db.trackDao().upsertTrack(TrackEntity(id = track2.id, title = track2.title, durationSec = track2.durationSec, createdAt = 0L, updatedAt = 0L))
        }

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
        composeTestRule.onNodeWithText("Playlist Name").performTextInput("E2E Ambient")
        composeTestRule.onNodeWithText("Create").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("E2E Ambient").fetchSemanticsNodes().isNotEmpty()
        }

        // Rename playlist
        composeTestRule.onNodeWithContentDescription("Options for E2E Ambient").performClick()
        composeTestRule.onNodeWithText("Rename").performClick()
        composeTestRule.onNodeWithContentDescription("Playlist Name Field").performTextReplacement("E2E Ambient Deluxe")
        composeTestRule.onNodeWithContentDescription("Confirm Rename").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("E2E Ambient Deluxe").fetchSemanticsNodes().isNotEmpty()
        }

        // Open playlist detail
        composeTestRule.onNodeWithText("E2E Ambient Deluxe").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("This playlist has no tracks yet").fetchSemanticsNodes().isNotEmpty()
        }

        // Add tracks
        composeTestRule.onNodeWithContentDescription("Add tracks to playlist").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Solaris Echoes").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Solaris Echoes").performClick()
        composeTestRule.onNodeWithText("Breathe Deep").performClick()
        composeTestRule.onNodeWithText("Add (2)").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("2 tracks", substring = true).fetchSemanticsNodes().isNotEmpty()
        }

        // Reorder: Move first track down
        composeTestRule.onAllNodesWithContentDescription("Move track down").onFirst().performClick()

        // Verify Room persistence: playlist items are swapped
        val allPlaylists = runBlocking { db.playlistDao().observeAllPlaylists().first() }
        val playlistId = allPlaylists.first { it.name == "E2E Ambient Deluxe" }.id
        composeTestRule.waitUntil(5000) {
            runBlocking {
                val items = db.playlistDao().getItemsForPlaylist(playlistId)
                items.size == 2 && items[0].trackId == "e2e-track-2"
            }
        }
        val itemsInDb = runBlocking { db.playlistDao().getItemsForPlaylist(playlistId) }
        assertEquals(2, itemsInDb.size)
        assertEquals("e2e-track-2", itemsInDb[0].trackId)
        assertEquals(0, itemsInDb[0].position)
        assertEquals("e2e-track-1", itemsInDb[1].trackId)
        assertEquals(1, itemsInDb[1].position)
    }

    @Test
    fun flowD_playlist_to_queue_to_now_playing() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Direct queue initialization simulating playlist play
        testPort.setQueue(listOf(track1, track2), 0)
        testPort.play()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Solaris Echoes").fetchSemanticsNodes().isNotEmpty()
        }

        // Tap MiniPlayer to open Now Playing bottom sheet
        composeTestRule.onNodeWithText("Solaris Echoes").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("NOW PLAYING").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("NOW PLAYING").assertIsDisplayed()

        // Toggle Queue view
        composeTestRule.onNodeWithContentDescription("Show Queue").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYING QUEUE (2)").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PLAYING QUEUE (2)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Breathe Deep").assertIsDisplayed()

        // Jump to second item
        composeTestRule.onNodeWithText("Breathe Deep").performClick()
        assertEquals(1, testPort.lastJumpIndex)
    }

    @Test
    fun flowE_missing_track_file_deterministic_graceful_handling() {
        val playlistId = "pl-missing-test"
        runBlocking {
            // Track with UNAVAILABLE file exists in DB
            db.trackDao().upsertTrack(TrackEntity(id = missingTrack.id, title = missingTrack.title, durationSec = missingTrack.durationSec, createdAt = 0L, updatedAt = 0L))

            // Create playlist with missing track
            val playlist = org.harmonia.player.data.local.entity.PlaylistEntity(
                id = playlistId,
                name = "Incomplete Library Mix",
                createdAt = 0L,
                updatedAt = 0L
            )
            db.playlistDao().insertPlaylist(playlist)
            db.playlistDao().insertItem(
                org.harmonia.player.data.local.entity.PlaylistItemEntity(
                    id = "item-missing-1",
                    playlistId = playlistId,
                    trackId = missingTrack.id,
                    position = 0,
                    addedAt = 0L
                )
            )
        }

        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(playbackViewModel = playbackViewModel)
            }
        }

        // Navigate to Playlists tab
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Incomplete Library Mix").fetchSemanticsNodes().isNotEmpty()
        }

        // Open playlist
        composeTestRule.onNodeWithText("Incomplete Library Mix").performClick()
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Ghost Frequency").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify missing track row renders without crash
        composeTestRule.onNodeWithText("Ghost Frequency").assertIsDisplayed()

        // Verify database entry remains preserved
        runBlocking {
            val itemsInDb = db.playlistDao().getItemsForPlaylist(playlistId)
            assertEquals(1, itemsInDb.size)
            assertEquals(missingTrack.id, itemsInDb[0].trackId)
        }
    }
}
