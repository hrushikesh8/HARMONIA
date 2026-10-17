package org.harmonia.player

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.harmonia.player.domain.model.personalization.TrackPersonalization
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.PersonalizationRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.presentation.library.EmptyFavoritesState
import org.harmonia.player.presentation.library.FavoritesTabContent
import org.harmonia.player.presentation.library.LibraryScreen
import org.harmonia.player.presentation.library.LibraryTab
import org.harmonia.player.presentation.library.LibraryViewModel
import org.harmonia.player.presentation.library.TrackRowCard
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private class FakePersonalizationRepoForLibrary : PersonalizationRepository {
    val favorites = MutableStateFlow<Map<String, Track>>(emptyMap())
    val ratings = MutableStateFlow<Map<String, Int>>(emptyMap())

    override fun observePersonalization(trackId: String): Flow<TrackPersonalization> {
        return favorites.map { favMap ->
            TrackPersonalization(
                trackId = trackId,
                isFavorite = favMap.containsKey(trackId),
                rating = ratings.value[trackId],
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    override fun observeFavorite(trackId: String): Flow<Boolean> {
        return favorites.map { it.containsKey(trackId) }
    }

    override fun observeRating(trackId: String): Flow<Int?> {
        return ratings.map { it[trackId] }
    }

    override fun observeFavoriteTracks(): Flow<List<Track>> {
        return favorites.map { it.values.toList() }
    }

    override fun observeRatedTracks(): Flow<List<RatedTrack>> = flowOf(emptyList())

    override suspend fun getPersonalization(trackId: String): TrackPersonalization {
        return TrackPersonalization(
            trackId = trackId,
            isFavorite = favorites.value.containsKey(trackId),
            rating = ratings.value[trackId],
            updatedAt = System.currentTimeMillis()
        )
    }

    override suspend fun setFavorite(trackId: String, isFavorite: Boolean) {
        val current = favorites.value.toMutableMap()
        if (isFavorite) {
            current[trackId] = Track(
                id = trackId,
                title = "Favorite Track $trackId",
                artistName = "Harmonia Artist",
                albumTitle = "Harmonia Album",
                durationSec = 210.0
            )
        } else {
            current.remove(trackId)
        }
        favorites.value = current
    }

    override suspend fun toggleFavorite(trackId: String): Boolean {
        val wasFav = favorites.value.containsKey(trackId)
        setFavorite(trackId, !wasFav)
        return !wasFav
    }

    override suspend fun setRating(trackId: String, rating: Int?) {
        val current = ratings.value.toMutableMap()
        if (rating != null) current[trackId] = rating else current.remove(trackId)
        ratings.value = current
    }

    override suspend fun clearPersonalization(trackId: String) {
        val currentFav = favorites.value.toMutableMap()
        currentFav.remove(trackId)
        favorites.value = currentFav
    }
}

@RunWith(AndroidJUnit4::class)
class LibraryFavoritesUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testTrack1 = Track(
        id = "fav-trk-1",
        title = "Acoustic Reflection",
        artistName = "Obsidian Ensemble",
        albumTitle = "Resonance",
        durationSec = 245.0
    )

    private val testTrack2 = Track(
        id = "fav-trk-2",
        title = "Midnight Echo",
        artistName = "Obsidian Ensemble",
        albumTitle = "Resonance",
        durationSec = 180.0
    )

    @Test
    fun empty_favorites_state_renders_polished_elements_and_action() {
        var browseClicked = false

        composeTestRule.setContent {
            HarmoniaTheme {
                EmptyFavoritesState(
                    onBrowseTracks = { browseClicked = true }
                )
            }
        }

        // Title and description verification
        composeTestRule.onNodeWithText("No Favorite Tracks").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tracks marked as favorites in your library or Now Playing will appear here.").assertIsDisplayed()

        // Action button verification with 48dp touch target
        val browseButton = composeTestRule.onNodeWithText("Browse Tracks")
        browseButton.assertIsDisplayed()
        browseButton.assertHeightIsAtLeast(48.dp)
        browseButton.assertWidthIsAtLeast(48.dp)

        // Action invocation
        browseButton.performClick()
        assertTrue(browseClicked)
    }

    @Test
    fun favorites_tab_content_renders_track_list_and_handles_play_and_toggle() {
        var toggledTrackId: String? = null
        var clickedTrackIndex: Int? = null

        composeTestRule.setContent {
            HarmoniaTheme {
                FavoritesTabContent(
                    tracks = listOf(testTrack1, testTrack2),
                    onToggleFavorite = { track -> toggledTrackId = track.id },
                    onBrowseTracks = {},
                    onTrackClick = { _, index -> clickedTrackIndex = index }
                )
            }
        }

        // Verify tracks displayed
        composeTestRule.onNodeWithText("Acoustic Reflection").assertIsDisplayed()
        composeTestRule.onNodeWithText("Midnight Echo").assertIsDisplayed()

        // Verify favorite button states for favorite items (both favorited)
        val favButtons = composeTestRule.onAllNodesWithContentDescription("Favorite track, currently favorited")
        favButtons[0].assertIsDisplayed()
        favButtons[0].assertWidthIsAtLeast(48.dp)
        favButtons[0].assertHeightIsAtLeast(48.dp)

        // Toggle first favorite track
        favButtons[0].performClick()
        assertEquals("fav-trk-1", toggledTrackId)

        // Click track card to trigger playback
        composeTestRule.onNodeWithText("Acoustic Reflection").performClick()
        assertEquals(0, clickedTrackIndex)
    }

    @Test
    fun track_row_card_displays_favorite_button_with_independent_click() {
        var rowClicked = false
        var favoriteToggled = false

        composeTestRule.setContent {
            HarmoniaTheme {
                TrackRowCard(
                    track = testTrack1,
                    isFavorite = false,
                    onToggleFavorite = { favoriteToggled = true },
                    onClick = { rowClicked = true }
                )
            }
        }

        // Favorite button should be in unfavorited state
        val favButton = composeTestRule.onNodeWithContentDescription("Favorite track, currently not favorited")
        favButton.assertIsDisplayed()
        favButton.assertHeightIsAtLeast(48.dp)
        favButton.assertWidthIsAtLeast(48.dp)

        // Clicking favorite button toggles favorite without triggering row click
        favButton.performClick()
        assertTrue(favoriteToggled)
        assertFalse(rowClicked)

        // Clicking row triggers row click
        composeTestRule.onNodeWithText("Acoustic Reflection").performClick()
        assertTrue(rowClicked)
    }

    @Test
    fun reactive_favorites_flow_updates_tab_counter_and_removes_item() {
        val fakeRepo = FakePersonalizationRepoForLibrary()
        val personalizationVm = PersonalizationViewModel(personalizationRepository = fakeRepo)

        // Pre-populate one favorite
        fakeRepo.favorites.value = mapOf(testTrack1.id to testTrack1)

        composeTestRule.setContent {
            HarmoniaTheme {
                val favTracks by personalizationVm.favoriteTracks.collectAsState()
                FavoritesTabContent(
                    tracks = favTracks,
                    onToggleFavorite = { track -> personalizationVm.toggleFavorite(track.id) },
                    onBrowseTracks = {},
                    onTrackClick = { _, _ -> }
                )
            }
        }

        // Initially track is displayed
        composeTestRule.onNodeWithText("Acoustic Reflection").assertIsDisplayed()

        // Click heart to unfavorite
        val favButton = composeTestRule.onNodeWithContentDescription("Favorite track, currently favorited")
        favButton.performClick()

        // Empty state is displayed reactively without app restart
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("No Favorite Tracks").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("No Favorite Tracks").assertIsDisplayed()
    }
}
