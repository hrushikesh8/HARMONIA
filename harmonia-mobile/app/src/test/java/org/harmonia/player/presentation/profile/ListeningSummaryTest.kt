package org.harmonia.player.presentation.profile

import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.ListeningEventType
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.TrackPlayStats
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningSummaryTest {

    private val a1 = Track(id = "a1", title = "A1", artistId = "ar-a", artistName = "Nova", durationSec = 120.0)
    private val a2 = Track(id = "a2", title = "A2", artistId = "ar-a", artistName = "Nova", durationSec = 180.0)
    private val b1 = Track(id = "b1", title = "B1", artistId = "ar-b", artistName = "Echo", durationSec = 60.0)
    private val tracks = listOf(a1, a2, b1)

    private fun stat(id: String, recent: Int, last: Long = 0) = TrackPlayStats(id, totalPlays = recent + 1, recentPlays = recent, lastPlayedAt = last)

    @Test
    fun `month totals add up plays and approximate minutes`() {
        val summary = buildListeningSummary(tracks, listOf(stat("a1", 2), stat("b1", 3)), emptyList(), emptyList())
        assertEquals(5, summary.playsThisMonth)
        assertEquals(7, summary.minutesThisMonth) // 2x2 min + 3x1 min
    }

    @Test
    fun `top artists combine their songs' plays`() {
        val summary = buildListeningSummary(tracks, listOf(stat("a1", 2), stat("a2", 2), stat("b1", 3)), emptyList(), emptyList())
        assertEquals(listOf("Nova" to 4, "Echo" to 3), summary.topArtists.map { it.name to it.plays })
        assertEquals("ar-a", summary.topArtists.first().artistId)
    }

    @Test
    fun `recently played skips back-to-back repeats and non-start events`() {
        fun ev(id: String, track: String, at: Long, type: ListeningEventType = ListeningEventType.STARTED) =
            ListeningEvent(id = id, eventType = type, trackId = track, timestamp = at)
        val events = listOf(ev("1", "a1", 100), ev("2", "a1", 200), ev("3", "b1", 300), ev("4", "b1", 350, ListeningEventType.PAUSED), ev("5", "a1", 400))
        val summary = buildListeningSummary(tracks, emptyList(), events, emptyList())
        assertEquals(listOf("a1", "b1", "a1"), summary.recentlyPlayed.map { it.track.id })
    }

    @Test
    fun `ratings are listed best first`() {
        val rated = listOf(RatedTrack(a1, 3, 10), RatedTrack(b1, 5, 5), RatedTrack(a2, 5, 20))
        val summary = buildListeningSummary(tracks, emptyList(), emptyList(), rated)
        assertEquals(listOf("a2", "b1", "a1"), summary.ratedTracks.map { it.track.id })
    }

    @Test
    fun `time ago reads naturally`() {
        val min = 60_000L
        assertEquals("Just now", formatTimeAgo(10 * min, 10 * min))
        assertEquals("12 min ago", formatTimeAgo(100 * min, 88 * min))
        assertEquals("3 hr ago", formatTimeAgo(1000 * min, 1000 * min - 3 * 60 * min))
        assertEquals("Yesterday", formatTimeAgo(5000 * min, 5000 * min - 30 * 60 * min))
        assertEquals("1 play", playsLabel(1))
    }
}
