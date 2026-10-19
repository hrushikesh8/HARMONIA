package org.harmonia.player.service.equalizer

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Service-owned bridge connecting ExoPlayer audio session events to [DefaultAudioEffectManager].
 *
 * Responsibilities:
 * 1. Attaches an [AnalyticsListener] to monitor [AnalyticsListener.onAudioSessionIdChanged].
 * 2. Queries initial player audio session ID immediately to avoid missing pre-registered sessions.
 * 3. Enqueues session events into a strictly FIFO serialized channel to prevent out-of-order execution
 *    or race conditions between initial session inspection and subsequent session changes.
 * 4. Ensures deterministic teardown: unregisters listener, cancels scope, and closes manager.
 */
class PlaybackServiceEqualizerBridge(
    private val audioEffectManager: DefaultAudioEffectManager,
    parentScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val bridgeScope = CoroutineScope(parentScope.coroutineContext + SupervisorJob())
    private val sessionChannel = Channel<SessionEvent>(Channel.UNLIMITED)
    private var analyticsListener: AnalyticsListener? = null
    private var listenerRemover: ((AnalyticsListener) -> Unit)? = null

    private var latestEnqueuedGeneration: Long = 0L
    private var latestProcessedGeneration: Long = 0L

    @Volatile
    private var isDetached = false

    val isClosed: Boolean
        get() = isDetached

    internal data class SessionEvent(
        val generation: Long,
        val sessionId: Int
    )

    init {
        bridgeScope.launch {
            for (event in sessionChannel) {
                if (isDetached) break

                // Drain any newer pending events in the channel so we only process the latest observation
                var latestEvent = event
                while (true) {
                    val next = sessionChannel.tryReceive().getOrNull() ?: break
                    if (next.generation > latestEvent.generation) {
                        latestEvent = next
                    }
                }

                if (latestEvent.generation <= latestProcessedGeneration) {
                    continue // Drop obsolete generation
                }
                latestProcessedGeneration = latestEvent.generation
                audioEffectManager.onAudioSessionIdChanged(latestEvent.sessionId)
            }
        }
    }

    /**
     * Attaches the bridge to the authoritative [ExoPlayer].
     */
    @OptIn(UnstableApi::class)
    fun attachToPlayer(player: ExoPlayer) {
        attachToSessionSource(
            currentAudioSessionId = player.audioSessionId,
            addListener = { listener -> player.addAnalyticsListener(listener) },
            removeListener = { listener -> player.removeAnalyticsListener(listener) }
        )
    }

    /**
     * Internal attach method supporting test seams without requiring full native ExoPlayer stubbing.
     */
    @OptIn(UnstableApi::class)
    internal fun attachToSessionSource(
        currentAudioSessionId: Int,
        addListener: (AnalyticsListener) -> Unit,
        removeListener: (AnalyticsListener) -> Unit
    ) {
        synchronized(this) {
            if (isDetached) return
            listenerRemover = removeListener

            // 1. Initial session snapshot is assigned the current baseline generation
            if (currentAudioSessionId != C.AUDIO_SESSION_ID_UNSET && currentAudioSessionId > 0) {
                val gen = ++latestEnqueuedGeneration
                sessionChannel.trySend(SessionEvent(gen, currentAudioSessionId))
            }

            // 2. Listener is registered SECOND so all subsequent callbacks receive strictly higher generation numbers
            val listener = object : AnalyticsListener {
                override fun onAudioSessionIdChanged(
                    eventTime: AnalyticsListener.EventTime,
                    audioSessionId: Int
                ) {
                    synchronized(this@PlaybackServiceEqualizerBridge) {
                        if (isDetached) return
                        val gen = ++latestEnqueuedGeneration
                        sessionChannel.trySend(SessionEvent(gen, audioSessionId))
                    }
                }
            }
            analyticsListener = listener
            addListener(listener)
        }
    }

    /**
     * Deterministically tears down the bridge: unregisters listeners, stops accepting
     * callbacks, cancels pending bridge processing, and closes the audio effect manager.
     */
    fun detachAndRelease(player: ExoPlayer?) {
        if (isDetached) return
        synchronized(this) {
            if (isDetached) return
            isDetached = true

            analyticsListener?.let { listener ->
                listenerRemover?.invoke(listener)
                player?.removeAnalyticsListener(listener)
                analyticsListener = null
            }
            listenerRemover = null

            sessionChannel.close()
            bridgeScope.cancel()
            audioEffectManager.close()
        }
    }
}


