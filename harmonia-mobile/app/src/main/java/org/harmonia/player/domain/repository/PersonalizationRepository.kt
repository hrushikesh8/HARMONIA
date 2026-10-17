package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.personalization.RatedTrack
import org.harmonia.player.domain.model.personalization.TrackPersonalization

/**
 * Pure domain repository interface for user personalization (Favorites and 1-5 Star Ratings).
 * Decoupled from canonical audio metadata per ADR-002 and ADR-021.
 */
interface PersonalizationRepository {

    /**
     * Observes the complete personalization state (favorite and rating) for a specific track.
     */
    fun observePersonalization(trackId: String): Flow<TrackPersonalization>

    /**
     * Observes only the favorite boolean state for a specific track.
     */
    fun observeFavorite(trackId: String): Flow<Boolean>

    /**
     * Observes only the star rating (1..5 or null if unrated) for a specific track.
     */
    fun observeRating(trackId: String): Flow<Int?>

    /**
     * Observes all tracks marked as favorites, ordered deterministically by recency descending.
     */
    fun observeFavoriteTracks(): Flow<List<Track>>

    /**
     * Observes all rated tracks, ordered deterministically by rating descending, then recency descending.
     */
    fun observeRatedTracks(): Flow<List<RatedTrack>>

    /**
     * Fetches a point-in-time snapshot of the track's personalization state.
     */
    suspend fun getPersonalization(trackId: String): TrackPersonalization

    /**
     * Idempotently marks or unmarks a track as a user favorite.
     */
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    /**
     * Atomically toggles the favorite state for a track, returning the new state.
     */
    suspend fun toggleFavorite(trackId: String): Boolean

    /**
     * Idempotently sets or clears a track's star rating.
     * Pass 1..5 to set/update rating, or null to clear the rating.
     */
    suspend fun setRating(trackId: String, rating: Int?)

    /**
     * Clears the user rating for a track.
     */
    suspend fun clearRating(trackId: String) = setRating(trackId, null)

    /**
     * Clears all personalization annotations (both favorite and rating) for a track.
     */
    suspend fun clearPersonalization(trackId: String)
}
