package org.harmonia.player.presentation.home

import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.TrackPlayStats

/** "This month" for Home means the last 30 days. */
internal const val TOP_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

/** A song counts as "not played lately" once it hasn't been played for this long. */
internal const val REDISCOVER_AFTER_MS = 30L * 24 * 60 * 60 * 1000

/** Minimum all-time plays before a song is a "favourite" worth resurfacing. */
internal const val REDISCOVER_MIN_PLAYS = 3

internal const val TOP_THIS_MONTH_LIMIT = 5
internal const val REDISCOVER_LIMIT = 10

/**
 * Your most played songs in the last 30 days, most plays first (ties: most recently played first).
 * Songs that no longer exist in the library are skipped.
 */
internal fun topThisMonth(
    stats: List<TrackPlayStats>,
    tracksById: Map<String, Track>,
    limit: Int = TOP_THIS_MONTH_LIMIT
): List<Track> = stats
    .filter { it.recentPlays > 0 }
    .sortedWith(compareByDescending<TrackPlayStats> { it.recentPlays }.thenByDescending { it.lastPlayedAt })
    .mapNotNull { tracksById[it.trackId] }
    .take(limit)

/**
 * Songs you played a lot ([REDISCOVER_MIN_PLAYS]+ times) but not in the last month,
 * most-played first. This is plain play history, not a recommendation engine.
 */
internal fun rediscover(
    stats: List<TrackPlayStats>,
    tracksById: Map<String, Track>,
    now: Long,
    limit: Int = REDISCOVER_LIMIT
): List<Track> = stats
    .filter { it.totalPlays >= REDISCOVER_MIN_PLAYS && it.lastPlayedAt < now - REDISCOVER_AFTER_MS }
    .sortedWith(compareByDescending<TrackPlayStats> { it.totalPlays }.thenBy { it.lastPlayedAt })
    .mapNotNull { tracksById[it.trackId] }
    .take(limit)
