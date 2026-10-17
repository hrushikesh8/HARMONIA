package org.harmonia.player.domain.model.equalizer

/**
 * Represents a hardware/OEM-defined equalizer preset.
 *
 * @property index 0-based preset index.
 * @property name Human-readable preset name reported by the device (e.g. "Normal", "Rock", "Jazz").
 */
data class EqualizerPreset(
    val index: Short,
    val name: String
)
