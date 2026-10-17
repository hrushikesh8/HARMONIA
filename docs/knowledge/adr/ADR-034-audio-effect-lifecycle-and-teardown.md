# ADR-034: Deterministic Audio Effect Lifecycle, Asynchronous Event Decoupling & Synchronous Teardown

## 1. Metadata & Status
- **Identifier:** `ADR-034`
- **Title:** Deterministic Audio Effect Lifecycle, Asynchronous Event Decoupling & Synchronous Teardown
- **Status:** **ACCEPTED** `[REPORT-BACKED]` / **VALIDATION PENDING** for physical hardware audio HAL `[VALIDATION-PENDING]`
- **Date:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Subsystem:** Harmonia Mobile (`harmonia-mobile/app/`)
- **Preceding Decisions:** [`ADR-020`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs), [`ADR-033`](ADR-033-equalizer-persisted-user-intent.md)
- **Phase Traceability:** [`mobile_m5c2_audio_session_integration.md`](../phases/mobile_m5c2_audio_session_integration.md)

---

## 2. Context & Problem Formulation
Android's `android.media.audiofx.AudioEffect` framework imposes stringent lifecycle constraints:
1. **Thread Blocking Risks:** Initializing an `android.media.audiofx.Equalizer` on Android's main or ExoPlayer playback thread can block for 50–200ms depending on OEM audio HAL responsiveness, risking playback stutter or ANRs.
2. **Rapid Skipping Races:** When a user rapidly skips multiple tracks, ExoPlayer fires successive `AnalyticsListener.onAudioSessionIdChanged` events in quick succession. Without synchronization, asynchronous responses can resolve out of order, applying an obsolete session ID after a newer one.
3. **Audio HAL Resource Leaks:** Android limits the maximum number of simultaneous audio effect instances (frequently 4 to 8 system-wide). If an active effect is not explicitly and synchronously closed when the playback service is destroyed, native handles leak into the `audioserver` process until garbage collection or process death.

---

## 3. Decision
1. **Decoupled Asynchronous Event Channel for Playback Transitions:**
   - In [`PlaybackServiceEqualizerBridge.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/PlaybackServiceEqualizerBridge.kt), `onAudioSessionIdChanged` simply pushes a `SessionEvent(generation, sessionId)` onto an unbounded Kotlin coroutine `Channel<SessionEvent>`.
   - A dedicated worker on `bridgeScope` drains the channel, drops obsolete generations, and dispatches the latest valid session to [`DefaultAudioEffectManager`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/DefaultAudioEffectManager.kt) on a background dispatcher, ensuring the ExoPlayer playback thread is never blocked.
2. **Monotonic Generation Fencing:**
   - Generation counters (`latestEnqueuedGeneration`, `latestProcessedGeneration`, `activeGeneration`) ensure that delayed platform query results from prior sessions are discarded immediately without mutating active state.
3. **Synchronous Teardown under `NonCancellable`:**
   - When [`HarmoniaPlaybackService.onDestroy()`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/HarmoniaPlaybackService.kt) runs, teardown must **not** be fire-and-forget asynchronous.
   - `detachAndRelease(player)` cancels the bridge scope, closes the channel, and calls `audioEffectManager.close()` synchronously.
   - Inside `DefaultAudioEffectManager`:
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
   - Native effect closing (`effect.close()`) is executed synchronously under `NonCancellable` to ensure the audio HAL handle is released before service destruction completes.

---

## 4. Alternatives Considered & Historical Reconciliation
- **Alternative A: Purely Asynchronous Background Release on Teardown:**  
  *Rejected & Reconciled:* An early historical review note hypothesized that teardown had switched to asynchronous release to avoid main-thread deadlocks. Investigation of the verified source code proved this hypothesis incorrect: releasing asynchronously during `onDestroy` causes a race condition where the service process unbinds while native `AudioEffect` handles remain open in the audio HAL. The architecture explicitly chose **synchronous release under `NonCancellable`** for teardown, combined with **asynchronous channel decoupling** for runtime session-change events.
- **Alternative B: Synchronous Effect Creation in ExoPlayer Analytics Listener:**  
  *Rejected:* Causes noticeable audio dropouts and UI frame skips when skipping tracks.

---

## 5. Consequences & Trade-offs
### Positive Consequences:
- ExoPlayer playback thread remains lightweight and non-blocking during rapid track skips.
- Zero audio HAL handle leaks: native audio effects are deterministically closed before service destruction.
- Safe from coroutine scope cancellation: wrapping in `NonCancellable` ensures cleanup completes even if the parent scope was cancelled.

### Negative Consequences:
- `runBlocking(NonCancellable)` inside `close()` briefly waits for mutex acquisition during service destruction (bounded by local lock release).

---

## 6. Durable Architectural Invariants
1. Session-change notifications from ExoPlayer must be queued asynchronously via `Channel` and stamped with monotonic generations.
2. Service destruction must invoke `audioEffectManager.close()` synchronously, ensuring native audio HAL handles are closed before process unbinding.
3. Transient pauses must **not** close or release the audio session or broadcast `ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION`.

---

## 7. Evidence & Traceability
- **Implementation Source:** [`PlaybackServiceEqualizerBridge.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/PlaybackServiceEqualizerBridge.kt), [`DefaultAudioEffectManager.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/DefaultAudioEffectManager.kt) `[VERIFIED-SRC]`
- **Test Evidence:** [`PlaybackServiceEqualizerBridgeTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/service/equalizer/PlaybackServiceEqualizerBridgeTest.kt) (11 `@Test` methods passed), [`AudioEffectManagerTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/service/equalizer/AudioEffectManagerTest.kt) (22 `@Test` methods passed) `[REPORT-BACKED]`
- **Related Incidents:** [`INC-003`](../incidents/INC-003-rapid-audio-session-race-and-generation-fencing.md), [`INC-004`](../incidents/INC-004-audio-effect-cancellation-masking-hal-leak.md)
