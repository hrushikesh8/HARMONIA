package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.AlbumDao
import org.harmonia.player.data.local.dao.ArtistDao
import org.harmonia.player.data.local.entity.AlbumEntity
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.repository.AlbumRepository

class OfflineAlbumRepository(
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao
) : AlbumRepository {

    override fun observeAllAlbums(): Flow<List<Album>> {
        return albumDao.observeAllAlbums().map { entities ->
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

    override suspend fun getAlbumById(id: String): Album? {
        val entity = albumDao.getAlbumById(id) ?: return null
        val artist = entity.artistId?.let { artistDao.getArtistById(it) }
        return Album(
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

    override fun observeAlbumsByArtist(artistId: String): Flow<List<Album>> {
        return albumDao.observeAlbumsByArtist(artistId).map { entities ->
            val artist = artistDao.getArtistById(artistId)
            entities.map { entity ->
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

    override suspend fun upsertAlbums(albums: List<Album>) {
        val entities = albums.map { domain ->
            AlbumEntity(
                id = domain.id,
                title = domain.title,
                artistId = domain.artistId,
                year = domain.year,
                totalTracks = domain.totalTracks,
                totalDiscs = domain.totalDiscs,
                mbid = domain.mbid,
                artworkHash = domain.artworkHash,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt
            )
        }
        albumDao.upsertAlbums(entities)
    }

    override suspend fun getAlbumsPaged(limit: Int, offset: Int): List<Album> {
        return albumDao.getAlbumsPaged(limit, offset).map { entity ->
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
