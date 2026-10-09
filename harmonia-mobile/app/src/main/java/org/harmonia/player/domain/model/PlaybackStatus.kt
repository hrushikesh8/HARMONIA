package org.harmonia.player.domain.model

/**
 * Factual playback engine status (pure domain, decoupled from ExoPlayer constants).
 */
enum class PlaybackStatus {
    IDLE,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    SUPPRESSED,
    ENDED,
    ERROR
}
