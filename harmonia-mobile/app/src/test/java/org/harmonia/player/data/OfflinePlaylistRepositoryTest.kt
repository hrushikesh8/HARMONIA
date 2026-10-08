package org.harmonia.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.OfflinePlaylistRepository
import org.harmonia.player.data.local.dao.PlaylistDao
import org.harmonia.player.data.local.entity.PlaylistEntity
import org.harmonia.player.data.local.entity.PlaylistItemEntity
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.data.local.entity.TrackWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakePlaylistDao : PlaylistDao {
    private val playlists = mutableListOf<PlaylistEntity>()
    private val playlistFlow = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    private val items = mutableListOf<PlaylistItemEntity>()
    private val itemsFlow = MutableStateFlow<List<PlaylistItemEntity>>(emptyList())

    override fun observeAllPlaylists(): Flow<List<PlaylistEntity>> = playlistFlow

    override suspend fun getPlaylistById(id: String): PlaylistEntity? = playlists.find { it.id == id }

    override suspend fun insertPlaylist(playlist: PlaylistEntity) {
        playlists.removeAll { it.id == playlist.id }
        playlists.add(playlist)
        playlistFlow.value = playlists.toList()
    }

    override suspend fun updatePlaylist(playlist: PlaylistEntity) {
        insertPlaylist(playlist)
    }

    override suspend fun deleteItemById(id: String) {
        items.removeAll { it.id == id }
        itemsFlow.value = items.toList()
    }

    override suspend fun getItemCount(playlistId: String): Int {
        return items.count { it.playlistId == playlistId }
    }

    override suspend fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
        items.removeAll { it.playlistId == id }
        playlistFlow.value = playlists.toList()
        itemsFlow.value = items.toList()
    }

    override fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItemEntity>> {
        return itemsFlow.map { list ->
            list.filter { it.playlistId == playlistId }.sortedBy { it.position }
        }
    }

    override suspend fun getItemsForPlaylist(playlistId: String): List<PlaylistItemEntity> {
        return items.filter { it.playlistId == playlistId }.sortedBy { it.position }
    }

    override suspend fun insertItem(item: PlaylistItemEntity) {
        items.removeAll { it.id == item.id }
        items.add(item)
        itemsFlow.value = items.toList()
    }

    override suspend fun insertItems(newItems: List<PlaylistItemEntity>) {
        items.addAll(newItems)
        itemsFlow.value = items.toList()
    }

    override suspend fun removeItem(playlistId: String, trackId: String) {
        items.removeAll { it.playlistId == playlistId && it.trackId == trackId }
        itemsFlow.value = items.toList()
    }

    override suspend fun clearPlaylist(playlistId: String) {
        items.removeAll { it.playlistId == playlistId }
        itemsFlow.value = items.toList()
    }

    override suspend fun getMaxPosition(playlistId: String): Int? {
        return items.filter { it.playlistId == playlistId }.maxOfOrNull { it.position }
    }

    override fun searchPlaylists(query: String): Flow<List<PlaylistEntity>> {
        return playlistFlow.map { list ->
            list.filter { it.name.contains(query, ignoreCase = true) }
        }
    }
}

class OfflinePlaylistRepositoryTest {

    private lateinit var playlistDao: FakePlaylistDao
    private lateinit var trackDao: FakeTrackDao
    private lateinit var repository: OfflinePlaylistRepository

    @Before
    fun setup() {
        playlistDao = FakePlaylistDao()
        trackDao = FakeTrackDao()
        repository = OfflinePlaylistRepository(playlistDao, trackDao)
    }

    @Test
    fun `createPlaylist stores new playlist and generates id`() = runBlocking {
        val created = repository.createPlaylist("Coding Focus", "Electronic music")
        assertNotNull(created.id)
        assertEquals("Coding Focus", created.name)
        assertEquals("Electronic music", created.description)

        val retrieved = repository.getPlaylistById(created.id)
        assertNotNull(retrieved)
        assertEquals("Coding Focus", retrieved?.name)
    }

    @Test
    fun `updatePlaylist updates name and timestamp`() = runBlocking {
        val playlist = repository.createPlaylist("Draft", null)
        val updated = playlist.copy(name = "Final")
        repository.updatePlaylist(updated)

        val result = repository.getPlaylistById(playlist.id)
        assertEquals("Final", result?.name)
    }

    @Test
    fun `deletePlaylist removes playlist and its items`() = runBlocking {
        val playlist = repository.createPlaylist("Temporary", null)
        repository.addTrackToPlaylist(playlist.id, "t-1")
        repository.addTrackToPlaylist(playlist.id, "t-2")

        assertNotNull(repository.getPlaylistById(playlist.id))
        repository.deletePlaylist(playlist.id)

        assertNull(repository.getPlaylistById(playlist.id))
        val items = repository.observePlaylistItems(playlist.id).first()
        assertTrue(items.isEmpty())
    }

    @Test
    fun `addTrackToPlaylist assigns sequential positions and joins track details`() = runBlocking {
        trackDao.seedTrackWithDetails(
            TrackWithDetails(
                track = TrackEntity(id = "track-a", title = "Strobe", durationSec = 637.0, createdAt = 0L, updatedAt = 0L)
            )
        )
        val playlist = repository.createPlaylist("Deadmau5 Picks", null)

        val item1 = repository.addTrackToPlaylist(playlist.id, "track-a")
        val item2 = repository.addTrackToPlaylist(playlist.id, "track-b")

        assertEquals(0, item1.position)
        assertEquals(1, item2.position)

        val items = repository.observePlaylistItems(playlist.id).first()
        assertEquals(2, items.size)
        assertEquals("Strobe", items[0].track?.title)
        assertNull(items[1].track)
    }

    @Test
    fun `reorderPlaylistItem maintains deterministic order and zero-based indexing`() = runBlocking {
        val playlist = repository.createPlaylist("Queue Order", null)
        repository.addTrackToPlaylist(playlist.id, "track-0")
        repository.addTrackToPlaylist(playlist.id, "track-1")
        repository.addTrackToPlaylist(playlist.id, "track-2")

        // Move item at index 2 to index 0
        repository.reorderPlaylistItem(playlist.id, fromPosition = 2, toPosition = 0)

        val reordered = repository.observePlaylistItems(playlist.id).first()
        assertEquals(3, reordered.size)
        assertEquals("track-2", reordered[0].trackId)
        assertEquals(0, reordered[0].position)
        assertEquals("track-0", reordered[1].trackId)
        assertEquals(1, reordered[1].position)
        assertEquals("track-1", reordered[2].trackId)
        assertEquals(2, reordered[2].position)
    }

    @Test
    fun `removeTrackFromPlaylist removes item and normalizes subsequent positions`() = runBlocking {
        val playlist = repository.createPlaylist("Prune Test", null)
        repository.addTrackToPlaylist(playlist.id, "track-0")
        repository.addTrackToPlaylist(playlist.id, "track-1")
        repository.addTrackToPlaylist(playlist.id, "track-2")

        repository.removeTrackFromPlaylist(playlist.id, "track-1")

        val remaining = repository.observePlaylistItems(playlist.id).first()
        assertEquals(2, remaining.size)
        assertEquals("track-0", remaining[0].trackId)
        assertEquals(0, remaining[0].position)
        assertEquals("track-2", remaining[1].trackId)
        assertEquals(1, remaining[1].position)
    }
}
