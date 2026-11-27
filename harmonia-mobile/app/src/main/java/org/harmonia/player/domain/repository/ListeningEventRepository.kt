package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.TrackPlayStats

/**
 * Pure domain repository interface for recording and querying factual playback telemetry events.
 */
interface ListeningEventRepository {
    /**
     * Records an immutable listening event log entry.
     */
    suspend fun recordEvent(event: ListeningEvent)

    /**
     * Observes recent listening events.
     */
    fun observeRecentEvents(limit: Int = 100): Flow<List<ListeningEvent>>

    /**
     * Returns the total count of recorded listening events.
     */
    suspend fun getEventCount(): Int

    /**
     * Play counts per track (from STARTED events), with plays since [recentSince] counted separately.
     * Powers Home's "Top this month" and "Rediscover" sections.
     */
    fun observeTrackPlayStats(recentSince: Long): Flow<List<TrackPlayStats>> = flowOf(emptyList())
}
