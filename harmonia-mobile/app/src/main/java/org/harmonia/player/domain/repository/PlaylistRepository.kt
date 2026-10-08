package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.PlaylistItem

/**
 * Pure domain repository interface for local playlist management.
 * Strictly decoupled from Android, Room, and Media3 frameworks.
 */
interface PlaylistRepository {
    fun observeAllPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: String): Playlist?
    suspend fun createPlaylist(name: String, description: String? = null): Playlist
    suspend fun renamePlaylist(id: String, newName: String)
    suspend fun updatePlaylist(playlist: Playlist)
    suspend fun deletePlaylist(id: String)

    fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItem>>
    suspend fun addTrackToPlaylist(playlistId: String, trackId: String): PlaylistItem
    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>): List<PlaylistItem>
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
    suspend fun reorderPlaylistItem(playlistId: String, fromPosition: Int, toPosition: Int)
}
