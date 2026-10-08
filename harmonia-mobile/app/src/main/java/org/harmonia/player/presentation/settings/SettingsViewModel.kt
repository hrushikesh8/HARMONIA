package org.harmonia.player.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.repository.TrackRepository

data class SettingsUiState(
    val totalTracks: Int = 0,
    val offlineTracks: Int = 0,
    val appVersion: String = "0.1.0",
    val walModeEnabled: Boolean = true,
    val storageUsageEstimateMb: Double = 0.0
)

class SettingsViewModel(
    trackRepository: TrackRepository = ServiceLocator.getTrackRepository()
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        trackRepository.observeAllTracks(),
        trackRepository.observeOfflineTracks()
    ) { allTracks, offlineTracks ->
        val totalSize = offlineTracks.sumOf { it.localFile?.fileSize ?: 0L }
        SettingsUiState(
            totalTracks = allTracks.size,
            offlineTracks = offlineTracks.size,
            appVersion = "0.1.0-alpha",
            walModeEnabled = true,
            storageUsageEstimateMb = totalSize / (1024.0 * 1024.0)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )
}
