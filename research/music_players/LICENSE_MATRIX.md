# Harmonia Research: Comprehensive License Matrix & IP Analysis

**Document Classification:** Architectural Research Specification  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Legal evaluation of 20 media repositories, dependency licenses, copyleft contagion boundaries, and clean-room implementation guidelines.

---

## 1. Executive Licensing Directive for Harmonia

> **CRITICAL RULE:** Harmonia is envisioned as a long-term, independent, local-first personal music ecosystem. To preserve future deployment flexibility and avoid copyleft contagion, **no GPLv2 or GPLv3 source code may be copied directly into Harmonia's codebase.** All algorithms originating from GPL projects must be treated strictly as **Architectural Reference Sources (Tier C)** and independently implemented in a clean-room environment.

---

## 2. Master Repository License Audit

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                  REPOSITORY LICENSE MATRIX                                              │
├────────────────────┬────────────────────┬───────────┬─────────────────┬─────────────────────────────────┤
│ Repository         │ Top-Level License  │ Harmonia  │ Direct Source   │ Reuse Conditions & Obligations  │
│                    │                    │ Tier      │ Copy Possible?  │                                 │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ LocalTracklist     │ MIT License        │ Tier A    │ YES             │ Retain copyright notice and     │
│ (LibrePlayer)      │                    │ (Safe)    │                 │ permission disclaimer.          │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Flick              │ MIT License        │ Tier A    │ YES             │ Retain copyright notice.        │
│                    │                    │ (Safe)    │                 │ (Rust DSP code is clean MIT).   │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ beets              │ MIT License        │ Tier A    │ YES             │ Retain Adrian Sampson copyright;│
│                    │                    │ (Safe)    │                 │ distance algorithms reusable.   │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ MusicHoarder       │ MIT License        │ Tier A    │ YES             │ Retain Jeffrey van den Brink    │
│                    │                    │ (Safe)    │                 │ copyright notice.               │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Phoebe             │ MIT License        │ Tier A    │ YES             │ Retain j-roskopf copyright;     │
│                    │                    │ (Safe)    │                 │ SQLDelight / KMP patterns.      │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Wavio              │ MIT License        │ Tier A    │ YES             │ Retain Joel Mercier copyright;  │
│                    │                    │ (Safe)    │                 │ Pinyin / Kana search utilities. │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Resonate           │ MIT License        │ Tier A    │ YES             │ Retain Kimhab copyright;        │
│                    │                    │ (Safe)    │                 │ ChromaClamp & LibraryReplacer.  │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ decent-player      │ MIT License        │ Tier A    │ YES             │ Retain Marcelo Silva copyright; │
│                    │                    │ (Safe)    │                 │ Native C++ driver is MIT.       │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ LocalSend          │ Apache 2.0         │ Tier A    │ YES             │ Retain NOTICE and Apache 2.0    │
│                    │                    │ (Safe)    │                 │ headers; state code changes.    │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Aurora             │ Apache 2.0         │ Tier A    │ YES             │ Retain Apache 2.0 headers;      │
│                    │                    │ (Safe)    │                 │ MixAnalyzer & stem patterns.    │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Finamp             │ MPL 2.0 (Mozilla)  │ Tier B    │ CONDITIONAL     │ File-level copyleft: changes to │
│                    │                    │ (Oblig.)  │ (File-level)    │ original files must stay MPL.   │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Chromaprint        │ MIT Core /         │ Tier B    │ CONDITIONAL     │ Core is MIT; if compiled with   │
│                    │ LGPL v2.1+         │ (Library) │ (Use as dynamic │ avfft, LGPL applies dynamically.│
│                    │                    │           │ binary library) │ CLI `fpcalc` is clean boundary. │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Auxio              │ GNU GPLv3          │ Tier C    │ NO              │ Reference only. Copying any code│
│                    │                    │ (Ref Only)│                 │ forces Harmonia to become GPLv3.│
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Booming Music      │ GNU GPLv3          │ Tier C    │ NO              │ Reference only. AutoEQ and      │
│                    │                    │ (Ref Only)│                 │ lyrics concepts must be rebuilt.│
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ fooyin             │ GNU GPLv3          │ Tier C    │ NO              │ Reference only. Database schema │
│                    │                    │ (Ref Only)│                 │ and WASAPI logic reference only.│
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Strawberry         │ GNU GPLv3          │ Tier C    │ NO              │ Reference only. Compilation and │
│                    │                    │ (Ref Only)│                 │ unavailable logic ref only.     │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Tauon Music Box    │ GNU GPLv3          │ Tier C    │ NO              │ Reference only.                 │
│                    │                    │ (Ref Only)│                 │                                 │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Odyssey            │ GNU GPLv3          │ Tier C    │ NO              │ Reference only.                 │
│                    │                    │ (Ref Only)│                 │                                 │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ MusicBrainz Picard │ GNU GPLv2          │ Tier C    │ NO              │ Reference only. MBID clustering │
│                    │                    │ (Ref Only)│                 │ must be independently written.  │
├────────────────────┼────────────────────┼───────────┼─────────────────┼─────────────────────────────────┤
│ Isai               │ Custom Disclaimer  │ Tier D    │ NO              │ Proprietary / restrictive       │
│                    │ (GPL/Proprietary)  │ (Avoid)   │                 │ disclaimer in README/license.   │
└────────────────────┴────────────────────┴───────────┴─────────────────┴─────────────────────────────────┘
```

---

## 3. Tier Classifications & Governance Rules

### Tier A: Safe for Direct Code Reuse (Permissive: MIT, Apache 2.0)
- **Projects:** LocalTracklist, Flick, beets, MusicHoarder, Phoebe, Wavio, Resonate, decent-player, LocalSend, Aurora.
- **Rules:**
  1. Algorithms, utility functions, and data models can be adapted or translated into Harmonia with proper author attribution.
  2. Maintain a top-level `THIRD_PARTY_NOTICES.md` file in Harmonia acknowledging original authors.
  3. Comply with Apache 2.0 Section 4 requirements (include notice, state changes made).

### Tier B: Reusable with File-Level Obligations (Weak Copyleft: MPL 2.0, LGPL 2.1+)
- **Projects:** Finamp (MPL 2.0), Chromaprint (LGPL 2.1+ when using FFmpeg FFT).
- **Rules:**
  1. Any modification to an individual MPL-2.0 file must be released under MPL 2.0.
  2. Larger work (Harmonia) remains under Harmonia's choice of permissive license.
  3. Chromaprint should be consumed as a standalone external shared library (`libchromaprint.so` / `chromaprint.dll`) or invoked via the `fpcalc` CLI subprocess, preserving a clean boundary.

### Tier C: Architectural Reference Only (Strong Copyleft: GPLv2, GPLv3)
- **Projects:** Auxio, Booming Music, fooyin, Strawberry, Tauon, Odyssey, Picard.
- **Rules:**
  1. **DO NOT COPY CODE.** Copying even small code chunks (e.g. `BetterShuffleOrder.kt` or `ReplayGainAudioProcessor.kt`) creates legal copyleft contagion that could compel Harmonia to open-source all components under GPLv3.
  2. **Clean-Room Independent Implementation:** Understand the mathematical algorithm or design concept from the research documentation, close the GPL file, and write Harmonia's implementation from first principles using Harmonia's Clean Architecture conventions.

### Tier D & E: Restricted / Proprietary / Avoid
- **Project:** Isai.
- **Rules:** Contains restrictive legal disclaimers; treat solely as conceptual functional inspiration, zero code usage.

---

## 4. Key Dependency License Checks in Harmonia Core

1. **Mutagen (GPLv2+):** Harmonia Core currently imports `mutagen` for embedded tag reading (`src/harmonia/infrastructure/metadata/mutagen_reader.py`).
   - *Risk Analysis:* Because Harmonia is a local personal software tool (not a distributed proprietary SaaS or closed binary SDK), GPLv2 on Mutagen is standard practice in Python media tools (used by beets and Picard).
   - *Harmonia Decision:* Acceptable for Harmonia Core. If Harmonia Mobile is written in Kotlin/Java, it must use permissive alternatives (Media3 tag extractors / TagLib under LGPL) rather than Mutagen to keep mobile build clean.
2. **FFmpeg / FFprobe (LGPL v2.1+ / GPL v2+):** Harmonia Core invokes `ffmpeg` and `ffprobe` exclusively as external subprocess CLI commands (`subprocess.run(["ffprobe", ...])`).
   - *Legal Status:* Executing FFmpeg as a separate operating system process creates an unmistakable execution boundary; copyleft does not extend to the calling Python application.
