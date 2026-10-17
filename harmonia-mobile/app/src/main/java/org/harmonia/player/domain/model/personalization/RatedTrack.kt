package org.harmonia.player.domain.model.personalization

import org.harmonia.player.domain.model.Track

/**
 * Pure domain model combining canonical track metadata with its user star rating.
 *
 * @property track Canonical track domain model.
 * @property rating User star rating strictly between 1 and 5 stars inclusive.
 * @property ratedAt Epoch millisecond timestamp when the rating was applied or updated.
 */
data class RatedTrack(
    val track: Track,
    val rating: Int,
    val ratedAt: Long
) {
    init {
        require(rating in 1..5) { "Personal rating must be between 1 and 5 stars inclusive, got: $rating" }
    }
}
