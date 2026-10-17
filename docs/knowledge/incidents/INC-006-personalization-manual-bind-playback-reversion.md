# Incident & Defect Record: INC-006 Personalization Manual Binding Reversion

## 1. Incident Overview
- **Incident ID:** `INC-006`
- **Date Detected:** 2026-10-11 *(Reconstructed / Inferred)* `[INFERRED-HIST]`
- **Target Subsystem:** Harmonia Mobile / Personalization Presentation (`harmonia-mobile/app/`)
- **Milestone / Phase:** Personalization Phase 3
- **Severity Classification:** **MEDIUM**
- **Investigation Status:** **RESOLVED & VERIFIED**
- **Confidence Rating:** `[VERIFIED-SRC]`

---

## 2. Symptoms & User/System Impact
- **Observed Behavior:**
  When a user opened a track details dialog or bottom sheet for Track B while Track A was playing, the UI correctly displayed Track B's favorite status and rating initially. However, as soon as Track A changed playback state (e.g. paused, resumed, or skipped to Track C), Track B's dialog abruptly flipped to displaying the playback track's favorite/rating status instead of Track B.
- **User Impact:**
  If the user tapped the favorite button or a star rating right as the song changed, their rating was inadvertently applied to the wrong track in the database.
- **Reproduction Steps:**
  1. Play Track A in the background.
  2. In the Library screen, tap the context menu on Track B and select "Details / Rate".
  3. `PersonalizationViewModel.bindTrack("trk_manual")` is invoked.
  4. While the dialog is open, pause or skip playback using media notification or Bluetooth headset.
  5. The dialog UI abruptly switches its bound track ID back to the playing track.

---

## 3. Root Cause Analysis
- **Confirmed Root Cause (Evidence-Backed):**
  In [`PersonalizationViewModel.kt`](../../../harmonia-mobile/app/src/main/java/org/harmonia/player/presentation/personalization/PersonalizationViewModel.kt), playback follow mode was established via a reactive subscription to `playbackPort.playbackState`. Calling `bindTrack(trackId)` set `manualTrackId.value`, but without state isolation, subsequent emissions from `playbackPort.playbackState` could overwrite `activeTrackId` with `currentTrackId`.
- **Hypotheses Considered & Disproven:**
  - *Hypothesis A: Room Flow was emitting incorrect track IDs:* Disproven. Room emitted accurate data for whatever track ID was requested; the ViewModel was requesting the wrong track ID due to playback port emissions overriding the binding.

---

## 4. Corrective Action & Implementation
- **Remediation Summary:**
  Introduced explicit manual binding state tracking in `PersonalizationViewModel`.
- **Code Modifications:**
  - In `PersonalizationViewModel.kt`:
    - Added private state `isManualBinding = MutableStateFlow(true)` and `manualTrackId` upon invocation of `bindTrack(trackId)`.
    - In `activeTrackId`, emissions are guarded: `if (isManual) manualId else playbackState.currentTrackId`.
    - Added `resumeFollowingPlayback()` to allow callers to deliberately re-engage follow mode when dialogs dismiss.
- **Alternatives Rejected:**
  - *Instantiating a Brand New ViewModel per Dialog:* Rejected. Over-allocates ViewModel instances and breaks shared repository Flow caching.

---

## 5. Regression Prevention & Invariant Protection
- **Regression Test Suite:**
  - Test File: [`PersonalizationViewModelTest.kt`](../../../harmonia-mobile/app/src/test/java/org/harmonia/player/presentation/personalization/PersonalizationViewModelTest.kt)
  - Test Method: `manual_track_binding_overrides_playback_and_resumes()` `[VERIFIED-TEST]`
  - Execution Output: Verified passing with 0 errors across 16 ViewModel tests.
- **Durable Architectural Invariant:**
  - When `bindTrack(trackId)` is explicitly invoked on `PersonalizationViewModel`, background playback state changes must not mutate the active track ID until `resumeFollowingPlayback()` is called `[VERIFIED-SRC]`.

---

## 6. Related Documentation & Architecture
- **Phase Record:** [`mobile_personalization_p3_viewmodel.md`](../phases/mobile_personalization_p3_viewmodel.md)
- **ADR Reference:** [`ADR-037`](../adr/ADR-037-personalization-presentation-architecture.md)
