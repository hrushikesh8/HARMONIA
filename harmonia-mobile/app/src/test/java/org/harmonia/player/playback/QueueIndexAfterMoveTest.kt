package org.harmonia.player.playback

import org.harmonia.player.data.playback.HarmoniaPlayerController.Companion.indexAfterMove
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueIndexAfterMoveTest {

    @Test
    fun `moving the active item follows it to its new slot`() {
        assertEquals(4, indexAfterMove(activeIndex = 1, fromIndex = 1, toIndex = 4))
        assertEquals(0, indexAfterMove(activeIndex = 3, fromIndex = 3, toIndex = 0))
    }

    @Test
    fun `moving an earlier item past the active item shifts active up`() {
        assertEquals(2, indexAfterMove(activeIndex = 3, fromIndex = 0, toIndex = 5))
        assertEquals(2, indexAfterMove(activeIndex = 3, fromIndex = 1, toIndex = 3))
    }

    @Test
    fun `moving a later item before the active item shifts active down`() {
        assertEquals(4, indexAfterMove(activeIndex = 3, fromIndex = 5, toIndex = 0))
        assertEquals(4, indexAfterMove(activeIndex = 3, fromIndex = 4, toIndex = 3))
    }

    @Test
    fun `moves entirely before or after the active item leave it unchanged`() {
        assertEquals(3, indexAfterMove(activeIndex = 3, fromIndex = 0, toIndex = 2))
        assertEquals(3, indexAfterMove(activeIndex = 3, fromIndex = 6, toIndex = 4))
    }
}
