package org.harmonia.player.presentation

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.preferences.InMemoryUserPreferencesRepository
import org.harmonia.player.presentation.library.AlbumSortOrder
import org.harmonia.player.presentation.library.ArtistSortOrder
import org.harmonia.player.presentation.library.FavoriteSortOrder
import org.harmonia.player.presentation.library.LibraryTab
import org.harmonia.player.presentation.library.LibraryUiState
import org.harmonia.player.presentation.library.LibraryViewModel
import org.harmonia.player.presentation.library.TrackSortOrder
import org.harmonia.player.presentation.library.libraryCategoryTitle
import org.harmonia.player.presentation.library.sortLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortMemoryTest {

    private fun newViewModel(prefs: InMemoryUserPreferencesRepository) =
        LibraryViewModel(FakeTrackRepo(), FakeAlbumRepo(), FakeArtistRepo(), userPreferences = prefs)

    @Test
    fun `sort orders survive a new view model, like reopening the app`() = runBlocking {
        val prefs = InMemoryUserPreferencesRepository()
        newViewModel(prefs).apply {
            setTrackSortOrder(TrackSortOrder.DATE_ADDED_DESC)
            setAlbumSortOrder(AlbumSortOrder.YEAR_DESC)
            setArtistSortOrder(ArtistSortOrder.NAME_DESC)
        }

        val state = newViewModel(prefs).uiState.first { !it.isLoading }
        assertEquals(TrackSortOrder.DATE_ADDED_DESC, state.trackSortOrder)
        assertEquals(AlbumSortOrder.YEAR_DESC, state.albumSortOrder)
        assertEquals(ArtistSortOrder.NAME_DESC, state.artistSortOrder)
    }

    @Test
    fun `unknown saved values fall back to the defaults`() = runBlocking {
        val prefs = InMemoryUserPreferencesRepository()
        prefs.setLibrarySort(LibraryTab.TRACKS.name, "NOT_A_SORT")

        val state = newViewModel(prefs).uiState.first { !it.isLoading }
        assertEquals(TrackSortOrder.TITLE_ASC, state.trackSortOrder)
    }

    @Test
    fun `category titles and sort labels use plain names`() {
        assertEquals("Songs", libraryCategoryTitle(LibraryTab.TRACKS))
        val state = LibraryUiState(trackSortOrder = TrackSortOrder.DATE_ADDED_DESC)
        assertEquals("Recently added", sortLabel(LibraryTab.TRACKS, state, FavoriteSortOrder.RECENTLY_FAVORITED))
        assertEquals("Recently favorited", sortLabel(LibraryTab.FAVORITES, state, FavoriteSortOrder.RECENTLY_FAVORITED))
    }
}
