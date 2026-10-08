package org.harmonia.player.domain.model

/**
 * Repeat mode options.
 */
enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Pure domain model representing the current playback engine state.
 */
data class PlaybackState(
    val currentTrackId: String? = null,
    val currentPositionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val shuffleMode: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volumeScalar: Float = 1.0f,
    val updatedAt: Long = System.currentTimeMillis()
)
