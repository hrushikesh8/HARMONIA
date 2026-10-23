package org.harmonia.player.presentation

import androidx.compose.ui.unit.dp
import org.harmonia.player.design.theme.MinimumInteractiveTouchTarget
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.presentation.playback.getRepeatAccessibilityLabel
import org.harmonia.player.presentation.playback.getShuffleAccessibilityLabel
import org.harmonia.player.presentation.settings.formatDb
import org.harmonia.player.presentation.settings.formatFrequency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating accessibility semantics remediation for M-REL.5.3.1:
 * - A11Y-01: Shuffle accessible label derivation and state transitions.
 * - A11Y-02: Repeat-mode accessible labels for all enum modes and cyclic transitions.
 * - A11Y-03: Equalizer slider state description conversion from millibels to decibels.
 * - A11Y-04: Playlist options touch target sizing and accessibility conformance.
 */
class AccessibilitySemanticsFormattingTest {

    // --- A11Y-01: Shuffle Semantics ---

    @Test
    fun shuffle_disabled_producesAccessibleLabelShuffleOff() {
        assertEquals("Shuffle off", getShuffleAccessibilityLabel(false))
    }

    @Test
    fun shuffle_enabled_producesAccessibleLabelShuffleOn() {
        assertEquals("Shuffle on", getShuffleAccessibilityLabel(true))
    }

    @Test
    fun shuffle_toggleTransition_correctlyUpdatesAccessibleLabel() {
        var shuffleMode = false
        assertEquals("Shuffle off", getShuffleAccessibilityLabel(shuffleMode))

        // Toggle on
        shuffleMode = !shuffleMode
        assertEquals("Shuffle on", getShuffleAccessibilityLabel(shuffleMode))

        // Toggle off
        shuffleMode = !shuffleMode
        assertEquals("Shuffle off", getShuffleAccessibilityLabel(shuffleMode))
    }

    // --- A11Y-02: Repeat Semantics ---

    @Test
    fun repeat_modeOff_producesAccessibleLabelRepeatOff() {
        assertEquals("Repeat off", getRepeatAccessibilityLabel(RepeatMode.OFF))
    }

    @Test
    fun repeat_modeAll_producesAccessibleLabelRepeatAll() {
        assertEquals("Repeat all", getRepeatAccessibilityLabel(RepeatMode.ALL))
    }

    @Test
    fun repeat_modeOne_producesAccessibleLabelRepeatOne() {
        assertEquals("Repeat one", getRepeatAccessibilityLabel(RepeatMode.ONE))
    }

    @Test
    fun repeat_allEnumValuesCoveredWithoutAmbiguity() {
        for (mode in RepeatMode.values()) {
            val label = getRepeatAccessibilityLabel(mode)
            assertTrue("Label must start with 'Repeat ': $label", label.startsWith("Repeat "))
            assertTrue(
                "Label must be one of the unambiguous accessible strings",
                label in setOf("Repeat off", "Repeat all", "Repeat one")
            )
        }
    }

    @Test
    fun repeat_cycleTransition_updatesAccessibleLabelsCorrectly() {
        var mode = RepeatMode.OFF
        assertEquals("Repeat off", getRepeatAccessibilityLabel(mode))

        // Cycle 1: OFF -> ALL
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals("Repeat all", getRepeatAccessibilityLabel(mode))

        // Cycle 2: ALL -> ONE
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals("Repeat one", getRepeatAccessibilityLabel(mode))

        // Cycle 3: ONE -> OFF
        mode = when (mode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        assertEquals("Repeat off", getRepeatAccessibilityLabel(mode))
    }

    // --- A11Y-03: Equalizer Formatting & Millibel-to-Decibel Conversion ---

    @Test
    fun equalizer_zeroMillibels_formatsAsZeroDb() {
        assertEquals("0.0 dB", formatDb(0.toShort()))
    }

    @Test
    fun equalizer_positiveLevels_convertsMillibelsToSignedDecibels() {
        assertEquals("+0.5 dB", formatDb(50.toShort()))
        assertEquals("+1.0 dB", formatDb(100.toShort()))
        assertEquals("+3.0 dB", formatDb(300.toShort()))
        assertEquals("+6.0 dB", formatDb(600.toShort()))
        assertEquals("+12.0 dB", formatDb(1200.toShort()))
        assertEquals("+15.0 dB", formatDb(1500.toShort()))
    }

    @Test
    fun equalizer_negativeLevels_convertsMillibelsToNegativeDecibels() {
        assertEquals("-0.5 dB", formatDb((-50).toShort()))
        assertEquals("-1.0 dB", formatDb((-100).toShort()))
        assertEquals("-3.0 dB", formatDb((-300).toShort()))
        assertEquals("-6.0 dB", formatDb((-600).toShort()))
        assertEquals("-12.0 dB", formatDb((-1200).toShort()))
        assertEquals("-15.0 dB", formatDb((-1500).toShort()))
    }

    @Test
    fun equalizer_frequencyFormatting_formatsHzAndKHzBoundaries() {
        assertEquals("60 Hz", formatFrequency(60))
        assertEquals("230 Hz", formatFrequency(230))
        assertEquals("910 Hz", formatFrequency(910))
        assertEquals("1 kHz", formatFrequency(1000))
        assertEquals("2.5 kHz", formatFrequency(2500))
        assertEquals("3.6 kHz", formatFrequency(3600))
        assertEquals("14 kHz", formatFrequency(14000))
    }

    // --- A11Y-04: Playlist Options Touch Target & Theme Tokens ---

    @Test
    fun touchTarget_themeTokenEnforcesMinimum48dp() {
        val minTarget = org.harmonia.player.design.theme.MinimumInteractiveTouchTarget
        assertEquals(48.dp, minTarget)
        assertTrue(minTarget.value >= 48f)
    }
}
