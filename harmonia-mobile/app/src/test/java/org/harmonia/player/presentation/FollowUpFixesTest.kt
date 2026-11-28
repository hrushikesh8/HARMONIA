package org.harmonia.player.presentation

import org.harmonia.player.domain.playback.shufflePlayOrder
import org.harmonia.player.presentation.library.friendlyScanError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowUpFixesTest {

    @Test
    fun `shuffle order is read by following the player's next pointers`() {
        // Shuffled play order 2 -> 0 -> 3 -> 1
        val next = mapOf(2 to 0, 0 to 3, 3 to 1, 1 to -1)
        assertEquals(listOf(2, 0, 3, 1), shufflePlayOrder(4, 2) { next.getValue(it) })
    }

    @Test
    fun `a broken or partial order falls back to queue order`() {
        assertTrue(shufflePlayOrder(4, 0) { if (it == 0) 1 else -1 }.isEmpty()) // stops early
        assertTrue(shufflePlayOrder(3, 0) { if (it == 0) 1 else 0 }.isEmpty())  // loops
        assertTrue(shufflePlayOrder(0, 0) { -1 }.isEmpty())
        assertTrue(shufflePlayOrder(3, -1) { -1 }.isEmpty())
    }

    @Test
    fun `scan errors are shown in plain words`() {
        assertEquals(
            "Some songs couldn't be read. The rest of your library is fine.",
            friendlyScanError("3 file(s) failed during reconciliation")
        )
        assertEquals("Harmonia needs access to your music to look for songs.", friendlyScanError("Storage permission required"))
        assertEquals("Something went wrong while looking for songs.", friendlyScanError("Media reconciliation failure"))
        assertEquals("Something went wrong while looking for songs.", friendlyScanError(null))
    }
}
