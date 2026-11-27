package org.harmonia.player.presentation.library

import org.harmonia.player.domain.model.Track
import java.util.Locale

internal const val ALL_FORMATS = "ALL"

/** One format chip: [key] is what the filter matches on, [label] is what the chip shows. */
internal data class FormatFilterOption(val key: String, val label: String, val count: Int)

private fun Track.formatKey(): String? =
    localFile?.format?.trim()?.takeIf { it.isNotEmpty() }?.uppercase(Locale.ROOT)

/**
 * Chips built from the formats that are actually in [tracks] (MP3, FLAC, OGG, OPUS...), most
 * common first, after an "All" chip. Empty when there is only one format: a filter with a single
 * choice is just noise.
 */
internal fun formatFilterOptions(tracks: List<Track>): List<FormatFilterOption> {
    val counts = tracks.mapNotNull { it.formatKey() }.groupingBy { it }.eachCount()
    if (counts.size < 2) return emptyList()
    val formats = counts.entries
        .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
        .map { FormatFilterOption(key = it.key, label = it.key, count = it.value) }
    return listOf(FormatFilterOption(ALL_FORMATS, "All", tracks.size)) + formats
}

/** Falls back to "All" when the chosen format is no longer in the list (e.g. switching to Favorites). */
internal fun effectiveFormatFilter(selected: String, options: List<FormatFilterOption>): String =
    if (options.any { it.key == selected }) selected else ALL_FORMATS

internal fun Track.matchesFormat(filter: String): Boolean =
    filter == ALL_FORMATS || formatKey() == filter
