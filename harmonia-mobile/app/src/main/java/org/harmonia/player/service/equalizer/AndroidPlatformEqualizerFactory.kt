package org.harmonia.player.service.equalizer

import android.media.audiofx.Equalizer
import org.harmonia.player.domain.model.equalizer.PlatformEqualizer
import org.harmonia.player.domain.model.equalizer.PlatformEqualizerFactory

/**
 * Concrete Android native implementation of [PlatformEqualizerFactory].
 *
 * Invariants:
 * 1. Audio session ID <= 0 is rejected immediately and returns null.
 * 2. Never attaches to the global output mix (session 0).
 * 3. Propagates expected recoverable native exceptions (e.g. [UnsupportedOperationException],
 *    [IllegalArgumentException], [RuntimeException]) to be properly diagnosed by the caller.
 */
class AndroidPlatformEqualizerFactory(
    private val equalizerCreator: (audioSessionId: Int) -> Equalizer = { sessionId -> Equalizer(0, sessionId) },
    private val adapterCreator: (Equalizer) -> PlatformEqualizer = { native -> AndroidPlatformEqualizer(native) }
) : PlatformEqualizerFactory {

    override fun create(audioSessionId: Int): PlatformEqualizer? {
        if (audioSessionId <= 0) {
            return null
        }
        val nativeEqualizer = equalizerCreator(audioSessionId)
        return try {
            adapterCreator(nativeEqualizer)
        } catch (e: Exception) {
            try {
                nativeEqualizer.release()
            } catch (cleanupEx: Exception) {
                e.addSuppressed(cleanupEx)
            }
            throw e
        }
    }
}

