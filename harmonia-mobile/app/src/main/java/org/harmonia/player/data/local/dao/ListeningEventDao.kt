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

    @Query(
        """
        SELECT track_id AS trackId,
               COUNT(*) AS totalPlays,
               SUM(CASE WHEN timestamp >= :recentSince THEN 1 ELSE 0 END) AS recentPlays,
               MAX(timestamp) AS lastPlayedAt
        FROM mobile_listening_events
        WHERE event_type = 'STARTED'
        GROUP BY track_id
        """
    )
    fun observeTrackPlayStats(recentSince: Long): Flow<List<TrackPlayStatsRow>>

    @Query("SELECT COUNT(*) FROM mobile_listening_events")
    suspend fun count(): Int

    @Query("DELETE FROM mobile_listening_events WHERE is_synced = 1 AND timestamp < :cutoffTimestamp")
    suspend fun pruneOldSyncedEvents(cutoffTimestamp: Long): Int
}

/** Row shape for [ListeningEventDao.observeTrackPlayStats]. */
data class TrackPlayStatsRow(
    val trackId: String,
    val totalPlays: Int,
    val recentPlays: Int,
    val lastPlayedAt: Long
)
