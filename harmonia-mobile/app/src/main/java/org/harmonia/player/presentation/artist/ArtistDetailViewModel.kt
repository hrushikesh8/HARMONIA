package org.harmonia.player.presentation.artist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.TrackPlayStats
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.ListeningEventRepository
import org.harmonia.player.domain.repository.TrackRepository

data class ArtistDetailUiState(
    val artist: Artist? = null,
    /** The artist's own albums, newest first. */
    val albums: List<Album> = emptyList(),
    /** Other artists' albums this artist is credited on (e.g. "feat."). */
    val appearsOn: List<Album> = emptyList(),
    /** Every song by or featuring the artist, in album order. */
    val tracks: List<Track> = emptyList(),
    /** Most played songs (falls back to album order when nothing has been played yet). */
    val topSongs: List<Track> = emptyList(),
    val totalDurationSec: Double = 0.0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

internal const val ARTIST_TOP_SONGS = 5

/**
 * Artist page state. Follows the library and listening history live, so new songs, a rescan or
 * fresh plays show up without leaving the page.
 */
class ArtistDetailViewModel(
    private val artistRepository: ArtistRepository = ServiceLocator.getArtistRepository(),
    private val albumRepository: AlbumRepository = ServiceLocator.getAlbumRepository(),
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository(),
    private val listeningEventRepository: ListeningEventRepository? =
        runCatching { ServiceLocator.getListeningEventRepository() }.getOrNull()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ArtistDetailUiState())
    val uiState: StateFlow<ArtistDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var currentArtistId: String? = null

    fun loadArtist(artistId: String) {
        if (artistId == currentArtistId && observeJob?.isActive == true) return
        currentArtistId = artistId
        observeJob?.cancel()
        _uiState.value = ArtistDetailUiState(isLoading = true)

        val playStats: Flow<List<TrackPlayStats>> =
            listeningEventRepository?.observeTrackPlayStats(recentSince = 0L) ?: flowOf(emptyList())

        observeJob = viewModelScope.launch {
            combine(
                artistRepository.observeAllArtists().map { artists -> artists.firstOrNull { it.id == artistId } },
                albumRepository.observeAllAlbums(),
                trackRepository.observeAllTracks(),
                playStats
            ) { artist, allAlbums, allTracks, stats ->
                buildArtistState(artistId, artist, allAlbums, allTracks, stats)
            }
                .catch { error ->
                    emit(ArtistDetailUiState(isLoading = false, errorMessage = error.message ?: "Something went wrong"))
                }
                .collect { _uiState.value = it }
        }
    }

    fun retry() {
        val id = currentArtistId ?: return
        observeJob?.cancel()
        observeJob = null
        currentArtistId = null
        loadArtist(id)
    }
}

internal fun buildArtistState(
    artistId: String,
    artist: Artist?,
    allAlbums: List<Album>,
    allTracks: List<Track>,
    stats: List<TrackPlayStats>
): ArtistDetailUiState {
    val albums = allAlbums
        .filter { it.artistId == artistId }
        .distinctBy { it.id }
        .sortedWith(compareByDescending<Album> { it.year ?: 0 }.thenBy { it.title.lowercase() })

    val ownTracks = allTracks.filter { it.artistId == artistId }
    // Multi-artist credits: "A feat. B" also belongs on B's page.
    val artistName = artist?.name?.lowercase()?.trim()
    val featuredTracks = if (artistName == null) emptyList() else allTracks.filter { track ->
        track.artistId != artistId && creditedArtistNames(track.artistName).any { it == artistName }
    }
    val albumOrder = albums.withIndex().associate { (index, album) -> album.id to index }
    val tracks = (ownTracks + featuredTracks)
        .distinctBy { it.id }
        .sortedWith(
            compareBy<Track> { albumOrder[it.albumId] ?: Int.MAX_VALUE }
                .thenBy { it.discNumber }
                .thenBy { it.trackNumber ?: Int.MAX_VALUE }
                .thenBy { it.title.lowercase() }
        )

    val ownAlbumIds = albums.map { it.id }.toSet()
    val appearsOnIds = featuredTracks.mapNotNull { it.albumId }.filterNot { it in ownAlbumIds }.toSet()
    val appearsOn = allAlbums.filter { it.id in appearsOnIds }.distinctBy { it.id }

    return ArtistDetailUiState(
        artist = artist,
        albums = albums,
        appearsOn = appearsOn,
        tracks = tracks,
        topSongs = topSongsFor(tracks, stats),
        totalDurationSec = tracks.sumOf { it.durationSec },
        isLoading = false
    )
}

/** Most played first; unplayed songs keep album order. Always up to [ARTIST_TOP_SONGS] songs. */
internal fun topSongsFor(tracks: List<Track>, stats: List<TrackPlayStats>): List<Track> {
    val plays = stats.associate { it.trackId to it.totalPlays }
    return tracks
        .withIndex()
        .sortedWith(compareByDescending<IndexedValue<Track>> { plays[it.value.id] ?: 0 }.thenBy { it.index })
        .map { it.value }
        .take(ARTIST_TOP_SONGS)
}

private val ARTIST_CREDIT_SEPARATORS =
    Regex("""\s*(?:,|;|&|/|\bfeat\.?(?=\s)|\bft\.?(?=\s)|\bfeaturing\b)\s*""", RegexOption.IGNORE_CASE)

/**
 * Splits a track's artist field into individual lower-cased credits, e.g.
 * "Daft Punk feat. Pharrell Williams" -> ["daft punk", "pharrell williams"].
 * Names must match a whole credit, so "Yes" does not match "Yesterday's Band".
 */
internal fun creditedArtistNames(artistField: String?): List<String> =
    artistField.orEmpty()
        .split(ARTIST_CREDIT_SEPARATORS)
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
