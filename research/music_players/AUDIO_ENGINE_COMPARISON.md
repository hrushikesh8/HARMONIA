# Harmonia Research: Audio Engine & Audiophile DSP Comparison

**Document Classification:** Architectural Research Specification  
**Status:** Complete  
**Date:** October 2026  
**Author:** Lead Research Architect for Harmonia  
**Scope:** Deep audiophile analysis covering PCM pipelines, ITU-R BS.1770-4 LUFS normalization, AutoEQ parametric databases, FIR convolution, bit-perfect USB DAC output, and signal path diagnostics.

---

## 1. Audiophile Capability Matrix Across Projects

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                AUDIOPHILE ENGINE FEATURE MATRIX                                         │
├──────────────┬────────────┬─────────────┬─────────────┬─────────────┬─────────────┬─────────────────────┤
│ Project      │ Internal   │ ReplayGain  │ Equalizer   │ AutoEQ      │ Convolution │ Bit-Perfect Output  │
│              │ Precision  │ / LUFS Mode │ Bands       │ Headphone DB│ FIR Engine  │ / Hardware Direct   │
├──────────────┼────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────────────┤
│ fooyin       │ 32-bit flt │ Track/Album │ 18-band PEQ │ Manual text │ No          │ WASAPI Exclusive /  │
│              │ (SoX res.) │ + Preamp    │ + SuperEQ   │ import      │             │ ALSA direct (hw:X,Y)│
├──────────────┼────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────────────┤
│ Flick        │ 32-bit flt │ In design   │ 10-band PEQ │ Manual      │ 16k-tap FIR │ UAC2 USB isochronous│
│              │ (rubato)   │             │ (biquads)   │ import      │ time-domain │ + DSD/DoP verify    │
├──────────────┼────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────────────┤
│ decent-player│ 32-bit int │ No          │ No          │ No          │ No          │ Native C++ thread   │
│              │ & flt32    │             │             │             │             │ to USB UAC2 driver  │
├──────────────┼────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────────────┤
│ Booming      │ 16-bit int │ Track/Album │ 10-band     │ 4,000+ DB   │ No          │ Android AudioTrack  │
│              │ (Media3)   │ (PCM clamp) │ DynamicsProc│ in Room     │             │ (Shared Mixer)      │
├──────────────┼────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────────────┤
│ Auxio        │ 16-bit int │ Track/Album │ System EQ   │ No          │ No          │ Android AudioTrack  │
│              │ (Media3)   │ / Dynamic   │ intent      │             │             │ (Shared Mixer)      │
├──────────────┼────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────────────┤
│ Phoebe       │ 32-bit flt │ Embedded    │ 31-band GEQ │ No          │ No          │ Platform default    │
│              │            │ tag read    │ (custom UI) │             │             │ (shared mixer)      │
├──────────────┼────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────────────┤
│ Strawberry   │ 32-bit flt │ Track/Album │ 10-band GEQ │ No          │ No          │ ALSA bit-perfect    │
│              │ (GStreamer)│ + Fallback  │ (GStreamer) │             │             │ direct device       │
├──────────────┼────────────┼─────────────┼─────────────┼─────────────┼─────────────┼─────────────────────┤
│ Harmonia     │ 64-bit flt │ ITU-R 1770  │ Parametric  │ AutoEQ Room │ 16k-tap FIR │ Dual-mode: Shared   │
│ (Target)     │ / 32-bit   │ -4 LUFS +   │ Biquad +    │ & SQLite DB │ Convolver   │ default + Direct USB│
│              │ flt mobile │ Peak Limiter│ Graphic     │ Integrated  │ IR Engine   │ / WASAPI Exclusive  │
└──────────────┴────────────┴─────────────┴─────────────┴─────────────┴─────────────┴─────────────────────┘
```

---

## 2. ReplayGain & ITU-R BS.1770-4 LUFS Architecture

Volume normalization prevents ear-splitting jumps between vintage masters (typically mastered around $-18$ to $-22$ LUFS) and hyper-compressed modern pop masters (often pushed to $-8$ to $-6$ LUFS during the "loudness wars").

### 2.1 The Two Processing Approaches Inspected

#### 1. Low-Level PCM Bitstream Manipulation ([Auxio](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Auxio-dev/app/src/main/java/org/oxycblt/auxio/playback/replaygain/ReplayGainAudioProcessor.kt)) `[SOURCE VERIFIED]`
- Uses Media3 `BaseAudioProcessor`. In `queueInput(ByteBuffer)`:
  $$\text{Gain Ratio} = 10^{\frac{\text{Adjustment (dB)}}{20}}$$
  $$\text{Sample}_{\text{out}} = \text{clamp}\left(\text{Sample}_{\text{in}} \times \text{Gain Ratio}, -32768, 32767\right)$$
- **Flaw Detected:** Clamping positive gain on 16-bit integers introduces severe clipping distortion if true peak exceeds 0 dBFS.

#### 2. Signal-Analysis-Driven Target Loudness ([Harmonia Phase 3](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/src/harmonia/infrastructure/audio/analyzer.py)) `[SOURCE VERIFIED]`
- Harmonia calculates exact ITU-R BS.1770-4 measurements on disk:
  - `integrated_lufs`: Gated integrated loudness in LUFS.
  - `true_peak_dbtp`: $4\times$ oversampled True Peak in dBTP.
  - `loudness_range_lu`: Dynamic range spread (LRA).

#### The Optimal Normalization Formula for Harmonia:
$$\Delta G = \text{Target LUFS} - \text{Integrated LUFS} + \text{Preamp dB}$$
To guarantee **zero clipping**:
$$\text{Max Allowed Gain} = -\text{True Peak (dBTP)} - \text{Headroom Margin (e.g. } 0.5\text{ dB)}$$
$$\text{Effective Gain} = \min(\Delta G, \text{Max Allowed Gain})$$

---

### 2.2 Gain Selection Strategies: Track vs. Album vs. Dynamic

Inspected across [Auxio](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Auxio-dev/app/src/main/java/org/oxycblt/auxio/playback/replaygain/ReplayGainMode.kt) and [Strawberry](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/strawberry-master/src/core/enginemetadata.cpp):

1. **Track Mode:** Normalizes every song independently to the target (e.g. $-14$ LUFS). Best for varied playlists and shuffle.
2. **Album Mode:** Applies a uniform album-wide gain offset to all tracks on an album. Preserves intentional volume differences between quiet acoustic interludes and loud orchestral crescendos.
3. **Dynamic Mode (Auxio's Innovation):**
   - If the player is currently playing through an entire album sequentially, it automatically engages **Album Gain**.
   - If the user enters a playlist, queue mix, or shuffle mode, it seamlessly switches to **Track Gain**.

---

## 3. Equalizers, AutoEQ, and FIR Convolution

### 3.1 Headphone AutoEQ Database Integration
Inspected in [Booming Music `AutoEqDao.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/BoomingMusic-master/app/src/main/java/com/mardous/booming/data/local/room/AutoEqDao.kt):
- Pre-populates a local SQLite table containing over 4,000 headphone calibration profiles from the AutoEQ project (Sennheiser HD600, Sony WH-1000XM5, Apple AirPods Max, Moondrop Blessing 2, etc.).
- Each entry stores:
  - Headphone Model & Manufacturer
  - Preamp offset (dB)
  - 5 to 10 Parametric Biquad Filter specifications: Filter Type (`Peaking`, `LowShelf`, `HighShelf`), Center Frequency ($f_c$ in Hz), Gain ($G$ in dB), and Quality Factor ($Q$).
- User selects their headphone model $\to$ sound profile is instantly linearized to the Harman target curve.

### 3.2 FIR Impulse Response (IR) Convolver
Inspected in [Flick `convolver-impulse-response-plan.md`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Flick-main/docs/audio/convolver-impulse-response-plan.md):
- **Purpose:** Loads WAV impulse response files for:
  - Headphone Crossfeed / Speaker Room Simulation (eliminates headphone listening fatigue).
  - Acoustic room correction (compensating for room modes and reflections).
  - Vintage studio reverb and tube/tape saturation models.
- **Algorithm Choice:**
  - Time-Domain Direct FIR Convolution with a tap cap of **16,384 samples** ($\approx 341$ ms at 48 kHz).
  - Algorithmic Latency: Zero samples (instantaneous).
  - CPU Overhead: $<1.2\%$ on modern mobile ARM cores.
  - Decoded via Symphonia, resampled via Rubato to current engine rate, and executed lock-free in the audio thread.

---

## 4. Bit-Perfect Playback & The USB Audio Frontier

Why do audiophiles complain about standard music players on Android and Windows?

### 4.1 The Shared Audio Mixer Problem
- **Android:** The Android `AudioFlinger` / AudioTrack system resamples all audio streams to a fixed hardware rate (usually 48 kHz), running them through a software mixer that introduces rounding noise, inter-sample clipping, and jitter.
- **Windows:** The Windows Audio Session API (WASAPI) Shared Mode mixes all application audio at the user's default system sample rate (e.g. 24-bit / 48 kHz), using low-grade polynomial interpolation.

### 4.2 Verified Solutions in Inspected Projects

#### 1. Windows Bit-Perfect WASAPI Exclusive Mode ([fooyin](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/fooyin-master/src/plugins/wasapi)) `[SOURCE VERIFIED]`
- Uses `IAudioClient::Initialize` with `AUDCLNT_SHAREMODE_EXCLUSIVE`.
- Requests direct hardware buffer access, automatically switching the external DAC's hardware master clock to match the track's native sample rate (44.1 kHz, 96 kHz, 192 kHz, 384 kHz). Zero resampling. Zero mixing.

#### 2. Native C++ Direct USB UAC2 Driver ([decent-player](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/decent-player-main/docs/libs/FLAC_DECODERS.md) & [Flick](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/Flick-main/docs/audio/usb-dac-architecture-audit.md)) `[SOURCE VERIFIED]`
- Claims Android USB host permissions (`android.hardware.usb.UsbManager`).
- Employs `libusb` in a dedicated native C++ thread to execute asynchronous isochronous transfers (`UAC2 Type I PCM`).
- Bypasses the entire Android OS audio stack.
- Supports native DSD (Direct Stream Digital) and DoP (DSD over PCM) streaming with payload wire auditing to verify true bit-perfect transmission.

---

## 5. Signal-Path Diagnostics: Making Audio Observable

A major innovation observed in [LocalTracklist `PlaybackAudioDiagnostics.kt`](file:///c:/Users/Hrushikesh%20Bunni/Downloads/H/PROJECTS/HARMONIA/RESOURCES/LocalTracklist-master/app/src/main/java/com/libreplayer/media/service/PlaybackAudioDiagnostics.kt) is **audio telemetry**.

Rather than keeping the signal chain a mystery, LocalTracklist exposes real-time instrumentation:
- Decoded input format: Container, Codec, Source Sample Rate, Channels, Bitrate, PCM bit-depth.
- Active AudioTrack / Sink config: Output Sample Rate, Buffer Size (bytes / ms), Audio Offload status, Tunneling status.
- Buffer underrun warnings: Elapsed feed time, dropped samples count.

---

## 6. The Proposed Harmonia Audio Signal Path

Synthesizing the best engineering practices from Flick, fooyin, Auxio, and Harmonia's existing Phase 3 signal intelligence, we define the **Harmonia Master Audio Signal Path**:

```
┌────────────────────────────────────────────────────────────────────────┐
│                   HARMONIA MASTER AUDIO SIGNAL PATH                    │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
                                   ▼
                [1. SOURCE LOSSLESS / HI-RES AUDIO FILE]
             (FLAC, ALAC, WAV, DSD, Opus, MP3, AAC, Ogg)
                                   │
                                   ▼
                 [2. SOFTWARE AUDIO DECODER ENGINE]
        (Native C++ libFLAC / FFmpeg / Media3 Software Codec)
                                   │
                                   ▼
             [3. UNPACKED 32-BIT / 64-BIT FLOAT PCM BUFFER]
         (High internal dynamic range prevents intermediate clipping)
                                   │
        ┌──────────────────────────┴──────────────────────────┐
        │                                                     │
   [BIT-PERFECT PASSTHROUGH MODE]                       [DSP PROCESSING MODE]
   (Bypasses all volume & EQ stages)                          │
        │                                                     ▼
        │                                    [4. REPLAYGAIN / LUFS LOUDNESS STAGE]
        │                                    (Uses Phase 3 integrated_lufs & true_peak;
        │                                     Dynamic mode: Album vs Track gain;
        │                                     Clamping protection via True Peak ceiling)
        │                                                     │
        │                                                     ▼
        │                                        [5. PARAMETRIC EQUALIZER]
        │                                    (AutoEQ Headphone Correction Biquads
        │                                     + User 10-band peaking/shelving filters)
        │                                                     │
        │                                                     ▼
        │                                      [6. FIR IMPULSE CONVOLVER]
        │                                    (16,384-tap direct time-domain FIR:
        │                                     Headphone crossfeed / room simulation)
        │                                                     │
        │                                                     ▼
        │                                    [7. DYNAMICS PEAK LIMITER / SAFETY]
        │                                    (Lookahead brickwall limiter prevents
        │                                     any sample from exceeding 0.0 dBFS)
        │                                                     │
        │                                                     ▼
        │                                           [8. MASTER VOLUME CONTROL]
        │                                                     │
        └──────────────────────────┬──────────────────────────┘
                                   │
                                   ▼
                    [9. OUTPUT ROUTING DISPATCHER]
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
        ▼                          ▼                          ▼
 [MOBILE SHARED MIXER]    [DIRECT USB UAC2 DAC]    [DESKTOP WASAPI EXCLUSIVE]
(Android AudioTrack 16/24) (Native C++ Bit-Perfect)   (Windows Bit-Perfect /
                           (Auto Sample Rate Switch)  Linux PipeWire Direct)
```
