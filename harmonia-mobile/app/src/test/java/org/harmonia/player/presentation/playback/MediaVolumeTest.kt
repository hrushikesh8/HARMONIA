package org.harmonia.player.presentation.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaVolumeTest {

    @Test
    fun `slider ends map to the min and max volume steps`() {
        assertEquals(0, MediaVolume.levelForFraction(0f, minLevel = 0, maxLevel = 15))
        assertEquals(15, MediaVolume.levelForFraction(1f, minLevel = 0, maxLevel = 15))
        assertEquals(1, MediaVolume.levelForFraction(0f, minLevel = 1, maxLevel = 25))
    }

    @Test
    fun `slider values round to the nearest whole step`() {
        assertEquals(8, MediaVolume.levelForFraction(0.5f, minLevel = 0, maxLevel = 15))
        assertEquals(7, MediaVolume.levelForFraction(0.45f, minLevel = 0, maxLevel = 15))
    }

    @Test
    fun `out of range slider values are clamped`() {
        assertEquals(0, MediaVolume.levelForFraction(-0.2f, minLevel = 0, maxLevel = 15))
        assertEquals(15, MediaVolume.levelForFraction(1.4f, minLevel = 0, maxLevel = 15))
    }
}
