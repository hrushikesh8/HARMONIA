package org.harmonia.player.presentation.carmode

import org.junit.Assert.assertEquals
import org.junit.Test

class CarModeTest {
    @Test
    fun `times read as minutes and seconds`() {
        assertEquals("0:00", formatCarTime(0))
        assertEquals("1:32", formatCarTime(92_400))
        assertEquals("12:05", formatCarTime(725_000))
        assertEquals("0:00", formatCarTime(-5))
    }
}
