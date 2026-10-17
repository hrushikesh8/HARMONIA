package org.harmonia.player.core.di

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator
import org.harmonia.player.domain.model.equalizer.EqualizerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class ServiceLocatorEqualizerTest {

    private class DummyCoordinator(val id: String) : AudioEqualizerCoordinator {
        override val state: StateFlow<EqualizerState> = MutableStateFlow(EqualizerState.NoSession())
        override suspend fun setEnabled(enabled: Boolean): Boolean = true
        override suspend fun setBandLevel(band: Short, levelMb: Short): Boolean = true
        override suspend fun usePreset(presetIndex: Short): Boolean = true
    }

    @Before
    fun setUp() {
        ServiceLocator.reset()
    }

    @After
    fun tearDown() {
        ServiceLocator.reset()
    }

    @Test
    fun coordinator_registration_and_retrieval() {
        val coordinator = DummyCoordinator("1")
        ServiceLocator.setAudioEqualizerCoordinator(coordinator)

        assertSame(coordinator, ServiceLocator.getAudioEqualizerCoordinator())
    }

    @Test
    fun clear_is_identity_safe_older_instance_cannot_clear_newer_instance() {
        val older = DummyCoordinator("older")
        val newer = DummyCoordinator("newer")

        ServiceLocator.setAudioEqualizerCoordinator(older)
        assertSame(older, ServiceLocator.getAudioEqualizerCoordinator())

        // Service restart/recreation: newer instance registers before older finishes destroying
        ServiceLocator.setAudioEqualizerCoordinator(newer)
        assertSame(newer, ServiceLocator.getAudioEqualizerCoordinator())

        // Older service instance attempts to clear coordinator
        ServiceLocator.clearAudioEqualizerCoordinator(older)

        // Must NOT clear the newer instance!
        assertSame(newer, ServiceLocator.getAudioEqualizerCoordinator())

        // Clearing with the matching instance successfully clears it
        val clearResult = ServiceLocator.clearAudioEqualizerCoordinator(newer)
        assertEquals(true, clearResult)
        assertNull(ServiceLocator.getAudioEqualizerCoordinator())
    }

    @Test
    fun concurrent_clear_and_set_is_atomic_cas_preventing_stale_clearing() {
        for (i in 0 until 50) {
            ServiceLocator.reset()
            val older = DummyCoordinator("older_$i")
            val newer = DummyCoordinator("newer_$i")
            ServiceLocator.setAudioEqualizerCoordinator(older)

            val startLatch = java.util.concurrent.CountDownLatch(1)
            val doneLatch = java.util.concurrent.CountDownLatch(2)

            var clearResult = false

            // Thread 1: older service teardown trying to clear
            val t1 = Thread {
                startLatch.await()
                clearResult = ServiceLocator.clearAudioEqualizerCoordinator(older)
                doneLatch.countDown()
            }

            // Thread 2: newer service creation setting coordinator
            val t2 = Thread {
                startLatch.await()
                ServiceLocator.setAudioEqualizerCoordinator(newer)
                doneLatch.countDown()
            }

            t1.start()
            t2.start()
            startLatch.countDown()
            doneLatch.await(5, java.util.concurrent.TimeUnit.SECONDS)

            // Final state must NEVER be 'older' and if 'newer' was set after clear, it must remain 'newer'.
            // Because 'newer' was unconditionally set, the final state must be 'newer'
            // (either clear(older) ran first and succeeded, then set(newer) ran; OR set(newer) ran first,
            // in which case clear(older) CAS failed and returned false).
            val current = ServiceLocator.getAudioEqualizerCoordinator()
            assertSame("Iteration $i failed: newer coordinator was lost", newer, current)
        }
    }
}

