# ADR-035: Coroutine Sleep Timer Monotonic Clock Suspension, Baseline Volume Safety & Transition Handling

## 1. Metadata & Status
- **Identifier:** `ADR-035`
- **Title:** Coroutine Sleep Timer Monotonic Clock Suspension, Baseline Volume Safety & Transition Handling
- **Status:** **ACCEPTED** `[REPORT-BACKED]`
- **Date:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Subsystem:** Harmonia Mobile (`harmonia-mobile/app/`)
- **Preceding Decisions:** [`ADR-020`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs), [`ADR-031`](../../MOBILE_M4_ARCHITECTURE.md#27-architectural-decision-records-adrs)
- **Phase Traceability:** [`mobile_m5d_sleep_timer.md`](../phases/mobile_m5d_sleep_timer.md)

---

## 2. Context & Problem Formulation
Implementing an audio player sleep timer involves subtle audio and lifecycle pitfalls:
1. **Wall-Clock Drift & Sleeping Device Ticks:** Using `System.currentTimeMillis()` or simple `delay()` calculations fails when the user pauses playback (e.g. for a phone call). A timer that continues counting down while paused would expire while the user is away and immediately silence future resumed playback.
2. **Destructive Volume Attenuation:** Fading out the audio (e.g. lowering player volume from 1.0 to 0.0) permanently lowers the user's master volume if the timer is cancelled, interrupted, or reset without restoring the pre-fade baseline.
3. **Repeat-Mode Boundary Collision:** If a sleep timer is set to "End of Track" while the user has "Repeat One" enabled, ExoPlayer loops the track seamlessly without firing standard queue-completion events, causing the player to play indefinitely.

---

## 3. Decision
1. **Monotonic Clock Suspension:**
   - Abstract the time source into [`MonotonicClock.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/audio/SleepTimerState.kt) backed by `SystemClock.elapsedRealtime()`.
   - When playback transitions to paused (`PAUSED_BY_USER` or `PAUSED_BY_FOCUS`), the remaining duration is captured, and the countdown loop is suspended. When playback resumes, the target deadline is recalculated using the monotonic clock.
2. **Unified 30-Second Bounded Fade Window:**
   - Standardize all volume fade-outs to **exactly 30 seconds** (`FADE_WINDOW_MS = 30_000L`).
   - If the requested sleep duration is shorter than 30 seconds, bound the fade window to the duration itself.
3. **Pre-Fade Baseline Volume Capture & Restoration:**
   - Capture `baselineVolume = player.volume` prior to initiating the linear fade.
   - If the timer is cancelled, reconfigured, or stopped, restore `player.volume` immediately to `baselineVolume`.
   - If the user manually changes system volume during an active fade, adopt the new user setting as the revised baseline.
4. **Repeat Mode Overriding on End-of-Track:**
   - In "End of Track" mode, monitor track transition events directly. When the active track finishes its current iteration, stop playback even if repeat mode is set to repeat-one, fulfilling the explicit user intent to sleep.
5. **Job Cancellation Mutex:**
   - Protect active timer jobs with explicit cancellation tokens to prevent race conditions when starting a new timer while a previous fade is still in flight.

---

## 4. Alternatives Considered
- **Alternative A: AlarmManager / System PendingIntents:**  
  *Rejected:* Wakes up device CPU unnecessarily, lacks smooth volume attenuation capabilities, and cannot suspend seamlessly when playback is paused.
- **Alternative B: Logarithmic / Exponential Audio Fade Curves:**  
  *Rejected:* Adds floating point overhead; linear attenuation over 30 seconds is perceptually smooth and sufficient for music playback.

---

## 5. Consequences & Trade-offs
### Positive Consequences:
- Non-destructive: user volume is never permanently attenuated after a sleep timer finishes.
- Pausing playback suspends the timer countdown, preventing unexpected timer expirations.
- Deterministic coroutine testing via `TestScope` and fake `MonotonicClock`.

### Negative Consequences:
- Requires holding an active coroutine job within `HarmoniaPlayerController` while the timer is running.

---

## 6. Durable Architectural Invariants
1. `player.volume` must be restored to `baselineVolume` whenever a sleep timer is cancelled, reset, or when playback stops.
2. Countdown time must not elapse while the player is in a paused state.
3. End-of-track sleep timers must stop playback upon track boundary crossing regardless of active repeat settings.

---

## 7. Evidence & Traceability
- **Implementation Source:** [`HarmoniaPlayerController.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt), [`SleepTimerState.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/audio/SleepTimerState.kt) `[VERIFIED-SRC]`
- **Test Evidence:** [`HarmoniaSleepTimerTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/playback/HarmoniaSleepTimerTest.kt) (14 `@Test` methods across 8 scenario categories passed) `[REPORT-BACKED]`
- **Related Incidents:** [`INC-005`](../incidents/INC-005-sleep-timer-repeat-one-infinite-loop.md)
