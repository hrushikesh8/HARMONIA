package org.harmonia.player.service.equalizer

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator
import org.harmonia.player.domain.model.equalizer.EqualizerBand
import org.harmonia.player.domain.model.equalizer.EqualizerFailureReason
import org.harmonia.player.domain.model.equalizer.EqualizerPreset
import org.harmonia.player.domain.model.equalizer.EqualizerState
import org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource
import org.harmonia.player.domain.model.equalizer.PlatformEqualizer
import org.harmonia.player.domain.model.equalizer.PlatformEqualizerFactory

/**
 * Concrete, serialized audio effect manager implementing [AudioEqualizerCoordinator].
 *
 * Concurrency Invariants:
 * 1. A single coroutine [Mutex] serializes all lifecycle transitions, operations, and callbacks.
 * 2. Monotonically increasing generation IDs prevent stale callbacks from modifying active state.
 * 3. Release is idempotent and completes even if the manager's CoroutineScope is cancelled.
 * 4. Operations queued during or after release fail closed immediately and never touch a released effect.
 * 5. Session ID <= 0 never allocates an effect.
 * 6. Partial initialization failures or cancellations clean up allocated effects and propagate cancellation.
 * 7. Real platform capability queries without fabricated defaults.
 * 8. Never publishes enabled/disabled states if the underlying native operation failed.
 */
class DefaultAudioEffectManager(
    private val platformEqualizerFactory: PlatformEqualizerFactory,
    private val userIntentSource: EqualizerUserIntentSource,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val mutex: Mutex = Mutex()
) : AudioEqualizerCoordinator, AutoCloseable {

    private val _state = MutableStateFlow<EqualizerState>(EqualizerState.NoSession())
    override val state: StateFlow<EqualizerState> = _state.asStateFlow()

    @Volatile
    private var isReleased = false

    private var activeGeneration: Long = 0L
    private var currentSessionId: Int = 0
    private var activeEffect: PlatformEqualizer? = null

    val isClosed: Boolean
        get() = isReleased

    suspend fun onAudioSessionIdChanged(sessionId: Int) {
        mutex.withLock {
            if (isReleased) return

            if (sessionId <= 0) {
                // Invalid or unset session: release existing effect and enter NoSession
                currentSessionId = sessionId
                activeGeneration++
                safeCloseEffect()
                _state.value = EqualizerState.NoSession()
                return
            }

            // Valid session: check for repeated binding of the same session
            if (sessionId == currentSessionId && activeEffect != null) {
                return // Avoid duplicate allocation
            }

            // Session changed: release previous effect before creating replacement
            activeGeneration++
            val generation = activeGeneration
            safeCloseEffect()
            currentSessionId = sessionId

            var partiallyCreatedEffect: PlatformEqualizer? = null
            try {
                val effect = platformEqualizerFactory.create(sessionId)
                if (effect == null) {
                    _state.value = EqualizerState.NoSession(
                        failureReason = EqualizerFailureReason.UnsupportedHardwareOrRoute("Factory returned null")
                    )
                    return
                }
                partiallyCreatedEffect = effect
                activeEffect = effect

                val bandRange = queryBandLevelRange(effect)
                val bands = queryBands(effect, bandRange)
                val presets = queryPresets(effect)

                // Wire control status listener guarded by instance generation
                effect.setControlStatusListener { controlGranted ->
                    scope.launch {
                        onControlStatusChanged(generation, controlGranted)
                    }
                }

                // Reconcile user intent
                val isUserEnabled = userIntentSource.isEqualizerEnabled()
                if (isUserEnabled) {
                    effect.enabled = true
                    _state.value = EqualizerState.EnabledWithControl(
                        bandLevelRange = bandRange,
                        bands = bands,
                        presets = presets,
                        currentPresetIndex = effect.currentPreset
                    )
                } else {
                    effect.enabled = false
                    _state.value = EqualizerState.AvailableDisabled(
                        bandLevelRange = bandRange,
                        bands = bands,
                        presets = presets
                    )
                }
            } catch (ce: CancellationException) {
                activeGeneration++
                activeEffect = null
                try {
                    partiallyCreatedEffect?.close()
                } catch (cleanupEx: CancellationException) {
                    throw cleanupEx
                } catch (cleanupEx: Exception) {
                    ce.addSuppressed(cleanupEx)
                }
                _state.value = EqualizerState.NoSession()
                throw ce
            } catch (e: Exception) {
                activeGeneration++
                activeEffect = null
                try {
                    partiallyCreatedEffect?.close()
                } catch (cleanupEx: CancellationException) {
                    throw cleanupEx
                } catch (cleanupEx: Exception) {
                    e.addSuppressed(cleanupEx)
                }
                classifyAndHandleCreationFailure(e)
            }
        }
    }

    private suspend fun onControlStatusChanged(callbackGeneration: Long, controlGranted: Boolean) {
        mutex.withLock {
            if (isReleased || callbackGeneration != activeGeneration || activeEffect == null) {
                return // Drop stale or post-release callback
            }

            val currentState = _state.value
            val (bandRange, bands, presets) = extractMetadata(currentState) ?: return

            if (!controlGranted) {
                // Control lost: preserve actual last known runtime enabled state
                val wasActuallyEnabled = currentState is EqualizerState.EnabledWithControl
                _state.value = EqualizerState.ControlLost(
                    lastKnownEnabled = wasActuallyEnabled,
                    bandLevelRange = bandRange,
                    bands = bands,
                    presets = presets
                )
            } else {
                // Control regained: reconcile strictly with latest user intent
                val latestUserEnabled = userIntentSource.isEqualizerEnabled()
                val effect = activeEffect ?: return
                try {
                    if (latestUserEnabled) {
                        effect.enabled = true
                        _state.value = EqualizerState.EnabledWithControl(
                            bandLevelRange = bandRange,
                            bands = bands,
                            presets = presets,
                            currentPresetIndex = effect.currentPreset
                        )
                    } else {
                        effect.enabled = false
                        _state.value = EqualizerState.AvailableDisabled(
                            bandLevelRange = bandRange,
                            bands = bands,
                            presets = presets
                        )
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    classifyAndHandleOperationFailure(e)
                }
            }
        }
    }

    override suspend fun setEnabled(enabled: Boolean): Boolean {
        return mutex.withLock {
            if (isReleased) return@withLock false
            userIntentSource.setEqualizerEnabled(enabled)

            val effect = activeEffect
            val currentState = _state.value

            if (effect == null || currentState is EqualizerState.NoSession) {
                // Intent persisted, state remains NoSession until valid session arrives
                return@withLock true
            }

            if (currentState is EqualizerState.ControlLost) {
                // Control is lost: intent is recorded in userIntentSource, but native effect cannot be toggled
                return@withLock true
            }

            val (bandRange, bands, presets) = extractMetadata(currentState) ?: return@withLock false

            try {
                if (enabled) {
                    effect.enabled = true
                    _state.value = EqualizerState.EnabledWithControl(
                        bandLevelRange = bandRange,
                        bands = bands,
                        presets = presets,
                        currentPresetIndex = effect.currentPreset
                    )
                } else {
                    effect.enabled = false
                    _state.value = EqualizerState.AvailableDisabled(
                        bandLevelRange = bandRange,
                        bands = bands,
                        presets = presets
                    )
                }
                true
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                classifyAndHandleOperationFailure(e)
                false
            }
        }
    }

    override suspend fun setBandLevel(band: Short, levelMb: Short): Boolean {
        return mutex.withLock {
            if (isReleased) return@withLock false
            val effect = activeEffect ?: return@withLock false
            val currentState = _state.value
            if (currentState !is EqualizerState.EnabledWithControl && currentState !is EqualizerState.AvailableDisabled) {
                return@withLock false
            }

            try {
                effect.setBandLevel(band, levelMb)
                val (bandRange, _, presets) = extractMetadata(currentState) ?: return@withLock false
                val updatedBands = queryBands(effect, bandRange)

                if (currentState is EqualizerState.EnabledWithControl) {
                    _state.value = EqualizerState.EnabledWithControl(
                        bandLevelRange = bandRange,
                        bands = updatedBands,
                        presets = presets,
                        currentPresetIndex = effect.currentPreset
                    )
                } else {
                    _state.value = EqualizerState.AvailableDisabled(
                        bandLevelRange = bandRange,
                        bands = updatedBands,
                        presets = presets
                    )
                }
                true
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                handleBandOrPresetFailure(e)
                false
            }
        }
    }

    override suspend fun usePreset(presetIndex: Short): Boolean {
        return mutex.withLock {
            if (isReleased) return@withLock false
            val effect = activeEffect ?: return@withLock false
            val currentState = _state.value
            if (currentState !is EqualizerState.EnabledWithControl && currentState !is EqualizerState.AvailableDisabled) {
                return@withLock false
            }

            try {
                effect.usePreset(presetIndex)
                val (bandRange, _, presets) = extractMetadata(currentState) ?: return@withLock false
                val updatedBands = queryBands(effect, bandRange)

                if (currentState is EqualizerState.EnabledWithControl) {
                    _state.value = EqualizerState.EnabledWithControl(
                        bandLevelRange = bandRange,
                        bands = updatedBands,
                        presets = presets,
                        currentPresetIndex = effect.currentPreset
                    )
                } else {
                    _state.value = EqualizerState.AvailableDisabled(
                        bandLevelRange = bandRange,
                        bands = updatedBands,
                        presets = presets
                    )
                }
                true
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                handleBandOrPresetFailure(e)
                false
            }
        }
    }

    suspend fun release() {
        withContext(NonCancellable) {
            mutex.withLock {
                if (isReleased) return@withLock
                isReleased = true
                activeGeneration++
                safeCloseEffect()
                currentSessionId = 0
                _state.value = EqualizerState.NoSession()
            }
        }
    }

    override fun close() {
        runBlocking(NonCancellable) {
            release()
        }
    }

    private fun safeCloseEffect() {
        val effect = activeEffect
        activeEffect = null
        if (effect != null) {
            try {
                effect.close()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Exception) {}
        }
    }

    private fun queryBandLevelRange(effect: PlatformEqualizer): IntRange {
        val range = effect.bandLevelRange
        require(range.size >= 2) { "bandLevelRange must contain at least 2 elements [min, max]" }
        return range[0].toInt()..range[1].toInt()
    }

    private fun queryBands(effect: PlatformEqualizer, bandRange: IntRange): List<EqualizerBand> {
        val count = effect.numberOfBands.toInt()
        if (count <= 0) return emptyList()
        val min = bandRange.first.toShort()
        val max = bandRange.last.toShort()
        return (0 until count).map { i ->
            val bandIndex = i.toShort()
            val centerFreqMhz = effect.getCenterFreq(bandIndex)
            val currentLevel = effect.getBandLevel(bandIndex)
            EqualizerBand(
                index = bandIndex,
                centerFreqHz = centerFreqMhz / 1000,
                currentLevelMb = currentLevel,
                minLevelMb = min,
                maxLevelMb = max
            )
        }
    }

    private fun queryPresets(effect: PlatformEqualizer): List<EqualizerPreset> {
        val count = effect.numberOfPresets.toInt()
        if (count <= 0) return emptyList()
        return (0 until count).map { i ->
            val presetIndex = i.toShort()
            val name = effect.getPresetName(presetIndex)
            EqualizerPreset(index = presetIndex, name = name)
        }
    }

    private fun extractMetadata(state: EqualizerState): Triple<IntRange, List<EqualizerBand>, List<EqualizerPreset>>? {
        return when (state) {
            is EqualizerState.EnabledWithControl -> Triple(state.bandLevelRange, state.bands, state.presets)
            is EqualizerState.AvailableDisabled -> Triple(state.bandLevelRange, state.bands, state.presets)
            is EqualizerState.ControlLost -> Triple(state.bandLevelRange, state.bands, state.presets)
            is EqualizerState.NoSession -> null
        }
    }

    private fun classifyAndHandleCreationFailure(e: Exception) {
        val reason = classifyException(e)
        safeCloseEffect()
        _state.value = EqualizerState.NoSession(failureReason = reason)
    }

    private fun classifyAndHandleOperationFailure(e: Exception) {
        val effect = activeEffect
        activeEffect = null
        if (effect != null) {
            try {
                effect.close()
            } catch (cleanupEx: CancellationException) {
                throw cleanupEx
            } catch (cleanupEx: Exception) {
                e.addSuppressed(cleanupEx)
            }
        }
        val reason = classifyException(e)
        _state.value = EqualizerState.NoSession(failureReason = reason)
    }

    private fun handleBandOrPresetFailure(e: Exception) {
        when (e) {
            is IllegalArgumentException -> {
                // Invalid band or preset parameter: programming/argument error, do not teardown healthy effect
            }
            is UnsupportedOperationException,
            is IllegalStateException,
            is RuntimeException -> {
                // Underlying native effect failure: teardown safely and enter NoSession
                val effect = activeEffect
                activeEffect = null
                if (effect != null) {
                    try {
                        effect.close()
                    } catch (cleanupEx: CancellationException) {
                        throw cleanupEx
                    } catch (cleanupEx: Exception) {
                        e.addSuppressed(cleanupEx)
                    }
                }
                val reason = classifyException(e)
                _state.value = EqualizerState.NoSession(failureReason = reason)
            }
            else -> throw e
        }
    }

    private fun classifyException(e: Exception): EqualizerFailureReason {
        return when (e) {
            is UnsupportedOperationException -> EqualizerFailureReason.UnsupportedHardwareOrRoute(e.message)
            is IllegalArgumentException -> EqualizerFailureReason.InvalidSessionOrArgument(e.message)
            is IllegalStateException -> EqualizerFailureReason.NativeIllegalState(e.message)
            is RuntimeException -> EqualizerFailureReason.UnexpectedNativeError(e.message, e.javaClass.simpleName)
            else -> EqualizerFailureReason.UnexpectedNativeError(e.message, e.javaClass.simpleName)
        }
    }
}
