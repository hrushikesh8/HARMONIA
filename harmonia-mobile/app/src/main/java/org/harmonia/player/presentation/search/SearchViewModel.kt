package org.harmonia.player.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.SearchRepository

data class SearchUiState(
    val query: String = "",
    val matchingTracks: List<Track> = emptyList(),
    val matchingAlbums: List<Album> = emptyList(),
    val matchingArtists: List<Artist> = emptyList(),
    val matchingPlaylists: List<Playlist> = emptyList(),
    val isSearching: Boolean = false
) {
    val isEmpty: Boolean get() = matchingTracks.isEmpty() && matchingAlbums.isEmpty() && matchingArtists.isEmpty() && matchingPlaylists.isEmpty()
    val totalCount: Int get() = matchingTracks.size + matchingAlbums.size + matchingArtists.size + matchingPlaylists.size
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val searchRepository: SearchRepository = ServiceLocator.getSearchRepository()
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<SearchUiState> = _query
        .debounce(250)
        .distinctUntilChanged()
        .flatMapLatest { text ->
            val clean = text.trim()
            if (clean.isBlank()) {
                flowOf(SearchUiState(query = text, isSearching = false))
            } else {
                combine(
                    searchRepository.searchTracks(clean),
                    searchRepository.searchAlbums(clean),
                    searchRepository.searchArtists(clean),
                    searchRepository.searchPlaylists(clean)
                ) { tracks, albums, artists, playlists ->
                    SearchUiState(
                        query = text,
                        matchingTracks = tracks,
                        matchingAlbums = albums,
                        matchingArtists = artists,
                        matchingPlaylists = playlists,
                        isSearching = false
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SearchUiState()
        )

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun clearQuery() {
        _query.value = ""
    }
}
