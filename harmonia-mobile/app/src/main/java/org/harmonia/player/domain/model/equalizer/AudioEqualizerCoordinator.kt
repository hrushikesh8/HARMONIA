package org.harmonia.player.domain.model.equalizer

import kotlinx.coroutines.flow.StateFlow

/**
 * Coordinator interface exposed to the application and presentation layers.
 * Provides observable state and serialized commands to control the active equalizer.
 */
interface AudioEqualizerCoordinator {
    /**
     * Observable operational state of the equalizer.
     */
    val state: StateFlow<EqualizerState>

    /**
     * Updates the user's master enable intent and applies it to the active effect if available.
     *
     * @return true if successfully processed.
     */
    suspend fun setEnabled(enabled: Boolean): Boolean

    /**
     * Sets the gain level of a specific band in millibels (100 mB = 1 dB).
     *
     * @param band 0-based band index.
     * @param levelMb Target gain in millibels.
     * @return true if applied to an active effect with control.
     */
    suspend fun setBandLevel(band: Short, levelMb: Short): Boolean

    /**
     * Applies a hardware/OEM preset.
     *
     * @param presetIndex 0-based preset index.
     * @return true if applied to an active effect with control.
     */
    suspend fun usePreset(presetIndex: Short): Boolean
}
