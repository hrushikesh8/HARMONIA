package org.harmonia.player.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Harmonia Official Brand Color Palette
 * Defined in Harmonia Brand Identity & Design System
 */
val ObsidianSpace = Color(0xFF090B10)
val AcousticCharcoal = Color(0xFF141721)
val StudioSlate = Color(0xFF1E2333)
val BorderSlate = Color(0xFF2A3042)

val AcousticTeal = Color(0xFF00D2B4)
val HarmonicViolet = Color(0xFF6E56CF)

val PureSignalWhite = Color(0xFFF0F3F8)
val MutedSlate = Color(0xFF8E95A5)
val SubtleSlate = Color(0xFF565E70)

val StatusSuccess = Color(0xFF00D2B4)
val StatusWarning = Color(0xFFFFB020)
val StatusError = Color(0xFFFF4B4B)
val OfflineBadgeColor = Color(0xFF00D2B4)
val StreamingBadgeColor = Color(0xFF8E95A5)

/**
 * Harmonia Daylight / Light Theme Tokens (Proposed & Verified Contrast)
 */
val SlateDay = Color(0xFFF4F6F9)
val PureDayWhite = Color(0xFFFFFFFF)
val CloudContainer = Color(0xFFE8ECF2)
val MistBorder = Color(0xFFCBD5E1)
val DeepTeal = Color(0xFF006064)
val PlumViolet = Color(0xFF5E35B1)
val InkBlack = Color(0xFF0F172A)
val SlateTextSecondary = Color(0xFF475569)
val DimTextDisabled = Color(0xFF94A3B8)
val DeepAmberWarning = Color(0xFFB45309)
val DeepRedError = Color(0xFFB91C1C)

/**
 * Semantic Color Container for Harmonia Theme Tokens.
 * Provides explicit, architecturally named roles across Dark and Light themes.
 */
data class HarmoniaSemanticColors(
    val backgroundRoot: Color,
    val surfaceElevated: Color,
    val surfaceContainer: Color,
    val borderStructural: Color,
    val primarySignal: Color,
    val secondarySignal: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val isDark: Boolean
)

val DarkHarmoniaSemanticColors = HarmoniaSemanticColors(
    backgroundRoot = ObsidianSpace,
    surfaceElevated = AcousticCharcoal,
    surfaceContainer = StudioSlate,
    borderStructural = BorderSlate,
    primarySignal = AcousticTeal,
    secondarySignal = HarmonicViolet,
    textPrimary = PureSignalWhite,
    textSecondary = MutedSlate,
    textDisabled = SubtleSlate,
    statusSuccess = StatusSuccess,
    statusWarning = StatusWarning,
    statusError = StatusError,
    isDark = true
)

val LightHarmoniaSemanticColors = HarmoniaSemanticColors(
    backgroundRoot = SlateDay,
    surfaceElevated = PureDayWhite,
    surfaceContainer = CloudContainer,
    borderStructural = MistBorder,
    primarySignal = DeepTeal,
    secondarySignal = PlumViolet,
    textPrimary = InkBlack,
    textSecondary = SlateTextSecondary,
    textDisabled = DimTextDisabled,
    statusSuccess = DeepTeal,
    statusWarning = DeepAmberWarning,
    statusError = DeepRedError,
    isDark = false
)
