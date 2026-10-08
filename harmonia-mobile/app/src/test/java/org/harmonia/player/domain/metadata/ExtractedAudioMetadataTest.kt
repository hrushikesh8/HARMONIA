package org.harmonia.player.domain.metadata

import org.harmonia.player.domain.model.metadata.ExtractedAudioMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractedAudioMetadataTest {

    @Test
    fun default_values_are_all_null() {
        val empty = ExtractedAudioMetadata()
        assertNull(empty.title)
        assertNull(empty.artist)
        assertNull(empty.album)
        assertNull(empty.albumArtist)
        assertNull(empty.genre)
        assertNull(empty.trackNumber)
        assertNull(empty.discNumber)
        assertNull(empty.year)
        assertNull(empty.durationSec)
        assertNull(empty.bitrateKbps)
        assertNull(empty.sampleRateHz)
        assertNull(empty.mimeType)
        assertNull(empty.artworkHash)
        assertNull(empty.rawArtworkBytes)
    }

    @Test
    fun equality_and_hashcode_handle_content_and_byte_arrays_correctly() {
        val bytes1 = byteArrayOf(1, 2, 3)
        val bytes2 = byteArrayOf(1, 2, 3)
        val bytes3 = byteArrayOf(1, 2, 4)

        val meta1 = ExtractedAudioMetadata(
            title = "Solar",
            artist = "Miles Davis",
            album = "Walkin'",
            trackNumber = 1,
            discNumber = 1,
            year = 1957,
            durationSec = 280.0,
            bitrateKbps = 320,
            sampleRateHz = 44100,
            mimeType = "audio/mpeg",
            artworkHash = "a1b2c3d4",
            rawArtworkBytes = bytes1
        )

        val meta2 = ExtractedAudioMetadata(
            title = "Solar",
            artist = "Miles Davis",
            album = "Walkin'",
            trackNumber = 1,
            discNumber = 1,
            year = 1957,
            durationSec = 280.0,
            bitrateKbps = 320,
            sampleRateHz = 44100,
            mimeType = "audio/mpeg",
            artworkHash = "a1b2c3d4",
            rawArtworkBytes = bytes2
        )

        val meta3 = meta1.copy(rawArtworkBytes = bytes3)
        val meta4 = meta1.copy(title = "Tune Up")

        assertEquals(meta1, meta2)
        assertEquals(meta1.hashCode(), meta2.hashCode())

        assertNotEquals(meta1, meta3)
        assertNotEquals(meta1, meta4)
    }
}
