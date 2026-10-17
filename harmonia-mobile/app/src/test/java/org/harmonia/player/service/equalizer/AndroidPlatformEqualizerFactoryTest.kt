package org.harmonia.player.service.equalizer

import android.media.audiofx.Equalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class AndroidPlatformEqualizerFactoryTest {

    private open class DummyEqualizer(priority: Int, audioSession: Int) : Equalizer(priority, audioSession)

    @Test
    fun session_zero_and_negative_are_rejected_and_return_null() {
        var creatorCalled = false
        val factory = AndroidPlatformEqualizerFactory(
            equalizerCreator = { session ->
                creatorCalled = true
                DummyEqualizer(0, session)
            }
        )

        assertNull(factory.create(0))
        assertFalse(creatorCalled)

        assertNull(factory.create(-1))
        assertFalse(creatorCalled)

        assertNull(factory.create(-100))
        assertFalse(creatorCalled)
    }

    @Test
    fun positive_session_id_is_passed_to_creator() {
        var passedSession = -1
        val factory = AndroidPlatformEqualizerFactory(
            equalizerCreator = { session ->
                passedSession = session
                DummyEqualizer(0, session)
            }
        )

        val instance = factory.create(42)
        assertNotNull(instance)
        assertEquals(42, passedSession)
    }

    @Test
    fun creator_exceptions_propagate_faithfully_for_manager_classification() {
        val unsupportedFactory = AndroidPlatformEqualizerFactory(
            equalizerCreator = {
                throw UnsupportedOperationException("Device lacks equalizer engine")
            }
        )
        try {
            unsupportedFactory.create(10)
            fail("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertEquals("Device lacks equalizer engine", e.message)
        }

        val illegalArgFactory = AndroidPlatformEqualizerFactory(
            equalizerCreator = {
                throw IllegalArgumentException("Audio session rejected by audio server")
            }
        )
        try {
            illegalArgFactory.create(10)
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("Audio session rejected by audio server", e.message)
        }

        val runtimeFactory = AndroidPlatformEqualizerFactory(
            equalizerCreator = {
                throw RuntimeException("AudioServer died during initialization")
            }
        )

        try {
            runtimeFactory.create(10)
            fail("Expected RuntimeException")
        } catch (e: RuntimeException) {
            assertEquals("AudioServer died during initialization", e.message)
        }
    }

    @Test
    fun partial_initialization_failure_releases_allocated_native_handle_and_preserves_exception() {
        var releaseCallCount = 0
        val nativeInstance = object : DummyEqualizer(0, 10) {
            override fun release() {
                releaseCallCount++
            }
        }
        val factory = AndroidPlatformEqualizerFactory(
            equalizerCreator = { nativeInstance },
            adapterCreator = { throw IllegalStateException("Adapter initialization query failed") }
        )

        try {
            factory.create(10)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Adapter initialization query failed", e.message)
        }

        assertEquals(1, releaseCallCount)
    }

    @Test
    fun partial_initialization_failure_preserves_original_exception_with_suppressed_cleanup_failure() {
        var releaseCallCount = 0
        val nativeInstance = object : DummyEqualizer(0, 10) {
            override fun release() {
                releaseCallCount++
                throw RuntimeException("Native release failed during cleanup")
            }
        }
        val factory = AndroidPlatformEqualizerFactory(
            equalizerCreator = { nativeInstance },
            adapterCreator = { throw IllegalStateException("Adapter initialization query failed") }
        )

        try {
            factory.create(10)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("Adapter initialization query failed", e.message)
            assertEquals(1, e.suppressed.size)
            assertEquals("Native release failed during cleanup", e.suppressed[0].message)
        }

        assertEquals(1, releaseCallCount)
    }
}

