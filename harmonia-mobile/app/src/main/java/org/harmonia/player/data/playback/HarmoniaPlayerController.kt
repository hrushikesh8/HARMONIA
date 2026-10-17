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
import kotlinx.coroutines.CoroutineDispatcher
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
import org.harmonia.player.domain.model.audio.MonotonicClock
import org.harmonia.player.domain.model.audio.SleepTimerMode
import org.harmonia.player.domain.model.audio.SleepTimerState
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
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
    private val storageVolumeLifecycleCoordinator: StorageVolumeLifecycleCoordinator? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val playerOverride: Player? = null,
    private val enablePositionPolling: Boolean = true,
    private val monotonicClock: MonotonicClock = MonotonicClock.DEFAULT,
    private val timerDispatcher: CoroutineDispatcher = Dispatchers.Default
) : PlaybackPort {

    private val _playbackState = MutableStateFlow(PlaybackState())
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    override val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    override val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var baselineVolume: Float = 1.0f

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

    // Storage lifecycle tracking
    private var currentSourceId: String? = null
    private var storageObservationJob: Job? = null

    companion object {
        const val STORAGE_DISCONNECTED_MESSAGE = PlaybackState.STORAGE_DISCONNECTED_MESSAGE
        const val TRACK_PREFIX = "track:"

        fun extractDatabaseTrackId(mediaId: String): String {
            return if (mediaId.startsWith(TRACK_PREFIX)) {
                mediaId.removePrefix(TRACK_PREFIX)
            } else {
                mediaId
            }
        }
    }

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
        observeStorageLifecycle()
        if (playerOverride != null) {
            mediaController = playerOverride
            setupPlayerListener(playerOverride)
            syncControllerWithQueue(playerOverride)
        } else {
            initializeSessionConnection()
        }
        restorePersistedState()
    }

    private fun observeStorageLifecycle() {
        val coordinator = storageVolumeLifecycleCoordinator ?: return
        storageObservationJob?.cancel()
        storageObservationJob = scope.launch {
            coordinator.runtimeStates.collect { states ->
                handleStorageRuntimeStates(states)
            }
        }
    }

    private suspend fun getOrResolveSourceId(): String? {
        if (currentSourceId != null) return currentSourceId
        val fileId = _playbackState.value.currentLocalFileId ?: return null
        val file = localFileRepository.getLocalFileById(fileId)
        currentSourceId = file?.sourceId
        return currentSourceId
    }

    private suspend fun handleStorageRuntimeStates(states: Map<String, SourceRuntimeState>) {
        val activeFileId = _playbackState.value.currentLocalFileId ?: return
        val activeSourceId = getOrResolveSourceId() ?: return

        val sourceState = states[activeSourceId] ?: return
        if (!sourceState.isAvailable) {
            handleStorageDisconnection()
        }
    }

    private fun handleStorageDisconnection() {
        cancelSleepTimer()
        val alreadyDisconnected = _playbackState.value.errorMessage == STORAGE_DISCONNECTED_MESSAGE
        val isPaused = mediaController?.playWhenReady == false

        if (alreadyDisconnected && isPaused) {
            return
        }

        mediaController?.pause()
        wasExplicitlyPaused = true

        val currentPos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: _playbackState.value.currentPositionMs
        val dur = if ((mediaController?.duration ?: 0L) > 0L) mediaController!!.duration else _playbackState.value.durationMs
        val status = if (mediaController?.playerError != null) PlaybackStatus.ERROR else PlaybackStatus.PAUSED

        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            status = status,
            playWhenReady = false,
            currentPositionMs = currentPos,
            durationMs = dur,
            errorMessage = STORAGE_DISCONNECTED_MESSAGE
        )
        persistCurrentState()
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
                    if (_sleepTimerState.value.isActive) {
                        restoreVolumeToBaseline()
                    }
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
                if (isPlaying && _sleepTimerState.value.isActive) {
                    val fadeRatio = calculateCurrentFadeRatio()
                    val attenuated = baselineVolume * fadeRatio
                    mediaController?.volume = attenuated
                    _playbackState.value = _playbackState.value.copy(volumeScalar = attenuated)
                }
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
                    if (_sleepTimerState.value.isActive && _sleepTimerState.value.mode == SleepTimerMode.END_OF_TRACK) {
                        completeSleepTimer()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                activeTrackStartTime = System.currentTimeMillis()
                hasRecordedCompletion = false
                wasExplicitlyPaused = false

                if (_sleepTimerState.value.isActive && _sleepTimerState.value.mode == SleepTimerMode.END_OF_TRACK) {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                        completeSleepTimer()
                    } else {
                        // User manually skipped/seeked to another track: re-bind to the new track and restore baseline volume
                        restoreVolumeToBaseline()
                    }
                }

                if (mediaItem != null) {
                    val rawTrackId = extractDatabaseTrackId(mediaItem.mediaId)
                    val queueIdx = controller.currentMediaItemIndex

                    scope.launch {
                        val track = trackRepository.getTrackById(rawTrackId)
                        val localFiles = localFileRepository.observeLocalFilesForTrack(rawTrackId).firstOrNull() ?: emptyList()
                        val localFile = localFiles.firstOrNull() ?: track?.localFile
                        activeTrackDurationSec = track?.durationSec ?: 0.0

                        val resolvedSourceId = localFile?.sourceId ?: localFile?.let { localFileRepository.getLocalFileById(it.id)?.sourceId }
                        currentSourceId = resolvedSourceId

                        val newDurMs = (activeTrackDurationSec * 1000).toLong()
                        _playbackState.value = _playbackState.value.copy(
                            currentTrackId = rawTrackId,
                            currentLocalFileId = localFile?.id,
                            activeQueueIndex = queueIdx,
                            durationMs = newDurMs
                        )

                        if (_sleepTimerState.value.isActive && _sleepTimerState.value.mode == SleepTimerMode.END_OF_TRACK) {
                            _sleepTimerState.value = _sleepTimerState.value.copy(
                                remainingDurationMs = newDurMs,
                                targetDurationMs = newDurMs
                            )
                        }

                        recordEvent(ListeningEventType.STARTED)
                        persistCurrentState()

                        if (resolvedSourceId != null && storageVolumeLifecycleCoordinator?.isSourceAvailable(resolvedSourceId) == false) {
                            handleStorageDisconnection()
                        }
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                cancelSleepTimer()
                val isFileNotFound = error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                val activeSource = currentSourceId
                val isSourceConfirmedUnavailable = activeSource != null && storageVolumeLifecycleCoordinator?.isSourceAvailable(activeSource) == false
                val isAlreadyMarkedDisconnected = _playbackState.value.errorMessage == STORAGE_DISCONNECTED_MESSAGE

                val errMessage = if (isFileNotFound && (isSourceConfirmedUnavailable || isAlreadyMarkedDisconnected)) {
                    wasExplicitlyPaused = true
                    STORAGE_DISCONNECTED_MESSAGE
                } else if (isAlreadyMarkedDisconnected) {
                    STORAGE_DISCONNECTED_MESSAGE
                } else {
                    "Playback error [${error.errorCodeName}]: ${error.message}"
                }

                _playbackState.value = _playbackState.value.copy(
                    status = PlaybackStatus.ERROR,
                    isPlaying = false,
                    errorMessage = errMessage
                )
                persistCurrentState()
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
                val fileId = savedState.currentLocalFileId
                if (fileId != null) {
                    currentSourceId = localFileRepository.getLocalFileById(fileId)?.sourceId
                }
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
        val srcId = currentSourceId
        if (srcId != null && storageVolumeLifecycleCoordinator?.isSourceAvailable(srcId) == false) {
            _playbackState.value = _playbackState.value.copy(
                errorMessage = STORAGE_DISCONNECTED_MESSAGE
            )
            return
        }

        if (_playbackState.value.errorMessage == STORAGE_DISCONNECTED_MESSAGE) {
            _playbackState.value = _playbackState.value.copy(errorMessage = null)
        }

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
        baselineVolume = clamped
        if (!_sleepTimerState.value.isActive) {
            mediaController?.volume = clamped
            _playbackState.value = _playbackState.value.copy(volumeScalar = clamped)
            persistCurrentState()
        } else {
            _sleepTimerState.value = _sleepTimerState.value.copy(baselineVolume = clamped)
            val fadeRatio = calculateCurrentFadeRatio()
            val attenuated = clamped * fadeRatio
            mediaController?.volume = attenuated
            _playbackState.value = _playbackState.value.copy(volumeScalar = attenuated)
            persistCurrentState()
        }
    }

    override fun startSleepTimer(durationMs: Long) {
        if (durationMs <= 0L) {
            cancelSleepTimer()
            return
        }
        sleepTimerJob?.cancel()
        restoreVolumeToBaseline()
        baselineVolume = mediaController?.volume ?: _playbackState.value.volumeScalar

        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            remainingDurationMs = durationMs,
            targetDurationMs = durationMs,
            mode = SleepTimerMode.DURATION,
            baselineVolume = baselineVolume
        )

        sleepTimerJob = scope.launch(timerDispatcher) {
            var remainingMs = durationMs
            var lastTickMs = monotonicClock.elapsedRealtimeMs()
            val fadeWindowMs = minOf(30_000L, durationMs)

            while (isActive && remainingMs > 0L) {
                delay(250L)
                val currentMs = monotonicClock.elapsedRealtimeMs()
                val elapsed = (currentMs - lastTickMs).coerceAtLeast(0L)
                lastTickMs = currentMs

                if (_playbackState.value.isPlaying) {
                    remainingMs = (remainingMs - elapsed).coerceAtLeast(0L)
                    _sleepTimerState.value = _sleepTimerState.value.copy(remainingDurationMs = remainingMs)

                    if (fadeWindowMs > 0L && remainingMs <= fadeWindowMs) {
                        val ratio = (remainingMs.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.0f, 1.0f)
                        val attenuated = baselineVolume * ratio
                        mediaController?.volume = attenuated
                        _playbackState.value = _playbackState.value.copy(volumeScalar = attenuated)
                    } else {
                        if (mediaController?.volume != baselineVolume) {
                            mediaController?.volume = baselineVolume
                            _playbackState.value = _playbackState.value.copy(volumeScalar = baselineVolume)
                        }
                    }
                } else {
                    // Suspended while paused: restore baseline volume during pause
                    if (mediaController?.volume != baselineVolume) {
                        mediaController?.volume = baselineVolume
                        _playbackState.value = _playbackState.value.copy(volumeScalar = baselineVolume)
                    }
                }
            }

            if (isActive && remainingMs <= 0L) {
                completeSleepTimer()
            }
        }
    }

    override fun startSleepTimerEndOfTrack() {
        sleepTimerJob?.cancel()
        restoreVolumeToBaseline()
        baselineVolume = mediaController?.volume ?: _playbackState.value.volumeScalar

        val dur = mediaController?.duration?.takeIf { it > 0 } ?: _playbackState.value.durationMs
        val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: _playbackState.value.currentPositionMs
        val remainingMs = (dur - pos).coerceAtLeast(0L)

        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            remainingDurationMs = remainingMs,
            targetDurationMs = dur,
            mode = SleepTimerMode.END_OF_TRACK,
            baselineVolume = baselineVolume
        )

        sleepTimerJob = scope.launch(timerDispatcher) {
            while (isActive) {
                delay(250L)
                if (_playbackState.value.isPlaying) {
                    val currentDur = mediaController?.duration?.takeIf { it > 0 } ?: _playbackState.value.durationMs
                    val currentPos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: _playbackState.value.currentPositionMs
                    val rem = (currentDur - currentPos).coerceAtLeast(0L)
                    _sleepTimerState.value = _sleepTimerState.value.copy(
                        remainingDurationMs = rem,
                        targetDurationMs = currentDur
                    )

                    val fadeWindowMs = if (currentDur in 1..29_999L) minOf(10_000L, currentDur) else 30_000L
                    if (fadeWindowMs > 0L && rem <= fadeWindowMs && currentDur > 0) {
                        val ratio = (rem.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.0f, 1.0f)
                        val attenuated = baselineVolume * ratio
                        mediaController?.volume = attenuated
                        _playbackState.value = _playbackState.value.copy(volumeScalar = attenuated)
                    } else {
                        if (mediaController?.volume != baselineVolume) {
                            mediaController?.volume = baselineVolume
                            _playbackState.value = _playbackState.value.copy(volumeScalar = baselineVolume)
                        }
                    }

                    if (currentDur > 0 && currentPos >= currentDur - 50L) {
                        completeSleepTimer()
                        break
                    }
                } else {
                    // Suspended while paused: restore baseline volume
                    if (mediaController?.volume != baselineVolume) {
                        mediaController?.volume = baselineVolume
                        _playbackState.value = _playbackState.value.copy(volumeScalar = baselineVolume)
                    }
                }
            }
        }
    }

    override fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        restoreVolumeToBaseline()
        _sleepTimerState.value = SleepTimerState(
            isActive = false,
            remainingDurationMs = 0L,
            targetDurationMs = 0L,
            mode = SleepTimerMode.DURATION,
            baselineVolume = baselineVolume
        )
    }

    private fun completeSleepTimer() {
        if (!_sleepTimerState.value.isActive) return
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        mediaController?.pause()
        wasExplicitlyPaused = true
        restoreVolumeToBaseline()
        _sleepTimerState.value = SleepTimerState(
            isActive = false,
            remainingDurationMs = 0L,
            targetDurationMs = 0L,
            mode = _sleepTimerState.value.mode,
            baselineVolume = baselineVolume
        )
    }

    private fun restoreVolumeToBaseline() {
        mediaController?.volume = baselineVolume
        _playbackState.value = _playbackState.value.copy(volumeScalar = baselineVolume)
    }

    private fun calculateCurrentFadeRatio(): Float {
        if (!_sleepTimerState.value.isActive || !_playbackState.value.isPlaying) return 1.0f
        return when (_sleepTimerState.value.mode) {
            SleepTimerMode.DURATION -> {
                val targetDur = _sleepTimerState.value.targetDurationMs
                val rem = _sleepTimerState.value.remainingDurationMs
                val fadeWindowMs = minOf(30_000L, targetDur)
                if (fadeWindowMs > 0L && rem <= fadeWindowMs) {
                    (rem.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.0f, 1.0f)
                } else 1.0f
            }
            SleepTimerMode.END_OF_TRACK -> {
                val dur = mediaController?.duration?.takeIf { it > 0 } ?: _playbackState.value.durationMs
                val pos = mediaController?.currentPosition?.coerceAtLeast(0L) ?: _playbackState.value.currentPositionMs
                val rem = (dur - pos).coerceAtLeast(0L)
                val fadeWindowMs = if (dur in 1..29_999L) minOf(10_000L, dur) else 30_000L
                if (fadeWindowMs > 0L && rem <= fadeWindowMs && dur > 0) {
                    (rem.toFloat() / fadeWindowMs.toFloat()).coerceIn(0.0f, 1.0f)
                } else 1.0f
            }
        }
    }

    fun release() {
        cancelSleepTimer()
        stopPositionPolling()
        storageObservationJob?.cancel()
        storageObservationJob = null
        activePlayerListener?.let { mediaController?.removeListener(it) }
        activePlayerListener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }

    internal fun isStorageObservationActive(): Boolean = storageObservationJob?.isActive == true
    internal fun setCurrentSourceIdForTesting(sourceId: String?) { currentSourceId = sourceId }
}
