package org.harmonia.player.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import org.harmonia.player.data.playback.HarmoniaPlayerController
import org.harmonia.player.domain.model.Track
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Removing songs from the queue, undoing it, and adding songs must keep the app's queue and the
 * player's own playlist identical (task 4.4).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QueueEditingTest {

    private val dispatcher = StandardTestDispatcher()
    private val tracks = (1..5).map { Track(id = "t$it", title = "Song $it", durationSec = 60.0) }

    private lateinit var fake: FakePlayerInvocationHandler
    private lateinit var controller: HarmoniaPlayerController

    @Before
    fun setup() {
        fake = FakePlayerInvocationHandler()
        fake.mediaItemCount = 0
        controller = HarmoniaPlayerController(
            context = null,
            playbackStateRepository = FakePlaybackStateRepository(),
            listeningEventRepository = FakeListeningEventRepository(),
            trackRepository = FakeTrackRepository(tracks),
            localFileRepository = FakeLocalFileRepository(),
            storageVolumeLifecycleCoordinator = FakeStorageVolumeLifecycleCoordinator(),
            scope = CoroutineScope(dispatcher + SupervisorJob()),
            playerOverride = fake.createProxy(),
            enablePositionPolling = false,
            timerDispatcher = dispatcher
        )
        controller.setQueue(tracks, 0)
        dispatcher.scheduler.runCurrent()
    }

    @After
    fun tearDown() = controller.release()

    private fun appQueue() = controller.queue.value.map { it.trackId }

    private fun assertInStep(expected: List<String>) {
        assertEquals(expected, appQueue())
        assertEquals("player playlist", expected, fake.mediaIds)
        assertEquals(expected.size, fake.mediaItemCount)
    }

    @Test
    fun `remove then undo puts the song back in the same place`() {
        controller.removeQueueItem(3)
        dispatcher.scheduler.runCurrent()
        assertInStep(listOf("t1", "t2", "t3", "t5"))

        controller.insertIntoQueue(3, listOf(tracks[3]))
        dispatcher.scheduler.runCurrent()
        assertInStep(listOf("t1", "t2", "t3", "t4", "t5"))
    }

    @Test
    fun `undo before the current song keeps the current song`() {
        fake.currentMediaItemIndex = 3
        controller.jumpToQueueIndex(3)
        dispatcher.scheduler.runCurrent()

        controller.removeQueueItem(1)
        dispatcher.scheduler.runCurrent()
        assertInStep(listOf("t1", "t3", "t4", "t5"))

        controller.insertIntoQueue(1, listOf(tracks[1]))
        dispatcher.scheduler.runCurrent()
        assertInStep(listOf("t1", "t2", "t3", "t4", "t5"))
        assertEquals(3, controller.playbackState.value.activeQueueIndex)
    }

    @Test
    fun `several removals and undos in a row stay in step`() {
        controller.removeQueueItem(4)
        dispatcher.scheduler.runCurrent()
        controller.removeQueueItem(2)
        dispatcher.scheduler.runCurrent()
        assertInStep(listOf("t1", "t2", "t4"))

        controller.insertIntoQueue(2, listOf(tracks[2]))
        dispatcher.scheduler.runCurrent()
        controller.insertIntoQueue(4, listOf(tracks[4]))
        dispatcher.scheduler.runCurrent()
        assertInStep(listOf("t1", "t2", "t3", "t4", "t5"))
    }

    @Test
    fun `adding several songs to the queue and playing them next stays in step`() {
        controller.appendToQueue(listOf(tracks[0], tracks[1]))
        dispatcher.scheduler.runCurrent()
        assertInStep(listOf("t1", "t2", "t3", "t4", "t5", "t1", "t2"))

        controller.playNext(listOf(tracks[4]))
        dispatcher.scheduler.runCurrent()
        assertInStep(listOf("t1", "t5", "t2", "t3", "t4", "t5", "t1", "t2"))
    }
}
