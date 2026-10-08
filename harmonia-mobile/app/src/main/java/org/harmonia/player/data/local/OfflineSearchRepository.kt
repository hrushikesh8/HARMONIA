package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.AlbumDao
import org.harmonia.player.data.local.dao.ArtistDao
import org.harmonia.player.data.local.dao.PlaylistDao
import org.harmonia.player.data.local.dao.TrackDao
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.SearchRepository

/**
 * Concrete implementation of SearchRepository backed by Room SQLite queries.
 * Establishes the query foundation for M3-B search.
 */
class OfflineSearchRepository(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao
) : SearchRepository {

    override fun searchTracks(query: String): Flow<List<Track>> {
        return trackDao.searchTracks(query).map { list ->
            list.map { it.toDomainTrack() }
        }
    }

    override fun searchAlbums(query: String): Flow<List<Album>> {
        return albumDao.searchAlbums(query).map { entities ->
            entities.map { entity ->
                val artist = entity.artistId?.let { artistDao.getArtistById(it) }
                Album(
                    id = entity.id,
                    title = entity.title,
                    artistId = entity.artistId,
                    artistName = artist?.name,
                    year = entity.year,
                    totalTracks = entity.totalTracks,
                    totalDiscs = entity.totalDiscs,
                    mbid = entity.mbid,
                    artworkHash = entity.artworkHash,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }
    }

    override fun searchArtists(query: String): Flow<List<Artist>> {
        return artistDao.searchArtists(query).map { entities ->
            entities.map { entity ->
                Artist(
                    id = entity.id,
                    name = entity.name,
                    sortName = entity.sortName,
                    mbid = entity.mbid,
                    artworkHash = entity.artworkHash,
                    createdAt = entity.createdAt,
                    updatedAt = entity.updatedAt
                )
            }
        }
    }

    override fun searchPlaylists(query: String): Flow<List<Playlist>> {
        return playlistDao.searchPlaylists(query).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
