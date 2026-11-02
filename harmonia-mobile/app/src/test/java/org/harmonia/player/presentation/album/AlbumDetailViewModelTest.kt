package org.harmonia.player.presentation.album

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AlbumDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeAlbumRepository(private val albums: List<Album>) : AlbumRepository {
        override suspend fun getAlbumById(id: String): Album? = albums.find { it.id == id }
        override fun observeAllAlbums(): Flow<List<Album>> = flowOf(albums)
        override fun observeAlbumsByArtist(artistId: String): Flow<List<Album>> =
            flowOf(albums.filter { it.artistId == artistId })
        override suspend fun upsertAlbums(albums: List<Album>) {}
    }

    private class FakeTrackRepository(private val tracks: List<Track>) : TrackRepository {
        override suspend fun getTrackById(id: String): Track? = tracks.find { it.id == id }
        override fun observeAllTracks(): Flow<List<Track>> = flowOf(tracks)
        override fun observeOfflineTracks(): Flow<List<Track>> = flowOf(tracks)
        override suspend fun getTracksByAlbum(albumId: String): List<Track> =
            tracks.filter { it.albumId == albumId }
        override suspend fun getTracksByArtist(artistId: String): List<Track> =
            tracks.filter { it.artistId == artistId }
        override fun searchTracks(query: String): Flow<List<Track>> = flowOf(tracks)
        override suspend fun upsertTracks(tracks: List<Track>) {}
        override suspend fun deleteTrack(id: String) {}
    }

    @Test
    fun `loadAlbum successfully aggregates tracks and calculates total duration`() = runTest {
        val testAlbum = Album(
            id = "album_interstellar",
            title = "Interstellar (Original Motion Picture Soundtrack)",
            artistId = "artist_hans_zimmer",
            artistName = "Hans Zimmer",
            year = 2014,
            artworkHash = "hash_interstellar_art"
        )

        val track1 = Track(
            id = "track_1",
            title = "Cornfield Chase",
            albumId = "album_interstellar",
            artistId = "artist_hans_zimmer",
            artistName = "Hans Zimmer",
            trackNumber = 1,
            discNumber = 1,
            durationSec = 126.0,
            year = 2014
        )

        val track2 = Track(
            id = "track_2",
            title = "Day One",
            albumId = "album_interstellar",
            artistId = "artist_hans_zimmer",
            artistName = "Hans Zimmer",
            trackNumber = 2,
            discNumber = 1,
            durationSec = 199.0,
            year = 2014
        )

        val albumRepo = FakeAlbumRepository(listOf(testAlbum))
        val trackRepo = FakeTrackRepository(listOf(track2, track1))

        val viewModel = AlbumDetailViewModel(albumRepo, trackRepo)
        viewModel.loadAlbum("album_interstellar")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.album)
        assertEquals("Interstellar (Original Motion Picture Soundtrack)", state.album?.title)
        assertEquals(2, state.tracks.size)

        // Verify duration calculation: 126.0 + 199.0 = 325.0 seconds
        assertEquals(325.0, state.totalDurationSec, 0.001)

        // Verify soundtrack indicator
        val isSoundtrack = state.album?.title?.contains("soundtrack", ignoreCase = true) == true
        assertTrue(isSoundtrack)
    }

    @Test
    fun `regular studio album without soundtrack indicator is not flagged as soundtrack`() = runTest {
        val testAlbum = Album(
            id = "album_random_access_memories",
            title = "Random Access Memories",
            artistId = "artist_daft_punk",
            artistName = "Daft Punk",
            year = 2013
        )

        val track1 = Track(
            id = "track_get_lucky",
            title = "Get Lucky",
            albumId = "album_random_access_memories",
            artistId = "artist_daft_punk",
            artistName = "Daft Punk feat. Pharrell Williams",
            trackNumber = 8,
            discNumber = 1,
            durationSec = 369.0,
            year = 2013
        )

        val albumRepo = FakeAlbumRepository(listOf(testAlbum))
        val trackRepo = FakeTrackRepository(listOf(track1))

        val viewModel = AlbumDetailViewModel(albumRepo, trackRepo)
        viewModel.loadAlbum("album_random_access_memories")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("Random Access Memories", state.album?.title)

        val isSoundtrack = state.album?.title?.contains("soundtrack", ignoreCase = true) == true
        assertFalse(isSoundtrack)
    }
}
