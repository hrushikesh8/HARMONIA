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
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.SearchRepository
import org.harmonia.player.domain.repository.UserPreferencesRepository

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
    private val searchRepository: SearchRepository = ServiceLocator.getSearchRepository(),
    private val userPreferences: UserPreferencesRepository? =
        runCatching { ServiceLocator.getUserPreferencesRepository() }.getOrNull()
) : ViewModel() {

    private val _recentSearches = MutableStateFlow(userPreferences?.getRecentSearches().orEmpty())
    /** Searches you ran or opened a result from, newest first, saved on this phone. */
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val settledResults = _query
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
        }
        // Emit an empty settled state immediately so the first keystroke already reports isSearching
        .onStart { emit(SearchUiState()) }

    /**
     * Results for the last settled query, flagged as [SearchUiState.isSearching] while the
     * live query is still ahead of them (debounce window or repository round-trip).
     */
    val uiState: StateFlow<SearchUiState> = combine(_query, settledResults) { current, results ->
        results.copy(isSearching = current.isNotBlank() && current.trim() != results.query.trim())
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

    /** Remember the current query (called on keyboard Search or when a result is opened). */
    fun commitSearch() {
        updateRecent(addRecentSearch(_recentSearches.value, _query.value))
    }

    fun useRecentSearch(query: String) {
        _query.value = query
        updateRecent(addRecentSearch(_recentSearches.value, query))
    }

    fun removeRecentSearch(query: String) {
        updateRecent(_recentSearches.value.filterNot { it.equals(query, ignoreCase = true) })
    }

    fun clearRecentSearches() {
        updateRecent(emptyList())
    }

    private fun updateRecent(queries: List<String>) {
        _recentSearches.value = queries
        userPreferences?.setRecentSearches(queries)
    }
}

internal const val MAX_RECENT_SEARCHES = 8

/** Puts [query] first (trimmed, no case-insensitive duplicates), keeping at most [MAX_RECENT_SEARCHES]. */
internal fun addRecentSearch(current: List<String>, query: String): List<String> {
    val clean = query.trim().replace(Regex("\\s+"), " ")
    if (clean.isEmpty()) return current
    return (listOf(clean) + current.filterNot { it.equals(clean, ignoreCase = true) }).take(MAX_RECENT_SEARCHES)
}
