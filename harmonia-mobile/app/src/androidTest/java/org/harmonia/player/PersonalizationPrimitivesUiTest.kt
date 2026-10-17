package org.harmonia.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.personalization.RatingBar
import org.harmonia.player.presentation.personalization.RatingDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonalizationPrimitivesUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun favorite_button_renders_unfavorited_state_and_toggles() {
        var isFav by mutableStateOf(false)
        var toggleCount = 0

        composeTestRule.setContent {
            HarmoniaTheme {
                FavoriteButton(
                    isFavorite = isFav,
                    onToggleFavorite = {
                        toggleCount++
                        isFav = !isFav
                    }
                )
            }
        }

        // Verify initial unfavorited semantics and bounds
        val unfavoritedNode = composeTestRule.onNodeWithContentDescription("Favorite track, currently not favorited")
        unfavoritedNode.assertIsDisplayed()
        unfavoritedNode.assertIsOff()
        unfavoritedNode.assertWidthIsAtLeast(48.dp)
        unfavoritedNode.assertHeightIsAtLeast(48.dp)

        // Perform click
        unfavoritedNode.performClick()
        assertEquals(1, toggleCount)
        assertTrue(isFav)

        // Verify updated favorited semantics
        val favoritedNode = composeTestRule.onNodeWithContentDescription("Favorite track, currently favorited")
        favoritedNode.assertIsDisplayed()
        favoritedNode.assertIsOn()
        favoritedNode.assertWidthIsAtLeast(48.dp)
        favoritedNode.assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun rating_bar_initial_unselected_state_for_null_rating() {
        var selectedRating: Int? = null

        composeTestRule.setContent {
            HarmoniaTheme {
                RatingBar(
                    rating = null,
                    onRatingSelected = { selectedRating = it }
                )
            }
        }

        // Verify all 5 stars exist, meet minimum 48dp touch bounds, and are not selected
        for (star in 1..5) {
            val label = "Rate $star star${if (star > 1) "s" else ""}"
            val starNode = composeTestRule.onNodeWithContentDescription(label)
            starNode.assertIsDisplayed()
            starNode.assertIsNotSelected()
            starNode.assertWidthIsAtLeast(48.dp)
            starNode.assertHeightIsAtLeast(48.dp)
        }

        // Click Star 4
        composeTestRule.onNodeWithContentDescription("Rate 4 stars").performClick()
        assertEquals(4, selectedRating)
    }

    @Test
    fun rating_bar_selection_updates_and_emits_selected_integer() {
        var currentRating by mutableStateOf<Int?>(2)

        composeTestRule.setContent {
            HarmoniaTheme {
                RatingBar(
                    rating = currentRating,
                    onRatingSelected = { currentRating = it }
                )
            }
        }

        // Verify Star 2 node exists and is selected, while Star 3 is not selected
        composeTestRule.onNodeWithContentDescription("Rate 2 stars").assertIsDisplayed().assertIsSelected()
        composeTestRule.onNodeWithContentDescription("Rate 3 stars").assertIsDisplayed().assertIsNotSelected()

        // Select Star 5
        composeTestRule.onNodeWithContentDescription("Rate 5 stars").performClick()
        assertEquals(5, currentRating)

        // Select Star 1
        composeTestRule.onNodeWithContentDescription("Rate 1 star").performClick()
        assertEquals(1, currentRating)
    }

    @Test
    fun rating_dialog_renders_track_metadata_and_clear_action() {
        var cleared = false
        var selectedValue: Int? = null
        var dismissed = false

        composeTestRule.setContent {
            HarmoniaTheme {
                RatingDialog(
                    currentRating = 3,
                    onRatingSelected = { selectedValue = it },
                    onClearRating = { cleared = true },
                    onDismissRequest = { dismissed = true },
                    trackTitle = "Karma Police",
                    artistName = "Radiohead"
                )
            }
        }

        // Assert Title and metadata
        composeTestRule.onNodeWithText("TRACK RATING").assertIsDisplayed()
        composeTestRule.onNodeWithText("Karma Police").assertIsDisplayed()
        composeTestRule.onNodeWithText("Radiohead").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 of 5 stars").assertIsDisplayed()

        // Assert Clear Rating button exists and is clickable via text and accessible description
        val clearButton = composeTestRule.onNodeWithContentDescription("Clear track rating")
        clearButton.assertIsDisplayed()
        clearButton.assertWidthIsAtLeast(48.dp)
        clearButton.assertHeightIsAtLeast(48.dp)
        clearButton.performClick()
        assertTrue(cleared)

        // Assert Done button exists and is clickable via text and accessible description
        val doneButton = composeTestRule.onNodeWithContentDescription("Close rating dialog")
        doneButton.assertIsDisplayed()
        doneButton.assertWidthIsAtLeast(48.dp)
        doneButton.assertHeightIsAtLeast(48.dp)
        doneButton.performClick()
        assertTrue(dismissed)
    }

    @Test
    fun rating_dialog_unrated_state_hides_clear_button() {
        var selectedValue: Int? = null

        composeTestRule.setContent {
            HarmoniaTheme {
                RatingDialog(
                    currentRating = null,
                    onRatingSelected = { selectedValue = it },
                    onClearRating = {},
                    onDismissRequest = {},
                    trackTitle = "No Surprises"
                )
            }
        }

        composeTestRule.onNodeWithText("No Surprises").assertIsDisplayed()
        composeTestRule.onNodeWithText("Not rated").assertIsDisplayed()

        // Clear Rating button must not be present when currentRating is null
        composeTestRule.onNodeWithText("CLEAR RATING").assertDoesNotExist()

        // Selecting a star emits the value
        composeTestRule.onNodeWithContentDescription("Rate 5 stars").performClick()
        assertEquals(5, selectedValue)
    }
}
