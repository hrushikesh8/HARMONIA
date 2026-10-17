# Harmonia Architecture Validation: Loudness & ReplayGain Pipeline

**Document Classification:** Pre-Implementation Freeze — Finalized Loudness Architecture  
**Status:** FROZEN & APPROVED  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Governing Rule:** *Do NOT conflate audio analysis, playback policy, and DSP. True Peak headroom clamping is an essential safety guard, but it is not the complete ReplayGain implementation. Clearly separate Analysis, Playback Policy, and future DSP.*

---

## 1. The Three-Tier Loudness Architecture

```
┌────────────────────────────────────────────────────────────────────────┐
│                        THREE-TIER LOUDNESS MODEL                       │
├────────────────────┬───────────────────────────────────────────────────┤
│ 1. ANALYSIS TIER   │ Executed Offline during Import (Phase 3 Core)     │
│    (Factual State) │ • FFmpeg/EBUR128 subprocess calculates:           │
│                    │   - integrated_lufs (LUFS)                        │
│                    │   - true_peak_dbtp (dBTP, 4x oversampled)         │
│                    │   - loudness_range_lu (LU)                        │
│                    │   - silence_start_sec / silence_end_sec           │
│                    │ • Stored immutably in AudioAnalysis database.     │
├────────────────────┼───────────────────────────────────────────────────┤
│ 2. PLAYBACK POLICY │ Executed by Application Playback Service          │
│    TIER            │ • User-configurable target: L_target (-14.0 LUFS).│
│    (Gain Decision) │ • Mode selection: Album Gain vs. Track Gain vs.   │
│                    │   Dynamic Mode (Album for sequential, Track for   │
│                    │   shuffle/playlist).                              │
│                    │ • Preamp offset application (e.g. +0.0 dB).       │
│                    │ • Computes: Target Gain Offset (dB).              │
│                    │ • Applies Peak Headroom Safety Guard.             │
│                    │ • Emits scalar linear multiplier to player backend│
├────────────────────┼───────────────────────────────────────────────────┤
│ 3. DSP TIER        │ Executed by Audio Backend / Adapter (Phase 6)     │
│    (Signal Render) │ • Applies scalar amplitude multiplier to PCM.     │
│                    │ • Optional Phase 6: Parametric AutoEQ biquads.    │
│                    │ • Optional Phase 6: FIR crossfeed convolver.      │
│                    │ • Optional Phase 6: Lookahead brickwall limiter.  │
└────────────────────┴───────────────────────────────────────────────────┘
```

---

## 2. Playback Policy Gain Calculation

At playback time, the player receives the objective measurements from `AudioAnalysis` and calculates the linear multiplier $A$:

### Step 1: Base Loudness Offset Calculation
Let $L_{\text{target}} = -14.0 \text{ LUFS}$ (default streaming target, user-configurable between $-14.0$ and $-18.0$ LUFS).  
Let $P = \text{Preamp Gain (dB)}$ (user setting, default $0.0 \text{ dB}$).  
Let $L_{\text{measured}} = \text{integrated\_lufs}$.

$$\Delta G_{\text{base}} = L_{\text{target}} - L_{\text{measured}} + P$$

### Step 2: Unanalyzed Fallback
If the track has not yet completed audio analysis:
$$\Delta G_{\text{base}} = P_{\text{fallback}} \quad (\text{default } -3.0 \text{ dB to prevent loud blasting})$$

### Step 3: Peak Headroom Safety Guard (Preventing Inter-Sample Clipping)
When $\Delta G_{\text{base}} > 0$ dB (boosting a quiet recording), high audio peaks could exceed digital full-scale (0 dBTP), creating harsh digital clipping distortion.

Let $TP = \text{true\_peak\_dbtp}$.  
Let $M_{\text{margin}} = 0.5 \text{ dB}$ (safety headroom below 0 dBTP).  
Let $G_{\text{max\_safe}} = -TP - M_{\text{margin}}$.

$$\Delta G_{\text{effective}} = \begin{cases} 
\min(\Delta G_{\text{base}}, G_{\text{max\_safe}}) & \text{if Peak Headroom Guard is enabled (default ON)} \\
\Delta G_{\text{base}} & \text{if Peak Headroom Guard is disabled}
\end{cases}$$

### Step 4: Conversion to Linear Amplitude
$$A = 10^{\frac{\Delta G_{\text{effective}}}{20}}$$

The scalar float multiplier $A$ is passed to the audio output backend.

---

## 3. Important Architectural Distinction: Safety Clamp vs. Full DSP Limiter

- **What True Peak Headroom Clamping Does:** It attenuates the entire track's volume slightly so that the highest measured peak never exceeds $-0.5$ dBTP. It is mathematically deterministic, runs in $O(1)$ time, and introduces **zero latency**.
- **What a Real-Time Brickwall Limiter Does (Phase 6 DSP):** A dynamic limiter selectively compresses only the transient peaks that exceed a threshold while leaving the rest of the song at full volume. It requires a lookahead ring buffer (5-10ms latency) and non-linear gain reduction algorithms.
- **Architectural Decision:** In Phase 4 and 5, **True Peak Headroom Clamping** provides safe, distortion-free playback with zero latency. Advanced dynamic limiters and convolution DSP are classified strictly as **Phase 6 Audiophile Architecture**.
