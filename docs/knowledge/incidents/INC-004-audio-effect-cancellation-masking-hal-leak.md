# Incident & Defect Record: INC-004 AudioEffect Native HAL Leak from Scope Cancellation

## 1. Incident Overview
- **Incident ID:** `INC-004`
- **Date Detected:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Target Subsystem:** Harmonia Mobile / Platform Audio Effects (`harmonia-mobile/app/`)
- **Milestone / Phase:** Stage M5-C2 Platform Equalizer Integration
- **Severity Classification:** **HIGH**
- **Investigation Status:** **RESOLVED & VERIFIED** (Contractual Lifecycle) / **VALIDATION PENDING** (Hardware HAL)
- **Confidence Rating:** `[VERIFIED-SRC]` (Coroutine Cancellation & Release Contract) / `[REPORT-BACKED]` `[VALIDATION-PENDING]` (Physical HAL Slot Exhaustion)

---

## 2. Symptoms & User/System Impact
- **Observed Behavior:**
  When stopping the playback service or swiping Harmonia away from Android recents while audio was playing, coroutine scope cancellation during teardown risked aborting cleanup before `activeEffect?.close()` was executed. In historical OEM defect reports and platform documentation, unclosed effect instances cause Android's `audioserver` process to log `AudioEffect: release() called with active audio effect slot open`, eventually exhausting system-wide effect slots (often 4 to 8 instances) and throwing `RuntimeException: Cannot initialize audio effect: status -1` `[REPORT-BACKED]`. (Actual physical hardware slot exhaustion is an OS-level phenomenon that cannot be demonstrated in a JVM fake test and remains pending physical device validation `[VALIDATION-PENDING]`).
- **User Impact:**
  If native audio effect handles leak across repeated app closures, platform equalisation becomes unavailable system-wide until the device is rebooted.
- **Reproduction Steps:**
  1. Enable Equalizer and play music.
  2. Swipe the app away from recent tasks to trigger service destruction.
  3. Reopen the app and start playback.
  4. Repeat steps 2 and 3 across multiple lifecycles.
  5. On devices without proactive HAL cleanup, unreleased effect handles exhaust available slots.

---

## 3. Root Cause Analysis
- **Root Cause & Scope Cancellation:**
  When [`HarmoniaPlaybackService.onDestroy()`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/HarmoniaPlaybackService.kt) ran, it cancelled the service's coroutine scope before invoking teardown. Inside `DefaultAudioEffectManager.release()`, executing coroutine code within a cancelled scope caused `CancellationException` to be thrown immediately, preventing execution from ever reaching `activeEffect?.close()`. Because `close()` was never called on the underlying `android.media.audiofx.Equalizer` instance, the native handle remained open `[VERIFIED-SRC]`.
- **Hypotheses Considered & Disproven:**
  - *Hypothesis A: ExoPlayer was holding the AudioTrack open:* Disproven. ExoPlayer was released, but the separate `AudioEffect` handle created on that session ID remained allocated in the OS audio server.
  - *Hypothesis B: Java Garbage Collector cleans up effects automatically:* Disproven. Finalizers on Android `AudioEffect` are non-deterministic and fail to release native HAL handles promptly before process unbinding.

---

## 4. Corrective Action & Implementation
- **Remediation Summary:**
  Enforced non-cancellable execution for release and synchronized teardown via `runBlocking(NonCancellable)`.
- **Code Modifications:**
  - In [`DefaultAudioEffectManager.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/DefaultAudioEffectManager.kt):
    ```kotlin
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
    ```
  - In [`PlaybackServiceEqualizerBridge.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/PlaybackServiceEqualizerBridge.kt): `detachAndRelease()` synchronously executes `audioEffectManager.close()` within a synchronized lock.
- **Alternatives Rejected:**
  - *Launching async release in GlobalScope:* Rejected. `GlobalScope.launch` is non-deterministic and may execute after the service process has unbound, failing to guarantee release.

---

## 5. Regression Prevention & Invariant Protection
- **Regression Test Suite:**
  - Test File: [`AudioEffectManagerTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/service/equalizer/AudioEffectManagerTest.kt)
  - Test Method: `release_remains_safe_when_ordinary_scope_is_cancelled()`
  - Execution Output: Verified passing with 0 errors `[VERIFIED-TEST]`.
  - Evidence Boundary & Limitation: This test verifies the Kotlin coroutine release contract against a test fake (`PlatformEqualizer`), proving that cancelling the manager's scope does not skip or abort `effect.close()`. It does not execute native Android C++ `audioserver` code; physical hardware validation of native HAL slot recovery remains `[VALIDATION-PENDING]`.
- **Durable Architectural Invariant:**
  - Audio effect teardown must execute under `NonCancellable` and must complete synchronously during service destruction to guarantee execution of the release contract before service unbinding `[VERIFIED-SRC]`.

---

## 6. Related Documentation & Architecture
- **Phase Record:** [`mobile_m5c2_audio_session_integration.md`](../phases/mobile_m5c2_audio_session_integration.md)
- **ADR Reference:** [`ADR-034`](../adr/ADR-034-audio-effect-lifecycle-and-teardown.md)
