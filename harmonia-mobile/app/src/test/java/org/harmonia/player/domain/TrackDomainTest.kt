package org.harmonia.player.domain

import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.StorageType
import org.harmonia.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDomainTest {

    @Test
    fun `track without local file has UNAVAILABLE status and is not available offline`() {
        val track = Track(
            id = "track-1",
            title = "Aqueous Transmission",
            durationSec = 466.0
        )

        assertEquals(LocalFileAvailability.UNAVAILABLE, track.availability)
        assertFalse(track.isAvailableOffline)
    }

    @Test
    fun `track with verified local file is available offline`() {
        val localFile = LocalFile(
            id = "file-1",
            trackId = "track-1",
            storageType = StorageType.INTERNAL_SYNC,
            fileUri = "/storage/emulated/0/Music/track-1.flac",
            fileSize = 45_000_000L,
            fileHash = "sha256-hash-abc",
            format = "FLAC",
            isLossless = true,
            isVerified = true
        )

        val track = Track(
            id = "track-1",
            title = "Aqueous Transmission",
            durationSec = 466.0,
            localFile = localFile
        )

        assertEquals(LocalFileAvailability.AVAILABLE_VERIFIED, track.availability)
        assertTrue(track.isAvailableOffline)
    }

    @Test
    fun `track with unverified local file has UNVERIFIED status`() {
        val localFile = LocalFile(
            id = "file-2",
            trackId = "track-2",
            storageType = StorageType.SAF_IMPORTED,
            fileUri = "content://media/external/audio/media/2",
            fileSize = 12_000_000L,
            fileHash = "unverified-hash",
            format = "MP3",
            isLossless = false,
            isVerified = false
        )

        val track = Track(
            id = "track-2",
            title = "Drive",
            durationSec = 232.0,
            localFile = localFile
        )

        assertEquals(LocalFileAvailability.UNVERIFIED, track.availability)
        assertTrue(track.isAvailableOffline)
    }

    @Test
    fun `explicit MISSING availability marks track offline unavailable`() {
        val track = Track(
            id = "track-3",
            title = "Pardon Me",
            durationSec = 224.0,
            availability = LocalFileAvailability.MISSING
        )

        assertEquals(LocalFileAvailability.MISSING, track.availability)
        assertFalse(track.isAvailableOffline)
    }
}
