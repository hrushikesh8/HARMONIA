package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.ListeningEventEntity

@Dao
interface ListeningEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: ListeningEventEntity)

    @Query("SELECT * FROM mobile_listening_events ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ListeningEventEntity>>

    @Query("SELECT COUNT(*) FROM mobile_listening_events")
    suspend fun count(): Int

    @Query("DELETE FROM mobile_listening_events WHERE is_synced = 1 AND timestamp < :cutoffTimestamp")
    suspend fun pruneOldSyncedEvents(cutoffTimestamp: Long): Int
}
