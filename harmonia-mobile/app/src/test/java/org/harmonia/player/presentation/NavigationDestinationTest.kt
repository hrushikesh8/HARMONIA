package org.harmonia.player.presentation

import org.harmonia.player.presentation.navigation.HarmoniaNavDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NavigationDestinationTest {

    @Test
    fun `five top-level navigation destinations are distinct and identifiable`() {
        val topLevel: List<HarmoniaNavDestination> = listOf(
            HarmoniaNavDestination.Home,
            HarmoniaNavDestination.Library,
            HarmoniaNavDestination.Playlists,
            HarmoniaNavDestination.Search,
            HarmoniaNavDestination.Settings
        )

        assertEquals(5, topLevel.toSet().size)
    }

    @Test
    fun `detail destinations retain identity arguments`() {
        val album = HarmoniaNavDestination.AlbumDetail(albumId = "album-42")
        val artist = HarmoniaNavDestination.ArtistDetail(artistId = "artist-7")
        val playlist = HarmoniaNavDestination.PlaylistDetail(playlistId = "playlist-99")

        assertEquals("album-42", album.albumId)
        assertEquals("artist-7", artist.artistId)
        assertEquals("playlist-99", playlist.playlistId)

        assertNotEquals(album, HarmoniaNavDestination.AlbumDetail("album-43"))
    }
}
