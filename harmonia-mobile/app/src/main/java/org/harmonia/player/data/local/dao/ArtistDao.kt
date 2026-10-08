package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.ArtistEntity

@Dao
interface ArtistDao {
    @Query("SELECT * FROM mobile_artists ORDER BY sort_name COLLATE NOCASE ASC")
    fun observeAllArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM mobile_artists WHERE id = :id LIMIT 1")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Query("SELECT * FROM mobile_artists WHERE name = :name LIMIT 1")
    suspend fun getArtistByName(name: String): ArtistEntity?

    @Query("""
        SELECT * FROM mobile_artists 
        WHERE name LIKE '%' || :query || '%' 
           OR (sort_name IS NOT NULL AND sort_name LIKE '%' || :query || '%')
        ORDER BY sort_name COLLATE NOCASE ASC
    """)
    fun searchArtists(query: String): Flow<List<ArtistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtists(artists: List<ArtistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtist(artist: ArtistEntity)

    @Query("DELETE FROM mobile_artists WHERE id = :id")
    suspend fun deleteArtistById(id: String)
}
