# Harmonia Research: Metadata Ecosystem Comparison

**Document Classification:** Architectural Research Specification  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Container tag specifications, normalization pipelines, multi-provider consensus, and provenance architectures across 20 media projects.

---

## 1. Container & Tagging Standard Support Across Projects

```
┌─────────────────────────────────────────────────────────────────────────────────────────────┐
│                            CONTAINER & TAG ENGINE MATRIX                                    │
├──────────────┬──────────────────┬─────────────────┬─────────────────────────────────────────┤
│ Container    │ Standards Used   │ Parser Library  │ Exemplary Projects Inspected            │
├──────────────┼──────────────────┼─────────────────┼─────────────────────────────────────────┤
│ MP3          │ ID3v2.3, ID3v2.4 │ Mutagen, TagLib │ Picard, beets, fooyin, Auxio, Booming   │
│ FLAC         │ Vorbis Comments  │ Mutagen, TagLib │ fooyin, strawberry, beets, Picard       │
│ Ogg / Opus   │ Vorbis Comments  │ Mutagen, TagLib │ fooyin, beets, Picard, Tauon            │
│ MP4 / M4A    │ QuickTime Atoms  │ Mutagen, TagLib │ Booming, Auxio, beets, Picard           │
│ ALAC         │ M4A Atoms        │ Mutagen, TagLib │ Booming, fooyin, Picard                 │
│ APE          │ APEv2            │ TagLib, Mutagen │ fooyin, beets, Picard                   │
│ WAV / AIFF   │ RIFF INFO / ID3  │ Mutagen, TagLib │ Picard, fooyin, strawberry              │
│ CUE Sheets   │ CDRWIN CUE       │ Custom Parsers  │ strawberry, Tauon, fooyin               │
└──────────────┴──────────────────┴─────────────────┴─────────────────────────────────────────┘
```

---

## 2. Deep Dive: Critical Metadata Domains

### 2.1 Multi-Disc, Compilations & Album Artist

A major failure mode in music players is splitting a single album into multiple fragmented albums due to:
1. Multi-disc releases (`Disc 1`, `Disc 2`)
2. Guest / featured artists (`Track Artist: Queen feat. David Bowie`, `Album Artist: Queen`)
3. Compilation albums (`Various Artists`, soundtracks, tribute albums)

#### Exemplary Implementations:
1. **[Strawberry's Tri-State Compilation Model](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/strawberry-master/data/schema/schema-13.sql#L51-L55) `[SOURCE VERIFIED]`:**
   - Tracks 4 explicit database columns:
     - `compilation_detected`: Derived automatically if multiple track artists exist under the same album title, or if ID3 `TCMP=1` / Vorbis `COMPILATION=1` is present.
     - `compilation_on`: User manually forced album to be treated as a compilation.
     - `compilation_off`: User manually forced album NOT to be a compilation (e.g. an album where the main artist collaborates on every track).
     - `compilation_effective`: Computed Boolean resolution: `(compilation_on OR (compilation_detected AND NOT compilation_off))`.
   - Uses `effective_albumartist`: If `compilation_effective` is true, defaults album artist to `"Various Artists"` if empty.
2. **[Picard's Release Group & Disc Indexing](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/picard-master/picard/metadata.py) `[SOURCE VERIFIED]`:**
   - Explicitly indexes `discnumber`, `totaldiscs`, `tracknumber`, `totaltracks`.
   - Links recordings to MusicBrainz Release Groups (`musicbrainz_releasegroupid`) to ensure deluxe editions, vinyl rips, and multi-disc box sets resolve to the same canonical conceptual album.
3. **[Auxio's Separators Dialog](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Auxio-dev/app/src/main/java/org/oxycblt/auxio/music/interpret/SeparatorsDialog.kt) `[SOURCE VERIFIED]`:**
   - Solves multi-artist tagging where ID3 lacks multi-value support.
   - Allows user to specify custom delimiters (e.g. `;`, `/`, `feat.`, `ft.`, `&`, `,`) to split raw artist strings into distinct first-class artist entities in the UI.

---

### 2.2 Matching Algorithms & Distance Scoring

How do systems determine if an untagged or messy track matches a known release?

#### [beets `distance.py` Algorithm](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/beets-master/beets/autotag/distance.py) `[SOURCE VERIFIED]`
beets implements a weighted mathematical distance metric ($D \in [0.0, 1.0]$):

1. **String Normalization:**
   - Lowercases and transliterates to ASCII via `unidecode`.
   - Replaces `&` with `and`.
   - Normalizes articles: `"Beatles, The"` $\leftrightarrow$ `"The Beatles"`.
2. **Reduced Weights / Discounted Patterns:**
   - Leading `"The "` has a penalty weight of only `0.1`.
   - `"(ep)"` or `"(single)"` has a penalty weight of `0.0` (ignored).
   - `"(featuring ...)"` or `"feat. ..."` has a penalty weight of `0.1`.
   - General parentheticals `\(.*?\)` and brackets `\[.*?\]` have a penalty weight of `0.3`.
   - Subtitle markers `"(part ...)"` have a penalty weight of `0.2`.
3. **Multi-dimensional Distance Vector:**
   $$\text{Total Distance} = w_{\text{title}} D_{\text{title}} + w_{\text{artist}} D_{\text{artist}} + w_{\text{album}} D_{\text{album}} + w_{\text{track}} D_{\text{track}} + w_{\text{length}} D_{\text{length}}$$
   Where $D_{\text{length}}$ is calculated from track duration:
   $$D_{\text{length}} = \min\left(1.0, \frac{|\Delta t|}{15.0}\right)$$
   (A duration difference of $<2$ seconds incurs virtually zero penalty; $>15$ seconds incurs max penalty).

---

### 2.3 Multi-Provider Consensus & Triage Workflows

#### [MusicHoarder Consensus Engine](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/MusicHoarder-main/MusicHoarder.Api/Matching) `[SOURCE VERIFIED]`
Rather than blindly accepting the first API result, MusicHoarder queries up to six independent providers:
1. **AcoustID / Chromaprint** (acoustic fingerprint match)
2. **MusicBrainz API** (catalog metadata search)
3. **Spotify Web API** (commercial streaming metadata)
4. **Apple Music API** (commercial streaming metadata)
5. **Deezer API** (ISRC and commercial metadata)
6. **Community Trackers** (unreleased / live recordings)

#### The Consensus Decision Rules:
- **Rule 1 (Auto-Match / High Confidence):** If $\ge 2$ independent providers agree on the recording title, artist, and album, the match is marked `AutoResolved` (100% confidence) and applied automatically.
- **Rule 2 (Single-Hit Candidate):** If only one provider returns a hit (e.g. AcoustID only), the match is marked `Candidate` and queued for review.
- **Rule 3 (Conflicting Hits / Review):** If Provider A says *"Song X (Remastered)"* and Provider B says *"Song X (Live at Wembley)"*, the conflict is routed to the user's **Inbox**.
- **Rule 4 (Canonical Album Reconciliation):** In multi-track folders, if 9 tracks match Release ID 123 and 1 track matches Release ID 456, the whole-album reconciler heals the outlier to prevent album fracturing.

---

### 2.4 Tag Writing vs. App-Only Metadata & Provenance

```
┌────────────────────────────────────────────────────────────────────────┐
│                    TAG PERSISTENCE STRATEGIES                          │
├──────────────────┬─────────────────┬───────────────────────────────────┤
│ Strategy         │ Risk Profile    │ Projects Employing                │
├──────────────────┼─────────────────┼───────────────────────────────────┤
│ Destructive      │ HIGH            │ Picard (default), beets (import   │
│ In-Place Write   │ (Can corrupt    │ with -w), fooyin tag editor       │
│                  │ audio streams)  │                                   │
├──────────────────┼─────────────────┼───────────────────────────────────┤
│ Non-Destructive  │ ZERO FILE RISK  │ MusicHoarder (reads source,       │
│ Copy/Mirror      │ (Requires 2x    │ writes clean copy to destination) │
│                  │ disk space)     │                                   │
├──────────────────┼─────────────────┼───────────────────────────────────┤
│ App-Only SQLite  │ ZERO FILE RISK  │ Harmonia Core, Auxio,             │
│ Virtual Metadata │ (Tags lost if   │ LocalTracklist, Strawberry        │
│                  │ DB deleted)     │                                   │
└──────────────────┴─────────────────┴───────────────────────────────────┘
```

#### Harmonia's Provenance Architecture
Harmonia already implements **Field-Level Provenance** (`MetadataFieldRecord`):
- Every metadata property (`title`, `artist`, `year`, `genre`) records its origin:
  - `FILE`: Parsed from embedded file header tags.
  - `PATH`: Inferred from filesystem directory structure (`/Artist/Album/01 - Title.flac`).
  - `PROVIDER`: Fetched from MusicBrainz or AcoustID.
  - `USER`: Explicitly edited or locked by the user.
- **Priority Resolution:** $\text{USER} > \text{PROVIDER} > \text{FILE} > \text{PATH}$.
- **User Locking:** Any user modification sets a lock flag that prevents external provider rescans from ever overwriting the field.

---

## 3. Harmonia Metadata Engine Blueprint

Based on this research, Harmonia's metadata subsystem should integrate the following enhancements:

1. **Adopt beets's String Distance Metric (`distance.py`):** Replace naive Levenshtein with musical article/featuring/duration-weighted distance scoring.
2. **Implement MusicHoarder's Multi-Provider Consensus & Triage Inbox:** Combine MusicBrainz + AcoustID with secondary provider validation; auto-apply matches with $\ge 2$ consensus; place ambiguities in an `Inbox` table for review.
3. **Incorporate Strawberry's Compilation Logic:** Add `is_compilation_detected`, `is_compilation_user_override`, and `effective_album_artist` to `Album` and `Track` domain entities.
4. **Maintain Strict Non-Destructive Default:** Never write tags back to source audio files without explicit user command (`harmonia tag --write-back`). Maintain all factual provenance within SQLite.
