package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.LocalFile

/**
 * Pure domain repository interface for physical audio file records on device storage.
 */
interface LocalFileRepository {
    /**
     * Observes local file records for a specific track.
     */
    fun observeLocalFilesForTrack(trackId: String): Flow<List<LocalFile>>

    /**
     * Retrieves a local file record by its file ID.
     */
    suspend fun getLocalFileById(id: String): LocalFile?

    /**
     * Retrieves a local file record by its URI.
     */
    suspend fun getLocalFileByUri(fileUri: String): LocalFile?

    /**
     * Inserts or updates local file presence.
     */
    suspend fun upsertLocalFile(localFile: LocalFile)

    /**
     * Updates verification status for a file.
     */
    suspend fun updateVerificationStatus(id: String, isVerified: Boolean, verifiedAt: Long)

    /**
     * Removes a local file record.
     */
    suspend fun deleteLocalFile(id: String)
}
