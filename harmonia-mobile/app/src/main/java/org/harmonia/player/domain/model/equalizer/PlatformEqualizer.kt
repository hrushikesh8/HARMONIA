package org.harmonia.player.domain.model.equalizer

/**
 * Fakeable abstraction over Android's native android.media.audiofx.Equalizer.
 * Provides safe wrapping and deterministic JVM mocking without touching Android native handles.
 */
interface PlatformEqualizer : AutoCloseable {
    /** Number of frequency bands supported by the hardware. */
    val numberOfBands: Short

    /** Level range in millibels: [minLevelMb, maxLevelMb]. Typically [-1500, 1500]. */
    val bandLevelRange: ShortArray

    /** Returns center frequency of the given band in milliHertz (mHz). */
    fun getCenterFreq(band: Short): Int

    /** Returns current gain level of the given band in millibels (mB). */
    fun getBandLevel(band: Short): Short

    /** Sets gain level of the given band in millibels (mB). */
    fun setBandLevel(band: Short, level: Short)

    /** Number of device-defined presets available. */
    val numberOfPresets: Short

    /** Returns human-readable name of the given preset index. */
    fun getPresetName(preset: Short): String

    /** Applies the given preset. */
    fun usePreset(preset: Short)

    /** Returns the currently active preset index, or -1 if custom band levels are set. */
    val currentPreset: Short

    /** Active/bypassed status of the effect. */
    var enabled: Boolean

    /** Registers listener for native effect control changes. */
    fun setControlStatusListener(listener: (controlGranted: Boolean) -> Unit)
}

/**
 * Factory contract for instantiating PlatformEqualizer instances for an audio session.
 */
interface PlatformEqualizerFactory {
    /**
     * Attempts to create a platform equalizer for the specified positive audio session ID.
     * Returns null if creation fails or the hardware does not support effects.
     */
    fun create(audioSessionId: Int): PlatformEqualizer?
}
