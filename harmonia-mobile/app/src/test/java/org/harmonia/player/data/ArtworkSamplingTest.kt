package org.harmonia.player.data

import org.harmonia.player.data.local.ArtworkProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSamplingTest {

    @Test
    fun `size buckets round up to powers of two starting at 64`() {
        assertEquals(64, ArtworkProvider.sizeBucket(1))
        assertEquals(64, ArtworkProvider.sizeBucket(64))
        assertEquals(128, ArtworkProvider.sizeBucket(65))
        assertEquals(256, ArtworkProvider.sizeBucket(132))
        assertEquals(1024, ArtworkProvider.sizeBucket(840))
    }

    @Test
    fun `sample size keeps both sides at or above the target`() {
        assertEquals(4, ArtworkProvider.calculateInSampleSize(1200, 1200, 256))
        assertEquals(1, ArtworkProvider.calculateInSampleSize(300, 300, 256))
        assertEquals(2, ArtworkProvider.calculateInSampleSize(3000, 600, 256))
    }

    @Test
    fun `small images are never upscaled`() {
        assertEquals(1, ArtworkProvider.calculateInSampleSize(100, 100, 512))
    }
}
