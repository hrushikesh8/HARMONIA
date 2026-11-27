package org.harmonia.player.presentation.album

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumPageTest {

    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private fun track(id: String, disc: Int, number: Int?, album: String = "al") =
        Track(id = id, title = id, albumId = album, discNumber = disc, trackNumber = number, durationSec = 60.0)

    private class LiveAlbums(val albums: MutableStateFlow<List<Album>>) : AlbumRepository {
        override suspend fun getAlbumById(id: String) = albums.value.find { it.id == id }
        override fun observeAllAlbums(): Flow<List<Album>> = albums
        override fun observeAlbumsByArtist(artistId: String): Flow<List<Album>> = albums
        override suspend fun upsertAlbums(albums: List<Album>) {}
    }

    private class LiveTracks(val tracks: Flow<List<Track>>) : TrackRepository {
        override fun observeAllTracks(): Flow<List<Track>> = tracks
        override fun observeOfflineTracks(): Flow<List<Track>> = tracks
        override suspend fun getTrackById(id: String): Track? = null
        override fun searchTracks(query: String): Flow<List<Track>> = tracks
        override suspend fun upsertTracks(tracks: List<Track>) {}
        override suspend fun deleteTrack(id: String) {}
    }

    @Test
    fun `single disc albums get no disc headers`() {
        val discs = groupByDisc(listOf(track("a", 1, 1), track("b", 1, 2)))
        assertEquals(1, discs.size)
        assertNull(discs.single().discNumber)
    }

    @Test
    fun `multi disc albums are grouped in disc order`() {
        val tracks = listOf(track("d2t1", 2, 1), track("d1t2", 1, 2), track("d1t1", 1, 1)).sortedWith(albumTrackOrder)
        val discs = groupByDisc(tracks)
        assertEquals(listOf(1, 2), discs.map { it.discNumber })
        assertEquals(listOf("d1t1", "d1t2"), discs[0].tracks.map { it.id })
    }

    @Test
    fun `unnumbered tracks go to the end of their disc`() {
        val sorted = listOf(track("x", 1, null), track("y", 1, 3)).sortedWith(albumTrackOrder)
        assertEquals(listOf("y", "x"), sorted.map { it.id })
    }

    @Test
    fun `album duration reads naturally`() {
        assertEquals("40 sec", formatAlbumDuration(40.0))
        assertEquals("48 min", formatAlbumDuration(48 * 60.0 + 30))
        assertEquals("1 hr 12 min", formatAlbumDuration(72 * 60.0))
    }

    @Test
    fun `album page updates live when the library changes`() = runTest(dispatcher) {
        val tracks = MutableStateFlow(listOf(track("a", 1, 1)))
        val viewModel = AlbumDetailViewModel(
            LiveAlbums(MutableStateFlow(listOf(Album(id = "al", title = "Album")))),
            LiveTracks(tracks)
        )
        viewModel.loadAlbum("al")
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.tracks.size)

        tracks.value = tracks.value + track("b", 1, 2) + track("other", 1, 1, album = "elsewhere")
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), viewModel.uiState.value.tracks.map { it.id })
    }

    @Test
    fun `load failures show an error instead of album not found`() = runTest(dispatcher) {
        val viewModel = AlbumDetailViewModel(
            LiveAlbums(MutableStateFlow(listOf(Album(id = "al", title = "Album")))),
            LiveTracks(flow { throw IllegalStateException("disk error") })
        )
        viewModel.loadAlbum("al")
        advanceUntilIdle()
        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.errorMessage)
    }
}
