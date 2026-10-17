package org.harmonia.player.service.equalizer

import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AndroidPlatformEqualizerTest {

    private open class FakeNativeEqualizer : Equalizer(0, 1) {
        var released = false
        var releaseCallCount = 0
        var controlListener: AudioEffect.OnControlStatusChangeListener? = null
        var currentPresetValue: Short = 1
        var enabledValue: Boolean = false
        val setBandLevelCalls = mutableListOf<Pair<Short, Short>>()
        val usePresetCalls = mutableListOf<Short>()

        override fun getNumberOfBands(): Short = 5
        override fun getBandLevelRange(): ShortArray = shortArrayOf(-1500, 1500)
        override fun getCenterFreq(band: Short): Int = (band + 1) * 1000000
        override fun getBandLevel(band: Short): Short = 100
        override fun setBandLevel(band: Short, level: Short) {
            setBandLevelCalls.add(band to level)
        }
        override fun getNumberOfPresets(): Short = 3
        override fun getPresetName(preset: Short): String = "Preset $preset"
        override fun usePreset(preset: Short) {
            usePresetCalls.add(preset)
            currentPresetValue = preset
        }
        override fun getCurrentPreset(): Short = currentPresetValue
        override fun getEnabled(): Boolean = enabledValue
        override fun setEnabled(enabled: Boolean): Int {
            enabledValue = enabled
            return 0
        }
        override fun setControlStatusListener(listener: AudioEffect.OnControlStatusChangeListener?) {
            controlListener = listener
        }
        override fun release() {
            releaseCallCount++
            released = true
        }
    }

    @Test
    fun capabilities_and_operations_forwarded_to_native_handle() {
        val fakeNative = FakeNativeEqualizer()
        val adapter = AndroidPlatformEqualizer(fakeNative)

        assertEquals(5.toShort(), adapter.numberOfBands)
        assertEquals((-1500).toShort(), adapter.bandLevelRange[0])
        assertEquals(1500.toShort(), adapter.bandLevelRange[1])
        assertEquals(1000000, adapter.getCenterFreq(0))
        assertEquals(100.toShort(), adapter.getBandLevel(0))

        adapter.setBandLevel(0, 300)
        assertEquals(1, fakeNative.setBandLevelCalls.size)
        assertEquals(0.toShort() to 300.toShort(), fakeNative.setBandLevelCalls[0])

        assertEquals(3.toShort(), adapter.numberOfPresets)
        assertEquals("Preset 0", adapter.getPresetName(0))

        adapter.usePreset(2)
        assertEquals(1, fakeNative.usePresetCalls.size)
        assertEquals(2.toShort(), fakeNative.usePresetCalls[0])
        assertEquals(2.toShort(), adapter.currentPreset)

        assertFalse(adapter.enabled)
        adapter.enabled = true
        assertTrue(adapter.enabled)
        assertTrue(fakeNative.enabledValue)
    }

    @Test
    fun control_status_listener_forwards_and_cleans_up_on_close() {
        val fakeNative = FakeNativeEqualizer()
        val adapter = AndroidPlatformEqualizer(fakeNative)

        var controlGrantedResult: Boolean? = null
        adapter.setControlStatusListener { granted ->
            controlGrantedResult = granted
        }

        fakeNative.controlListener?.onControlStatusChange(fakeNative, false)
        assertEquals(false, controlGrantedResult)

        fakeNative.controlListener?.onControlStatusChange(fakeNative, true)
        assertEquals(true, controlGrantedResult)

        adapter.close()
        assertTrue(fakeNative.released)

        // Callback after close is discarded
        controlGrantedResult = null
        fakeNative.controlListener?.onControlStatusChange(fakeNative, true)
        assertEquals(null, controlGrantedResult)
    }

    @Test
    fun operations_after_close_throw_illegal_state_and_close_is_idempotent() {
        val fakeNative = FakeNativeEqualizer()
        val adapter = AndroidPlatformEqualizer(fakeNative)

        adapter.close()
        assertTrue(fakeNative.released)
        assertEquals(1, fakeNative.releaseCallCount)

        // Idempotency: second close does not double-release
        adapter.close()
        assertEquals(1, fakeNative.releaseCallCount)

        // Operations after close throw IllegalStateException
        try {
            adapter.numberOfBands
            fail("Expected IllegalStateException on numberOfBands after close")
        } catch (_: IllegalStateException) {}

        try {
            adapter.bandLevelRange
            fail("Expected IllegalStateException on bandLevelRange after close")
        } catch (_: IllegalStateException) {}

        try {
            adapter.getCenterFreq(0)
            fail("Expected IllegalStateException on getCenterFreq after close")
        } catch (_: IllegalStateException) {}

        try {
            adapter.setBandLevel(0, 0)
            fail("Expected IllegalStateException on setBandLevel after close")
        } catch (_: IllegalStateException) {}

        try {
            adapter.usePreset(0)
            fail("Expected IllegalStateException on usePreset after close")
        } catch (_: IllegalStateException) {}

        try {
            adapter.enabled = true
            fail("Expected IllegalStateException on enabled setter after close")
        } catch (_: IllegalStateException) {}
    }

    @Test
    fun listener_failure_during_close_does_not_prevent_native_release() {
        var releaseCalled = false
        val throwingNative = object : FakeNativeEqualizer() {
            override fun setControlStatusListener(listener: AudioEffect.OnControlStatusChangeListener?) {
                if (listener == null) {
                    throw RuntimeException("AudioServer died while unregistering listener")
                }
                super.setControlStatusListener(listener)
            }
            override fun release() {
                releaseCalled = true
                super.release()
            }
        }
        val adapter = AndroidPlatformEqualizer(throwingNative)
        adapter.setControlStatusListener {}

        adapter.close()

        assertTrue(releaseCalled)
        assertTrue(throwingNative.released)
    }
}

