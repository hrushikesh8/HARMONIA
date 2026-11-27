package org.harmonia.player.service

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

class PlayNextShuffleOrderTest {

    private var current = C.INDEX_UNSET
    private fun order(length: Int) = PlayNextShuffleOrder(length, Random(7)) { current }

    private fun PlayNextShuffleOrder.walk(): List<Int> {
        val out = mutableListOf<Int>()
        var i = firstIndex
        while (i != C.INDEX_UNSET) {
            out += i
            i = getNextIndex(i)
        }
        return out
    }

    @Test
    fun `a new queue is a full shuffle and walking it visits every song once`() {
        val o = order(0).cloneAndInsert(0, 6) as PlayNextShuffleOrder
        assertEquals((0 until 6).toSet(), o.walk().toSet())
        assertEquals(6, o.walk().size)
        assertEquals(o.playOrder(), o.walk())
    }

    @Test
    fun `play next puts the songs right after the current one`() {
        val start = order(0).cloneAndInsert(0, 5) as PlayNextShuffleOrder
        current = 2
        val o = start.cloneAndInsert(3, 2) as PlayNextShuffleOrder // queue grew to 7: new songs are 3 and 4
        val play = o.playOrder()
        val at = play.indexOf(2)
        assertEquals(listOf(3, 4), play.subList(at + 1, at + 3))
        assertEquals(7, play.size)
        assertEquals((0 until 7).toSet(), play.toSet())
    }

    @Test
    fun `add to queue plays after everything already queued`() {
        val start = order(0).cloneAndInsert(0, 4) as PlayNextShuffleOrder
        current = 1
        val o = start.cloneAndInsert(4, 2) as PlayNextShuffleOrder
        assertEquals(listOf(4, 5), o.playOrder().takeLast(2))
    }

    @Test
    fun `existing play order survives inserts before it`() {
        val start = order(0).cloneAndInsert(0, 4) as PlayNextShuffleOrder
        val before = start.playOrder()
        current = C.INDEX_UNSET
        val o = start.cloneAndInsert(0, 1) as PlayNextShuffleOrder
        assertEquals(before.map { it + 1 }, o.playOrder().filter { it != 0 })
    }

    @Test
    fun `removing songs keeps the rest in the same play order`() {
        val start = order(0).cloneAndInsert(0, 6) as PlayNextShuffleOrder
        val before = start.playOrder()
        val o = start.cloneAndRemove(1, 3) as PlayNextShuffleOrder
        val expected = before.filter { it !in 1..2 }.map { if (it >= 3) it - 2 else it }
        assertEquals(expected, o.playOrder())
        assertEquals(4, o.length)
    }

    @Test
    fun `moving a song in the queue keeps who plays when`() {
        val start = order(0).cloneAndInsert(0, 5) as PlayNextShuffleOrder
        val before = start.playOrder()
        // Move song at index 0 to index 3: indices 1,2,3 shift down by one.
        val o = start.cloneAndMove(0, 1, 3) as PlayNextShuffleOrder
        val newIndexOf = mapOf(0 to 3, 1 to 0, 2 to 1, 3 to 2, 4 to 4)
        assertEquals(before.map { newIndexOf.getValue(it) }, o.playOrder())
    }

    @Test
    fun `clear empties the order`() {
        val o = order(0).cloneAndInsert(0, 3).cloneAndClear()
        assertEquals(0, o.length)
        assertEquals(C.INDEX_UNSET, o.firstIndex)
        assertEquals(C.INDEX_UNSET, o.lastIndex)
    }
}
