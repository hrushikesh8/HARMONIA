package org.harmonia.player.presentation.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.repository.PlaylistRepository

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false
)

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository = ServiceLocator.getPlaylistRepository()
) : ViewModel() {

    val uiState: StateFlow<PlaylistsUiState> = playlistRepository.observeAllPlaylists()
        .map { list -> PlaylistsUiState(playlists = list, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlaylistsUiState(isLoading = true)
        )

    fun createPlaylist(name: String, description: String? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            playlistRepository.createPlaylist(name.trim(), description?.trim())
        }
    }

    fun renamePlaylist(id: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(id, newName.trim())
        }
    }

    fun deletePlaylist(id: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(id)
        }
    }
}
