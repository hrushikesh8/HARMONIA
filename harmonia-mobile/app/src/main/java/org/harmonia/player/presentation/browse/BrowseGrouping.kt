package org.harmonia.player.presentation.browse

import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Track
import java.util.Calendar
import java.util.Locale

/*
 * Browse by genre and year (task 4.2). Genres and years come from the songs' own tags, which are
 * often messy, so they are cleaned up here when grouping rather than changed in the library.
 */

/** A genre with its songs (in title order) and how many albums they come from. */
data class GenreGroup(
    val name: String,
    val tracks: List<Track>,
    val albumCount: Int,
    val artworkHash: String?
)

data class YearGroup(val year: Int, val tracks: List<Track>)

/** A decade (e.g. 1990 for the 1990s) with its years, newest first. */
data class DecadeGroup(val decade: Int, val years: List<YearGroup>) {
    val label: String get() = decadeLabel(decade)
    val tracks: List<Track> get() = years.flatMap { it.tracks }
    val trackCount: Int get() = years.sumOf { it.tracks.size }
}

fun decadeLabel(decade: Int): String = "${decade}s"

/** The 80 standard ID3v1 genres, which old files store as a number like "17" or "(17)". */
private val ID3V1_GENRES = listOf(
    "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge", "Hip-Hop", "Jazz", "Metal",
    "New Age", "Oldies", "Other", "Pop", "R&B", "Rap", "Reggae", "Rock", "Techno", "Industrial",
    "Alternative", "Ska", "Death Metal", "Pranks", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal", "Jazz+Funk",
    "Fusion", "Trance", "Classical", "Instrumental", "Acid", "House", "Game", "Sound Clip", "Gospel", "Noise",
    "AlternRock", "Bass", "Soul", "Punk", "Space", "Meditative", "Instrumental Pop", "Instrumental Rock", "Ethnic", "Gothic",
    "Darkwave", "Techno-Industrial", "Electronic", "Pop-Folk", "Eurodance", "Dream", "Southern Rock", "Comedy", "Cult", "Gangsta",
    "Top 40", "Christian Rap", "Pop/Funk", "Jungle", "Native American", "Cabaret", "New Wave", "Psychedelic", "Rave", "Showtunes",
    "Trailer", "Lo-Fi", "Tribal", "Acid Punk", "Acid Jazz", "Polka", "Retro", "Musical", "Rock & Roll", "Hard Rock"
)

private val NUMERIC_GENRE = Regex("""^\(?(\d{1,3})\)?$""")

/** Placeholder genres that mean "no genre". */
private val NOT_A_GENRE = setOf("", "unknown", "other", "genre", "none", "misc", "<unknown>")

/**
 * The genre names in one tag value: "Rock; Pop", "Rock/Pop" and "Rock, Pop" are two genres, and
 * numeric ID3v1 codes become names. Names that contain "/" as part of the name ("Pop/Funk",
 * "AC/DC"-style) are rare in genre tags and are split too; "R&B" and "Rock & Roll" are kept.
 */
fun genreNames(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(';', '/', ',', '|', '\u0000')
        .map { it.trim() }
        .map { part ->
            NUMERIC_GENRE.matchEntire(part)?.groupValues?.get(1)?.toIntOrNull()
                ?.let { ID3V1_GENRES.getOrNull(it) } ?: part
        }
        .filter { it.lowercase(Locale.ROOT) !in NOT_A_GENRE }
        .distinctBy { it.lowercase(Locale.ROOT) }
}

/**
 * Songs grouped by genre, alphabetical. A song tagged "Rock; Pop" appears under both. Different
 * spellings ("rock", "Rock") merge, shown with the spelling most songs use.
 */
fun groupByGenre(tracks: List<Track>): List<GenreGroup> {
    val byKey = LinkedHashMap<String, MutableList<Pair<String, Track>>>()
    tracks.forEach { track ->
        genreNames(track.genre).forEach { name ->
            byKey.getOrPut(name.lowercase(Locale.ROOT)) { mutableListOf() }.add(name to track)
        }
    }
    return byKey.values.map { entries ->
        val name = displayGenreName(entries.groupingBy { it.first }.eachCount())
        val songs = entries.map { it.second }.distinctBy { it.id }.sortedBy { it.title.lowercase(Locale.ROOT) }
        GenreGroup(
            name = name,
            tracks = songs,
            albumCount = songs.mapNotNull { it.albumId }.distinct().size,
            artworkHash = songs.firstNotNullOfOrNull { it.artworkHash ?: it.localFile?.fileHash }
        )
    }.sortedBy { it.name.lowercase(Locale.ROOT) }
}

/**
 * The spelling to show for a genre: the one most songs use; on a tie, a capitalised one ("Rock"
 * over "rock"). A genre only ever written in lowercase is capitalised ("hip hop" -> "Hip Hop").
 */
internal fun displayGenreName(spellingCounts: Map<String, Int>): String {
    val best = spellingCounts.entries
        .maxWith(compareBy<Map.Entry<String, Int>>({ it.value }, { it.key.firstOrNull()?.isUpperCase() == true }))
        .key
    return if (best == best.lowercase(Locale.ROOT)) {
        best.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }
    } else {
        best
    }
}

/** Albums (from [albums]) that have at least one of [tracks], most songs first. */
fun albumsFor(tracks: List<Track>, albums: List<Album>): List<Album> {
    val counts = tracks.mapNotNull { it.albumId }.groupingBy { it }.eachCount()
    return albums.filter { it.id in counts }.sortedByDescending { counts[it.id] ?: 0 }
}

private fun plausibleYear(year: Int?): Int? {
    val latest = Calendar.getInstance().get(Calendar.YEAR) + 1
    return year?.takeIf { it in 1900..latest }
}

/**
 * Songs grouped into decades and years, newest first. A song without its own year uses its
 * album's year; songs with neither are left out.
 */
fun groupByDecade(tracks: List<Track>, albums: List<Album>): List<DecadeGroup> {
    val albumYear = albums.associate { it.id to it.year }
    val byYear = tracks.mapNotNull { track ->
        val year = plausibleYear(track.year) ?: plausibleYear(track.albumId?.let { albumYear[it] })
        year?.let { it to track }
    }.groupBy({ it.first }, { it.second })

    return byYear.entries
        .groupBy { (year, _) -> year / 10 * 10 }
        .map { (decade, years) ->
            DecadeGroup(
                decade = decade,
                years = years.sortedByDescending { it.key }.map { (year, songs) ->
                    YearGroup(
                        year = year,
                        tracks = songs.sortedWith(
                            compareBy<Track>({ it.albumTitle?.lowercase(Locale.ROOT) ?: "" }, { it.discNumber }, { it.trackNumber ?: 0 })
                        )
                    )
                }
            )
        }
        .sortedByDescending { it.decade }
}

/** "12 songs · 3 albums" */
fun genreSubtitle(group: GenreGroup): String {
    val songs = if (group.tracks.size == 1) "1 song" else "${group.tracks.size} songs"
    return when (group.albumCount) {
        0 -> songs
        1 -> "$songs · 1 album"
        else -> "$songs · ${group.albumCount} albums"
    }
}

fun songCount(count: Int): String = if (count == 1) "1 song" else "$count songs"
