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
        return playlistDao.observePlaylistsWithCount().map { list ->
            list.map { it.playlist.toDomain(trackCount = it.trackCount) }
        }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        val entity = playlistDao.getPlaylistById(id) ?: return null
        val count = playlistDao.getItemCount(id)
        return entity.toDomain(trackCount = count)
    }

    override suspend fun createPlaylist(name: String, description: String?): Playlist {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "Playlist name cannot be blank" }
        val now = System.currentTimeMillis()
        val playlist = Playlist(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            description = description?.trim()?.ifBlank { null },
            createdAt = now,
            updatedAt = now,
            trackCount = 0
        )
        playlistDao.insertPlaylist(PlaylistEntity.fromDomain(playlist))
        return playlist
    }

    override suspend fun renamePlaylist(id: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "Playlist name cannot be blank" }
        val existing = playlistDao.getPlaylistById(id) ?: return
        val updated = existing.copy(name = trimmed, updatedAt = System.currentTimeMillis())
        playlistDao.updatePlaylist(updated)
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
        val now = System.currentTimeMillis()
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            trackId = trackId,
            position = nextPos,
            addedAt = now
        )
        playlistDao.insertItem(PlaylistItemEntity.fromDomain(item))
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null) {
            playlistDao.updatePlaylist(playlist.copy(updatedAt = now))
        }
        return item
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>): List<PlaylistItem> {
        if (trackIds.isEmpty()) return emptyList()
        val maxPos = playlistDao.getMaxPosition(playlistId)
        var nextPos = if (maxPos != null) maxPos + 1 else 0
        val now = System.currentTimeMillis()
        val items = trackIds.map { trackId ->
            PlaylistItem(
                id = UUID.randomUUID().toString(),
                playlistId = playlistId,
                trackId = trackId,
                position = nextPos++,
                addedAt = now
            )
        }
        playlistDao.insertItems(items.map { PlaylistItemEntity.fromDomain(it) })
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null) {
            playlistDao.updatePlaylist(playlist.copy(updatedAt = now))
        }
        return items
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        playlistDao.removeItem(playlistId, trackId)
        normalizePositions(playlistId)
        val playlist = playlistDao.getPlaylistById(playlistId)
        if (playlist != null) {
            playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun reorderPlaylistItem(playlistId: String, fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) return
        val items = playlistDao.getItemsForPlaylist(playlistId).toMutableList()
        if (fromPosition !in items.indices || toPosition !in items.indices) return

        val moved = items.removeAt(fromPosition)
        items.add(toPosition, moved)

        val updatedEntities = items.mapIndexed { index, entity ->
            entity.copy(position = index)
        }
        playlistDao.replacePlaylistItems(playlistId, updatedEntities)

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
        playlistDao.replacePlaylistItems(playlistId, updatedEntities)
    }

    override suspend fun getPlaylistsPaged(limit: Int, offset: Int): List<Playlist> {
        return playlistDao.getPlaylistsWithCountPaged(limit, offset).map {
            it.playlist.toDomain(trackCount = it.trackCount)
        }
    }

    override suspend fun getPlaylistItemsPaged(playlistId: String, limit: Int, offset: Int): List<PlaylistItem> {
        return playlistDao.getPlaylistItemsPaged(playlistId, limit, offset).map { entity ->
            val track = trackDao.getTrackById(entity.trackId)?.toDomainTrack()
            entity.toDomain().copy(track = track)
        }
    }
}
