package org.harmonia.player.presentation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.PlaylistItem
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.PlaylistRepository
import org.harmonia.player.presentation.playlists.PlaylistDetailViewModel
import org.harmonia.player.presentation.playlists.PlaylistsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.UUID

class FakePlaylistRepository : PlaylistRepository {
    private val playlists = mutableListOf<Playlist>()
    val playlistsFlow = MutableStateFlow<List<Playlist>>(emptyList())
    private val items = mutableListOf<PlaylistItem>()
    val itemsFlow = MutableStateFlow<List<PlaylistItem>>(emptyList())

    override fun observeAllPlaylists(): Flow<List<Playlist>> = playlistsFlow

    override suspend fun getPlaylistById(id: String): Playlist? = playlists.find { it.id == id }

    override suspend fun createPlaylist(name: String, description: String?): Playlist {
        require(name.isNotBlank()) { "Playlist name cannot be blank" }
        val pl = Playlist(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            description = description?.trim(),
            trackCount = 0
        )
        playlists.add(pl)
        playlistsFlow.value = playlists.toList()
        return pl
    }

    override suspend fun renamePlaylist(id: String, newName: String) {
        val trimmed = newName.trim()
        require(trimmed.isNotBlank()) { "Playlist name cannot be blank" }
        val idx = playlists.indexOfFirst { it.id == id }
        if (idx >= 0) {
            playlists[idx] = playlists[idx].copy(name = trimmed, updatedAt = System.currentTimeMillis())
            playlistsFlow.value = playlists.toList()
        }
    }

    override suspend fun updatePlaylist(playlist: Playlist) {
        val idx = playlists.indexOfFirst { it.id == playlist.id }
        if (idx >= 0) {
            playlists[idx] = playlist
            playlistsFlow.value = playlists.toList()
        }
    }

    override suspend fun deletePlaylist(id: String) {
        playlists.removeAll { it.id == id }
        items.removeAll { it.playlistId == id }
        playlistsFlow.value = playlists.toList()
        itemsFlow.value = items.toList()
    }

    override fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItem>> {
        return itemsFlow.map { list ->
            list.filter { it.playlistId == playlistId }.sortedBy { it.position }
        }
    }

    override suspend fun addTrackToPlaylist(playlistId: String, trackId: String): PlaylistItem {
        val nextPos = items.filter { it.playlistId == playlistId }.maxOfOrNull { it.position }?.plus(1) ?: 0
        val item = PlaylistItem(
            id = UUID.randomUUID().toString(),
            playlistId = playlistId,
            trackId = trackId,
            position = nextPos
        )
        items.add(item)
        itemsFlow.value = items.toList()
        return item
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>): List<PlaylistItem> {
        var nextPos = items.filter { it.playlistId == playlistId }.maxOfOrNull { it.position }?.plus(1) ?: 0
        val created = trackIds.map { id ->
            PlaylistItem(
                id = UUID.randomUUID().toString(),
                playlistId = playlistId,
                trackId = id,
                position = nextPos++
            )
        }
        items.addAll(created)
        itemsFlow.value = items.toList()
        return created
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        items.removeAll { it.playlistId == playlistId && it.trackId == trackId }
        val remaining = items.filter { it.playlistId == playlistId }.sortedBy { it.position }
        items.removeAll { it.playlistId == playlistId }
        remaining.forEachIndexed { idx, itm ->
            items.add(itm.copy(position = idx))
        }
        itemsFlow.value = items.toList()
    }

    override suspend fun reorderPlaylistItem(playlistId: String, fromPosition: Int, toPosition: Int) {
        val current = items.filter { it.playlistId == playlistId }.sortedBy { it.position }.toMutableList()
        if (fromPosition !in current.indices || toPosition !in current.indices) return
        val moved = current.removeAt(fromPosition)
        current.add(toPosition, moved)
        items.removeAll { it.playlistId == playlistId }
        current.forEachIndexed { idx, itm ->
            items.add(itm.copy(position = idx))
        }
        itemsFlow.value = items.toList()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var playlistRepo: FakePlaylistRepository
    private lateinit var trackRepo: FakeTrackRepository

    private val track1 = Track(id = "trk-1", title = "Track One", artistName = "Artist A", durationSec = 180.0)
    private val track2 = Track(id = "trk-2", title = "Track Two", artistName = "Artist B", durationSec = 210.0)
    private val track3 = Track(id = "trk-3", title = "Track Three", artistName = "Artist C", durationSec = 240.0)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        playlistRepo = FakePlaylistRepository()
        trackRepo = FakeTrackRepository()
        trackRepo.tracks[track1.id] = track1
        trackRepo.tracks[track2.id] = track2
        trackRepo.tracks[track3.id] = track3
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `PlaylistsViewModel creates and renames playlists`() = runTest(testDispatcher) {
        val viewModel = PlaylistsViewModel(playlistRepo)

        viewModel.createPlaylist("Late Night Drive", "Synthwave vibe")
        advanceUntilIdle()

        val all = playlistRepo.observeAllPlaylists().first()
        assertEquals(1, all.size)
        assertEquals("Late Night Drive", all[0].name)

        viewModel.renamePlaylist(all[0].id, "Nightcall")
        advanceUntilIdle()

        val renamed = playlistRepo.observeAllPlaylists().first()
        assertEquals("Nightcall", renamed[0].name)
    }

    @Test
    fun `PlaylistsViewModel deletes playlist`() = runTest(testDispatcher) {
        val viewModel = PlaylistsViewModel(playlistRepo)
        val created = playlistRepo.createPlaylist("To Delete", null)
        advanceUntilIdle()

        viewModel.deletePlaylist(created.id)
        advanceUntilIdle()

        val all = playlistRepo.observeAllPlaylists().first()
        assertTrue(all.isEmpty())
    }

    @Test
    fun `PlaylistDetailViewModel loads playlist and ordered tracks`() = runTest(testDispatcher) {
        val playlist = playlistRepo.createPlaylist("Roadtrip", null)
        playlistRepo.addTracksToPlaylist(playlist.id, listOf(track1.id, track2.id))

        val detailViewModel = PlaylistDetailViewModel(playlist.id, playlistRepo, trackRepo)
        advanceUntilIdle()

        val state = detailViewModel.uiState.value
        assertEquals("Roadtrip", state.playlist?.name)
        assertEquals(2, state.items.size)
        assertEquals(track1.id, state.items[0].trackId)
        assertEquals(track2.id, state.items[1].trackId)
    }

    @Test
    fun `PlaylistDetailViewModel moveTrackDown and moveTrackUp reorders deterministically`() = runTest(testDispatcher) {
        val playlist = playlistRepo.createPlaylist("Reorder Test", null)
        playlistRepo.addTracksToPlaylist(playlist.id, listOf(track1.id, track2.id, track3.id))

        val detailViewModel = PlaylistDetailViewModel(playlist.id, playlistRepo, trackRepo)
        advanceUntilIdle()

        // Move track 0 down to position 1
        detailViewModel.moveTrackDown(0)
        advanceUntilIdle()

        var items = detailViewModel.uiState.value.items
        assertEquals(track2.id, items[0].trackId)
        assertEquals(track1.id, items[1].trackId)
        assertEquals(track3.id, items[2].trackId)

        // Move track 1 up to position 0
        detailViewModel.moveTrackUp(1)
        advanceUntilIdle()

        items = detailViewModel.uiState.value.items
        assertEquals(track1.id, items[0].trackId)
        assertEquals(track2.id, items[1].trackId)
        assertEquals(track3.id, items[2].trackId)
    }

    @Test
    fun `PlaylistDetailViewModel removeTrack removes item and updates state`() = runTest(testDispatcher) {
        val playlist = playlistRepo.createPlaylist("Remove Test", null)
        playlistRepo.addTracksToPlaylist(playlist.id, listOf(track1.id, track2.id))

        val detailViewModel = PlaylistDetailViewModel(playlist.id, playlistRepo, trackRepo)
        advanceUntilIdle()

        detailViewModel.removeTrack(track1.id)
        advanceUntilIdle()

        val items = detailViewModel.uiState.value.items
        assertEquals(1, items.size)
        assertEquals(track2.id, items[0].trackId)
        assertEquals(0, items[0].position)
    }

    @Test
    fun `PlaylistDetailViewModel deletePlaylist calls onDeleted callback`() = runTest(testDispatcher) {
        val playlist = playlistRepo.createPlaylist("Callback Test", null)
        val detailViewModel = PlaylistDetailViewModel(playlist.id, playlistRepo, trackRepo)
        advanceUntilIdle()

        var deletedCalled = false
        detailViewModel.deletePlaylist(onDeleted = { deletedCalled = true })
        advanceUntilIdle()

        assertTrue(deletedCalled)
        assertNull(playlistRepo.getPlaylistById(playlist.id))
    }
}
