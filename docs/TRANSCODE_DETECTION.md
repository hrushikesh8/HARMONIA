# Harmonia Suspicious Spectral Cutoff & Transcode Detection Specification

**Document Classification:** Subsystem Specification — Desktop Milestone D5-B  
**Status:** ACTIVE & AUTHORITATIVE  
**Guiding Architecture:** [`docs/ARCHITECTURE.md`](ARCHITECTURE.md), [`docs/DATA_MODEL.md`](DATA_MODEL.md)  
**Primary Implementations:**
- Domain Port & Contracts: [`src/harmonia/domain/ports/audio_analyzer.py`](../src/harmonia/domain/ports/audio_analyzer.py)
- Transcode Detector: [`src/harmonia/infrastructure/audio/transcode_detector.py`](../src/harmonia/infrastructure/audio/transcode_detector.py)
- Analyzer Adapter: [`src/harmonia/infrastructure/audio/ffmpeg_analyzer.py`](../src/harmonia/infrastructure/audio/ffmpeg_analyzer.py)
- Application Service: [`src/harmonia/application/audio_analysis_service.py`](../src/harmonia/application/audio_analysis_service.py)
- CLI Command: [`src/harmonia/cli/analyze.py`](../src/harmonia/cli/analyze.py)

---

## 1. Overview & Heuristic Principles

Harmonia Desktop Milestone **D5-B** provides an offline, read-only heuristic analysis tool designed to inspect nominally lossless audio files (e.g., FLAC, ALAC, WAV, AIFF) for artificial, steep brickwall high-frequency cutoffs characteristic of lossy-to-lossless upconverts (such as transcoding MP3 or low-bitrate AAC to FLAC).

### 1.1 Invariant: Heuristic Analysis vs. Authenticity Proof
> [!IMPORTANT]
> **Defensive Epistemic Boundary:**
> This subsystem identifies **suspicious spectral cutoffs**. It **cannot** and **does not** prove file provenance, bit-exact authenticity, or that a file is genuinely lossless. Natural recordings, vintage masters, vocal tracks, synthesizer patches, and acoustic arrangements may roll off naturally without ever having been compressed by a lossy codec.
> 
> Therefore, Harmonia strictly forbids using terms like `CLEAN_LOSSLESS`, `VERIFIED_GENUINE`, or `PROVEN_LOSSLESS` in its domain classification.

---

## 2. Classification Contract & Reason Codes

### 2.1 Four-State Classification Contract (`TranscodeClassification`)

The analysis outputs one of four mutually exclusive, conservative classifications:

| Classification | Meaning |
| :--- | :--- |
| `SUSPICIOUS_CUTOFF` | Detected a sharp step-down cliff ($\ge 14.0$ dB/kHz) followed by deep spectral suppression ($\le -20.0$ dB relative to midband) within the 13–21 kHz region. |
| `NO_SUSPICIOUS_CUTOFF` | Acoustic energy extends smoothly toward Nyquist ($F_s / 2$) or rolls off with a gentle slope without brickwall suppression. |
| `INSUFFICIENT_EVIDENCE` | The acoustic content cannot be reliably classified (e.g. digital silence, duration $< 0.5$s, low sample rate $< 32$ kHz, speech-limited treble). |
| `ANALYSIS_UNAVAILABLE` | Technical or operational failure prevented completion (e.g. missing FFmpeg, decode error, corrupt container, process timeout). |

### 2.2 Machine-Readable Reason Codes (`TranscodeReasonCode`)

Callers and automated pipelines distinguish exact operational and signal causes via `reason_code`:

```python
class TranscodeReasonCode(StrEnum):
    UNSUPPORTED_CONTAINER = "unsupported_container"
    LOW_SAMPLE_RATE = "low_sample_rate"
    SHORT_DURATION = "short_duration"
    SILENCE_OR_LOW_ENERGY = "silence_or_low_energy"
    NATURAL_ROLLOFF_OR_SPEECH = "natural_rolloff_or_speech"
    BANDLIMITED_BELOW_SEARCH_RANGE = "bandlimited_below_search_range"
    INSUFFICIENT_FRAMES = "insufficient_frames"
    BANDWIDTH_EXCEEDED = "bandwidth_exceeded"
    SUSPICIOUS_CUTOFF_DETECTED = "suspicious_cutoff_detected"
    NO_SUSPICIOUS_CUTOFF_DETECTED = "no_suspicious_cutoff_detected"
    TOOL_MISSING = "tool_missing"
    TIMEOUT = "timeout"
    DECODE_ERROR = "decode_error"
    CORRUPT_OR_UNPARSEABLE = "corrupt_or_unparseable"
    ANALYSIS_FAILED = "analysis_failed"
```

---

## 3. Signal Processing Architecture & Execution Pipeline

```
[Audio File]
     │
     ▼
[Gate 1: Container & Codec Check] ──(Lossy Container)──► ANALYSIS_UNAVAILABLE
     │ (Nominally Lossless)
     ▼
[Gate 2: Sample Rate >= 32 kHz] ────(SR < 32 kHz)──────► INSUFFICIENT_EVIDENCE (Nyquist too low)
     │
     ▼
[Gate 3: Duration >= 0.5s] ─────────(Duration < 0.5s)──► INSUFFICIENT_EVIDENCE (Too short)
     │
     ▼
[Temporal Window Sampling] ─────────(Bounded 1.5s windows at 25%, 50%, 75%)
     │ (FFmpeg pipe: 32-bit float mono PCM, <800 KB RAM)
     ▼
[STFT / PSD Computation] ───────────(Hann windowed, pure-Python Radix-2 FFT or NumPy)
     │
     ▼
[Gate 4: Reference Power (2-8 kHz)] ─(p_ref < 1e-7)─────► INSUFFICIENT_EVIDENCE (Silence / ambient)
     │
     ▼
[Gate 5: Treble Energy (8-12 kHz)] ──(treble < -42 dB)──► INSUFFICIENT_EVIDENCE (Natural rolloff / speech)
     │
     ▼
[500 Hz Sub-Band Step-Down Analysis] (13 kHz to max_analysis_freq)
     │
     ├── Maximum slope drop >= 14 dB/kHz?
     ├── Upper spectral suppression <= -20 dB?
     ▼
[Upper Boundary Reliability Guard]
     ├── If drop boundary within 1000 Hz of Nyquist guard (e.g. >= 21 kHz @ 44.1k):
     │   └── classification = SUSPICIOUS_CUTOFF, cutoff_frequency_hz = None (Unavailable)
     └── If drop boundary is interior (13.5k - 20.5k):
         └── classification = SUSPICIOUS_CUTOFF, cutoff_frequency_hz = detected Hz
```

### 3.1 FFT Fallback & Dependency-Free Math
The detector operates with zero external scientific dependencies. When NumPy is unavailable (the default state in the Harmonia virtual environment), a pure-Python Cooley-Tukey Radix-2 Decimation-In-Time FFT is used. It achieves numerical accuracy within $6.7 \times 10^{-13}$ of analytical DFT.

### 3.2 Subprocess Safety and Bounded Resources
- **Decoding:** Streams mono 32-bit float PCM via FFmpeg stdout using `pan=mono|c0=0.5*c0+0.5*c1` for multichannel streams.
- **Memory:** Audio is decoded strictly within three 1.5-second windows (max 4.5 seconds total, consuming $<800$ KB memory). Full audio files are never decoded into RAM.
- **Timeouts:** Metadata extraction (`ffprobe`) and PCM decoding (`ffmpeg`) enforce finite timeouts (`timeout_seconds=60.0`, default decode slice 15.0s).

---

## 4. Known Limitations

1. **Multichannel Downmix:** For multichannel audio ($\ge 3$ channels), the front left and right channels are averaged and surround/LFE channels are omitted. If transcode artifacts were isolated exclusively to surround channels while front channels remained uncompressed, the cutoff could be attenuated.
2. **Encoders Without Brickwall Lowpass Filters:** Certain lossy encoders (e.g., native FFmpeg AAC or Vorbis on flat synthetic noise) do not apply steep brickwall lowpass filtering. Spectral cutoff detection specifically detects *steep spectral shelf cutoffs*; it cannot detect psychoacoustic quantization noise where no lowpass filter is applied.
3. **Upper Boundary Ambiguity:** In 44.1 kHz material, lowpass transitions above 20.5 kHz fall within 1,000 Hz of the upper analysis boundary (21,550 Hz). In this region, genuine digital anti-aliasing / reconstruction filters cannot be reliably distinguished from lossy encoder filters. The detector marks the frequency estimate as unavailable (`None`) while recording the suspicious drop.
