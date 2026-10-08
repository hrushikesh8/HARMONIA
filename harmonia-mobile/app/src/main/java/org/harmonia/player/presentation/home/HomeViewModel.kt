package org.harmonia.player.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.ListeningEventRepository
import org.harmonia.player.domain.repository.PlaybackStateRepository
import org.harmonia.player.domain.repository.PlaylistRepository
import org.harmonia.player.domain.repository.TrackRepository

data class HomeUiState(
    val recentlyPlayed: List<Track> = emptyList(),
    val recentlyAdded: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val totalTrackCount: Int = 0,
    val resumeTrack: Track? = null,
    val playbackState: PlaybackState? = null,
    val isLoading: Boolean = false
)

private data class HomeCatalog(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>
)

private data class HomeActivity(
    val playlists: List<Playlist>,
    val recentEvents: List<ListeningEvent>,
    val playbackState: PlaybackState?
)

class HomeViewModel(
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository(),
    private val albumRepository: AlbumRepository = ServiceLocator.getAlbumRepository(),
    private val artistRepository: ArtistRepository = ServiceLocator.getArtistRepository(),
    private val playlistRepository: PlaylistRepository = ServiceLocator.getPlaylistRepository(),
    private val listeningEventRepository: ListeningEventRepository = ServiceLocator.getListeningEventRepository(),
    private val playbackStateRepository: PlaybackStateRepository = ServiceLocator.getPlaybackStateRepository()
) : ViewModel() {

    private val catalogFlow = combine(
        trackRepository.observeAllTracks(),
        albumRepository.observeAllAlbums(),
        artistRepository.observeAllArtists()
    ) { tracks, albums, artists ->
        HomeCatalog(tracks, albums, artists)
    }

    private val activityFlow = combine(
        playlistRepository.observeAllPlaylists(),
        listeningEventRepository.observeRecentEvents(30),
        playbackStateRepository.observePlaybackState()
    ) { playlists, events, state ->
        HomeActivity(playlists, events, state)
    }

    val uiState: StateFlow<HomeUiState> = combine(
        catalogFlow,
        activityFlow
    ) { catalog, activity ->
        val trackMap = catalog.tracks.associateBy { it.id }

        // Factual recently played from listening event history
        val recentlyPlayed = activity.recentEvents
            .mapNotNull { trackMap[it.trackId] }
            .distinctBy { it.id }
            .take(10)

        // Recently added tracks ordered by creation timestamp
        val recentlyAdded = catalog.tracks
            .sortedByDescending { it.createdAt }
            .take(10)

        val resumeTrack = activity.playbackState?.currentTrackId?.let { trackMap[it] }

        HomeUiState(
            recentlyPlayed = recentlyPlayed,
            recentlyAdded = recentlyAdded,
            albums = catalog.albums,
            artists = catalog.artists,
            playlists = activity.playlists,
            totalTrackCount = catalog.tracks.size,
            resumeTrack = resumeTrack,
            playbackState = activity.playbackState,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState(isLoading = true)
    )
}
