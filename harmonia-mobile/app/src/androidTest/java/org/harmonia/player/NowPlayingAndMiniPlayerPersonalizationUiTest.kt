package org.harmonia.player

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.harmonia.player.domain.model.personalization.TrackPersonalization
import org.harmonia.player.domain.repository.PersonalizationRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.presentation.MainAppContainer
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

class FakePersonalizationRepo : PersonalizationRepository {
    val state = MutableStateFlow<Map<String, TrackPersonalization>>(emptyMap())

    override fun observePersonalization(trackId: String): Flow<TrackPersonalization> {
        return state.map { it[trackId] ?: TrackPersonalization(trackId = trackId, isFavorite = false, rating = null, updatedAt = 0L) }
    }

    override fun observeFavorite(trackId: String): Flow<Boolean> {
        return observePersonalization(trackId).map { it.isFavorite }
    }

    override fun observeRating(trackId: String): Flow<Int?> {
        return observePersonalization(trackId).map { it.rating }
    }

    override fun observeFavoriteTracks(): Flow<List<Track>> = flowOf(emptyList())
    override fun observeRatedTracks(): Flow<List<RatedTrack>> = flowOf(emptyList())

    override suspend fun getPersonalization(trackId: String): TrackPersonalization {
        return state.value[trackId] ?: TrackPersonalization(trackId = trackId, isFavorite = false, rating = null, updatedAt = 0L)
    }

    override suspend fun setFavorite(trackId: String, isFavorite: Boolean) {
        val current = getPersonalization(trackId)
        val updated = current.copy(isFavorite = isFavorite, updatedAt = System.currentTimeMillis())
        state.value = state.value + (trackId to updated)
    }

    override suspend fun toggleFavorite(trackId: String): Boolean {
        val current = getPersonalization(trackId)
        val newFav = !current.isFavorite
        setFavorite(trackId, newFav)
        return newFav
    }

    override suspend fun setRating(trackId: String, rating: Int?) {
        val current = getPersonalization(trackId)
        val updated = current.copy(rating = rating, updatedAt = System.currentTimeMillis())
        state.value = state.value + (trackId to updated)
    }

    override suspend fun clearPersonalization(trackId: String) {
        state.value = state.value - trackId
    }
}

@RunWith(AndroidJUnit4::class)
class NowPlayingAndMiniPlayerPersonalizationUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val track1 = Track(
        id = "track-1",
        title = "Pyramid Song",
        artistName = "Radiohead",
        albumTitle = "Amnesiac",
        durationSec = 289.0
    )

    private val track2 = Track(
        id = "track-2",
        title = "You and Whose Army?",
        artistName = "Radiohead",
        albumTitle = "Amnesiac",
        durationSec = 191.0
    )

    private lateinit var fakeRepo: FakePersonalizationRepo
    private lateinit var testPort: TestPort
    private lateinit var playbackVm: PlaybackViewModel
    private lateinit var personalizationVm: PersonalizationViewModel

    @Before
    fun setup() {
        testPort = TestPort()
        fakeRepo = FakePersonalizationRepo()

        ServiceLocator.setPlaybackPort(testPort)
        ServiceLocator.setPersonalizationRepositoryForTesting(fakeRepo)

        val trackRepo = object : TrackRepository {
            val tracks = mapOf(track1.id to track1, track2.id to track2)
            override fun observeAllTracks(): Flow<List<Track>> = flowOf(tracks.values.toList())
            override fun observeOfflineTracks(): Flow<List<Track>> = flowOf(tracks.values.toList())
            override suspend fun getTrackById(id: String): Track? = tracks[id]
            override fun searchTracks(query: String): Flow<List<Track>> = flowOf(emptyList())
            override suspend fun upsertTracks(tracks: List<Track>) {}
            override suspend fun deleteTrack(id: String) {}
        }

        playbackVm = PlaybackViewModel(playbackPort = testPort, trackRepository = trackRepo)
        personalizationVm = PersonalizationViewModel(personalizationRepository = fakeRepo, playbackPort = testPort)
    }

    @Test
    fun now_playing_displays_favorite_state_and_toggles_reactively() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(
                    playbackViewModel = playbackVm,
                    personalizationViewModel = personalizationVm
                )
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

        // Verify FavoriteButton is displayed in unfavorited state with 48dp touch target
        val unfavButton = composeTestRule.onAllNodesWithContentDescription("Favorite track, currently not favorited").onLast()
        unfavButton.assertIsDisplayed()
        unfavButton.assertWidthIsAtLeast(48.dp)
        unfavButton.assertHeightIsAtLeast(48.dp)

        // Click Favorite Button
        unfavButton.performClick()

        // Wait for reactive favorited state
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Favorite track, currently favorited").fetchSemanticsNodes().isNotEmpty()
        }

        val favButton = composeTestRule.onAllNodesWithContentDescription("Favorite track, currently favorited").onLast()
        favButton.assertIsDisplayed()

        // Toggle back to unfavorited
        favButton.performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Favorite track, currently not favorited").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithContentDescription("Favorite track, currently not favorited").onLast().assertIsDisplayed()
    }

    @Test
    fun now_playing_opens_rating_dialog_and_sets_and_clears_rating() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(
                    playbackViewModel = playbackVm,
                    personalizationViewModel = personalizationVm
                )
            }
        }

        testPort.setQueue(listOf(track1), 0)
        testPort.play()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pyramid Song").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Now Playing sheet
        composeTestRule.onNodeWithText("Pyramid Song").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("NOW PLAYING").fetchSemanticsNodes().isNotEmpty()
        }

        // Check unrated trigger button
        val rateTrigger = composeTestRule.onNodeWithContentDescription("Rate track")
        rateTrigger.assertIsDisplayed()
        rateTrigger.assertWidthIsAtLeast(48.dp)
        rateTrigger.assertHeightIsAtLeast(48.dp)

        // Click to open RatingDialog
        rateTrigger.performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("TRACK RATING").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("TRACK RATING").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Pyramid Song").onLast().assertIsDisplayed()
        composeTestRule.onNodeWithText("Not rated").assertIsDisplayed()

        // Rate 4 stars
        composeTestRule.onNodeWithContentDescription("Rate 4 stars").performClick()

        // Done button to dismiss dialog
        composeTestRule.onNodeWithText("DONE").performClick()

        // Verify updated rating display in Now Playing
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Rating: 4 of 5 stars").fetchSemanticsNodes().isNotEmpty()
        }
        val ratedTrigger = composeTestRule.onNodeWithContentDescription("Rating: 4 of 5 stars")
        ratedTrigger.assertIsDisplayed()
        composeTestRule.onNodeWithText("4").assertIsDisplayed()

        // Open dialog again to clear rating
        ratedTrigger.performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("4 of 5 stars").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("4 of 5 stars").assertIsDisplayed()

        // Click CLEAR RATING
        composeTestRule.onNodeWithText("CLEAR RATING").performClick()

        // Verify Now Playing reverts to unrated state
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Rate track").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Rate track").assertIsDisplayed()
    }

    @Test
    fun mini_player_and_now_playing_maintain_consistent_favorite_state() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(
                    playbackViewModel = playbackVm,
                    personalizationViewModel = personalizationVm
                )
            }
        }

        testPort.setQueue(listOf(track1), 0)
        testPort.play()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pyramid Song").fetchSemanticsNodes().isNotEmpty()
        }

        // Click FavoriteButton directly on MiniPlayer
        val miniFav = composeTestRule.onNodeWithContentDescription("Favorite track, currently not favorited")
        miniFav.assertIsDisplayed()
        miniFav.performClick()

        // MiniPlayer should update to favorited
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Favorite track, currently favorited").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Favorite track, currently favorited").assertIsDisplayed()

        // Open Now Playing sheet
        composeTestRule.onNodeWithText("Pyramid Song").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("NOW PLAYING").fetchSemanticsNodes().isNotEmpty()
        }

        // Both MiniPlayer and Now Playing show favorited (no contradiction)
        val favButtons = composeTestRule.onAllNodesWithContentDescription("Favorite track, currently favorited")
        assertTrue(favButtons.fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun changing_tracks_updates_personalization_without_stale_values() {
        // Pre-populate track1 as favorited and rated 5
        kotlinx.coroutines.runBlocking {
            fakeRepo.setFavorite("track-1", true)
            fakeRepo.setRating("track-1", 5)
        }

        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(
                    playbackViewModel = playbackVm,
                    personalizationViewModel = personalizationVm
                )
            }
        }

        testPort.setQueue(listOf(track1, track2), 0)
        testPort.play()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pyramid Song").fetchSemanticsNodes().isNotEmpty()
        }

        // Open Now Playing
        composeTestRule.onNodeWithText("Pyramid Song").performClick()

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("NOW PLAYING").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Track 1 personalization: favorited and 5 stars
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Favorite track, currently favorited").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Rating: 5 of 5 stars").assertIsDisplayed()

        // Switch to Track 2
        testPort.setQueue(listOf(track1, track2), 1)

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("You and Whose Army?").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Track 2 personalization: unfavorited and unrated (no stale track 1 state)
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Rate track").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Rate track").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Favorite track, currently not favorited").onLast().assertIsDisplayed()

        // Switch back to Track 1
        testPort.setQueue(listOf(track1, track2), 0)

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Pyramid Song").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify Track 1 personalization restored
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithContentDescription("Rating: 5 of 5 stars").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithContentDescription("Rating: 5 of 5 stars").assertIsDisplayed()
        composeTestRule.onAllNodesWithContentDescription("Favorite track, currently favorited").onLast().assertIsDisplayed()
    }

    @Test
    fun no_current_track_is_safe() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer(
                    playbackViewModel = playbackVm,
                    personalizationViewModel = personalizationVm
                )
            }
        }

        // With no queue and no current track, MiniPlayer should safely return and not render
        composeTestRule.onNodeWithContentDescription("Favorite track, currently favorited").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Favorite track, currently not favorited").assertDoesNotExist()
    }
}
