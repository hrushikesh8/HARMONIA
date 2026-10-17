# Harmonia Mobile — Stage 5C: Visual Design Concepts & Review

**Document Classification:** Visual Design Milestone Package  
**Status:** COMPLETE & PROPOSED (Stage 5C Review Gate)  
**Author:** Principal Product Designer, Senior Android UI/UX Architect & Design Systems Engineer  
**Baseline Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  
**Execution Safety:** Zero application source code, build scripts, tests, manifests, or dependencies modified.  

---

## 1. Purpose & Scope

This package delivers the visual design direction and screen concepts for **Harmonia Mobile**, translating the accepted Stage 5B specifications into concrete, reviewable visual artifacts, component taxonomies, and architectural handoff plans.

All concepts adhere strictly to Harmonia's **local-first and offline-first product identity**:
- Autonomous offline playback without cloud, streaming, or AI dependencies.
- Strict read-only media file policy (no file tagging, renaming, or deletion).
- Honest physical representation of storage volume lifecycle and platform audio effect routing.
- High-contrast, tactile audiophile aesthetics ("Obsidian Acoustic Instrument").

---

## 2. Directory Index & Document Navigation

The Stage 5C package contains the following 8 comprehensive documents and associated visual artifacts located in `docs/knowledge/ui-research/stage-5c/`:

1. [`README.md`](README.md) — *This document:* Package purpose, scope, and navigation index.
2. [`CONCEPT_A_HOME_LIBRARY.md`](CONCEPT_A_HOME_LIBRARY.md) — Concept A: Home dashboard, recently played carousel, library metrics, segmented catalog tabs, track lists, and persistent Mini-Player. Includes visual mockup artifact [`concept_a_home_library.jpg`](concept_a_home_library.jpg).
3. [`CONCEPT_B_NOW_PLAYING.md`](CONCEPT_B_NOW_PLAYING.md) — Concept B: Now Playing playback stage, commanding vinyl artwork, scrub navigation, primary transport controls, technical format chip, Audio Signal Inspector specification, and queue drawer peek. Includes visual mockup artifact [`concept_b_now_playing.jpg`](concept_b_now_playing.jpg).
4. [`CONCEPT_C_SETTINGS_STORAGE_EQ.md`](CONCEPT_C_SETTINGS_STORAGE_EQ.md) — Concept C: Sovereign settings hierarchy, physical storage volume lifecycle states (connected vs unmounted SD card), 5-band graphic equalizer with dB gain readouts, and sleep timer. Includes visual mockup artifact [`concept_c_settings_storage_eq.jpg`](concept_c_settings_storage_eq.jpg).
5. [`CONCEPT_D_SEARCH_PLAYLISTS.md`](CONCEPT_D_SEARCH_PLAYLISTS.md) — Concept D: Categorized local search, playlist hero header, violet accent styling, verified 48dp discrete Move Up/Move Down track reordering, and proposed continuous drag-and-drop reordering.
6. [`DESIGN_REVIEW.md`](DESIGN_REVIEW.md) — Comprehensive visual design review, 12-point comparative evaluation matrix, external benchmark lessons (Musicolet, Poweramp, Auxio, Material 3), and recommended visual direction rationale.
7. [`IMPLEMENTATION_HANDOFF_DRAFT.md`](IMPLEMENTATION_HANDOFF_DRAFT.md) — Phased implementation sequence (5D.1 to 5D.4), component dependencies, state prerequisites, and test verification suite requirements.
8. [`DECISIONS_REQUIRED.md`](DECISIONS_REQUIRED.md) — Four explicit architectural and design decisions requiring project-lead approval prior to Phase 5D implementation.
9. [`VISUAL_APPROVAL_CHECKLIST.md`](VISUAL_APPROVAL_CHECKLIST.md) — Executive visual review and sign-off checklist covering the 4 concepts, cross-concept consistency, and open decision gates.
10. [`FINAL_DESIGN_REVIEW.md`](FINAL_DESIGN_REVIEW.md) — Comprehensive final visual design evaluation, strengths/weaknesses, scope recommendations, and implementation readiness verdict.
11. [`STAGE_5D_IMPLEMENTATION_PLAN_DRAFT.md`](STAGE_5D_IMPLEMENTATION_PLAN_DRAFT.md) — Phased implementation plan (Phases 5D.1–5D.5), component dependencies, state owners, and test requirements.

---

## 3. Visual Language Summary: "Obsidian Acoustic Instrument"

- **Background:** `ObsidianSpace` (`#090B10`) — Deep, distraction-free matte black.
- **Elevated Surfaces:** `CharcoalElevated` (`#141721`) with 1dp structural hairline borders (`BorderSlate #2A3042`).
- **Active Signal:** `AcousticTeal` (`#00D2B4`) — High-contrast cyan-teal accent for transport states, active scrub, and system health (10.19:1 contrast).
- **Curated Spaces:** `HarmonicViolet` (`#6E56CF`) — Restrained secondary accent for playlists and collections.
- **Accessibility Guarantee:** All interactive touch targets measure $\ge$ 48dp × 48dp; all text pairings meet or exceed WCAG AA/AAA standards.

---

## 4. Visual Approval Readiness Status

**Current Readiness Status:** **`READY FOR STAKEHOLDER APPROVAL TO PLAN STAGE 5D`**

- Concepts A, B, and C possess fully rendered, high-resolution visual phone mockups (`concept_a_home_library.jpg`, `concept_b_now_playing.jpg`, `concept_c_settings_storage_eq.jpg`).
- Concept D is completely specified via precision architectural ASCII blueprints, component token mappings, and verified source bindings (image mockup recommended as a follow-up once API quota resets).
- The implementation plan is drafted in [`STAGE_5D_IMPLEMENTATION_PLAN_DRAFT.md`](STAGE_5D_IMPLEMENTATION_PLAN_DRAFT.md). Implementation remains strictly on hold until explicit approval is granted by the project lead.
