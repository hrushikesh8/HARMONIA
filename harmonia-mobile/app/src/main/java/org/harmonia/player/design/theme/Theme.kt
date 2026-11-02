package org.harmonia.player.design.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SonicAmber,
    onPrimary = ObsidianAbyss,
    primaryContainer = ObsidianContainer,
    onPrimaryContainer = WarmSignalWhite,
    secondary = CeruleanTechnical,
    onSecondary = ObsidianAbyss,
    secondaryContainer = ObsidianContainer,
    onSecondaryContainer = WarmSignalWhite,
    tertiary = AnalogWarmGold,
    background = ObsidianAbyss,
    onBackground = WarmSignalWhite,
    surface = ObsidianSurface,
    onSurface = WarmSignalWhite,
    surfaceVariant = ObsidianContainer,
    onSurfaceVariant = MutedStone,
    outline = ObsidianBorder,
    error = StatusErrorCrimson,
    onError = WarmSignalWhite
)

private val LightColorScheme = lightColorScheme(
    primary = DaySonicAmber,
    onPrimary = PureDayWhite,
    primaryContainer = DayContainer,
    onPrimaryContainer = DayInkPrimary,
    secondary = DayCeruleanTechnical,
    onSecondary = PureDayWhite,
    secondaryContainer = DayContainer,
    onSecondaryContainer = DayInkPrimary,
    tertiary = DayCeruleanTechnical,
    background = ParchmentDay,
    onBackground = DayInkPrimary,
    surface = PureDayWhite,
    onSurface = DayInkPrimary,
    surfaceVariant = DayContainer,
    onSurfaceVariant = DaySlateSecondary,
    outline = DayBorder,
    error = DayCrimsonError,
    onError = PureDayWhite
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
            typography = HarmoniaTypography,
            shapes = HarmoniaShapes,
            content = content
        )
    }
}
