package org.harmonia.player.service.audio

import androidx.annotation.OptIn
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.metadata.flac.VorbisComment
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import java.util.Locale
import kotlin.math.min
import kotlin.math.pow

/**
 * ReplayGain loudness info from a song's tags (task 4.5, volume levelling).
 * Gains are in dB relative to the ReplayGain reference level; peaks are linear (1.0 = full scale).
 */
data class ReplayGain(
    val trackGainDb: Float? = null,
    val albumGainDb: Float? = null,
    val trackPeak: Float? = null,
    val albumPeak: Float? = null
)

/** Opus R128 gains are relative to -23 LUFS; ReplayGain's reference is about 5 dB louder. */
private const val R128_TO_REPLAYGAIN_DB = 5f

private fun parseDb(text: String): Float? =
    text.trim().lowercase(Locale.ROOT).removeSuffix("db").trim().removePrefix("+").toFloatOrNull()

/**
 * Reads the usual tag names (MP3 TXXX, FLAC/Ogg Vorbis comments, MP4 freeform "----" atoms and Opus
 * R128 gains). Returns null when the song has no loudness tags.
 */
fun parseReplayGainTags(tags: List<Pair<String, String>>): ReplayGain? {
    var result = ReplayGain()
    var found = false
    for ((rawKey, value) in tags) {
        val key = rawKey.trim().lowercase(Locale.ROOT)
        when (key) {
            "replaygain_track_gain" -> parseDb(value)?.let { result = result.copy(trackGainDb = it); found = true }
            "replaygain_album_gain" -> parseDb(value)?.let { result = result.copy(albumGainDb = it); found = true }
            "replaygain_track_peak" -> value.trim().toFloatOrNull()?.let { result = result.copy(trackPeak = it) }
            "replaygain_album_peak" -> value.trim().toFloatOrNull()?.let { result = result.copy(albumPeak = it) }
            // Opus: signed Q7.8 fixed point (value / 256 dB)
            "r128_track_gain" -> value.trim().toIntOrNull()?.let {
                if (result.trackGainDb == null) result = result.copy(trackGainDb = it / 256f + R128_TO_REPLAYGAIN_DB)
                found = true
            }
            "r128_album_gain" -> value.trim().toIntOrNull()?.let {
                if (result.albumGainDb == null) result = result.copy(albumGainDb = it / 256f + R128_TO_REPLAYGAIN_DB)
                found = true
            }
        }
    }
    return if (found) result else null
}

/**
 * Linear volume multiplier for a song: its track gain (album gain if that's all it has), lowered if
 * needed so the loudest peak can't clip. 1.0 when there's nothing to go on.
 */
fun levelingMultiplier(gain: ReplayGain?): Float {
    if (gain == null) return 1f
    val db = gain.trackGainDb ?: gain.albumGainDb ?: return 1f
    var linear = 10f.pow(db / 20f)
    val peak = gain.trackPeak ?: gain.albumPeak
    if (peak != null && peak > 0f) linear = min(linear, 1f / peak)
    return linear.coerceIn(0.1f, 4f)
}

/** The tag name/value pairs inside ExoPlayer's [Metadata] that can carry ReplayGain. */
@OptIn(UnstableApi::class)
fun Metadata.replayGainTagPairs(): List<Pair<String, String>> =
    (0 until length()).mapNotNull { index ->
        when (val entry = get(index)) {
            is TextInformationFrame -> entry.description?.let { desc -> entry.values.firstOrNull()?.let { desc to it } }
            is InternalFrame -> entry.description to entry.text
            is VorbisComment -> entry.key to entry.value
            else -> null
        }
    }
