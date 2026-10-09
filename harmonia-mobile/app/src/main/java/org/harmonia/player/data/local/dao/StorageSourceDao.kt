package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.StorageSourceEntity

@Dao
interface StorageSourceDao {

    @Query("SELECT * FROM mobile_storage_sources ORDER BY display_name ASC")
    fun observeAllSources(): Flow<List<StorageSourceEntity>>

    @Query("SELECT * FROM mobile_storage_sources WHERE is_enabled = 1 ORDER BY display_name ASC")
    fun observeEnabledSources(): Flow<List<StorageSourceEntity>>

    @Query("SELECT * FROM mobile_storage_sources WHERE id = :id")
    suspend fun getSourceById(id: String): StorageSourceEntity?

    @Query("SELECT * FROM mobile_storage_sources ORDER BY display_name ASC")
    suspend fun getAllSources(): List<StorageSourceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: StorageSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSources(sources: List<StorageSourceEntity>)

    @Update
    suspend fun updateSource(source: StorageSourceEntity)

    @Query("UPDATE mobile_storage_sources SET is_enabled = :isEnabled WHERE id = :id")
    suspend fun setSourceEnabled(id: String, isEnabled: Boolean)

    @Query("DELETE FROM mobile_storage_sources WHERE id = :id")
    suspend fun deleteSourceById(id: String)
}
