package org.harmonia.player.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.PlaylistEntity
import org.harmonia.player.data.local.entity.PlaylistItemEntity

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    @ColumnInfo(name = "track_count") val trackCount: Int
)

@Dao
interface PlaylistDao {

    @Query("SELECT * FROM mobile_playlists ORDER BY updated_at DESC")
    fun observeAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("""
        SELECT p.*, COUNT(i.id) AS track_count
        FROM mobile_playlists p
        LEFT JOIN mobile_playlist_items i ON p.id = i.playlist_id
        GROUP BY p.id
        ORDER BY p.updated_at DESC
    """)
    fun observePlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Query("SELECT * FROM mobile_playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("""
        SELECT * FROM mobile_playlists 
        WHERE name LIKE '%' || :query || '%' 
           OR (description IS NOT NULL AND description LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE ASC
    """)
    fun searchPlaylists(query: String): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM mobile_playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("DELETE FROM mobile_playlists")
    suspend fun deleteAllPlaylists()

    @Query("SELECT * FROM mobile_playlist_items WHERE playlist_id = :playlistId ORDER BY position ASC")
    fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM mobile_playlist_items WHERE playlist_id = :playlistId ORDER BY position ASC")
    suspend fun getItemsForPlaylist(playlistId: String): List<PlaylistItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItem(item: PlaylistItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM mobile_playlist_items WHERE playlist_id = :playlistId AND track_id = :trackId")
    suspend fun removeItem(playlistId: String, trackId: String)

    @Query("DELETE FROM mobile_playlist_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM mobile_playlist_items WHERE playlist_id = :playlistId")
    suspend fun clearPlaylist(playlistId: String)

    @Query("SELECT COUNT(*) FROM mobile_playlist_items WHERE playlist_id = :playlistId")
    suspend fun getItemCount(playlistId: String): Int

    @Query("SELECT MAX(position) FROM mobile_playlist_items WHERE playlist_id = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Int?

    @Transaction
    suspend fun replacePlaylistItems(playlistId: String, items: List<PlaylistItemEntity>) {
        clearPlaylist(playlistId)
        insertItems(items)
    }
}
