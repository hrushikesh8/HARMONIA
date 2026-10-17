# Harmonia Engineering Knowledge Base

Welcome to the durable, authoritative engineering knowledge base for the Harmonia music system.

---

## 1. Purpose and Philosophy

Harmonia is built on deterministic music infrastructure, strict offline-first principles, clean architecture, and decoupled content identity.

This knowledge base serves as the persistent historical and architectural record of Harmonia's evolution. It documents:
- **Milestones, phases, and subphases** across the ecosystem with verifiable evidence.
- **Architectural decisions** and the rejected alternatives considered.
- **Difficult bugs, root-cause analyses, and regression-prevention invariants**.
- **Accurate verification records**, separating executed tests from compilation-only or device-pending status.

---

## 2. Product Ecosystem Separation

To avoid confusion and maintain clean boundaries, Harmonia separates its codebase into three distinct tiers:

1. **Harmonia Desktop / Core Server (Python 3.11+)**
   - **Directory:** `src/harmonia/`, `tests/`, `migrations/`
   - **Scope:** High-throughput filesystem scanner, Mutagen metadata parser, ITU-R BS.1770-4 loudness analyzer, SQLite WAL engine with FTS5 search, local-first FastAPI REST API, and embedded web player.
   - **Guiding Architecture:** [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md), [`docs/DATA_MODEL.md`](../DATA_MODEL.md), [`docs/ROADMAP.md`](../ROADMAP.md).

2. **Harmonia Mobile (Android / Kotlin 2.0+ & Jetpack Compose)**
   - **Directory:** `harmonia-mobile/app/`
   - **Scope:** Native Android audio player built with AndroidX Media3 (ExoPlayer), Room SQLite database, Storage Access Framework (SAF) multi-volume crawler, platform audio effect intents, sleep timer, and offline personal annotations (Favorites & 1–5 Star Ratings).
   - **Guiding Architecture:** [`docs/MOBILE_ARCHITECTURE_v0.1.md`](../MOBILE_ARCHITECTURE_v0.1.md), [`docs/MOBILE_M4_ARCHITECTURE.md`](../MOBILE_M4_ARCHITECTURE.md), [`docs/MOBILE_M5_ARCHITECTURE.md`](../MOBILE_M5_ARCHITECTURE.md).

3. **Harmonia AI (Future Standalone Companion)**
   - **Scope:** Independent conversational and context-aware companion that interacts strictly through Harmonia's public API and maintains its own isolated database (`harmonia_ai.db`). Zero code currently resides in this repository.
   - **Guiding Roadmap:** [`docs/ROADMAP.md:§Stream 5`](../ROADMAP.md#6-stream-5-harmonia-ai-conversational-companion).

---

## 3. Knowledge Base Navigation

- **[Master Implementation Map](IMPLEMENTATION_MAP.md)**
  A centralized status matrix tracking all past, active, and planned milestones, subphases, and tasks with evidence tags, confidence ratings, and commit anchors.
- **[Engineering Journal](JOURNAL.md)**
  A chronological record of milestones, breakthroughs, architectural reviews, and remediation passes backed by verified Git history and session records.
- **[Phase Completion Records](phases/)**
  Detailed evidence-based records for completed milestones and subphases:
  - [`mobile_m4f.md`](phases/mobile_m4f.md): Storage Lifecycle, UI Feedback & Hardening
  - [`mobile_m5a_automotive.md`](phases/mobile_m5a_automotive.md): MediaLibraryService Foundation & Manifest
  - [`mobile_m5b_media_library.md`](phases/mobile_m5b_media_library.md): Media Tree Hierarchy, Browsing & External Playback
  - [`mobile_m5c1_audio_effect_contracts.md`](phases/mobile_m5c1_audio_effect_contracts.md): Audio Effect Contracts & State Machine
  - [`mobile_m5c2_audio_session_integration.md`](phases/mobile_m5c2_audio_session_integration.md): Platform Equalizer & Audio Session Integration
  - [`mobile_m5c3_equalizer_settings.md`](phases/mobile_m5c3_equalizer_settings.md): Equalizer Preferences & Settings UI
  - [`mobile_m5d_sleep_timer.md`](phases/mobile_m5d_sleep_timer.md): Coroutine Sleep Timer & Volume Ergonomics
  - [`mobile_personalization_p1_persistence.md`](phases/mobile_personalization_p1_persistence.md): Persistence Foundation (Schema v5)
  - [`mobile_personalization_p2_repository.md`](phases/mobile_personalization_p2_repository.md): Domain & Repository Layer
  - [`mobile_personalization_p3_viewmodel.md`](phases/mobile_personalization_p3_viewmodel.md): ViewModels & Presentation State
- **[Architecture Decision Records](adr/)**
  Documented architectural decisions with context, tradeoffs, and invariants:
  - [`ADR-032`](adr/ADR-032-media-library-automotive-browsing.md): Android Auto MediaLibraryService Architecture & Hierarchical Browsing
  - [`ADR-033`](adr/ADR-033-equalizer-persisted-user-intent.md): Decoupling Persisted Equalizer User Intent from Transient Hardware Audio Sessions
  - [`ADR-034`](adr/ADR-034-audio-effect-lifecycle-and-teardown.md): Deterministic Audio Effect Lifecycle, Asynchronous Event Decoupling & Synchronous Teardown
  - [`ADR-035`](adr/ADR-035-sleep-timer-monotonic-clock-and-volume-safety.md): Coroutine Sleep Timer Monotonic Clock Suspension, Baseline Volume Safety & Transition Handling
  - [`ADR-036`](adr/ADR-036-personalization-persistence-schema-v5.md): Room-Backed Track Personalization Schema v5 — Row-Presence Semantics & Deletion Tombstone Segregation
  - [`ADR-037`](adr/ADR-037-personalization-presentation-architecture.md): Decoupled Presentation Architecture for Track Personalization — Follow-Mode vs Explicit Binding
  - [`ADR-038`](adr/ADR-038-favorites-playback-queue.md): Library Favorites Playback Queue Contract — Point-in-Time Snapshot Semantics vs Dynamic Synchronization
- **[Engineering Incident Records](incidents/)**
  Post-mortems and root-cause analyses of consequential defects with regression tests:
  - [`INC-001`](incidents/INC-001-storage-unmount-during-playback.md): Storage Volume Disconnection During Active Playback
  - [`INC-002`](incidents/INC-002-concurrent-scan-mutex-lock-contention.md): Concurrent Scan Lock Contention & Scanner Deadlock
  - [`INC-003`](incidents/INC-003-rapid-audio-session-race-and-generation-fencing.md): Rapid Audio Session Race & Generation Invalidation
  - [`INC-004`](incidents/INC-004-audio-effect-cancellation-masking-hal-leak.md): AudioEffect Native HAL Leak from Scope Cancellation
  - [`INC-005`](incidents/INC-005-sleep-timer-repeat-one-infinite-loop.md): Sleep Timer Repeat-One Infinite Loop
  - [`INC-006`](incidents/INC-006-personalization-manual-bind-playback-reversion.md): Personalization Manual Binding Reversion
- **[Templates](templates/)**
  Standardized templates for creating durable records:
  - [`PHASE_RECORD_TEMPLATE.md`](templates/PHASE_RECORD_TEMPLATE.md): Standardized format for capturing phase completions.
  - [`INCIDENT_TEMPLATE.md`](templates/INCIDENT_TEMPLATE.md): Root-cause analysis and regression-prevention template.

---

## 4. Key Authoritative References

### System Architecture & Data Models
- [Desktop Core Architecture (`docs/ARCHITECTURE.md`)](../ARCHITECTURE.md)
- [Desktop SQLite Data Model (`docs/DATA_MODEL.md`)](../DATA_MODEL.md)
- [Harmonia Visual Brand Identity & Design Tokens (`docs/BRAND_IDENTITY.md`)](../BRAND_IDENTITY.md)
- [Local REST API OpenAPI Contract (`docs/contracts/openapi.json`)](../contracts/openapi.json)

### Mobile Architecture Specifications
- [Mobile Master Architecture Specification v0.1 (`docs/MOBILE_ARCHITECTURE_v0.1.md`)](../MOBILE_ARCHITECTURE_v0.1.md)
- [Mobile Architecture Reconciliation (`docs/MOBILE_ARCHITECTURE_RECONCILIATION.md`)](../MOBILE_ARCHITECTURE_RECONCILIATION.md)
- [Mobile M3 Architecture Specification (`docs/MOBILE_M3_ARCHITECTURE.md`)](../MOBILE_M3_ARCHITECTURE.md)
- [Mobile M4 Local Storage Architecture (`docs/MOBILE_M4_ARCHITECTURE.md`)](../MOBILE_M4_ARCHITECTURE.md)
- [Mobile M5 Automotive & Audio Ergonomics (`docs/MOBILE_M5_ARCHITECTURE.md`)](../MOBILE_M5_ARCHITECTURE.md)

### Prior Milestone Implementation Reports
- **M1 Foundation:** [`docs/MOBILE_M1_IMPLEMENTATION.md`](../MOBILE_M1_IMPLEMENTATION.md)
- **M2 Native Playback Engine:** [`docs/MOBILE_M2_PLAYBACK.md`](../MOBILE_M2_PLAYBACK.md)
- **M3 UI & Playlists:**
  - M3-A Library: [`docs/MOBILE_M3A_IMPLEMENTATION.md`](../MOBILE_M3A_IMPLEMENTATION.md)
  - M3-B Search & Home: [`docs/MOBILE_M3B_IMPLEMENTATION.md`](../MOBILE_M3B_IMPLEMENTATION.md)
  - M3-C Now Playing & Queue: [`docs/MOBILE_M3C_IMPLEMENTATION.md`](../MOBILE_M3C_IMPLEMENTATION.md)
  - M3-D Playlists: [`docs/MOBILE_M3D_IMPLEMENTATION.md`](../MOBILE_M3D_IMPLEMENTATION.md)
  - M3-E Integration: [`docs/MOBILE_M3E_INTEGRATION.md`](../MOBILE_M3E_INTEGRATION.md)
- **M4 Storage Lifecycle:**
  - M4-A Storage Foundation: [`docs/MOBILE_M4A_IMPLEMENTATION.md`](../MOBILE_M4A_IMPLEMENTATION.md)
  - M4-B Metadata & Artwork: [`docs/MOBILE_M4B_IMPLEMENTATION.md`](../MOBILE_M4B_IMPLEMENTATION.md)
  - M4-C Scanner Engine: [`docs/MOBILE_M4C_IMPLEMENTATION.md`](../MOBILE_M4C_IMPLEMENTATION.md)
  - M4-D Volume Disconnection: [`docs/MOBILE_M4D_IMPLEMENTATION.md`](../MOBILE_M4D_IMPLEMENTATION.md)
  - M4-E Integration Gate: [`docs/MOBILE_M4E_IMPLEMENTATION.md`](../MOBILE_M4E_IMPLEMENTATION.md)

### Competitive Reference Studies
- [Music Player Architecture Studies (`research/music_players/`)](../../research/music_players/) (25 comparative reference audits covering Auxio, Fooyin, Beets, Tauon, LocalSend, etc.)

---

## 5. Evidence Standards & Confidence Classification

Every claim in this knowledge base is labeled according to its verification level:

1. `[VERIFIED-SRC]`: Directly proven by current committed source code, active Git tree, or verified test output.
2. `[REPORT-BACKED]`: Supported by detailed engineering audit reports and verified working-tree artifacts, but resides uncommitted.
3. `[INFERRED-HIST]`: Reconstructed from commit messages, restore tags, and cross-references.
4. `[PLANNED-FUTURE]`: Planned design or future phase with zero implementation in the repository.
