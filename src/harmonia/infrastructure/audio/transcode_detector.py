"""Spectral transcode detection adapter for identifying lossy-to-lossless upconverts.

Analyzes the frequency spectrum of nominally lossless audio files (FLAC, ALAC, WAV)
to detect artificial high-frequency brickwall cutoffs characteristic of lossy
psychoacoustic encoders (MP3, AAC, Vorbis, Opus).
"""

from __future__ import annotations

import json
import math
import struct
import subprocess
from pathlib import Path

from harmonia.core.exceptions import (
    AnalysisTimeoutError,
    AudioAnalysisError,
    AudioDecodeError,
    AudioToolNotFoundError,
)
from harmonia.domain.ports.audio_analyzer import (
    TechnicalAudioProperties,
    TranscodeAnalysisResult,
    TranscodeClassification,
    TranscodeReasonCode,
)

try:
    import numpy as np

    _HAS_NUMPY = True
except ImportError:
    _HAS_NUMPY = False

LOSSLESS_EXTENSIONS = {".flac", ".alac", ".wav", ".wave", ".aif", ".aiff", ".wv", ".ape"}
LOSSLESS_CODECS = {
    "flac",
    "alac",
    "wav",
    "pcm_s16le",
    "pcm_s24le",
    "pcm_s32le",
    "pcm_f32le",
    "pcm_s16be",
    "pcm_s24be",
    "pcm_s32be",
    "ape",
    "wavpack",
    "truehd",
    "mlp",
}


def _pure_python_fft(x: list[complex]) -> list[complex]:
    """Cooley-Tukey Radix-2 Decimation-In-Time FFT for power-of-two lengths."""
    n = len(x)
    if n == 0 or (n & (n - 1)) != 0:
        raise ValueError(f"FFT input length must be a non-zero power of two, got {n}")
    return _cooley_tukey_radix2(x)


def _cooley_tukey_radix2(x: list[complex]) -> list[complex]:
    n = len(x)
    if n <= 1:
        return x
    even = _cooley_tukey_radix2(x[0::2])
    odd = _cooley_tukey_radix2(x[1::2])
    half = n // 2
    factors = [math.e ** (-2j * math.pi * k / n) * odd[k] for k in range(half)]
    return [even[k] + factors[k] for k in range(half)] + [even[k] - factors[k] for k in range(half)]


class SpectralTranscodeDetector:
    """Detects suspicious brickwall frequency cutoffs in nominally lossless audio files.

    Heuristic Architecture:
    1. Stream decoded mono PCM from representative temporal windows via FFmpeg stdout.
    2. Compute Short-Time Fourier Transform (STFT) with Hann windowing and 50% overlap.
    3. Evaluate midband reference energy (2-8 kHz) and upper treble energy (8-12 kHz).
       If acoustic energy is naturally absent or quiet, emits INSUFFICIENT_EVIDENCE
       to avoid false positives on speech, silence, bass solos, or vintage masters.
    4. Measures adjacent sub-band step-down slope (dB/kHz) between 13 kHz and 21.5 kHz.
    5. A steep drop (>= 14 dB/kHz) followed by deep spectral suppression (<= -20 dB)
       below 20.8 kHz triggers SUSPICIOUS_CUTOFF.
    6. Natural, gradual roll-off or continuous extension toward Nyquist triggers
       NO_SUSPICIOUS_CUTOFF.
    """

    FFT_SIZE = 2048
    HOP_SIZE = 1024
    MIN_DROP_DB_PER_KHZ = 14.0
    MIN_UPPER_SUPPRESSION_DB = -20.0
    MAX_CUTOFF_FREQ_HZ = 21000
    MIN_CUTOFF_FREQ_HZ = 13000
    SUB_BAND_STEP_HZ = 500

    def __init__(
        self,
        ffprobe_bin: str = "ffprobe",
        ffmpeg_bin: str = "ffmpeg",
        timeout_seconds: float = 60.0,
    ) -> None:
        self.ffprobe_bin = ffprobe_bin
        self.ffmpeg_bin = ffmpeg_bin
        self.timeout_seconds = timeout_seconds

    def check_transcode(
        self,
        file_path: Path,
        tech_props: TechnicalAudioProperties | None = None,
    ) -> TranscodeAnalysisResult:
        """Inspect file spectrum and classify transcode status."""
        if not file_path.exists():
            raise AudioAnalysisError(str(file_path), "File does not exist")

        # 1. Gate 1a: Fast extension check for obviously lossy files
        ext = file_path.suffix.lower()
        if ext and ext not in LOSSLESS_EXTENSIONS:
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.ANALYSIS_UNAVAILABLE,
                is_lossless_container=False,
                duration_analyzed_sec=0.0,
                details=(
                    f"File format ({ext.lstrip('.')}) is already lossy; "
                    "cutoff detection applies only to nominally lossless containers."
                ),
                reason_code=TranscodeReasonCode.UNSUPPORTED_CONTAINER,
            )

        # 2. Obtain technical properties if not supplied
        if tech_props is None:
            try:
                tech_props = self._probe_properties(file_path)
            except (AudioDecodeError, AudioToolNotFoundError, AnalysisTimeoutError) as e:
                if isinstance(e, AudioToolNotFoundError):
                    rcode = TranscodeReasonCode.TOOL_MISSING
                elif isinstance(e, AnalysisTimeoutError):
                    rcode = TranscodeReasonCode.TIMEOUT
                else:
                    rcode = TranscodeReasonCode.CORRUPT_OR_UNPARSEABLE
                return TranscodeAnalysisResult(
                    classification=TranscodeClassification.ANALYSIS_UNAVAILABLE,
                    is_lossless_container=ext in LOSSLESS_EXTENSIONS,
                    duration_analyzed_sec=0.0,
                    details=f"Failed to read audio stream metadata: {e}",
                    error=str(e),
                    reason_code=rcode,
                )

        # 3. Gate 1b: Verify stream codec is truly lossless
        is_lossless = tech_props.is_lossless or (
            tech_props.codec is not None and tech_props.codec.lower() in LOSSLESS_CODECS
        )
        if not is_lossless:
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.ANALYSIS_UNAVAILABLE,
                is_lossless_container=False,
                duration_analyzed_sec=0.0,
                details=(
                    f"Audio codec ({tech_props.codec or 'unknown'}) is lossy; "
                    "cutoff detection applies only to nominally lossless containers."
                ),
                reason_code=TranscodeReasonCode.UNSUPPORTED_CONTAINER,
            )

        sample_rate = tech_props.sample_rate
        duration = tech_props.duration_seconds or 0.0

        # 4. Gate 2: Low sample rate
        if sample_rate is None or sample_rate < 32000:
            nyquist_str = f"{sample_rate / 2.0:.0f} Hz" if sample_rate else "unknown"
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.INSUFFICIENT_EVIDENCE,
                is_lossless_container=True,
                duration_analyzed_sec=0.0,
                details=(
                    f"Sample rate ({sample_rate or 'unknown'} Hz) has Nyquist ({nyquist_str}) "
                    "below lossy cutoff analysis bandwidth (14-21 kHz)."
                ),
                reason_code=TranscodeReasonCode.LOW_SAMPLE_RATE,
            )

        # 5. Gate 3: Short duration
        if duration < 0.5:
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.INSUFFICIENT_EVIDENCE,
                is_lossless_container=True,
                duration_analyzed_sec=duration,
                details="Audio duration is too short (< 0.5s) for stable spectral cutoff analysis.",
                reason_code=TranscodeReasonCode.SHORT_DURATION,
            )

        # 6. Determine analysis windows
        windows = self._select_analysis_windows(duration)
        channels = tech_props.channels or 2

        # 7. Decode PCM samples across windows
        samples: list[float] = []
        total_time_analyzed = 0.0

        for start_sec, win_len in windows:
            try:
                seg_samples = self._decode_pcm_segment(file_path, start_sec, win_len, channels)
                if seg_samples:
                    samples.extend(seg_samples)
                    total_time_analyzed += win_len
            except (AudioDecodeError, AudioToolNotFoundError, AnalysisTimeoutError) as e:
                if isinstance(e, AudioToolNotFoundError):
                    decode_rcode = TranscodeReasonCode.TOOL_MISSING
                elif isinstance(e, AnalysisTimeoutError):
                    decode_rcode = TranscodeReasonCode.TIMEOUT
                else:
                    decode_rcode = TranscodeReasonCode.DECODE_ERROR
                return TranscodeAnalysisResult(
                    classification=TranscodeClassification.ANALYSIS_UNAVAILABLE,
                    is_lossless_container=True,
                    duration_analyzed_sec=round(total_time_analyzed, 2),
                    details=f"Audio decode failed: {e}",
                    error=str(e),
                    reason_code=decode_rcode,
                )

        if len(samples) < self.FFT_SIZE:
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.INSUFFICIENT_EVIDENCE,
                is_lossless_container=True,
                duration_analyzed_sec=total_time_analyzed,
                details="Decoded stream has insufficient sample frames for FFT analysis.",
                reason_code=TranscodeReasonCode.INSUFFICIENT_FRAMES,
            )

        # 7. Compute average Power Spectral Density (PSD)
        psd, freqs = self._compute_psd(samples, sample_rate)

        # 8. Gate 4: Silence and low-energy qualification
        p_ref = self._band_power(psd, freqs, 2000.0, 8000.0)
        if p_ref < 1e-7:
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.INSUFFICIENT_EVIDENCE,
                is_lossless_container=True,
                duration_analyzed_sec=round(total_time_analyzed, 2),
                details="Signal has negligible acoustic energy in the 2-8 kHz reference band (silence or ambient tone).",
                reason_code=TranscodeReasonCode.SILENCE_OR_LOW_ENERGY,
            )

        # Check upper treble energy (8-12 kHz)
        p_treble = self._band_power(psd, freqs, 8000.0, 12000.0)
        treble_ratio_db = 10.0 * math.log10(max(1e-12, p_treble / (p_ref + 1e-12)))
        if treble_ratio_db < -42.0:
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.INSUFFICIENT_EVIDENCE,
                is_lossless_container=True,
                duration_analyzed_sec=round(total_time_analyzed, 2),
                details=(
                    f"Signal rolls off naturally below 12 kHz ({treble_ratio_db:.1f} dB relative to midband); "
                    "insufficient high-frequency content to distinguish lossy cutoff from natural source bandwidth."
                ),
                reason_code=TranscodeReasonCode.NATURAL_ROLLOFF_OR_SPEECH,
            )

        # 9. Step-down sub-band slope analysis
        nyquist = sample_rate / 2.0
        max_analysis_freq = min(22000.0, nyquist - 500.0)
        if max_analysis_freq <= self.MIN_CUTOFF_FREQ_HZ + 1000:
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.INSUFFICIENT_EVIDENCE,
                is_lossless_container=True,
                duration_analyzed_sec=round(total_time_analyzed, 2),
                details=f"Analysis bandwidth limit ({max_analysis_freq:.0f} Hz) too low for cutoff detection.",
                reason_code=TranscodeReasonCode.BANDWIDTH_EXCEEDED,
            )

        band_powers: list[tuple[int, float]] = []
        bands = list(range(self.MIN_CUTOFF_FREQ_HZ, int(max_analysis_freq), self.SUB_BAND_STEP_HZ))

        for b_start in bands:
            b_end = min(b_start + self.SUB_BAND_STEP_HZ, max_analysis_freq)
            bp = self._band_power(psd, freqs, float(b_start), float(b_end))
            db = 10.0 * math.log10(max(1e-12, bp / (p_ref + 1e-12)))
            band_powers.append((b_start, db))

        # Check if high frequencies reach the 13-14 kHz analysis zone
        if band_powers and band_powers[0][1] < -42.0:
            return TranscodeAnalysisResult(
                classification=TranscodeClassification.INSUFFICIENT_EVIDENCE,
                is_lossless_container=True,
                duration_analyzed_sec=round(total_time_analyzed, 2),
                details=(
                    f"Acoustic energy at 13-14 kHz ({band_powers[0][1]:.1f} dB relative to midband) is below "
                    "qualification threshold; signal does not extend into the 14-22 kHz analysis region."
                ),
                reason_code=TranscodeReasonCode.BANDLIMITED_BELOW_SEARCH_RANGE,
            )

        # Find maximum step-down drop (dB/kHz) across adjacent sub-bands.
        max_drop = 0.0
        drop_boundary: int | None = None

        for i in range(len(band_powers) - 1):
            f_cur, db_cur = band_powers[i]
            f_next, db_next = band_powers[i + 1]
            delta_khz = (f_next - f_cur) / 1000.0
            if delta_khz <= 0.0:
                continue
            drop_per_khz = (db_cur - db_next) / delta_khz
            if drop_per_khz > max_drop:
                max_drop = drop_per_khz
                drop_boundary = f_next

        # Measure high-frequency ratio (19k to max_analysis_freq)
        p_hf = self._band_power(psd, freqs, 19000.0, max_analysis_freq) if max_analysis_freq > 19000 else 0.0
        hf_ratio_db = 10.0 * math.log10(max(1e-12, p_hf / (p_ref + 1e-12)))

        # Power suppression above cutoff transition
        suppression_db = 0.0
        if drop_boundary is not None:
            p_above = self._band_power(psd, freqs, float(drop_boundary), max_analysis_freq)
            suppression_db = 10.0 * math.log10(max(1e-12, p_above / (p_ref + 1e-12)))

        # 10. Classification decision and boundary reliability check
        is_suspicious = (
            max_drop >= self.MIN_DROP_DB_PER_KHZ
            and suppression_db <= self.MIN_UPPER_SUPPRESSION_DB
        )

        cutoff_hz: int | None = None
        if is_suspicious and drop_boundary is not None:
            # Check for upper analysis boundary distortion:
            # If the detected transition is within 1000 Hz of the upper analysis limit
            # or exceeds MAX_CUTOFF_FREQ_HZ, the frequency cannot be reliably separated
            # from boundary roll-off. In that case, represent the estimate as unavailable (None).
            if (max_analysis_freq - drop_boundary) >= 1000.0 and drop_boundary <= self.MAX_CUTOFF_FREQ_HZ:
                cutoff_hz = drop_boundary
            else:
                cutoff_hz = None

        if is_suspicious:
            classification = TranscodeClassification.SUSPICIOUS_CUTOFF
            if cutoff_hz is not None:
                details = (
                    f"Suspicious brickwall cutoff of {max_drop:.1f} dB/kHz detected at ~{cutoff_hz} Hz; "
                    f"energy suppressed to {suppression_db:.1f} dB above cutoff."
                )
            else:
                details = (
                    f"Suspicious brickwall cutoff of {max_drop:.1f} dB/kHz detected; "
                    f"cutoff frequency near upper analysis boundary (~{drop_boundary} Hz) is unavailable."
                )
            reason_code = TranscodeReasonCode.SUSPICIOUS_CUTOFF_DETECTED
        else:
            classification = TranscodeClassification.NO_SUSPICIOUS_CUTOFF
            details = (
                f"No suspicious cutoff detected (max drop: {max_drop:.1f} dB/kHz, "
                f"HF ratio: {hf_ratio_db:.1f} dB). Spectral energy rolls off smoothly or extends to Nyquist."
            )
            reason_code = TranscodeReasonCode.NO_SUSPICIOUS_CUTOFF_DETECTED

        return TranscodeAnalysisResult(
            classification=classification,
            is_lossless_container=True,
            cutoff_frequency_hz=cutoff_hz if classification == TranscodeClassification.SUSPICIOUS_CUTOFF else None,
            cutoff_steepness_db_per_khz=round(max_drop, 2),
            high_frequency_ratio_db=round(hf_ratio_db, 2),
            duration_analyzed_sec=round(total_time_analyzed, 2),
            details=details,
            reason_code=reason_code,
        )

    # ── Internal Helpers ────────────────────────────────────────────────────

    def _probe_properties(self, file_path: Path) -> TechnicalAudioProperties:
        """Run ffprobe to extract basic audio stream attributes."""
        cmd = [
            self.ffprobe_bin,
            "-v",
            "quiet",
            "-print_format",
            "json",
            "-show_format",
            "-show_streams",
            str(file_path),
        ]
        try:
            res = subprocess.run(cmd, capture_output=True, text=True, timeout=self.timeout_seconds, check=False)
        except FileNotFoundError:
            raise AudioToolNotFoundError("ffprobe") from None
        except subprocess.TimeoutExpired:
            raise AnalysisTimeoutError(str(file_path), f"ffprobe timed out after {self.timeout_seconds}s") from None

        if res.returncode != 0 or not res.stdout.strip():
            raise AudioDecodeError(str(file_path), f"ffprobe failed with exit code {res.returncode}")

        try:
            data = json.loads(res.stdout)
        except json.JSONDecodeError as exc:
            raise AudioDecodeError(str(file_path), f"Invalid ffprobe JSON: {exc}") from exc

        audio_stream = next((s for s in data.get("streams", []) if s.get("codec_type") == "audio"), None)
        if not audio_stream:
            raise AudioDecodeError(str(file_path), "No audio stream found")

        codec = audio_stream.get("codec_name")
        lossless_codecs = {"flac", "alac", "wav", "pcm_s16le", "pcm_s24le", "pcm_s32le", "ape", "wavpack", "tta"}
        is_lossless = (codec in lossless_codecs) if codec else False

        sample_rate = int(audio_stream["sample_rate"]) if "sample_rate" in audio_stream else None
        channels = int(audio_stream["channels"]) if "channels" in audio_stream else None
        duration = float(audio_stream.get("duration") or data.get("format", {}).get("duration", 0.0))

        if not sample_rate or not channels or sample_rate <= 0 or channels <= 0:
            raise AudioDecodeError(str(file_path), "Audio stream is corrupt or unparseable (missing valid sample rate or channels)")

        return TechnicalAudioProperties(
            duration_seconds=duration,
            codec=codec,
            sample_rate=sample_rate,
            channels=channels,
            is_lossless=is_lossless,
        )

    def _select_analysis_windows(self, duration: float) -> list[tuple[float, float]]:
        """Select representative temporal windows across the track."""
        if duration >= 12.0:
            return [
                (duration * 0.25, 1.5),
                (duration * 0.50, 1.5),
                (duration * 0.75, 1.5),
            ]
        elif duration >= 4.0:
            return [
                (0.5, 1.5),
                (duration - 2.0, 1.5),
            ]
        else:
            return [(0.0, duration)]

    def _decode_pcm_segment(
        self,
        file_path: Path,
        start_sec: float,
        duration_sec: float,
        channels: int,
    ) -> list[float]:
        """Decode mono 32-bit float PCM via FFmpeg stdout pipe."""
        # For multi-channel, average front left and right to isolate from LFE/surround
        af_filter = "pan=mono|c0=0.5*c0+0.5*c1" if channels >= 2 else "aformat=channel_layouts=mono"

        cmd = [
            self.ffmpeg_bin,
            "-nostats",
            "-v",
            "error",
            "-ss",
            f"{start_sec:.3f}",
            "-t",
            f"{duration_sec:.3f}",
            "-i",
            str(file_path),
            "-vn",
            "-af",
            af_filter,
            "-f",
            "f32le",
            "-",
        ]

        try:
            res = subprocess.run(cmd, capture_output=True, timeout=self.timeout_seconds, check=False)
        except FileNotFoundError:
            raise AudioToolNotFoundError("ffmpeg") from None
        except subprocess.TimeoutExpired:
            raise AnalysisTimeoutError(str(file_path), f"FFmpeg decode timed out after {self.timeout_seconds}s") from None

        if res.returncode != 0:
            raise AudioDecodeError(str(file_path), f"FFmpeg decode error ({res.returncode}): {res.stderr[:200]}")

        raw = res.stdout
        num_floats = len(raw) // 4
        if num_floats == 0:
            return []
        return list(struct.unpack(f"<{num_floats}f", raw[: num_floats * 4]))

    def _compute_psd(self, samples: list[float], sample_rate: int) -> tuple[list[float], list[float]]:
        """Compute average power spectral density across windowed frames."""
        n = self.FFT_SIZE
        hop = self.HOP_SIZE
        num_frames = (len(samples) - n) // hop
        if num_frames <= 0:
            num_frames = 1
            padded = samples + [0.0] * (n - len(samples))
            frames_list = [padded]
        else:
            # Bound total frames analyzed to 64 for speed
            step_stride = max(1, num_frames // 48)
            frames_list = [samples[i * hop : i * hop + n] for i in range(0, num_frames, step_stride)]

        # Precompute Hann window
        hann = [0.5 * (1.0 - math.cos(2.0 * math.pi * i / (n - 1))) for i in range(n)]

        if _HAS_NUMPY:
            arr_frames = np.array(frames_list, dtype=np.float32)
            arr_hann = np.array(hann, dtype=np.float32)
            windowed = arr_frames * arr_hann
            fft_res = np.abs(np.fft.rfft(windowed, axis=1)) ** 2
            mean_psd = np.mean(fft_res, axis=0)
            freqs = np.fft.rfftfreq(n, 1.0 / sample_rate)
            return mean_psd.tolist(), freqs.tolist()
        else:
            # Pure Python fallback
            half = n // 2 + 1
            acc_psd = [0.0] * half
            for frame in frames_list:
                w_frame = [complex(frame[i] * hann[i], 0.0) for i in range(n)]
                fft_vals = _pure_python_fft(w_frame)
                for k in range(half):
                    acc_psd[k] += (fft_vals[k].real ** 2 + fft_vals[k].imag ** 2) / len(frames_list)
            freqs_py = [k * sample_rate / n for k in range(half)]
            return acc_psd, freqs_py

    def _band_power(self, psd: list[float], freqs: list[float], f_min: float, f_max: float) -> float:
        """Calculate mean power within a frequency band."""
        powers = [psd[i] for i, f in enumerate(freqs) if f_min <= f < f_max]
        if not powers:
            return 0.0
        return sum(powers) / len(powers)
