package org.harmonia.player.presentation.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.PlaylistItem
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.PlaylistRepository
import org.harmonia.player.domain.repository.TrackRepository

data class PlaylistDetailUiState(
    val playlist: Playlist? = null,
    val items: List<PlaylistItem> = emptyList(),
    val availableLibraryTracks: List<Track> = emptyList(),
    val isLoading: Boolean = true
)

class PlaylistDetailViewModel(
    val playlistId: String,
    private val playlistRepository: PlaylistRepository = ServiceLocator.getPlaylistRepository(),
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistDetailUiState())
    val uiState: StateFlow<PlaylistDetailUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            // Observe playlist metadata and items
            launch {
                playlistRepository.observeAllPlaylists().collect { all ->
                    val pl = all.firstOrNull { it.id == playlistId }
                    _uiState.value = _uiState.value.copy(playlist = pl)
                }
            }

            launch {
                playlistRepository.observePlaylistItems(playlistId).collect { items ->
                    val sorted = items.sortedBy { it.position }
                    _uiState.value = _uiState.value.copy(
                        items = sorted,
                        isLoading = false
                    )
                }
            }

            launch {
                trackRepository.observeAllTracks().collect { tracks ->
                    _uiState.value = _uiState.value.copy(availableLibraryTracks = tracks)
                }
            }
        }
    }

    fun renamePlaylist(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, trimmed)
        }
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            onDeleted()
        }
    }

    fun addTracks(trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.addTracksToPlaylist(playlistId, trackIds)
        }
    }

    fun removeTrack(trackId: String) {
        viewModelScope.launch {
            playlistRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun moveTrackUp(index: Int) {
        if (index <= 0) return
        viewModelScope.launch {
            playlistRepository.reorderPlaylistItem(playlistId, fromPosition = index, toPosition = index - 1)
        }
    }

    fun moveTrackDown(index: Int) {
        val currentSize = _uiState.value.items.size
        if (index >= currentSize - 1) return
        viewModelScope.launch {
            playlistRepository.reorderPlaylistItem(playlistId, fromPosition = index, toPosition = index + 1)
        }
    }

    class Factory(
        private val playlistId: String,
        private val playlistRepository: PlaylistRepository = ServiceLocator.getPlaylistRepository(),
        private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlaylistDetailViewModel(playlistId, playlistRepository, trackRepository) as T
        }
    }
}
