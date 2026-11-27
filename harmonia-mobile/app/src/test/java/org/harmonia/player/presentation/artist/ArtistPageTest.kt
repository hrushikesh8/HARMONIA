package org.harmonia.player.presentation.artist

import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.TrackPlayStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistPageTest {

    private val artist = Artist(id = "ar1", name = "Nova")
    private val ownOld = Album(id = "old", title = "Old Album", artistId = "ar1", year = 2010)
    private val ownNew = Album(id = "new", title = "New Album", artistId = "ar1", year = 2020)
    private val guestAlbum = Album(id = "guest", title = "Friends", artistId = "ar2", artistName = "Other")

    private fun track(id: String, album: String, number: Int, artistId: String = "ar1", artistName: String = "Nova") =
        Track(id = id, title = id, albumId = album, trackNumber = number, artistId = artistId,
            artistName = artistName, durationSec = 100.0)

    private val tracks = listOf(
        track("old1", "old", 1),
        track("new2", "new", 2),
        track("new1", "new", 1),
        track("feat", "guest", 1, artistId = "ar2", artistName = "Other feat. Nova"),
        track("unrelated", "guest", 2, artistId = "ar2", artistName = "Other")
    )

    @Test
    fun `songs include features and follow newest album first`() {
        val state = buildArtistState("ar1", artist, listOf(ownOld, ownNew, guestAlbum), tracks, emptyList())
        assertEquals(listOf("new", "old"), state.albums.map { it.id })
        assertEquals(listOf("new1", "new2", "old1", "feat"), state.tracks.map { it.id })
    }

    @Test
    fun `albums the artist is only featured on appear under appears on`() {
        val state = buildArtistState("ar1", artist, listOf(ownOld, ownNew, guestAlbum), tracks, emptyList())
        assertEquals(listOf("guest"), state.appearsOn.map { it.id })
    }

    @Test
    fun `top songs are ranked by plays, unplayed songs keep album order`() {
        val stats = listOf(
            TrackPlayStats("old1", totalPlays = 9, recentPlays = 0, lastPlayedAt = 1),
            TrackPlayStats("feat", totalPlays = 4, recentPlays = 0, lastPlayedAt = 1)
        )
        val state = buildArtistState("ar1", artist, listOf(ownOld, ownNew, guestAlbum), tracks, stats)
        assertEquals(listOf("old1", "feat", "new1", "new2"), state.topSongs.map { it.id })
    }

    @Test
    fun `top songs are capped`() {
        val many = (1..9).map { track("t$it", "new", it) }
        assertEquals(ARTIST_TOP_SONGS, topSongsFor(many, emptyList()).size)
    }

    @Test
    fun `summary line reads naturally`() {
        assertEquals("2 albums · 5 songs", artistSummary(2, 5))
        assertEquals("1 song", artistSummary(0, 1))
    }
}
