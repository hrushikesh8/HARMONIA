package org.harmonia.player.domain.model

/**
 * Pure domain model representing an entry in the persistent active playback queue.
 */
data class QueueItem(
    val position: Int,
    val trackId: String,
    val track: Track? = null,
    val originalIndex: Int? = null,
    val addedAt: Long = System.currentTimeMillis()
)
