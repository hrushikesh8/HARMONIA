# ADR-033: Decoupling Persisted Equalizer User Intent from Transient Hardware Audio Sessions

## 1. Metadata & Status
- **Identifier:** `ADR-033`
- **Title:** Decoupling Persisted Equalizer User Intent from Transient Hardware Audio Sessions
- **Status:** **ACCEPTED** `[REPORT-BACKED]`
- **Date:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Subsystem:** Harmonia Mobile (`harmonia-mobile/app/`)
- **Preceding Decisions:** [`ADR-020`](../../MOBILE_ARCHITECTURE_v0.1.md#22-architectural-decision-records-adrs)
- **Phase Traceability:** [`mobile_m5c1_audio_effect_contracts.md`](../phases/mobile_m5c1_audio_effect_contracts.md), [`mobile_m5c3_equalizer_settings.md`](../phases/mobile_m5c3_equalizer_settings.md)

---

## 2. Context & Problem Formulation
Android audio effects hook into hardware audio output through an integer `audioSessionId` allocated dynamically by the audio HAL when an `AudioTrack` is instantiated. When playback is stopped, or when the player is idle, no active audio session exists (`audioSessionId = 0` or unset).

In naive implementations:
- Equalizer settings are tied directly to an active `android.media.audiofx.Equalizer` instance.
- If the user configures equalizer settings while playback is paused or stopped, the system throws an exception or fails to record the change.
- When an audio session is destroyed (e.g. during track change or playback stop), user preferences are lost.

Harmonia required a clean architectural boundary where the user's intent to enable equalisation and custom band adjustments is persistent, durable, and independent of whether a hardware DSP session is currently alive.

---

## 3. Decision
1. **Decoupled User Intent Contract:** Introduce [`EqualizerUserIntentSource.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/equalizer/EqualizerUserIntentSource.kt) as a pure domain port representing persisted intent (`isEqualizerEnabled()`, `getBandLevels()`, `getCurrentPresetIndex()`).
2. **SharedPreferences Persistence:** Implement [`SharedPreferencesEqualizerUserIntentSource.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/preferences/SharedPreferencesEqualizerUserIntentSource.kt) to persist user toggles and custom band levels across application process restarts.
3. **Exhaustive State Machine:** Model platform session availability as an explicit sealed interface [`EqualizerState`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/equalizer/EqualizerState.kt):
   - `NoSession(failureReason: EqualizerFailureReason? = null)`: No active audio session (e.g. playback idle) or effect creation failed. Diagnostic failure causes (e.g. unsupported hardware route, invalid session ID, or unexpected native error) are encapsulated in `failureReason` rather than separate top-level states. User intent is preserved.
   - `AvailableDisabled`: Valid hardware session present, but user intent is disabled.
   - `EnabledWithControl`: Valid hardware session present, user intent is enabled, DSP controls active.
   - `ControlLost`: Valid hardware session present, but effect control was acquired by another audio process.
4. **Automatic Hardware Re-binding:** When ExoPlayer establishes a new audio session, [`DefaultAudioEffectManager`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/DefaultAudioEffectManager.kt) checks `EqualizerUserIntentSource`. If enabled, it automatically applies user band levels to the new hardware instance without requiring manual re-activation.

---

## 4. Alternatives Considered
- **Alternative A: Custom Software DSP Biquad Filters (In-App AudioProcessor):**  
  *Rejected:* Embedding software IIR/FIR filter chains in ExoPlayer increases CPU usage, causes battery drain, adds latency, and risks clipping/distortion. Leveraging platform HAL effects gives hardware-accelerated processing without NDK bloat.
- **Alternative B: Retaining Equalizer in ViewModel Memory Only:**  
  *Rejected:* Loses all user adjustments as soon as the settings screen or application process is destroyed.

---

## 5. Consequences & Trade-offs
### Positive Consequences:
- Users can adjust their equalizer preferences anytime, even when no music is playing.
- When playback starts, the equalizer seamlessly applies user preferences.
- Pure clean architecture: domain and presentation layers depend on abstract contracts with zero Android audiofx imports.

### Negative Consequences:
- Custom band levels saved in SharedPreferences may not map 1:1 if the user switches to a different audio output device with a different number of hardware equalizer bands.

---

## 6. Durable Architectural Invariants
1. Changing equalizer state while playback is stopped must update persisted intent and must **never** throw a runtime exception.
2. Disconnecting a session must transition state to `NoSession` without mutating or resetting persisted user intent.

---

## 7. Evidence & Traceability
- **Implementation Source:** [`EqualizerUserIntentSource.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/domain/model/equalizer/EqualizerUserIntentSource.kt), [`SharedPreferencesEqualizerUserIntentSource.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/data/local/preferences/SharedPreferencesEqualizerUserIntentSource.kt), [`DefaultAudioEffectManager.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/service/equalizer/DefaultAudioEffectManager.kt) `[VERIFIED-SRC]`
- **Test Evidence:** [`SettingsViewModelEqualizerTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/presentation/settings/SettingsViewModelEqualizerTest.kt) (14 `@Test` methods passed), [`SharedPreferencesEqualizerUserIntentSourceTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/data/local/preferences/SharedPreferencesEqualizerUserIntentSourceTest.kt) `[REPORT-BACKED]`
