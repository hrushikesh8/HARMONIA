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

@Composable
fun HarmoniaTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = ObsidianSpace.toArgb()
                window.navigationBarColor = ObsidianSpace.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = HarmoniaTypography,
        shapes = HarmoniaShapes,
        content = content
    )
}
