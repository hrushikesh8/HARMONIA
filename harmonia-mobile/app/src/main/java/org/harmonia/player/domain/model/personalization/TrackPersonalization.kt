package org.harmonia.player.domain.model.personalization

/**
 * Pure domain model representing personal user annotations (favorite status and star rating) for a track.
 * Decoupled from canonical music metadata per ADR-002 and ADR-021.
 *
 * @property trackId Unique identifier of the associated track.
 * @property isFavorite Whether the track is marked as a user favorite.
 * @property rating User star rating between 1 and 5 stars inclusive, or null if unrated.
 * @property updatedAt Epoch millisecond timestamp of the most recent user annotation.
 */
data class TrackPersonalization(
    val trackId: String,
    val isFavorite: Boolean = false,
    val rating: Int? = null,
    val updatedAt: Long = 0L
) {
    init {
        rating?.let {
            require(it in 1..5) { "Personal rating must be between 1 and 5 stars inclusive, got: $it" }
        }
    }
}
