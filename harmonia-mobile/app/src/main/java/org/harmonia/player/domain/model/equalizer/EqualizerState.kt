package org.harmonia.player.domain.model.equalizer

/**
 * Observable operational state of the platform equalizer.
 */
sealed interface EqualizerState {
    /**
     * No active audio session (ID <= 0, playback stopped), or effect creation failed.
     * Audio playback proceeds completely unaltered.
     *
     * @property failureReason Optional diagnostic failure reason if creation or operation failed.
     */
    data class NoSession(val failureReason: EqualizerFailureReason? = null) : EqualizerState

    /**
     * Attached to an active audio session, but the effect is bypassed/disabled.
     */
    data class AvailableDisabled(
        val bandLevelRange: IntRange,
        val bands: List<EqualizerBand>,
        val presets: List<EqualizerPreset>
    ) : EqualizerState

    /**
     * Attached to an active audio session, effect enabled and actively filtering audio,
     * with effect control established by Harmonia.
     */
    data class EnabledWithControl(
        val bandLevelRange: IntRange,
        val bands: List<EqualizerBand>,
        val presets: List<EqualizerPreset>,
        val currentPresetIndex: Short
    ) : EqualizerState

    /**
     * Attached to an active audio session, but effect control was acquired by another
     * audio process or higher-priority application.
     *
     * @property lastKnownEnabled Preserves whether the effect was active before control was lost.
     */
    data class ControlLost(
        val lastKnownEnabled: Boolean,
        val bandLevelRange: IntRange,
        val bands: List<EqualizerBand>,
        val presets: List<EqualizerPreset>
    ) : EqualizerState
}
