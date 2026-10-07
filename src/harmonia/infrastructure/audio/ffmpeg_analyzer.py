"""FFprobe and FFmpeg audio analysis adapter."""

from __future__ import annotations

import json
import re
import shutil
import subprocess
from pathlib import Path

from harmonia.core.exceptions import (
    AnalysisTimeoutError,
    AudioAnalysisError,
    AudioDecodeError,
    AudioToolNotFoundError,
)
from harmonia.domain.ports.audio_analyzer import (
    SignalAnalysisResult,
    TechnicalAudioProperties,
)

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
    "tta",
}


class FFmpegAudioAnalyzer:
    """Audio analyzer implementation using system ffprobe and ffmpeg CLI tools."""

    ANALYZER_NAME = "ffmpeg_ebur128"
    ANALYZER_VERSION = "1.0.0"
    CONFIG_VERSION = "1.0.0"

    def __init__(
        self,
        ffprobe_bin: str = "ffprobe",
        ffmpeg_bin: str = "ffmpeg",
        timeout_seconds: float = 120.0,
    ) -> None:
        self.ffprobe_bin = ffprobe_bin
        self.ffmpeg_bin = ffmpeg_bin
        self.timeout_seconds = timeout_seconds

    def check_tools_available(self) -> tuple[bool, bool]:
        """Check if ffprobe and ffmpeg are accessible on system PATH."""
        has_probe = shutil.which(self.ffprobe_bin) is not None
        has_ffmpeg = shutil.which(self.ffmpeg_bin) is not None
        return has_probe, has_ffmpeg

    def probe_technical(self, file_path: Path) -> TechnicalAudioProperties:
        """Extract stream container and technical encoding properties via ffprobe."""
        if not file_path.exists():
            raise AudioAnalysisError(str(file_path), "File does not exist")

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
            res = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
            )
        except FileNotFoundError:
            raise AudioToolNotFoundError("ffprobe") from None
        except subprocess.TimeoutExpired:
            raise AnalysisTimeoutError(str(file_path), f"Probe timed out after {self.timeout_seconds}s") from None

        if res.returncode != 0 or not res.stdout.strip():
            raise AudioDecodeError(str(file_path), f"ffprobe exited with code {res.returncode}")

        try:
            data = json.loads(res.stdout)
        except json.JSONDecodeError as err:
            raise AudioDecodeError(str(file_path), f"Invalid ffprobe JSON: {err}") from err

        fmt = data.get("format", {})
        streams = data.get("streams", [])
        audio_stream = next((s for s in streams if s.get("codec_type") == "audio"), None)

        if not audio_stream:
            raise AudioDecodeError(str(file_path), "No audio stream found in file")

        codec = audio_stream.get("codec_name")
        is_lossless = (codec in LOSSLESS_CODECS) if codec else False

        # Bitrate
        bitrate: int | None = None
        raw_bitrate = audio_stream.get("bit_rate") or fmt.get("bit_rate")
        if raw_bitrate:
            try:
                bitrate = round(float(raw_bitrate) / 1000.0)
            except (ValueError, TypeError):
                bitrate = None

        # Sample rate
        sample_rate: int | None = None
        if "sample_rate" in audio_stream:
            try:
                sample_rate = int(audio_stream["sample_rate"])
            except (ValueError, TypeError):
                sample_rate = None

        # Bit depth
        bit_depth: int | None = None
        raw_depth = audio_stream.get("bits_per_raw_sample") or audio_stream.get("bits_per_sample")
        if raw_depth:
            try:
                bit_depth = int(raw_depth)
            except (ValueError, TypeError):
                bit_depth = None

        # Channels
        channels: int | None = None
        if "channels" in audio_stream:
            try:
                channels = int(audio_stream["channels"])
            except (ValueError, TypeError):
                channels = None

        # Duration
        duration: float | None = None
        raw_dur = audio_stream.get("duration") or fmt.get("duration")
        if raw_dur:
            try:
                duration = float(raw_dur)
            except (ValueError, TypeError):
                duration = None

        channel_layout = audio_stream.get("channel_layout")
        tags = fmt.get("tags", {})
        encoder = tags.get("encoder") or audio_stream.get("tags", {}).get("encoder")

        return TechnicalAudioProperties(
            duration_seconds=duration,
            codec=codec,
            bitrate=bitrate,
            sample_rate=sample_rate,
            bit_depth=bit_depth,
            channels=channels,
            channel_layout=channel_layout,
            encoder=encoder,
            is_lossless=is_lossless,
        )

    def analyze_signal(self, file_path: Path) -> SignalAnalysisResult:
        """Decode and measure objective audio signal characteristics via ffmpeg."""
        if not file_path.exists():
            raise AudioAnalysisError(str(file_path), "File does not exist")

        cmd = [
            self.ffmpeg_bin,
            "-nostats",
            "-i",
            str(file_path),
            "-af",
            "ebur128=peak=true,volumedetect,silencedetect=noise=-60dB:d=0.1",
            "-f",
            "null",
            "-",
        ]

        try:
            res = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=self.timeout_seconds,
                check=False,
            )
        except FileNotFoundError:
            raise AudioToolNotFoundError("ffmpeg") from None
        except subprocess.TimeoutExpired:
            raise AnalysisTimeoutError(str(file_path), f"Analysis timed out after {self.timeout_seconds}s") from None

        if res.returncode != 0:
            raise AudioDecodeError(str(file_path), f"ffmpeg exited with code {res.returncode}: {res.stderr[:300]}")

        output = res.stderr
        return self._parse_ffmpeg_output(output)

    def _parse_ffmpeg_output(self, output: str) -> SignalAnalysisResult:
        """Parse ebur128, volumedetect, and silencedetect statistics from ffmpeg stderr."""
        # 1. Integrated loudness (LUFS)
        m_lufs = re.search(r"Integrated loudness:\s+I:\s+([-+\d.]+)\s+LUFS", output)
        integrated_lufs = float(m_lufs.group(1)) if m_lufs else None

        # 2. Loudness range (LU)
        m_lra = re.search(r"Loudness range:\s+LRA:\s+([-+\d.]+)\s+LU", output)
        loudness_range_lu = float(m_lra.group(1)) if m_lra else None

        # 3. True peak (dBTP / dBFS)
        m_tp = re.search(r"True peak:\s+Peak:\s+([-+\d.]+)\s+dB(?:FS|TP)", output)
        true_peak_dbtp = float(m_tp.group(1)) if m_tp else None

        # 4. Max volume (sample peak in dBFS)
        m_sample_peak = re.search(r"max_volume:\s+([-+\d.]+)\s+dB", output)
        sample_peak_dbfs = float(m_sample_peak.group(1)) if m_sample_peak else None

        # 5. Clipping samples (histogram_0db)
        m_clip = re.search(r"histogram_0db:\s+(\d+)", output)
        clipping_samples = int(m_clip.group(1)) if m_clip else 0

        # Clipping determination
        is_clipped = False
        if clipping_samples > 0 or (true_peak_dbtp is not None and true_peak_dbtp >= -0.01) or (sample_peak_dbfs is not None and sample_peak_dbfs >= -0.01):
            is_clipped = True

        # 6. Peak-to-loudness ratio (crest factor in dB)
        peak_to_loudness_db: float | None = None
        if true_peak_dbtp is not None and integrated_lufs is not None:
            peak_to_loudness_db = round(true_peak_dbtp - integrated_lufs, 2)

        # 7. Silence boundaries
        # Leading silence: silence_start: 0 -> silence_end: X
        silence_start_sec: float | None = None
        m_lead_silence = re.search(
            r"silencedetect.*?silence_start:\s*0(?:\.0+)?\b.*?silence_end:\s*([-\d.]+)",
            output,
            re.DOTALL,
        )
        if m_lead_silence:
            silence_start_sec = round(float(m_lead_silence.group(1)), 3)

        # Trailing silence: find all silence_start occurrences
        silence_end_sec: float | None = None
        silence_starts = re.findall(r"silence_start:\s*([-\d.]+)", output)
        silence_ends = re.findall(r"silence_end:\s*([-\d.]+)", output)
        # If there is a silence_start without a matching silence_end, it continues to EOF
        if len(silence_starts) > len(silence_ends) and silence_starts:
            last_start = float(silence_starts[-1])
            # Determine total duration to compute trailing silence length
            m_dur = re.search(r"time=(\d+):(\d+):([-\d.]+)", output)
            if m_dur:
                hours, minutes, seconds = m_dur.groups()
                total_duration = int(hours) * 3600 + int(minutes) * 60 + float(seconds)
                trailing_len = max(0.0, total_duration - last_start)
                silence_end_sec = round(trailing_len, 3)

        # Duration analyzed from progress or input
        duration_analyzed: float | None = None
        m_time = re.search(r"time=(\d+):(\d+):([-\d.]+)", output)
        if m_time:
            h, m, s = m_time.groups()
            duration_analyzed = round(int(h) * 3600 + int(m) * 60 + float(s), 3)

        return SignalAnalysisResult(
            integrated_lufs=integrated_lufs,
            loudness_range_lu=loudness_range_lu,
            true_peak_dbtp=true_peak_dbtp,
            sample_peak_dbfs=sample_peak_dbfs,
            peak_to_loudness_db=peak_to_loudness_db,
            clipping_samples=clipping_samples,
            is_clipped=is_clipped,
            duration_analyzed=duration_analyzed,
            silence_start_sec=silence_start_sec,
            silence_end_sec=silence_end_sec,
            analyzer_name=self.ANALYZER_NAME,
            analyzer_version=self.ANALYZER_VERSION,
            config_version=self.CONFIG_VERSION,
        )
