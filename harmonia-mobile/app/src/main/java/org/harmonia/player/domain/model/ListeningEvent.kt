package org.harmonia.player.domain.model

import java.util.UUID

/**
 * Pure domain model representing an immutable factual listening event log entry.
 * Factual telemetry only — no personalization, recommendation, or analytics tracking.
 */
data class ListeningEvent(
    val id: String = UUID.randomUUID().toString(),
    val eventType: ListeningEventType = ListeningEventType.STARTED,
    val trackId: String,
    val localFileId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val durationPlayedSec: Double = 0.0,
    val completedFully: Boolean = false,
    val outputRoute: String? = null,
    val isSynced: Boolean = false
)
