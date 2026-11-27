package org.harmonia.player.design.theme

import androidx.compose.ui.graphics.Color

/**
 * Harmonia colour palette: "Soft Lavender".
 *
 * - Dark theme follows the Spotify approach: a neutral near-black canvas, a few grey
 *   surface steps, white/grey text, and ONE accent colour (lavender) reserved for
 *   playback actions, progress, the active tab and the playing track.
 * - Light theme is designed, not inverted: white surfaces with a faint lavender-grey
 *   tint and a deeper lavender so the accent passes contrast on white.
 * - Contrast ratios are noted next to each token (WCAG 2.1).
 */

// ── Dark theme neutrals ────────────────────────────────────────────────────
val InkBackground = Color(0xFF121212)          // Root canvas
val InkSurface = Color(0xFF181818)             // Cards, sheets, mini player
val InkContainer = Color(0xFF242424)           // Chips, list containers, pills
val InkBorder = Color(0xFF2E2E2E)              // Hairline separators
val InkHighlight = Color(0xFF333333)           // Pressed / selected surfaces

// ── Accent: Soft Lavender ──────────────────────────────────────────────────
val Lavender = Color(0xFFA99BFF)                // 7.87:1 on InkBackground (AAA)
val LavenderBright = Color(0xFFC4B9FF)          // 10.46:1 on InkBackground, links / secondary accent
val LavenderPressed = Color(0xFFBDB1FF)
val LavenderGlow = Color(0x33A99BFF)
val LavenderMist = Color(0xFFBDB6E0)            // 9.75:1, technical labels (format, bitrate)
val LavenderTint = Color(0x26A99BFF)            // Soft accent wash behind pills

// ── Dark theme text ────────────────────────────────────────────────────────
val InkTextPrimary = Color(0xFFFFFFFF)          // 18.73:1 on InkBackground
val InkTextSecondary = Color(0xFFB3B3B3)        // 8.93:1 on InkBackground
val InkTextDisabled = Color(0xFF6A6A6A)         // 3.46:1, disabled only

// ── Status ─────────────────────────────────────────────────────────────────
val StatusSuccessMint = Color(0xFF4ADE80)
val StatusWarningAmber = Color(0xFFFFB547)       // 10.66:1 on InkBackground
val StatusErrorCrimson = Color(0xFFFF6B6B)       // 6.75:1 on InkBackground

// ── Light theme ────────────────────────────────────────────────────────────
val PaperBackground = Color(0xFFF7F6FB)         // Canvas with a faint lavender tint
val PaperSurface = Color(0xFFFFFFFF)            // Cards, sheets
val PaperContainer = Color(0xFFEEECF5)          // Chips, list containers
val PaperBorder = Color(0xFFE0DDEA)             // Hairline separators
val PaperTextPrimary = Color(0xFF121212)        // 17.7:1 on PaperBackground
val PaperTextSecondary = Color(0xFF5E5C66)      // 6.11:1 on PaperBackground
val PaperTextDisabled = Color(0xFF9A98A3)
val DayLavender = Color(0xFF5B4BC4)             // 6.01:1 on PaperBackground (AA)
val DayLavenderDeep = Color(0xFF4E3FB0)         // 7.89:1 on white, links
val DayLavenderMist = Color(0xFF5E5791)         // 6.47:1, technical labels
val DayWarning = Color(0xFFA35A00)              // 5.22:1 on white
val DayError = Color(0xFFC62828)                // 5.62:1 on white
val DaySuccess = Color(0xFF1B7F3B)

// ── Legacy names (pre-lavender) ────────────────────────────────────────────
// Older code and docs refer to these; they now resolve to the lavender palette.
val ObsidianAbyss = InkBackground
val ObsidianSurface = InkSurface
val ObsidianContainer = InkContainer
val ObsidianBorder = InkBorder
val ObsidianHighlight = InkHighlight
val SonicAmber = Lavender
val SonicAmberBright = LavenderBright
val SonicAmberHover = LavenderPressed
val SonicAmberGlow = LavenderGlow
val CeruleanTechnical = LavenderBright
val CeruleanTechnicalHover = LavenderPressed
val CeruleanTechnicalGlow = LavenderGlow
val CeruleanMutedHighlight = LavenderBright
val CeruleanIceAccent = LavenderMist
val CeruleanFormatPillBg = LavenderTint
val AnalogWarmGold = LavenderMist
val WarmSignalWhite = InkTextPrimary
val MutedStone = InkTextSecondary
val SubtleCharcoal = InkTextDisabled
val OfflineBadgeColor = LavenderBright
val StreamingBadgeColor = InkTextSecondary
val ParchmentDay = PaperBackground
val PureDayWhite = PaperSurface
val DayContainer = PaperContainer
val DayBorder = PaperBorder
val DayInkPrimary = PaperTextPrimary
val DaySlateSecondary = PaperTextSecondary
val DayDimDisabled = PaperTextDisabled
val DaySonicAmber = DayLavender
val DayCeruleanTechnical = DayLavenderDeep
val DayAmberWarning = DayWarning
val DayCrimsonError = DayError

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
val InkBlack = PaperTextPrimary
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
    val primarySignalHover: Color = LavenderPressed,
    val secondarySignal: Color,
    val technicalAccent: Color = LavenderMist,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val isDark: Boolean
)

val DarkHarmoniaSemanticColors = HarmoniaSemanticColors(
    backgroundRoot = InkBackground,
    surfaceElevated = InkSurface,
    surfaceContainer = InkContainer,
    borderStructural = InkBorder,
    primarySignal = Lavender,
    primarySignalHover = LavenderPressed,
    secondarySignal = LavenderBright,
    technicalAccent = LavenderMist,
    textPrimary = InkTextPrimary,
    textSecondary = InkTextSecondary,
    textDisabled = InkTextDisabled,
    statusSuccess = StatusSuccessMint,
    statusWarning = StatusWarningAmber,
    statusError = StatusErrorCrimson,
    isDark = true
)

val LightHarmoniaSemanticColors = HarmoniaSemanticColors(
    backgroundRoot = PaperBackground,
    surfaceElevated = PaperSurface,
    surfaceContainer = PaperContainer,
    borderStructural = PaperBorder,
    primarySignal = DayLavender,
    primarySignalHover = DayLavenderDeep,
    secondarySignal = DayLavenderDeep,
    technicalAccent = DayLavenderMist,
    textPrimary = PaperTextPrimary,
    textSecondary = PaperTextSecondary,
    textDisabled = PaperTextDisabled,
    statusSuccess = DaySuccess,
    statusWarning = DayWarning,
    statusError = DayError,
    isDark = false
)
