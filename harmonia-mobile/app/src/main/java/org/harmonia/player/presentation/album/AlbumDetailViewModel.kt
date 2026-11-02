package org.harmonia.player.presentation.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.TrackRepository

data class AlbumDetailUiState(
    val album: Album? = null,
    val tracks: List<Track> = emptyList(),
    val totalDurationSec: Double = 0.0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class AlbumDetailViewModel(
    private val albumRepository: AlbumRepository = ServiceLocator.getAlbumRepository(),
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    fun loadAlbum(albumId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val album = albumRepository.getAlbumById(albumId)
                val unsortedTracks = trackRepository.getTracksByAlbum(albumId)
                val tracks = unsortedTracks.sortedWith(
                    compareBy<Track> { it.discNumber ?: 1 }
                        .thenBy { it.trackNumber ?: 999 }
                        .thenBy { it.title }
                )
                val totalSec = tracks.sumOf { it.durationSec }
                _uiState.value = AlbumDetailUiState(
                    album = album,
                    tracks = tracks,
                    totalDurationSec = totalSec,
                    isLoading = false
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Failed to load album details"
                )
            }
        }
    }
}
