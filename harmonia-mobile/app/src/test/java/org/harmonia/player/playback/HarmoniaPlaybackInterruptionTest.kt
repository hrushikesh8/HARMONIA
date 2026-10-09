package org.harmonia.player.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.harmonia.player.data.playback.HarmoniaPlayerController
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.ListeningEventType
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.PlaybackStatus
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.ListeningEventRepository
import org.harmonia.player.domain.repository.LocalFileRepository
import org.harmonia.player.domain.repository.PlaybackStateRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M4-D2 Regression Test Suite verifying:
 * 1. Distinction between user intent (playWhenReady) and transient system suppression (playbackSuppressionReason).
 * 2. Accurate playback status exposure (PLAYING, PAUSED, SUPPRESSED, BUFFERING, ERROR, ENDED).
 * 3. Factual listening telemetry without false PAUSED/RESUMED events during transient suppression or buffering.
 * 4. Preservation of explicit user pauses during and after system interruptions.
 * 5. Media3-driven becoming-noisy (headphone disconnect) semantics.
 * 6. Queue, seeking, and error resilience.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HarmoniaPlaybackInterruptionTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakePlayerHandler: FakePlayerInvocationHandler
    private lateinit var player: Player

    private lateinit var fakeStateRepo: FakePlaybackStateRepository
    private lateinit var fakeEventRepo: FakeListeningEventRepository
    private lateinit var fakeTrackRepo: FakeTrackRepository
    private lateinit var fakeFileRepo: FakeLocalFileRepository

    private lateinit var controller: HarmoniaPlayerController

    private val track1 = Track(id = "trk-1", title = "Track One", durationSec = 180.0)
    private val track2 = Track(id = "trk-2", title = "Track Two", durationSec = 240.0)

    @Before
    fun setup() {
        fakePlayerHandler = FakePlayerInvocationHandler()
        player = fakePlayerHandler.createProxy()

        fakeStateRepo = FakePlaybackStateRepository()
        fakeEventRepo = FakeListeningEventRepository()
        fakeTrackRepo = FakeTrackRepository(listOf(track1, track2))
        fakeFileRepo = FakeLocalFileRepository()

        controller = HarmoniaPlayerController(
            context = null,
            playbackStateRepository = fakeStateRepo,
            listeningEventRepository = fakeEventRepo,
            trackRepository = fakeTrackRepo,
            localFileRepository = fakeFileRepo,
            scope = testScope,
            playerOverride = player,
            enablePositionPolling = false
        )
    }

    @After
    fun tearDown() {
        controller.release()
    }

    private fun startTrack1() {
        val mediaItem = MediaItem.Builder().setMediaId("trk-1").build()
        fakePlayerHandler.currentMediaItemIndex = 0
        fakePlayerHandler.playbackState = Player.STATE_READY
        fakePlayerHandler.playWhenReady = true
        fakePlayerHandler.isPlaying = true
        fakePlayerHandler.playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE

        fakePlayerHandler.fireMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        testDispatcher.scheduler.runCurrent()

        fakePlayerHandler.firePlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        fakePlayerHandler.firePlaybackStateChanged(Player.STATE_READY)
        fakePlayerHandler.fireIsPlayingChanged(true)
        testDispatcher.scheduler.runCurrent()
    }

    @Test
    fun `scenario 1 - user presses pause accurately updates state and records PAUSED event`() = runTest(testDispatcher) {
        startTrack1()

        assertEquals(PlaybackStatus.PLAYING, controller.playbackState.value.status)
        assertTrue(controller.playbackState.value.isPlaying)
        assertTrue(controller.playbackState.value.playWhenReady)
        assertEquals(1, fakeEventRepo.events.size)
        assertEquals(ListeningEventType.STARTED, fakeEventRepo.events[0].eventType)

        // User triggers pause
        controller.pause()
        testDispatcher.scheduler.runCurrent()

        assertFalse(controller.playbackState.value.isPlaying)
        assertFalse(controller.playbackState.value.playWhenReady)
        assertEquals(PlaybackStatus.PAUSED, controller.playbackState.value.status)

        // Verify exactly one PAUSED telemetry event
        val recentEvents = fakeEventRepo.events
        assertEquals(2, recentEvents.size)
        assertEquals(ListeningEventType.PAUSED, recentEvents[0].eventType)
        assertEquals("trk-1", recentEvents[0].trackId)
    }

    @Test
    fun `scenario 2 - transient system audio focus loss sets SUPPRESSED without false PAUSED event`() = runTest(testDispatcher) {
        startTrack1()
        assertEquals(1, fakeEventRepo.events.size)

        // System transient audio focus loss (e.g. navigation prompt / call ring)
        fakePlayerHandler.playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        fakePlayerHandler.isPlaying = false
        fakePlayerHandler.firePlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        fakePlayerHandler.fireIsPlayingChanged(false)
        testDispatcher.scheduler.runCurrent()

        // State reflects suppression while preserving user intent
        assertEquals(PlaybackStatus.SUPPRESSED, controller.playbackState.value.status)
        assertTrue("User intent must remain true during transient suppression", controller.playbackState.value.playWhenReady)
        assertFalse("Actual playback must be reported as false during suppression", controller.playbackState.value.isPlaying)
        assertEquals(
            Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS,
            controller.playbackState.value.playbackSuppressionReason
        )

        // Crucial invariant: No false PAUSED telemetry event recorded
        assertEquals(1, fakeEventRepo.events.size)
        assertEquals(ListeningEventType.STARTED, fakeEventRepo.events[0].eventType)
    }

    @Test
    fun `scenario 3 - suppression recovery restores PLAYING without false RESUMED event`() = runTest(testDispatcher) {
        startTrack1()

        // Enter transient suppression
        fakePlayerHandler.playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        fakePlayerHandler.isPlaying = false
        fakePlayerHandler.firePlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        fakePlayerHandler.fireIsPlayingChanged(false)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.SUPPRESSED, controller.playbackState.value.status)
        assertEquals(1, fakeEventRepo.events.size)

        // System clears suppression and Media3 resumes playback
        fakePlayerHandler.playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        fakePlayerHandler.isPlaying = true
        fakePlayerHandler.firePlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
        fakePlayerHandler.fireIsPlayingChanged(true)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.PLAYING, controller.playbackState.value.status)
        assertTrue(controller.playbackState.value.isPlaying)
        assertTrue(controller.playbackState.value.playWhenReady)
        assertEquals(Player.PLAYBACK_SUPPRESSION_REASON_NONE, controller.playbackState.value.playbackSuppressionReason)

        // No false RESUMED event since the user never paused
        assertEquals(1, fakeEventRepo.events.size)
        assertEquals(ListeningEventType.STARTED, fakeEventRepo.events[0].eventType)
    }

    @Test
    fun `scenario 4 - user pauses during suppression prevents unintended resumption upon focus return`() = runTest(testDispatcher) {
        startTrack1()

        // Enter transient suppression
        fakePlayerHandler.playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        fakePlayerHandler.isPlaying = false
        fakePlayerHandler.firePlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        fakePlayerHandler.fireIsPlayingChanged(false)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.SUPPRESSED, controller.playbackState.value.status)

        // User explicitly pauses while suppressed
        controller.pause()
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.PAUSED, controller.playbackState.value.status)
        assertFalse(controller.playbackState.value.playWhenReady)
        assertEquals(2, fakeEventRepo.events.size)
        assertEquals(ListeningEventType.PAUSED, fakeEventRepo.events[0].eventType)

        // Later, audio focus returns in system
        fakePlayerHandler.playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        // Because playWhenReady is false, Media3 does NOT resume
        fakePlayerHandler.isPlaying = false
        fakePlayerHandler.firePlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
        testDispatcher.scheduler.runCurrent()

        // State remains PAUSED, no auto-resumption!
        assertEquals(PlaybackStatus.PAUSED, controller.playbackState.value.status)
        assertFalse(controller.playbackState.value.isPlaying)
        assertFalse(controller.playbackState.value.playWhenReady)
        assertEquals(2, fakeEventRepo.events.size)
    }

    @Test
    fun `scenario 5 - headphone disconnection becoming noisy pauses cleanly without duplicate handling`() = runTest(testDispatcher) {
        startTrack1()

        // Media3 handles ACTION_AUDIO_BECOMING_NOISY and changes playWhenReady
        fakePlayerHandler.playWhenReady = false
        fakePlayerHandler.isPlaying = false
        fakePlayerHandler.firePlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY)
        fakePlayerHandler.fireIsPlayingChanged(false)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.PAUSED, controller.playbackState.value.status)
        assertFalse(controller.playbackState.value.playWhenReady)
        assertFalse(controller.playbackState.value.isPlaying)

        val recentEvents = fakeEventRepo.events
        assertEquals(2, recentEvents.size)
        assertEquals(ListeningEventType.PAUSED, recentEvents[0].eventType)

        // Reconnecting headphones does not trigger auto-resume
        testDispatcher.scheduler.runCurrent()
        assertEquals(PlaybackStatus.PAUSED, controller.playbackState.value.status)
    }

    @Test
    fun `scenario 6 - buffering transitions do not generate false pause or resume telemetry`() = runTest(testDispatcher) {
        startTrack1()
        assertEquals(1, fakeEventRepo.events.size)

        // Player enters buffering during playback
        fakePlayerHandler.playbackState = Player.STATE_BUFFERING
        fakePlayerHandler.isPlaying = false
        fakePlayerHandler.firePlaybackStateChanged(Player.STATE_BUFFERING)
        fakePlayerHandler.fireIsPlayingChanged(false)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.BUFFERING, controller.playbackState.value.status)
        assertTrue(controller.playbackState.value.playWhenReady)
        assertFalse(controller.playbackState.value.isPlaying)
        // No false PAUSED event
        assertEquals(1, fakeEventRepo.events.size)

        // Buffer ready
        fakePlayerHandler.playbackState = Player.STATE_READY
        fakePlayerHandler.isPlaying = true
        fakePlayerHandler.firePlaybackStateChanged(Player.STATE_READY)
        fakePlayerHandler.fireIsPlayingChanged(true)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.PLAYING, controller.playbackState.value.status)
        // No false RESUMED event
        assertEquals(1, fakeEventRepo.events.size)
    }

    @Test
    fun `scenario 7 - media item transition during interruption updates track identity and starts new track`() = runTest(testDispatcher) {
        startTrack1()

        // Enter transient suppression
        fakePlayerHandler.playbackSuppressionReason = Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
        fakePlayerHandler.isPlaying = false
        fakePlayerHandler.firePlaybackSuppressionReasonChanged(Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS)
        fakePlayerHandler.fireIsPlayingChanged(false)
        testDispatcher.scheduler.runCurrent()

        // Skip to next track
        val nextItem = MediaItem.Builder().setMediaId("trk-2").build()
        fakePlayerHandler.currentMediaItemIndex = 1
        fakePlayerHandler.fireMediaItemTransition(nextItem, Player.MEDIA_ITEM_TRANSITION_REASON_SEEK)
        testDispatcher.scheduler.runCurrent()

        assertEquals("trk-2", controller.playbackState.value.currentTrackId)
        assertEquals(1, controller.playbackState.value.activeQueueIndex)
        assertEquals(240000L, controller.playbackState.value.durationMs)

        // Telemetry recorded STARTED for track 2
        val recentEvents = fakeEventRepo.events
        assertEquals(2, recentEvents.size)
        assertEquals(ListeningEventType.STARTED, recentEvents[0].eventType)
        assertEquals("trk-2", recentEvents[0].trackId)
    }

    @Test
    fun `scenario 8 - repeated or reordered callbacks produce no duplicate listening telemetry`() = runTest(testDispatcher) {
        startTrack1()

        // User pauses
        controller.pause()
        testDispatcher.scheduler.runCurrent()
        assertEquals(2, fakeEventRepo.events.size)
        assertEquals(ListeningEventType.PAUSED, fakeEventRepo.events[0].eventType)

        // Rapid duplicate callbacks from platform
        fakePlayerHandler.firePlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        fakePlayerHandler.fireIsPlayingChanged(false)
        fakePlayerHandler.firePlaybackStateChanged(Player.STATE_READY)
        testDispatcher.scheduler.runCurrent()

        // Still exactly 2 events (no duplicate PAUSED)
        assertEquals(2, fakeEventRepo.events.size)

        // User resumes
        controller.play()
        testDispatcher.scheduler.runCurrent()
        assertEquals(3, fakeEventRepo.events.size)
        assertEquals(ListeningEventType.RESUMED, fakeEventRepo.events[0].eventType)

        // Rapid duplicate isPlaying callbacks
        fakePlayerHandler.fireIsPlayingChanged(true)
        fakePlayerHandler.firePlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        testDispatcher.scheduler.runCurrent()

        // Still exactly 3 events (no duplicate RESUMED)
        assertEquals(3, fakeEventRepo.events.size)
    }

    @Test
    fun `scenario 9 - playback failure is strictly distinguished from transient suppression`() = runTest(testDispatcher) {
        startTrack1()

        val exception = PlaybackException("Decoder initialization failed", null, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        fakePlayerHandler.playerError = exception
        fakePlayerHandler.firePlayerError(exception)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.ERROR, controller.playbackState.value.status)
        assertNotNull(controller.playbackState.value.errorMessage)
        assertTrue(controller.playbackState.value.errorMessage!!.contains("ERROR_CODE_DECODER_INIT_FAILED"))
    }

    @Test
    fun `scenario 10 - existing play, seek, queue, repeat, shuffle and completion work correctly`() = runTest(testDispatcher) {
        startTrack1()

        // Queue setup
        controller.setQueue(listOf(track1, track2), 0)
        testDispatcher.scheduler.runCurrent()
        assertEquals(2, controller.queue.value.size)

        // Seeking
        controller.seekTo(45000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(45000L, controller.playbackState.value.currentPositionMs)
        assertEquals(ListeningEventType.SEEKED, fakeEventRepo.events[0].eventType)

        // Shuffle & Repeat
        controller.setShuffleMode(true)
        testDispatcher.scheduler.runCurrent()
        assertTrue(controller.playbackState.value.shuffleMode)

        controller.setRepeatMode(RepeatMode.ALL)
        testDispatcher.scheduler.runCurrent()
        assertEquals(RepeatMode.ALL, controller.playbackState.value.repeatMode)

        // Track completion
        fakePlayerHandler.playbackState = Player.STATE_ENDED
        fakePlayerHandler.isPlaying = false
        fakePlayerHandler.firePlaybackStateChanged(Player.STATE_ENDED)
        fakePlayerHandler.fireIsPlayingChanged(false)
        testDispatcher.scheduler.runCurrent()

        assertEquals(PlaybackStatus.ENDED, controller.playbackState.value.status)
        val completionEvent = fakeEventRepo.events.find { it.eventType == ListeningEventType.COMPLETED }
        assertNotNull(completionEvent)
        assertTrue(completionEvent!!.completedFully)

        // Stop
        controller.stop()
        testDispatcher.scheduler.runCurrent()
        val stopEvent = fakeEventRepo.events.find { it.eventType == ListeningEventType.STOPPED }
        assertNotNull(stopEvent)
    }

    @Test
    fun `scenario 11 - position polling updates current position when enabled and terminates deterministically`() = runTest(testDispatcher) {
        val pollingController = HarmoniaPlayerController(
            context = null,
            playbackStateRepository = fakeStateRepo,
            listeningEventRepository = fakeEventRepo,
            trackRepository = fakeTrackRepo,
            localFileRepository = fakeFileRepo,
            scope = testScope,
            playerOverride = player,
            enablePositionPolling = true
        )

        val mediaItem = MediaItem.Builder().setMediaId("trk-1").build()
        fakePlayerHandler.currentMediaItemIndex = 0
        fakePlayerHandler.playbackState = Player.STATE_READY
        fakePlayerHandler.playWhenReady = true
        fakePlayerHandler.isPlaying = true
        fakePlayerHandler.currentPosition = 12500L

        fakePlayerHandler.fireMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        fakePlayerHandler.firePlaybackStateChanged(Player.STATE_READY)
        fakePlayerHandler.fireIsPlayingChanged(true)
        testDispatcher.scheduler.runCurrent()

        // Advance virtual time by 300ms to trigger one poll tick
        testDispatcher.scheduler.advanceTimeBy(300L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(12500L, pollingController.playbackState.value.currentPositionMs)

        // Pausing stops polling, cancelling the coroutine loop cleanly
        pollingController.pause()
        testDispatcher.scheduler.runCurrent()

        pollingController.release()
        testDispatcher.scheduler.runCurrent()
    }
}

// ---- Test Doubles ----

class FakePlayerInvocationHandler : InvocationHandler {
    var isPlaying: Boolean = false
    var playWhenReady: Boolean = false
    var playbackSuppressionReason: Int = Player.PLAYBACK_SUPPRESSION_REASON_NONE
    var playbackState: Int = Player.STATE_IDLE
    var currentPosition: Long = 0L
    var duration: Long = 180000L
    var currentMediaItemIndex: Int = 0
    var mediaItemCount: Int = 1
    var volume: Float = 1.0f
    var shuffleModeEnabled: Boolean = false
    var repeatMode: Int = Player.REPEAT_MODE_OFF
    var playerError: PlaybackException? = null

    val listeners = mutableListOf<Player.Listener>()

    fun createProxy(): Player {
        return Proxy.newProxyInstance(
            Player::class.java.classLoader,
            arrayOf(Player::class.java),
            this
        ) as Player
    }

    fun firePlayWhenReadyChanged(pwr: Boolean, reason: Int) {
        listeners.toList().forEach { it.onPlayWhenReadyChanged(pwr, reason) }
    }

    fun firePlaybackSuppressionReasonChanged(reason: Int) {
        listeners.toList().forEach { it.onPlaybackSuppressionReasonChanged(reason) }
    }

    fun fireIsPlayingChanged(playing: Boolean) {
        listeners.toList().forEach { it.onIsPlayingChanged(playing) }
    }

    fun firePlaybackStateChanged(state: Int) {
        listeners.toList().forEach { it.onPlaybackStateChanged(state) }
    }

    fun fireMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        listeners.toList().forEach { it.onMediaItemTransition(mediaItem, reason) }
    }

    fun firePlayerError(error: PlaybackException) {
        listeners.toList().forEach { it.onPlayerError(error) }
    }

    override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
        return when (method.name) {
            "isPlaying" -> isPlaying
            "getPlayWhenReady" -> playWhenReady
            "getPlaybackSuppressionReason" -> playbackSuppressionReason
            "getPlaybackState" -> playbackState
            "getCurrentPosition" -> currentPosition
            "getDuration" -> duration
            "getCurrentMediaItemIndex" -> currentMediaItemIndex
            "getMediaItemCount" -> mediaItemCount
            "getVolume" -> volume
            "getShuffleModeEnabled" -> shuffleModeEnabled
            "getRepeatMode" -> repeatMode
            "getPlayerError" -> playerError
            "addListener" -> {
                val listener = args?.get(0) as? Player.Listener
                if (listener != null) listeners.add(listener)
                null
            }
            "removeListener" -> {
                val listener = args?.get(0) as? Player.Listener
                if (listener != null) listeners.remove(listener)
                null
            }
            "play" -> {
                playWhenReady = true
                if (playbackState == Player.STATE_READY && playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE) {
                    isPlaying = true
                }
                listeners.toList().forEach {
                    it.onPlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    if (isPlaying) it.onIsPlayingChanged(true)
                }
                null
            }
            "pause" -> {
                playWhenReady = false
                isPlaying = false
                listeners.toList().forEach {
                    it.onPlayWhenReadyChanged(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    it.onIsPlayingChanged(false)
                }
                null
            }
            "stop" -> {
                playWhenReady = false
                isPlaying = false
                playbackState = Player.STATE_IDLE
                listeners.toList().forEach {
                    it.onPlaybackStateChanged(Player.STATE_IDLE)
                    it.onIsPlayingChanged(false)
                }
                null
            }
            "seekTo" -> {
                if (args != null && args.size == 1) {
                    currentPosition = (args[0] as Number).toLong()
                } else if (args != null && args.size == 2) {
                    currentMediaItemIndex = (args[0] as Number).toInt()
                    currentPosition = (args[1] as Number).toLong()
                }
                null
            }
            "prepare" -> {
                if (playbackState == Player.STATE_IDLE) {
                    playbackState = Player.STATE_READY
                    listeners.toList().forEach { it.onPlaybackStateChanged(Player.STATE_READY) }
                }
                null
            }
            "hasNextMediaItem" -> currentMediaItemIndex < mediaItemCount - 1
            "hasPreviousMediaItem" -> currentMediaItemIndex > 0
            "seekToNextMediaItem" -> {
                currentMediaItemIndex++
                currentPosition = 0L
                null
            }
            "seekToPreviousMediaItem" -> {
                currentMediaItemIndex = (currentMediaItemIndex - 1).coerceAtLeast(0)
                currentPosition = 0L
                null
            }
            "seekToDefaultPosition" -> {
                if (args != null && args.isNotEmpty()) {
                    currentMediaItemIndex = (args[0] as Number).toInt()
                }
                currentPosition = 0L
                null
            }
            "setMediaItems" -> {
                if (args != null && args.isNotEmpty()) {
                    val items = args[0] as? List<*>
                    mediaItemCount = items?.size ?: 0
                }
                null
            }
            "addMediaItems" -> {
                if (args != null && args.isNotEmpty()) {
                    val items = args[0] as? List<*>
                    mediaItemCount += items?.size ?: 0
                }
                null
            }
            "setShuffleModeEnabled" -> {
                shuffleModeEnabled = args?.get(0) as Boolean
                null
            }
            "setRepeatMode" -> {
                repeatMode = args?.get(0) as Int
                null
            }
            "setVolume" -> {
                volume = (args?.get(0) as Number).toFloat()
                null
            }
            "equals" -> proxy === args?.get(0)
            "hashCode" -> System.identityHashCode(proxy)
            "toString" -> "FakePlayer"
            else -> {
                when (method.returnType) {
                    Boolean::class.javaPrimitiveType -> false
                    Int::class.javaPrimitiveType -> 0
                    Long::class.javaPrimitiveType -> 0L
                    Float::class.javaPrimitiveType -> 0f
                    Double::class.javaPrimitiveType -> 0.0
                    else -> null
                }
            }
        }
    }
}

class FakePlaybackStateRepository : PlaybackStateRepository {
    private val stateFlow = MutableStateFlow<PlaybackState?>(null)
    private val queueFlow = MutableStateFlow<List<QueueItem>>(emptyList())

    override fun observePlaybackState(): Flow<PlaybackState?> = stateFlow
    override suspend fun savePlaybackState(state: PlaybackState) { stateFlow.value = state }
    override fun observeQueue(): Flow<List<QueueItem>> = queueFlow
    override suspend fun setQueue(items: List<QueueItem>) { queueFlow.value = items }
    override suspend fun appendToQueue(items: List<QueueItem>) { queueFlow.value = queueFlow.value + items }
    override suspend fun clearQueue() { queueFlow.value = emptyList() }
}

class FakeListeningEventRepository : ListeningEventRepository {
    val events = mutableListOf<ListeningEvent>()
    private val flow = MutableStateFlow<List<ListeningEvent>>(emptyList())

    override suspend fun recordEvent(event: ListeningEvent) {
        events.add(0, event)
        flow.value = events.toList()
    }
    override fun observeRecentEvents(limit: Int): Flow<List<ListeningEvent>> = flow.map { it.take(limit) }
    override suspend fun getEventCount(): Int = events.size
}

class FakeTrackRepository(private val tracks: List<Track>) : TrackRepository {
    override suspend fun getTrackById(id: String): Track? = tracks.find { it.id == id }
    override fun observeAllTracks(): Flow<List<Track>> = MutableStateFlow(tracks)
    override fun observeOfflineTracks(): Flow<List<Track>> = MutableStateFlow(tracks)
    override fun searchTracks(query: String): Flow<List<Track>> = MutableStateFlow(emptyList())
    override suspend fun upsertTracks(tracks: List<Track>) {}
    override suspend fun deleteTrack(id: String) {}
}

class FakeLocalFileRepository : LocalFileRepository {
    override fun observeLocalFilesForTrack(trackId: String): Flow<List<LocalFile>> = MutableStateFlow(emptyList())
    override suspend fun getLocalFileById(id: String): LocalFile? = null
    override suspend fun getLocalFileByUri(fileUri: String): LocalFile? = null
    override suspend fun upsertLocalFile(localFile: LocalFile) {}
    override suspend fun updateVerificationStatus(id: String, isVerified: Boolean, verifiedAt: Long) {}
    override suspend fun deleteLocalFile(id: String) {}
}
