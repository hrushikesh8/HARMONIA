package org.harmonia.player.design.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = Lavender,
    onPrimary = InkBackground,
    primaryContainer = InkContainer,
    onPrimaryContainer = InkTextPrimary,
    inversePrimary = DayLavender,
    secondary = LavenderBright,
    onSecondary = InkBackground,
    secondaryContainer = InkContainer,
    onSecondaryContainer = InkTextPrimary,
    tertiary = LavenderMist,
    onTertiary = InkBackground,
    background = InkBackground,
    onBackground = InkTextPrimary,
    surface = InkBackground,
    onSurface = InkTextPrimary,
    surfaceVariant = InkContainer,
    onSurfaceVariant = InkTextSecondary,
    surfaceTint = Lavender,
    surfaceBright = InkHighlight,
    surfaceDim = InkBackground,
    surfaceContainerLowest = InkBackground,
    surfaceContainerLow = InkSurface,
    surfaceContainer = InkSurface,
    surfaceContainerHigh = InkContainer,
    surfaceContainerHighest = InkHighlight,
    inverseSurface = PaperSurface,
    inverseOnSurface = PaperTextPrimary,
    outline = InkBorder,
    outlineVariant = InkBorder,
    error = StatusErrorCrimson,
    onError = InkBackground,
    scrim = Color.Black
)

private val LightColorScheme = lightColorScheme(
    primary = DayLavender,
    onPrimary = PaperSurface,
    primaryContainer = PaperContainer,
    onPrimaryContainer = PaperTextPrimary,
    inversePrimary = Lavender,
    secondary = DayLavenderDeep,
    onSecondary = PaperSurface,
    secondaryContainer = PaperContainer,
    onSecondaryContainer = PaperTextPrimary,
    tertiary = DayLavenderMist,
    onTertiary = PaperSurface,
    background = PaperBackground,
    onBackground = PaperTextPrimary,
    surface = PaperBackground,
    onSurface = PaperTextPrimary,
    surfaceVariant = PaperContainer,
    onSurfaceVariant = PaperTextSecondary,
    surfaceTint = DayLavender,
    surfaceBright = PaperSurface,
    surfaceDim = PaperContainer,
    surfaceContainerLowest = PaperSurface,
    surfaceContainerLow = PaperSurface,
    surfaceContainer = PaperSurface,
    surfaceContainerHigh = PaperContainer,
    surfaceContainerHighest = PaperBorder,
    inverseSurface = InkSurface,
    inverseOnSurface = InkTextPrimary,
    outline = PaperBorder,
    outlineVariant = PaperBorder,
    error = DayError,
    onError = PaperSurface,
    scrim = Color.Black
)

val LocalHarmoniaColors = androidx.compose.runtime.staticCompositionLocalOf { DarkHarmoniaSemanticColors }

object HarmoniaThemeTokens {
    val colors: HarmoniaSemanticColors
        @Composable
        get() = LocalHarmoniaColors.current

    val spacing: HarmoniaSpacing
        @Composable
        get() = LocalHarmoniaSpacing.current
}

@Composable
fun HarmoniaTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val semanticColors = if (darkTheme) DarkHarmoniaSemanticColors else LightHarmoniaSemanticColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = semanticColors.backgroundRoot.toArgb()
                window.navigationBarColor = semanticColors.backgroundRoot.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalHarmoniaColors provides semanticColors,
        LocalHarmoniaSpacing provides HarmoniaSpacing()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = remember(semanticColors) { harmoniaTypography(semanticColors) },
            shapes = HarmoniaShapes,
            content = content
        )
    }
}
