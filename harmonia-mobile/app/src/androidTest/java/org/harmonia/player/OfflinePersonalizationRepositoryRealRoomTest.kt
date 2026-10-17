package org.harmonia.player

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.OfflinePersonalizationRepository
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.domain.model.personalization.TrackPersonalization
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Android instrumentation integration test verifying OfflinePersonalizationRepository
 * backed by a real Room in-memory SQLite database.
 *
 * NOTE: Requires an active Android device or emulator to execute at runtime.
 */
@RunWith(AndroidJUnit4::class)
class OfflinePersonalizationRepositoryRealRoomTest {

    private lateinit var db: HarmoniaDatabase
    private lateinit var repository: OfflinePersonalizationRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HarmoniaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = OfflinePersonalizationRepository(
            personalizationDao = db.personalizationDao(),
            trackDao = db.trackDao()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    private suspend fun insertTestTrack(id: String, title: String) {
        val now = System.currentTimeMillis()
        db.trackDao().upsertTrack(
            TrackEntity(
                id = id,
                title = title,
                durationSec = 200.0,
                trackNumber = 1,
                discNumber = 1,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Test
    fun realRoom_personalization_lifecycle_and_observation() = runBlocking {
        insertTestTrack("trk_real_1", "Real Room Track 1")
        insertTestTrack("trk_real_2", "Real Room Track 2")

        // Initial state
        val initial = repository.observePersonalization("trk_real_1").first()
        assertEquals("trk_real_1", initial.trackId)
        assertFalse(initial.isFavorite)
        assertNull(initial.rating)

        // Set favorite
        repository.setFavorite("trk_real_1", true)
        val afterFav = repository.observePersonalization("trk_real_1").first()
        assertTrue(afterFav.isFavorite)
        assertNull(afterFav.rating)

        // Set rating
        repository.setRating("trk_real_1", 5)
        val afterRating = repository.observePersonalization("trk_real_1").first()
        assertTrue(afterRating.isFavorite)
        assertEquals(5, afterRating.rating)

        // Observe favorite tracks
        val favorites = repository.observeFavoriteTracks().first()
        assertEquals(1, favorites.size)
        assertEquals("trk_real_1", favorites[0].id)

        // Toggle favorite
        val toggled = repository.toggleFavorite("trk_real_1")
        assertFalse(toggled)
        assertEquals(0, repository.observeFavoriteTracks().first().size)

        // Clear rating
        repository.clearRating("trk_real_1")
        val cleared = repository.getPersonalization("trk_real_1")
        assertFalse(cleared.isFavorite)
        assertNull(cleared.rating)
    }

    @Test
    fun realRoom_ratedTracks_ordering_by_stars_descending() = runBlocking {
        insertTestTrack("trk_a", "Song A")
        insertTestTrack("trk_b", "Song B")
        insertTestTrack("trk_c", "Song C")

        repository.setRating("trk_a", 2)
        repository.setRating("trk_b", 5)
        repository.setRating("trk_c", 4)

        val rated = repository.observeRatedTracks().first()
        assertEquals(3, rated.size)
        assertEquals("trk_b", rated[0].track.id)
        assertEquals(5, rated[0].rating)
        assertEquals("trk_c", rated[1].track.id)
        assertEquals(4, rated[1].rating)
        assertEquals("trk_a", rated[2].track.id)
        assertEquals(2, rated[2].rating)
    }
}
