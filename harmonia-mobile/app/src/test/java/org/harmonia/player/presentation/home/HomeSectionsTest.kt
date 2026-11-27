package org.harmonia.player.presentation.home

import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.TrackPlayStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSectionsTest {

    private val day = 24L * 60 * 60 * 1000
    private val now = 1_000L * day
    private val tracks = listOf("a", "b", "c", "d", "e").associateWith { Track(id = it, title = it.uppercase(), durationSec = 180.0) }

    private fun stats(id: String, total: Int, recent: Int, lastPlayedDaysAgo: Long) =
        TrackPlayStats(id, totalPlays = total, recentPlays = recent, lastPlayedAt = now - lastPlayedDaysAgo * day)

    @Test
    fun `top this month ranks by plays in the window, ties by most recent`() {
        val result = topThisMonth(
            listOf(
                stats("a", total = 20, recent = 2, lastPlayedDaysAgo = 5),
                stats("b", total = 4, recent = 4, lastPlayedDaysAgo = 1),
                stats("c", total = 9, recent = 2, lastPlayedDaysAgo = 1),
                stats("d", total = 50, recent = 0, lastPlayedDaysAgo = 90)
            ),
            tracks
        )
        assertEquals(listOf("b", "c", "a"), result.map { it.id })
    }

    @Test
    fun `top this month is capped and skips songs no longer in the library`() {
        val many = (1..8).map { stats("x$it", total = it, recent = it, lastPlayedDaysAgo = 1) } +
            stats("a", total = 1, recent = 1, lastPlayedDaysAgo = 1)
        val result = topThisMonth(many, tracks)
        assertEquals(listOf("a"), result.map { it.id })
        assertTrue(topThisMonth(List(10) { stats("a", 1, 1, 1) }, tracks).size <= TOP_THIS_MONTH_LIMIT)
    }

    @Test
    fun `rediscover picks well-played songs not heard in the last month`() {
        val result = rediscover(
            listOf(
                stats("a", total = 12, recent = 0, lastPlayedDaysAgo = 60),  // yes
                stats("b", total = 30, recent = 3, lastPlayedDaysAgo = 2),   // played recently: no
                stats("c", total = 2, recent = 0, lastPlayedDaysAgo = 200),  // too few plays: no
                stats("d", total = 25, recent = 0, lastPlayedDaysAgo = 45)   // yes, more plays
            ),
            tracks,
            now = now
        )
        assertEquals(listOf("d", "a"), result.map { it.id })
    }
}
