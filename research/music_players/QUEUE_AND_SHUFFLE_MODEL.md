# Harmonia Architecture Validation: Queue & Shuffle Mechanics

**Document Classification:** Validation Pass 1 — Playback & Queue Specification  
**Status:** Validated & Formally Specified  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Rule:** *Strictly decouple Queue Mechanics (Core deterministic playback) from Shuffle Intelligence and AI Recommendations. AI suggests; Core executes.*

---

## 1. Deconstructing the Queue Architecture

In standard open-source music players, "queue", "playlist", "shuffle", and "up next" are frequently collapsed into a single mutable array. This causes common user bugs:
- Clicking "Play Next" shuffles the track to the very end of the playlist.
- Toggling Shuffle ON and OFF wipes the user's manual queue additions.
- Turning Shuffle OFF scrambles the original album order permanently.

---

## 2. Harmonia's Validated Dual-Layer Queue Model

Harmonia implements a **Dual-Layer Deterministic Queue**:

```
┌────────────────────────────────────────────────────────────────────────┐
│                      HARMONIA DUAL-LAYER QUEUE                         │
├────────────────────────────────────────────────────────────────────────┤
│ CURRENTLY PLAYING: Track [ K ]                                         │
├────────────────────────────────────────────────────────────────────────┤
│ LAYER 1: EXPLICIT USER QUEUE ("Up Next")                               │
│ • Higher Priority. FIFO queue.                                         │
│ • Items explicitly added by user ("Play Next", "Add to Queue").        │
│ • NEVER shuffled. Always plays sequentially in exact user order.       │
│   Track [ U1 ] -> Track [ U2 ] -> Track [ U3 ]                        │
├────────────────────────────────────────────────────────────────────────┤
│ LAYER 2: BACKGROUND CONTEXT QUEUE ("Context Playback")                 │
│ • Lower Priority.                                                      │
│ • The ambient album, playlist, or library list initiated by user.      │
│ • Holds: [ Original Unshuffled Index List ] AND                        │
│          [ Active Shuffled Permutation Index List ]                    │
│   Track [ C1 ] -> Track [ C2 ] -> Track [ C3 ] -> Track [ C4 ] ...     │
└────────────────────────────────────────────────────────────────────────┘
```

### Deterministic Next-Track Resolution:
When Track $K$ finishes:
1. **Check Layer 1 (Up Next):** If Layer 1 is non-empty, pop and play the head item (`U1`).
2. **Fallback to Layer 2 (Context Queue):** If Layer 1 is empty:
   - If **Shuffle is OFF**: Advance to the next sequential track in Layer 2.
   - If **Shuffle is ON**: Advance to the next index in the active shuffled permutation.
   - If end of Layer 2 is reached: Check **Repeat Mode** (`REPEAT_ALL` loops back to start; `REPEAT_OFF` stops).

---

## 3. Auxio's "BetterShuffleOrder" Mechanics Verified

Inspected in [Auxio `BetterShuffleOrder.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Auxio-dev/app/src/main/java/org/oxycblt/auxio/playback/service/BetterShuffleOrder.kt#L68) `[SOURCE VERIFIED]`:

### The Flaw in Default ExoPlayer `DefaultShuffleOrder`:
When inserting a new track at position $P$ into an active shuffled queue of length $N$, `DefaultShuffleOrder.cloneAndInsert` generates a pseudo-random index for the new track across $[0, N]$. The newly queued track is randomly scattered far ahead or behind!

### Auxio's Algorithmic Fix:
1. Maintains an inverted index mapping `indexInShuffled` alongside the shuffled array.
2. Identifies the `pivot` (the position of the currently playing track in the shuffled array).
3. Inserts newly queued tracks **contiguously immediately after the pivot** (`pivot + 1`), shifting subsequent shuffled elements forward.
4. When Shuffle is toggled OFF, tracks remain in their original natural order.

---

## 4. Separation of Queue Intelligence Tiers

Harmonia strictly separates the mechanics of playing tracks from the intelligence that chooses them:

```
┌────────────────────────────────────────────────────────────────────────┐
│                        QUEUE INTELLIGENCE TIERS                        │
├────────────────────┬───────────────────────────────────────────────────┤
│ TIER 1: CORE       │ Deterministic Playback Engine                     │
│ QUEUE MECHANICS    │ • Pure FIFO Up Next queue.                        │
│ (Phase 4)          │ • Standard Fisher-Yates uniform shuffle.          │
│                    │ • Contiguous "Play Next" insertion (Auxio logic). │
│                    │ • Persistent SQLite snapshot across app restarts. │
├────────────────────┼───────────────────────────────────────────────────┤
│ TIER 2: CORE       │ Deterministic Smart Shuffle Algorithm             │
│ SMART SHUFFLE      │ • Artist Spacing: Guarantees at least 3 tracks    │
│ (Phase 5)          │   between songs by the same artist.               │
│                    │ • Album Separation: Prevents consecutive tracks   │
│                    │   from the same album in large playlists.         │
│                    │ • Pure deterministic math; zero AI dependencies.  │
├────────────────────┼───────────────────────────────────────────────────┤
│ TIER 3: HARMONIA   │ Contextual & Behavioral Recommendation Queue      │
│ AI QUEUE           │ • Harmonic Mixing: BPM & musical key transitions. │
│ (Phase 7)          │ • Taste Profiling: Weights tracks by mood, time   │
│                    │   of day, activity, and skip history.             │
│                    │ • AI communicates exclusively via REST API.       │
└────────────────────┴───────────────────────────────────────────────────┘
```

---

## 5. Architectural Invariant: The AI Boundary

Harmonia AI **never** hooks directly into the real-time audio playback callback or ExoPlayer's internal queue thread. 

When the user requests an AI-generated session (e.g. *"Play a late-night focus flow"*):
1. Harmonia AI queries Harmonia Core's API (`GET /api/v1/tracks?genre=ambient...`).
2. Harmonia AI computes semantic rankings on its local model (`harmonia_ai.db`).
3. Harmonia AI posts an ordered list of `track_id` UUIDs to Harmonia Core (`POST /api/v1/queue/set`).
4. Harmonia Core deterministically queues the tracks and executes playback.

If Harmonia AI is offline, disabled, or uninstalled, **100% of Harmonia's playback, queue, and shuffle capabilities operate flawlessly without interruption.**
