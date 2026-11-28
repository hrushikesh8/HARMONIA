package org.harmonia.player.service.audio

import android.os.Handler
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.harmonia.player.domain.model.PlaybackOptions
import org.harmonia.player.domain.repository.UserPreferencesRepository

/**
 * Applies Settings → Playback to the service's player (task 4.5): speed, volume levelling from
 * ReplayGain tags, and fades between songs. Runs on the main thread, like the player.
 */
@UnstableApi
class PlaybackOptionsBinder(
    private val player: ExoPlayer,
    private val gain: GainAudioProcessor,
    private val preferences: UserPreferencesRepository
) {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val handler = Handler(Looper.getMainLooper())

    private var options = PlaybackOptions()
    private var songLevel = 1f
    private var fadeInActive = false

    private val fadeTicker = object : Runnable {
        override fun run() {
            updateFade()
            if (player.isPlaying && options.fadeSeconds > 0) handler.postDelayed(this, FADE_TICK_MS)
        }
    }

    private val listener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            songLevel = levelingMultiplier(parseReplayGainTags(tagsOf(tracks)))
            applyLevel()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Fade in only when the last song played out into this one, not after a manual skip.
            fadeInActive = options.fadeSeconds > 0 && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            updateFade()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(fadeTicker)
            if (isPlaying) handler.post(fadeTicker)
        }

        override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            // A seek inside the song cancels a fade-in that was still running.
            if (reason == Player.DISCONTINUITY_REASON_SEEK) fadeInActive = false
            updateFade()
        }
    }

    fun start() {
        player.addListener(listener)
        scope.launch {
            preferences.observePlaybackOptions().collect { apply(it) }
        }
    }

    fun release() {
        handler.removeCallbacks(fadeTicker)
        player.removeListener(listener)
        scope.cancel()
    }

    private fun apply(newOptions: PlaybackOptions) {
        options = newOptions
        if (player.playbackParameters.speed != newOptions.speed) player.setPlaybackSpeed(newOptions.speed)
        applyLevel()
        if (newOptions.fadeSeconds == 0) fadeInActive = false
        updateFade()
        handler.removeCallbacks(fadeTicker)
        if (player.isPlaying) handler.post(fadeTicker)
    }

    private fun applyLevel() {
        gain.levelGain = if (options.volumeLeveling) songLevel else 1f
    }

    private fun updateFade() {
        val fadeMs = options.fadeSeconds * 1000L
        if (fadeMs <= 0) {
            gain.fadeGain = 1f
            return
        }
        // Fade out whenever another song will play on after this one (same album or not).
        val fadeOutAllowed = player.nextMediaItemIndex != C.INDEX_UNSET && player.repeatMode != Player.REPEAT_MODE_ONE
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        gain.fadeGain = SongFades.fadeGain(player.currentPosition, duration, fadeMs, fadeOutAllowed, fadeInActive)
    }

    private fun tagsOf(tracks: Tracks): List<Pair<String, String>> =
        tracks.groups
            .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
            .flatMap { group -> (0 until group.length).mapNotNull { group.getTrackFormat(it).metadata } }
            .flatMap { it.replayGainTagPairs() }

    private companion object {
        const val FADE_TICK_MS = 50L
    }
}
