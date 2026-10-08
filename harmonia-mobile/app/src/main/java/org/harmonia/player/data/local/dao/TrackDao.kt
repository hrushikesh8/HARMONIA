package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.data.local.entity.TrackWithDetails

@Dao
interface TrackDao {

    @Transaction
    @Query("SELECT * FROM mobile_tracks ORDER BY title COLLATE NOCASE ASC")
    fun observeAllTracks(): Flow<List<TrackWithDetails>>

    @Transaction
    @Query("""
        SELECT * FROM mobile_tracks 
        WHERE id IN (
            SELECT track_id FROM mobile_local_files 
            WHERE availability_status = 'AVAILABLE'
        )
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun observeOfflineTracks(): Flow<List<TrackWithDetails>>

    @Transaction
    @Query("SELECT * FROM mobile_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: String): TrackWithDetails?

    @Transaction
    @Query("""
        SELECT * FROM mobile_tracks 
        WHERE title LIKE '%' || :query || '%'
           OR artist_id IN (SELECT id FROM mobile_artists WHERE name LIKE '%' || :query || '%')
           OR album_id IN (SELECT id FROM mobile_albums WHERE title LIKE '%' || :query || '%')
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchTracks(query: String): Flow<List<TrackWithDetails>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrack(track: TrackEntity)

    @Query("DELETE FROM mobile_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: String)

    @Query("DELETE FROM mobile_tracks")
    suspend fun deleteAllTracks()

    @Query("SELECT COUNT(*) FROM mobile_tracks")
    suspend fun getTrackCount(): Int
}
