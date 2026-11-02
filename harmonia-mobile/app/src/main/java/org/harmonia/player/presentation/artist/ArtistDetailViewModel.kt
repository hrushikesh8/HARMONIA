package org.harmonia.player.presentation.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.TrackRepository

data class ArtistDetailUiState(
    val artist: Artist? = null,
    val albums: List<Album> = emptyList(),
    val tracks: List<Track> = emptyList(),
    val totalDurationSec: Double = 0.0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

class ArtistDetailViewModel(
    private val artistRepository: ArtistRepository = ServiceLocator.getArtistRepository(),
    private val albumRepository: AlbumRepository = ServiceLocator.getAlbumRepository(),
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    fun loadArtist(artistId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val artist = artistRepository.getArtistById(artistId)
                val rawAlbums = albumRepository.observeAlbumsByArtist(artistId).firstOrNull() ?: emptyList()
                val albums = rawAlbums.distinctBy { it.id }.sortedWith(
                    compareByDescending<Album> { it.year ?: 0 }.thenBy { it.title }
                )
                val primaryTracks = trackRepository.getTracksByArtist(artistId)

                // Multi-artist credit handling: find collaboration tracks matching artist name
                val allTracks = if (artist != null) {
                    val allLocal = trackRepository.observeAllTracks().firstOrNull() ?: emptyList()
                    val artistNameLower = artist.name.lowercase().trim()
                    val collabTracks = allLocal.filter { track ->
                        val trackArtistLower = track.artistName?.lowercase() ?: ""
                        track.artistId != artistId && (
                            trackArtistLower.contains(artistNameLower) ||
                            trackArtistLower.split(",", ";", "&", "feat.", "ft.", "/").any { it.trim() == artistNameLower }
                        )
                    }
                    (primaryTracks + collabTracks).distinctBy { it.id }
                } else {
                    primaryTracks
                }
                val totalSec = allTracks.sumOf { it.durationSec }

                _uiState.value = ArtistDetailUiState(
                    artist = artist,
                    albums = albums,
                    tracks = allTracks,
                    totalDurationSec = totalSec,
                    isLoading = false
                )
            } catch (t: Throwable) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Failed to load artist details"
                )
            }
        }
    }
}
