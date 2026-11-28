package org.harmonia.player.domain.model

/**
 * Listening options from Settings → Playback (task 4.5).
 *
 * @property speed playback speed; pitch stays natural at any speed.
 * @property fadeSeconds fade out at the end of a song and in at the start of the next; 0 = off.
 * @property volumeLeveling plays songs at a similar loudness using their ReplayGain tags.
 */
data class PlaybackOptions(
    val speed: Float = 1f,
    val fadeSeconds: Int = 0,
    val volumeLeveling: Boolean = false
) {
    companion object {
        val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
        val FADE_CHOICES = listOf(0, 3, 6, 10)

        /** Keeps stored values within what the app offers. */
        fun sanitized(speed: Float, fadeSeconds: Int, volumeLeveling: Boolean) = PlaybackOptions(
            speed = speed.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f,
            fadeSeconds = fadeSeconds.coerceIn(0, 12),
            volumeLeveling = volumeLeveling
        )
    }
}
