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
}
