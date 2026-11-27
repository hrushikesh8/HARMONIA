package org.harmonia.player.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DragAutoScrollTest {

    private fun speed(top: Float, height: Float = 60f) =
        dragAutoScrollSpeed(top, top + height, viewportStart = 0f, viewportEnd = 1000f, edge = 100f, maxSpeed = 20f)

    @Test
    fun `no scrolling in the middle of the list`() {
        assertEquals(0f, speed(400f))
    }

    @Test
    fun `scrolls up near the top and down near the bottom`() {
        assertTrue(speed(50f) < 0f)
        assertTrue(speed(900f) > 0f)
    }

    @Test
    fun `goes faster deeper into the edge, up to the maximum`() {
        assertTrue(speed(10f) < speed(60f))
        assertEquals(-20f, speed(-200f))
        assertEquals(20f, speed(1200f))
    }

    @Test
    fun `an item taller than the list does not scroll`() {
        assertEquals(0f, speed(-50f, height = 1100f))
    }
}
