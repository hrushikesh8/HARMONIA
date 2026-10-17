package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.Artist

/**
 * Pure domain repository interface for artist catalog queries.
 */
interface ArtistRepository {
    fun observeAllArtists(): Flow<List<Artist>>
    suspend fun getArtistById(id: String): Artist?
    suspend fun upsertArtists(artists: List<Artist>)
    suspend fun getArtistsPaged(limit: Int, offset: Int): List<Artist> = emptyList()
}
