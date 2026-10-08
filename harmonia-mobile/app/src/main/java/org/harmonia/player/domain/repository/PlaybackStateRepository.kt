package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.QueueItem

/**
 * Pure domain repository interface for persisting playback state and queue.
 */
interface PlaybackStateRepository {
    /**
     * Observes current persistent playback state (playback position, shuffle, repeat).
     */
    fun observePlaybackState(): Flow<PlaybackState?>

    /**
     * Saves or updates persistent playback state.
     */
    suspend fun savePlaybackState(state: PlaybackState)

    /**
     * Observes the persistent queue items in playback order.
     */
    fun observeQueue(): Flow<List<QueueItem>>

    /**
     * Replaces the persistent queue with new items.
     */
    suspend fun setQueue(items: List<QueueItem>)

    /**
     * Appends items to the active queue.
     */
    suspend fun appendToQueue(items: List<QueueItem>)

    /**
     * Clears the active queue.
     */
    suspend fun clearQueue()
}
