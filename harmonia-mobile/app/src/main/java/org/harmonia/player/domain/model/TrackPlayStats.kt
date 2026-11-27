package org.harmonia.player.domain.model

/**
 * How often a track has been played, computed from STARTED listening events.
 *
 * @param totalPlays plays ever recorded on this phone
 * @param recentPlays plays since the requested cut-off (e.g. the last 30 days)
 * @param lastPlayedAt time of the most recent play, epoch millis
 */
data class TrackPlayStats(
    val trackId: String,
    val totalPlays: Int,
    val recentPlays: Int,
    val lastPlayedAt: Long
)
