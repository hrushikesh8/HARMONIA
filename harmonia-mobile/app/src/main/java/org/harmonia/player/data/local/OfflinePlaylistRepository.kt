package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.PlaylistDao
import org.harmonia.player.data.local.dao.TrackDao
import org.harmonia.player.data.local.entity.PlaylistEntity
import org.harmonia.player.data.local.entity.PlaylistItemEntity
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.PlaylistItem
import org.harmonia.player.domain.repository.PlaylistRepository
import java.util.UUID

/**
 * Concrete implementation of PlaylistRepository backed by Room SQLite.
 */
class OfflinePlaylistRepository(
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao
) : PlaylistRepository {

    override fun observeAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.observeAllPlaylists().map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        return playlistDao.getPlaylistById(id)?.toDomain()
    }

    override suspend fun createPlaylist(name: String, description: String?): Playlist {
        val now = System.currentTimeMillis()
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description?.trim(),
            createdAt = now,
            updatedAt = now
        )
        playlistDao.insertPlaylist(PlaylistEntity.fromDomain(playlist))
        return playlist
    }

    override suspend fun updatePlaylist(playlist: Playlist) {
        val updated = playlist.copy(updatedAt = System.currentTimeMillis())
        playlistDao.updatePlaylist(PlaylistEntity.fromDomain(updated))
    }

    override suspend fun deletePlaylist(id: String) {
        playlistDao.deletePlaylist(id)
    }

    override fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItem>> {
        return playlistDao.observePlaylistItems(playlistId).map { entities ->
            entities.map { entity ->
                val track = trackDao.getTrackById(entity.trackId)?.toDomainTrack()
                entity.toDomain().copy(track = track)
            }
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String): PlaylistItem {
        val maxPos = playlistDao.getMaxPosition(playlistId)
        val nextPos = if (maxPos != null) maxPos + 1 else 0
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            trackId = trackId,
            position = nextPos,
            addedAt = System.currentTimeMillis()
        )
        playlistDao.insertItem(PlaylistItemEntity.fromDomain(item))
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null) {
            playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
        return item
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.removeItem(playlistId, trackId)
        normalizePositions(playlistId)
    }

    override suspend fun reorderPlaylistItem(playlistId: String, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val items = playlistDao.getItemsForPlaylist(playlistId).toMutableList()
        if (fromPosition !in items.indices || toPosition !in items.indices) return

        val moved = items.removeAt(fromPosition)
        items.add(toPosition, moved)

        // Clear and rewrite with updated 0-based positions to prevent unique constraint conflict
        playlistDao.clearPlaylist(playlistId)
        val updatedEntities = items.mapIndexed { index, entity ->
            entity.copy(position = index)
        }
        playlistDao.insertItems(updatedEntities)

        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null) {
            playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    private suspend fun normalizePositions(playlistId: String) {
        val items = playlistDao.getItemsForPlaylist(playlistId)
        val updatedEntities = items.mapIndexed { index, entity ->
            entity.copy(position = index)
        }
        playlistDao.clearPlaylist(playlistId)
        playlistDao.insertItems(updatedEntities)
    }
}
