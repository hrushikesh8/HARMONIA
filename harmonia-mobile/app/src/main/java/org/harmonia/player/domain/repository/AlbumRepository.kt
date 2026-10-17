package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.Album

/**
 * Pure domain repository interface for album browsing.
 */
interface AlbumRepository {
    fun observeAllAlbums(): Flow<List<Album>>
    suspend fun getAlbumById(id: String): Album?
    fun observeAlbumsByArtist(artistId: String): Flow<List<Album>>
    suspend fun upsertAlbums(albums: List<Album>)
    suspend fun getAlbumsPaged(limit: Int, offset: Int): List<Album> = emptyList()
}
