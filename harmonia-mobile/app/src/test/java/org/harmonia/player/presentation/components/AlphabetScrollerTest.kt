package org.harmonia.player.presentation.components

import org.harmonia.player.presentation.library.toggled
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlphabetScrollerTest {

    @Test
    fun `index letter is the first letter, accents removed, others go under hash`() {
        assertEquals('A', indexLetterFor("abba"))
        assertEquals('E', indexLetterFor("Édith Piaf"))
        assertEquals('#', indexLetterFor("99 Problems"))
        assertEquals('#', indexLetterFor("  "))
        assertEquals('#', indexLetterFor(null))
        assertEquals('#', indexLetterFor("ももクロ"))
        assertEquals('T', indexLetterFor("The Beatles")) // matches how the list is sorted
    }

    @Test
    fun `positions are the first item of each letter`() {
        val keys = listOf("1999", "Alpha", "Apple", "Beta", "Delta", "delta two")
        assertEquals(mapOf('#' to 0, 'A' to 1, 'B' to 3, 'D' to 4), alphabetPositions(keys))
    }

    @Test
    fun `an empty letter jumps to the next letter that has songs, else the previous one`() {
        val positions = mapOf('A' to 0, 'D' to 5, 'M' to 9)
        assertEquals(0, positionForLetter('A', positions))
        assertEquals(5, positionForLetter('B', positions))
        assertEquals(9, positionForLetter('E', positions))
        assertEquals(9, positionForLetter('Z', positions))
        assertNull(positionForLetter('A', emptyMap()))
    }

    @Test
    fun `bar shows only when items fall under at least two letters`() {
        assertFalse(shouldShowAlphabetBar(mapOf('A' to 0)))
        assertTrue(shouldShowAlphabetBar(mapOf('A' to 0, 'B' to 4)))
    }

    @Test
    fun `selection toggles in and out`() {
        val picked = setOf("a").toggled("b")
        assertEquals(setOf("a", "b"), picked)
        assertEquals(setOf("b"), picked.toggled("a"))
    }
}
