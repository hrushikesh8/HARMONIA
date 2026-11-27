package org.harmonia.player.presentation.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkTintTest {

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    private fun hueDistance(a: Float, b: Float) = minOf(kotlin.math.abs(a - b), 360f - kotlin.math.abs(a - b))

    @Test
    fun `hsl round trip keeps the colour`() {
        listOf(rgb(200, 40, 40), rgb(30, 160, 90), rgb(60, 90, 220), rgb(240, 200, 30)).forEach { c ->
            val back = hslToArgb(argbToHsl(c))
            listOf(16, 8, 0).forEach { shift ->
                val diff = kotlin.math.abs(((c shr shift) and 0xFF) - ((back shr shift) and 0xFF))
                assertTrue("channel off by $diff", diff <= 2)
            }
        }
    }

    @Test
    fun `the most common colourful hue wins`() {
        val blue = rgb(40, 90, 220)
        val orange = rgb(230, 140, 30)
        val pixels = IntArray(600) { if (it < 400) blue else orange }
        val hsl = dominantArtworkHsl(pixels)
        assertNotNull(hsl)
        hsl!!
        assertTrue(hueDistance(hsl.hue, argbToHsl(blue).hue) < 10f)
    }

    @Test
    fun `a white border and black background do not decide the colour`() {
        val red = rgb(200, 30, 40)
        val pixels = IntArray(1000) {
            when {
                it < 450 -> rgb(255, 255, 255)
                it < 850 -> rgb(5, 5, 5)
                else -> red
            }
        }
        val hsl = dominantArtworkHsl(pixels)!!
        assertTrue(hueDistance(hsl.hue, argbToHsl(red).hue) < 10f)
    }

    @Test
    fun `grey covers give no tint`() {
        val pixels = IntArray(500) { rgb(it % 256, it % 256, it % 256) }
        assertNull(dominantArtworkHsl(pixels))
        assertNull(dominantArtworkHsl(IntArray(0)))
    }

    @Test
    fun `background stays dark in dark mode and pale in light mode`() {
        val vivid = Hsl(hue = 50f, saturation = 1f, lightness = 0.5f)
        assertEquals(0.22f, argbToHsl(tintBackgroundArgb(vivid, isDark = true)).lightness, 0.02f)
        assertEquals(0.88f, argbToHsl(tintBackgroundArgb(vivid, isDark = false)).lightness, 0.02f)
        assertTrue(argbToHsl(tintBackgroundArgb(vivid, isDark = true)).saturation <= 0.56f)
    }
}
