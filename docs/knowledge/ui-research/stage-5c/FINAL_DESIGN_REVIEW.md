# Harmonia Mobile — Final Design Review & Implementation Readiness (Stage 5C.3)

**Document Classification:** Final Visual Review & Implementation Readiness Recommendation  
**Status:** DRAFT FOR PROJECT LEAD REVIEW (Stage 5C.3 Gate)  
**Author:** Principal Android Product Designer, UI/UX Architect & Engineering Documentation Auditor  
**Baseline Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  

---

> [!IMPORTANT]
> **Advisory Scope Statement:** This document synthesizes the visual research and provides architectural recommendations for the upcoming implementation phases. **It does not authorize application source code changes, build alterations, or dependency additions.**

---

## 1. Recommended Visual Direction: "Obsidian Acoustic Instrument"

We formally recommend adopting the **"Obsidian Acoustic Instrument"** visual direction established in Stage 5B and refined across Stage 5C:

1. **Quiet Obsidian Baseline:** Pure matte black (`#090B10`) background creates maximum depth, recedes into the device bezel, and allows uncropped album artwork and playback signals to command user focus.
2. **Elevated Charcoal Surfaces:** Cards, list rows, bottom navigation, and modal sheets sit on `#141721` with distinct 1dp structural hairline borders (`BorderSlate #2A3042`). This replaces heavy, blurry drop shadows with a crisp, physical aesthetic.
3. **Acoustic Teal Electrical Signal:** A vibrant cyan-teal (`#00D2B4`) serves strictly as the active signal flow accent—playback transport buttons, active seek lines, tab indicators, and healthy storage status.
4. **Harmonic Violet Depth:** A restrained secondary violet (`#6E56CF`) is reserved exclusively for user-curated spaces (playlists, collection badges, star rating highlights), avoiding contrast violations in body text.
5. **Ergonomic One-Handed Dominance:** Primary navigation, persistent Mini-Player (64dp), and primary transport controls are strictly anchored in the lower 50% of the display.

---

## 2. Strengths and Weaknesses of the Current Concepts

### 2.1 Strengths
- **Aesthetic Authority:** Distinct from both generic streaming services (Spotify, YouTube Music) and overly complex power tools (Poweramp). Feels like a dedicated high-end audio DAP (Digital Audio Player).
- **Physical Tactility:** Every interactive element enforces a minimum **48dp × 48dp touch target**, backed by clear visual focus and click states.
- **State Honesty:** Storage states (Connected, Unmounted SD Card, Permission Revoked) and technical audio telemetry (Container vs Decoded vs Sink) are represented truthfully without fake or misleading claims.
- **Decoupled Playback Safety:** Playback controls and the persistent Mini-Player remain strictly decoupled from catalog screen navigation.
- **Accessibility & Contrast:** Primary text-to-background contrast reaches **17.70:1** (WCAG AAA); secondary text reaches **5.95:1** (WCAG AA). Responsive artwork constraints (`max(320dp)`) ensure zero clipping under 200% font scaling.

### 2.2 Weaknesses & Mitigations
- **Dark-First Bias:** While Dark Mode is the signature experience, outdoor daylight usability requires high-contrast Light Mode (`SlateDay #F4F6F9`, Deep Teal `#006064`). *Mitigation:* Keep Light Mode specified with strict WCAG AAA contrast, but schedule its implementation in a dedicated follow-up phase after the core Dark theme is established.
- **Concept D Render Quota Gap:** Image generation quota exhaustion prevented generating a visual PNG/JPG mockup for Concept D. *Mitigation:* Concept D is fully specified via high-precision architectural ASCII blueprints, 48dp touch targets, and verified repository bindings.

---

## 3. Cross-Screen Consistency Findings

An integrated review across Concepts A–D confirms complete visual and behavioral consistency:

| Attribute | Concept A (Home & Library) | Concept B (Now Playing) | Concept C (Settings & EQ) | Concept D (Search & Playlists) |
| :--- | :--- | :--- | :--- | :--- |
| **Root Background** | `#090B10` | `#090B10` | `#090B10` | `#090B10` |
| **Surface Elevation** | `#141721` with 1dp border | `#141721` with 1dp border | `#141721` with 1dp border | `#141721` with 1dp border |
| **Active Signal Accent** | Cyan-Teal (`#00D2B4`) | Cyan-Teal (`#00D2B4`) | Cyan-Teal (`#00D2B4`) | Cyan-Teal (`#00D2B4`) |
| **Curated Accent** | N/A (Catalog) | N/A (Transport) | N/A (System) | Violet (`#6E56CF`) (Playlists) |
| **Mini-Player Bar** | 64dp with teal progress | Collapsed target | 64dp with teal progress | 64dp with teal progress |
| **Touch Targets** | $\ge$ 48dp × 48dp | $\ge$ 48dp × 48dp | $\ge$ 48dp × 48dp | $\ge$ 48dp × 48dp |
| **Data Labeling** | Factual offline counts | Factual file metadata | Factual volume states | Factual local matches |

---

## 4. Existing Functionality versus Proposed Visual Changes

| Screen / Area | Existing Verified Implementation (`[VERIFIED-SRC]`) | Proposed Visual Changes (`[PROPOSED]`) | Status |
| :--- | :--- | :--- | :--- |
| **Home & Library** | Room track/album/artist queries; `ListeningEventRepository` recently played; track counts. | 1dp hairline card borders; `FLAC` format badge on track items; structured library metrics pill. | Backend verified; visual styling proposed. |
| **Now Playing** | ExoPlayer playback, scrub slider, skip/play/pause, repeat/shuffle cycling. | Responsive artwork constraint (`max(320dp)`); `FLAC 24-bit / 96 kHz` format chip; Audio Signal Inspector modal sheet. | Playback verified; Inspector layout proposed. |
| **Settings & Storage** | `StorageVolumeLifecycleCoordinator` volume tracking; SAF permission recovery; offline track preservation. | Distinct status pills (`CONNECTED` vs `UNMOUNTED / OFFLINE`); physical storage path display. | Lifecycle engine verified; card UI proposed. |
| **Equalizer** | Platform `android.media.audiofx.Equalizer` on active session; `InMemoryEqualizerUserIntentSource` master switch. | 5-band slider card with live dB readouts; preset filter chips (`Flat`, `Acoustic`, `Bass Boost`, `Custom`). | Platform engine verified; UI surface proposed. |
| **Playlist Reordering** | Discrete `Move Up` / `Move Down` buttons calling `OfflinePlaylistRepository.reorderPlaylistItem()`. | Expanding discrete touch targets from 36dp to **48dp**; continuous touch-and-hold drag handle. | Discrete reorder verified; 48dp target required; drag handle proposed. |
| **Navigation** | `Screen` enum state machine with `rememberSaveable` in `MainActivity.kt`. | Migration to `androidx.navigation:navigation-compose:2.8.x` with `@Serializable` type-safe routes. | Proposed migration; currently unapproved. |

---

## 5. Known Accessibility and Technical Limitations

1. **Physical DAC Telemetry Limitation:** Standard Android public SDK APIs (`AudioTrack`, `AudioManager`, `MediaMetadataRetriever`) can verify file container format, decoded PCM stream format, and routed audio output device, but **cannot confirm physical DAC chip identity or bit-perfect kernel hardware sink offload**. DAC chip branding is strictly marked `[DEFERRED]`.
2. **Dynamic Equalizer Bands & Presets:** Frequencies (`60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz`), gain limits, and factory presets are OEM/hardware-dependent. The UI cannot hardcode fixed bands; it must bind dynamically to `EqualizerBand` and `EqualizerPreset` returned by the device HAL.
3. **Touch Target Deficit in Existing Code:** Discrete `Move Up` and `Move Down` buttons in `PlaylistDetailScreen.kt` currently measure 36dp, failing the 48dp standard. This must be corrected in Phase 5D.

---

## 6. Concept D Visual Mockup Gap Analysis

- **Status:** Concept D has an exhaustive documented blueprint and token mapping in `CONCEPT_D_SEARCH_PLAYLISTS.md`, but lacks an image mockup (`concept_d_search_playlists.jpg`) due to Google Cloud image generation quota exhaustion (429 rate limit).
- **Recommendation:** **Concept D's missing image does NOT block initial implementation planning.**
  - *Rationale:* The components comprising Concept D (search bar, tab rows, list items, discrete move buttons, Mini-Player) are identical in styling, tokens, and behavior to the approved components in Concepts A–C.
  - The missing image can be rendered as a follow-up review item once API quota resets, without impeding the foundational tokens and navigation setup of Phase 5D.

---

## 7. Recommended Scope for First UI Implementation (Stage 5D)

We recommend dividing Stage 5D into two focused implementation releases:

- **Release 1 (Core Foundations & Dark-First Baseline):**
  - Implement shared design tokens, colors, typography, shapes, and 1dp border primitives in `Theme.kt`.
  - Resolve navigation frame (retaining `MainActivity` state machine or implementing approved Navigation Compose).
  - Implement persistent Mini-Player (64dp) and Now Playing sheet with responsive artwork (`max(320dp)`).
  - Implement Home & Library catalog browsing with 48dp touch targets.
  - Implement discrete 48dp `Move Up` / `Move Down` buttons in Playlist Details.
  - Implement Storage lifecycle cards (Connected vs Unmounted volume feedback).
- **Release 2 (Advanced Surfaces & Polish):**
  - Implement 5-band Graphic Equalizer UI sliders and preset chips.
  - Implement Audio Signal Inspector 3-layer modal sheet.
  - Implement Light Theme tokens and high-glare outdoor mode.
  - Re-evaluate continuous drag-and-drop playlist reordering once discrete baseline is verified.

---

## 8. Summary of Open Decisions & Recommended Defaults

| Decision Gate | Recommended Default | Trade-Off & Implementation Prerequisite | Approval Status |
| :--- | :--- | :--- | :--- |
| **1. Navigation Migration** | **Option A (Navigation Compose 2.8.x)** | Clean back-stacks and predictive back vs adding dependencies. Prerequisite: Add library to catalog. | `[UNAPPROVED PROPOSAL]` |
| **2. Playlist Reordering** | **Option A (48dp Discrete Buttons)** | Immediate accessibility and zero new dependencies vs multi-tap reordering. Prerequisite: None. | Discrete is `[VERIFIED]`; Drag is `[UNAPPROVED]` |
| **3. Audio Telemetry** | **Option A (Verified Scope Only)** | Total technical honesty vs omitting DAC brand names. Prerequisite: None. | Verified scope `[APPROVED]`; DAC chip `[DEFERRED]` |
| **4. Equalizer Persistence** | **Option B (In-Memory Session Only)** | Zero database schema changes vs re-engaging EQ settings after app restart. Prerequisite: None. | Master switch `[VERIFIED]`; Persistence `[UNAPPROVED]` |
| **5. Light Theme Timing** | **Release 2 Follow-Up** | Rapid focus on primary Dark baseline vs dual-theme testing overhead. Prerequisite: Core tokens. | `[UNAPPROVED PROPOSAL]` |
| **6. Concept D Mockup Gate**| **Proceed with Blueprint** | Avoids blocking development on image quota vs visual render review. Prerequisite: None. | `[UNAPPROVED PROPOSAL]` |

---

## 9. Verdict

**`READY FOR STAKEHOLDER APPROVAL TO PLAN STAGE 5D`**

The visual concepts, design review, and implementation plan are technically grounded, mathematically verified for accessibility, and aligned with Harmonia's offline-first architecture. Implementation planning can proceed immediately upon project lead approval of the decision defaults.
