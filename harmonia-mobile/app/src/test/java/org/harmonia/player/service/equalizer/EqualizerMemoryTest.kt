package org.harmonia.player.service.equalizer

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.harmonia.player.domain.model.equalizer.EqualizerLayout
import org.harmonia.player.domain.model.equalizer.EqualizerPersistenceCodec
import org.harmonia.player.domain.model.equalizer.EqualizerState
import org.harmonia.player.domain.model.equalizer.PlatformEqualizer
import org.harmonia.player.domain.model.equalizer.PlatformEqualizerFactory
import org.harmonia.player.domain.model.equalizer.UserEqualizerPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerMemoryTest {

    // ── Codec ──────────────────────────────────────────────────────────────

    @Test
    fun `levels layout and presets survive a round trip`() {
        val levels = mapOf(60 to 300.toShort(), 14000 to (-150).toShort())
        assertEquals(levels, EqualizerPersistenceCodec.decodeLevels(EqualizerPersistenceCodec.encodeLevels(levels)))

        val layout = EqualizerLayout(listOf(60, 230, 910), -1500, 1500)
        assertEquals(layout, EqualizerPersistenceCodec.decodeLayout(EqualizerPersistenceCodec.encodeLayout(layout)))

        val presets = listOf(UserEqualizerPreset("Late night", levels), UserEqualizerPreset("Bass", mapOf(60 to 600.toShort())))
        assertEquals(presets, EqualizerPersistenceCodec.decodePresets(EqualizerPersistenceCodec.encodePresets(presets)))
    }

    @Test
    fun `bad stored text is ignored instead of crashing`() {
        assertEquals(emptyMap<Int, Short>(), EqualizerPersistenceCodec.decodeLevels("garbage,60:x,:5"))
        assertNull(EqualizerPersistenceCodec.decodeLayout("1|2"))
        assertNull(EqualizerPersistenceCodec.decodeLayout(null))
        assertEquals(emptyList<UserEqualizerPreset>(), EqualizerPersistenceCodec.decodePresets("no tab here\n\tmissing name"))
    }

    @Test
    fun `preset names lose tabs and line breaks`() {
        assertEquals("Late night mix", EqualizerPersistenceCodec.cleanPresetName("  Late\tnight\nmix "))
    }

    // ── Engine ─────────────────────────────────────────────────────────────

    private val dispatcher = StandardTestDispatcher()
    private val scope = TestScope(dispatcher)

    @Test
    fun `starting playback remembers the layout and re-applies saved levels`() = scope.runTest {
        val intent = InMemoryEqualizerUserIntentSource(isEnabled = true)
        intent.setCustomBandLevels(mapOf(60 to 600.toShort(), 3600 to (-300).toShort(), 99_999 to 100.toShort()))
        val effect = SimpleEqualizer()
        val manager = DefaultAudioEffectManager(factoryFor(effect), intent, scope = scope)

        manager.onAudioSessionIdChanged(7)
        advanceUntilIdle()

        assertEquals(listOf(60, 230, 910, 3600, 14000), intent.getLastKnownLayout()?.centerFreqsHz)
        assertEquals(600.toShort(), effect.getBandLevel(0))
        assertEquals((-300).toShort(), effect.getBandLevel(3))
        val state = manager.state.value as EqualizerState.EnabledWithControl
        assertEquals(600.toShort(), state.bands[0].currentLevelMb)
    }

    @Test
    fun `changing a band saves all levels by frequency`() = scope.runTest {
        val intent = InMemoryEqualizerUserIntentSource(isEnabled = true)
        val manager = DefaultAudioEffectManager(factoryFor(SimpleEqualizer()), intent, scope = scope)
        manager.onAudioSessionIdChanged(7)
        advanceUntilIdle()

        assertTrue(manager.setBandLevel(1, 450))
        assertEquals(450.toShort(), intent.getCustomBandLevels()[230])
        assertEquals(5, intent.getCustomBandLevels().size)
    }

    private fun factoryFor(effect: PlatformEqualizer) = object : PlatformEqualizerFactory {
        override fun create(audioSessionId: Int): PlatformEqualizer = effect
    }

    private class SimpleEqualizer : PlatformEqualizer {
        private val freqsMilliHz = listOf(60_000, 230_000, 910_000, 3_600_000, 14_000_000)
        private val levels = ShortArray(5)
        override val numberOfBands: Short = 5
        override val bandLevelRange: ShortArray = shortArrayOf(-1500, 1500)
        override fun getCenterFreq(band: Short): Int = freqsMilliHz[band.toInt()]
        override fun getBandLevel(band: Short): Short = levels[band.toInt()]
        override fun setBandLevel(band: Short, level: Short) { levels[band.toInt()] = level }
        override val numberOfPresets: Short = 1
        override fun getPresetName(preset: Short): String = "Normal"
        override fun usePreset(preset: Short) {}
        override val currentPreset: Short = 0
        override var enabled: Boolean = false
        override fun setControlStatusListener(listener: (controlGranted: Boolean) -> Unit) {}
        override fun close() {}
    }
}
