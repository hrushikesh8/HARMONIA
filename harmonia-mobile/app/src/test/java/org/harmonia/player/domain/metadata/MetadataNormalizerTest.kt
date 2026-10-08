package org.harmonia.player.domain.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataNormalizerTest {

    @Test
    fun text_normalization_trims_whitespace_and_applies_nfc() {
        assertNull(MetadataNormalizer.normalizeText(null))
        assertNull(MetadataNormalizer.normalizeText(""))
        assertNull(MetadataNormalizer.normalizeText("   "))

        // Standard string
        assertEquals("Harmonia", MetadataNormalizer.normalizeText("  Harmonia  "))

        // Unicode decomposed form (e + acute accent -> é composed)
        val decomposed = "e\u0301" // 'e' followed by combining acute accent
        val normalized = MetadataNormalizer.normalizeText(decomposed)
        assertEquals("\u00e9", normalized)

        // Length bounding
        val longString = "A".repeat(600)
        val capped = MetadataNormalizer.normalizeText(longString, maxLength = 50)
        assertNotNull(capped)
        assertEquals(50, capped?.length)
    }

    @Test
    fun multi_artist_string_is_preserved_as_composite_without_splitting() {
        val compositeSemicolon = "Daft Punk; Pharrell Williams"
        assertEquals(compositeSemicolon, MetadataNormalizer.normalizeText(compositeSemicolon))

        val compositeSlash = "Queen / David Bowie"
        assertEquals(compositeSlash, MetadataNormalizer.normalizeText(compositeSlash))

        val compositeFeat = "Gorillaz feat. Del the Funky Homosapien"
        assertEquals(compositeFeat, MetadataNormalizer.normalizeText(compositeFeat))
    }

    @Test
    fun track_number_parsing_handles_single_compound_and_invalid_formats() {
        assertEquals(4, MetadataNormalizer.parseTrackNumber("4"))
        assertEquals(4, MetadataNormalizer.parseTrackNumber(" 4 "))
        assertEquals(4, MetadataNormalizer.parseTrackNumber("4/12"))
        assertEquals(4, MetadataNormalizer.parseTrackNumber(" 4/12 "))
        assertEquals(4, MetadataNormalizer.parseTrackNumber("4-12"))
        assertEquals(7, MetadataNormalizer.parseTrackNumber("07"))

        // Invalid / out of range
        assertNull(MetadataNormalizer.parseTrackNumber(null))
        assertNull(MetadataNormalizer.parseTrackNumber(""))
        assertNull(MetadataNormalizer.parseTrackNumber("   "))
        assertNull(MetadataNormalizer.parseTrackNumber("Unknown"))
        assertNull(MetadataNormalizer.parseTrackNumber("0"))
        assertNull(MetadataNormalizer.parseTrackNumber("-1"))
        assertNull(MetadataNormalizer.parseTrackNumber("100000"))
    }

    @Test
    fun disc_number_parsing_handles_single_compound_and_invalid_formats() {
        assertEquals(1, MetadataNormalizer.parseDiscNumber("1"))
        assertEquals(1, MetadataNormalizer.parseDiscNumber("1/2"))
        assertEquals(2, MetadataNormalizer.parseDiscNumber(" 2/3 "))
        assertEquals(2, MetadataNormalizer.parseDiscNumber("2"))

        // Invalid
        assertNull(MetadataNormalizer.parseDiscNumber(null))
        assertNull(MetadataNormalizer.parseDiscNumber(""))
        assertNull(MetadataNormalizer.parseDiscNumber("0"))
        assertNull(MetadataNormalizer.parseDiscNumber("100"))
    }

    @Test
    fun year_parsing_extracts_4_digit_calendar_year_safely() {
        assertEquals(2022, MetadataNormalizer.parseYear("2022"))
        assertEquals(2022, MetadataNormalizer.parseYear("2022-05-17"))
        assertEquals(2001, MetadataNormalizer.parseYear("2001/01/01"))
        assertEquals(1998, MetadataNormalizer.parseYear(" 1998 "))
        assertEquals(1973, MetadataNormalizer.parseYear("March 1973"))

        // Invalid
        assertNull(MetadataNormalizer.parseYear(null))
        assertNull(MetadataNormalizer.parseYear(""))
        assertNull(MetadataNormalizer.parseYear("invalid"))
        assertNull(MetadataNormalizer.parseYear("1750")) // Out of range (< 1800)
        assertNull(MetadataNormalizer.parseYear("2250")) // Out of range (> 2100)
    }

    @Test
    fun duration_parsing_converts_milliseconds_to_seconds() {
        assertEquals(3.5, MetadataNormalizer.parseDurationSec("3500") ?: 0.0, 0.001)
        assertEquals(180.0, MetadataNormalizer.parseDurationSec("180000") ?: 0.0, 0.001)

        assertNull(MetadataNormalizer.parseDurationSec(null))
        assertNull(MetadataNormalizer.parseDurationSec(""))
        assertNull(MetadataNormalizer.parseDurationSec("0"))
        assertNull(MetadataNormalizer.parseDurationSec("-500"))
        assertNull(MetadataNormalizer.parseDurationSec("abc"))
    }

    @Test
    fun bitrate_and_sample_rate_parsing_handle_bps_and_kbps() {
        // Bitrate in bps
        assertEquals(320, MetadataNormalizer.parseBitrateKbps("320000"))
        // Bitrate already in kbps
        assertEquals(256, MetadataNormalizer.parseBitrateKbps("256"))

        assertNull(MetadataNormalizer.parseBitrateKbps(null))
        assertNull(MetadataNormalizer.parseBitrateKbps("0"))
        assertNull(MetadataNormalizer.parseBitrateKbps("-1"))

        // Sample rate
        assertEquals(44100, MetadataNormalizer.parseSampleRateHz("44100"))
        assertEquals(96000, MetadataNormalizer.parseSampleRateHz("96000"))

        assertNull(MetadataNormalizer.parseSampleRateHz(null))
        assertNull(MetadataNormalizer.parseSampleRateHz("1000")) // < 4000 Hz
        assertNull(MetadataNormalizer.parseSampleRateHz("500000")) // > 384000 Hz
    }

    @Test
    fun artwork_hash_computation_produces_deterministic_sha256() {
        val emptyBytes = ByteArray(0)
        val emptyHash = MetadataNormalizer.computeArtworkHash(emptyBytes)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", emptyHash)

        val testBytes = "Harmonia".toByteArray(Charsets.UTF_8)
        val testHash = MetadataNormalizer.computeArtworkHash(testBytes)
        assertEquals(64, testHash.length)
        assertTrue(testHash.matches(Regex("^[a-f0-9]{64}$")))
    }
}
