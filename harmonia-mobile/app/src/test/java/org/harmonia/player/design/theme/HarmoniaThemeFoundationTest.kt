package org.harmonia.player.design.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Validates the mathematical contrast ratios of Harmonia's design foundation tokens
 * according to WCAG 2.1 relative luminance standards.
 *
 * Formula:
 * L = 0.2126 * R + 0.7152 * G + 0.0722 * B
 * where each channel is converted to linear space:
 * c = if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055) ^ 2.4
 *
 * Contrast ratio = (L1 + 0.05) / (L2 + 0.05) where L1 is lighter and L2 is darker.
 */
class HarmoniaThemeFoundationTest {

    private fun channelToLinear(channel: Float): Double {
        return if (channel <= 0.04045f) {
            channel / 12.92
        } else {
            ((channel + 0.055) / 1.055).pow(2.4)
        }
    }

    private fun calculateRelativeLuminance(color: Color): Double {
        val r = channelToLinear(color.red)
        val g = channelToLinear(color.green)
        val b = channelToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun calculateContrastRatio(c1: Color, c2: Color): Double {
        val l1 = calculateRelativeLuminance(c1)
        val l2 = calculateRelativeLuminance(c2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testDarkTheme_primaryTextOnObsidian_exceedsWcagAaa() {
        val contrast = calculateContrastRatio(DarkHarmoniaSemanticColors.textPrimary, DarkHarmoniaSemanticColors.backgroundRoot)
        // Expected ~18.69:1, WCAG AAA threshold is 7.0:1
        assertTrue("Expected contrast > 7.0 but was $contrast", contrast >= 7.0)
        assertTrue("Expected contrast > 17.0 but was $contrast", contrast > 17.0)
    }

    @Test
    fun testDarkTheme_primarySignalOnObsidian_exceedsWcagAaa() {
        val contrast = calculateContrastRatio(DarkHarmoniaSemanticColors.primarySignal, DarkHarmoniaSemanticColors.backgroundRoot)
        // Sonic Amber on Obsidian exceeds WCAG AAA threshold (7.0:1, expected ~9.77:1)
        assertTrue("Expected contrast >= 7.0 (WCAG AAA) but was $contrast", contrast >= 7.0)
    }

    @Test
    fun testDarkTheme_secondaryTextOnElevatedSurface_exceedsWcagAa() {
        val contrast = calculateContrastRatio(DarkHarmoniaSemanticColors.textSecondary, DarkHarmoniaSemanticColors.surfaceElevated)
        // Expected ~7.75:1, WCAG AA threshold is 4.5:1, exceeds even AAA (7.0:1)
        assertTrue("Expected contrast >= 4.5 but was $contrast", contrast >= 4.5)
    }

    @Test
    fun testDarkTheme_violetAccentOnObsidian_meetsLargeTextAndUiIconThreshold() {
        val contrast = calculateContrastRatio(DarkHarmoniaSemanticColors.secondarySignal, DarkHarmoniaSemanticColors.backgroundRoot)
        // Cerulean on Obsidian is ~7.56:1, meets and exceeds WCAG AA for large text / UI icons (>= 3.0:1)
        assertTrue("Expected contrast >= 3.0 for large text/icons but was $contrast", contrast >= 3.0)
    }

    @Test
    fun testLightTheme_primaryTextOnSlateDay_exceedsWcagAaa() {
        val contrast = calculateContrastRatio(LightHarmoniaSemanticColors.textPrimary, LightHarmoniaSemanticColors.backgroundRoot)
        // Expected ~18.13:1, WCAG AAA threshold is 7.0:1
        assertTrue("Expected contrast > 7.0 but was $contrast", contrast >= 7.0)
    }

    @Test
    fun testLightTheme_deepTealOnSlateDay_exceedsWcagAa() {
        val contrast = calculateContrastRatio(LightHarmoniaSemanticColors.primarySignal, LightHarmoniaSemanticColors.backgroundRoot)
        // DaySonicAmber on ParchmentDay expected ~7.03:1, exceeds WCAG AA (4.5:1) and meets AAA (7.0:1)
        assertTrue("Expected contrast >= 4.5 but was $contrast", contrast >= 4.5)
    }

    @Test
    fun testLightTheme_secondaryTextOnSlateDay_exceedsWcagAa() {
        val contrast = calculateContrastRatio(LightHarmoniaSemanticColors.textSecondary, LightHarmoniaSemanticColors.backgroundRoot)
        // DaySlateSecondary on ParchmentDay expected ~7.07:1, exceeds WCAG AA (4.5:1)
        assertTrue("Expected contrast >= 4.5 but was $contrast", contrast >= 4.5)
    }

    @Test
    fun testLightTheme_plumVioletOnSlateDay_meetsLargeTextAndUiIconThreshold() {
        val contrast = calculateContrastRatio(LightHarmoniaSemanticColors.secondarySignal, LightHarmoniaSemanticColors.backgroundRoot)
        // DayCeruleanTechnical on ParchmentDay expected ~5.34:1, exceeds 3.0:1
        assertTrue("Expected contrast >= 3.0 but was $contrast", contrast >= 3.0)
    }

    @Test
    fun testLightTheme_statusErrorOnSlateDay_exceedsWcagAa() {
        val contrast = calculateContrastRatio(LightHarmoniaSemanticColors.statusError, LightHarmoniaSemanticColors.backgroundRoot)
        // DeepRedError on ParchmentDay expected ~4.6:1, exceeds WCAG AA (4.5:1)
        assertTrue("Expected contrast >= 4.5 but was $contrast", contrast >= 4.5)
    }

    @Test
    fun testLightTheme_statusWarningOnSlateDay_meetsLargeTextAndUiIconThreshold() {
        val contrast = calculateContrastRatio(LightHarmoniaSemanticColors.statusWarning, LightHarmoniaSemanticColors.backgroundRoot)
        // DeepAmberWarning on ParchmentDay expected ~3.2:1, exceeds 3.0:1
        assertTrue("Expected contrast >= 3.0 but was $contrast", contrast >= 3.0)
    }

    @Test
    fun testSpacingScale_incrementsAreConsistent() {
        val spacing = HarmoniaSpacing()
        assertEquals(4, spacing.xSmall.value.toInt())
        assertEquals(8, spacing.small.value.toInt())
        assertEquals(12, spacing.medium.value.toInt())
        assertEquals(16, spacing.standard.value.toInt())
        assertEquals(24, spacing.large.value.toInt())
        assertEquals(32, spacing.xLarge.value.toInt())
        assertEquals(96, spacing.bottomScaffold.value.toInt())
    }

    @Test
    fun testMinimumTouchTarget_is48dp() {
        assertEquals(48, MinimumInteractiveTouchTarget.value.toInt())
    }

    @Test
    fun testDarkTheme_miniPlayerAndNowPlaying_elevatedCardContrast_exceedsWcagAaa() {
        val contrast = calculateContrastRatio(DarkHarmoniaSemanticColors.textPrimary, DarkHarmoniaSemanticColors.surfaceElevated)
        assertTrue("Expected primary text on surface elevated >= 7.0 but was $contrast", contrast >= 7.0)
    }

    @Test
    fun testDarkTheme_primarySignalOnElevatedSurface_exceedsWcagAa() {
        val contrast = calculateContrastRatio(DarkHarmoniaSemanticColors.primarySignal, DarkHarmoniaSemanticColors.surfaceElevated)
        assertTrue("Expected primary teal signal on surface elevated >= 4.5 but was $contrast", contrast >= 4.5)
    }

    @Test
    fun testDarkTheme_statusIndicatorsOnElevatedSurface_exceedThresholds() {
        val errorContrast = calculateContrastRatio(DarkHarmoniaSemanticColors.statusError, DarkHarmoniaSemanticColors.surfaceElevated)
        val warningContrast = calculateContrastRatio(DarkHarmoniaSemanticColors.statusWarning, DarkHarmoniaSemanticColors.surfaceElevated)
        assertTrue("Expected error status on elevated surface >= 3.0 but was $errorContrast", errorContrast >= 3.0)
        assertTrue("Expected warning status on elevated surface >= 3.0 but was $warningContrast", warningContrast >= 3.0)
    }
}
