package org.harmonia.player.service.audio

import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Fade-out at the end of a song and fade-in at the start of the next (task 4.5).
 *
 * ExoPlayer plays one song at a time, so this is a fade between songs rather than an overlapping
 * crossfade. It applies to every song that plays on into the next, including tracks of the same
 * album; it is skipped after a manual skip, on repeat-one and for very short songs.
 */
object SongFades {

    /** Audio is processed a little before it is heard; fading out this much early ends the fade on time. */
    const val OUTPUT_LOOKAHEAD_MS = 400L

    /**
     * Gain (0..1) for the audio being processed now.
     *
     * @param fadeOutAllowed another song will play on after this one.
     * @param fadeInActive this song started by playing on from a faded song (not a manual skip).
     */
    fun fadeGain(
        positionMs: Long,
        durationMs: Long,
        fadeMs: Long,
        fadeOutAllowed: Boolean,
        fadeInActive: Boolean
    ): Float {
        if (fadeMs <= 0) return 1f
        // Very short songs would spend most of their time fading.
        if (durationMs in 1 until fadeMs * 3) return 1f
        val out = if (fadeOutAllowed && durationMs > 0) {
            ease((durationMs - positionMs - OUTPUT_LOOKAHEAD_MS).toFloat() / fadeMs)
        } else {
            1f
        }
        val `in` = if (fadeInActive) ease(positionMs.toFloat() / fadeMs) else 1f
        return min(out, `in`)
    }

    /** Smooth (equal-power style) curve so fades sound even rather than dropping off at the end. */
    private fun ease(t: Float): Float = sin(t.coerceIn(0f, 1f) * PI.toFloat() / 2f)
}
