package org.harmonia.player.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.PlaybackStateRepository
import org.harmonia.player.domain.repository.TrackRepository

data class HomeUiState(
    val recentTracks: List<Track> = emptyList(),
    val offlineTrackCount: Int = 0,
    val totalTrackCount: Int = 0,
    val playbackState: PlaybackState? = null,
    val isLoading: Boolean = false
)

class HomeViewModel(
    trackRepository: TrackRepository = ServiceLocator.getTrackRepository(),
    playbackStateRepository: PlaybackStateRepository = ServiceLocator.getPlaybackStateRepository()
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        trackRepository.observeAllTracks(),
        trackRepository.observeOfflineTracks(),
        playbackStateRepository.observePlaybackState()
    ) { allTracks, offlineTracks, playbackState ->
        HomeUiState(
            recentTracks = allTracks.take(10),
            offlineTrackCount = offlineTracks.size,
            totalTrackCount = allTracks.size,
            playbackState = playbackState,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )
}
