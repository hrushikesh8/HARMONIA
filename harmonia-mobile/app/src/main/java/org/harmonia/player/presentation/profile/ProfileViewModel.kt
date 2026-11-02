package org.harmonia.player.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.repository.ListeningEventRepository
import org.harmonia.player.domain.repository.PersonalizationRepository
import org.harmonia.player.domain.repository.StorageSourceRepository
import org.harmonia.player.domain.repository.TrackRepository

data class ProfileUiState(
    val totalTracks: Int = 0,
    val offlineTracks: Int = 0,
    val favoritesCount: Int = 0,
    val ratedTracksCount: Int = 0,
    val storageSourcesCount: Int = 0,
    val totalListeningEvents: Int = 0,
    val recentListeningEvents: List<ListeningEvent> = emptyList(),
    val averageRating: Double? = null,
    val storageUsageEstimateMb: Double = 0.0
)

class ProfileViewModel(
    trackRepository: TrackRepository = ServiceLocator.getTrackRepository(),
    personalizationRepository: PersonalizationRepository = ServiceLocator.getPersonalizationRepository(),
    storageSourceRepository: StorageSourceRepository = ServiceLocator.getStorageSourceRepository(),
    listeningEventRepository: ListeningEventRepository = ServiceLocator.getListeningEventRepository()
) : ViewModel() {

    private val catalogStatsFlow = combine(
        trackRepository.observeAllTracks(),
        trackRepository.observeOfflineTracks()
    ) { allTracks, offlineTracks ->
        val totalSize = offlineTracks.sumOf { it.localFile?.fileSize ?: 0L }
        Triple(allTracks.size, offlineTracks.size, totalSize / (1024.0 * 1024.0))
    }

    private val personalizationStatsFlow = combine(
        personalizationRepository.observeFavoriteTracks(),
        personalizationRepository.observeRatedTracks()
    ) { favs, rated ->
        val avg = if (rated.isNotEmpty()) {
            rated.map { it.rating }.average()
        } else null
        Triple(favs.size, rated.size, avg)
    }

    private val activityFlow = listeningEventRepository.observeRecentEvents(50)

    val uiState: StateFlow<ProfileUiState> = combine(
        catalogStatsFlow,
        personalizationStatsFlow,
        storageSourceRepository.observeAllSources(),
        activityFlow
    ) { (totalTracks, offlineTracks, storageMb), (favsCount, ratedCount, avgRating), sources, events ->
        ProfileUiState(
            totalTracks = totalTracks,
            offlineTracks = offlineTracks,
            favoritesCount = favsCount,
            ratedTracksCount = ratedCount,
            storageSourcesCount = sources.size,
            totalListeningEvents = events.size,
            recentListeningEvents = events,
            averageRating = avgRating,
            storageUsageEstimateMb = storageMb
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ProfileUiState()
    )
}
