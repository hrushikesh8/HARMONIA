# Harmonia Mobile — Decisions Required for Implementation Approval (Stage 5C)

**Document Classification:** Architectural & Design Decision Gate  
**Status:** PENDING PROJECT LEAD APPROVAL (Stage 5C Gate)  
**Author:** Principal Mobile Product Designer & Senior Android UI/UX Architect  

---

The following explicit decisions must be reviewed and approved by the project lead before any Phase 5D implementation, dependency addition, or application code changes begin:

---

### Decision 1: Navigation Architecture Migration

- **Current Status:** `[UNAPPROVED PROPOSAL]`
- **Context:** Harmonia Mobile currently manages top-level screen switching using a custom `Screen` enum and `rememberSaveable` in `MainActivity.kt`. It does not support deep linking, nested detail back-stacks (`AlbumDetail`, `ArtistDetail`, `PlaylistDetail`), or Android 14+ predictive back gestures without manual back-stack management.
- **Option A (Recommended Proposal):** Migrate to `androidx.navigation:navigation-compose:2.8.x` with Kotlin serialization type-safe routes.
  - *Benefits:* Full detail back-stack handling, built-in predictive back animations, decoupled screen navigation.
  - *Costs:* Adds AndroidX Navigation and `kotlinx.serialization` dependencies to `libs.versions.toml` and `build.gradle.kts`; requires refactoring `MainActivity.kt`.
- **Option B (Conservative Fallback):** Retain and expand the custom `MainActivity` state machine.
  - *Benefits:* Zero new dependencies; no changes to version catalog.
  - *Costs:* Requires manually implementing back-stack lists, pop logic, and back-press handling for all detail screens.
- **Recommended Default:** Option A. *(Remains an unapproved proposal pending explicit project lead sign-off; no dependency or code changes are authorized).*

---

### Decision 2: Playlist Reordering Interaction

- **Current Status:** Discrete Move Up/Down is `[VERIFIED-SRC]`; Continuous Drag-and-Drop is `[UNAPPROVED PROPOSAL]`.
- **Context:** Source inspection confirms that `PlaylistDetailScreen.kt` currently implements discrete `Move Up` and `Move Down` icon buttons backed by `OfflinePlaylistRepository.reorderPlaylistItem()`. However, the current icon buttons measure only 36dp (below the 48dp accessibility standard).
- **Option A (Recommended Proposal):** Upgrade the existing discrete `Move Up` / `Move Down` buttons to strictly enforce **48dp × 48dp minimum touch bounds**, keeping continuous touch-and-drag reordering deferred.
  - *Benefits:* Immediate accessibility compliance; 100% reliable on all touch screens; zero new library dependencies; full TalkBack screen reader accessibility out of the box.
  - *Costs:* Requires multiple taps to move a track across large playlist distances.
- **Option B:** Add a continuous drag-and-drop reorder library (e.g. `reorderable` or custom pointer input) alongside discrete buttons.
  - *Benefits:* High-speed multi-row reordering.
  - *Costs:* Introduces third-party dependency; adds touch gesture conflict risks with vertical scrolling.
- **Recommended Default:** Option A. *(Continuous drag-and-drop remains strictly unapproved and unimplemented).*

---

### Decision 3: Audio Signal Inspector Presentation Scope

- **Current Status:** File Metadata & Decoded Stream Format is `[VERIFIED-SRC]`; Hardware Direct-Sink is `[DEFERRED]`.
- **Context:** The Now Playing screen features an audio technical badge (`FLAC 24-bit / 96 kHz`) opening an on-demand Audio Signal Inspector modal sheet. Standard Android public APIs (`AudioTrack`, `AudioManager`, `MediaMetadataRetriever`) can verify container format, decoded stream format, and connected output device, but **cannot confirm physical DAC chip identity or bit-perfect kernel hardware sink offload**.
- **Option A (Recommended Proposal):** Restrict the Audio Signal Inspector to verified platform capabilities (Container Format, File Bitrate, Decoded Stream Sample Rate / Bit Depth, and Routed Output Device Type). Mark physical DAC chip identity and bit-perfect confirmation as **`[DEFERRED]` / Unsupported**.
  - *Benefits:* Total technical honesty; no false or misleading hardware claims.
  - *Costs:* Does not display DAC brand names (e.g. "ESS Sabre").
- **Option B:** Attempt heuristic string matching based on connected USB device strings.
  - *Benefits:* May display vendor names for certain recognized USB DACs.
  - *Costs:* Brittle; fails on generic USB adapters; cannot verify whether Android OS mixer resampled the audio.
- **Recommended Default:** Option A. *(Physical DAC and direct-sink confirmation are explicitly deferred; no hardware claims will be made).*

---

### Decision 4: Equalizer State Lifecycle & Global Persistence

- **Current Status:** Master Enable Switch & In-Memory Intent is `[VERIFIED-SRC]`; Global Persistence of Band Levels / Presets is `[UNAPPROVED PROPOSAL]`.
- **Context:** The 5-band graphic equalizer engine is attached to the active `ExoPlayer` audio session in `HarmoniaPlaybackService`. Currently, `InMemoryEqualizerUserIntentSource` manages the master on/off state in-memory during service execution.
- **Separation of Architectural Layers:**
  1. *Master Enablement (Existing):* The master switch enables or disables audio effect processing on the active session.
  2. *Individual Band Levels & Presets Persistence (Proposed):* Storing custom band gain offsets (-1500 to +1500 mB) and selected presets across application process restarts requires persistence.
- **Option A (Recommended Proposal):** When persistence is authorized in future Phase 5D work, persist equalizer band gains and master state in a dedicated preferences store, restoring them on service startup.
  - *Benefits:* Seamless user experience across app restarts.
  - *Costs:* Requires repository and persistence implementation.
- **Option B (Current Baseline):** Maintain equalizer state in-memory only during the active playback service session, resetting on complete service teardown.
  - *Benefits:* Zero persistence overhead; aligns with current codebase.
  - *Costs:* User must re-engage custom EQ settings after process death.
- **Recommended Default:** Option B remains the active implementation baseline until an explicit decision authorizes persistence migration. *(Zero database schema, Room, or DataStore changes are authorized by this review).*

