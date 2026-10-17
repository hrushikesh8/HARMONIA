package org.harmonia.player.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.harmonia.player.data.playback.HarmoniaPlayerController
import org.harmonia.player.domain.model.PlaybackStatus
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.audio.MonotonicClock
import org.harmonia.player.domain.model.audio.SleepTimerMode
import org.harmonia.player.domain.model.audio.SleepTimerPreset
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stage M5-D Deterministic Sleep Timer Test Suite covering:
 * 1. Presets (15m, 30m, 45m, 60m) and End-of-Track mode.
 * 2. Monotonic deadline tracking and smooth 30-second linear volume attenuation.
 * 3. Short duration (< 30s) bounded fade interval.
 * 4. Countdown suspension on pause and volume preservation during pauses.
 * 5. Cancellation and replacement during active fade with immediate baseline restoration.
 * 6. User volume tracking and preservation during active timer.
 * 7. End-of-Track repeat override, manual seek re-binding, and seeking out of fade.
 * 8. Cleanup and volume restoration on storage disconnection, playback errors, and release.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HarmoniaSleepTimerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakePlayerHandler: FakePlayerInvocationHandler
    private lateinit var player: Player

    private lateinit var fakeStateRepo: FakePlaybackStateRepository
    private lateinit var fakeEventRepo: FakeListeningEventRepository
    private lateinit var fakeTrackRepo: FakeTrackRepository
    private lateinit var fakeFileRepo: FakeLocalFileRepository
    private lateinit var fakeCoordinator: FakeStorageVolumeLifecycleCoordinator

    private lateinit var controller: HarmoniaPlayerController

    private val testClock = object : MonotonicClock {
        override fun elapsedRealtimeMs(): Long = testDispatcher.scheduler.currentTime
    }

    private val track1 = Track(id = "trk-1", title = "Track One", durationSec = 180.0)
    private val track2 = Track(id = "trk-2", title = "Track Two", durationSec = 240.0)

    private val localFile1 = org.harmonia.player.domain.model.LocalFile(
        id = "file-1",
        trackId = "trk-1",
        sourceId = "src-sdcard-1",
        storageType = org.harmonia.player.domain.model.StorageType.EXTERNAL_SD,
        fileUri = "file:///storage/sdcard1/Music/track1.mp3",
        fileSize = 5000000L,
        fileHash = "hash1",
        format = "mp3"
    )

    @Before
    fun setup() {
        fakePlayerHandler = FakePlayerInvocationHandler()
        fakePlayerHandler.volume = 1.0f
        player = fakePlayerHandler.createProxy()

        fakeStateRepo = FakePlaybackStateRepository()
        fakeEventRepo = FakeListeningEventRepository()
        fakeTrackRepo = FakeTrackRepository(listOf(track1, track2))
        fakeFileRepo = FakeLocalFileRepository()
        fakeCoordinator = FakeStorageVolumeLifecycleCoordinator()

        controller = HarmoniaPlayerController(
            context = null,
            playbackStateRepository = fakeStateRepo,
            listeningEventRepository = fakeEventRepo,
            trackRepository = fakeTrackRepo,
            localFileRepository = fakeFileRepo,
            storageVolumeLifecycleCoordinator = fakeCoordinator,
            scope = kotlinx.coroutines.CoroutineScope(testDispatcher + kotlinx.coroutines.SupervisorJob()),
            playerOverride = player,
            enablePositionPolling = false,
            monotonicClock = testClock,
            timerDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        controller.release()
    }

    private fun startPlayback(trackId: String = "trk-1", durationMs: Long = 180_000L, positionMs: Long = 0L) {
        val mediaItem = MediaItem.Builder().setMediaId(trackId).build()
        fakePlayerHandler.currentMediaItemIndex = 0
        fakePlayerHandler.playbackState = Player.STATE_READY
        fakePlayerHandler.playWhenReady = true
        fakePlayerHandler.isPlaying = true
        fakePlayerHandler.duration = durationMs
        fakePlayerHandler.currentPosition = positionMs

        fakePlayerHandler.fireMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
        testDispatcher.scheduler.runCurrent()
        fakePlayerHandler.firePlayWhenReadyChanged(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        fakePlayerHandler.firePlaybackStateChanged(Player.STATE_READY)
        fakePlayerHandler.fireIsPlayingChanged(true)
        testDispatcher.scheduler.runCurrent()
    }

    @Test
    fun `all sleep timer presets arm with correct durations and monotonic mode`() = runTest(testDispatcher) {
        startPlayback()

        // 15 minutes
        controller.startSleepTimer(SleepTimerPreset.MINUTES_15.durationMs!!)
        testDispatcher.scheduler.runCurrent()
        var state = controller.sleepTimerState.value
        assertTrue(state.isActive)
        assertEquals(15 * 60 * 1000L, state.remainingDurationMs)
        assertEquals(15 * 60 * 1000L, state.targetDurationMs)
        assertEquals(SleepTimerMode.DURATION, state.mode)

        // 30 minutes
        controller.startSleepTimer(SleepTimerPreset.MINUTES_30.durationMs!!)
        testDispatcher.scheduler.runCurrent()
        state = controller.sleepTimerState.value
        assertTrue(state.isActive)
        assertEquals(30 * 60 * 1000L, state.remainingDurationMs)

        // 45 minutes
        controller.startSleepTimer(SleepTimerPreset.MINUTES_45.durationMs!!)
        testDispatcher.scheduler.runCurrent()
        state = controller.sleepTimerState.value
        assertTrue(state.isActive)
        assertEquals(45 * 60 * 1000L, state.remainingDurationMs)

        // 60 minutes
        controller.startSleepTimer(SleepTimerPreset.MINUTES_60.durationMs!!)
        testDispatcher.scheduler.runCurrent()
        state = controller.sleepTimerState.value
        assertTrue(state.isActive)
        assertEquals(60 * 60 * 1000L, state.remainingDurationMs)

        // End of track
        controller.startSleepTimerEndOfTrack()
        testDispatcher.scheduler.runCurrent()
        state = controller.sleepTimerState.value
        assertTrue(state.isActive)
        assertEquals(SleepTimerMode.END_OF_TRACK, state.mode)
        controller.cancelSleepTimer()
    }

    @Test
    fun `timed preset executes smooth 30s linear fade and pauses on expiry with volume restored`() = runTest(testDispatcher) {
        startPlayback()
        fakePlayerHandler.volume = 0.8f
        controller.setVolume(0.8f)

        // Set 60-second timer
        controller.startSleepTimer(60_000L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(0.8f, fakePlayerHandler.volume, 0.001f)

        // Advance 25 seconds: remaining 35s > 30s fade window -> volume stays at baseline 0.8f
        testDispatcher.scheduler.advanceTimeBy(25_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(35_000L, controller.sleepTimerState.value.remainingDurationMs)
        assertEquals(0.8f, fakePlayerHandler.volume, 0.001f)

        // Advance 5 seconds: remaining 30s -> fade window starts
        testDispatcher.scheduler.advanceTimeBy(5_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(30_000L, controller.sleepTimerState.value.remainingDurationMs)
        assertEquals(0.8f, fakePlayerHandler.volume, 0.02f)

        // Advance 15 seconds: remaining 15s -> volume should be 0.8 * (15/30) = 0.4f
        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(15_000L, controller.sleepTimerState.value.remainingDurationMs)
        assertEquals(0.4f, fakePlayerHandler.volume, 0.02f)

        // Advance 7.5 seconds: remaining 7.5s -> volume should be 0.8 * (7.5/30) = 0.2f
        testDispatcher.scheduler.advanceTimeBy(7_500L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.2f, fakePlayerHandler.volume, 0.02f)

        // Advance remaining 7.5 seconds to zero -> timer completes, pauses playback, restores baseline
        testDispatcher.scheduler.advanceTimeBy(7_500L)
        testDispatcher.scheduler.runCurrent()

        assertFalse(controller.sleepTimerState.value.isActive)
        assertFalse(fakePlayerHandler.isPlaying)
        assertEquals(0.8f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `duration shorter than 30s fades smoothly over the entire target duration`() = runTest(testDispatcher) {
        startPlayback()
        fakePlayerHandler.volume = 1.0f
        controller.setVolume(1.0f)

        // 10-second timer
        controller.startSleepTimer(10_000L)
        testDispatcher.scheduler.runCurrent()

        // At t=5s (remaining 5s of 10s): volume should be 0.5f
        testDispatcher.scheduler.advanceTimeBy(5_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.02f)

        // At t=10s: timer completes, pauses, restores baseline
        testDispatcher.scheduler.advanceTimeBy(5_000L)
        testDispatcher.scheduler.runCurrent()
        assertFalse(controller.sleepTimerState.value.isActive)
        assertFalse(fakePlayerHandler.isPlaying)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `playback pause suspends countdown and restores baseline volume during pause`() = runTest(testDispatcher) {
        startPlayback()
        fakePlayerHandler.volume = 1.0f
        controller.setVolume(1.0f)

        controller.startSleepTimer(40_000L)
        testDispatcher.scheduler.runCurrent()

        // Advance 25 seconds into fade (remaining 15s, volume attenuated to ~0.5f)
        testDispatcher.scheduler.advanceTimeBy(25_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(15_000L, controller.sleepTimerState.value.remainingDurationMs)
        assertEquals(0.5f, fakePlayerHandler.volume, 0.02f)

        // Pause playback
        controller.pause()
        testDispatcher.scheduler.runCurrent()

        // Baseline volume must be restored during pause so audio is not left attenuated
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)

        // Advance time by 30 seconds while paused: countdown must be suspended
        testDispatcher.scheduler.advanceTimeBy(30_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(15_000L, controller.sleepTimerState.value.remainingDurationMs)
        assertTrue(controller.sleepTimerState.value.isActive)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)

        // Resume playback: countdown resumes from remaining 15s and fade re-attenuates
        controller.play()
        testDispatcher.scheduler.runCurrent()
        testDispatcher.scheduler.advanceTimeBy(250L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.03f)

        // Advance remaining 15 seconds to zero: timer expires cleanly
        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertFalse(controller.sleepTimerState.value.isActive)
        assertFalse(fakePlayerHandler.isPlaying)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `cancellation while fade is active immediately restores baseline volume`() = runTest(testDispatcher) {
        startPlayback()
        controller.setVolume(1.0f)
        controller.startSleepTimer(30_000L)
        testDispatcher.scheduler.runCurrent()

        // Advance 15s: volume attenuated to 0.5f
        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.02f)

        // Cancel timer
        controller.cancelSleepTimer()
        testDispatcher.scheduler.runCurrent()

        assertFalse(controller.sleepTimerState.value.isActive)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)

        // Further time advancement does not attenuate volume or pause player
        testDispatcher.scheduler.advanceTimeBy(30_000L)
        testDispatcher.scheduler.runCurrent()
        assertTrue(fakePlayerHandler.isPlaying)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `replacing active timer while in fade restores baseline and arms new countdown`() = runTest(testDispatcher) {
        startPlayback()
        controller.setVolume(1.0f)
        controller.startSleepTimer(30_000L)
        testDispatcher.scheduler.runCurrent()

        // Advance 15s: volume attenuated to 0.5f
        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.02f)

        // Replace with 60s preset
        controller.startSleepTimer(60_000L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(60_000L, controller.sleepTimerState.value.remainingDurationMs)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
        controller.cancelSleepTimer()
    }

    @Test
    fun `user volume adjustments during active timer update baseline accurately`() = runTest(testDispatcher) {
        startPlayback()
        controller.setVolume(1.0f)
        controller.startSleepTimer(30_000L)
        testDispatcher.scheduler.runCurrent()

        // Advance 15s: ratio is 0.5 -> volume is 0.5f
        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.02f)

        // User manually sets volume to 0.6f while in fade
        controller.setVolume(0.6f)
        testDispatcher.scheduler.runCurrent()

        assertEquals(0.6f, controller.sleepTimerState.value.baselineVolume, 0.001f)
        // Attenuated volume should now be 0.6 * 0.5 = 0.3f
        assertEquals(0.3f, fakePlayerHandler.volume, 0.02f)

        // Cancel timer: restores to the user's updated baseline (0.6f), NOT 1.0f
        controller.cancelSleepTimer()
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.6f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `end of track mode completes and overrides repeat mode one`() = runTest(testDispatcher) {
        startPlayback(trackId = "trk-1", durationMs = 180_000L, positionMs = 140_000L)
        fakePlayerHandler.volume = 1.0f
        controller.setRepeatMode(RepeatMode.ONE)
        controller.startSleepTimerEndOfTrack()
        testDispatcher.scheduler.runCurrent()

        // Advance into fade window (position 165_000L, remaining 15s)
        fakePlayerHandler.currentPosition = 165_000L
        testDispatcher.scheduler.advanceTimeBy(500L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.03f)

        // ExoPlayer would loop on REPEAT_MODE_ONE via MEDIA_ITEM_TRANSITION_REASON_REPEAT
        val mediaItem = MediaItem.Builder().setMediaId("trk-1").build()
        fakePlayerHandler.fireMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)
        testDispatcher.scheduler.runCurrent()

        // Sleep timer must override repeat mode, pause player, restore baseline volume, and deactivate
        assertFalse(controller.sleepTimerState.value.isActive)
        assertFalse(fakePlayerHandler.isPlaying)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `end of track mode completes and overrides repeat mode all on transition`() = runTest(testDispatcher) {
        startPlayback(trackId = "trk-1", durationMs = 180_000L, positionMs = 175_000L)
        fakePlayerHandler.volume = 1.0f
        controller.setRepeatMode(RepeatMode.ALL)
        controller.startSleepTimerEndOfTrack()
        testDispatcher.scheduler.runCurrent()

        // Next item transition with AUTO
        val mediaItem = MediaItem.Builder().setMediaId("trk-2").build()
        fakePlayerHandler.fireMediaItemTransition(mediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        testDispatcher.scheduler.runCurrent()

        assertFalse(controller.sleepTimerState.value.isActive)
        assertFalse(fakePlayerHandler.isPlaying)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `end of track mode completes cleanly on STATE_ENDED`() = runTest(testDispatcher) {
        startPlayback(trackId = "trk-1", durationMs = 180_000L, positionMs = 170_000L)
        controller.startSleepTimerEndOfTrack()
        testDispatcher.scheduler.runCurrent()

        fakePlayerHandler.firePlaybackStateChanged(Player.STATE_ENDED)
        testDispatcher.scheduler.runCurrent()

        assertFalse(controller.sleepTimerState.value.isActive)
        assertFalse(fakePlayerHandler.isPlaying)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `seeking backward during end of track fade restores baseline volume`() = runTest(testDispatcher) {
        startPlayback(trackId = "trk-1", durationMs = 180_000L, positionMs = 165_000L)
        controller.startSleepTimerEndOfTrack()
        testDispatcher.scheduler.runCurrent()

        testDispatcher.scheduler.advanceTimeBy(500L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.03f)

        // User seeks back to 60s (remaining 120s > 30s)
        fakePlayerHandler.currentPosition = 60_000L
        testDispatcher.scheduler.advanceTimeBy(500L)
        testDispatcher.scheduler.runCurrent()

        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
        assertTrue(controller.sleepTimerState.value.isActive)
        controller.cancelSleepTimer()
    }

    @Test
    fun `storage disconnection cancels sleep timer and restores volume`() = runTest(testDispatcher) {
        fakeFileRepo.files.clear()
        fakeFileRepo.files.add(localFile1)
        fakeCoordinator.setSourceAvailable("src-sdcard-1", true)

        startPlayback()
        controller.setVolume(1.0f)
        controller.startSleepTimer(30_000L)
        testDispatcher.scheduler.runCurrent()

        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.02f)

        // Simulate storage volume disconnect
        fakeCoordinator.setSourceAvailable("src-sdcard-1", false)
        testDispatcher.scheduler.runCurrent()

        // Storage disconnection should pause playback, cancel timer, and restore baseline
        assertFalse(controller.sleepTimerState.value.isActive)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `playback error cancels sleep timer and restores volume`() = runTest(testDispatcher) {
        startPlayback()
        controller.setVolume(1.0f)
        controller.startSleepTimer(30_000L)
        testDispatcher.scheduler.runCurrent()

        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.02f)

        val error = PlaybackException("Decode error", null, PlaybackException.ERROR_CODE_DECODER_INIT_FAILED)
        fakePlayerHandler.firePlayerError(error)
        testDispatcher.scheduler.runCurrent()

        assertFalse(controller.sleepTimerState.value.isActive)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }

    @Test
    fun `controller release cancels sleep timer job and restores volume`() = runTest(testDispatcher) {
        startPlayback()
        controller.setVolume(1.0f)
        controller.startSleepTimer(30_000L)
        testDispatcher.scheduler.runCurrent()

        testDispatcher.scheduler.advanceTimeBy(15_000L)
        testDispatcher.scheduler.runCurrent()
        assertEquals(0.5f, fakePlayerHandler.volume, 0.02f)

        controller.release()
        testDispatcher.scheduler.runCurrent()

        assertFalse(controller.sleepTimerState.value.isActive)
        assertEquals(1.0f, fakePlayerHandler.volume, 0.001f)
    }
}
