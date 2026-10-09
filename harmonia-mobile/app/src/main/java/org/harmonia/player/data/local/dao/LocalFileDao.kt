package org.harmonia.player.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import org.harmonia.player.data.local.entity.LocalFileEntity

@Dao
interface LocalFileDao {

    @Query("SELECT * FROM mobile_local_files WHERE track_id = :trackId")
    fun observeLocalFilesForTrack(trackId: String): Flow<List<LocalFileEntity>>

    @Query("SELECT * FROM mobile_local_files WHERE id = :id LIMIT 1")
    suspend fun getLocalFileById(id: String): LocalFileEntity?

    @Query("SELECT * FROM mobile_local_files WHERE file_uri = :fileUri LIMIT 1")
    suspend fun getLocalFileByUri(fileUri: String): LocalFileEntity?

    @Query("SELECT * FROM mobile_local_files WHERE file_hash = :fileHash LIMIT 1")
    suspend fun getLocalFileByHash(fileHash: String): LocalFileEntity?

    @Query("SELECT * FROM mobile_local_files WHERE storage_type = :storageType")
    suspend fun getLocalFilesByStorageType(storageType: String): List<LocalFileEntity>

    @Query("SELECT * FROM mobile_local_files")
    suspend fun getAllLocalFiles(): List<LocalFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalFile(localFile: LocalFileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLocalFiles(localFiles: List<LocalFileEntity>)

    @Query("UPDATE mobile_local_files SET availability_status = :status, verified_at = :verifiedAt WHERE id = :id")
    suspend fun updateAvailabilityStatus(id: String, status: String, verifiedAt: Long)

    @Query("DELETE FROM mobile_local_files WHERE id = :id")
    suspend fun deleteLocalFileById(id: String)

    @Query("DELETE FROM mobile_local_files WHERE track_id = :trackId")
    suspend fun deleteLocalFilesByTrackId(trackId: String)

    @Query("SELECT COUNT(*) FROM mobile_local_files WHERE availability_status = 'AVAILABLE'")
    suspend fun getAvailableFileCount(): Int

    @Query("SELECT * FROM mobile_local_files WHERE source_id = :sourceId")
    suspend fun getLocalFilesBySourceId(sourceId: String): List<LocalFileEntity>

    @Query("SELECT * FROM mobile_local_files WHERE source_id = :sourceId")
    fun observeLocalFilesForSource(sourceId: String): Flow<List<LocalFileEntity>>

    @Query("SELECT * FROM mobile_local_files WHERE source_id IS NULL")
    suspend fun getLocalFilesWithUnresolvedSource(): List<LocalFileEntity>

    @Query("UPDATE mobile_local_files SET source_id = :sourceId WHERE id = :id")
    suspend fun updateSourceId(id: String, sourceId: String)
}
