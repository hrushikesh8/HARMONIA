package org.harmonia.player.service.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Multiplies the audio by [levelGain] (volume levelling) × [fadeGain] (fades between songs),
 * gliding between values within each buffer so changes never click (task 4.5).
 *
 * It sits inside ExoPlayer's audio pipeline, so it is separate from the player volume that the
 * sleep timer fades, and from the system equalizer. Handles 16-bit and float PCM; louder-than-input
 * gain is clamped so it can't wrap around into distortion.
 */
@UnstableApi
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile var levelGain: Float = 1f
    @Volatile var fadeGain: Float = 1f

    private var currentGain = 1f

    private val targetGain: Float get() = levelGain * fadeGain

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT && inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val output = replaceOutputBuffer(size)
        val target = targetGain
        if (abs(currentGain - 1f) < 1e-4f && abs(target - 1f) < 1e-4f) {
            output.put(inputBuffer) // nothing to do: pass straight through
            output.flip()
            return
        }

        val format = inputAudioFormat
        val frames = size / format.bytesPerFrame
        val channels = format.channelCount
        val step = if (frames > 0) (target - currentGain) / frames else 0f
        var gain = currentGain
        if (format.encoding == C.ENCODING_PCM_16BIT) {
            repeat(frames) {
                gain += step
                repeat(channels) { output.putShort(scalePcm16(inputBuffer.getShort(), gain)) }
            }
        } else {
            repeat(frames) {
                gain += step
                repeat(channels) { output.putFloat((inputBuffer.getFloat() * gain).coerceIn(-1f, 1f)) }
            }
        }
        // Any trailing partial frame (shouldn't happen) is dropped rather than corrupting output.
        inputBuffer.position(inputBuffer.limit())
        currentGain = target
        output.flip()
    }

    override fun onFlush() {
        currentGain = targetGain
    }

    override fun onReset() {
        levelGain = 1f
        fadeGain = 1f
        currentGain = 1f
    }

    companion object {
        /** One 16-bit sample times [gain], clamped to the 16-bit range. */
        fun scalePcm16(sample: Short, gain: Float): Short =
            (sample * gain).roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }
}
