package org.harmonia.player.presentation.personalization

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.harmonia.player.domain.model.personalization.TrackPersonalization
import org.harmonia.player.domain.playback.PlaybackPort
import org.harmonia.player.domain.repository.PersonalizationRepository

/**
 * Presentation state for track personalization (Favorite and 1-5 Star Rating).
 *
 * @property trackId Active track identifier currently being presented, or null if no track is active.
 * @property isFavorite Whether the active track is marked as a user favorite.
 * @property rating User star rating between 1 and 5 inclusive, or null if unrated.
 * @property isLoading Whether the presentation state is initializing.
 * @property userErrorMessage User-facing error message describing action failures, or null if healthy.
 */
data class TrackPersonalizationUiState(
    val trackId: String? = null,
    val isFavorite: Boolean = false,
    val rating: Int? = null,
    val isLoading: Boolean = false,
    val userErrorMessage: String? = null
)

/**
 * Dedicated ViewModel for user personalization (Favorites & Ratings).
 * Uses Room SQLite via PersonalizationRepository as the single source of truth.
 *
 * Supports both automatic synchronization with active playback (via PlaybackPort)
 * and explicit track binding (e.g. for track list items, context menus, and dialogs).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonalizationViewModel(
    private val personalizationRepository: PersonalizationRepository = ServiceLocator.getPersonalizationRepository(),
    private val playbackPort: PlaybackPort? = runCatching { ServiceLocator.getPlaybackPort() }.getOrNull()
) : ViewModel() {

    private val isManualBinding = MutableStateFlow(false)
    private val manualTrackId = MutableStateFlow<String?>(null)
    private val userErrorMessageFlow = MutableStateFlow<String?>(null)
    private val mutationMutex = Mutex()

    /**
     * Resolves the active track identifier.
     * Follows manual binding if set; otherwise follows the active playback track if PlaybackPort is present.
     */
    val activeTrackId: StateFlow<String?> = combine(
        isManualBinding,
        manualTrackId,
        playbackPort?.playbackState ?: flowOf(PlaybackState())
    ) { isManual, manualId, playbackState ->
        if (isManual) manualId else playbackState.currentTrackId
    }.distinctUntilChanged().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null
    )

    /**
     * Reactive UI state for the active track.
     * Uses flatMapLatest so changing the active track immediately cancels observation
     * of the previous track, eliminating stale emissions.
     */
    val uiState: StateFlow<TrackPersonalizationUiState> = combine(
        activeTrackId.flatMapLatest { trackId ->
            if (trackId == null) {
                flowOf(TrackPersonalization(trackId = "", isFavorite = false, rating = null, updatedAt = 0L) to null)
            } else {
                personalizationRepository.observePersonalization(trackId)
                    .catch { throwable ->
                        userErrorMessageFlow.value = throwable.message ?: "Failed to observe track personalization"
                        emit(TrackPersonalization(trackId = trackId, isFavorite = false, rating = null, updatedAt = 0L))
                    }
                    .map { pers -> pers to trackId }
            }
        },
        userErrorMessageFlow
    ) { (pers, trackId), errorMsg ->
        TrackPersonalizationUiState(
            trackId = trackId,
            isFavorite = pers.isFavorite,
            rating = pers.rating,
            isLoading = false,
            userErrorMessage = errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TrackPersonalizationUiState(isLoading = true)
    )

    /**
     * Reactively exposes all favorite tracks, ordered deterministically by recency descending.
     */
    val favoriteTracks: StateFlow<List<Track>> = personalizationRepository.observeFavoriteTracks()
        .catch { throwable ->
            userErrorMessageFlow.value = throwable.message ?: "Failed to observe favorite tracks"
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Reactively exposes all rated tracks, ordered deterministically by star rating descending.
     */
    val ratedTracks: StateFlow<List<RatedTrack>> = personalizationRepository.observeRatedTracks()
        .catch { throwable ->
            userErrorMessageFlow.value = throwable.message ?: "Failed to observe rated tracks"
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Explicitly binds this ViewModel to observe a specific track (e.g. in a dialog or details screen).
     */
    fun bindTrack(trackId: String?) {
        manualTrackId.value = trackId
        isManualBinding.value = true
    }

    /**
     * Resets explicit track binding and resumes observing active playback.
     */
    fun resumeFollowingPlayback() {
        manualTrackId.value = null
        isManualBinding.value = false
    }

    /**
     * Idempotently marks or unmarks a track as favorite.
     * Operates on target trackId if provided, or the currently active track if omitted.
     */
    fun setFavorite(isFavorite: Boolean, trackId: String? = null) {
        val targetId = resolveTargetTrackId(trackId) ?: return
        viewModelScope.launch {
            try {
                userErrorMessageFlow.value = null
                mutationMutex.withLock {
                    personalizationRepository.setFavorite(targetId, isFavorite)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                userErrorMessageFlow.value = e.message ?: "Failed to update favorite status"
            }
        }
    }

    /**
     * Atomically toggles the favorite status of the active or specified track.
     */
    fun toggleFavorite(trackId: String? = null) {
        val targetId = resolveTargetTrackId(trackId) ?: return
        viewModelScope.launch {
            try {
                userErrorMessageFlow.value = null
                mutationMutex.withLock {
                    personalizationRepository.toggleFavorite(targetId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                userErrorMessageFlow.value = e.message ?: "Failed to toggle favorite status"
            }
        }
    }

    /**
     * Sets or updates a star rating (1..5) for the active or specified track.
     * Passing null clears the rating.
     */
    fun setRating(rating: Int?, trackId: String? = null) {
        val targetId = resolveTargetTrackId(trackId) ?: return
        if (rating != null && rating !in 1..5) {
            userErrorMessageFlow.value = "Personal rating must be between 1 and 5 stars inclusive, got: $rating"
            return
        }
        viewModelScope.launch {
            try {
                userErrorMessageFlow.value = null
                mutationMutex.withLock {
                    personalizationRepository.setRating(targetId, rating)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                userErrorMessageFlow.value = e.message ?: "Failed to set rating"
            }
        }
    }

    /**
     * Clears the user star rating for the active or specified track.
     */
    fun clearRating(trackId: String? = null) {
        setRating(null, trackId)
    }

    /**
     * Clears all personalization annotations (both favorite and rating) for the active or specified track.
     */
    fun clearPersonalization(trackId: String? = null) {
        val targetId = resolveTargetTrackId(trackId) ?: return
        viewModelScope.launch {
            try {
                userErrorMessageFlow.value = null
                mutationMutex.withLock {
                    personalizationRepository.clearPersonalization(targetId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                userErrorMessageFlow.value = e.message ?: "Failed to clear personalization"
            }
        }
    }

    /**
     * Clears any active user-facing error message.
     */
    fun clearError() {
        userErrorMessageFlow.value = null
    }

    private fun resolveTargetTrackId(explicitTrackId: String?): String? {
        val target = explicitTrackId ?: activeTrackId.value
        if (target == null) {
            userErrorMessageFlow.value = "No track currently selected"
        }
        return target
    }
}
