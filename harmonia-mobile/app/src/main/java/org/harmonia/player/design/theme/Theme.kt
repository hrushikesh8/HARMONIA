package org.harmonia.player.design.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = AcousticTeal,
    onPrimary = ObsidianSpace,
    primaryContainer = StudioSlate,
    onPrimaryContainer = PureSignalWhite,
    secondary = HarmonicViolet,
    onSecondary = PureSignalWhite,
    secondaryContainer = StudioSlate,
    onSecondaryContainer = PureSignalWhite,
    tertiary = AcousticTeal,
    background = ObsidianSpace,
    onBackground = PureSignalWhite,
    surface = AcousticCharcoal,
    onSurface = PureSignalWhite,
    surfaceVariant = StudioSlate,
    onSurfaceVariant = MutedSlate,
    outline = BorderSlate,
    error = StatusError,
    onError = PureSignalWhite
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
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Base M3 scheme uses dark for now

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
