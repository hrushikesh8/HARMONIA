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
 * - Text colors come from the active theme's semantic colors, so styles stay legible in light mode
 *   (dark: WarmSignalWhite primary, MutedStone secondary, AnalogWarmGold technical labels).
 */
fun harmoniaTypography(colors: HarmoniaSemanticColors) = Typography(
    // Large ambient headings, greeting titles
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = (-0.5).sp,
        color = colors.textPrimary
    ),
    // Screen titles (Home, Library, Now Playing)
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
        color = colors.textPrimary
    ),
    // Section headers, card titles, track titles in lists
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.sp,
        color = colors.textPrimary
    ),
    // Primary body content, descriptive text
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
        letterSpacing = 0.15.sp,
        color = colors.textPrimary
    ),
    // Artist names, secondary metadata
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.15.sp,
        color = colors.textSecondary
    ),
    // Album years, track counts, duration labels
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp,
        color = colors.textSecondary
    ),
    // Interactive buttons, tabs, category action pills
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.25.sp,
        color = colors.textPrimary
    ),
    // STRICTLY for technical audio parameters, timestamps, bitrates, sample rates, format pills
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
        color = colors.technicalAccent
    )
)
