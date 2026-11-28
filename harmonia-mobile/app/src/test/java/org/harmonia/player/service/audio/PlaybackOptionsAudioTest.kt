package org.harmonia.player.service.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.harmonia.player.domain.model.PlaybackOptions
import org.harmonia.player.presentation.settings.fadeLabel
import org.harmonia.player.presentation.settings.speedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.pow

class PlaybackOptionsAudioTest {

    // ── ReplayGain ────────────────────────────────────────────────────────

    @Test
    fun `reads track and album gain in the common spellings`() {
        val gain = parseReplayGainTags(
            listOf(
                "REPLAYGAIN_TRACK_GAIN" to "-6.20 dB",
                "replaygain_album_gain" to "+1.5 dB",
                "REPLAYGAIN_TRACK_PEAK" to "0.988",
                "TITLE" to "ignored"
            )
        )!!
        assertEquals(-6.2f, gain.trackGainDb!!, 0.001f)
        assertEquals(1.5f, gain.albumGainDb!!, 0.001f)
        assertEquals(0.988f, gain.trackPeak!!, 0.001f)
    }

    @Test
    fun `opus R128 gains are converted to the ReplayGain level`() {
        val gain = parseReplayGainTags(listOf("R128_TRACK_GAIN" to "-512"))!! // -2 dB at -23 LUFS
        assertEquals(3f, gain.trackGainDb!!, 0.001f)
    }

    @Test
    fun `songs without loudness tags are left alone`() {
        assertNull(parseReplayGainTags(listOf("ARTIST" to "x", "REPLAYGAIN_TRACK_GAIN" to "loud")))
        assertEquals(1f, levelingMultiplier(null))
    }

    @Test
    fun `multiplier follows the gain and never lets peaks clip`() {
        assertEquals(10f.pow(-6f / 20f), levelingMultiplier(ReplayGain(trackGainDb = -6f)), 0.0001f)
        // +6 dB would push a 0.9 peak past full scale, so it is capped at 1 / 0.9.
        assertEquals(1f / 0.9f, levelingMultiplier(ReplayGain(trackGainDb = 6f, trackPeak = 0.9f)), 0.0001f)
        // Falls back to album gain.
        assertEquals(10f.pow(-3f / 20f), levelingMultiplier(ReplayGain(albumGainDb = -3f)), 0.0001f)
    }

    // ── Fades ─────────────────────────────────────────────────────────────

    @Test
    fun `fade out at the end, fade in at the start, full volume in between`() {
        val fade = 6_000L
        val duration = 200_000L
        assertEquals(1f, SongFades.fadeGain(100_000, duration, fade, fadeOutAllowed = true, fadeInActive = true), 0.0001f)
        assertEquals(0f, SongFades.fadeGain(1, duration, fade, fadeOutAllowed = false, fadeInActive = true), 0.01f)
        assertEquals(0f, SongFades.fadeGain(duration, duration, fade, fadeOutAllowed = true, fadeInActive = false), 0.0001f)
        val midOut = SongFades.fadeGain(duration - 3_000 - SongFades.OUTPUT_LOOKAHEAD_MS, duration, fade, true, false)
        assertTrue(midOut in 0.6f..0.8f)
    }

    @Test
    fun `no fade when off, when not allowed, or for very short songs`() {
        assertEquals(1f, SongFades.fadeGain(199_000, 200_000, 0, true, true))
        assertEquals(1f, SongFades.fadeGain(199_000, 200_000, 6_000, fadeOutAllowed = false, fadeInActive = false))
        assertEquals(1f, SongFades.fadeGain(9_000, 10_000, 6_000, true, true))
    }

    // ── Gain stage ────────────────────────────────────────────────────────

    private fun pcm16(vararg samples: Short): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * 2).order(ByteOrder.nativeOrder()).apply {
            samples.forEach { putShort(it) }
            flip()
        }

    private fun process(processor: GainAudioProcessor, input: ByteBuffer): ShortArray {
        processor.queueInput(input)
        val out = processor.output
        return ShortArray(out.remaining() / 2) { out.getShort() }
    }

    @Test
    fun `unity gain passes audio through untouched`() {
        val processor = GainAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        assertTrue(process(processor, pcm16(100, -200, 32_000)).contentEquals(shortArrayOf(100, -200, 32_000)))
    }

    @Test
    fun `gain scales samples and clamps instead of wrapping`() {
        val processor = GainAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        processor.levelGain = 0.5f
        processor.flush() // settles at the target, so no glide in this buffer
        assertTrue(process(processor, pcm16(1_000, -1_000)).contentEquals(shortArrayOf(500, -500)))

        processor.levelGain = 4f
        processor.flush()
        val loud = process(processor, pcm16(20_000, -20_000))
        assertTrue(loud.contentEquals(shortArrayOf(Short.MAX_VALUE, Short.MIN_VALUE)))
        assertEquals(Short.MAX_VALUE, GainAudioProcessor.scalePcm16(30_000, 2f))
    }

    @Test
    fun `gain changes glide across the buffer instead of jumping`() {
        val processor = GainAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        processor.flush()
        processor.fadeGain = 0f
        val out = process(processor, pcm16(10_000, 10_000, 10_000, 10_000))
        assertTrue(out.first() > out.last())
        assertEquals(0, out.last().toInt())
    }

    // ── Settings labels and stored values ────────────────────────────────

    @Test
    fun `labels and sanitising`() {
        assertEquals("1×", speedLabel(1f))
        assertEquals("1.25×", speedLabel(1.25f))
        assertEquals("0.75×", speedLabel(0.75f))
        assertEquals("Off", fadeLabel(0))
        assertEquals("6s", fadeLabel(6))
        assertEquals(PlaybackOptions(speed = 2f, fadeSeconds = 12), PlaybackOptions.sanitized(9f, 99, false))
        assertEquals(1f, PlaybackOptions.sanitized(Float.NaN, 0, false).speed)
    }
}
