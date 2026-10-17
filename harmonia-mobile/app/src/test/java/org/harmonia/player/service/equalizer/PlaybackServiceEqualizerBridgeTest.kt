package org.harmonia.player.service.equalizer

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.analytics.AnalyticsListener
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.harmonia.player.domain.model.equalizer.EqualizerFailureReason
import org.harmonia.player.domain.model.equalizer.EqualizerState
import org.harmonia.player.domain.model.equalizer.PlatformEqualizer
import org.harmonia.player.domain.model.equalizer.PlatformEqualizerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackServiceEqualizerBridgeTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeFactory: FakeEqualizerFactory
    private lateinit var userIntentSource: InMemoryEqualizerUserIntentSource
    private lateinit var manager: DefaultAudioEffectManager
    private lateinit var bridge: PlaybackServiceEqualizerBridge
    private lateinit var playerFake: FakeAudioSessionPlayer

    @Before
    fun setUp() {
        fakeFactory = FakeEqualizerFactory()
        userIntentSource = InMemoryEqualizerUserIntentSource(isEnabled = false)
        manager = DefaultAudioEffectManager(
            platformEqualizerFactory = fakeFactory,
            userIntentSource = userIntentSource,
            scope = testScope
        )
        bridge = PlaybackServiceEqualizerBridge(
            audioEffectManager = manager,
            parentScope = testScope
        )
        playerFake = FakeAudioSessionPlayer()
    }

    private fun dummyEventTime(): AnalyticsListener.EventTime {
        val constructor = AnalyticsListener.EventTime::class.java.constructors[0]
        val args = constructor.parameterTypes.map { paramType ->
            when (paramType) {
                Long::class.javaPrimitiveType -> 0L
                Int::class.javaPrimitiveType -> 0
                Timeline::class.java -> Timeline.EMPTY
                else -> null
            }
        }.toTypedArray()
        return constructor.newInstance(*args) as AnalyticsListener.EventTime
    }

    private fun attachBridge() {
        bridge.attachToSessionSource(
            currentAudioSessionId = playerFake.currentAudioSessionId,
            addListener = { playerFake.addListener(it) },
            removeListener = { playerFake.removeListener(it) }
        )
    }

    // 1. Initial valid audio session ID from player is forwarded upon attach
    @Test
    fun initial_valid_session_id_is_forwarded_upon_attach() = testScope.runTest {
        playerFake.currentAudioSessionId = 42

        attachBridge()
        advanceUntilIdle()

        assertEquals(1, fakeFactory.createCalls.size)
        assertEquals(42, fakeFactory.createCalls[0])
        assertTrue(manager.state.value is EqualizerState.AvailableDisabled)
    }

    // 2. Initial unset or zero session ID does not allocate an effect
    @Test
    fun initial_zero_or_unset_session_does_not_allocate_effect() = testScope.runTest {
        playerFake.currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET

        attachBridge()
        advanceUntilIdle()

        assertEquals(0, fakeFactory.createCalls.size)
        assertTrue(manager.state.value is EqualizerState.NoSession)
    }

    // 3. onAudioSessionIdChanged event forwards new positive session ID
    @Test
    fun onAudioSessionIdChanged_event_forwards_new_session_id() = testScope.runTest {
        playerFake.currentAudioSessionId = C.AUDIO_SESSION_ID_UNSET
        attachBridge()
        advanceUntilIdle()
        assertEquals(0, fakeFactory.createCalls.size)

        // Callback fires with newly allocated audio session
        playerFake.notifyAudioSessionIdChanged(dummyEventTime(), 84)
        advanceUntilIdle()

        assertEquals(1, fakeFactory.createCalls.size)
        assertEquals(84, fakeFactory.createCalls[0])
        assertTrue(manager.state.value is EqualizerState.AvailableDisabled)
    }

    // 4. onAudioSessionIdChanged with 0 or negative enters NoSession
    @Test
    fun onAudioSessionIdChanged_with_zero_or_negative_enters_no_session() = testScope.runTest {
        playerFake.currentAudioSessionId = 100
        attachBridge()
        advanceUntilIdle()
        val firstEffect = fakeFactory.lastCreated!!
        assertFalse(firstEffect.isClosed)

        // Session reset to 0
        playerFake.notifyAudioSessionIdChanged(dummyEventTime(), 0)
        advanceUntilIdle()

        assertTrue(firstEffect.isClosed)
        assertTrue(manager.state.value is EqualizerState.NoSession)
    }

    // 5. Session replacement releases old effect before new effect
    @Test
    fun session_replacement_releases_old_effect_before_new() = testScope.runTest {
        playerFake.currentAudioSessionId = 100
        attachBridge()
        advanceUntilIdle()
        val firstEffect = fakeFactory.lastCreated!!

        var firstClosedWhenSecondCreated = false
        fakeFactory.onInstanceCreated = {
            if (firstEffect.isClosed) {
                firstClosedWhenSecondCreated = true
            }
        }

        // Change session to 200
        playerFake.notifyAudioSessionIdChanged(dummyEventTime(), 200)
        advanceUntilIdle()

        assertTrue(firstClosedWhenSecondCreated)
        assertTrue(firstEffect.isClosed)
        assertEquals(2, fakeFactory.createCalls.size)
    }

    // 6. detachAndRelease removes listener from player and closes manager
    @Test
    fun detachAndRelease_removes_listener_and_closes_manager() = testScope.runTest {
        playerFake.currentAudioSessionId = 100
        attachBridge()
        advanceUntilIdle()
        val effect = fakeFactory.lastCreated!!
        assertEquals(1, playerFake.listeners.size)

        bridge.detachAndRelease(null)
        advanceUntilIdle()

        assertTrue(bridge.isClosed)
        assertTrue(manager.isClosed)
        assertTrue(effect.isClosed)
        assertEquals(0, playerFake.listeners.size)
    }

    // 7. Repeated detachAndRelease is idempotent
    @Test
    fun repeated_detachAndRelease_is_idempotent() = testScope.runTest {
        playerFake.currentAudioSessionId = 100
        attachBridge()
        advanceUntilIdle()

        bridge.detachAndRelease(null)
        advanceUntilIdle()
        assertTrue(bridge.isClosed)

        // Second call
        bridge.detachAndRelease(null)
        advanceUntilIdle()
        assertTrue(bridge.isClosed)
    }

    // 8. Callbacks after detachAndRelease are ignored
    @Test
    fun callbacks_after_detachAndRelease_are_ignored() = testScope.runTest {
        playerFake.currentAudioSessionId = 100
        attachBridge()
        advanceUntilIdle()
        assertEquals(1, fakeFactory.createCalls.size)

        val listenersBeforeDetach = playerFake.listeners.toList()
        bridge.detachAndRelease(null)
        advanceUntilIdle()

        // Directly invoke old listener if held externally
        for (listener in listenersBeforeDetach) {
            listener.onAudioSessionIdChanged(dummyEventTime(), 300)
        }
        advanceUntilIdle()

        // Must not create new effect
        assertEquals(1, fakeFactory.createCalls.size)
        assertTrue(manager.isClosed)
    }

    // 9. Manager creation failure does not crash bridge or player
    @Test
    fun manager_creation_failure_does_not_crash_bridge_or_player() = testScope.runTest {
        fakeFactory.throwOnCreate = UnsupportedOperationException("Device lacks equalizer engine")
        playerFake.currentAudioSessionId = 100

        // Attach must not throw
        attachBridge()
        advanceUntilIdle()

        // State degrades safely to NoSession with failure reason
        val state = manager.state.value
        assertTrue(state is EqualizerState.NoSession)
        val failure = (state as EqualizerState.NoSession).failureReason
        assertTrue(failure is EqualizerFailureReason.UnsupportedHardwareOrRoute)

        // Player remains healthy and listeners intact
        assertEquals(1, playerFake.listeners.size)
    }

    // 10. Initial session snapshot superseded before processing is drained and does not overwrite newer callback
    @Test
    fun initial_session_cannot_overwrite_newer_session_callback() = testScope.runTest {
        bridge.attachToSessionSource(
            currentAudioSessionId = 100,
            addListener = { listener ->
                // Simulate that ExoPlayer immediately delivers session 200 during listener registration
                listener.onAudioSessionIdChanged(dummyEventTime(), 200)
            },
            removeListener = {}
        )
        advanceUntilIdle()

        // Explicit generation semantics and draining ensure stale initial snapshot 100 is superseded by 200
        assertEquals(listOf(200), fakeFactory.createCalls)
        val state = manager.state.value
        assertTrue(state is EqualizerState.AvailableDisabled)
    }

    // 11. Non-monotonic numeric session IDs over time are processed in exact arrival order
    @Test
    fun non_monotonic_session_ids_processed_in_exact_arrival_order_leaving_latest_observed_session() = testScope.runTest {
        playerFake.currentAudioSessionId = 42
        attachBridge()
        advanceUntilIdle()
        assertEquals(listOf(42), fakeFactory.createCalls)

        // Sequence of arbitrary, non-monotonic session IDs over time: 42 -> 15 -> 88 -> 15
        playerFake.notifyAudioSessionIdChanged(dummyEventTime(), 15)
        advanceUntilIdle()
        assertEquals(listOf(42, 15), fakeFactory.createCalls)

        playerFake.notifyAudioSessionIdChanged(dummyEventTime(), 88)
        advanceUntilIdle()
        assertEquals(listOf(42, 15, 88), fakeFactory.createCalls)

        playerFake.notifyAudioSessionIdChanged(dummyEventTime(), 15)
        advanceUntilIdle()
        assertEquals(listOf(42, 15, 88, 15), fakeFactory.createCalls)

        val state = manager.state.value
        assertTrue(state is EqualizerState.AvailableDisabled)
    }

    // --- Test Doubles ---




    private class FakeAudioSessionPlayer {
        var currentAudioSessionId: Int = C.AUDIO_SESSION_ID_UNSET
        val listeners = mutableListOf<AnalyticsListener>()

        fun addListener(listener: AnalyticsListener) {
            listeners.add(listener)
        }

        fun removeListener(listener: AnalyticsListener) {
            listeners.remove(listener)
        }

        fun notifyAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, newSessionId: Int) {
            currentAudioSessionId = newSessionId
            for (listener in listeners.toList()) {
                listener.onAudioSessionIdChanged(eventTime, newSessionId)
            }
        }
    }

    private class FakeEqualizerFactory : PlatformEqualizerFactory {
        val createCalls = mutableListOf<Int>()
        var throwOnCreate: Exception? = null
        var lastCreated: FakeEqualizerInstance? = null
        var onInstanceCreated: ((FakeEqualizerInstance) -> Unit)? = null

        override fun create(audioSessionId: Int): PlatformEqualizer? {
            createCalls.add(audioSessionId)
            throwOnCreate?.let { throw it }
            val instance = FakeEqualizerInstance()
            lastCreated = instance
            onInstanceCreated?.invoke(instance)
            return instance
        }
    }

    private class FakeEqualizerInstance : PlatformEqualizer {
        var isClosed = false
        var controlListener: ((Boolean) -> Unit)? = null

        override val numberOfBands: Short = 5
        override val bandLevelRange: ShortArray = shortArrayOf(-1500, 1500)
        override val numberOfPresets: Short = 2
        override var currentPreset: Short = 0
        override var enabled: Boolean = false

        override fun getCenterFreq(band: Short): Int = 1000000
        override fun getBandLevel(band: Short): Short = 0
        override fun setBandLevel(band: Short, level: Short) {}
        override fun getPresetName(preset: Short): String = "Preset $preset"
        override fun usePreset(preset: Short) {}

        override fun setControlStatusListener(listener: (controlGranted: Boolean) -> Unit) {
            controlListener = listener
        }

        override fun close() {
            isClosed = true
        }
    }
}
