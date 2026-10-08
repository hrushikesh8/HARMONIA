package org.harmonia.player.presentation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.presentation.library.AlbumSortOrder
import org.harmonia.player.presentation.library.ArtistSortOrder
import org.harmonia.player.presentation.library.LibraryTab
import org.harmonia.player.presentation.library.LibraryViewModel
import org.harmonia.player.presentation.library.TrackSortOrder
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class FakeTrackRepo : TrackRepository {
    val tracks = MutableStateFlow<List<Track>>(emptyList())
    override fun observeAllTracks(): Flow<List<Track>> = tracks
    override fun observeOfflineTracks(): Flow<List<Track>> = tracks
    override suspend fun getTrackById(id: String): Track? = tracks.value.find { it.id == id }
    override fun searchTracks(query: String): Flow<List<Track>> = tracks.map { list ->
        list.filter { it.title.contains(query, ignoreCase = true) }
    }
    override suspend fun upsertTracks(tracks: List<Track>) { this.tracks.value = tracks }
    override suspend fun deleteTrack(id: String) { this.tracks.value = this.tracks.value.filter { it.id != id } }
}

class FakeAlbumRepo : AlbumRepository {
    val albums = MutableStateFlow<List<Album>>(emptyList())
    override fun observeAllAlbums(): Flow<List<Album>> = albums
    override suspend fun getAlbumById(id: String): Album? = albums.value.find { it.id == id }
    override fun observeAlbumsByArtist(artistId: String): Flow<List<Album>> = albums.map { list ->
        list.filter { it.artistId == artistId }
    }
    override suspend fun upsertAlbums(albums: List<Album>) { this.albums.value = albums }
}

class FakeArtistRepo : ArtistRepository {
    val artists = MutableStateFlow<List<Artist>>(emptyList())
    override fun observeAllArtists(): Flow<List<Artist>> = artists
    override suspend fun getArtistById(id: String): Artist? = artists.value.find { it.id == id }
    override suspend fun upsertArtists(artists: List<Artist>) { this.artists.value = artists }
}

class LibrarySortingTest {

    private lateinit var trackRepo: FakeTrackRepo
    private lateinit var albumRepo: FakeAlbumRepo
    private lateinit var artistRepo: FakeArtistRepo
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        trackRepo = FakeTrackRepo()
        albumRepo = FakeAlbumRepo()
        artistRepo = FakeArtistRepo()

        val sampleTracks = listOf(
            Track(id = "1", title = "Zebra", artistName = "Alpha", durationSec = 300.0, createdAt = 1000L),
            Track(id = "2", title = "Alpha", artistName = "Beta", durationSec = 150.0, createdAt = 3000L),
            Track(id = "3", title = "Echo", artistName = "Gamma", durationSec = 450.0, createdAt = 2000L)
        )
        val sampleAlbums = listOf(
            Album(id = "a1", title = "Ok Computer", artistName = "Radiohead", year = 1997),
            Album(id = "a2", title = "Amnesiac", artistName = "Radiohead", year = 2001),
            Album(id = "a3", title = "Kid A", artistName = "Radiohead", year = 2000)
        )
        val sampleArtists = listOf(
            Artist(id = "ar1", name = "Radiohead"),
            Artist(id = "ar2", name = "Aphex Twin"),
            Artist(id = "ar3", name = "Boards of Canada")
        )

        trackRepo.tracks.value = sampleTracks
        albumRepo.albums.value = sampleAlbums
        artistRepo.artists.value = sampleArtists

        viewModel = LibraryViewModel(trackRepo, albumRepo, artistRepo)
    }

    @Test
    fun `tracks sort by title ascending by default`() = runBlocking {
        viewModel.setTrackSortOrder(TrackSortOrder.TITLE_ASC)
        val state = viewModel.uiState.first { !it.isLoading }
        val titles = state.tracks.map { it.title }
        assertEquals(listOf("Alpha", "Echo", "Zebra"), titles)
    }

    @Test
    fun `tracks sort by title descending`() = runBlocking {
        viewModel.setTrackSortOrder(TrackSortOrder.TITLE_DESC)
        val state = viewModel.uiState.first { it.trackSortOrder == TrackSortOrder.TITLE_DESC }
        val titles = state.tracks.map { it.title }
        assertEquals(listOf("Zebra", "Echo", "Alpha"), titles)
    }

    @Test
    fun `tracks sort by duration descending`() = runBlocking {
        viewModel.setTrackSortOrder(TrackSortOrder.DURATION_DESC)
        val state = viewModel.uiState.first { it.trackSortOrder == TrackSortOrder.DURATION_DESC }
        val titles = state.tracks.map { it.title }
        assertEquals(listOf("Echo", "Zebra", "Alpha"), titles)
    }

    @Test
    fun `tracks sort by date added descending`() = runBlocking {
        viewModel.setTrackSortOrder(TrackSortOrder.DATE_ADDED_DESC)
        val state = viewModel.uiState.first { it.trackSortOrder == TrackSortOrder.DATE_ADDED_DESC }
        val titles = state.tracks.map { it.title }
        assertEquals(listOf("Alpha", "Echo", "Zebra"), titles)
    }

    @Test
    fun `albums sort by title ascending`() = runBlocking {
        viewModel.setAlbumSortOrder(AlbumSortOrder.TITLE_ASC)
        val state = viewModel.uiState.first { it.albumSortOrder == AlbumSortOrder.TITLE_ASC && !it.isLoading }
        val titles = state.albums.map { it.title }
        assertEquals(listOf("Amnesiac", "Kid A", "Ok Computer"), titles)
    }

    @Test
    fun `albums sort by year descending`() = runBlocking {
        viewModel.setAlbumSortOrder(AlbumSortOrder.YEAR_DESC)
        val state = viewModel.uiState.first { it.albumSortOrder == AlbumSortOrder.YEAR_DESC }
        val titles = state.albums.map { it.title }
        assertEquals(listOf("Amnesiac", "Kid A", "Ok Computer"), titles)
    }

    @Test
    fun `artists sort by name ascending and descending`() = runBlocking {
        viewModel.setArtistSortOrder(ArtistSortOrder.NAME_ASC)
        var state = viewModel.uiState.first { it.artistSortOrder == ArtistSortOrder.NAME_ASC && !it.isLoading }
        assertEquals(listOf("Aphex Twin", "Boards of Canada", "Radiohead"), state.artists.map { it.name })

        viewModel.setArtistSortOrder(ArtistSortOrder.NAME_DESC)
        state = viewModel.uiState.first { it.artistSortOrder == ArtistSortOrder.NAME_DESC }
        assertEquals(listOf("Radiohead", "Boards of Canada", "Aphex Twin"), state.artists.map { it.name })
    }

    @Test
    fun `library tab selection switches state`() = runBlocking {
        viewModel.selectTab(LibraryTab.ALBUMS)
        val state = viewModel.uiState.first { it.selectedTab == LibraryTab.ALBUMS }
        assertEquals(LibraryTab.ALBUMS, state.selectedTab)
    }
}
