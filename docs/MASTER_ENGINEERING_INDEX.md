# Harmonia Ecosystem — Master Engineering Documentation Index

**Document Classification:** Top-Level Ecosystem Architecture & History Navigation  
**Document Identifier:** `INDEX-MASTER-001`  
**Status:** ACTIVE & AUTHORITATIVE  
**Ecosystem Baseline:** Commit `0fc183916be8ed2d773c72a487b968544745549b`  
**Cross-Worktree Scope:** Primary Development Repo (`HARMONIA`) & Isolated Release Worktree (`harmonia-release-build`)  

---

## 1. Ecosystem Worktree Topology

The Harmonia project ecosystem is partitioned across two synchronized Git worktrees to maintain strict release isolation:

```
                          ┌──────────────────────────────────────────────┐
                          │         Shared Git Object Database           │
                          │   Baseline Commit: 0fc183916be8ed2d773c...   │
                          └──────────────────────┬───────────────────────┘
                                                 │
                   ┌─────────────────────────────┴─────────────────────────────┐
                   │                                                           │
                   ▼                                                           ▼
┌───────────────────────────────────────┐   ┌───────────────────────────────────────────────┐
│ Primary Development Worktree (HARMONIA)│   │ Isolated Release Worktree (harmonia-release)  │
│ - Path: .../PROJECTS/HARMONIA         │   │ - Path: .../PROJECTS/harmonia-release-build   │
│ - Branch: master (active dev)         │   │ - State: Detached HEAD at 0fc18391            │
│ - Scope: Desktop, Core Server, Specs  │   │ - Scope: Android Release & Documentation      │
│ - Uncommitted: Personalization API    │   │ - Artifacts: Unsigned Candidate & Test APKs   │
└───────────────────────────────────────┘   └───────────────────────────────────────────────┘
```

---

## 2. Complete Engineering Documentation Corpus (12 Authoritative Documents)

Every cross-worktree reference is structured with a **dual-layer link standard**:
1. **Local Relative Link**: Functional within identical directory hierarchies.
2. **Canonical Portable Fallback**: Machine-readable coordinate `[Worktree | Path | DocumentID]` that survives cross-platform movement or remote archiving.

### Primary Development Worktree (`HARMONIA`)

| Document Title | Functional Focus | Canonical Portable Fallback | Local Relative Link |
|---|---|---|---|
| **Master Engineering Index** | Top-level navigation matrix, worktree topology, and inventory | `[Worktree: HARMONIA \| Path: docs/MASTER_ENGINEERING_INDEX.md \| ID: INDEX-MASTER-001]` | [MASTER_ENGINEERING_INDEX.md](MASTER_ENGINEERING_INDEX.md) |
| **Desktop Architecture** | Subsystem boundaries, streaming engine, audio intelligence worker | `[Worktree: HARMONIA \| Path: docs/ARCHITECTURE.md \| ID: ARCH-DESK-001]` | [ARCHITECTURE.md](ARCHITECTURE.md) |
| **Desktop Relational Data Model** | Relational schema, SQLite WAL, and Alembic migrations 001–006 | `[Worktree: HARMONIA \| Path: docs/DATA_MODEL.md \| ID: DATA-DESK-001]` | [DATA_MODEL.md](DATA_MODEL.md) |
| **Desktop Phase Ledger** | Forensic catalog of 41 desktop subphases, commits, and test passes | `[Worktree: HARMONIA \| Path: docs/DESKTOP_PHASE_LEDGER.md \| ID: LEDGER-DESK-PHASE-001]` | [DESKTOP_PHASE_LEDGER.md](DESKTOP_PHASE_LEDGER.md) |
| **Desktop Prompt Ledger** | 42 catalogued milestone directives from transcripts | `[Worktree: HARMONIA \| Path: docs/DESKTOP_PROMPT_LEDGER.md \| ID: LEDGER-DESK-PROMPT-001]` | [DESKTOP_PROMPT_LEDGER.md](DESKTOP_PROMPT_LEDGER.md) |
| **OpenAPI 3.1 REST Specification** | Contract definitions for 43 paths including Personalization WIP | `[Worktree: HARMONIA \| Path: docs/contracts/openapi.json \| ID: CONTRACT-REST-001]` | [openapi.json](contracts/openapi.json) |
| **LocalSync Protocol Specification** | Normative zero-cloud peer synchronization contract (M6-R1..R14) | `[Worktree: HARMONIA \| Path: docs/contracts/LOCALSYNC_PROTOCOL.md \| ID: SPEC-LOCALSYNC-001]` | [LOCALSYNC_PROTOCOL.md](contracts/LOCALSYNC_PROTOCOL.md) |

### Isolated Mobile Release Worktree (`harmonia-release-build`)

| Document Title | Functional Focus | Canonical Portable Fallback | Local Relative Link (from Primary Repo) |
|---|---|---|---|
| **Mobile Release Closeout** | Executive release statement, build provenance, signing instructions | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_RELEASE.md \| ID: DOC-MOB-001]` | [MOBILE_RELEASE.md](../../harmonia-release-build/docs/MOBILE_RELEASE.md) |
| **Mobile Engineering History** | Chronological technical narrative from M0.1 through M-REL.9K | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_ENGINEERING_HISTORY.md \| ID: DOC-MOB-002]` | [MOBILE_ENGINEERING_HISTORY.md](../../harmonia-release-build/docs/MOBILE_ENGINEERING_HISTORY.md) |
| **Mobile Phase Ledger** | 65-row tracking ledger through M-REL.9E (36 dev + 29 release rows) | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_PHASE_LEDGER.md \| ID: DOC-MOB-003]` | [MOBILE_PHASE_LEDGER.md](../../harmonia-release-build/docs/MOBILE_PHASE_LEDGER.md) |
| **Mobile Prompt Ledger** | 32 catalogued release sequence prompt directives (v2.8) | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_PROMPT_LEDGER.md \| ID: DOC-MOB-004]` | [MOBILE_PROMPT_LEDGER.md](../../harmonia-release-build/docs/MOBILE_PROMPT_LEDGER.md) |
| **Mobile Engineering Prompt Archive** | 46 recovered development prompt directives (M0.1 through M5-R) | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_ENGINEERING_PROMPT_ARCHIVE.md \| ID: ARCHIVE-MOB-PROMPT-001]` | [MOBILE_ENGINEERING_PROMPT_ARCHIVE.md](../../harmonia-release-build/docs/MOBILE_ENGINEERING_PROMPT_ARCHIVE.md) |
| **Mobile Problems & Resolutions** | Formal root-cause analyses and post-mortems for 16 incidents | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_PROBLEMS_AND_RESOLUTIONS.md \| ID: DOC-MOB-005]` | [MOBILE_PROBLEMS_AND_RESOLUTIONS.md](../../harmonia-release-build/docs/MOBILE_PROBLEMS_AND_RESOLUTIONS.md) |
| **Mobile Test & Qualification Matrix** | 386 JVM unit tests, 97 connected tests, and qualification gates | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_TEST_AND_QUALIFICATION_MATRIX.md \| ID: DOC-MOB-006]` | [MOBILE_TEST_AND_QUALIFICATION_MATRIX.md](../../harmonia-release-build/docs/MOBILE_TEST_AND_QUALIFICATION_MATRIX.md) |
| **Mobile Release Decisions** | 10 architectural and versioning decision records | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_RELEASE_DECISIONS.md \| ID: DOC-MOB-007]` | [MOBILE_RELEASE_DECISIONS.md](../../harmonia-release-build/docs/MOBILE_RELEASE_DECISIONS.md) |
| **Mobile Documentation Index** | Worktree-specific documentation index and link validation matrix | `[Worktree: harmonia-release-build \| Path: docs/MOBILE_DOCUMENTATION_INDEX.md \| ID: DOC-MOB-008]` | [MOBILE_DOCUMENTATION_INDEX.md](../../harmonia-release-build/docs/MOBILE_DOCUMENTATION_INDEX.md) |

---

## 3. Reconciled Canonical Milestone Accounting (166 Identifiers)

The ecosystem encompasses exactly **166 canonical unique identifiers**, partitioned across four non-overlapping functional domains:

$$egin{aligned}
	ext{Desktop-Specific Subphases} &= 30 	ext{ (Core Features)} + 6 	ext{ (Gates)} + 5 	ext{ (Personalization Parity)} = \mathbf{41} \
	ext{Shared / Platform-Spanning} &= 5 	ext{ (Brand)} + 10 	ext{ (Personalization Specs)} + 14 	ext{ (LocalSync Specs)} + 4 	ext{ (Knowledge Base)} = \mathbf{33} \
	ext{Mobile Development Subphases} &= 11 	ext{ (Foundation)} + 17 	ext{ (Storage)} + 7 	ext{ (Playback)} + 7 	ext{ (Personalization)} + 8 	ext{ (Obsidian UI)} = \mathbf{50} \
	ext{Mobile Release Sequence} &= 1 	ext{ (Config)} + 5 	ext{ (Minify)} + 3 	ext{ (Smoke)} + 1 	ext{ (Audit)} + 6 	ext{ (Qual)} + 2 	ext{ (Harden)} + 1 	ext{ (RC1)} \
&\quad + 9 	ext{ (Candidate)} + 1 	ext{ (Test APK)} + 13 	ext{ (Doc Closeout)} = \mathbf{42} \
\hline
\mathbf{Total\ Canonical\ Identifiers} &= 41 + 33 + 50 + 42 = \mathbf{166}
\end{aligned}$$

---

## 4. Cross-Platform Schema Parity Matrix

Harmonia maintains strict architectural parity between Desktop relational models and Mobile Room database structures:

| Domain Entity | Desktop SQLAlchemy / SQLite Table | Mobile Android Room v5 Entity | Parity Status |
|---|---|---|:---:|
| **Artist** | `artists` (UUID, name, sort_name, mbid) | `ArtistEntity` (id, name, sort_name) | `PARITY VERIFIED` |
| **Album** | `albums` (UUID, title, artist_id, year, art_path) | `AlbumEntity` (id, title, artist_id, year, art_uri) | `PARITY VERIFIED` |
| **Track** | `tracks` (UUID, title, artist_id, album_id, disc, number) | `TrackEntity` (id, title, artist_id, album_id, duration) | `PARITY VERIFIED` |
| **Audio File** | `track_files` (path, hash, format, bitrate, sample_rate) | Embedded in `TrackEntity` + `StorageSource` | `ARCHITECTURAL FIT` |
| **Signal Intelligence** | `audio_analyses` (LUFS, True Peak, clipping, silence) | `AudioAnalysisEntity` (M5-C audio session bridge) | `PARITY VERIFIED` |
| **Favorites** | `favorites` (entity_type, entity_id, created_at) | `FavoriteEntity` (entity_type, entity_id, added_at) | `PARITY VERIFIED` |
| **Ratings** | `ratings` (entity_type, entity_id, rating_value) | `RatingEntity` (entity_type, entity_id, rating_value) | `PARITY VERIFIED` |
| **Playlists** | `playlists`, `playlist_items` (position ordering) | `PlaylistEntity`, `PlaylistTrackCrossRef` | `PARITY VERIFIED` |
| **Listening Events** | `listening_events` (duration, completed, context) | `PlaybackEventEntity` (local playback history) | `PARITY VERIFIED` |

---

## 5. Mobile Release Artifact Integrity & Cryptographic Checksums

The release worktree holds the authoritative binary release candidate outputs and signed test packages:

| Artifact File | Size (Bytes) | SHA-256 Digest | Signature Scheme | Distribution Status |
|---|:---:|---|---|---|
| `harmonia-mobile-0.1.0-candidate.apk` | 2,948,156 | `61bb05b2f6cb1b601cce7008f1a69eb7a0ac200c2f5a6caabbbe9a99aabf7256` | None (Unsigned Candidate) | Locked in `RELEASE_MANIFEST_FINAL.txt` |
| `harmonia-mobile-0.1.0-test-signed.apk` | 2,974,755 | `c9355f6d16747232622cdd61bc2bb597a7b2286ef963abb7ff9720c34e7daa2c` | APK Signature Scheme v2/v3 (Debug `CN=Android Debug`) | Operator sideload test binary |
| `harmonia-mobile-0.1.0-test-signed.apk.idsig` | 30,083 | `d0498021fb9df392f90a3530bdcc1fa3cb5468874d81ee98918f253f3d9ddad0` | v4 signature index block | Fast ADB sideloading |

---

## 6. Epistemic Disclosure & Verification Boundaries

1. **Alembic Chain Integrity**: Verified declaratively from migration file ASTs on disk ($001 
ightarrow 006$). No live runtime migration command was executed.
2. **Personalization Implementation**: Fully implemented and tested across database layer, application layer, and REST API routes (`D-P2-D.1`).
3. **Hardware Boundaries**: Physical USB-OTG drive hot-unplugging, live acoustic retail TalkBack speech evaluation, pre-inception verbal brainstorming, and destructive whole-volume scans remain unperformed as disclosed.
