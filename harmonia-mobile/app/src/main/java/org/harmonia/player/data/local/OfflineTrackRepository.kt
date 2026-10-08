package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.TrackDao
import org.harmonia.player.data.local.entity.TrackEntity
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.TrackRepository

/**
 * Concrete implementation of TrackRepository backed by Room SQLite.
 */
class OfflineTrackRepository(
    private val trackDao: TrackDao
) : TrackRepository {

    override fun observeAllTracks(): Flow<List<Track>> {
        return trackDao.observeAllTracks().map { list ->
            list.map { it.toDomainTrack() }
        }
    }

    override fun observeOfflineTracks(): Flow<List<Track>> {
        return trackDao.observeOfflineTracks().map { list ->
            list.map { it.toDomainTrack() }
        }
    }

    override suspend fun getTrackById(id: String): Track? {
        return trackDao.getTrackById(id)?.toDomainTrack()
    }

    override fun searchTracks(query: String): Flow<List<Track>> {
        return trackDao.searchTracks(query).map { list ->
            list.map { it.toDomainTrack() }
        }
    }

    override suspend fun upsertTracks(tracks: List<Track>) {
        val entities = tracks.map { track ->
            TrackEntity(
                id = track.id,
                albumId = track.albumId,
                artistId = track.artistId,
                title = track.title,
                trackNumber = track.trackNumber,
                discNumber = track.discNumber,
                durationSec = track.durationSec,
                year = track.year,
                genre = track.genre,
                mbid = track.mbid,
                integratedLufs = track.integratedLufs,
                truePeakDbtp = track.truePeakDbtp,
                createdAt = track.createdAt,
                updatedAt = track.updatedAt
            )
        }
        trackDao.upsertTracks(entities)
    }

    override suspend fun deleteTrack(id: String) {
        trackDao.deleteTrackById(id)
    }
}
