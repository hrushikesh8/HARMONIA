package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.ScanCheckpointEntity

@Dao
interface ScanCheckpointDao {

    @Query("SELECT * FROM mobile_scan_checkpoints WHERE source_id = :sourceId")
    fun observeCheckpoint(sourceId: String): Flow<ScanCheckpointEntity?>

    @Query("SELECT * FROM mobile_scan_checkpoints WHERE source_id = :sourceId")
    suspend fun getCheckpoint(sourceId: String): ScanCheckpointEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCheckpoint(checkpoint: ScanCheckpointEntity)

    @Query("DELETE FROM mobile_scan_checkpoints WHERE source_id = :sourceId")
    suspend fun deleteCheckpoint(sourceId: String)
}
