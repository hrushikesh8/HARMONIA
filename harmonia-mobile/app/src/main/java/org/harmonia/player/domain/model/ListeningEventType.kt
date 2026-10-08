package org.harmonia.player.domain.model

/**
 * Factual listening event types representing playback lifecycle milestones.
 */
enum class ListeningEventType {
    STARTED,
    PAUSED,
    RESUMED,
    SEEKED,
    COMPLETED,
    SKIPPED,
    STOPPED
}
