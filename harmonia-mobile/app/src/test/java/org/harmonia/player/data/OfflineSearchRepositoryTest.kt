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
        return MutableStateFlow(artists.filter { it.name.contains(query, ignoreCase = true) })
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
    fun `search tracks queries track dao`() = runBlocking {
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
    fun `search albums queries album dao`() = runBlocking {
        albumDao.upsertAlbums(
            listOf(AlbumEntity(id = "al1", title = "Radio City Music", createdAt = 0L, updatedAt = 0L))
        )
        val albums = searchRepo.searchAlbums("radio").first()
        assertEquals(1, albums.size)
        assertEquals("Radio City Music", albums[0].title)
    }

    @Test
    fun `search artists queries artist dao`() = runBlocking {
        artistDao.upsertArtists(
            listOf(ArtistEntity(id = "ar1", name = "Radiohead", sortName = "Radiohead", createdAt = 0L, updatedAt = 0L))
        )
        val artists = searchRepo.searchArtists("radio").first()
        assertEquals(1, artists.size)
        assertEquals("Radiohead", artists[0].name)
    }

    @Test
    fun `search playlists queries playlist dao`() = runBlocking {
        playlistDao.insertPlaylist(
            PlaylistEntity(id = "p1", name = "Radio Favourites", createdAt = 0L, updatedAt = 0L)
        )
        val playlists = searchRepo.searchPlaylists("radio").first()
        assertEquals(1, playlists.size)
        assertEquals("Radio Favourites", playlists[0].name)
    }
}
