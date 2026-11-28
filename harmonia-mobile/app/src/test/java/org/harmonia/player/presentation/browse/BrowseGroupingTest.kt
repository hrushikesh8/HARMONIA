package org.harmonia.player.presentation.browse

import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowseGroupingTest {

    private fun track(id: String, genre: String? = null, year: Int? = null, album: String? = null, number: Int? = null) =
        Track(id = id, title = "Song $id", durationSec = 60.0, genre = genre, year = year, albumId = album, albumTitle = album, trackNumber = number)

    @Test
    fun `multi-genre tags split and numeric ID3 codes become names`() {
        assertEquals(listOf("Rock", "Pop"), genreNames("Rock; Pop"))
        assertEquals(listOf("Jazz", "Blues"), genreNames("Jazz/Blues"))
        assertEquals(listOf("Pop"), genreNames("(13)"))
        assertEquals(listOf("Rock"), genreNames("17"))
        assertEquals(listOf("R&B"), genreNames("R&B"))
        assertEquals(emptyList<String>(), genreNames("Unknown"))
        assertEquals(emptyList<String>(), genreNames(null))
        assertEquals(listOf("Rock"), genreNames("Rock; rock"))
    }

    @Test
    fun `songs group by genre, spellings merge and multi-genre songs appear twice`() {
        val genres = groupByGenre(
            listOf(
                track("1", "Rock", album = "A"),
                track("2", "rock", album = "A"),
                track("3", "Rock; Pop", album = "B"),
                track("4", "Jazz"),
                track("5", null)
            )
        )
        assertEquals(listOf("Jazz", "Pop", "Rock"), genres.map { it.name })
        val rock = genres.first { it.name == "Rock" }
        assertEquals(listOf("1", "2", "3"), rock.tracks.map { it.id })
        assertEquals(2, rock.albumCount)
        assertEquals("3 songs · 2 albums", genreSubtitle(rock))
        assertEquals("1 song", genreSubtitle(genres.first { it.name == "Jazz" }))
    }

    @Test
    fun `years group into decades newest first, using the album year as a fallback`() {
        val albums = listOf(Album(id = "OK", title = "OK", year = 1997))
        val decades = groupByDecade(
            listOf(
                track("1", year = 1994),
                track("2", year = null, album = "OK"),
                track("3", year = 2008),
                track("4", year = 1991),
                track("5", year = null),
                track("6", year = 3020) // nonsense year: left out
            ),
            albums
        )
        assertEquals(listOf(2000, 1990), decades.map { it.decade })
        assertEquals("1990s", decades[1].label)
        assertEquals(listOf(1997, 1994, 1991), decades[1].years.map { it.year })
        assertEquals(3, decades[1].trackCount)
    }

    @Test
    fun `genre name prefers the capitalised spelling and capitalises lowercase-only names`() {
        assertEquals("Rock", displayGenreName(mapOf("rock" to 1, "Rock" to 1)))
        assertEquals("Rock", displayGenreName(mapOf("rock" to 3)))
        assertEquals("Hip Hop", displayGenreName(mapOf("hip hop" to 2)))
        assertEquals("ROCK", displayGenreName(mapOf("ROCK" to 2, "Rock" to 1)))
    }

    @Test
    fun `albums for a set of songs, most songs first`() {
        val albums = listOf(Album(id = "A", title = "A"), Album(id = "B", title = "B"), Album(id = "C", title = "C"))
        val result = albumsFor(listOf(track("1", album = "B"), track("2", album = "B"), track("3", album = "A")), albums)
        assertEquals(listOf("B", "A"), result.map { it.id })
        assertTrue(albumsFor(emptyList(), albums).isEmpty())
    }
}
