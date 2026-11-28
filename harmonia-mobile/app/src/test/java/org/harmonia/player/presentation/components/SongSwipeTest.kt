package org.harmonia.player.presentation.components

import androidx.compose.material3.SwipeToDismissBoxValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SongSwipeTest {

    @Test
    fun `right to left plays next, left to right removes`() {
        assertEquals(SongSwipe.PLAY_NEXT, SongSwipe.of(SwipeToDismissBoxValue.EndToStart))
        assertEquals(SongSwipe.REMOVE, SongSwipe.of(SwipeToDismissBoxValue.StartToEnd))
        assertNull(SongSwipe.of(SwipeToDismissBoxValue.Settled))
    }
}
