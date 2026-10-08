package org.harmonia.player.data.local.scanner

import java.util.Locale

/**
 * Filter utility for determining if a media item is an audio format supported by Harmonia Mobile.
 */
object AudioFileFilter {

    private val SUPPORTED_AUDIO_EXTENSIONS = setOf(
        "mp3",
        "flac",
        "m4a",
        "aac",
        "wav",
        "ogg",
        "opus"
    )

    private val SUPPORTED_MIME_PREFIXES = listOf(
        "audio/"
    )

    /**
     * Checks if the given MIME type or file name/path represents a supported audio format.
     */
    fun isSupportedAudio(displayName: String?, mimeType: String?): Boolean {
        if (!mimeType.isNullOrBlank()) {
            val lowerMime = mimeType.lowercase(Locale.ROOT).trim()
            if (SUPPORTED_MIME_PREFIXES.any { lowerMime.startsWith(it) }) {
                return true
            }
        }

        if (!displayName.isNullOrBlank()) {
            val extension = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT).trim()
            if (extension in SUPPORTED_AUDIO_EXTENSIONS) {
                return true
            }
        }

        return false
    }

    /**
     * Extracts normalized format name (e.g. "mp3", "flac") from file name or MIME type.
     */
    fun extractFormat(displayName: String?, mimeType: String?): String {
        if (!displayName.isNullOrBlank()) {
            val ext = displayName.substringAfterLast('.', "").lowercase(Locale.ROOT).trim()
            if (ext.isNotBlank()) return ext
        }

        if (!mimeType.isNullOrBlank()) {
            val sub = mimeType.substringAfter('/', "").lowercase(Locale.ROOT).trim()
            if (sub.isNotBlank()) return sub
        }

        return "unknown"
    }
}
