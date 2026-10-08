package org.harmonia.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.presentation.MainAppContainer
import org.harmonia.player.presentation.home.HomeScreen
import org.harmonia.player.presentation.library.LibraryScreen
import org.harmonia.player.presentation.playlists.PlaylistsScreen
import org.harmonia.player.presentation.search.SearchScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HarmoniaNavigationAndLibraryUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun main_navigation_displays_five_destinations_and_switches_screens() {
        composeTestRule.setContent {
            HarmoniaTheme {
                MainAppContainer()
            }
        }

        // Wait for initial Home state to emit from Flow
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("HARMONIA").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify bottom bar tabs exist
        composeTestRule.onNodeWithText("Home").assertIsDisplayed()
        composeTestRule.onNodeWithText("Library").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Playlists").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Search").assertIsDisplayed()
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed()

        // 1. Initial Home Screen is active
        composeTestRule.onNodeWithText("HARMONIA").assertIsDisplayed()
        composeTestRule.onNodeWithText("USER-OWNED MUSIC PLAYER").assertIsDisplayed()

        // 2. Navigate to Library
        composeTestRule.onNodeWithText("Library").performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("LIBRARY").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("LIBRARY").assertIsDisplayed()

        // 3. Navigate to Playlists
        composeTestRule.onAllNodesWithText("Playlists").onFirst().performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("PLAYLISTS").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("PLAYLISTS").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Create new playlist").assertIsDisplayed()

        // 4. Navigate to Search
        composeTestRule.onNodeWithText("Search").performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("SEARCH").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("SEARCH").assertIsDisplayed()
        composeTestRule.onNodeWithText("LOCAL CATALOG INDEX").assertIsDisplayed()

        // 5. Navigate to Settings
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("SETTINGS").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("SETTINGS").assertIsDisplayed()

        // Return to Home
        composeTestRule.onNodeWithText("Home").performClick()
        composeTestRule.waitUntil(3000) {
            composeTestRule.onAllNodesWithText("HARMONIA").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("HARMONIA").assertIsDisplayed()
    }

    @Test
    fun home_screen_renders_shortcuts_and_empty_state() {
        var navigatedToLibrary = false
        composeTestRule.setContent {
            HarmoniaTheme {
                HomeScreen(
                    onNavigateToLibrary = { navigatedToLibrary = true }
                )
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("HARMONIA").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("HARMONIA").assertIsDisplayed()
        composeTestRule.onNodeWithText("Artists").assertIsDisplayed()
        composeTestRule.onNodeWithText("Albums").assertIsDisplayed()
        composeTestRule.onAllNodesWithText("Playlists").onFirst().assertIsDisplayed()
        composeTestRule.onNodeWithText("Welcome to Harmonia").assertIsDisplayed()

        // Click "Open Library" in empty state
        composeTestRule.onNodeWithText("Open Library").performClick()
        assertTrue(navigatedToLibrary)
    }

    @Test
    fun library_screen_renders_tabs_and_sorting_dialog() {
        composeTestRule.setContent {
            HarmoniaTheme {
                LibraryScreen()
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("LIBRARY").fetchSemanticsNodes().isNotEmpty()
        }

        // Verify tabs
        composeTestRule.onNodeWithText("Tracks (0)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Albums (0)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Artists (0)").assertIsDisplayed()

        // Verify sort button opens dropdown menu
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

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("PLAYLISTS").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("PLAYLISTS").assertIsDisplayed()
        composeTestRule.onNodeWithText("No Playlists Yet").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Create new playlist").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create Playlist").assertIsDisplayed()
    }

    @Test
    fun search_screen_renders_search_field_and_local_indicator() {
        composeTestRule.setContent {
            HarmoniaTheme {
                SearchScreen()
            }
        }

        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("SEARCH").fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithText("SEARCH").assertIsDisplayed()
        composeTestRule.onNodeWithText("LOCAL CATALOG INDEX").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Search input field").assertIsDisplayed()
        composeTestRule.onNodeWithText("Type to search local catalog").assertIsDisplayed()
    }
}
