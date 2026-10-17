# Harmonia Architecture Validation: Master Decision Table

**Document Classification:** Pre-Implementation Freeze — Formal Architecture Decisions  
**Status:** FROZEN & APPROVED  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Objective:** Authoritative resolution of all candidate recommendations, establishing the binding architectural decisions for Harmonia.

---

## 1. Master Decision Table

| # | Architectural Decision Area | Previous Recommendation | Validation Finding | Final Decision | Target Phase |
|---|---|---|---|---|---|
| **1** | **Track vs. TrackFile Modeling** | Keep ADR-002 separation | **CONFIRMED**: Collapsing files into songs is a fatal flaw in other players. | **KEEP ADR-002**: Pure 1:Many logical Track to physical TrackFile model. | Existing |
| **2** | **Disconnected Storage Lifecycle** | Add simple `is_available` boolean | **CONFIRMED WITH MODIFICATION**: Simple boolean is too coarse for unmounted drives vs deleted files. | **ADOPT STATE MACHINE**: `STORAGE_UNMOUNTED`, `FILE_MISSING`, `FILE_PRESENT`, `FILE_READ_ERROR`. | Phase 5 |
| **3** | **History / ListeningEvent Identity** | Key stats on content hash (fooyin) | **INCORRECT**: Hash-keying breaks multi-format co-existence and transcode history. | **ADOPT DUAL-POINTER**: Bind `ListeningEvent` to `Track.id` (logical) + `TrackFile.id` (physical). `device_id` is nullable in Phase 4. UUIDv4 identity. | Phase 4 |
| **4** | **Metadata Consensus Rule** | Auto-apply if $\ge 2$ providers agree | **INCORRECT**: Providers collude on label errors; duration/version differences trap users. | **ADOPT MULTI-SIGNAL SCORING**: Duration guard ($|\Delta t| < 4s$) + version qualifier guard + triage review Inbox. | Phase 5 |
| **5** | **Non-Destructive Tag Policy** | SQLite virtual tags + opt-in write | **CONFIRMED**: Preserves archival integrity; avoids stream corruption. | **KEEP & EXTEND**: Virtual SQLite tags by default; explicit user opt-in command for file write-back. | Phase 5 |
| **6** | **Atomic Library Rescan Swap** | Adapt Resonate table swap | **CONFIRMED WITH MODIFICATION**: Raw table rename drops SQLite FTS5 triggers and foreign keys. | **ADOPT TRANSACTIONAL RECONCILIATION**: In-place set difference update inside a single WAL transaction. | Phase 4 |
| **7** | **Queue & Shuffle Architecture** | Auxio BetterShuffleOrder in Core | **CONFIRMED WITH MODIFICATION**: Separate deterministic queue from shuffle intelligence. | **ADOPT DUAL QUEUE**: FIFO Up Next + Shuffled Context; Auxio contiguous insertion; AI remains outside. | Phase 4 |
| **8** | **Device Synchronization** | LocalSend P2P delta sync | **CONFIRMED WITH MODIFICATION**: LocalSend is for raw files; Harmonia requires structured catalog sync. | **ADOPT LOCALSYNC PROTOCOL**: mDNS discovery + TLS handshake + delta catalog JSON + chunked audio stream. | Phase 5 |
| **9** | **Mobile Persistence Engine** | Android Room | **CONFIRMED**: Room provides native Paging 3, compile-time verification, and official KMP future. | **ADOPT ROOM (SQLITE WAL)**: Official Android Jetpack persistence standard. | Phase 5 |
| **10** | **Audio Engine Structure** | Single universal C++ audio engine | **INCORRECT**: Monolithic C++ engine creates massive NDK/JNI overhead and battery drain. | **DECOUPLE CONTRACT & SOURCES**: `PlaybackPort` domain protocol; `PlaybackSource` (LocalFile, StreamUri, PlatformUri); native Media3 on Android; HTML5/native on desktop. | Phase 4 & 5 |
| **11** | **ReplayGain Loudness Application** | Runtime lookahead limiter | **CONFIRMED WITH MODIFICATION**: Lookahead limiter introduces unnecessary latency and pumping. | **THREE-TIER LOUDNESS PIPELINE**: Analysis (Phase 3 Core) $\to$ Playback Policy (gain offset + True Peak headroom clamp) $\to$ downstream DSP (Phase 6). | Phase 4 |
| **12** | **Headphone AutoEQ Database** | Adopt immediately | **CONFIRMED WITH MODIFICATION**: Validated as CC0 data; belongs in audiophile tier. | **DEFER TO PHASE 6**: Bundle 4,000+ AutoEQ profiles into Phase 6 Audiophile Engine. | Phase 6 |
| **13** | **FIR Impulse Response Convolver** | 16k-tap FIR in Core | **CONFIRMED WITH MODIFICATION**: Validated as zero latency, but is an advanced feature. | **DEFER TO PHASE 6**: Implement FIR crossfeed/room correction in Phase 6. | Phase 6 |
| **14** | **Bit-Perfect Direct USB DAC Output** | Adopt across mobile | **CONFIRMED WITH MODIFICATION**: Essential for audiophiles, but complex driver work. | **DEFER TO PHASE 6**: WASAPI Exclusive on desktop and direct USB UAC2 driver on mobile in Phase 6. | Phase 6 |
| **15** | **Quantitative Performance Numbers** | Accept README benchmark claims | **INCORRECT**: 10x headroom was a weak DAP observation; <1.2% CPU was theoretical. | **REJECT AS UNVERIFIED**: Benchmark strictly on budget physical hardware baselines; label all internal metrics as HARMONIA ENGINEERING TARGET. | Ongoing |
| **16** | **Licensing & Copyleft Boundaries** | Permissive projects safe to copy | **CONFIRMED WITH MODIFICATION**: Bundled submodules and dependencies (TagLib#, Symphonia) have terms. | **HARMONIA CLEAN-ROOM POLICY**: Tiers A-D. Zero GPL copy-paste. Independent clean-room implementation. Audit sub-dependencies. | Ongoing |
| **17** | **Core vs. Harmonia AI Boundary** | Protect separation | **CONFIRMED**: Crucial for stability and privacy. | **IMMUTABLE BOUNDARY**: Core is factual deterministic music infrastructure; AI is subjective local client. | All Phases |
| **18** | **Desktop UI Architecture** | Audio engineering console | **CONFIRMED**: Embedded Web UI served at `/` and `/ui`. | **KEEP EXISTING EMBEDDED UI**: Real-time ITU-R BS.1770-4 meters, FTS5 search, HTML5 audio stream. | Existing |
| **19** | **Mobile Development Framework** | Kotlin + Compose + Media3 | **CONFIRMED**: Proven superior to Flutter or React Native for native audio resilience. | **ADOPT PURE ANDROID NATIVE**: Kotlin, Jetpack Compose, Media3 ExoPlayer, Room DB. | Phase 5 |
| **20** | **Acoustic Fingerprinting** | Chromaprint / AcoustID | **CONFIRMED**: Indispensable for identifying untagged collections. | **ADOPT CLI SUBPROCESS**: Invoke `fpcalc` binary via subprocess; zero C++ linking contamination. | Phase 5 |

---

## 2. Summary of Strategic Shifts in Validation Pass 1

1. **Rejection of Content-Hash History Keying:** We rejected keying listening events to content hashes (`TrackHash`), replacing it with a robust dual-pointer model (`track_id` + `track_file_id`) that preserves multi-format listening and transcode history. `device_id` is nullable in Phase 4.
2. **Rejection of Naive Consensus:** We rejected `2 providers agree -> auto apply`, replacing it with a multi-signal scoring pipeline that includes duration checks ($|\Delta t| < 4s$), version qualifier guards, and an Inbox triage review queue.
3. **Decoupling Playback Contract and Transport:** HTTP 206 is strictly an audio transport mechanism. The playback abstraction is `PlaybackPort` backed by polymorphic `PlaybackSource` implementations (LocalFile, StreamUri, PlatformUri).
4. **Three-Tier Loudness Pipeline:** Loudness analysis is separated from playback policy (gain offset with True Peak headroom clamp) and downstream DSP (Phase 6).
5. **Harmonia Clean-Room Implementation Policy:** Replaced vague "zero contagion" claims with an engineering reuse policy (Tiers A-D) enforcing zero copy-paste from copyleft repositories and strict third-party notice tracking.
6. **Scoping Phase 4 Realistically:** Pruned AutoEQ, FIR convolvers, mobile apps, and network sync from Phase 4, focusing Phase 4 strictly on a rock-solid desktop playback engine, deterministic queue, and factual listening telemetry.
