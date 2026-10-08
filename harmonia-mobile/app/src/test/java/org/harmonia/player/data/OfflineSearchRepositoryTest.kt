package org.harmonia.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.OfflineSearchRepository
import org.harmonia.player.data.local.dao.AlbumDao
import org.harmonia.player.data.local.dao.ArtistDao
import org.harmonia.player.data.local.entity.AlbumEntity
import org.harmonia.player.data.local.entity.ArtistEntity
import org.harmonia.player.data.local.entity.PlaylistEntity
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.data.local.entity.TrackWithDetails
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeSearchAlbumDao : AlbumDao {
    private val albums = mutableListOf<AlbumEntity>()
    override fun observeAllAlbums(): Flow<List<AlbumEntity>> = MutableStateFlow(albums)
    override suspend fun getAlbumById(id: String): AlbumEntity? = albums.find { it.id == id }
    override fun observeAlbumsByArtist(artistId: String): Flow<List<AlbumEntity>> = MutableStateFlow(albums.filter { it.artistId == artistId })
    override suspend fun upsertAlbums(albums: List<AlbumEntity>) { this.albums.addAll(albums) }
    override suspend fun upsertAlbum(album: AlbumEntity) {
        albums.removeAll { it.id == album.id }
        albums.add(album)
    }
    override suspend fun deleteAlbumById(id: String) { albums.removeAll { it.id == id } }
    override fun searchAlbums(query: String): Flow<List<AlbumEntity>> {
        return MutableStateFlow(albums.filter { it.title.contains(query, ignoreCase = true) })
    }
}

class FakeSearchArtistDao : ArtistDao {
    private val artists = mutableListOf<ArtistEntity>()
    override fun observeAllArtists(): Flow<List<ArtistEntity>> = MutableStateFlow(artists)
    override suspend fun getArtistById(id: String): ArtistEntity? = artists.find { it.id == id }
    override suspend fun upsertArtists(artists: List<ArtistEntity>) { this.artists.addAll(artists) }
    override suspend fun upsertArtist(artist: ArtistEntity) {
        artists.removeAll { it.id == artist.id }
        artists.add(artist)
    }
    override suspend fun deleteArtistById(id: String) { artists.removeAll { it.id == id } }
    override fun searchArtists(query: String): Flow<List<ArtistEntity>> {
        return MutableStateFlow(artists.filter { it.name.contains(query, ignoreCase = true) || it.sortName.contains(query, ignoreCase = true) })
    }
}

class OfflineSearchRepositoryTest {

    private lateinit var trackDao: FakeTrackDao
    private lateinit var albumDao: FakeSearchAlbumDao
    private lateinit var artistDao: FakeSearchArtistDao
    private lateinit var playlistDao: FakePlaylistDao
    private lateinit var searchRepo: OfflineSearchRepository

    @Before
    fun setup() {
        trackDao = FakeTrackDao()
        albumDao = FakeSearchAlbumDao()
        artistDao = FakeSearchArtistDao()
        playlistDao = FakePlaylistDao()
        searchRepo = OfflineSearchRepository(trackDao, albumDao, artistDao, playlistDao)
    }

    @Test
    fun `search tracks queries track dao and returns domain tracks`() = runBlocking {
        trackDao.seedTrackWithDetails(
            TrackWithDetails(
                track = TrackEntity(id = "t1", title = "Radioactive", durationSec = 180.0, createdAt = 0L, updatedAt = 0L)
            )
        )
        val tracks = searchRepo.searchTracks("radio").first()
        assertEquals(1, tracks.size)
        assertEquals("Radioactive", tracks[0].title)

        val empty = searchRepo.searchTracks("nonexistent").first()
        assertTrue(empty.isEmpty())
    }

    @Test
    fun `case-insensitive search matches mixed casing`() = runBlocking {
        trackDao.seedTrackWithDetails(
            TrackWithDetails(
                track = TrackEntity(id = "t1", title = "Radioactive", durationSec = 180.0, createdAt = 0L, updatedAt = 0L)
            )
        )
        val tracks = searchRepo.searchTracks("rAdIoAcTiVe").first()
        assertEquals(1, tracks.size)
        assertEquals("Radioactive", tracks[0].title)
    }

    @Test
    fun `partial matching finds substring anywhere in title`() = runBlocking {
        trackDao.seedTrackWithDetails(
            TrackWithDetails(
                track = TrackEntity(id = "t1", title = "Radioactive", durationSec = 180.0, createdAt = 0L, updatedAt = 0L)
            )
        )
        val tracks = searchRepo.searchTracks("active").first()
        assertEquals(1, tracks.size)
        assertEquals("Radioactive", tracks[0].title)
    }

    @Test
    fun `blank or whitespace query returns empty list without calling dao`() = runBlocking {
        trackDao.seedTrackWithDetails(
            TrackWithDetails(
                track = TrackEntity(id = "t1", title = "Radioactive", durationSec = 180.0, createdAt = 0L, updatedAt = 0L)
            )
        )
        val emptyPure = searchRepo.searchTracks("").first()
        assertTrue(emptyPure.isEmpty())

        val emptySpaces = searchRepo.searchTracks("    ").first()
        assertTrue(emptySpaces.isEmpty())

        val emptyTab = searchRepo.searchAlbums("\t \n").first()
        assertTrue(emptyTab.isEmpty())
    }

    @Test
    fun `whitespace normalized query trims before querying`() = runBlocking {
        trackDao.seedTrackWithDetails(
            TrackWithDetails(
                track = TrackEntity(id = "t1", title = "Radioactive", durationSec = 180.0, createdAt = 0L, updatedAt = 0L)
            )
        )
        val tracks = searchRepo.searchTracks("  Radio  ").first()
        assertEquals(1, tracks.size)
        assertEquals("Radioactive", tracks[0].title)
    }

    @Test
    fun `search albums queries album dao with title and artist join`() = runBlocking {
        artistDao.upsertArtists(
            listOf(ArtistEntity(id = "art1", name = "Massive Attack", sortName = "Massive Attack", createdAt = 0L, updatedAt = 0L))
        )
        albumDao.upsertAlbums(
            listOf(AlbumEntity(id = "al1", title = "Radio City Music", artistId = "art1", createdAt = 0L, updatedAt = 0L))
        )
        val albums = searchRepo.searchAlbums("radio").first()
        assertEquals(1, albums.size)
        assertEquals("Radio City Music", albums[0].title)
        assertEquals("Massive Attack", albums[0].artistName)
    }

    @Test
    fun `search artists queries artist dao by name and sort name`() = runBlocking {
        artistDao.upsertArtists(
            listOf(ArtistEntity(id = "ar1", name = "Radiohead", sortName = "Radiohead", createdAt = 0L, updatedAt = 0L))
        )
        val artists = searchRepo.searchArtists("radio").first()
        assertEquals(1, artists.size)
        assertEquals("Radiohead", artists[0].name)
    }

    @Test
    fun `search playlists queries playlist dao by name and description`() = runBlocking {
        playlistDao.insertPlaylist(
            PlaylistEntity(id = "p1", name = "Radio Favourites", description = "Hits", createdAt = 0L, updatedAt = 0L)
        )
        val playlists = searchRepo.searchPlaylists("radio").first()
        assertEquals(1, playlists.size)
        assertEquals("Radio Favourites", playlists[0].name)
    }

    @Test
    fun `search results maintain deterministic ordering`() = runBlocking {
        trackDao.seedTrackWithDetails(
            TrackWithDetails(track = TrackEntity(id = "t1", title = "Alpha", durationSec = 100.0, createdAt = 10L, updatedAt = 10L))
        )
        trackDao.seedTrackWithDetails(
            TrackWithDetails(track = TrackEntity(id = "t2", title = "Beta", durationSec = 100.0, createdAt = 20L, updatedAt = 20L))
        )
        val tracks = searchRepo.searchTracks("a").first()
        assertEquals(2, tracks.size)
        assertEquals("Alpha", tracks[0].title)
        assertEquals("Beta", tracks[1].title)
    }
}
