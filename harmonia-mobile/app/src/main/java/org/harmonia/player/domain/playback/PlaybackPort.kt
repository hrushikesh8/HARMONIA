package org.harmonia.player.domain.playback

import kotlinx.coroutines.flow.StateFlow
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track

/**
 * Pure domain playback port contract.
 * Decoupled completely from Android framework objects (ExoPlayer, MediaSession, Uri, Context).
 *
 * The UI and application layers communicate strictly through this contract.
 * Queue operations are authoritative: the UI requests [next] or [previous],
 * rather than calculating indices directly.
 */
interface PlaybackPort {
    /**
     * Observable stream of authoritative playback state.
     */
    val playbackState: StateFlow<PlaybackState>

    /**
     * Observable stream of the active playback queue.
     */
    val queue: StateFlow<List<QueueItem>>

    /**
     * Starts or resumes playback.
     */
    fun play()

    /**
     * Pauses playback.
     */
    fun pause()

    /**
     * Stops playback and releases transient resources.
     */
    fun stop()

    /**
     * Seeks to a specific millisecond position in the currently active track.
     */
    fun seekTo(positionMs: Long)

    /**
     * Advances to the next item in the authoritative queue according to shuffle/repeat rules.
     */
    fun next()

    /**
     * Rewinds or navigates to the previous item in the authoritative queue.
     */
    fun previous()

    /**
     * Replaces the entire queue with [tracks] and starts playback at [startIndex].
     */
    fun setQueue(tracks: List<Track>, startIndex: Int = 0)

    /**
     * Appends [tracks] to the end of the active queue.
     */
    fun appendToQueue(tracks: List<Track>)

    /**
     * Jumps directly to a specific position in the queue.
     */
    fun jumpToQueueIndex(index: Int)

    /**
     * Toggles or sets shuffle mode.
     */
    fun setShuffleMode(enabled: Boolean)

    /**
     * Cycles or sets repeat mode (OFF, ALL, ONE).
     */
    fun setRepeatMode(mode: RepeatMode)

    /**
     * Sets playback volume scalar (0.0 to 1.0).
     */
    fun setVolume(volume: Float)
}
