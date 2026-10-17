package org.harmonia.player.presentation.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.audio.SleepTimerPreset
import org.harmonia.player.domain.model.audio.SleepTimerState
import org.harmonia.player.domain.playback.PlaybackPort
import org.harmonia.player.domain.repository.TrackRepository

data class PlayerUiState(
    val playbackState: PlaybackState = PlaybackState(),
    val currentTrack: Track? = null,
    val queue: List<QueueItem> = emptyList(),
    val sleepTimerState: SleepTimerState = SleepTimerState()
)

class PlaybackViewModel(
    private val playbackPort: PlaybackPort = ServiceLocator.getPlaybackPort(),
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository()
) : ViewModel() {

    val uiState: StateFlow<PlayerUiState> = combine(
        playbackPort.playbackState,
        playbackPort.queue,
        playbackPort.sleepTimerState
    ) { state, queue, sleepTimer ->
        val currentTrack = state.currentTrackId?.let { id ->
            queue.firstOrNull { it.trackId == id }?.track ?: trackRepository.getTrackById(id)
        }
        PlayerUiState(
            playbackState = state,
            currentTrack = currentTrack,
            queue = queue,
            sleepTimerState = sleepTimer
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PlayerUiState()
    )

    fun play() = playbackPort.play()
    fun pause() = playbackPort.pause()
    fun next() = playbackPort.next()
    fun previous() = playbackPort.previous()
    fun seekTo(positionMs: Long) = playbackPort.seekTo(positionMs)
    fun jumpToQueueIndex(index: Int) = playbackPort.jumpToQueueIndex(index)

    fun toggleShuffle() {
        val current = playbackPort.playbackState.value.shuffleMode
        playbackPort.setShuffleMode(!current)
    }

    fun cycleRepeat() {
        val nextMode = when (playbackPort.playbackState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        playbackPort.setRepeatMode(nextMode)
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int) {
        playbackPort.setQueue(tracks, startIndex)
        playbackPort.play()
    }

    fun shuffleAll() {
        viewModelScope.launch {
            val tracks = trackRepository.observeAllTracks().firstOrNull()
            if (!tracks.isNullOrEmpty()) {
                playbackPort.setQueue(tracks.shuffled(), 0)
                playbackPort.play()
            }
        }
    }

    fun startSleepTimer(preset: SleepTimerPreset) {
        val durationMs = preset.durationMs
        if (durationMs != null) {
            playbackPort.startSleepTimer(durationMs)
        } else {
            playbackPort.startSleepTimerEndOfTrack()
        }
    }

    fun startSleepTimer(durationMs: Long) {
        playbackPort.startSleepTimer(durationMs)
    }

    fun startSleepTimerEndOfTrack() {
        playbackPort.startSleepTimerEndOfTrack()
    }

    fun cancelSleepTimer() {
        playbackPort.cancelSleepTimer()
    }
}
