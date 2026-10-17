# Harmonia Architecture Validation: Clean-Room Licensing Policy

**Document Classification:** Pre-Implementation Freeze — Engineering IP Policy  
**Status:** FROZEN & APPROVED  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Rule:** *This document establishes an engineering policy for Harmonia contributors and AI agents, not formal legal advice. Harmonia enforces strict clean-room implementation standards to maintain long-term architectural autonomy.*

---

## 1. Harmonia Clean-Room Implementation Policy

Harmonia's core codebase must remain independent, maintainable, and unencumbered by copyleft obligations. To achieve this, all code contributions must comply with a four-tier classification:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        ENGINEERING REUSE TIERS                         │
├────────────────────┬───────────────────────────────────────────────────┤
│ TIER A: DIRECT     │ Permissive Licenses (MIT, Apache 2.0, BSD-3)      │
│ REUSE CANDIDATES   │ • Permitted after auditing internal dependencies. │
│                    │ • Preserves original copyright notices.           │
│                    │ • Maintained in THIRD_PARTY_NOTICES.md.           │
├────────────────────┼───────────────────────────────────────────────────┤
│ TIER B: REUSE WITH │ Weak Copyleft (MPL 2.0, LGPL 2.1+)                │
│ EXPLICIT           │ • File-level copyleft or shared dynamic library.  │
│ OBLIGATIONS        │ • Changes to original files must remain under     │
│                    │   license; larger Harmonia work remains clean.    │
│                    │ • Subprocess boundaries (e.g. fpcalc) preferred.  │
├────────────────────┼───────────────────────────────────────────────────┤
│ TIER C: REFERENCE  │ Strong Copyleft (GPLv2, GPLv3, AGPL)              │
│ ONLY (CLEAN-ROOM)  │ • ABSOLUTELY NO CODE COPYING.                     │
│                    │ • Permitted strictly for studying architecture,   │
│                    │   mathematical equations, and system behavior.    │
│                    │ • Code must be written from first principles.     │
├────────────────────┼───────────────────────────────────────────────────┤
│ TIER D: FEATURE    │ Proprietary / Restricted / Ambiguous Licenses     │
│ INSPIRATION ONLY   │ • Conceptual feature inspiration only.            │
│                    │ • Zero code copying or binary bundling.           │
│                    │ • If licensing is ambiguous: NEEDS LEGAL REVIEW.  │
└────────────────────┴───────────────────────────────────────────────────┘
```

---

## 2. Project Classification Matrix

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                       AUDITED SPECIMEN MATRIX                                           │
├────────────────────┬────────────────────┬────────────────────┬──────────────────┬───────────────────────┤
│ Repository         │ Top-Level License  │ Audited Submodule/ │ Harmonia Policy  │ Engineering Action &  │
│                    │ (Verified File)    │ Dependency Risk    │ Classification   │ Boundary Rules        │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ LocalTracklist     │ MIT License        │ AndroidX Media3    │ TIER A           │ Direct adaptation with│
│ (LibrePlayer)      │ (`LICENSE`)        │ (Apache 2.0)       │ (Direct Reuse)   │ notice preservation.  │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Flick              │ MIT License        │ Symphonia (MPL 2), │ TIER A / TIER B  │ Core is MIT; avoid    │
│                    │ (`LICENSE`)        │ Rubato (MIT)       │ (Clean Room DSP) │ copying Symphonia.    │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ decent-player      │ MIT License        │ libFLAC (BSD-3),   │ TIER A           │ C++ driver is MIT;    │
│                    │ (`LICENSE`)        │ Media3 (Apache 2)  │ (Clean Room)     │ clean room rewrite.   │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ beets              │ MIT License        │ Mutagen (GPLv2+)   │ TIER A           │ `distance.py` math    │
│                    │ (`LICENSE`)        │                    │ (Algorithm Adapt)│ adapted independently.│
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ MusicHoarder       │ MIT License        │ TagLib# (LGPL 2.1) │ TIER A           │ Matching algorithms   │
│                    │ (`LICENSE`)        │                    │ (Concept Adapt)  │ adapted into Python.  │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Resonate           │ MIT License        │ Media3 (Apache 2)  │ TIER A           │ Clean MIT code;       │
│                    │ (`LICENSE`)        │                    │ (Direct Reuse)   │ retain notice.        │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ LocalSend          │ Apache 2.0         │ Rust boringtun     │ TIER A           │ Protocol architecture │
│                    │ (`LICENSE`)        │                    │ (Protocol Adapt) │ adapted cleanly.      │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Chromaprint        │ MIT Core /         │ FFmpeg avfft       │ TIER B           │ SUBPROCESS BOUNDARY:  │
│                    │ LGPL 2.1+          │ (LGPL / GPL)       │ (Dynamic Subproc)│ Invoke `fpcalc` CLI.  │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Finamp             │ MPL 2.0            │ Isar, Hive CE      │ TIER B           │ Study state machine;  │
│                    │ (`LICENSE`)        │                    │ (Reference Only) │ do not copy files.    │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Auxio              │ GNU GPLv3          │ AndroidX, Musikr   │ TIER C           │ Clean-room independent│
│                    │ (`LICENSE`)        │                    │ (Clean-Room Ref) │ implementation only.  │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Booming Music      │ GNU GPLv3          │ AutoEQ (CC0)       │ TIER C           │ AutoEQ dataset is CC0;│
│                    │ (`LICENSE.txt`)    │                    │ (Clean-Room Ref) │ code is clean-room.   │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ fooyin             │ GNU GPLv3          │ Qt6, TagLib (LGPL) │ TIER C           │ Study DB schema;      │
│                    │ (`COPYING`)        │                    │ (Clean-Room Ref) │ do not copy C++ code. │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Strawberry         │ GNU GPLv3          │ Qt6, GStreamer     │ TIER C           │ Schema study only.    │
│                    │ (`COPYING`)        │                    │ (Clean-Room Ref) │                       │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Tauon Music Box    │ GNU GPLv3          │ BASS (Proprietary!)│ TIER C / TIER D  │ BASS library rejected;│
│                    │ (`LICENSE`)        │                    │ (Clean-Room Ref) │ do not use BASS.      │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ MusicBrainz Picard │ GNU GPLv2          │ PyQt6, Mutagen     │ TIER C           │ Matching concepts     │
│                    │ (`COPYING`)        │                    │ (Clean-Room Ref) │ clean-room written.   │
├────────────────────┼────────────────────┼────────────────────┼──────────────────┼───────────────────────┤
│ Isai               │ Custom Disclaimer  │ audio_service      │ TIER D           │ Legal disclaimer:     │
│                    │ (`LICENSE`)        │                    │ (NEEDS REVIEW)   │ Treat as untrusted.   │
└────────────────────┴────────────────────┴────────────────────┴──────────────────┴───────────────────────┘
```

---

## 3. Clean-Room Implementation Protocol for Engineers and AI

For all Tier C repositories (Auxio, Booming Music, fooyin, Strawberry, Picard):
1. **Abstract Functional Specification:** The design architect specifies the algorithm in pseudo-code, mathematical equations, or UML state machines.
2. **Clean-Room Implementation:** The implementing engineer writes the code directly into Harmonia's Clean Architecture conventions without viewing, pasting, or importing the GPL source file.
3. **No Direct Copy-Paste:** Direct copy-pasting of functions, class declarations, or variable blocks from Tier C/D repositories is strictly prohibited.
