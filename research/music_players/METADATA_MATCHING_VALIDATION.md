# Harmonia Architecture Validation: Metadata Matching & Consensus

**Document Classification:** Validation Pass 1 — Intelligence & Catalog Validation  
**Status:** Validated & Formally Specified  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Rule:** *Never blindly trust two external providers. A sound matching architecture requires multi-signal scoring, duration sanity guards, version qualification, and an explicit triage review queue.*

---

## 1. Why the Naive Rule (">=2 Providers Agree") Fails

In the exploratory research, MusicHoarder's consensus concept was summarized as:
> *"When 2 or more providers agree, automatically apply the match."*

### The Fatal Flaws in Naive Consensus:
1. **Collusive Errors (Lack of True Independence):** Spotify, Apple Music, and Deezer frequently ingest identical automated catalog deliveries from aggregators (DistroKid, TuneCore). If a label made a typo or inverted artists on an album delivery, three commercial streaming APIs will agree 100% on the error!
2. **The "Radio Edit" vs. "Album Mix" Trap:** A commercial API might return *"Bohemian Rhapsody"* matching the title and artist, but representing a 3-minute radio edit, whereas the user's file is the 5-minute 55-second album master. Agreeing on title + artist without a duration guard corrupts the library.
3. **The Version Qualifier Trap ([MusicHoarder `VersionQualifier.cs`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/MusicHoarder-main/MusicHoarder.Api/Matching/VersionQualifier.cs)) `[SOURCE VERIFIED]`:** A file named `Hotel California.flac` could be matched by two providers as `Hotel California (Live at the Forum 1976)` or `Hotel California (Acoustic 1994)`. Treating them as the same song is catastrophic for an audiophile collection.
4. **Overwriting Curated Metadata:** If the user manually tagged a track with special Japanese transliterations or classical movement numbers, an automated consensus match must NEVER overwrite user-locked fields.

---

## 2. Multi-Signal Matching Architecture

Instead of binary agreement, Harmonia defines a **Multi-Signal Confidence Score** ($S \in [0.0, 1.0]$) composed of six deterministic verification layers:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   HARMONIA MULTI-SIGNAL MATCHING STACK                 │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
 [LAYER 1: ACOUSTIC FINGERPRINT] (Chromaprint / AcoustID)
 • Matches actual audio waveform independent of file name or tags.
 • Confirms raw audio identity ($S_{\text{acoust}} \in [0.0, 1.0]$ based on AcoustID sources).
                                   │
                                   ▼
 [LAYER 2: DURATION SANITY GUARD] (Hard Filter)
 • If $|\Delta t| = |\text{file\_duration} - \text{candidate\_duration}| > 4.0\text{ seconds}$:
   -> Immediate penalty: $S_{\text{dur\_penalty}} = -0.5$.
 • If $|\Delta t| > 15.0\text{ seconds}$:
   -> Hard block: Candidate REJECTED (different edit or mastering).
                                   │
                                   ▼
 [LAYER 3: VERSION QUALIFIER GUARD] (Hard Filter)
 • Checks for mismatched version tokens: [Live, Acoustic, Remix, Instrumental, Radio Edit].
 • If file lacks "Live" but candidate has "Live": Candidate REJECTED.
                                   │
                                   ▼
 [LAYER 4: ISRC EXACT CORROBORATION] (Cryptographic Match)
 • If file and candidate share identical 12-character ISRC:
   -> Massive confidence boost: $+0.30$.
                                   │
                                   ▼
 [LAYER 5: MUSICAL DISTANCE STRING SCORING] (beets distance.py)
 • Weighted string distance across Title, Artist, Album, Disc/Track number.
 • Discounts parentheticals and articles.
                                   │
                                   ▼
 [LAYER 6: WHOLE-ALBUM RECONCILIATION]
 • For multi-track folders, cross-checks candidate release against neighboring tracks.
 • Rewards album consistency ($+0.15$ if all tracks belong to same MusicBrainz Release).
```

---

## 3. Four-Tier Triage Classification

Based on the final composite score $S_{\text{total}}$, the match is routed to one of four lifecycle states:

```
┌────────────────────────────────────────────────────────────────────────┐
│                       MATCHING TRIAGE LIFECYCLE                        │
├────────────────────┬──────────────┬────────────────────────────────────┤
│ Tier               │ Score Range  │ System Action                      │
├────────────────────┼──────────────┼────────────────────────────────────┤
│ 1. AUTO-APPLY      │ $S \ge 0.95$ │ • AcoustID match confirmed.        │
│    (High Conf.)    │              │ • Duration matches ($|\Delta t| < 2s$). │
│                    │              │ • No version qualifier conflicts.  │
│                    │              │ • Automatically applied to Track.  │
├────────────────────┼──────────────┼────────────────────────────────────┤
│ 2. PROPOSED        │ $0.75 \le S  │ • Placed in "Inbox: Tag Review".   │
│    CANDIDATE       │ < 0.95$      │ • Shows field-by-field diff.       │
│    (Inbox Review)  │              │ • User can approve with one click. │
├────────────────────┼──────────────┼────────────────────────────────────┤
│ 3. CONFLICTING     │ Competing    │ • Multiple confident candidates    │
│    MATCHES         │ candidates   │   with different release years.    │
│    (Inbox Review)  │ with $S>0.70$│ • User explicitly selects version. │
├────────────────────┼──────────────┼────────────────────────────────────┤
│ 4. REJECTED /      │ $S < 0.70$   │ • Discarded. Track remains tagged  │
│    UNIDENTIFIED    │              │   with file/path fallback.         │
└────────────────────┴──────────────┴────────────────────────────────────┘
```

---

## 4. Interaction with Field-Level Provenance & User Locks

Harmonia's existing Phase 2 architecture enforces **Field-Level Provenance** (`MetadataFieldRecord`):
- `USER` priority ($P = 100$)
- `PROVIDER` priority ($P = 50$)
- `FILE` priority ($P = 20$)
- `PATH` priority ($P = 10$)

### Safety Invariant:
Even if a match achieves $S_{\text{total}} = 1.00$ (Auto-Apply), the resolution engine **never overwrites any field marked with `USER` priority**. If the user explicitly edited the artist name to *"The Beatles (UK Mono)"*, the provider match can update `mbid`, `year`, and `label`, but the `artist` field remains locked and untouched.

---

## 5. Validation Classification

| Dimension | Previous Finding | Validation Pass 1 Verdict | Action |
| :--- | :--- | :--- | :--- |
| **>=2 Providers Consensus** | Auto-apply on 2 providers | **INCORRECT** | Replace with Multi-Signal Score + Duration & Version Guards. |
| **Duration Check** | Passive comparison | **CONFIRMED** | Enforce hard rejection if $|\Delta t| > 15$ seconds. |
| **Version Qualifiers** | Ignored | **CONFIRMED WITH MODIFICATION** | Port MusicHoarder's `VersionQualifier` token checks. |
| **Review Inbox** | Nice to have | **CONFIRMED** | Mandatory UI console for reviewing matches with score $0.75-0.95$. |
| **User Lock Protection** | General concept | **CONFIRMED** | Enforce strict priority ($USER > PROVIDER$). |
