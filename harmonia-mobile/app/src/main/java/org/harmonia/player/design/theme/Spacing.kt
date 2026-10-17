package org.harmonia.player.design.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Harmonia Official Spacing Scale & Layout Grid.
 * Based on 8dp grid with 4dp sub-units.
 */
data class HarmoniaSpacing(
    val xSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val standard: Dp = 16.dp,
    val large: Dp = 24.dp,
    val xLarge: Dp = 32.dp,
    val bottomScaffold: Dp = 96.dp
)

val LocalHarmoniaSpacing = androidx.compose.runtime.staticCompositionLocalOf { HarmoniaSpacing() }
