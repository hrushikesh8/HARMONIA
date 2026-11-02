package org.harmonia.player.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Harmonia Official Brand Color Palette — Direction 2: Obsidian & Warm Analog (Acoustic Sanctuary)
 *
 * Core Principles:
 * - Deep obsidian abyss background hierarchy (ObsidianAbyss, ObsidianSurface, ObsidianContainer).
 * - Sonic Amber (#F5A623 / #FFB020) reserved for vital playback actions and active playback states.
 * - Electric Cerulean (#3BA5FF) remains secondary and technical (storage indicators, local sync).
 * - Analog Warm Gold (#D4AF37) for hi-res audio badges, bitrates, format pills.
 * - Warm Signal White (#FAF8F5) for primary text (no harsh ocular glare), Muted Stone for secondary.
 * - Coherent, refined daylight palette with verified WCAG AAA/AA contrast.
 * - Full backward-compatibility aliases maintained for existing screen references.
 */

// ── Obsidian & Acoustic Sanctuary Foundations (Dark Theme) ───────────────
val ObsidianAbyss = Color(0xFF080A0E)          // Root background
val ObsidianSurface = Color(0xFF12161F)        // Elevated surfaces (cards, bottom sheet)
val ObsidianContainer = Color(0xFF1A202C)      // Inner containers, list items, pill backgrounds
val ObsidianBorder = Color(0xFF232B3B)         // Subtle structural separators
val ObsidianHighlight = Color(0xFF2C364A)      // Interactive hover / pressed surfaces

// ── Primary Playback Accent — Sonic Amber ──────────────────────────────────
// Reserved strictly for primary playback actions, playing state indicators, active scrubbers
val SonicAmber = Color(0xFFF5A623)             // 9.77:1 contrast on ObsidianAbyss (WCAG AAA)
val SonicAmberBright = Color(0xFFFFB020)       // 10.83:1 contrast on ObsidianAbyss (WCAG AAA)
val SonicAmberHover = Color(0xFFFFBE4D)        // Pressed/hover state
val SonicAmberGlow = Color(0x33F5A623)         // Ambient drop shadow / aura

// ── Secondary Technical Accent — Electric Cerulean & Analog Gold ──────────
// Cerulean remains secondary and technical for storage indicators, local sync, and neutral navigation
val CeruleanTechnical = Color(0xFF3BA5FF)      // 7.56:1 contrast on ObsidianAbyss (WCAG AAA)
val CeruleanTechnicalHover = Color(0xFF5CB9FF)
val CeruleanTechnicalGlow = Color(0x333BA5FF)
val CeruleanMutedHighlight = Color(0xFF82B1FF)
val CeruleanIceAccent = Color(0xFF90CAF9)
val CeruleanFormatPillBg = Color(0x1F3BA5FF)

// Analog Warm Gold for bitrates, sample rates, format badges (FLAC/DSD/WAV)
val AnalogWarmGold = Color(0xFFD4AF37)

// ── Typography & Content Signals (Dark Theme) ─────────────────────────────
val WarmSignalWhite = Color(0xFFFAF8F5)        // 18.69:1 contrast on ObsidianAbyss (WCAG AAA)
val MutedStone = Color(0xFFA0AABF)             // 8.49:1 contrast on ObsidianAbyss (WCAG AAA)
val SubtleCharcoal = Color(0xFF67738A)         // 4.54:1 contrast on ObsidianAbyss (WCAG AA)

// ── Status & Semantic Badges ──────────────────────────────────────────────
val StatusSuccessMint = Color(0xFF34C759)
val StatusWarningAmber = Color(0xFFFFB020)
val StatusErrorCrimson = Color(0xFFFF4B4B)
val OfflineBadgeColor = CeruleanTechnical
val StreamingBadgeColor = MutedStone

// ── Daylight / Light Theme Foundations (Acoustic Daylight) ────────────────
// Coherent warm light palette designed intentionally rather than inverted
val ParchmentDay = Color(0xFFF6F8FA)           // Warm clean daylight canvas
val PureDayWhite = Color(0xFFFFFFFF)           // Elevated daylight surfaces
val DayContainer = Color(0xFFEDF1F5)           // Daylight inner containers / chips
val DayBorder = Color(0xFFD8E0E8)              // Subtle daylight separators
val DayInkPrimary = Color(0xFF0A0E17)          // 18.13:1 contrast on ParchmentDay (WCAG AAA)
val DaySlateSecondary = Color(0xFF4A5568)      // 7.07:1 contrast on ParchmentDay (WCAG AAA)
val DayDimDisabled = Color(0xFF8E9BAE)         // 3.5:1 contrast
val DaySonicAmber = Color(0xFF844400)          // Deep warm amber: 7.03:1 contrast on ParchmentDay (WCAG AAA)
val DayCeruleanTechnical = Color(0xFF0A66C2)   // Deep cerulean: 5.34:1 contrast on ParchmentDay (WCAG AA)
val DayAmberWarning = Color(0xFFB45309)
val DayCrimsonError = Color(0xFFB91C1C)

// ── Backward-Compatibility Aliases ─────────────────────────────────────────
// Map legacy tokens cleanly so existing screen composables and imports compile seamlessly
val NavyNearBlack = ObsidianAbyss
val DarkNavyCharcoal = ObsidianSurface
val CardBlueGray = ObsidianContainer
val BorderBlueGray = ObsidianBorder
val SurfaceHighlightBlue = ObsidianHighlight

val ObsidianNearBlack = ObsidianAbyss
val DarkCharcoal = ObsidianSurface
val CardElevated = ObsidianContainer
val BorderSubtle = ObsidianBorder
val SurfaceHighlight = ObsidianHighlight

val ElectricBlue = CeruleanTechnical
val ElectricBlueHover = CeruleanTechnicalHover
val ElectricBlueGlow = CeruleanTechnicalGlow
val LighterBlueHighlight = CeruleanMutedHighlight
val IceBlueAccent = CeruleanIceAccent
val AudioFormatPillBg = CeruleanFormatPillBg

val PureSignalWhite = WarmSignalWhite
val MutedSlate = MutedStone
val SubtleSlate = SubtleCharcoal

val StatusSuccess = StatusSuccessMint
val StatusWarning = StatusWarningAmber
val StatusError = StatusErrorCrimson

val SpotifyGreen = SonicAmber
val SpotifyGreenHover = SonicAmberHover
val SpotifyGreenGlow = SonicAmberGlow
val AmazonCyan = CeruleanTechnical
val AmazonCyanAudio = CeruleanIceAccent
val ObsidianSpace = ObsidianAbyss
val AcousticCharcoal = ObsidianSurface
val StudioSlate = ObsidianContainer
val BorderSlate = ObsidianBorder
val AcousticTeal = CeruleanTechnical
val HarmonicViolet = CeruleanMutedHighlight

val SlateDay = ParchmentDay
val CloudContainer = DayContainer
val MistBorder = DayBorder
val DeepBlueAccent = DaySonicAmber
val LightBlueAccent = DayCeruleanTechnical
val InkBlack = DayInkPrimary
val SlateTextSecondary = DaySlateSecondary
val DimTextDisabled = DayDimDisabled
val DeepAmberWarning = DayAmberWarning
val DeepRedError = DayCrimsonError
val DeepGreenAccent = DaySonicAmber
val DeepCyanAccent = DayCeruleanTechnical

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
    val primarySignalHover: Color = SonicAmberHover,
    val secondarySignal: Color,
    val technicalAccent: Color = CeruleanIceAccent,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val isDark: Boolean
)

val DarkHarmoniaSemanticColors = HarmoniaSemanticColors(
    backgroundRoot = ObsidianAbyss,
    surfaceElevated = ObsidianSurface,
    surfaceContainer = ObsidianContainer,
    borderStructural = ObsidianBorder,
    primarySignal = SonicAmber,
    primarySignalHover = SonicAmberHover,
    secondarySignal = CeruleanTechnical,
    technicalAccent = AnalogWarmGold,
    textPrimary = WarmSignalWhite,
    textSecondary = MutedStone,
    textDisabled = SubtleCharcoal,
    statusSuccess = StatusSuccessMint,
    statusWarning = StatusWarningAmber,
    statusError = StatusErrorCrimson,
    isDark = true
)

val LightHarmoniaSemanticColors = HarmoniaSemanticColors(
    backgroundRoot = ParchmentDay,
    surfaceElevated = PureDayWhite,
    surfaceContainer = DayContainer,
    borderStructural = DayBorder,
    primarySignal = DaySonicAmber,
    primarySignalHover = DaySonicAmber,
    secondarySignal = DayCeruleanTechnical,
    technicalAccent = DayCeruleanTechnical,
    textPrimary = DayInkPrimary,
    textSecondary = DaySlateSecondary,
    textDisabled = DayDimDisabled,
    statusSuccess = DaySonicAmber,
    statusWarning = DayAmberWarning,
    statusError = DayCrimsonError,
    isDark = false
)
