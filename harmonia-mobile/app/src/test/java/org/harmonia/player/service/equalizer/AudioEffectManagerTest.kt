package org.harmonia.player.service.equalizer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.harmonia.player.domain.model.equalizer.EqualizerFailureReason
import org.harmonia.player.domain.model.equalizer.EqualizerState
import org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource
import org.harmonia.player.domain.model.equalizer.PlatformEqualizer
import org.harmonia.player.domain.model.equalizer.PlatformEqualizerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class AudioEffectManagerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeFactory: FakePlatformEqualizerFactory
    private lateinit var fakeUserIntentSource: FakeUserIntentSource
    private lateinit var manager: DefaultAudioEffectManager

    @Before
    fun setUp() {
        fakeFactory = FakePlatformEqualizerFactory()
        fakeUserIntentSource = FakeUserIntentSource(enabled = false)
        manager = DefaultAudioEffectManager(
            platformEqualizerFactory = fakeFactory,
            userIntentSource = fakeUserIntentSource,
            scope = testScope
        )
    }

    // 1. Zero and negative session IDs
    @Test
    fun session_zero_and_negative_are_rejected_and_remain_no_session() = testScope.runTest {
        // Zero session ID
        manager.onAudioSessionIdChanged(0)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.NoSession)
        assertEquals(0, fakeFactory.createCallCount)

        // Negative session ID
        manager.onAudioSessionIdChanged(-1)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.NoSession)
        assertEquals(0, fakeFactory.createCallCount)
    }

    // 2. Successful initialization with enabled and disabled user intent
    @Test
    fun initialization_reconciles_with_enabled_and_disabled_user_intent() = testScope.runTest {
        // Disabled user intent
        fakeUserIntentSource.setEqualizerEnabled(false)
        manager.onAudioSessionIdChanged(42)
        advanceUntilIdle()

        val disabledState = manager.state.value
        assertTrue(disabledState is EqualizerState.AvailableDisabled)
        val disabledEffect = fakeFactory.lastCreatedInstance
        assertNotNull(disabledEffect)
        assertFalse(disabledEffect!!.enabled)

        // Switch to session with enabled user intent
        fakeUserIntentSource.setEqualizerEnabled(true)
        manager.onAudioSessionIdChanged(43)
        advanceUntilIdle()

        val enabledState = manager.state.value
        assertTrue(enabledState is EqualizerState.EnabledWithControl)
        val enabledEffect = fakeFactory.lastCreatedInstance
        assertNotNull(enabledEffect)
        assertTrue(enabledEffect!!.enabled)
    }

    // 3. Replacement releases the old effect before creating the new one
    @Test
    fun replacement_releases_old_effect_before_creating_new_one() = testScope.runTest {
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        val firstEffect = fakeFactory.lastCreatedInstance
        assertNotNull(firstEffect)
        assertFalse(firstEffect!!.isClosed)

        // Transition to new session
        manager.onAudioSessionIdChanged(200)
        advanceUntilIdle()
        val secondEffect = fakeFactory.lastCreatedInstance
        assertNotNull(secondEffect)

        // First effect must be closed
        assertTrue(firstEffect.isClosed)
        assertEquals(1, firstEffect.closeCallCount)
        assertFalse(secondEffect!!.isClosed)
    }

    // 4. Rebinding the same session avoids duplicate allocation
    @Test
    fun rebinding_same_valid_session_avoids_duplicate_allocation() = testScope.runTest {
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        assertEquals(1, fakeFactory.createCallCount)

        // Rebind same session
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        assertEquals(1, fakeFactory.createCallCount)
    }

    // 5. Stale callbacks are ignored
    @Test
    fun stale_callbacks_from_previous_generations_are_ignored() = testScope.runTest {
        fakeUserIntentSource.setEqualizerEnabled(true)
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        val firstEffect = fakeFactory.lastCreatedInstance!!
        assertTrue(manager.state.value is EqualizerState.EnabledWithControl)

        // Replace session (increments generation)
        manager.onAudioSessionIdChanged(200)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.EnabledWithControl)

        // Fire control status change from the old firstEffect
        firstEffect.simulateControlStatusChange(controlGranted = false)
        advanceUntilIdle()

        // Current state on session 200 must NOT become ControlLost
        assertTrue(manager.state.value is EqualizerState.EnabledWithControl)
    }

    // 6. Control loss preserves enable intent
    @Test
    fun control_loss_preserves_enable_intent() = testScope.runTest {
        fakeUserIntentSource.setEqualizerEnabled(true)
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.EnabledWithControl)

        val effect = fakeFactory.lastCreatedInstance!!
        effect.simulateControlStatusChange(controlGranted = false)
        advanceUntilIdle()

        val state = manager.state.value
        assertTrue(state is EqualizerState.ControlLost)
        val controlLost = state as EqualizerState.ControlLost
        assertTrue(controlLost.lastKnownEnabled)
        assertTrue(fakeUserIntentSource.isEqualizerEnabled())
    }

    // 7. Control regain reconciles the latest intent
    @Test
    fun control_regain_reconciles_latest_intent() = testScope.runTest {
        fakeUserIntentSource.setEqualizerEnabled(true)
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()

        val effect = fakeFactory.lastCreatedInstance!!
        // Lose control
        effect.simulateControlStatusChange(controlGranted = false)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.ControlLost)

        // While control is lost, user disables equalizer
        manager.setEnabled(false)
        advanceUntilIdle()
        assertFalse(fakeUserIntentSource.isEqualizerEnabled())

        // Regain control
        effect.simulateControlStatusChange(controlGranted = true)
        advanceUntilIdle()

        // Should transition to AvailableDisabled since user disabled it
        val state = manager.state.value
        assertTrue(state is EqualizerState.AvailableDisabled)
        assertFalse(effect.enabled)
    }

    // 8. Release is idempotent and prevents subsequent operations
    @Test
    fun release_is_idempotent_and_prevents_subsequent_operations() = testScope.runTest {
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        val effect = fakeFactory.lastCreatedInstance!!
        assertFalse(effect.isClosed)

        // First release
        manager.release()
        advanceUntilIdle()
        assertTrue(manager.isClosed)
        assertTrue(effect.isClosed)
        assertTrue(manager.state.value is EqualizerState.NoSession)

        // Subsequent releases are safe no-ops
        manager.release()
        manager.close()
        advanceUntilIdle()
        assertEquals(1, effect.closeCallCount)

        // Subsequent operations fail closed and do not allocate
        val success = manager.setEnabled(true)
        assertFalse(success)
        manager.onAudioSessionIdChanged(200)
        advanceUntilIdle()
        assertEquals(1, fakeFactory.createCallCount)
    }

    // 9. Release racing with queued operations never touches a released effect (sequential post-release test)
    @Test
    fun queued_operations_after_release_never_touch_released_effect() = testScope.runTest {
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        val effect = fakeFactory.lastCreatedInstance!!

        // Close manager
        manager.release()
        advanceUntilIdle()

        // If an operation touches effect after release, effect throws IllegalStateException
        val bandResult = manager.setBandLevel(0, 300)
        val presetResult = manager.usePreset(1)
        assertFalse(bandResult)
        assertFalse(presetResult)
        assertEquals(0, effect.setBandLevelCalls.size)
        assertEquals(0, effect.usePresetCalls.size)
    }

    // 10. Release remains safe when ordinary coroutine scope is cancelled
    @Test
    fun release_remains_safe_when_ordinary_scope_is_cancelled() = testScope.runTest {
        val managerScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + testDispatcher)
        val isolatedManager = DefaultAudioEffectManager(
            platformEqualizerFactory = fakeFactory,
            userIntentSource = fakeUserIntentSource,
            scope = managerScope
        )

        isolatedManager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        val effect = fakeFactory.lastCreatedInstance!!
        assertFalse(effect.isClosed)

        // Cancel the manager's ordinary scope
        managerScope.cancel()

        // Calling release using runBlocking/NonCancellable must still succeed without throwing
        isolatedManager.close()
        assertTrue(isolatedManager.isClosed)
        assertTrue(effect.isClosed)
    }

    // 11. Runtime unavailability does not overwrite user intent
    @Test
    fun runtime_unavailability_does_not_overwrite_user_intent() = testScope.runTest {
        fakeUserIntentSource.setEqualizerEnabled(true)

        // Session unmount / disconnect (sessionId = 0)
        manager.onAudioSessionIdChanged(0)
        advanceUntilIdle()

        assertTrue(manager.state.value is EqualizerState.NoSession)
        // User intent remains true!
        assertTrue(fakeUserIntentSource.isEqualizerEnabled())

        // Later valid session connects: automatically activates effect
        manager.onAudioSessionIdChanged(500)
        advanceUntilIdle()

        val state = manager.state.value
        assertTrue(state is EqualizerState.EnabledWithControl)
        val effect = fakeFactory.lastCreatedInstance!!
        assertTrue(effect.enabled)
    }

    // 12. Expected platform failures degrade safely
    @Test
    fun expected_platform_failures_degrade_safely() = testScope.runTest {
        // Unsupported hardware / offload route
        fakeFactory.throwOnCreate = UnsupportedOperationException("Hardware offload does not support effects")
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()

        val state = manager.state.value
        assertTrue(state is EqualizerState.NoSession)
        val failure = (state as EqualizerState.NoSession).failureReason
        assertTrue(failure is EqualizerFailureReason.UnsupportedHardwareOrRoute)

        // Invalid session ID argument
        fakeFactory.throwOnCreate = IllegalArgumentException("Bad audio session ID")
        manager.onAudioSessionIdChanged(101)
        advanceUntilIdle()

        val state2 = manager.state.value
        assertTrue(state2 is EqualizerState.NoSession)
        val failure2 = (state2 as EqualizerState.NoSession).failureReason
        assertTrue(failure2 is EqualizerFailureReason.InvalidSessionOrArgument)
    }

    // 13. Unexpected failures remain diagnosable
    @Test
    fun unexpected_failures_remain_diagnosable() = testScope.runTest {
        fakeFactory.throwOnCreate = RuntimeException("AudioFlinger binder dead -5")
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()

        val state = manager.state.value
        assertTrue(state is EqualizerState.NoSession)
        val failure = (state as EqualizerState.NoSession).failureReason
        assertTrue(failure is EqualizerFailureReason.UnexpectedNativeError)
        val unexpected = failure as EqualizerFailureReason.UnexpectedNativeError
        assertEquals("RuntimeException", unexpected.causeName)
    }

    // 14. Cancellation propagates correctly
    @Test
    fun cancellation_propagates_correctly() = testScope.runTest {
        fakeFactory.throwOnCreate = CancellationException("Coroutine cancelled")

        try {
            manager.onAudioSessionIdChanged(100)
            advanceUntilIdle()
            org.junit.Assert.fail("Expected CancellationException was not rethrown")
        } catch (ce: CancellationException) {
            assertEquals("Coroutine cancelled", ce.message)
        }
    }

    // --- Audit Remediation Regression Tests ---

    // 15. Partial initialization cancellation cleans up effect, invalidates generation, and propagates cancellation
    @Test
    fun partial_initialization_cancellation_cleans_up_and_propagates_cancellation() = testScope.runTest {
        fakeFactory.onInstanceCreated = { effect ->
            effect.throwOnGetCenterFreq = CancellationException("Cancelled during band query")
        }

        var caughtCancellation = false
        try {
            manager.onAudioSessionIdChanged(100)
            advanceUntilIdle()
        } catch (ce: CancellationException) {
            caughtCancellation = true
        }

        assertTrue(caughtCancellation)
        val effect = fakeFactory.lastCreatedInstance
        assertNotNull(effect)
        // Partially initialized effect must be closed!
        assertTrue(effect!!.isClosed)
        // State must be NoSession
        assertTrue(manager.state.value is EqualizerState.NoSession)

        // Late callback from the partially initialized effect must be ignored
        effect.simulateControlStatusChange(controlGranted = true)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.NoSession)
    }

    // 16. Capability query failure cleans up effect and sets diagnosable failure without fabricated defaults
    @Test
    fun capability_query_failure_cleans_up_and_sets_diagnosable_failure() = testScope.runTest {
        fakeFactory.onInstanceCreated = { effect ->
            effect.throwOnGetCenterFreq = RuntimeException("HAL center freq query failed")
        }

        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()

        val effect = fakeFactory.lastCreatedInstance
        assertNotNull(effect)
        // Effect must be closed
        assertTrue(effect!!.isClosed)
        val state = manager.state.value
        assertTrue(state is EqualizerState.NoSession)
        val failure = (state as EqualizerState.NoSession).failureReason
        assertTrue(failure is EqualizerFailureReason.UnexpectedNativeError)
    }

    // 17. Enable failure does not publish enabled state and degrades safely
    @Test
    fun enable_failure_does_not_publish_enabled_state_and_degrades_safely() = testScope.runTest {
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.AvailableDisabled)

        val effect = fakeFactory.lastCreatedInstance!!
        effect.throwOnEnable = RuntimeException("Native enable failed")

        val result = manager.setEnabled(true)
        advanceUntilIdle()

        assertFalse(result)
        // Must NOT be EnabledWithControl!
        assertFalse(manager.state.value is EqualizerState.EnabledWithControl)
        assertTrue(manager.state.value is EqualizerState.NoSession)
        val failure = (manager.state.value as EqualizerState.NoSession).failureReason
        assertTrue(failure is EqualizerFailureReason.UnexpectedNativeError)
        assertTrue(effect.isClosed)
    }

    // 18. Disable failure does not publish available disabled and degrades safely
    @Test
    fun disable_failure_does_not_publish_available_disabled_and_degrades_safely() = testScope.runTest {
        fakeUserIntentSource.setEqualizerEnabled(true)
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.EnabledWithControl)

        val effect = fakeFactory.lastCreatedInstance!!
        effect.throwOnDisable = RuntimeException("Native disable failed")

        val result = manager.setEnabled(false)
        advanceUntilIdle()

        assertFalse(result)
        // Must NOT be AvailableDisabled!
        assertFalse(manager.state.value is EqualizerState.AvailableDisabled)
        assertTrue(manager.state.value is EqualizerState.NoSession)
        val failure = (manager.state.value as EqualizerState.NoSession).failureReason
        assertTrue(failure is EqualizerFailureReason.UnexpectedNativeError)
        assertTrue(effect.isClosed)
    }

    // 19. Actual concurrency test: operation queued against a held mutex while release competes
    @Test
    fun actual_concurrent_mutex_queueing_and_release_race() = runBlocking(Dispatchers.Default) {
        val trackingMutex = ContentionTrackingMutex()
        val testManager = DefaultAudioEffectManager(
            platformEqualizerFactory = fakeFactory,
            userIntentSource = fakeUserIntentSource,
            scope = this,
            mutex = trackingMutex
        )
        testManager.onAudioSessionIdChanged(100)
        withTimeout(5000) {
            testManager.state.first { it is EqualizerState.AvailableDisabled }
        }
        val effect = fakeFactory.lastCreatedInstance!!

        val inSetBandLevel = CountDownLatch(1)
        val proceedSetBandLevel = CountDownLatch(1)

        effect.beforeSetBandLevel = {
            inSetBandLevel.countDown()
            proceedSetBandLevel.await(5, TimeUnit.SECONDS)
        }

        // Job 1: Acquires mutex in setBandLevel and pauses inside effect
        val job1 = async {
            testManager.setBandLevel(0, 500)
        }

        // Deterministically verify Job 1 is inside setBandLevel holding the mutex
        assertTrue(inSetBandLevel.await(5, TimeUnit.SECONDS))

        // Set up deterministic synchronization for release arriving at the contention point
        val releaseQueuedLatch = CountDownLatch(1)
        trackingMutex.onLockAttempt = {
            releaseQueuedLatch.countDown()
        }

        // Job 2: release() attempts to acquire mutex and must suspend at the contention point
        val releaseJob = async {
            testManager.release()
        }

        // Deterministically verify release has called lock() and reached the contention point
        assertTrue(releaseQueuedLatch.await(5, TimeUnit.SECONDS))

        // Set up deterministic synchronization for usePreset arriving at the contention point
        val usePresetQueuedLatch = CountDownLatch(1)
        trackingMutex.onLockAttempt = {
            usePresetQueuedLatch.countDown()
        }

        // Job 3: usePreset() queues behind release()
        val job3 = async {
            testManager.usePreset(1)
        }

        // Deterministically verify usePreset has called lock() and reached the contention point
        assertTrue(usePresetQueuedLatch.await(5, TimeUnit.SECONDS))

        // Disarm tracking hook
        trackingMutex.onLockAttempt = null

        // At this point, Job 1 holds the mutex; releaseJob and Job 3 are deterministically proved to be queued
        assertFalse(releaseJob.isCompleted)
        assertFalse(job3.isCompleted)
        assertFalse(effect.isClosed)
        assertFalse(testManager.isClosed)

        // Allow Job 1 to proceed and release the mutex
        proceedSetBandLevel.countDown()

        val job1Result = job1.await()
        releaseJob.await()
        val job3Result = job3.await()

        // Verification of execution ordering and invariants:
        assertTrue(job1Result)
        assertTrue(testManager.isClosed)
        assertTrue(effect.isClosed)

        // Release idempotency verification: second release does not re-close or fail
        testManager.release()
        assertEquals(1, effect.closeCallCount)

        // Job 3 ran after release, saw isReleased == true, and returned false without touching closed effect
        assertFalse(job3Result)
        assertEquals(0, effect.usePresetCalls.size)
    }

    // 20. Control loss keeps actual runtime state distinct from user intent
    @Test
    fun control_loss_distinguishes_runtime_state_from_user_intent() = testScope.runTest {
        // User intent is enabled in persistence, but runtime effect is currently disabled
        fakeUserIntentSource.setEqualizerEnabled(true)
        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()
        // Now explicitly set to false at runtime
        manager.setEnabled(false)
        advanceUntilIdle()
        assertTrue(manager.state.value is EqualizerState.AvailableDisabled)

        // Now artificially set user intent to true to simulate out-of-band/stored intent
        fakeUserIntentSource.setEqualizerEnabled(true)

        val effect = fakeFactory.lastCreatedInstance!!
        // Trigger control loss: runtime was disabled, so lastKnownEnabled MUST be false
        effect.simulateControlStatusChange(controlGranted = false)
        advanceUntilIdle()

        val state = manager.state.value
        assertTrue(state is EqualizerState.ControlLost)
        val controlLost = state as EqualizerState.ControlLost
        assertFalse(controlLost.lastKnownEnabled)
    }

    // 21. Partial initialization preserves original failure when cleanup also fails
    @Test
    fun partial_initialization_preserves_original_failure_when_cleanup_fails() = testScope.runTest {
        var createdEffect: FakePlatformEqualizer? = null
        fakeFactory.onInstanceCreated = { instance ->
            createdEffect = instance
            instance.throwOnGetCenterFreq = RuntimeException("Original query frequency failure")
            instance.throwOnClose = IllegalStateException("Secondary cleanup close failure")
        }

        manager.onAudioSessionIdChanged(100)
        advanceUntilIdle()

        assertNotNull(createdEffect)
        assertTrue(createdEffect!!.isClosed)
        assertTrue(manager.state.value is EqualizerState.NoSession)
        val failure = (manager.state.value as EqualizerState.NoSession).failureReason
        assertTrue(failure is EqualizerFailureReason.UnexpectedNativeError)
        val unexpected = failure as EqualizerFailureReason.UnexpectedNativeError
        // Original failure message and cause must be preserved, NOT replaced by the close failure
        assertEquals("Original query frequency failure", unexpected.message)
        assertEquals("RuntimeException", unexpected.causeName)
    }

    // 22. Cancellation during initialization preserves cleanup failure as suppressed and rethrows cancellation
    @Test
    fun cancellation_during_initialization_attaches_cleanup_failure_and_rethrows() = testScope.runTest {
        var createdEffect: FakePlatformEqualizer? = null
        fakeFactory.onInstanceCreated = { instance ->
            createdEffect = instance
            instance.throwOnGetCenterFreq = CancellationException("Test initialization cancellation")
            instance.throwOnClose = IllegalStateException("Secondary cleanup close failure")
        }

        var thrownCancellation: CancellationException? = null
        try {
            manager.onAudioSessionIdChanged(100)
            advanceUntilIdle()
        } catch (ce: CancellationException) {
            thrownCancellation = ce
        }

        val cancellation = thrownCancellation
        assertNotNull(cancellation)
        assertEquals("Test initialization cancellation", cancellation!!.message)
        assertEquals(1, cancellation.suppressedExceptions.size)
        assertTrue(cancellation.suppressedExceptions[0] is IllegalStateException)
        assertEquals("Secondary cleanup close failure", cancellation.suppressedExceptions[0].message)
        assertNotNull(createdEffect)
        assertTrue(createdEffect!!.isClosed)
        assertTrue(manager.state.value is EqualizerState.NoSession)
    }

    // --- Test Doubles ---

    private class FakeUserIntentSource(private var enabled: Boolean) : EqualizerUserIntentSource {
        override fun isEqualizerEnabled(): Boolean = enabled
        override fun setEqualizerEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class FakePlatformEqualizerFactory : PlatformEqualizerFactory {
        var createCallCount = 0
        var throwOnCreate: Exception? = null
        var lastCreatedInstance: FakePlatformEqualizer? = null
        var onInstanceCreated: ((FakePlatformEqualizer) -> Unit)? = null

        override fun create(audioSessionId: Int): PlatformEqualizer? {
            createCallCount++
            throwOnCreate?.let { throw it }
            val instance = FakePlatformEqualizer()
            lastCreatedInstance = instance
            onInstanceCreated?.invoke(instance)
            return instance
        }
    }

    private class FakePlatformEqualizer : PlatformEqualizer {
        var isClosed = false
        var closeCallCount = 0
        var controlListener: ((Boolean) -> Unit)? = null
        val setBandLevelCalls = mutableListOf<Pair<Short, Short>>()
        val usePresetCalls = mutableListOf<Short>()

        var throwOnGetCenterFreq: Exception? = null
        var throwOnEnable: Exception? = null
        var throwOnDisable: Exception? = null
        var throwOnClose: Exception? = null
        var beforeSetBandLevel: (() -> Unit)? = null

        override val numberOfBands: Short = 5
        override val bandLevelRange: ShortArray = shortArrayOf(-1500, 1500)
        override val numberOfPresets: Short = 3
        override var currentPreset: Short = 0

        private var _enabled: Boolean = false
        override var enabled: Boolean
            get() = _enabled
            set(value) {
                checkNotClosed()
                if (value) {
                    throwOnEnable?.let { throw it }
                } else {
                    throwOnDisable?.let { throw it }
                }
                _enabled = value
            }

        private val centerFreqs = listOf(60000, 230000, 910000, 3600000, 14000000) // mHz
        private val bandLevels = mutableMapOf<Short, Short>(
            0.toShort() to 0,
            1.toShort() to 0,
            2.toShort() to 0,
            3.toShort() to 0,
            4.toShort() to 0
        )
        private val presetNames = listOf("Normal", "Rock", "Jazz")

        override fun getCenterFreq(band: Short): Int {
            checkNotClosed()
            throwOnGetCenterFreq?.let { throw it }
            return centerFreqs.getOrElse(band.toInt()) { 1000000 }
        }

        override fun getBandLevel(band: Short): Short {
            checkNotClosed()
            return bandLevels[band] ?: 0
        }

        override fun setBandLevel(band: Short, level: Short) {
            checkNotClosed()
            beforeSetBandLevel?.invoke()
            checkNotClosed()
            setBandLevelCalls.add(band to level)
            bandLevels[band] = level
            currentPreset = -1
        }

        override fun getPresetName(preset: Short): String {
            checkNotClosed()
            return presetNames.getOrElse(preset.toInt()) { "Preset $preset" }
        }

        override fun usePreset(preset: Short) {
            checkNotClosed()
            usePresetCalls.add(preset)
            currentPreset = preset
        }

        override fun setControlStatusListener(listener: (controlGranted: Boolean) -> Unit) {
            checkNotClosed()
            this.controlListener = listener
        }

        fun simulateControlStatusChange(controlGranted: Boolean) {
            controlListener?.invoke(controlGranted)
        }

        override fun close() {
            closeCallCount++
            isClosed = true
            throwOnClose?.let { throw it }
        }

        private fun checkNotClosed() {
            if (isClosed) {
                throw IllegalStateException("PlatformEqualizer has already been closed/released!")
            }
        }
    }

    private class ContentionTrackingMutex(private val delegate: Mutex = Mutex()) : Mutex {
        @Volatile
        var onLockAttempt: (() -> Unit)? = null

        override val isLocked: Boolean
            get() = delegate.isLocked

        override fun holdsLock(owner: Any): Boolean = delegate.holdsLock(owner)

        override suspend fun lock(owner: Any?) {
            onLockAttempt?.invoke()
            delegate.lock(owner)
        }

        override fun tryLock(owner: Any?): Boolean = delegate.tryLock(owner)

        override fun unlock(owner: Any?) = delegate.unlock(owner)

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        @ExperimentalCoroutinesApi
        override val onLock: kotlinx.coroutines.selects.SelectClause2<Any?, Mutex>
            get() = delegate.onLock
    }
}
