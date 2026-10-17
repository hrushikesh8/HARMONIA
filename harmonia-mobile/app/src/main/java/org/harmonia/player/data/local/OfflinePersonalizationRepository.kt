package org.harmonia.player.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.harmonia.player.data.local.dao.PersonalizationDao
import org.harmonia.player.data.local.dao.TrackDao
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.harmonia.player.domain.model.personalization.TrackPersonalization
import org.harmonia.player.domain.repository.PersonalizationRepository

/**
 * Concrete implementation of PersonalizationRepository backed by Room SQLite.
 * Uses Room as the single source of truth and preserves deterministic ordering.
 */
class OfflinePersonalizationRepository(
    private val personalizationDao: PersonalizationDao,
    private val trackDao: TrackDao
) : PersonalizationRepository {

    override fun observePersonalization(trackId: String): Flow<TrackPersonalization> {
        return combine(
            personalizationDao.observeFavorite(trackId),
            personalizationDao.observeRating(trackId)
        ) { favoriteEntity, ratingEntity ->
            TrackPersonalization(
                trackId = trackId,
                isFavorite = favoriteEntity != null,
                rating = ratingEntity?.rating,
                updatedAt = maxOf(
                    favoriteEntity?.clientUpdatedAt ?: 0L,
                    ratingEntity?.clientUpdatedAt ?: 0L
                )
            )
        }
    }

    override fun observeFavorite(trackId: String): Flow<Boolean> {
        return personalizationDao.observeFavorite(trackId).map { it != null }
    }

    override fun observeRating(trackId: String): Flow<Int?> {
        return personalizationDao.observeRating(trackId).map { it?.rating }
    }

    override fun observeFavoriteTracks(): Flow<List<Track>> {
        return personalizationDao.observeFavoriteTracks().map { list ->
            list.map { it.toDomainTrack() }
        }
    }

    override fun observeRatedTracks(): Flow<List<RatedTrack>> {
        return combine(
            personalizationDao.observeAllRatings(),
            trackDao.observeAllTracks()
        ) { ratings, trackDetails ->
            val trackMap = trackDetails.associateBy { it.track.id }
            ratings.mapNotNull { ratingEntity ->
                trackMap[ratingEntity.trackId]?.let { trackWithDetails ->
                    RatedTrack(
                        track = trackWithDetails.toDomainTrack(),
                        rating = ratingEntity.rating,
                        ratedAt = ratingEntity.clientUpdatedAt
                    )
                }
            }
        }
    }

    override suspend fun getPersonalization(trackId: String): TrackPersonalization {
        val fav = personalizationDao.getFavorite(trackId)
        val rat = personalizationDao.getRating(trackId)
        return TrackPersonalization(
            trackId = trackId,
            isFavorite = fav != null,
            rating = rat?.rating,
            updatedAt = maxOf(
                fav?.clientUpdatedAt ?: 0L,
                rat?.clientUpdatedAt ?: 0L
            )
        )
    }

    override suspend fun setFavorite(trackId: String, isFavorite: Boolean) {
        personalizationDao.setFavorite(trackId, isFavorite)
    }

    override suspend fun toggleFavorite(trackId: String): Boolean {
        return personalizationDao.toggleFavorite(trackId)
    }

    override suspend fun setRating(trackId: String, rating: Int?) {
        if (rating != null) {
            require(rating in 1..5) { "Personal rating must be between 1 and 5 stars inclusive, got: $rating" }
        }
        personalizationDao.setRating(trackId, rating)
    }

    override suspend fun clearPersonalization(trackId: String) {
        personalizationDao.deleteFavorite(trackId)
        personalizationDao.deleteRating(trackId)
    }
}
