# Harmonia Master Implementation Map

This document is the operational status matrix for all past, active, and planned engineering work across the Harmonia ecosystem. For the high-level multi-stream strategic roadmap, see [`docs/ROADMAP.md`](../ROADMAP.md).

---

## 1. Status Taxonomy & Confidence Scale

Every engineering unit is tagged with an explicit status label and an evidence confidence tag:

### Status Labels
- **ACCEPTED:** Independently audited, fully verified against requirements, and accepted.
- **REVIEWED:** Reviewed by independent engineering audit; findings identified and closed.
- **REPORTED COMPLETE:** Implementation and tests reported complete by author; awaiting independent acceptance audit.
- **IN PROGRESS:** Active implementation underway; working-tree modifications present.
- **VALIDATION PENDING:** Implementation and JVM tests pass, but physical hardware/emulator runtime execution is pending.
- **PLANNED:** Documented in architecture/roadmap; awaiting explicit human authorization before coding.

### Confidence Scale
- `[VERIFIED-SRC]`: Proven by current committed source code, active Git commit, or reproducible test output.
- `[REPORT-BACKED]`: Supported by audit logs, working-tree source files, and test XML reports, but uncommitted.
- `[INFERRED-HIST]`: Reconstructed from commit messages, restore tags, and cross-references.
- `[PLANNED-FUTURE]`: Design phase only; zero implementation code in repository.

---

## 2. Harmonia Desktop & Core Ecosystem

| Milestone / Phase | Subsystem / Scope | Storage / Commit Status | Status | Evidence & Confidence | Key Artifacts / Anchor |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Desktop Phase 1** | Python foundation, ORM, scanner, CLI | Committed (`master`) | **ACCEPTED** | `[VERIFIED-SRC]` | `src/harmonia/`, tag `restore-point/phase-3-1-start` |
| **Desktop Phase 2** | Metadata normalization, FTS5 search, duplicate detection | Committed (`master`) | **ACCEPTED** | `[VERIFIED-SRC]` | `src/harmonia/domain/services/`, Alembic migrations |
| **Desktop Phase 3** | Audio intelligence (BS.1770-4 LUFS, True Peak), FastAPI | Committed (`master`) | **ACCEPTED** | `[VERIFIED-SRC]` | `src/harmonia/api/`, commit `5dc60826` |
| **Visual Productization** | Desktop Web UI served at `/` and `/ui`, dock player | Committed (`master`) | **ACCEPTED** | `[VERIFIED-SRC]` | `src/harmonia/api/static/`, commit `3bb7ce17` |
| **Desktop Phase 4** | Native playback engine (mpv), queue, listening events | Uncommitted in working tree | **IN PROGRESS** | `[VERIFIED-SRC]` | `src/harmonia/application/playback_service.py`, `tests/integration/` |
| **Desktop Phase 5** | Harmonia AI companion (`harmonia_ai.db`), recommendations | Not started | **PLANNED** | `[PLANNED-FUTURE]` | [`docs/ROADMAP.md:§Stream 5`](../ROADMAP.md#6-stream-5-harmonia-ai-conversational-companion) |
| **Desktop Phase 6–8** | Context engine, Jarvis protocol adapter, audiophile polish | Not started | **PLANNED** | `[PLANNED-FUTURE]` | [`docs/ROADMAP.md`](../ROADMAP.md) |

---

## 3. Harmonia Mobile: Foundation & Storage Lifecycle (M1 – M4-F)

All work in this section has been completed, merged, and committed to Git on branch `master`.

| Milestone | Scope & Core Components | Commit Anchor | Status | Evidence & Confidence | Verified Tests & Deliverables |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **M1** | Android shell, Room v1 schema, design system, squircle icon | `34a5f791` | **ACCEPTED** | `[VERIFIED-SRC]` | Clean build, [`docs/MOBILE_M1_IMPLEMENTATION.md`](../MOBILE_M1_IMPLEMENTATION.md) |
| **M2** | Media3 ExoPlayer service, audio output pipeline, notification | `51218be7` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m2-complete`, [`docs/MOBILE_M2_PLAYBACK.md`](../MOBILE_M2_PLAYBACK.md) |
| **M3-A** | Database, navigation, library foundation (Tracks, Albums, Artists) | `37407705` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m3a-complete`, [`docs/MOBILE_M3A_IMPLEMENTATION.md`](../MOBILE_M3A_IMPLEMENTATION.md) |
| **M3-B** | Search, Home screen, content-addressed artwork loader | `e05a74f2` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m3b-complete`, [`docs/MOBILE_M3B_IMPLEMENTATION.md`](../MOBILE_M3B_IMPLEMENTATION.md) |
| **M3-C** | Now Playing sheet, MiniPlayer, authoritative queue transport | `6057d5e6` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m3c-complete`, [`docs/MOBILE_M3C_IMPLEMENTATION.md`](../MOBILE_M3C_IMPLEMENTATION.md) |
| **M3-D** | User playlists CRUD, reordering, Room persistence | `d6f34e97` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m3d-complete`, [`docs/MOBILE_M3D_IMPLEMENTATION.md`](../MOBILE_M3D_IMPLEMENTATION.md) |
| **M3-E** | Full M3 UI & integration test suite verification | `c952e159` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m3-complete`, [`docs/MOBILE_M3E_INTEGRATION.md`](../MOBILE_M3E_INTEGRATION.md) |
| **M4-A** | Storage foundation (`StorageSourceEntity`, Room v2) | `f389fbd9` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m4a-complete`, [`docs/MOBILE_M4A_IMPLEMENTATION.md`](../MOBILE_M4A_IMPLEMENTATION.md) |
| **M4-B** | Audio metadata extraction & artwork caching engine | `91447c77` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m4b-complete`, [`docs/MOBILE_M4B_IMPLEMENTATION.md`](../MOBILE_M4B_IMPLEMENTATION.md) |
| **M4-C** | MediaStore & SAF tree scanners, provider watermarks | `797fdf23` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m4c-complete`, [`docs/MOBILE_M4C_IMPLEMENTATION.md`](../MOBILE_M4C_IMPLEMENTATION.md) |
| **M4-D** | Volume lifecycle, unmount protection, Room v4 (`MIGRATION_3_4`) | `7048c7e3` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m4d-complete`, [`docs/MOBILE_M4D_IMPLEMENTATION.md`](../MOBILE_M4D_IMPLEMENTATION.md) |
| **M4-E** | Multi-source storage UI, cross-caller scan mutex, SAF grants | `7048c7e3` | **ACCEPTED** | `[VERIFIED-SRC]` | Tag `restore-point/mobile-m4e-complete`, [`docs/MOBILE_M4E_IMPLEMENTATION.md`](../MOBILE_M4E_IMPLEMENTATION.md) |
| **M4-F** | Forensic release preflight, UI feedback & hardening | `ad3d1a07` | **ACCEPTED** | `[VERIFIED-SRC]` | Reported M4-F release gate: 192 JVM tests, 97 connected Android tests, 173 Python tests passed. Tag `restore-point/mobile-m4f-complete` (`789552af`), [`mobile_m4f.md`](phases/mobile_m4f.md), [`INC-001`](incidents/INC-001-storage-unmount-during-playback.md), [`INC-002`](incidents/INC-002-concurrent-scan-mutex-lock-contention.md) |

---

## 4. Harmonia Mobile: Automotive & Platform Audio Ergonomics (M5)

All M5 work currently resides **uncommitted in the local working tree** on top of HEAD commit `ad3d1a07`.

| Subphase | Component / Scope | Git Storage Status | Status | Evidence & Confidence | Key Invariants & Artifacts |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **M5-A** | `HarmoniaPlaybackService` migration to `MediaLibraryService`, automotive manifest | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | 200 JVM tests at gate, Media3 1.9.2 verified, [`mobile_m5a_automotive.md`](phases/mobile_m5a_automotive.md), [`ADR-032`](adr/ADR-032-media-library-automotive-browsing.md), `automotive_app_desc.xml` |
| **M5-B** | `MediaItemTreeResolver` hierarchy browsing, automotive voice & text search | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | 223 JVM tests at gate, Unicode query cache key, [`mobile_m5b_media_library.md`](phases/mobile_m5b_media_library.md), [`ADR-032`](adr/ADR-032-media-library-automotive-browsing.md) |
| **M5-C1** | `AudioEffect` domain contracts, monotonic generation state machine | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | 245 JVM tests at gate, Cancellation cleanup, [`mobile_m5c1_audio_effect_contracts.md`](phases/mobile_m5c1_audio_effect_contracts.md), [`ADR-033`](adr/ADR-033-equalizer-persisted-user-intent.md) |
| **M5-C2** | Android Equalizer bridge, ExoPlayer session ID broadcast | Uncommitted working tree | **REVIEWED / VALIDATION PENDING** | `[REPORT-BACKED]` | 268 JVM tests at gate, Synchronous release under NonCancellable; [`mobile_m5c2_audio_session_integration.md`](phases/mobile_m5c2_audio_session_integration.md), [`ADR-034`](adr/ADR-034-audio-effect-lifecycle-and-teardown.md), [`INC-003`](incidents/INC-003-rapid-audio-session-race-and-generation-fencing.md), [`INC-004`](incidents/INC-004-audio-effect-cancellation-masking-hal-leak.md) |
| **M5-C3** | SharedPreferences user intent, Settings UI equalizer controls | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | 290 JVM tests at gate, `SettingsViewModelEqualizerTest.kt`, [`mobile_m5c3_equalizer_settings.md`](phases/mobile_m5c3_equalizer_settings.md), [`ADR-033`](adr/ADR-033-equalizer-persisted-user-intent.md) |
| **M5-D** | Coroutine sleep timer with 30s volume fade-out & clock suspension | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | 305 JVM tests at gate, `HarmoniaSleepTimerTest.kt`, [`mobile_m5d_sleep_timer.md`](phases/mobile_m5d_sleep_timer.md), [`ADR-035`](adr/ADR-035-sleep-timer-monotonic-clock-and-volume-safety.md), [`INC-005`](incidents/INC-005-sleep-timer-repeat-one-infinite-loop.md) |

---

## 5. Harmonia Mobile: Personalization Milestone (Dedicated)

Personalization was separated from M5 and M6 into a dedicated milestone. All implemented work resides **uncommitted in the local working tree** on top of HEAD commit `ad3d1a07`.

| Phase | Scope & Key Contracts | Git Storage Status | Status | Evidence & Confidence | Verification & Test Evidence |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Discovery** | Schema reconciliation: row presence vs redundant boolean flags; M6 sync tombstone separation | Working tree (`MOBILE_ARCHITECTURE_v0.1.md`) | **ACCEPTED** | `[VERIFIED-SRC]` | [`docs/MOBILE_ARCHITECTURE_v0.1.md:§11.2`](../MOBILE_ARCHITECTURE_v0.1.md) |
| **Phase 1** | Room Schema v5 (`MIGRATION_4_5`), `FavoriteEntity`, `RatingEntity`, `PersonalizationDao`, exported schema `5.json` | Uncommitted working tree | **ACCEPTED / VALIDATION PENDING** | `[REPORT-BACKED]` | 305 JVM tests at gate, [`mobile_personalization_p1_persistence.md`](phases/mobile_personalization_p1_persistence.md), [`ADR-036`](adr/ADR-036-personalization-persistence-schema-v5.md), `5.json`, `HarmoniaDatabase.kt`, `RoomMigrationV4ToV5AndPersonalizationDaoTest.kt` (compiled in androidTest APK; runtime pending device) |
| **Phase 2** | Domain models (`TrackPersonalization`, `RatedTrack`), `PersonalizationRepository`, `OfflinePersonalizationRepository`, `ServiceLocator` | Uncommitted working tree | **ACCEPTED / VALIDATION PENDING** | `[REPORT-BACKED]` | 328 JVM tests at gate (23 focused tests), [`mobile_personalization_p2_repository.md`](phases/mobile_personalization_p2_repository.md), `OfflinePersonalizationRepositoryRealRoomTest.kt` compiled |
| **Phase 3** | `PersonalizationViewModel`, `TrackPersonalizationUiState`, reactive playback follow & manual binding, mutex serialization | Uncommitted working tree | **ACCEPTED / VALIDATION PENDING** | `[REPORT-BACKED]` | 344 JVM tests across 38 suites at working-tree gate (16 focused tests), [`mobile_personalization_p3_viewmodel.md`](phases/mobile_personalization_p3_viewmodel.md), [`ADR-037`](adr/ADR-037-personalization-presentation-architecture.md), [`INC-006`](incidents/INC-006-personalization-manual-bind-playback-reversion.md), `assembleDebug` & `assembleDebugAndroidTest` clean |
| **Phase 4** | Compose UI: Now Playing favorite/rating controls, Library Favorites tab, Rating Dialog | Uncommitted working tree | **REVIEWED / VALIDATION PENDING** | `[REPORT-BACKED]` | 363 JVM unit tests (0 failures), 10 connected Android UI tests passing 100% on `medium_phone(AVD) - 16` (`NowPlayingAndMiniPlayerPersonalizationUiTest` [5], `PersonalizationPrimitivesUiTest` [5]), 16 verified emulator screenshots; physical TalkBack pending; snapshot queue behavior pending ADR-038 |

---

## 6. Harmonia Mobile: "Obsidian Acoustic Instrument" UI System (Stages 5A–5D)

All UI implementation work currently resides **uncommitted in the local working tree** on top of HEAD commit `ad3d1a07`.

| Stage | Subsystem / Scope | Git Storage Status | Status | Evidence & Confidence | Key Invariants & Artifacts |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Stage 5A** | Reference Research & Design Principles (Acoustic Black, Dieter Rams, OP-1, Walkman) | Committed / Docs | **ACCEPTED** | `[VERIFIED-SRC]` | [`ui-research/REFERENCE_RESEARCH.md`](ui-research/REFERENCE_RESEARCH.md), [`DESIGN_PRINCIPLES.md`](ui-research/DESIGN_PRINCIPLES.md) |
| **Stage 5B** | UI Architecture, Navigation Flows & Component Specifications (11 specs) | Committed / Docs | **ACCEPTED** | `[VERIFIED-SRC]` | [`ui-research/SCREEN_SPECIFICATIONS.md`](ui-research/SCREEN_SPECIFICATIONS.md), [`NAVIGATION_SPEC.md`](ui-research/NAVIGATION_SPEC.md), [`DESIGN_SYSTEM_SPEC.md`](ui-research/DESIGN_SYSTEM_SPEC.md) |
| **Stage 5C** | Visual Concepts, Concept Art & Final Design Review (14 review files) | Committed / Docs | **ACCEPTED** | `[VERIFIED-SRC]` | [`ui-research/stage-5c/FINAL_DESIGN_REVIEW.md`](ui-research/stage-5c/FINAL_DESIGN_REVIEW.md), [`STAGE_5D_IMPLEMENTATION_PLAN_DRAFT.md`](ui-research/stage-5c/STAGE_5D_IMPLEMENTATION_PLAN_DRAFT.md) |
| **Stage 5D.1** | Design Tokens (`HarmoniaThemeTokens`, `Color.kt`, `Spacing.kt`, `Modifiers.kt`) | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | Mathematical WCAG AAA/AA $\ge 4.5:1$ contrast compliance verified in `HarmoniaThemeFoundationTest.kt` |
| **Stage 5D.2** | Playback Sheet & Transport Controls (`NowPlayingSheet.kt`, `MiniPlayer.kt`, Queue, Sleep Timer) | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | Docked mini-player, expandable sheet, queue bottom sheet, coroutine sleep timer dialog; verified in unit & connected tests |
| **Stage 5D.3** | Home & Library Screens (`HomeScreen.kt`, `LibraryScreen.kt`) | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | Tracks, Favorites, Albums, Artists tabs; multi-criteria sorting dialog; verified in unit & connected tests |
| **Stage 5D.4** | Settings, Equalizer & Storage Sources (`SettingsScreen.kt`) | Uncommitted working tree | **REVIEWED** | `[REPORT-BACKED]` | `EqualizerBandSlider`, `StorageSourceRow`, subsystem status inspection; verified in unit tests and screenshots |
| **Stage 5D.5** | Accessibility Verification & Visual Acceptance | Uncommitted working tree | **REVIEWED / VALIDATION PENDING** | `[REPORT-BACKED]` | 363 JVM unit tests passing (39 XML suites); 16 bit-perfect emulator screenshots archived; Compose semantics & touch targets ($\ge 48\text{dp}$) verified; physical TalkBack exploration pending |

---

## 7. Future Roadmap Milestones

| Milestone | Scope & Major Components | Status | Dependencies |
| :--- | :--- | :--- | :--- |
| **Consolidation (C1–C3)** | **Milestone Consolidation & Hardening:** C1 doc reconciliation, C2 `ADR-038` (Favorites queue snapshot contract), C3 Git commit checkpoints (`restore-point/mobile-m5-complete`, `restore-point/mobile-personalization-p4-complete`, `restore-point/mobile-stage-5d-complete`) | **IN PROGRESS** | Verification complete; awaiting C1–C3 execution |
| **Mobile M6** | **LocalSync P2P Engine:** Local Wi-Fi sync, mDNS discovery (`_harmonia._tcp`), QR pairing with pinned TLS, delta manifest exchange, `mobile_sync_tombstones`, resumable HTTP 206 audio downloads | **PLANNED** | Consolidation C1–C3 complete, local storage stability proven |
| **Mobile M7** | **Audiophile & Engine Hardening:** Gapless audio transitions, ReplayGain ITU-R BS.1770 volume normalization via ExoPlayer `AudioProcessor`, 100k track indexing stress validation | **PLANNED** | Milestone M6 complete |
| **Mobile M8** | **Automotive Deep Integration:** Advanced Android Auto templates (`androidx.car.app`), voice action routing | **PLANNED** | M5 automotive baseline & M6 sync |
