package org.harmonia.player.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.TrackRepository

data class LibraryUiState(
    val tracks: List<Track> = emptyList(),
    val offlineOnly: Boolean = false,
    val searchQuery: String = "",
    val totalCount: Int = 0,
    val offlineCount: Int = 0,
    val isLoading: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository()
) : ViewModel() {

    private val _offlineOnly = MutableStateFlow(false)
    val offlineOnly: StateFlow<Boolean> = _offlineOnly

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val uiState: StateFlow<LibraryUiState> = combine(
        _offlineOnly,
        _searchQuery
    ) { offlineOnly, query ->
        Pair(offlineOnly, query)
    }.flatMapLatest { (offlineOnly, query) ->
        val tracksFlow = if (query.isBlank()) {
            if (offlineOnly) trackRepository.observeOfflineTracks()
            else trackRepository.observeAllTracks()
        } else {
            trackRepository.searchTracks(query)
        }

        combine(
            tracksFlow,
            trackRepository.observeAllTracks(),
            trackRepository.observeOfflineTracks()
        ) { filteredTracks, allTracks, offlineTracks ->
            val finalTracks = if (offlineOnly && query.isNotBlank()) {
                filteredTracks.filter { it.isAvailableOffline }
            } else {
                filteredTracks
            }

            LibraryUiState(
                tracks = finalTracks,
                offlineOnly = offlineOnly,
                searchQuery = query,
                totalCount = allTracks.size,
                offlineCount = offlineTracks.size,
                isLoading = false
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(isLoading = true)
    )

    fun toggleOfflineOnly() {
        _offlineOnly.value = !_offlineOnly.value
    }

    fun setOfflineOnly(enabled: Boolean) {
        _offlineOnly.value = enabled
    }

    fun onSearchQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery
    }
}
