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
 * Contains no Android platform classes.
 */
data class PlaybackState(
    val currentTrackId: String? = null,
    val currentLocalFileId: String? = null,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val status: PlaybackStatus = PlaybackStatus.IDLE,
    val playWhenReady: Boolean = false,
    val playbackSuppressionReason: Int = 0,
    val activeQueueIndex: Int = -1,
    val shuffleMode: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val volumeScalar: Float = 1.0f,
    val errorMessage: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STORAGE_DISCONNECTED_MESSAGE = "Storage volume disconnected"
    }
}
