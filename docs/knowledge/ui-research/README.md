# Harmonia Mobile — UI Architecture, Research & Design System Specification (Stage 5A & 5B)

**Document Classification:** Architectural Design & UX Specification Package  
**Status:** COMPLETE & FROZEN AT ACCEPTANCE GATE (Stage 5B Complete)  
**Baseline Anchor:** Commit `ad3d1a0764ba594a303d6175314b8fd3deab3089` on branch `master`  
**Author:** Principal Mobile Product Designer, Android UX Architect & Design Systems Engineer for Harmonia  
**Scope:** Research, empirical audit, reference analysis, design principles, screen inventory, architectural resolution, visual design system tokens, screen-by-screen specifications, interaction flows, and visual verification plan. **Zero implementation code changes.**

---

## 1. Package Purpose and Boundaries

This comprehensive specification package establishes the implementation-ready UX and visual foundation for **Harmonia Mobile**, a native offline personal music player built on Android (Kotlin, Jetpack Compose, Material 3, AndroidX Media3, Room SQLite).

Harmonia operates under a strict **local-first and read-only media invariant**:
- Media files are never renamed, deleted, moved, or tagged.
- Audio playback functions autonomously without a PC, server, internet connection, or AI service.
- The UI layer strictly reflects underlying domain guarantees (two-tier decoupled identity, volume disconnection protection, non-destructive sleep timer, platform audio effect routing).

> [!IMPORTANT]
> **Stage 5B Execution Boundaries:**
> - Specification and architectural design only. No Kotlin, Compose, XML, manifest, build script, or dependency edits.
> - Personalization Phase 4 (Favorites and 1–5 Star Rating UI controls) is specified as a future design requirement; no UI implementation has begun.
> - No mockups are presented as approved production designs.
> - All tests, build configurations, and unrelated working-tree modifications are strictly preserved.

---

## 2. Evidence Taxonomy

To prevent subjective design assertions, unverified visual assumptions, or premature architectural commitments, all statements and recommendations across this package use a strict evidence taxonomy:

| Evidence Tag | Definition | Permitted Usage |
| :--- | :--- | :--- |
| `[VERIFIED-SRC]` | Directly verified against working-tree source code or schemas. | Concrete line-numbered code references in `harmonia-mobile/` or `src/harmonia/`. |
| `[VERIFIED-DOC]` | Supported by authoritative platform or project documentation. | Official Android, Material 3, AndroidX Media3, or accepted Harmonia ADR specs. |
| `[OBSERVED-REF]` | Directly observed and documented in a cited external reference app. | Empirical findings from Musicolet, Poweramp, Oto Music, Auxio, Resonate, etc. |
| `[REPORT-BACKED]` | Supported by prior formal research reports in `research/`. | Claims originating from existing milestone audits or research papers. |
| `[INFERRED]` | Reasoned deduction or usability hypothesis requiring empirical validation. | Usability friction hypotheses, potential layout edge cases, or gesture conflicts. |
| `[PROPOSED]` | Concrete UX or architectural recommendation awaiting stakeholder approval. | Proposed screens, components, layout improvements, and design tokens. |
| `[DEFERRED]` | Feature or pattern deliberately placed out of scope for current phases. | Cloud streaming, tag editing, custom DSP NDK filters, iOS, companion sync. |

---

## 3. Package Navigation & Document Index

The complete Stage 5 research and specification package consists of 11 interconnected documents located under `docs/knowledge/ui-research/`:

### Stage 5A: Research & Audit Baseline
1. [`README.md`](README.md) — *This document:* Package overview, methodology, evidence taxonomy, and navigational index.
2. [`CURRENT_UI_AUDIT.md`](CURRENT_UI_AUDIT.md) — Exhaustive empirical audit of Harmonia Mobile's current Compose UI (`MainActivity`, `HomeScreen`, `LibraryScreen`, `PlaylistsScreen`, `SearchScreen`, `SettingsScreen`, `MiniPlayer`, `NowPlayingSheet`, design tokens, and error/unmounted states).
3. [`REFERENCE_RESEARCH.md`](REFERENCE_RESEARCH.md) — Detailed comparative analysis of external reference applications (Musicolet, Poweramp, Oto Music) alongside open-source benchmarks (Auxio, LocalTracklist, Resonate) and modern Android / Material 3 design guidance.
4. [`DESIGN_PRINCIPLES.md`](DESIGN_PRINCIPLES.md) — Core UX and visual principles defining Harmonia's sovereign, tactile, audiophile-inspired identity, reachability rules, typography, contrast standards, and state honesty.
5. [`SCREEN_INVENTORY.md`](SCREEN_INVENTORY.md) — Comprehensive inventory of existing, improved, proposed, and deferred screens, modal sheets, dialogs, and states.
6. [`OPEN_QUESTIONS.md`](OPEN_QUESTIONS.md) — Architectural, ergonomic, and product questions separating resolved architectural recommendations from open decisions awaiting user approval.

### Stage 5B: Implementation-Ready Specifications
7. [`DESIGN_SYSTEM_SPEC.md`](DESIGN_SYSTEM_SPEC.md) — Exhaustive design system specification: semantic design tokens for Dark and Light themes, typography scale, 8dp layout grid, shapes, 48dp minimum touch targets, artwork rules, motion, and measurable accessibility criteria.
8. [`NAVIGATION_SPEC.md`](NAVIGATION_SPEC.md) — Routing and navigation architecture: evaluation of `Navigation Compose` type-safe routes, top-level tabs, detail back-stack behavior, search state preservation, scroll restoration, and adaptive layouts (compact, medium, expanded).
9. [`SCREEN_SPECIFICATIONS.md`](SCREEN_SPECIFICATIONS.md) — Screen-by-screen layout hierarchy, components, data dependencies, error states, and acceptance criteria for all catalog, playback, settings, and edge-case views.
10. [`INTERACTION_FLOWS.md`](INTERACTION_FLOWS.md) — 13 critical end-to-end user journeys and state transitions (first launch, SAF selection, scanning, playback, queue reordering, search preservation, storage disconnection, sleep timer, equalizer control loss, personalization).
11. [`VISUAL_VERIFICATION_PLAN.md`](VISUAL_VERIFICATION_PLAN.md) — QA verification protocol covering device matrix configurations, theme verification, 200% text scaling, artwork edge cases, large catalog stress testing, touch-target verification, and acceptance checklists.

### Stage 5C: Visual Design Concepts & Review
12. [`stage-5c/README.md`](stage-5c/README.md) — *Stage 5C Package Entry:* Overview of 4 primary visual concepts, design review, implementation handoff draft, and required approval gates.
13. [`stage-5c/CONCEPT_A_HOME_LIBRARY.md`](stage-5c/CONCEPT_A_HOME_LIBRARY.md) — Concept A: Home & Library surface with visual mockup.
14. [`stage-5c/CONCEPT_B_NOW_PLAYING.md`](stage-5c/CONCEPT_B_NOW_PLAYING.md) — Concept B: Now Playing playback stage with visual mockup.
15. [`stage-5c/CONCEPT_C_SETTINGS_STORAGE_EQ.md`](stage-5c/CONCEPT_C_SETTINGS_STORAGE_EQ.md) — Concept C: Settings, storage lifecycle, and 5-band equalizer with visual mockup.
16. [`stage-5c/CONCEPT_D_SEARCH_PLAYLISTS.md`](stage-5c/CONCEPT_D_SEARCH_PLAYLISTS.md) — Concept D: Search & playlist details with discrete 48dp reordering.
17. [`stage-5c/DESIGN_REVIEW.md`](stage-5c/DESIGN_REVIEW.md) — Comparative design evaluation, benchmark analysis, and visual direction recommendation.
18. [`stage-5c/IMPLEMENTATION_HANDOFF_DRAFT.md`](stage-5c/IMPLEMENTATION_HANDOFF_DRAFT.md) — Implementation sequencing draft (Phases 5D.1–5D.4).
19. [`stage-5c/DECISIONS_REQUIRED.md`](stage-5c/DECISIONS_REQUIRED.md) — Architectural and design decision gates awaiting project lead approval.

---

## 4. Key Architectural & Design Decisions (Stage 5B Resolutions)

1. **Navigation Paradigm (`[PROPOSED]`):** Recommend adopting `androidx.navigation:navigation-compose` with type-safe `@Serializable` routes to support robust detail back-stacks (`AlbumDetail`, `ArtistDetail`, `PlaylistDetail`) and Android 14+ predictive back gestures without compromising decoupled playback state.
2. **Sheet Expansion Dynamics (`[PROPOSED]`):** Retain standard `ModalBottomSheet` for initial simplicity while engineering touch-target parity; evaluate continuous gesture-interpolated sheet expansion (Auxio pattern) as a dedicated enhancement.
3. **Personalization Phase 4 Boundary (`[PROPOSED]`):** Heart toggle and 1–5 star rating UI requirements are formally specified in `SCREEN_SPECIFICATIONS.md` and `INTERACTION_FLOWS.md` mapping to existing `PersonalizationViewModel` flows, while strictly deferring code implementation to authorized Phase 4.
4. **Equalizer Surface Placement (`[PROPOSED]`):** Maintain the primary master 5-band EQ panel inside `SettingsScreen`, with a proposed quick-access icon in `NowPlayingSheet` opening an Equalizer modal bottom sheet during active listening.
5. **Dual Theme Strategy (`[PROPOSED]`):** Dark Mode (`ObsidianSpace #090B10`) is the signature, default primary experience; Light Mode (`SlateDay #F4F6F9`) is specified with strict WCAG AA contrast (15.2:1 text contrast) for high-ambient outdoor sunlight readability.
6. **Technical Audio Telemetry (`[PROPOSED]`):** Non-intrusive format chip on Now Playing (e.g. `FLAC · 24-bit / 96 kHz`) opening an on-demand **Audio Signal Inspector** modal sheet displaying container format, codec, bitrate, sample rate, bit depth, channel configuration, and AudioTrack direct sink telemetry.

