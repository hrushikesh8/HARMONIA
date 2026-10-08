package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.AlbumEntity

@Dao
interface AlbumDao {
    @Query("SELECT * FROM mobile_albums ORDER BY title COLLATE NOCASE ASC")
    fun observeAllAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM mobile_albums WHERE id = :id LIMIT 1")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Query("SELECT * FROM mobile_albums WHERE title = :title AND (artist_id = :artistId OR (:artistId IS NULL AND artist_id IS NULL)) LIMIT 1")
    suspend fun getAlbumByTitleAndArtist(title: String, artistId: String?): AlbumEntity?

    @Query("SELECT * FROM mobile_albums WHERE artist_id = :artistId ORDER BY year DESC, title ASC")
    fun observeAlbumsByArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("""
        SELECT * FROM mobile_albums 
        WHERE title LIKE '%' || :query || '%' 
           OR artist_id IN (SELECT id FROM mobile_artists WHERE name LIKE '%' || :query || '%')
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchAlbums(query: String): Flow<List<AlbumEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbums(albums: List<AlbumEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlbum(album: AlbumEntity)

    @Query("DELETE FROM mobile_albums WHERE id = :id")
    suspend fun deleteAlbumById(id: String)
}
