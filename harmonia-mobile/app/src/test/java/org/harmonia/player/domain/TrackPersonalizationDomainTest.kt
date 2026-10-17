package org.harmonia.player.domain

import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.harmonia.player.domain.model.personalization.TrackPersonalization
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TrackPersonalizationDomainTest {

    private fun createTestTrack(id: String = "trk_1"): Track {
        return Track(
            id = id,
            title = "Domain Track",
            durationSec = 210.0,
            trackNumber = 1,
            discNumber = 1
        )
    }

    @Test
    fun trackPersonalization_allows_valid_1_to_5_ratings() {
        for (star in 1..5) {
            val personalization = TrackPersonalization(
                trackId = "trk_test",
                isFavorite = true,
                rating = star,
                updatedAt = 1000L
            )
            assertEquals(star, personalization.rating)
            assertTrue(personalization.isFavorite)
            assertEquals("trk_test", personalization.trackId)
            assertEquals(1000L, personalization.updatedAt)
        }
    }

    @Test
    fun trackPersonalization_allows_null_rating() {
        val personalization = TrackPersonalization(
            trackId = "trk_test",
            isFavorite = false,
            rating = null,
            updatedAt = 500L
        )
        assertNull(personalization.rating)
        assertFalse(personalization.isFavorite)
    }

    @Test
    fun trackPersonalization_rejects_rating_below_1() {
        try {
            TrackPersonalization(trackId = "trk_1", rating = 0)
            fail("Expected IllegalArgumentException for rating = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("between 1 and 5 stars") == true)
        }

        try {
            TrackPersonalization(trackId = "trk_1", rating = -1)
            fail("Expected IllegalArgumentException for rating = -1")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("between 1 and 5 stars") == true)
        }
    }

    @Test
    fun trackPersonalization_rejects_rating_above_5() {
        try {
            TrackPersonalization(trackId = "trk_1", rating = 6)
            fail("Expected IllegalArgumentException for rating = 6")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("between 1 and 5 stars") == true)
        }

        try {
            TrackPersonalization(trackId = "trk_1", rating = 10)
            fail("Expected IllegalArgumentException for rating = 10")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("between 1 and 5 stars") == true)
        }
    }

    @Test
    fun ratedTrack_allows_valid_1_to_5_ratings() {
        val track = createTestTrack("trk_test")
        for (star in 1..5) {
            val rated = RatedTrack(
                track = track,
                rating = star,
                ratedAt = 12345L
            )
            assertEquals(track, rated.track)
            assertEquals(star, rated.rating)
            assertEquals(12345L, rated.ratedAt)
        }
    }

    @Test
    fun ratedTrack_rejects_rating_out_of_range() {
        val track = createTestTrack("trk_test")
        try {
            RatedTrack(track = track, rating = 0, ratedAt = 100L)
            fail("Expected IllegalArgumentException for rating = 0")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("between 1 and 5 stars") == true)
        }

        try {
            RatedTrack(track = track, rating = 6, ratedAt = 100L)
            fail("Expected IllegalArgumentException for rating = 6")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("between 1 and 5 stars") == true)
        }
    }

    @Test
    fun trackPersonalization_equality_and_copy() {
        val p1 = TrackPersonalization("trk_1", isFavorite = true, rating = 4, updatedAt = 100L)
        val p2 = TrackPersonalization("trk_1", isFavorite = true, rating = 4, updatedAt = 100L)
        val p3 = p1.copy(rating = 5)

        assertEquals(p1, p2)
        assertEquals(5, p3.rating)
        assertTrue(p3.isFavorite)
    }
}
