package org.harmonia.player.data.local.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFileFilterTest {

    @Test
    fun is_supported_audio_accepts_common_formats_by_mime_and_extension() {
        // Supported extensions
        assertTrue(AudioFileFilter.isSupportedAudio("track.mp3", null))
        assertTrue(AudioFileFilter.isSupportedAudio("track.FLAC", null))
        assertTrue(AudioFileFilter.isSupportedAudio("track.m4a", null))
        assertTrue(AudioFileFilter.isSupportedAudio("track.aac", null))
        assertTrue(AudioFileFilter.isSupportedAudio("track.wav", null))
        assertTrue(AudioFileFilter.isSupportedAudio("track.ogg", null))
        assertTrue(AudioFileFilter.isSupportedAudio("track.opus", null))

        // Supported MIME types
        assertTrue(AudioFileFilter.isSupportedAudio("unknown_file", "audio/mpeg"))
        assertTrue(AudioFileFilter.isSupportedAudio("unknown_file", "audio/flac"))
        assertTrue(AudioFileFilter.isSupportedAudio("unknown_file", "audio/mp4"))
        assertTrue(AudioFileFilter.isSupportedAudio("unknown_file", "AUDIO/WAV"))

        // Unsupported files
        assertFalse(AudioFileFilter.isSupportedAudio("cover.jpg", "image/jpeg"))
        assertFalse(AudioFileFilter.isSupportedAudio("lyrics.lrc", "text/plain"))
        assertFalse(AudioFileFilter.isSupportedAudio("video.mp4", "video/mp4"))
        assertFalse(AudioFileFilter.isSupportedAudio("document.pdf", "application/pdf"))
        assertFalse(AudioFileFilter.isSupportedAudio(null, null))
        assertFalse(AudioFileFilter.isSupportedAudio("", ""))
    }

    @Test
    fun extract_format_returns_correct_extension_or_mime_subtype() {
        assertEquals("mp3", AudioFileFilter.extractFormat("song.mp3", "audio/mpeg"))
        assertEquals("flac", AudioFileFilter.extractFormat("track.flac", null))
        assertEquals("mpeg", AudioFileFilter.extractFormat(null, "audio/mpeg"))
        assertEquals("unknown", AudioFileFilter.extractFormat(null, null))
    }
}
