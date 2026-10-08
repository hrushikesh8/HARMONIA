package org.harmonia.player.domain.model

/**
 * Pure domain model representing an immutable listening event log entry.
 */
data class ListeningEvent(
    val id: String,
    val trackId: String,
    val localFileId: String? = null,
    val startedAt: Long,
    val completedAt: Long? = null,
    val durationPlayedSec: Double,
    val completedFully: Boolean,
    val outputRoute: String? = null,
    val isSynced: Boolean = false
)
