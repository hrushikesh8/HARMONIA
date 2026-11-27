package org.harmonia.player.domain.model.equalizer

/**
 * Provides access to the user's master enable intent.
 * Decouples user intent from transient runtime audio availability.
 */
interface EqualizerUserIntentSource {
    /**
     * Returns true if the user desires the equalizer to be active when available.
     */
    fun isEqualizerEnabled(): Boolean

    /**
     * Persists or updates the user's master enable intent.
     */
    fun setEqualizerEnabled(enabled: Boolean)

    /**
     * Observes the user's master enable intent reactively.
     */
    fun observeEqualizerEnabled(): kotlinx.coroutines.flow.Flow<Boolean> =
        kotlinx.coroutines.flow.flowOf(isEqualizerEnabled())

    /** Band layout last reported by this phone's equalizer, or null if it has never run. */
    fun getLastKnownLayout(): EqualizerLayout? = null
    fun setLastKnownLayout(layout: EqualizerLayout) {}

    /** Your band levels (millibels) keyed by centre frequency; re-applied when playback starts. */
    fun getCustomBandLevels(): Map<Int, Short> = emptyMap()
    fun setCustomBandLevels(levels: Map<Int, Short>) {}

    /** Presets you saved yourself, in the order you created them. */
    fun getUserPresets(): List<UserEqualizerPreset> = emptyList()
    fun setUserPresets(presets: List<UserEqualizerPreset>) {}
}
