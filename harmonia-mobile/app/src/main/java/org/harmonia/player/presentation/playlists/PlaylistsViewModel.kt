package org.harmonia.player.presentation.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.repository.PlaylistRepository

data class PlaylistsUiState(
    val playlists: List<Playlist> = emptyList(),
    val isLoading: Boolean = false,
    /** Cover per playlist id: the playlist's own art, else its first song's art. */
    val covers: Map<String, String?> = emptyMap()
)

class PlaylistsViewModel(
    private val playlistRepository: PlaylistRepository = ServiceLocator.getPlaylistRepository()
) : ViewModel() {

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<PlaylistsUiState> = playlistRepository.observeAllPlaylists()
        .flatMapLatest { list ->
            if (list.isEmpty()) {
                flowOf(PlaylistsUiState(playlists = list, isLoading = false))
            } else {
                combine(list.map { playlist ->
                    playlistRepository.observePlaylistItems(playlist.id).map { items ->
                        playlist.id to (playlist.artworkHash ?: items.firstNotNullOfOrNull { item ->
                            item.track?.artworkHash ?: item.track?.localFile?.fileHash
                        })
                    }
                }) { covers -> PlaylistsUiState(playlists = list, isLoading = false, covers = covers.toMap()) }
            }
        }
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

    fun addTrackToPlaylist(playlistId: String, trackId: String) {
        viewModelScope.launch {
            playlistRepository.addTrackToPlaylist(playlistId, trackId)
        }
    }

    fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        if (trackIds.isEmpty()) return
        viewModelScope.launch {
            playlistRepository.addTracksToPlaylist(playlistId, trackIds)
        }
    }

    fun createPlaylistWithTracks(name: String, trackIds: List<String>) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlist = playlistRepository.createPlaylist(name.trim())
            if (trackIds.isNotEmpty()) playlistRepository.addTracksToPlaylist(playlist.id, trackIds)
        }
    }

    fun createPlaylistWithTrack(name: String, trackId: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val playlist = playlistRepository.createPlaylist(name.trim())
            playlistRepository.addTrackToPlaylist(playlist.id, trackId)
        }
    }
}
