package org.harmonia.player.presentation

import org.harmonia.player.data.local.preferences.InMemoryUserPreferencesRepository
import org.harmonia.player.presentation.search.MAX_RECENT_SEARCHES
import org.harmonia.player.presentation.search.SearchViewModel
import org.harmonia.player.presentation.search.addRecentSearch
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {

    @Test
    fun `newest search goes first and duplicates are merged ignoring case`() {
        val result = addRecentSearch(listOf("Radiohead", "daft punk"), "  Daft   Punk ")
        assertEquals(listOf("Daft Punk", "Radiohead"), result)
    }

    @Test
    fun `blank searches are ignored`() {
        assertEquals(listOf("a"), addRecentSearch(listOf("a"), "   "))
    }

    @Test
    fun `history is capped`() {
        var list = emptyList<String>()
        repeat(MAX_RECENT_SEARCHES + 3) { list = addRecentSearch(list, "q$it") }
        assertEquals(MAX_RECENT_SEARCHES, list.size)
        assertEquals("q${MAX_RECENT_SEARCHES + 2}", list.first())
    }

    @Test
    fun `recent searches are saved and survive a new screen`() {
        val prefs = InMemoryUserPreferencesRepository()
        SearchViewModel(FakeSearchRepository(), prefs).apply {
            onQueryChanged("Nova")
            commitSearch()
            useRecentSearch("Echo")
            removeRecentSearch("nova")
        }
        assertEquals(listOf("Echo"), SearchViewModel(FakeSearchRepository(), prefs).recentSearches.value)
    }
}
