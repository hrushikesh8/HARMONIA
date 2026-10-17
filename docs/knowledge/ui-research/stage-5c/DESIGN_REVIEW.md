# Harmonia Mobile — Visual Design Review & Comparative Evaluation (Stage 5C)

**Document Classification:** Architectural Visual Review & Comparative Analysis  
**Status:** DRAFT CONCEPT FOR REVIEW (Stage 5C)  
**Author:** Principal Product Designer, Senior Android UI/UX Architect & Design Systems Engineer  

---

## 1. Executive Summary & Design Verdict

The visual design concepts created in Stage 5C establish a unified, premium, and technically grounded design direction for **Harmonia Mobile**. The visual language—anchored by the "Obsidian Acoustic Instrument" aesthetic—succeeds in creating an authoritative personal music player that is distinct from generic streaming services (Spotify, YouTube Music) and dense power tools (Poweramp).

### Evaluation Criteria & Comparative Matrix

All four concepts were evaluated against 12 rigorous criteria defined in the Stage 5C mandate:

| Evaluation Criterion | Concept A (Home & Library) | Concept B (Now Playing) | Concept C (Settings & EQ) | Concept D (Search & Playlists) | Overall Rating |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **1. Brand Distinctiveness** | High (Quiet obsidian, no ads) | Superior (Commanding vinyl art, cyan signal) | Superior (Honest storage & EQ) | High (Clean typography) | **Exceptional** |
| **2. Visual Consistency** | Seamless | Seamless | Seamless | Seamless | **Unified** |
| **3. Album Art Presentation** | 120dp cards, 48dp thumbnails | Centered max 320dp square | N/A (Functional) | 120dp playlist hero | **Exemplary** |
| **4. Playback Control Clarity** | Tactile Mini-Player | Dominant 64dp FAB, clear icons | N/A (Equalizer sliders) | Clear Play All / Shuffle pills | **Tactile** |
| **5. One-Handed Reachability** | Primary tabs & Mini-Player bottom | Controls anchored in bottom 50% | Sliders & toggles in middle/lower | Reorder buttons on right thumb | **Ergonomic** |
| **6. Information Density** | Clean, uncluttered metrics | Focused, non-intrusive format chip | Compact, organized cards | Structured tabular track rows | **Balanced** |
| **7. Readability & Contrast** | 14.82:1 (AAA) text contrast | 17.70:1 (AAA) text contrast | 14.82:1 (AAA) text contrast | 14.82:1 (AAA) text contrast | **Verified** |
| **8. Accessibility & Scaling** | Auto-expanding row heights | Responsive artwork (max 320dp) | 48dp vertical slider targets | 48dp reorder touch targets | **Scalable** |
| **9. Error & State Honesty** | Factual offline counts | Non-blocking corrupt file skips | Distinct unmounted / offline badges | Factual local zero-state | **Honest** |
| **10. Offline-First Identity** | 100% offline library metrics | Local audio file playback | Local mount path inspection | Local SQLite catalog queries | **Sovereign** |
| **11. Compose Feasibility** | 100% achievable in Compose | Standard Compose layouts | Standard Android AudioEffect | Standard LazyColumn list | **High** |
| **12. Separation of Status** | Verified data separated from proposed | Verified playback vs proposed chip | Verified Coordinator vs proposed EQ | Verified discrete vs proposed drag | **Strict** |

---

## 2. External Benchmark References & Specific Lessons

Rather than copying existing applications, Harmonia’s design extracts specific engineering and UX lessons from leading benchmarks:

1. **Musicolet (Empirical Benchmark):**
   - *Lesson Taken:* Multi-queue navigation and factual offline tag presentation.
   - *Harmonia Differentiation:* Eliminates Musicolet’s excessive micro-icon clutter, providing a calm, spacious Material 3 layout with hairline structural borders.

2. **Poweramp (Empirical Benchmark):**
   - *Lesson Taken:* Dedicated graphic equalizer precision and technical audio telemetry.
   - *Harmonia Differentiation:* Avoids complex skeuomorphic knobs and unverified "direct-to-DAC" claims. Harmonia provides an honest 5-band slider interface anchored in standard Android `AudioTrack` APIs.

3. **Auxio (Open-Source Benchmark):**
   - *Lesson Taken:* Clean, distraction-free playback with seamless Mini-Player to Now Playing transitions.
   - *Harmonia Differentiation:* Enhances Auxio’s minimalist baseline with high-contrast cyan-teal accent signals, robust storage volume lifecycle feedback, and discrete touch-target reordering.

4. **Android Material 3 & Modern Android Guidance:**
   - *Lesson Taken:* Dynamic color support, strict 48dp minimum touch bounding boxes, predictive back integration, and edge-to-edge system bar insets.

---

## 3. Recommended Visual Direction & Rationale

We formally recommend adopting the **"Obsidian Acoustic Instrument"** visual direction demonstrated across Concepts A–D:

- **Dominant Base:** Pure Obsidian (`#090B10`) paired with Elevated Charcoal surfaces (`#141721`).
- **Primary Electrical Signal:** Acoustic Cyan-Teal (`#00D2B4`) used exclusively for active transport states, scrub lines, and system health.
- **Secondary Curated Depth:** Harmonic Violet (`#6E56CF`) for user-curated playlist environments.
- **Structural Framing:** 1dp hairline borders (`#2A3042`) providing physical separation without blur shadows.

**Key Trade-offs Acknowledged:**
- The dark-first baseline prioritizes nighttime and indoor listening. The proposed high-contrast Light Theme (`SlateDay #F4F6F9`, Deep Teal `#006064`) must be maintained in parallel for high-glare outdoor environments.
- Limiting the Now Playing artwork to `max(320dp)` prevents vertical clipping on small screens (e.g. 5.5" phones) and under 200% font scaling, sacrificing excessive artwork scaling for total layout stability.

---

## 4. Accessibility & Feasibility Summary

- **Mathematical Contrast:** Primary text-to-background contrast is **17.70:1** (WCAG AAA). Secondary text is **5.95:1** (WCAG AA). Active cyan-teal signal is **10.19:1** (WCAG AAA).
- **Physical Touch Target Guarantee:** All interactive elements (play/pause, skip, dismiss, overflow menu, equalizer sliders, reorder buttons) strictly enforce `48.dp × 48.dp` minimum touch bounds.
- **Compose Technical Feasibility:** All components utilize standard Jetpack Compose primitives (`LazyColumn`, `ModalBottomSheet`, `Slider`, `Card`, `IconButton`). Zero unverified native NDK UI dependencies are introduced.
