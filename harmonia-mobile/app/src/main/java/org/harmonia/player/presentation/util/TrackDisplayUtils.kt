package org.harmonia.player.presentation.util

import org.harmonia.player.domain.model.Track
import java.util.Locale

/**
 * Display formatting utilities for Harmonia metadata presentation.
 * 
 * Rules:
 * 1. Never expose raw internal identifiers (e.g. UUIDs or raw 32/64-char hashes) as user-facing titles.
 * 2. Strip technical filename prefixes (e.g. "01 - ", "02_ ", "1. ") and common audio extensions (.mp3, .flac)
 *    when clean metadata is available.
 * 3. Always preserve the original filename / title as a safe fallback.
 * 4. Never fabricate or hallucinate metadata.
 */
object TrackDisplayUtils {

    private val PREFIX_PATTERN = Regex("""^(\d{1,3}[\s._-]+)""")
    private val AUDIO_EXTENSIONS = listOf(".flac", ".mp3", ".m4a", ".ogg", ".opus", ".wav", ".aac", ".alac", ".aiff")
    private val UUID_OR_HASH_PATTERN = Regex("""^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$|^[0-9a-fA-F]{32,64}$""")

    /**
     * Resolves the best user-facing title for a track.
     */
    fun formatDisplayTitle(track: Track): String {
        return formatDisplayTitle(track.title, track.localFile?.fileUri)
    }

    /**
     * Cleans raw title string from technical prefixes, audio extensions, and raw IDs.
     */
    fun formatDisplayTitle(rawTitle: String?, fileUriOrPath: String? = null): String {
        if (rawTitle.isNullOrBlank()) {
            return extractFallbackFromUri(fileUriOrPath) ?: "Unknown Track"
        }

        var title = rawTitle.trim()

        // Check if title is a raw UUID or SHA-256 hash
        if (UUID_OR_HASH_PATTERN.matches(title)) {
            val fromUri = extractFallbackFromUri(fileUriOrPath)
            if (!fromUri.isNullOrBlank() && !UUID_OR_HASH_PATTERN.matches(fromUri)) {
                title = fromUri
            }
        }

        // Strip known audio extensions if present at end of title
        for (ext in AUDIO_EXTENSIONS) {
            if (title.endsWith(ext, ignoreCase = true)) {
                title = title.substring(0, title.length - ext.length).trim()
                break
            }
        }

        // Strip leading track number prefixes (e.g. "01 - ", "02. ", "03_")
        val prefixMatch = PREFIX_PATTERN.find(title)
        if (prefixMatch != null) {
            val cleaned = title.substring(prefixMatch.range.last + 1).trim()
            if (cleaned.isNotBlank()) {
                title = cleaned
            }
        }

        return title.ifBlank { rawTitle.trim() }
    }

    /**
     * Formats secondary metadata string (Artist • Album • Audio Specs).
     */
    fun formatSubtitle(track: Track, includeSpecs: Boolean = false): String {
        val parts = mutableListOf<String>()
        val artist = track.artistName?.trim()
        val album = track.albumTitle?.trim()

        if (!artist.isNullOrBlank()) {
            parts.add(artist)
        }
        if (!album.isNullOrBlank()) {
            parts.add(album)
        }

        if (includeSpecs && track.localFile != null) {
            val file = track.localFile
            val formatStr = file.format.uppercase(Locale.ROOT)
            val resStr = when {
                file.bitDepth != null && file.sampleRate != null -> {
                    val khz = file.sampleRate / 1000.0
                    val sampleRateStr = if (khz % 1.0 == 0.0) "${khz.toInt()}kHz" else String.format(Locale.US, "%.1fkHz", khz)
                    "${file.bitDepth}-bit / $sampleRateStr"
                }
                file.isLossless -> "Lossless"
                file.bitrate != null -> "${file.bitrate} kbps"
                else -> null
            }
            if (resStr != null) {
                parts.add("$formatStr • $resStr")
            } else {
                parts.add(formatStr)
            }
        }

        return if (parts.isEmpty()) "Harmonia Local Audio" else parts.joinToString(" • ")
    }

    private fun extractFallbackFromUri(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        val decoded = try {
            java.net.URLDecoder.decode(uri, "UTF-8")
        } catch (_: Exception) {
            uri
        }
        val lastSegment = decoded.substringAfterLast('/').substringAfterLast('%').trim()
        if (lastSegment.isBlank()) return null

        var name = lastSegment
        for (ext in AUDIO_EXTENSIONS) {
            if (name.endsWith(ext, ignoreCase = true)) {
                name = name.substring(0, name.length - ext.length).trim()
                break
            }
        }
        return name.ifBlank { null }
    }
}
