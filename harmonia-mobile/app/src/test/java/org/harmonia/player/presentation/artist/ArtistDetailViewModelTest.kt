package org.harmonia.player.presentation.artist

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
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeArtistRepository(private val artists: List<Artist>) : ArtistRepository {
        override suspend fun getArtistById(id: String): Artist? = artists.find { it.id == id }
        override fun observeAllArtists(): Flow<List<Artist>> = flowOf(artists)
        override suspend fun upsertArtists(artists: List<Artist>) {}
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
    fun `loadArtist aggregates tracks and discography and handles collaboration credits`() = runTest {
        val daftPunk = Artist(
            id = "artist_daft_punk",
            name = "Daft Punk"
        )

        val album1 = Album(
            id = "album_discovery",
            title = "Discovery",
            artistId = "artist_daft_punk",
            artistName = "Daft Punk",
            year = 2001
        )

        val album2 = Album(
            id = "album_ram",
            title = "Random Access Memories",
            artistId = "artist_daft_punk",
            artistName = "Daft Punk",
            year = 2013
        )

        val track1 = Track(
            id = "track_one_more_time",
            title = "One More Time",
            albumId = "album_discovery",
            artistId = "artist_daft_punk",
            artistName = "Daft Punk",
            durationSec = 320.0
        )

        val track2 = Track(
            id = "track_get_lucky",
            title = "Get Lucky",
            albumId = "album_ram",
            artistId = "artist_daft_punk",
            artistName = "Daft Punk feat. Pharrell Williams",
            durationSec = 369.0
        )

        val artistRepo = FakeArtistRepository(listOf(daftPunk))
        val albumRepo = FakeAlbumRepository(listOf(album1, album2))
        val trackRepo = FakeTrackRepository(listOf(track1, track2))

        val viewModel = ArtistDetailViewModel(artistRepo, albumRepo, trackRepo)
        viewModel.loadArtist("artist_daft_punk")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.artist)
        assertEquals("Daft Punk", state.artist?.name)
        assertEquals(2, state.albums.size)
        assertEquals(2, state.tracks.size)

        // Verify collaboration credit track is retained
        assertTrue(state.tracks.any { it.title == "Get Lucky" })
        val getLucky = state.tracks.first { it.title == "Get Lucky" }
        assertEquals("Daft Punk feat. Pharrell Williams", getLucky.artistName)

        // Verify total duration calculation: 320.0 + 369.0 = 689.0 seconds
        assertEquals(689.0, state.totalDurationSec, 0.001)

        // Verify albums are ordered in reverse chronological order (2013 before 2001)
        assertEquals("Random Access Memories", state.albums[0].title)
        assertEquals("Discovery", state.albums[1].title)
    }
}
