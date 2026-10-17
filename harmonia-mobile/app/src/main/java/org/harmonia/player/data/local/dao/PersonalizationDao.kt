package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.FavoriteEntity
import org.harmonia.player.data.local.entity.RatingEntity
import org.harmonia.player.data.local.entity.TrackWithDetails

/**
 * Room Data Access Object for personal user annotations (Favorites and 1-5 Star Ratings).
 * Provides atomic, idempotent mutations and reactive observation.
 * Separate from canonical track catalog queries per ADR-002 and ADR-021.
 */
@Dao
interface PersonalizationDao {

    // --- Favorites Queries ---

    @Query("SELECT * FROM mobile_favorites WHERE track_id = :trackId LIMIT 1")
    fun observeFavorite(trackId: String): Flow<FavoriteEntity?>

    @Query("SELECT * FROM mobile_favorites ORDER BY client_updated_at DESC, track_id ASC")
    fun observeAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM mobile_favorites WHERE track_id = :trackId LIMIT 1")
    suspend fun getFavorite(trackId: String): FavoriteEntity?

    @Query("SELECT COUNT(*) FROM mobile_favorites WHERE track_id = :trackId")
    suspend fun isFavorite(trackId: String): Boolean

    @Query("SELECT COUNT(*) FROM mobile_favorites")
    suspend fun getFavoriteCount(): Int

    @Transaction
    @Query("""
        SELECT mobile_tracks.* FROM mobile_tracks
        INNER JOIN mobile_favorites ON mobile_tracks.id = mobile_favorites.track_id
        ORDER BY mobile_favorites.client_updated_at DESC, mobile_tracks.title COLLATE NOCASE ASC, mobile_tracks.id ASC
    """)
    fun observeFavoriteTracks(): Flow<List<TrackWithDetails>>

    // --- Favorites Mutations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM mobile_favorites WHERE track_id = :trackId")
    suspend fun deleteFavorite(trackId: String)

    @Query("DELETE FROM mobile_favorites")
    suspend fun deleteAllFavorites()

    /**
     * Idempotent mutation contract for setting favorite status.
     * If isFavorite is true, upserts the favorite row with current timestamp.
     * If isFavorite is false, removes the favorite row.
     */
    @Transaction
    suspend fun setFavorite(trackId: String, isFavorite: Boolean) {
        if (isFavorite) {
            upsertFavorite(FavoriteEntity(trackId = trackId, clientUpdatedAt = System.currentTimeMillis()))
        } else {
            deleteFavorite(trackId)
        }
    }

    /**
     * Atomic toggle operation for favorite status.
     * Safe under concurrent invocations within an SQLite transaction.
     * Returns the resulting favorite status (true if favorited, false if unfavorited).
     */
    @Transaction
    suspend fun toggleFavorite(trackId: String): Boolean {
        val existing = getFavorite(trackId)
        return if (existing != null) {
            deleteFavorite(trackId)
            false
        } else {
            upsertFavorite(FavoriteEntity(trackId = trackId, clientUpdatedAt = System.currentTimeMillis()))
            true
        }
    }

    // --- Ratings Queries ---

    @Query("SELECT * FROM mobile_ratings WHERE track_id = :trackId LIMIT 1")
    fun observeRating(trackId: String): Flow<RatingEntity?>

    @Query("SELECT * FROM mobile_ratings ORDER BY rating DESC, client_updated_at DESC, track_id ASC")
    fun observeAllRatings(): Flow<List<RatingEntity>>

    @Query("SELECT * FROM mobile_ratings WHERE track_id = :trackId LIMIT 1")
    suspend fun getRating(trackId: String): RatingEntity?

    @Query("SELECT COUNT(*) FROM mobile_ratings")
    suspend fun getRatingCount(): Int

    @Transaction
    @Query("""
        SELECT mobile_tracks.* FROM mobile_tracks
        INNER JOIN mobile_ratings ON mobile_tracks.id = mobile_ratings.track_id
        ORDER BY mobile_ratings.rating DESC, mobile_ratings.client_updated_at DESC, mobile_tracks.title COLLATE NOCASE ASC, mobile_tracks.id ASC
    """)
    fun observeRatedTracks(): Flow<List<TrackWithDetails>>

    // --- Ratings Mutations ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRating(rating: RatingEntity)

    @Query("DELETE FROM mobile_ratings WHERE track_id = :trackId")
    suspend fun deleteRating(trackId: String)

    @Query("DELETE FROM mobile_ratings")
    suspend fun deleteAllRatings()

    /**
     * Idempotent mutation contract for setting or clearing a track rating.
     * If rating is non-null, validates 1..5 range and upserts the rating.
     * If rating is null, clears the rating by deleting the row.
     */
    @Transaction
    suspend fun setRating(trackId: String, rating: Int?) {
        if (rating != null) {
            require(rating in 1..5) { "Rating must be between 1 and 5 stars inclusive, got: $rating" }
            upsertRating(RatingEntity(trackId = trackId, rating = rating, clientUpdatedAt = System.currentTimeMillis()))
        } else {
            deleteRating(trackId)
        }
    }
}
