# Incident & Defect Record: INC-005 Sleep Timer Repeat-One Infinite Loop

## 1. Incident Overview
- **Incident ID:** `INC-005`
- **Date Detected:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Target Subsystem:** Harmonia Mobile / Playback Engine (`harmonia-mobile/app/`)
- **Milestone / Phase:** Stage M5-D Coroutine Sleep Timer
- **Severity Classification:** **MEDIUM**
- **Investigation Status:** **RESOLVED & VERIFIED**
- **Confidence Rating:** `[VERIFIED-SRC]`

---

## 2. Symptoms & User/System Impact
- **Observed Behavior:**
  When a user configured the sleep timer to "End of Track" while the active track had "Repeat One" enabled, playback failed to stop when the track reached its end. The player continuously re-wound to 0:00 and looped indefinitely throughout the night, completely defeating the purpose of the sleep timer.
- **User Impact:**
  The user woke up to an unstopped music player and a drained phone battery.
- **Reproduction Steps:**
  1. Start playing a track.
  2. Set repeat mode to `RepeatMode.ONE` (loop current track).
  3. Open Now Playing menu and select Sleep Timer -> "End of Track".
  4. Allow the track to reach its conclusion.
  5. Playback loops back to the start and does not stop.

---

## 3. Root Cause Analysis
- **Confirmed Root Cause (Evidence-Backed):**
  The initial end-of-track detector relied solely on `Player.Listener.onPlaybackStateChanged(state)` waiting for `Player.STATE_ENDED`. In ExoPlayer, when `repeatMode == Player.REPEAT_MODE_ONE`, reaching the end of the media source causes ExoPlayer to seek to 0 and stay in `Player.STATE_READY`; `STATE_ENDED` is never emitted. Consequently, the completion listener never fired.
- **Hypotheses Considered & Disproven:**
  - *Hypothesis A: Track duration metadata was inaccurate:* Disproven. Position reached track duration, but state did not transition to `STATE_ENDED`.

---

## 4. Corrective Action & Implementation
- **Remediation Summary:**
  Hooked into media item transition events and explicit playback position progress monitoring in [`HarmoniaPlayerController.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/playback/HarmoniaPlayerController.kt).
- **Code Modifications:**
  - Added transition monitoring: When End-of-Track mode is active, the controller monitors `onMediaItemTransition` and iteration boundary crossing. If the track loops back to 0 due to `REPEAT_MODE_ONE`, the controller recognizes that one full iteration has completed, pauses playback immediately, and deactivates the timer.
- **Alternatives Rejected:**
  - *Disabling Repeat-One when Sleep Timer is enabled:* Rejected. Mutating user repeat mode without consent is poor UX; fulfilling the timer when the current play finishes is the expected behavior.

---

## 5. Regression Prevention & Invariant Protection
- **Regression Test Suite:**
  - Test File: [`HarmoniaSleepTimerTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/playback/HarmoniaSleepTimerTest.kt)
  - Test Method: `` `end of track mode completes and overrides repeat mode one` `` `[VERIFIED-TEST]`
  - Execution Output: Verified clean with 0 errors across 14 sleep timer test methods.
- **Durable Architectural Invariant:**
  - End-of-track sleep timers must stop playback upon completion of the active track's iteration regardless of whether repeat-one or queue repeat is enabled `[VERIFIED-SRC]`.

---

## 6. Related Documentation & Architecture
- **Phase Record:** [`mobile_m5d_sleep_timer.md`](../phases/mobile_m5d_sleep_timer.md)
- **ADR Reference:** [`ADR-035`](../adr/ADR-035-sleep-timer-monotonic-clock-and-volume-safety.md)
