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
     * With shuffle on, queue positions in the order they will play; empty means plain queue order
     * (shuffle off, or not known). Default for test fakes: always queue order.
     */
    val playOrder: StateFlow<List<Int>> get() = QueueOrderUnknown

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
     * Inserts [tracks] immediately after the currently active queue item.
     * When the queue is empty this behaves like [setQueue].
     */
    fun playNext(tracks: List<Track>)

    /**
     * Moves the queue item at [fromIndex] to [toIndex], keeping the active item playing.
     */
    fun moveQueueItem(fromIndex: Int, toIndex: Int)

    /**
     * Removes the queue item at [index]. The currently active item cannot be removed.
     */
    fun removeQueueItem(index: Int)

    /**
     * Puts [tracks] back into the queue at [index] (used to undo a removal). Implementations
     * without positional insert fall back to appending.
     */
    fun insertIntoQueue(index: Int, tracks: List<Track>) = appendToQueue(tracks)

    /**
     * Connects to the background playback service if that hasn't happened yet. Safe to call
     * repeatedly. The UI calls this once its first frame is drawn so that starting the service
     * (which builds the audio player on the main thread) doesn't compete with app launch.
     */
    fun ensureConnected() {}

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

    /**
     * Observable stream of the authoritative sleep timer state.
     */
    val sleepTimerState: StateFlow<org.harmonia.player.domain.model.audio.SleepTimerState>

    /**
     * Starts or replaces the active sleep timer with a duration countdown in milliseconds.
     */
    fun startSleepTimer(durationMs: Long)

    /**
     * Starts or replaces the active sleep timer to trigger upon completion of the currently playing track.
     */
    fun startSleepTimerEndOfTrack()

    /**
     * Cancels the active sleep timer and immediately restores playback volume to the baseline.
     */
    fun cancelSleepTimer()
}

/** Shared "no shuffle order" value for [PlaybackPort.playOrder]. */
private val QueueOrderUnknown: StateFlow<List<Int>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())

/**
 * Walks a shuffle order from [first] using [next] (which returns -1 at the end) and returns the
 * positions in play order, or an empty list if the walk doesn't cover all [count] items once.
 */
fun shufflePlayOrder(count: Int, first: Int, next: (Int) -> Int): List<Int> {
    if (count <= 0 || first !in 0 until count) return emptyList()
    val order = ArrayList<Int>(count)
    val seen = HashSet<Int>(count)
    var index = first
    while (index in 0 until count && seen.add(index) && order.size < count) {
        order += index
        index = next(index)
    }
    return if (order.size == count) order else emptyList()
}
