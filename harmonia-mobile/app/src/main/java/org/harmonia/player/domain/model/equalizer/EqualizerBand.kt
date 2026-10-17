package org.harmonia.player.domain.model.equalizer

/**
 * Represents a single frequency band supported by the hardware equalizer.
 *
 * @property index 0-based band index as reported by the device HAL.
 * @property centerFreqHz Center frequency of this band in Hertz (e.g. 60 Hz, 1000 Hz).
 * @property currentLevelMb Current gain level in millibels (100 mB = 1 dB).
 * @property minLevelMb Minimum supported gain level in millibels (typically -1500 mB).
 * @property maxLevelMb Maximum supported gain level in millibels (typically +1500 mB).
 */
data class EqualizerBand(
    val index: Short,
    val centerFreqHz: Int,
    val currentLevelMb: Short,
    val minLevelMb: Short,
    val maxLevelMb: Short
)
