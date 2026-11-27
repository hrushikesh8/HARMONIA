package org.harmonia.player.design.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.harmonia.player.R

/**
 * Harmonia typography: Figtree everywhere.
 *
 * Figtree is a friendly geometric sans (SIL Open Font License, see third_party/figtree/OFL.txt),
 * bundled as one variable font file so it works offline and every weight comes from ~60 KB.
 * Numbers use tabular figures so timers and durations don't jitter while they count.
 * Text colours come from the active theme, so styles stay legible in light and dark mode.
 */
@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: FontWeight) = Font(
    resId = R.font.figtree_variable,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight))
)

val HarmoniaFontFamily = FontFamily(
    figtree(FontWeight.Normal),
    figtree(FontWeight.Medium),
    figtree(FontWeight.SemiBold),
    figtree(FontWeight.Bold),
    figtree(FontWeight.ExtraBold),
    figtree(FontWeight.Black)
)

private const val TABULAR_FIGURES = "tnum"

private fun style(
    weight: FontWeight,
    size: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    color: androidx.compose.ui.graphics.Color
) = TextStyle(
    fontFamily = HarmoniaFontFamily,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = letterSpacing,
    color = color,
    fontFeatureSettings = TABULAR_FIGURES
)

fun harmoniaTypography(colors: HarmoniaSemanticColors) = Typography(
    // Big screen greetings and hero titles (album / artist / playlist headers)
    displayLarge = style(FontWeight.ExtraBold, 30.sp, 36.sp, (-0.6).sp, colors.textPrimary),
    displayMedium = style(FontWeight.ExtraBold, 26.sp, 32.sp, (-0.5).sp, colors.textPrimary),
    displaySmall = style(FontWeight.Bold, 24.sp, 30.sp, (-0.4).sp, colors.textPrimary),
    headlineLarge = style(FontWeight.Bold, 24.sp, 30.sp, (-0.4).sp, colors.textPrimary),
    headlineMedium = style(FontWeight.Bold, 22.sp, 28.sp, (-0.3).sp, colors.textPrimary),
    headlineSmall = style(FontWeight.Bold, 20.sp, 26.sp, (-0.2).sp, colors.textPrimary),
    // Screen titles and section headers
    titleLarge = style(FontWeight.Bold, 21.sp, 27.sp, (-0.3).sp, colors.textPrimary),
    // Track titles in lists, card titles
    titleMedium = style(FontWeight.SemiBold, 16.sp, 22.sp, 0.sp, colors.textPrimary),
    titleSmall = style(FontWeight.SemiBold, 14.sp, 20.sp, 0.sp, colors.textPrimary),
    // Body and descriptive text
    bodyLarge = style(FontWeight.Normal, 15.sp, 22.sp, 0.1.sp, colors.textPrimary),
    // Artist names, secondary metadata
    bodyMedium = style(FontWeight.Normal, 13.sp, 18.sp, 0.1.sp, colors.textSecondary),
    // Years, track counts, durations
    bodySmall = style(FontWeight.Normal, 12.sp, 16.sp, 0.15.sp, colors.textSecondary),
    // Buttons (Material buttons read labelLarge). No fixed colour, so each button's own
    // content colour wins (white on lavender, lavender on text buttons, and so on).
    labelLarge = style(FontWeight.Bold, 14.sp, 20.sp, 0.1.sp, androidx.compose.ui.graphics.Color.Unspecified),
    // Tabs, pills, chips
    labelMedium = style(FontWeight.SemiBold, 12.sp, 16.sp, 0.2.sp, colors.textPrimary),
    // Small tags: formats (FLAC, MP3), timestamps, eyebrow labels
    labelSmall = style(FontWeight.Bold, 11.sp, 14.sp, 0.4.sp, colors.technicalAccent)
)
