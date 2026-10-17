package org.harmonia.player.service.equalizer

import android.media.audiofx.Equalizer
import org.harmonia.player.domain.model.equalizer.PlatformEqualizer

/**
 * Concrete Android native implementation of [PlatformEqualizer] wrapping [android.media.audiofx.Equalizer].
 *
 * Invariants:
 * 1. Native handle is released exactly once upon [close].
 * 2. All operations after release throw [IllegalStateException] to prevent use-after-free.
 * 3. The native [android.media.audiofx.AudioEffect.OnControlStatusChangeListener] is cleared upon release.
 * 4. Stale or late callbacks delivered after release are immediately discarded.
 * 5. Does not fabricate fallback frequencies, presets, or band ranges.
 */
class AndroidPlatformEqualizer(
    private val nativeEqualizer: Equalizer
) : PlatformEqualizer {

    @Volatile
    private var isReleased = false

    @Volatile
    private var controlStatusListener: ((Boolean) -> Unit)? = null

    private fun checkNotReleased() {
        if (isReleased) {
            throw IllegalStateException("AndroidPlatformEqualizer has already been released")
        }
    }

    override val numberOfBands: Short
        get() {
            checkNotReleased()
            return nativeEqualizer.numberOfBands
        }

    override val bandLevelRange: ShortArray
        get() {
            checkNotReleased()
            return nativeEqualizer.bandLevelRange
        }

    override fun getCenterFreq(band: Short): Int {
        checkNotReleased()
        return nativeEqualizer.getCenterFreq(band)
    }

    override fun getBandLevel(band: Short): Short {
        checkNotReleased()
        return nativeEqualizer.getBandLevel(band)
    }

    override fun setBandLevel(band: Short, level: Short) {
        checkNotReleased()
        nativeEqualizer.setBandLevel(band, level)
    }

    override val numberOfPresets: Short
        get() {
            checkNotReleased()
            return nativeEqualizer.numberOfPresets
        }

    override fun getPresetName(preset: Short): String {
        checkNotReleased()
        return nativeEqualizer.getPresetName(preset)
    }

    override fun usePreset(preset: Short) {
        checkNotReleased()
        nativeEqualizer.usePreset(preset)
    }

    override val currentPreset: Short
        get() {
            checkNotReleased()
            return nativeEqualizer.currentPreset
        }

    override var enabled: Boolean
        get() {
            checkNotReleased()
            return nativeEqualizer.enabled
        }
        set(value) {
            checkNotReleased()
            nativeEqualizer.enabled = value
        }

    override fun setControlStatusListener(listener: (controlGranted: Boolean) -> Unit) {
        checkNotReleased()
        controlStatusListener = listener
        nativeEqualizer.setControlStatusListener { _, controlGranted ->
            if (!isReleased) {
                controlStatusListener?.invoke(controlGranted)
            }
        }
    }

    override fun close() {
        if (isReleased) return
        synchronized(this) {
            if (isReleased) return
            isReleased = true
            controlStatusListener = null
            var cleanupException: Exception? = null
            try {
                nativeEqualizer.setControlStatusListener(null)
            } catch (e: Exception) {
                cleanupException = e
            }
            try {
                nativeEqualizer.release()
            } catch (e: Exception) {
                if (cleanupException != null) {
                    cleanupException.addSuppressed(e)
                } else {
                    cleanupException = e
                }
            }
        }
    }
}

