package org.harmonia.player.presentation.library

import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatFiltersTest {

    private fun track(id: String, format: String?) = Track(
        id = id,
        title = id,
        durationSec = 60.0,
        localFile = format?.let {
            LocalFile(
                id = "f$id", trackId = id, storageType = StorageType.MEDIA_STORE_PRIMARY,
                fileUri = "content://$id", fileSize = 1, fileHash = "h$id", format = it
            )
        }
    )

    @Test
    fun `chips come from the library, most common first`() {
        val tracks = listOf(
            track("1", "mp3"), track("2", "flac"), track("3", "MP3"),
            track("4", "opus"), track("5", "flac "), track("6", "mp3"), track("7", null)
        )
        val options = formatFilterOptions(tracks)
        assertEquals(listOf(ALL_FORMATS, "MP3", "FLAC", "OPUS"), options.map { it.key })
        assertEquals(listOf(7, 3, 2, 1), options.map { it.count })
        assertEquals("All", options.first().label)
    }

    @Test
    fun `no chips when the library has only one format`() {
        assertTrue(formatFilterOptions(listOf(track("1", "mp3"), track("2", "MP3"))).isEmpty())
        assertTrue(formatFilterOptions(emptyList()).isEmpty())
    }

    @Test
    fun `a format that is no longer there falls back to All`() {
        val options = formatFilterOptions(listOf(track("1", "mp3"), track("2", "ogg")))
        assertEquals("OGG", effectiveFormatFilter("OGG", options))
        assertEquals(ALL_FORMATS, effectiveFormatFilter("FLAC", options))
        assertEquals(ALL_FORMATS, effectiveFormatFilter("MP3", emptyList()))
    }

    @Test
    fun `matching ignores case and spaces`() {
        assertTrue(track("1", " flac").matchesFormat("FLAC"))
        assertTrue(track("2", null).matchesFormat(ALL_FORMATS))
        assertFalse(track("3", null).matchesFormat("MP3"))
        assertFalse(track("4", "mp3").matchesFormat("FLAC"))
    }
}
