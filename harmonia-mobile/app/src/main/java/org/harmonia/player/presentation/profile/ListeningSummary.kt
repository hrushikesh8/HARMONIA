package org.harmonia.player.presentation.profile

import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.ListeningEventType
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.TrackPlayStats
import org.harmonia.player.domain.model.personalization.RatedTrack

/** "This month" on the Your listening page means the last 30 days. */
internal const val LISTENING_WINDOW_MS = 30L * 24 * 60 * 60 * 1000

data class RecentPlay(val track: Track, val playedAt: Long)

data class ArtistPlays(val artistId: String?, val name: String, val plays: Int)

/** Everything the Your listening page shows, built purely from data already on the phone. */
data class ListeningSummary(
    val playsThisMonth: Int = 0,
    /** Approximate: each play counts the song's full length. */
    val minutesThisMonth: Int = 0,
    val topArtists: List<ArtistPlays> = emptyList(),
    val topSongs: List<Track> = emptyList(),
    val recentlyPlayed: List<RecentPlay> = emptyList(),
    val ratedTracks: List<RatedTrack> = emptyList()
)

internal fun buildListeningSummary(
    tracks: List<Track>,
    stats: List<TrackPlayStats>,
    recentEvents: List<ListeningEvent>,
    rated: List<RatedTrack>,
    topLimit: Int = 5,
    recentLimit: Int = 20
): ListeningSummary {
    val tracksById = tracks.associateBy { it.id }
    val recent = stats.filter { it.recentPlays > 0 }

    val playsThisMonth = recent.sumOf { it.recentPlays }
    val secondsThisMonth = recent.sumOf { stat -> (tracksById[stat.trackId]?.durationSec ?: 0.0) * stat.recentPlays }

    val topArtists = recent
        .mapNotNull { stat -> tracksById[stat.trackId]?.let { it to stat.recentPlays } }
        .filter { (track, _) -> !track.artistName.isNullOrBlank() }
        .groupBy { (track, _) -> track.artistName!!.trim().lowercase() }
        .map { (_, entries) ->
            val first = entries.first().first
            ArtistPlays(artistId = first.artistId, name = first.artistName!!.trim(), plays = entries.sumOf { it.second })
        }
        .sortedWith(compareByDescending<ArtistPlays> { it.plays }.thenBy { it.name.lowercase() })
        .take(topLimit)

    val topSongs = recent
        .sortedWith(compareByDescending<TrackPlayStats> { it.recentPlays }.thenByDescending { it.lastPlayedAt })
        .mapNotNull { tracksById[it.trackId] }
        .take(topLimit)

    // Song starts, newest first, without repeating the same song back to back.
    val recentlyPlayed = recentEvents
        .filter { it.eventType == ListeningEventType.STARTED }
        .sortedByDescending { it.timestamp }
        .mapNotNull { event -> tracksById[event.trackId]?.let { RecentPlay(it, event.timestamp) } }
        .fold(mutableListOf<RecentPlay>()) { acc, play ->
            if (acc.lastOrNull()?.track?.id != play.track.id) acc.add(play)
            acc
        }
        .take(recentLimit)

    return ListeningSummary(
        playsThisMonth = playsThisMonth,
        minutesThisMonth = (secondsThisMonth / 60).toInt(),
        topArtists = topArtists,
        topSongs = topSongs,
        recentlyPlayed = recentlyPlayed,
        ratedTracks = rated.sortedWith(compareByDescending<RatedTrack> { it.rating }.thenByDescending { it.ratedAt })
    )
}

/** "Just now", "12 min ago", "3 hr ago", "Yesterday", "5 days ago", then "over a week ago". */
internal fun formatTimeAgo(now: Long, then: Long): String {
    val minutes = ((now - then).coerceAtLeast(0) / 60_000)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        minutes < 24 * 60 -> "${minutes / 60} hr ago"
        minutes < 48 * 60 -> "Yesterday"
        minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)} days ago"
        else -> "Over a week ago"
    }
}
