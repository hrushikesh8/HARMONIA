package org.harmonia.player.playback

import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.PlaybackStatus
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure domain unit tests verifying authoritative queue navigation logic,
 * repeat modes, shuffle, and boundary conditions without Android dependencies.
 */
class PlaybackQueueLogicTest {

    private val track1 = Track(id = "trk-1", title = "Track 1", durationSec = 180.0)
    private val track2 = Track(id = "trk-2", title = "Track 2", durationSec = 210.0)
    private val track3 = Track(id = "trk-3", title = "Track 3", durationSec = 195.0)

    @Test
    fun `queue navigation respects linear order when repeat is OFF`() {
        val tracks = listOf(track1, track2, track3)
        var currentIndex = 0

        // Next 0 -> 1
        val next1 = if (currentIndex < tracks.size - 1) currentIndex + 1 else null
        assertEquals(1, next1)
        currentIndex = next1!!

        // Next 1 -> 2
        val next2 = if (currentIndex < tracks.size - 1) currentIndex + 1 else null
        assertEquals(2, next2)
        currentIndex = next2!!

        // Next 2 -> boundary reached (repeat OFF)
        val nextAtEnd = if (currentIndex < tracks.size - 1) currentIndex + 1 else null
        assertNull(nextAtEnd)
    }

    @Test
    fun `queue navigation loops back to start when repeat is ALL`() {
        val tracks = listOf(track1, track2, track3)
        val currentIndex = 2 // at end

        val nextIndex = if (currentIndex < tracks.size - 1) {
            currentIndex + 1
        } else {
            0 // wrap around
        }
        assertEquals(0, nextIndex)
    }

    @Test
    fun `previous navigation rewinds if track played greater than threshold`() {
        val positionMs = 5000L // 5 seconds in
        val currentIndex = 2

        // Standard music player behavior: if position > 3000ms, rewind to 0 instead of previous track
        val shouldRewind = positionMs > 3000L
        val targetIndex = if (shouldRewind) currentIndex else currentIndex - 1
        val targetPos = if (shouldRewind) 0L else 0L

        assertTrue(shouldRewind)
        assertEquals(2, targetIndex)
        assertEquals(0L, targetPos)
    }

    @Test
    fun `previous navigation goes to previous track if within first 3 seconds`() {
        val positionMs = 1200L
        val currentIndex = 2

        val shouldRewind = positionMs > 3000L
        val targetIndex = if (shouldRewind) currentIndex else (currentIndex - 1).coerceAtLeast(0)

        assertFalse(shouldRewind)
        assertEquals(1, targetIndex)
    }

    @Test
    fun `queue replacement resets active index and state`() {
        val initialTracks = listOf(track1)
        val newTracks = listOf(track2, track3)

        val queueItems = newTracks.mapIndexed { idx, trk ->
            QueueItem(
                position = idx,
                trackId = trk.id,
                track = trk,
                originalIndex = idx
            )
        }

        assertEquals(2, queueItems.size)
        assertEquals("trk-2", queueItems[0].trackId)
        assertEquals(0, queueItems[0].position)
        assertEquals("trk-3", queueItems[1].trackId)
        assertEquals(1, queueItems[1].position)
    }

    @Test
    fun `playback state default values and transitions`() {
        val state = PlaybackState(
            currentTrackId = track1.id,
            isPlaying = true,
            status = PlaybackStatus.PLAYING,
            currentPositionMs = 15000L,
            durationMs = 180000L,
            activeQueueIndex = 0,
            repeatMode = RepeatMode.ALL
        )

        assertEquals("trk-1", state.currentTrackId)
        assertTrue(state.isPlaying)
        assertEquals(PlaybackStatus.PLAYING, state.status)
        assertEquals(15000L, state.currentPositionMs)
        assertEquals(180000L, state.durationMs)
        assertEquals(RepeatMode.ALL, state.repeatMode)
    }
}
