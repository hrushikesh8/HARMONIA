package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.ArtistDao
import org.harmonia.player.data.local.entity.ArtistEntity
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.repository.ArtistRepository

class OfflineArtistRepository(
    private val artistDao: ArtistDao
) : ArtistRepository {

    override fun observeAllArtists(): Flow<List<Artist>> {
        return artistDao.observeAllArtists().map { entities ->
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

    override suspend fun getArtistById(id: String): Artist? {
        val entity = artistDao.getArtistById(id) ?: return null
        return Artist(
            id = entity.id,
            name = entity.name,
            sortName = entity.sortName,
            mbid = entity.mbid,
            artworkHash = entity.artworkHash,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    override suspend fun upsertArtists(artists: List<Artist>) {
        val entities = artists.map { domain ->
            ArtistEntity(
                id = domain.id,
                name = domain.name,
                sortName = domain.sortName,
                mbid = domain.mbid,
                artworkHash = domain.artworkHash,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt
            )
        }
        artistDao.upsertArtists(entities)
    }

    override suspend fun getArtistsPaged(limit: Int, offset: Int): List<Artist> {
        return artistDao.getArtistsPaged(limit, offset).map { entity ->
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
