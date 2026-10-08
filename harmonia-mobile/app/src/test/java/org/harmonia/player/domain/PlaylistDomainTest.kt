package org.harmonia.player.domain

import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.PlaylistItem
import org.harmonia.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistDomainTest {

    @Test
    fun `playlist domain model preserves core attributes`() {
        val playlist = Playlist(
            id = "pl-1",
            name = "Late Night Ambient",
            description = "Downtempo electronic and atmospheric pieces",
            artworkHash = "hash-1234",
            createdAt = 1710000000000L,
            updatedAt = 1710000500000L
        )

        assertEquals("pl-1", playlist.id)
        assertEquals("Late Night Ambient", playlist.name)
        assertEquals("Late Night Ambient", playlist.title)
        assertEquals("Downtempo electronic and atmospheric pieces", playlist.description)
        assertEquals("hash-1234", playlist.artworkHash)
        assertEquals(1710000000000L, playlist.createdAt)
        assertEquals(1710000500000L, playlist.updatedAt)
    }

    @Test
    fun `playlist items preserve deterministic position and track references`() {
        val trackA = Track(id = "track-a", title = "Strobe", durationSec = 637.0)
        val item1 = PlaylistItem(
            id = "pi-1",
            playlistId = "pl-1",
            trackId = "track-a",
            position = 0,
            addedAt = 100L,
            track = trackA
        )
        val item2 = PlaylistItem(
            id = "pi-2",
            playlistId = "pl-1",
            trackId = "track-b",
            position = 1,
            addedAt = 200L,
            track = null
        )

        assertEquals(0, item1.position)
        assertEquals("track-a", item1.trackId)
        assertEquals("Strobe", item1.track?.title)

        assertEquals(1, item2.position)
        assertEquals("track-b", item2.trackId)
        assertNull(item2.track)
    }
}
