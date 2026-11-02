package org.harmonia.player.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Harmonia Official Typography Hierarchy — Direction 2: Acoustic Sanctuary
 *
 * Principles:
 * - Natural, legible Sans-Serif hierarchy for content and navigation.
 * - Monospace strictly reserved for numerical values, timers, bitrates, and technical formats (FLAC/WAV).
 * - Calibrated optical letter spacing and line heights for maximum legibility.
 * - Primary text uses WarmSignalWhite (reduced eye fatigue), secondary uses MutedStone.
 */
val HarmoniaTypography = Typography(
    // Large ambient headings, greeting titles
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        color = WarmSignalWhite
    ),
    // Screen titles (Home, Library, Now Playing)
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        color = WarmSignalWhite
    ),
    // Section headers, card titles, track titles in lists
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        color = WarmSignalWhite
    ),
    // Primary body content, descriptive text
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        color = WarmSignalWhite
    ),
    // Artist names, secondary metadata
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
        color = MutedStone
    ),
    // Album years, track counts, duration labels
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        color = MutedStone
    ),
    // Interactive buttons, tabs, category action pills
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
        color = WarmSignalWhite
    ),
    // STRICTLY for technical audio parameters, timestamps, bitrates, sample rates, format pills
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        color = AnalogWarmGold
    )
)
