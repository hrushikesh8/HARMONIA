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

    /**
     * Retrieves a paginated slice of tracks sorted deterministically by title, id.
     */
    suspend fun getTracksPaged(limit: Int, offset: Int): List<Track> = emptyList()

    /**
     * Retrieves tracks for an album paginated and sorted by disc, track number, title, id.
     */
    suspend fun getTracksByAlbumPaged(albumId: String, limit: Int, offset: Int): List<Track> = emptyList()

    /**
     * Retrieves all tracks for an album sorted deterministically for container playback.
     */
    suspend fun getTracksByAlbum(albumId: String): List<Track> = emptyList()

    /**
     * Retrieves tracks for an artist paginated and sorted by album, disc, track number, title, id.
     */
    suspend fun getTracksByArtistPaged(artistId: String, limit: Int, offset: Int): List<Track> = emptyList()

    /**
     * Retrieves all tracks for an artist sorted deterministically for container playback.
     */
    suspend fun getTracksByArtist(artistId: String): List<Track> = emptyList()
}
