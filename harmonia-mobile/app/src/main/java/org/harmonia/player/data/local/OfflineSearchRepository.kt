package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
 * Establishes the offline query foundation for search without artificial benchmarks.
 */
class OfflineSearchRepository(
    private val trackDao: TrackDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao
) : SearchRepository {

    override fun searchTracks(query: String): Flow<List<Track>> {
        val clean = query.trim()
        if (clean.isBlank()) return flowOf(emptyList())
        return trackDao.searchTracks(clean).map { list ->
            list.map { it.toDomainTrack() }
        }
    }

    override fun searchAlbums(query: String): Flow<List<Album>> {
        val clean = query.trim()
        if (clean.isBlank()) return flowOf(emptyList())
        return albumDao.searchAlbums(clean).map { entities ->
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
        val clean = query.trim()
        if (clean.isBlank()) return flowOf(emptyList())
        return artistDao.searchArtists(clean).map { entities ->
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
        val clean = query.trim()
        if (clean.isBlank()) return flowOf(emptyList())
        return playlistDao.searchPlaylists(clean).map { entities ->
            entities.map { it.toDomain() }
        }
    }
}
