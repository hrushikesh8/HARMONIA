package org.harmonia.player.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.SearchRepository
import org.harmonia.player.presentation.search.SearchViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSearchRepository : SearchRepository {
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    val albums = MutableStateFlow<List<Album>>(emptyList())
    val artists = MutableStateFlow<List<Artist>>(emptyList())
    val playlists = MutableStateFlow<List<Playlist>>(emptyList())

    override fun searchTracks(query: String): Flow<List<Track>> = tracks.map { list ->
        list.filter { it.title.contains(query, ignoreCase = true) || it.artistName?.contains(query, ignoreCase = true) == true }
    }

    override fun searchAlbums(query: String): Flow<List<Album>> = albums.map { list ->
        list.filter { it.title.contains(query, ignoreCase = true) || it.artistName?.contains(query, ignoreCase = true) == true }
    }

    override fun searchArtists(query: String): Flow<List<Artist>> = artists.map { list ->
        list.filter { it.name.contains(query, ignoreCase = true) }
    }

    override fun searchPlaylists(query: String): Flow<List<Playlist>> = playlists.map { list ->
        list.filter { it.name.contains(query, ignoreCase = true) || it.description?.contains(query, ignoreCase = true) == true }
    }
}

class SearchViewModelTest {

    private lateinit var fakeRepo: FakeSearchRepository
    private lateinit var viewModel: SearchViewModel

    @Before
    fun setup() {
        fakeRepo = FakeSearchRepository()
        viewModel = SearchViewModel(fakeRepo)

        fakeRepo.tracks.value = listOf(
            Track(id = "t1", title = "Paranoid Android", artistName = "Radiohead", durationSec = 380.0),
            Track(id = "t2", title = "Karma Police", artistName = "Radiohead", durationSec = 260.0)
        )
        fakeRepo.albums.value = listOf(
            Album(id = "a1", title = "OK Computer", artistName = "Radiohead", year = 1997)
        )
        fakeRepo.artists.value = listOf(
            Artist(id = "ar1", name = "Radiohead")
        )
        fakeRepo.playlists.value = listOf(
            Playlist(id = "p1", name = "Radiohead Essentials", description = "Best tracks")
        )
    }

    @Test
    fun `initial search state is empty and not searching`() = runBlocking {
        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertTrue(state.isEmpty)
        assertEquals(0, state.totalCount)
        assertFalse(state.isSearching)
    }

    @Test
    fun `onQueryChanged updates query state and clearQuery resets it`() {
        viewModel.onQueryChanged("Karma")
        assertEquals("Karma", viewModel.query.value)

        viewModel.clearQuery()
        assertEquals("", viewModel.query.value)
    }

    @Test
    fun `search returns categorized results across all four entities`() = runBlocking {
        viewModel.onQueryChanged("Radio")

        // Wait for debounce and flow aggregation
        val state = viewModel.uiState.first { it.totalCount > 0 }
        assertFalse(state.isEmpty)
        assertEquals(2, state.matchingTracks.size)
        assertEquals(1, state.matchingAlbums.size)
        assertEquals(1, state.matchingArtists.size)
        assertEquals(1, state.matchingPlaylists.size)
        assertEquals(5, state.totalCount)
    }

    @Test
    fun `search returns empty collections when no entities match`() = runBlocking {
        viewModel.onQueryChanged("NonExistentTermXYZ")

        val state = viewModel.uiState.first { it.query == "NonExistentTermXYZ" }
        assertTrue(state.isEmpty)
        assertEquals(0, state.totalCount)
    }
}
