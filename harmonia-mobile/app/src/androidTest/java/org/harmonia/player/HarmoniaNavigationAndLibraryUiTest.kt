package org.harmonia.player

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.entity.PlaybackStateEntity
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.presentation.MainAppContainer
import org.harmonia.player.presentation.home.HomeScreen
import org.harmonia.player.presentation.library.LibraryScreen
import org.harmonia.player.presentation.playlists.PlaylistsScreen
import org.harmonia.player.presentation.search.SearchScreen
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Navigation (three tabs: Home, Search, Library; Playlists and Settings live inside Library and
 * Profile) and the main screens' empty and seeded states.
 */
@RunWith(AndroidJUnit4::class)
class HarmoniaNavigationAndLibraryUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = HarmoniaDatabase.getInstance(context)
        db.trackDao().deleteAllTracks()
        db.playbackStateDao().clearQueue()
    }

    @After
    fun tearDown() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = HarmoniaDatabase.getInstance(context)
        db.trackDao().deleteAllTracks()
        db.playbackStateDao().clearQueue()
    }

    private fun waitForText(text: String, timeoutMs: Long = 5000) {
        composeTestRule.waitUntil(timeoutMs) {
            composeTestRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun clickTab(title: String) {
        // Each tab's icon carries the tab title as its description.
        composeTestRule.onNodeWithContentDescription(title, useUnmergedTree = true).performClick()
    }

    @Test
    fun main_navigation_has_three_tabs_and_reaches_playlists_and_settings() {
        // With no songs the Library tab shows the empty state, so seed one to get the landing list.
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            HarmoniaDatabase.getInstance(context).trackDao().upsertTrack(
                TrackEntity(id = "nav-trk-1", title = "Nav Test Track", durationSec = 120.0, createdAt = 1000L, updatedAt = 1000L)
            )
        }
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer()
            }
        }

        waitForText("HARMONIA")
        composeTestRule.onNodeWithContentDescription("Home", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Search", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Library", useUnmergedTree = true).assertIsDisplayed()

        // Search tab
        clickTab("Search")
        waitForText("Browse your library")
        composeTestRule.onNodeWithContentDescription("Search input field").assertIsDisplayed()

        // Library tab: landing list, then Playlists inside it
        clickTab("Library")
        waitForText("Favorites")
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        waitForText("Made by you")
        composeTestRule.onNodeWithContentDescription("Create new playlist").assertIsDisplayed()

        // Settings: Library -> Profile -> Settings
        composeTestRule.onNodeWithContentDescription("Back to Library").performClick()
        waitForText("Favorites")
        composeTestRule.onNodeWithContentDescription("Open Profile and Settings").performClick()
        waitForText("Your listening")
        composeTestRule.onNode(hasScrollAction()).performScrollToNode(hasText("Settings"))
        composeTestRule.onNodeWithText("Settings").performClick()
        waitForText("Appearance")

        // Back to Home
        clickTab("Home")
        waitForText("HARMONIA")
    }

    @Test
    fun home_screen_shows_welcome_state_when_library_is_empty() {
        var navigatedToLibrary = false
        composeTestRule.setContent {
            HarmoniaTheme {
                HomeScreen(onNavigateToLibrary = { navigatedToLibrary = true })
            }
        }

        waitForText("Welcome to Harmonia")
        composeTestRule.onNodeWithText("HARMONIA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open Library").performClick()
        assertTrue(navigatedToLibrary)
    }

    @Test
    fun library_screen_renders_tabs_and_sorting_menu() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            HarmoniaDatabase.getInstance(context).trackDao().upsertTrack(
                TrackEntity(id = "lib-trk-1", title = "Library Test Track", durationSec = 180.0, createdAt = 1000L, updatedAt = 1000L)
            )
        }

        composeTestRule.setContent {
            HarmoniaTheme {
                LibraryScreen()
            }
        }

        waitForText("Tracks (1)")
        composeTestRule.onNodeWithText("Tracks (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Albums (0)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Artists (0)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Library Test Track").assertIsDisplayed()

        composeTestRule.onNodeWithContentDescription("Sort library items").performClick()
        composeTestRule.onNodeWithText("Title (A-Z)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Title (Z-A)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Duration").assertIsDisplayed()
    }

    @Test
    fun playlists_screen_renders_empty_state_and_create_button() {
        composeTestRule.setContent {
            HarmoniaTheme {
                PlaylistsScreen()
            }
        }

        waitForText("Made by you")
        composeTestRule.onNodeWithText("No playlists yet").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Create new playlist").assertIsDisplayed()
    }

    @Test
    fun search_screen_renders_search_field_and_browse_tiles() {
        composeTestRule.setContent {
            HarmoniaTheme {
                SearchScreen()
            }
        }

        waitForText("Browse your library")
        composeTestRule.onNodeWithContentDescription("Search input field").assertIsDisplayed()
        composeTestRule.onNodeWithText("Songs, artists, albums, playlists").assertIsDisplayed()
    }

    @Test
    fun search_screen_typing_query_shows_clear_button_and_clearing_works() {
        composeTestRule.setContent {
            HarmoniaTheme {
                SearchScreen()
            }
        }

        waitForText("Browse your library")
        composeTestRule.onNodeWithContentDescription("Search input field").performTextInput("Radiohead")
        composeTestRule.onNodeWithContentDescription("Clear search query").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Clear search query").performClick()
        composeTestRule.onNodeWithText("Songs, artists, albums, playlists").assertIsDisplayed()
    }

    @Test
    fun search_screen_no_results_displays_helpful_empty_state() {
        composeTestRule.setContent {
            HarmoniaTheme {
                SearchScreen()
            }
        }

        waitForText("Browse your library")
        composeTestRule.onNodeWithContentDescription("Search input field").performTextInput("NonExistentItemXYZ")

        waitForText("No matches for \"NonExistentItemXYZ\"")
        composeTestRule.onNodeWithText("Check the spelling or try another word").assertIsDisplayed()
    }

    @Test
    fun home_screen_shows_jump_back_in_and_shuffle_all() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = HarmoniaDatabase.getInstance(context)
        db.trackDao().upsertTrack(
            TrackEntity(id = "home-trk-seed", title = "Home Quick Track", durationSec = 180.0, createdAt = 1000L, updatedAt = 1000L)
        )
        db.playbackStateDao().upsertPlaybackState(
            PlaybackStateEntity(singletonId = 1, currentTrackId = "home-trk-seed", currentPositionMs = 12000L, isPlaying = false, updatedAt = 1000L)
        )

        var resumeClicked = false
        var shuffleClicked = false

        composeTestRule.setContent {
            HarmoniaTheme {
                HomeScreen(
                    onResumeClick = { resumeClicked = true },
                    onShuffleAllClick = { shuffleClicked = true }
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("JUMP BACK IN").fetchSemanticsNodes().isNotEmpty()
        }
        // The round play button resumes; tapping the card itself opens the song.
        composeTestRule.onNodeWithContentDescription("Play").performClick()
        assertTrue(resumeClicked)

        composeTestRule.onNodeWithContentDescription("Shuffle all library tracks").performClick()
        assertTrue(shuffleClicked)
    }
}
