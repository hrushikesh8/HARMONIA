# Incident & Defect Record: INC-003 Rapid Audio Session Race & Generation Invalidation

## 1. Incident Overview
- **Incident ID:** `INC-003`
- **Date Detected:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Target Subsystem:** Harmonia Mobile / Platform Audio Effects (`harmonia-mobile/app/`)
- **Milestone / Phase:** Stage M5-C2 Platform Equalizer Integration
- **Severity Classification:** **MEDIUM**
- **Investigation Status:** **RESOLVED & VERIFIED**
- **Confidence Rating:** `[VERIFIED-SRC]`

---

## 2. Symptoms & User/System Impact
- **Observed Behavior:**
  When skipping through multiple tracks rapidly (e.g. tapping "Next" 5 times in two seconds), the equalizer state intermittently disconnected from the active audio stream. The Settings UI reported "No active audio session" or controlled an obsolete audio session, leaving the currently playing track un-equalized.
- **User Impact:**
  User lost audio enhancement effects while skipping music; equalizer sliders had no audible effect on the newly playing track until playback was stopped and restarted.
- **Reproduction Steps:**
  1. Enable Equalizer in Settings.
  2. Start playback of an album.
  3. Rapidly tap "Next Track" multiple times before each track finishes buffering.
  4. Inspect active audio session ID in `DefaultAudioEffectManager` vs ExoPlayer `audioSessionId`.

---

## 3. Root Cause Analysis
- **Confirmed Root Cause (Evidence-Backed):**
  ExoPlayer fires `AnalyticsListener.onAudioSessionIdChanged` on its playback thread. Each event was dispatched asynchronously to query platform equalizer capabilities on a background dispatcher. Because asynchronous coroutines complete non-deterministically, the query response for an earlier session ID (e.g. Session 101) finished *after* the query response for a later session ID (e.g. Session 105), overwriting the active session with an obsolete handle.
- **Hypotheses Considered & Disproven:**
  - *Hypothesis A: ExoPlayer allocated session ID 0 during track transitions:* Disproven. ExoPlayer allocated valid session IDs, but older session responses arrived out of order.
  - *Hypothesis B: Native Equalizer crashed on recreation:* Disproven. Platform equalizer instantiation succeeded, but pointed to the abandoned session.

---

## 4. Corrective Action & Implementation
- **Remediation Summary:**
  Implemented monotonic generation stamping and channel batch draining in [`PlaybackServiceEqualizerBridge.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/PlaybackServiceEqualizerBridge.kt) and [`DefaultAudioEffectManager.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/DefaultAudioEffectManager.kt).
- **Code Modifications:**
  - `PlaybackServiceEqualizerBridge.kt`: Enqueues `SessionEvent(generation, sessionId)`. When reading from the channel, it drains all intermediate pending events in one pass and only processes the highest generation number.
  - `DefaultAudioEffectManager.kt`: Increments `activeGeneration` on every state change and mutex acquisition; any platform callback returning with `generation < activeGeneration` is silently dropped.
- **Alternatives Rejected:**
  - *Synchronous Processing on Player Thread:* Rejected. Calling platform audio HAL queries synchronously on the playback thread causes audio clicks and UI ANRs.

---

## 5. Regression Prevention & Invariant Protection
- **Regression Test Suite:**
  - Test Files: [`PlaybackServiceEqualizerBridgeTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/service/equalizer/PlaybackServiceEqualizerBridgeTest.kt), [`AudioEffectManagerTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/service/equalizer/AudioEffectManagerTest.kt)
  - Test Methods:
    - `AudioEffectManagerTest.kt`: `stale_callbacks_from_previous_generations_are_ignored()` `[VERIFIED-TEST]`
    - `PlaybackServiceEqualizerBridgeTest.kt`: `initial_session_cannot_overwrite_newer_session_callback()` and `non_monotonic_session_ids_processed_in_exact_arrival_order_leaving_latest_observed_session()` `[VERIFIED-TEST]`
  - Execution Output: Verified clean with 0 errors across all audio effect test suites.
- **Durable Architectural Invariant:**
  - Audio session state transitions must carry monotonic generation stamps; obsolete generations must be dropped before touching platform effect handles `[VERIFIED-SRC]`.

---

## 6. Related Documentation & Architecture
- **Phase Record:** [`mobile_m5c2_audio_session_integration.md`](../phases/mobile_m5c2_audio_session_integration.md)
- **ADR Reference:** [`ADR-034`](../adr/ADR-034-audio-effect-lifecycle-and-teardown.md)
