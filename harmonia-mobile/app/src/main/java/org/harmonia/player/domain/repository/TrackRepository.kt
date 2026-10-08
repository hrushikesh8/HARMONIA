package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.Track

/**
 * Pure domain repository interface for track metadata and library catalog queries.
 */
interface TrackRepository {
    /**
     * Observes all tracks currently cataloged in the library.
     */
    fun observeAllTracks(): Flow<List<Track>>

    /**
     * Observes only tracks that are verified and available locally on disk for offline playback.
     */
    fun observeOfflineTracks(): Flow<List<Track>>

    /**
     * Fetches a track by its canonical UUID.
     */
    suspend fun getTrackById(id: String): Track?

    /**
     * Searches tracks by query text across title, artist name, and album title.
     */
    fun searchTracks(query: String): Flow<List<Track>>

    /**
     * Inserts or updates tracks in the local database.
     */
    suspend fun upsertTracks(tracks: List<Track>)

    /**
     * Deletes a track by ID.
     */
    suspend fun deleteTrack(id: String)
}
