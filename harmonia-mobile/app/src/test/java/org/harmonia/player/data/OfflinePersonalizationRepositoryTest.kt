package org.harmonia.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.OfflinePersonalizationRepository
import org.harmonia.player.data.local.dao.PersonalizationDao
import org.harmonia.player.data.local.entity.FavoriteEntity
import org.harmonia.player.data.local.entity.RatingEntity
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.data.local.entity.TrackWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for OfflinePersonalizationRepository using Fake DAOs.
 * Tests mapping, mutations, reactive emissions, validation, clearing, missing tracks, and error propagation.
 */
class OfflinePersonalizationRepositoryTest {

    private lateinit var fakePersonalizationDao: FakePersonalizationDao
    private lateinit var fakeTrackDao: FakeTrackDao
    private lateinit var repository: OfflinePersonalizationRepository

    private fun createTrackEntity(id: String, title: String): TrackEntity {
        return TrackEntity(
            id = id,
            title = title,
            durationSec = 180.0,
            trackNumber = 1,
            discNumber = 1,
            createdAt = 1000L,
            updatedAt = 1000L
        )
    }

    @Before
    fun setUp() {
        fakePersonalizationDao = FakePersonalizationDao()
        fakeTrackDao = FakeTrackDao()
        fakePersonalizationDao.trackDetailsProvider = {
            runBlocking { fakeTrackDao.observeAllTracks().first() }
        }
        repository = OfflinePersonalizationRepository(
            personalizationDao = fakePersonalizationDao,
            trackDao = fakeTrackDao
        )
    }

    @Test
    fun observePersonalization_emits_initial_unfavorited_and_unrated_state() = runBlocking {
        val personalization = repository.observePersonalization("trk_1").first()
        assertEquals("trk_1", personalization.trackId)
        assertFalse(personalization.isFavorite)
        assertNull(personalization.rating)
        assertEquals(0L, personalization.updatedAt)
    }

    @Test
    fun observePersonalization_reacts_to_favorite_and_rating_changes() = runBlocking {
        repository.setFavorite("trk_1", true)
        var state = repository.observePersonalization("trk_1").first()
        assertTrue(state.isFavorite)
        assertNull(state.rating)
        assertTrue(state.updatedAt > 0L)

        repository.setRating("trk_1", 5)
        state = repository.observePersonalization("trk_1").first()
        assertTrue(state.isFavorite)
        assertEquals(5, state.rating)

        repository.setFavorite("trk_1", false)
        state = repository.observePersonalization("trk_1").first()
        assertFalse(state.isFavorite)
        assertEquals(5, state.rating)
    }

    @Test
    fun observePersonalization_computes_max_updated_at() = runBlocking {
        fakePersonalizationDao.upsertFavorite(FavoriteEntity("trk_1", clientUpdatedAt = 1000L))
        fakePersonalizationDao.upsertRating(RatingEntity("trk_1", rating = 4, clientUpdatedAt = 2000L))

        val state = repository.observePersonalization("trk_1").first()
        assertEquals(2000L, state.updatedAt)
    }

    @Test
    fun observeFavorite_emits_boolean_state() = runBlocking {
        assertFalse(repository.observeFavorite("trk_1").first())

        repository.setFavorite("trk_1", true)
        assertTrue(repository.observeFavorite("trk_1").first())

        repository.setFavorite("trk_1", false)
        assertFalse(repository.observeFavorite("trk_1").first())
    }

    @Test
    fun observeRating_emits_star_rating_and_null_when_cleared() = runBlocking {
        assertNull(repository.observeRating("trk_1").first())

        repository.setRating("trk_1", 4)
        assertEquals(4, repository.observeRating("trk_1").first())

        repository.setRating("trk_1", null)
        assertNull(repository.observeRating("trk_1").first())
    }

    @Test
    fun setFavorite_is_idempotent() = runBlocking {
        repository.setFavorite("trk_1", true)
        repository.setFavorite("trk_1", true)
        assertEquals(1, fakePersonalizationDao.getFavoriteCount())
        assertTrue(repository.getPersonalization("trk_1").isFavorite)

        repository.setFavorite("trk_1", false)
        repository.setFavorite("trk_1", false)
        assertEquals(0, fakePersonalizationDao.getFavoriteCount())
        assertFalse(repository.getPersonalization("trk_1").isFavorite)
    }

    @Test
    fun toggleFavorite_flips_state_atomically() = runBlocking {
        val result1 = repository.toggleFavorite("trk_1")
        assertTrue(result1)
        assertTrue(repository.getPersonalization("trk_1").isFavorite)

        val result2 = repository.toggleFavorite("trk_1")
        assertFalse(result2)
        assertFalse(repository.getPersonalization("trk_1").isFavorite)
    }

    @Test
    fun setRating_accepts_valid_ratings() = runBlocking {
        for (star in 1..5) {
            repository.setRating("trk_1", star)
            assertEquals(star, repository.getPersonalization("trk_1").rating)
        }
    }

    @Test
    fun setRating_rejects_out_of_range_ratings() = runBlocking {
        val invalidRatings = listOf(0, -1, 6, 10)
        for (invalid in invalidRatings) {
            try {
                repository.setRating("trk_1", invalid)
                fail("Expected IllegalArgumentException for rating: $invalid")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message?.contains("between 1 and 5 stars") == true)
            }
        }
    }

    @Test
    fun clearRating_removes_rating() = runBlocking {
        repository.setRating("trk_1", 3)
        assertEquals(3, repository.getPersonalization("trk_1").rating)

        repository.clearRating("trk_1")
        assertNull(repository.getPersonalization("trk_1").rating)
    }

    @Test
    fun clearPersonalization_removes_both_favorite_and_rating() = runBlocking {
        repository.setFavorite("trk_1", true)
        repository.setRating("trk_1", 5)

        val before = repository.getPersonalization("trk_1")
        assertTrue(before.isFavorite)
        assertEquals(5, before.rating)

        repository.clearPersonalization("trk_1")

        val after = repository.getPersonalization("trk_1")
        assertFalse(after.isFavorite)
        assertNull(after.rating)
        assertEquals(0, fakePersonalizationDao.getFavoriteCount())
        assertEquals(0, fakePersonalizationDao.getRatingCount())
    }

    @Test
    fun getPersonalization_returns_snapshot_for_missing_track() = runBlocking {
        val snapshot = repository.getPersonalization("nonexistent_id")
        assertEquals("nonexistent_id", snapshot.trackId)
        assertFalse(snapshot.isFavorite)
        assertNull(snapshot.rating)
        assertEquals(0L, snapshot.updatedAt)
    }

    @Test
    fun observeFavoriteTracks_maps_to_domain_tracks_preserving_order() = runBlocking {
        fakeTrackDao.seedTrackWithDetails(TrackWithDetails(track = createTrackEntity("trk_a", "Alpha")))
        fakeTrackDao.seedTrackWithDetails(TrackWithDetails(track = createTrackEntity("trk_b", "Beta")))

        fakePersonalizationDao.upsertFavorite(FavoriteEntity("trk_a", clientUpdatedAt = 1000L))
        fakePersonalizationDao.upsertFavorite(FavoriteEntity("trk_b", clientUpdatedAt = 2000L))

        val favorites = repository.observeFavoriteTracks().first()
        assertEquals(2, favorites.size)
        // trk_b was favorited more recently (2000L vs 1000L)
        assertEquals("trk_b", favorites[0].id)
        assertEquals("trk_a", favorites[1].id)
    }

    @Test
    fun observeRatedTracks_maps_to_domain_ratedTracks_preserving_order() = runBlocking {
        fakeTrackDao.seedTrackWithDetails(TrackWithDetails(track = createTrackEntity("trk_a", "Track A")))
        fakeTrackDao.seedTrackWithDetails(TrackWithDetails(track = createTrackEntity("trk_b", "Track B")))
        fakeTrackDao.seedTrackWithDetails(TrackWithDetails(track = createTrackEntity("trk_c", "Track C")))

        fakePersonalizationDao.upsertRating(RatingEntity("trk_a", rating = 3, clientUpdatedAt = 1000L))
        fakePersonalizationDao.upsertRating(RatingEntity("trk_b", rating = 5, clientUpdatedAt = 1000L))
        fakePersonalizationDao.upsertRating(RatingEntity("trk_c", rating = 4, clientUpdatedAt = 1000L))

        val rated = repository.observeRatedTracks().first()
        assertEquals(3, rated.size)
        assertEquals("trk_b", rated[0].track.id) // 5 stars
        assertEquals(5, rated[0].rating)
        assertEquals("trk_c", rated[1].track.id) // 4 stars
        assertEquals(4, rated[1].rating)
        assertEquals("trk_a", rated[2].track.id) // 3 stars
        assertEquals(3, rated[2].rating)
    }

    @Test
    fun observeRatedTracks_cleanly_filters_out_missing_tracks() = runBlocking {
        fakeTrackDao.seedTrackWithDetails(TrackWithDetails(track = createTrackEntity("trk_exists", "Existing Track")))

        fakePersonalizationDao.upsertRating(RatingEntity("trk_exists", rating = 5, clientUpdatedAt = 1000L))
        fakePersonalizationDao.upsertRating(RatingEntity("trk_missing", rating = 4, clientUpdatedAt = 1000L))

        val rated = repository.observeRatedTracks().first()
        assertEquals(1, rated.size)
        assertEquals("trk_exists", rated[0].track.id)
    }

    @Test
    fun error_propagation_on_dao_failure() = runBlocking {
        fakePersonalizationDao.shouldThrowOnFavorite = true
        try {
            repository.setFavorite("trk_fail", true)
            fail("Expected exception when DAO fails")
        } catch (e: RuntimeException) {
            assertEquals("Simulated DAO failure on favorite", e.message)
        }

        fakePersonalizationDao.shouldThrowOnRating = true
        try {
            repository.setRating("trk_fail", 4)
            fail("Expected exception when DAO fails")
        } catch (e: RuntimeException) {
            assertEquals("Simulated DAO failure on rating", e.message)
        }
    }
}

/**
 * Fake in-memory implementation of PersonalizationDao for unit tests.
 */
class FakePersonalizationDao : PersonalizationDao {

    private val favoritesMap = mutableMapOf<String, FavoriteEntity>()
    private val ratingsMap = mutableMapOf<String, RatingEntity>()

    private val favoritesFlow = MutableStateFlow<Map<String, FavoriteEntity>>(emptyMap())
    private val ratingsFlow = MutableStateFlow<Map<String, RatingEntity>>(emptyMap())

    var trackDetailsProvider: () -> List<TrackWithDetails> = { emptyList() }
    var shouldThrowOnFavorite: Boolean = false
    var shouldThrowOnRating: Boolean = false

    override fun observeFavorite(trackId: String): Flow<FavoriteEntity?> {
        return favoritesFlow.map { it[trackId] }
    }

    override fun observeAllFavorites(): Flow<List<FavoriteEntity>> {
        return favoritesFlow.map { map ->
            map.values.sortedByDescending { it.clientUpdatedAt }
        }
    }

    override suspend fun isFavorite(trackId: String): Boolean = favoritesMap.containsKey(trackId)

    override fun observeFavoriteTracks(): Flow<List<TrackWithDetails>> {
        return favoritesFlow.map { map ->
            val tracks = trackDetailsProvider().associateBy { it.track.id }
            map.values
                .sortedByDescending { it.clientUpdatedAt }
                .mapNotNull { tracks[it.trackId] }
        }
    }

    override suspend fun getFavorite(trackId: String): FavoriteEntity? = favoritesMap[trackId]

    override suspend fun getFavoriteCount(): Int = favoritesMap.size

    override suspend fun upsertFavorite(favorite: FavoriteEntity) {
        if (shouldThrowOnFavorite) throw RuntimeException("Simulated DAO failure on favorite")
        favoritesMap[favorite.trackId] = favorite
        favoritesFlow.value = favoritesMap.toMap()
    }

    override suspend fun deleteFavorite(trackId: String) {
        if (shouldThrowOnFavorite) throw RuntimeException("Simulated DAO failure on favorite")
        favoritesMap.remove(trackId)
        favoritesFlow.value = favoritesMap.toMap()
    }

    override suspend fun deleteAllFavorites() {
        favoritesMap.clear()
        favoritesFlow.value = emptyMap()
    }

    override fun observeRating(trackId: String): Flow<RatingEntity?> {
        return ratingsFlow.map { it[trackId] }
    }

    override fun observeAllRatings(): Flow<List<RatingEntity>> {
        return ratingsFlow.map { map ->
            map.values.sortedWith(
                compareByDescending<RatingEntity> { it.rating }
                    .thenByDescending { it.clientUpdatedAt }
                    .thenBy { it.trackId }
            )
        }
    }

    override suspend fun getRating(trackId: String): RatingEntity? = ratingsMap[trackId]

    override suspend fun getRatingCount(): Int = ratingsMap.size

    override fun observeRatedTracks(): Flow<List<TrackWithDetails>> {
        return ratingsFlow.map { map ->
            val tracks = trackDetailsProvider().associateBy { it.track.id }
            map.values
                .sortedWith(
                    compareByDescending<RatingEntity> { it.rating }
                        .thenByDescending { it.clientUpdatedAt }
                        .thenBy { it.trackId }
                )
                .mapNotNull { tracks[it.trackId] }
        }
    }

    override suspend fun upsertRating(rating: RatingEntity) {
        if (shouldThrowOnRating) throw RuntimeException("Simulated DAO failure on rating")
        ratingsMap[rating.trackId] = rating
        ratingsFlow.value = ratingsMap.toMap()
    }

    override suspend fun deleteRating(trackId: String) {
        if (shouldThrowOnRating) throw RuntimeException("Simulated DAO failure on rating")
        ratingsMap.remove(trackId)
        ratingsFlow.value = ratingsMap.toMap()
    }

    override suspend fun deleteAllRatings() {
        ratingsMap.clear()
        ratingsFlow.value = emptyMap()
    }
}
