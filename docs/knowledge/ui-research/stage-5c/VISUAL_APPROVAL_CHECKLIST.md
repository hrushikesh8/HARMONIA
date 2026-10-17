# Harmonia Mobile — Final Visual Approval Checklist (Stage 5C.2)

**Document Classification:** Visual Review & Executive Approval Gate  
**Status:** PARTIALLY READY FOR VISUAL APPROVAL (Stage 5C.2 Gate)  
**Author:** Principal Android Product Designer, UI/UX Architect & Engineering Documentation Auditor  
**Baseline Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  

---

> [!IMPORTANT]
> **Implementation Authorization Invariant:** Sign-off on this visual checklist confirms acceptance of visual aesthetics, layout hierarchies, accessibility standards, and component responsibilities only. **It does NOT authorize application code edits, dependency additions, navigation migrations, or implementation of Phase 5D.**

---

## 1. Concept Inventory & Visual Artifact Review Status

| Concept & Scope | Document Reference | Visual Artifact Status | Review Evidence / Limitation |
| :--- | :--- | :--- | :--- |
| **Concept A: Home & Library**<br>Home dashboard, Recently Played vinyl carousel, factual offline library metrics, segmented tabs (`Tracks`, `Albums`, `Artists`), and persistent Mini-Player. | [`CONCEPT_A_HOME_LIBRARY.md`](CONCEPT_A_HOME_LIBRARY.md) | **RENDERED & INSPECTED**<br>Image: [`concept_a_home_library.jpg`](concept_a_home_library.jpg) | High-resolution phone composition (9:16). Sharp typography, distinct 48dp thumbnails, cyan-teal active underlines, and persistent bottom-anchored Mini-Player. All displayed metrics and track names are illustrative sample data. |
| **Concept B: Now Playing & Queue**<br>Full playback stage, commanding vinyl artwork (`max(320dp)`), scrub bar, 64dp Play/Pause FAB, `FLAC` format badge, 3-layer Audio Signal Inspector modal, and queue peek. | [`CONCEPT_B_NOW_PLAYING.md`](CONCEPT_B_NOW_PLAYING.md) | **RENDERED & INSPECTED**<br>Image: [`concept_b_now_playing.jpg`](concept_b_now_playing.jpg) | High-resolution phone composition. Verified uncropped artwork geometry, high-contrast cyan-teal transport buttons, and clear queue entry. File container metadata is strictly distinguished from decoded stream format and output routing. |
| **Concept C: Settings, Storage & EQ**<br>Settings hierarchy, physical storage volume lifecycle badges (`CONNECTED` vs `UNMOUNTED / OFFLINE`), 5-band graphic equalizer with live dB labels, and sleep timer. | [`CONCEPT_C_SETTINGS_STORAGE_EQ.md`](CONCEPT_C_SETTINGS_STORAGE_EQ.md) | **RENDERED & INSPECTED**<br>Image: [`concept_c_settings_storage_eq.jpg`](concept_c_settings_storage_eq.jpg) | High-resolution phone composition. Distinct status pills for storage health. 5-band vertical sliders enforce 48dp touch bounds. Frequencies, dB readouts, and presets are explicitly marked as illustrative sample values (device HAL dependent). |
| **Concept D: Search & Playlists**<br>Local categorized search (`Tracks`, `Albums`, `Artists`), playlist hero header with violet accent glow, accessible 48dp discrete `Move Up` / `Move Down` reordering, and proposed continuous drag. | [`CONCEPT_D_SEARCH_PLAYLISTS.md`](CONCEPT_D_SEARCH_PLAYLISTS.md) | **DOCUMENTED SPECIFICATION**<br>*(Image Generation Quota Exhausted)* | **Awaiting Visual Render**: Fully specified via precision architectural ASCII blueprints, component token mappings, and verified source bindings. Image generation quota is exhausted (429 rate-limit with a ~4.7h reset window). No mock image substituted. |

---

## 2. Cross-Concept Consistency Audit

An integrated review across Concepts A–D confirms adherence to core architectural and design system principles:

- [x] **Unified Background & Surfaces:** All concepts utilize pure matte Obsidian (`#090B10`) background paired with Elevated Charcoal surfaces (`#141721`) bounded by subtle 1dp structural hairline borders (`#2A3042`).
- [x] **Acoustic Teal Electrical Signal:** Vibrant cyan-teal (`#00D2B4`) is consistently applied as the active signal flow accent (active playback transport, progress scrub lines, active tab indicators, and healthy storage connection status).
- [x] **Restrained Curated Violet:** Harmonic Violet (`#6E56CF`) is strictly isolated to user-curated playlist environments, avoiding contrast violations in small body text.
- [x] **Ergonomic One-Handed Usability:** Primary destinations, persistent Mini-Player (64dp height), and primary transport controls are strictly anchored in the lower 50% of the display.
- [x] **Artwork Scaling & Display Safety:** Now Playing artwork enforces responsive constraints (`fillMaxWidth(0.85f)`, `max(320dp)`) with vertical scroll fallback, preventing UI clipping under 200% font scaling.
- [x] **Physical Touch Target Guarantee:** All interactive buttons (transport, dismiss, overflow menu, equalizer sliders, playlist reorder buttons) enforce $\ge$ 48dp × 48dp minimum bounding boxes.
- [x] **Honest State & Data Representation:** Zero unverified claims of "bit-perfect" DAC offload, cloud streaming, or algorithmic recommendations appear. All counts, file paths, and track titles are explicitly marked as illustrative sample data.

---

## 3. Outstanding Architectural & Design Decisions

The four decision gates from [`DECISIONS_REQUIRED.md`](DECISIONS_REQUIRED.md) remain pending executive sign-off prior to Phase 5D:

### Decision 1: Navigation Architecture Migration
- **Current Verified Implementation:** `Screen` enum state machine with `rememberSaveable` in `MainActivity.kt`.
- **Recommended Default:** **Option A (Migrate to `androidx.navigation:navigation-compose:2.8.x` with `@Serializable` type-safe routes)**.
- **Trade-Off:** Provides native detail back-stacks (`AlbumDetail`, `ArtistDetail`, `PlaylistDetail`) and Android 14+ predictive back gestures; requires adding Navigation Compose dependency and refactoring `MainActivity.kt`.
- **Status:** `[UNAPPROVED PROPOSAL]` *(Dependency not approved; no migration authorized)*.

### Decision 2: Playlist Reordering Interaction
- **Current Verified Implementation:** Discrete `Move Up` and `Move Down` icon buttons calling `OfflinePlaylistRepository.reorderPlaylistItem()`.
- **Recommended Default:** **Option A (Upgrade discrete buttons to 48dp × 48dp minimum touch bounds; defer continuous drag)**.
- **Trade-Off:** Provides immediate accessibility compliance, zero third-party dependency risks, and prevents scroll-vs-drag gesture conflicts; moving tracks across large distances requires repeated taps.
- **Status:** Discrete reordering is `[VERIFIED-SRC]`; 48dp touch bounds is an accepted fix; continuous drag is `[UNAPPROVED PROPOSAL]`.

### Decision 3: Audio Signal Inspector Presentation Scope
- **Current Verified Implementation:** Static container metadata from `LocalFileEntity`; decoded audio format from `ExoPlayer`; routed output device from Android `AudioTrack`.
- **Recommended Default:** **Option A (Restrict Audio Signal Inspector to verified container format, decoded stream, and output device routing)**.
- **Trade-Off:** Guarantees total technical honesty; omits physical DAC chip model names (e.g. "ESS Sabre") and hardware direct-sink confirmation because standard Android public APIs cannot verify offloaded kernel PCM streams.
- **Status:** File metadata and stream format are `[VERIFIED-SRC]`; physical DAC telemetry is `[DEFERRED]`.

### Decision 4: Equalizer State Lifecycle & Global Persistence
- **Current Verified Implementation:** Platform `android.media.audiofx.Equalizer` attached to active ExoPlayer session in `HarmoniaPlaybackService`; master on/off state managed in-memory via `InMemoryEqualizerUserIntentSource`.
- **Recommended Default:** **Option B (Maintain in-memory baseline during active playback session)** until a dedicated persistence migration is authorized for Phase 5D.
- **Trade-Off:** Preserves the existing clean architectural separation between master session enablement and persistence without prematurely introducing database schema or DataStore migrations.
- **Status:** Master enable switch is `[VERIFIED-SRC]`; global persistence of band levels is `[UNAPPROVED PROPOSAL]`.

---

## 4. Visual Review Sign-Off Matrix (For Project Lead)

Please check the appropriate boxes to record explicit visual design approval:

- [ ] **Visual Language & Aesthetic Direction:** Approve the "Obsidian Acoustic Instrument" design language, semantic tokens, 1dp hairline borders, and typography scale.
- [ ] **Screen Layouts (Concepts A, B, C):** Approve the visual compositions for Home/Library, Now Playing, and Settings/Storage/Equalizer as rendered in `concept_a_home_library.jpg`, `concept_b_now_playing.jpg`, and `concept_c_settings_storage_eq.jpg`.
- [ ] **Concept D Specification:** Approve the documented blueprint, categorized search hierarchy, and 48dp discrete playlist reordering controls in `CONCEPT_D_SEARCH_PLAYLISTS.md` (with image mockup to follow once API quota resets).
- [ ] **Decision 1 (Navigation):** Approve Recommended Default (Option A: Navigation Compose 2.8.x) OR Specify Option B (Retain `MainActivity` state machine).
- [ ] **Decision 2 (Reordering):** Approve Recommended Default (Option A: 48dp Discrete Buttons) OR Specify Option B (Add Drag-and-Drop library).
- [ ] **Decision 3 (Audio Inspector):** Approve Recommended Default (Option A: Verified Container, Decoded Stream & Output Device Only).
- [ ] **Decision 4 (Equalizer):** Approve Recommended Default (Option B: In-memory session baseline) OR Authorize Option A (Global persistence migration).

---

> [!CAUTION]
> **Next Gate:** Checking these boxes approves the visual design direction and establishes implementation requirements. Phase 5D implementation must remain blocked until the project lead completes this review and issues explicit authorization to modify application code.
