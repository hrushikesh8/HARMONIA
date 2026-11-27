package org.harmonia.player.presentation.album

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
) {
    /** Tracks grouped by disc; a single group with disc = null when the album has one disc. */
    val discs: List<DiscSection> get() = groupByDisc(tracks)
}

/** One disc of an album. [discNumber] is null for single-disc albums (no header shown). */
data class DiscSection(val discNumber: Int?, val tracks: List<Track>)

/** Album track order: disc, then track number (unnumbered last), then title. */
internal val albumTrackOrder: Comparator<Track> =
    compareBy<Track> { it.discNumber }.thenBy { it.trackNumber ?: Int.MAX_VALUE }.thenBy { it.title.lowercase() }

internal fun groupByDisc(tracks: List<Track>): List<DiscSection> {
    if (tracks.isEmpty()) return emptyList()
    val byDisc = tracks.groupBy { it.discNumber }
    if (byDisc.size == 1) return listOf(DiscSection(null, tracks))
    return byDisc.toSortedMap().map { (disc, discTracks) -> DiscSection(disc, discTracks) }
}

/** "48 min", "1 hr 12 min", or "40 sec" for very short albums. */
internal fun formatAlbumDuration(totalSec: Double): String {
    val totalMinutes = (totalSec / 60).toInt()
    return when {
        totalMinutes >= 60 -> "${totalMinutes / 60} hr ${totalMinutes % 60} min"
        totalMinutes > 0 -> "$totalMinutes min"
        else -> "${totalSec.toInt()} sec"
    }
}

/**
 * Album page state. Follows the database live, so a rescan, a deleted file or new tags show up
 * without leaving the page.
 */
class AlbumDetailViewModel(
    private val albumRepository: AlbumRepository = ServiceLocator.getAlbumRepository(),
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlbumDetailUiState())
    val uiState: StateFlow<AlbumDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var currentAlbumId: String? = null

    fun loadAlbum(albumId: String) {
        if (albumId == currentAlbumId && observeJob?.isActive == true) return
        currentAlbumId = albumId
        observeJob?.cancel()
        _uiState.value = AlbumDetailUiState(isLoading = true)
        observeJob = viewModelScope.launch {
            combine(
                albumRepository.observeAllAlbums().map { albums -> albums.firstOrNull { it.id == albumId } },
                trackRepository.observeAllTracks().map { tracks ->
                    tracks.filter { it.albumId == albumId }.sortedWith(albumTrackOrder)
                }
            ) { album, tracks ->
                AlbumDetailUiState(
                    album = album,
                    tracks = tracks,
                    totalDurationSec = tracks.sumOf { it.durationSec },
                    isLoading = false
                )
            }
                .catch { error ->
                    emit(
                        AlbumDetailUiState(
                            isLoading = false,
                            errorMessage = error.message ?: "Something went wrong"
                        )
                    )
                }
                .collect { _uiState.value = it }
        }
    }

    fun retry() {
        val id = currentAlbumId ?: return
        observeJob?.cancel()
        observeJob = null
        currentAlbumId = null
        loadAlbum(id)
    }
}
