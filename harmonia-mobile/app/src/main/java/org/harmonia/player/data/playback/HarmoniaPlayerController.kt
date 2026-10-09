package org.harmonia.player.data.playback

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.ListeningEventType
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.PlaybackStatus
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.playback.PlaybackPort
import org.harmonia.player.domain.repository.ListeningEventRepository
import org.harmonia.player.domain.repository.LocalFileRepository
import org.harmonia.player.domain.repository.PlaybackStateRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.service.HarmoniaPlaybackService

/**
 * Implementation of [PlaybackPort] bridging the domain layer to Media3 [MediaController].
 * Maintains the authoritative queue, synchronizes state with [PlaybackStateRepository],
 * and logs factual [ListeningEvent]s to [ListeningEventRepository].
 */
class HarmoniaPlayerController(
    private val context: Context? = null,
    private val playbackStateRepository: PlaybackStateRepository,
    private val listeningEventRepository: ListeningEventRepository,
    private val trackRepository: TrackRepository,
    private val localFileRepository: LocalFileRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val playerOverride: Player? = null,
    private val enablePositionPolling: Boolean = true
) : PlaybackPort {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: Player? = null
    private var activePlayerListener: Player.Listener? = null

    // Tracking for factual ListeningEvent telemetry
    private var activeTrackStartTime: Long = 0L
    private var activeTrackDurationSec: Double = 0.0
    private var hasRecordedCompletion: Boolean = false
    private var wasExplicitlyPaused: Boolean = false

    // Real-time authoritative position polling while playing
    private var positionPollingJob: Job? = null

    private fun startPositionPolling(controller: Player) {
        if (!enablePositionPolling) return
        positionPollingJob?.cancel()
        positionPollingJob = scope.launch {
            while (isActive) {
                if (controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = if (controller.duration > 0) controller.duration else _playbackState.value.durationMs
                    _playbackState.value = _playbackState.value.copy(
                        currentPositionMs = pos,
                        durationMs = dur
                    )
                }
                delay(250L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }

    init {
        if (playerOverride != null) {
            mediaController = playerOverride
            setupPlayerListener(playerOverride)
            syncControllerWithQueue(playerOverride)
        } else {
            initializeSessionConnection()
        }
        restorePersistedState()
    }

    private fun initializeSessionConnection() {
        val ctx = context ?: return
        val sessionToken = SessionToken(
            ctx,
            ComponentName(ctx, HarmoniaPlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(ctx, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    val controller = controllerFuture?.get() ?: return@addListener
                    mediaController = controller
                    setupPlayerListener(controller)
                    syncControllerWithQueue(controller)
                } catch (e: Exception) {
                    _playbackState.value = _playbackState.value.copy(
                        status = PlaybackStatus.ERROR,
                        errorMessage = "Failed to connect to playback service: ${e.message}"
                    )
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun setupPlayerListener(controller: Player) {
        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateStateFromController(controller)
                if (!playWhenReady) {
                    if (reason != Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                        controller.playbackState != Player.STATE_ENDED &&
                        _playbackState.value.currentTrackId != null
                    ) {
                        if (!wasExplicitlyPaused) {
                            wasExplicitlyPaused = true
                            recordEvent(ListeningEventType.PAUSED)
                        }
                    }
                }
            }

            override fun onPlaybackSuppressionReasonChanged(playbackSuppressionReason: Int) {
                updateStateFromController(controller)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateStateFromController(controller)
                val currentTrackId = _playbackState.value.currentTrackId
                if (currentTrackId != null && isPlaying) {
                    if (wasExplicitlyPaused) {
                        wasExplicitlyPaused = false
                        recordEvent(ListeningEventType.RESUMED)
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                updateStateFromController(controller)
                if (state == Player.STATE_ENDED) {
                    wasExplicitlyPaused = false
                    checkAndRecordCompletion()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                activeTrackStartTime = System.currentTimeMillis()
                hasRecordedCompletion = false
                wasExplicitlyPaused = false

                if (mediaItem != null) {
                    val trackId = mediaItem.mediaId
                    val queueIdx = controller.currentMediaItemIndex

                    scope.launch {
                        val track = trackRepository.getTrackById(trackId)
                        val localFiles = localFileRepository.observeLocalFilesForTrack(trackId).firstOrNull() ?: emptyList()
                        val localFile = localFiles.firstOrNull() ?: track?.localFile
                        activeTrackDurationSec = track?.durationSec ?: 0.0

                        _playbackState.value = _playbackState.value.copy(
                            currentTrackId = trackId,
                            currentLocalFileId = localFile?.id,
                            activeQueueIndex = queueIdx,
                            durationMs = (activeTrackDurationSec * 1000).toLong()
                        )
                        recordEvent(ListeningEventType.STARTED)
                        persistCurrentState()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playbackState.value = _playbackState.value.copy(
                    status = PlaybackStatus.ERROR,
                    errorMessage = "Playback error [${error.errorCodeName}]: ${error.message}"
                )
            }
        }
        controller.addListener(listener)
        activePlayerListener = listener
    }

    internal fun getPlayerListenerForTesting(): Player.Listener? = activePlayerListener

    private fun updateStateFromController(controller: Player) {
        val status = when {
            controller.playerError != null -> PlaybackStatus.ERROR
            controller.playbackState == Player.STATE_IDLE -> PlaybackStatus.IDLE
            controller.playbackState == Player.STATE_ENDED -> PlaybackStatus.ENDED
            !controller.playWhenReady -> PlaybackStatus.PAUSED
            controller.playbackSuppressionReason != Player.PLAYBACK_SUPPRESSION_REASON_NONE -> PlaybackStatus.SUPPRESSED
            controller.playbackState == Player.STATE_BUFFERING -> PlaybackStatus.BUFFERING
            controller.playbackState == Player.STATE_READY && controller.isPlaying -> PlaybackStatus.PLAYING
            controller.playbackState == Player.STATE_READY -> PlaybackStatus.READY
            else -> PlaybackStatus.IDLE
        }

        val dur = if (controller.duration > 0) controller.duration else _playbackState.value.durationMs

        _playbackState.value = _playbackState.value.copy(
            isPlaying = controller.isPlaying,
            status = status,
            playWhenReady = controller.playWhenReady,
            playbackSuppressionReason = controller.playbackSuppressionReason,
            currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
            durationMs = dur,
            activeQueueIndex = controller.currentMediaItemIndex,
            volumeScalar = controller.volume,
            shuffleMode = controller.shuffleModeEnabled,
            repeatMode = when (controller.repeatMode) {
                Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                else -> RepeatMode.OFF
            }
        )
        if (controller.isPlaying) {
            if (positionPollingJob == null || positionPollingJob?.isActive == false) {
                startPositionPolling(controller)
            }
        } else {
            stopPositionPolling()
        }
        persistCurrentState()
    }

    private fun restorePersistedState() {
        scope.launch {
            val savedQueue = playbackStateRepository.observeQueue().firstOrNull() ?: emptyList()
            if (savedQueue.isNotEmpty()) {
                _queue.value = savedQueue
            }
            val savedState = playbackStateRepository.observePlaybackState().firstOrNull()
            if (savedState != null) {
                _playbackState.value = savedState.copy(
                    isPlaying = false,
                    status = PlaybackStatus.IDLE,
                    playWhenReady = false,
                    playbackSuppressionReason = 0
                )
            }
        }
    }

    private fun persistCurrentState() {
        scope.launch {
            playbackStateRepository.savePlaybackState(_playbackState.value)
        }
    }

    private fun recordEvent(type: ListeningEventType) {
        val trackId = _playbackState.value.currentTrackId ?: return
        val localFileId = _playbackState.value.currentLocalFileId
        val posMs = _playbackState.value.currentPositionMs
        val durMs = _playbackState.value.durationMs

        scope.launch {
            listeningEventRepository.recordEvent(
                ListeningEvent(
                    eventType = type,
                    trackId = trackId,
                    localFileId = localFileId,
                    positionMs = posMs,
                    durationMs = durMs,
                    durationPlayedSec = posMs / 1000.0,
                    completedFully = (type == ListeningEventType.COMPLETED)
                )
            )
        }
    }

    private fun checkAndRecordCompletion() {
        if (!hasRecordedCompletion) {
            hasRecordedCompletion = true
            recordEvent(ListeningEventType.COMPLETED)
        }
    }

    private fun syncControllerWithQueue(controller: Player) {
        val currentItems = _queue.value
        if (currentItems.isNotEmpty() && controller.mediaItemCount == 0) {
            scope.launch {
                val mediaItems = currentItems.mapNotNull { item ->
                    val track = item.track ?: trackRepository.getTrackById(item.trackId) ?: return@mapNotNull null
                    val localFiles = localFileRepository.observeLocalFilesForTrack(track.id).firstOrNull() ?: emptyList()
                    val localFile = localFiles.firstOrNull() ?: track.localFile
                    LocalFileSourceAdapter.toMediaItem(track, localFile)
                }
                if (mediaItems.isNotEmpty()) {
                    controller.setMediaItems(mediaItems)
                    val activeIdx = _playbackState.value.activeQueueIndex.coerceIn(0, mediaItems.size - 1)
                    val posMs = _playbackState.value.currentPositionMs
                    controller.seekTo(activeIdx, posMs)
                    controller.prepare()
                }
            }
        }
    }

    override fun play() {
        mediaController?.let { controller ->
            if (controller.playbackState == Player.STATE_IDLE && controller.mediaItemCount > 0) {
                controller.prepare()
            }
            controller.play()
        }
    }

    override fun pause() {
        mediaController?.pause()
    }

    override fun stop() {
        stopPositionPolling()
        wasExplicitlyPaused = false
        mediaController?.let { controller ->
            recordEvent(ListeningEventType.STOPPED)
            controller.stop()
        }
    }

    override fun seekTo(positionMs: Long) {
        mediaController?.let { controller ->
            controller.seekTo(positionMs)
            recordEvent(ListeningEventType.SEEKED)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = positionMs)
            persistCurrentState()
        }
    }

    override fun next() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            recordEvent(ListeningEventType.SKIPPED)
            controller.seekToNextMediaItem()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && controller.mediaItemCount > 0) {
            recordEvent(ListeningEventType.SKIPPED)
            controller.seekToDefaultPosition(0)
        }
    }

    override fun previous() {
        val controller = mediaController ?: return
        // If track has been playing for more than 3 seconds, rewind to beginning
        if (controller.currentPosition > 3000L) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else if (_playbackState.value.repeatMode == RepeatMode.ALL && controller.mediaItemCount > 0) {
            controller.seekToDefaultPosition(controller.mediaItemCount - 1)
        }
    }

    override fun setQueue(tracks: List<Track>, startIndex: Int) {
        scope.launch {
            val queueItems = tracks.mapIndexed { idx, track ->
                QueueItem(
                    position = idx,
                    trackId = track.id,
                    track = track,
                    originalIndex = idx
                )
            }
            _queue.value = queueItems
            playbackStateRepository.setQueue(queueItems)

            val mediaItems = tracks.map { track ->
                val localFiles = localFileRepository.observeLocalFilesForTrack(track.id).firstOrNull() ?: emptyList()
                val localFile = localFiles.firstOrNull() ?: track.localFile
                LocalFileSourceAdapter.toMediaItem(track, localFile)
            }

            mediaController?.let { controller ->
                controller.setMediaItems(mediaItems, startIndex, 0L)
                controller.prepare()
                controller.play()
            }
        }
    }

    override fun appendToQueue(tracks: List<Track>) {
        scope.launch {
            val currentQueue = _queue.value.toMutableList()
            val startPos = currentQueue.size
            val newItems = tracks.mapIndexed { idx, track ->
                QueueItem(
                    position = startPos + idx,
                    trackId = track.id,
                    track = track,
                    originalIndex = startPos + idx
                )
            }
            currentQueue.addAll(newItems)
            _queue.value = currentQueue
            playbackStateRepository.appendToQueue(newItems)

            val mediaItems = tracks.map { track ->
                val localFiles = localFileRepository.observeLocalFilesForTrack(track.id).firstOrNull() ?: emptyList()
                val localFile = localFiles.firstOrNull() ?: track.localFile
                LocalFileSourceAdapter.toMediaItem(track, localFile)
            }
            mediaController?.addMediaItems(mediaItems)
        }
    }

    override fun jumpToQueueIndex(index: Int) {
        mediaController?.let { controller ->
            if (index in 0 until controller.mediaItemCount) {
                controller.seekTo(index, 0L)
                controller.play()
            }
        }
    }

    override fun setShuffleMode(enabled: Boolean) {
        mediaController?.shuffleModeEnabled = enabled
        _playbackState.value = _playbackState.value.copy(shuffleMode = enabled)
        persistCurrentState()
    }

    override fun setRepeatMode(mode: RepeatMode) {
        val exoMode = when (mode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        mediaController?.repeatMode = exoMode
        _playbackState.value = _playbackState.value.copy(repeatMode = mode)
        persistCurrentState()
    }

    override fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0.0f, 1.0f)
        mediaController?.volume = clamped
        _playbackState.value = _playbackState.value.copy(volumeScalar = clamped)
        persistCurrentState()
    }

    fun release() {
        stopPositionPolling()
        activePlayerListener?.let { mediaController?.removeListener(it) }
        activePlayerListener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
