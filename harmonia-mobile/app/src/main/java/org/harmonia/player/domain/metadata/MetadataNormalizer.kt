package org.harmonia.player.domain.metadata

import java.security.MessageDigest
import java.text.Normalizer

/**
 * Pure JVM metadata normalization utilities for Harmonia Mobile.
 * Completely decoupled from Android platform types.
 *
 * Adheres strictly to M4 architecture rules:
 * 1. Factual textual representation (Unicode NFC normalization, whitespace trimming).
 * 2. Empty or whitespace-only strings become null.
 * 3. Bounded text lengths to prevent unbounded memory allocation from corrupt tags.
 * 4. Safe compound numeric parsing (track "4/12" -> 4, disc "1/2" -> 1).
 * 5. Year parsing from ISO dates or year strings.
 * 6. Preserves composite multi-artist strings as-is (no premature artist splitting).
 * 7. Never infers, invents, or fabricates missing metadata.
 */
object MetadataNormalizer {

    private const val DEFAULT_MAX_TEXT_LENGTH = 500
    private val YEAR_REGEX = Regex("""\b(18|19|20|21)\d{2}\b""")

    /**
     * Cleans and normalizes textual fields:
     * - Trims surrounding whitespace.
     * - Applies Unicode NFC normalization.
     * - Converts empty or blank strings to null.
     * - Caps maximum length to prevent unbounded tag memory consumption.
     */
    fun normalizeText(raw: String?, maxLength: Int = DEFAULT_MAX_TEXT_LENGTH): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val normalized = Normalizer.normalize(trimmed, Normalizer.Form.NFC)
        return if (normalized.length > maxLength) {
            normalized.substring(0, maxLength)
        } else {
            normalized
        }
    }

    /**
     * Parses track number from single or compound representations:
     * Examples: "4/12" -> 4, "4" -> 4, " 4/12 " -> 4, "4-12" -> 4.
     * Non-numeric or invalid values return null.
     */
    fun parseTrackNumber(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        if (clean.startsWith("-") || clean.startsWith("+")) return null
        val firstPart = clean.split('/', '-', ' ', ',').firstOrNull { it.isNotBlank() } ?: return null
        val parsed = firstPart.toIntOrNull() ?: return null
        return if (parsed in 1..9999) parsed else null
    }

    /**
     * Parses disc number from single or compound representations:
     * Examples: "1/2" -> 1, "1" -> 1, " 2 " -> 2.
     * Non-numeric or invalid values return null.
     */
    fun parseDiscNumber(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val clean = raw.trim()
        if (clean.startsWith("-") || clean.startsWith("+")) return null
        val firstPart = clean.split('/', '-', ' ', ',').firstOrNull { it.isNotBlank() } ?: return null
        val parsed = firstPart.toIntOrNull() ?: return null
        return if (parsed in 1..99) parsed else null
    }

    /**
     * Extracts a 4-digit calendar year from date or year strings.
     * Examples: "2022-05-17" -> 2022, "2001/01/01" -> 2001, "1998" -> 1998.
     * Returns null if no valid year is detected.
     */
    fun parseYear(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val match = YEAR_REGEX.find(raw) ?: return null
        val year = match.value.toIntOrNull() ?: return null
        return if (year in 1800..2100) year else null
    }

    /**
     * Parses duration from MediaMetadataRetriever millisecond string to seconds.
     * Returns null if non-numeric or non-positive.
     */
    fun parseDurationSec(durationMsRaw: String?): Double? {
        if (durationMsRaw.isNullOrBlank()) return null
        val ms = durationMsRaw.trim().toLongOrNull() ?: return null
        return if (ms > 0) ms / 1000.0 else null
    }

    /**
     * Parses bitrate string to kbps integer.
     * Handles bps (> 5000 bps -> / 1000) and already-kbps values safely.
     */
    fun parseBitrateKbps(bitrateRaw: String?): Int? {
        if (bitrateRaw.isNullOrBlank()) return null
        val bps = bitrateRaw.trim().toIntOrNull() ?: return null
        if (bps <= 0) return null
        return if (bps >= 5000) bps / 1000 else bps
    }

    /**
     * Parses sample rate string in Hz.
     * Validates realistic audio sample rate range (4000 Hz .. 384,000 Hz).
     */
    fun parseSampleRateHz(sampleRateRaw: String?): Int? {
        if (sampleRateRaw.isNullOrBlank()) return null
        val hz = sampleRateRaw.trim().toIntOrNull() ?: return null
        return if (hz in 4000..384000) hz else null
    }

    /**
     * Computes lowercase hex SHA-256 digest of raw artwork bytes.
     */
    fun computeArtworkHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return hash.joinToString("") { "%02x".format(it) }
    }
}
