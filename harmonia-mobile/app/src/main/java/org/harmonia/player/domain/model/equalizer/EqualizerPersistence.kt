package org.harmonia.player.domain.model.equalizer

/**
 * The band layout this phone's equalizer last reported (real values from the device, saved so the
 * bands can be shown and adjusted while nothing is playing).
 */
data class EqualizerLayout(
    val centerFreqsHz: List<Int>,
    val minLevelMb: Short,
    val maxLevelMb: Short
)

/** A preset you saved yourself: a level (millibels) for each band, keyed by centre frequency. */
data class UserEqualizerPreset(
    val name: String,
    val levelsByFreqHz: Map<Int, Short>
)

/**
 * Plain-text encoding for equalizer settings so they fit in SharedPreferences without a JSON
 * dependency. Band levels are keyed by centre frequency, not band index, so they only re-apply to
 * matching bands if the output device changes.
 */
object EqualizerPersistenceCodec {

    fun encodeLevels(levels: Map<Int, Short>): String =
        levels.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    fun decodeLevels(text: String?): Map<Int, Short> =
        text.orEmpty().split(',').mapNotNull { pair ->
            val parts = pair.split(':')
            val freq = parts.getOrNull(0)?.trim()?.toIntOrNull()
            val level = parts.getOrNull(1)?.trim()?.toShortOrNull()
            if (parts.size == 2 && freq != null && level != null) freq to level else null
        }.toMap()

    fun encodeLayout(layout: EqualizerLayout): String =
        "${layout.minLevelMb}|${layout.maxLevelMb}|${layout.centerFreqsHz.joinToString(",")}"

    fun decodeLayout(text: String?): EqualizerLayout? {
        val parts = text?.split('|') ?: return null
        if (parts.size != 3) return null
        val min = parts[0].toShortOrNull() ?: return null
        val max = parts[1].toShortOrNull() ?: return null
        val freqs = parts[2].split(',').mapNotNull { it.trim().toIntOrNull() }
        if (freqs.isEmpty() || min >= max) return null
        return EqualizerLayout(freqs, min, max)
    }

    /** One preset per line: "name<TAB>levels". Names are cleaned of tabs and line breaks. */
    fun encodePresets(presets: List<UserEqualizerPreset>): String =
        presets.joinToString("\n") { "${cleanPresetName(it.name)}\t${encodeLevels(it.levelsByFreqHz)}" }

    fun decodePresets(text: String?): List<UserEqualizerPreset> =
        text.orEmpty().lines().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val levels = decodeLevels(line.substring(tab + 1))
            if (levels.isEmpty()) null else UserEqualizerPreset(line.substring(0, tab), levels)
        }

    fun cleanPresetName(name: String): String =
        name.replace(Regex("[\\t\\r\\n]+"), " ").trim().take(MAX_PRESET_NAME_LENGTH)

    const val MAX_PRESET_NAME_LENGTH = 30
}
