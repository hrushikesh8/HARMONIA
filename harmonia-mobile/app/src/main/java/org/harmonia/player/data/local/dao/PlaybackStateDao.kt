package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.PlaybackStateEntity
import org.harmonia.player.data.local.entity.QueueItemEntity

@Dao
interface PlaybackStateDao {

    @Query("SELECT * FROM mobile_playback_state WHERE singleton_id = 1 LIMIT 1")
    fun observePlaybackState(): Flow<PlaybackStateEntity?>

    @Query("SELECT * FROM mobile_playback_state WHERE singleton_id = 1 LIMIT 1")
    suspend fun getPlaybackState(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlaybackState(state: PlaybackStateEntity)

    @Query("SELECT * FROM mobile_queue_items ORDER BY position ASC")
    fun observeQueueItems(): Flow<List<QueueItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<QueueItemEntity>)

    @Query("DELETE FROM mobile_queue_items")
    suspend fun clearQueue()

    @Transaction
    suspend fun replaceQueue(items: List<QueueItemEntity>) {
        clearQueue()
        insertQueueItems(items)
    }
}
