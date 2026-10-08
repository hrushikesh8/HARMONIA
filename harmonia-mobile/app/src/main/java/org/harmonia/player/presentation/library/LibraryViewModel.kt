package org.harmonia.player.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.TrackRepository

enum class LibraryTab {
    TRACKS,
    ALBUMS,
    ARTISTS
}

enum class TrackSortOrder {
    TITLE_ASC,
    TITLE_DESC,
    ARTIST_ASC,
    DATE_ADDED_DESC,
    DURATION_DESC
}

enum class AlbumSortOrder {
    TITLE_ASC,
    ARTIST_ASC,
    YEAR_DESC
}

enum class ArtistSortOrder {
    NAME_ASC,
    NAME_DESC
}

data class LibraryUiState(
    val selectedTab: LibraryTab = LibraryTab.TRACKS,
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val trackSortOrder: TrackSortOrder = TrackSortOrder.TITLE_ASC,
    val albumSortOrder: AlbumSortOrder = AlbumSortOrder.TITLE_ASC,
    val artistSortOrder: ArtistSortOrder = ArtistSortOrder.NAME_ASC,
    val isLoading: Boolean = false
)

private data class CatalogData(
    val tracks: List<Track>,
    val albums: List<Album>,
    val artists: List<Artist>
)

private data class SortSettings(
    val tab: LibraryTab,
    val trackSort: TrackSortOrder,
    val albumSort: AlbumSortOrder,
    val artistSort: ArtistSortOrder
)

class LibraryViewModel(
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository(),
    private val albumRepository: AlbumRepository = ServiceLocator.getAlbumRepository(),
    private val artistRepository: ArtistRepository = ServiceLocator.getArtistRepository()
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(LibraryTab.TRACKS)
    private val _trackSortOrder = MutableStateFlow(TrackSortOrder.TITLE_ASC)
    private val _albumSortOrder = MutableStateFlow(AlbumSortOrder.TITLE_ASC)
    private val _artistSortOrder = MutableStateFlow(ArtistSortOrder.NAME_ASC)

    private val catalogFlow = combine(
        trackRepository.observeAllTracks(),
        albumRepository.observeAllAlbums(),
        artistRepository.observeAllArtists()
    ) { tracks, albums, artists ->
        CatalogData(tracks, albums, artists)
    }

    private val sortFlow = combine(
        _selectedTab,
        _trackSortOrder,
        _albumSortOrder,
        _artistSortOrder
    ) { tab, trackSort, albumSort, artistSort ->
        SortSettings(tab, trackSort, albumSort, artistSort)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        catalogFlow,
        sortFlow
    ) { catalog, sort ->
        val sortedTracks = when (sort.trackSort) {
            TrackSortOrder.TITLE_ASC -> catalog.tracks.sortedBy { it.title.lowercase() }
            TrackSortOrder.TITLE_DESC -> catalog.tracks.sortedByDescending { it.title.lowercase() }
            TrackSortOrder.ARTIST_ASC -> catalog.tracks.sortedBy { it.artistName?.lowercase() ?: "" }
            TrackSortOrder.DATE_ADDED_DESC -> catalog.tracks.sortedByDescending { it.createdAt }
            TrackSortOrder.DURATION_DESC -> catalog.tracks.sortedByDescending { it.durationSec }
        }

        val sortedAlbums = when (sort.albumSort) {
            AlbumSortOrder.TITLE_ASC -> catalog.albums.sortedBy { it.title.lowercase() }
            AlbumSortOrder.ARTIST_ASC -> catalog.albums.sortedBy { it.artistName?.lowercase() ?: "" }
            AlbumSortOrder.YEAR_DESC -> catalog.albums.sortedByDescending { it.year ?: 0 }
        }

        val sortedArtists = when (sort.artistSort) {
            ArtistSortOrder.NAME_ASC -> catalog.artists.sortedBy { it.name.lowercase() }
            ArtistSortOrder.NAME_DESC -> catalog.artists.sortedByDescending { it.name.lowercase() }
        }

        LibraryUiState(
            selectedTab = sort.tab,
            tracks = sortedTracks,
            albums = sortedAlbums,
            artists = sortedArtists,
            trackSortOrder = sort.trackSort,
            albumSortOrder = sort.albumSort,
            artistSortOrder = sort.artistSort,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(isLoading = true)
    )

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun setTrackSortOrder(order: TrackSortOrder) {
        _trackSortOrder.value = order
    }

    fun setAlbumSortOrder(order: AlbumSortOrder) {
        _albumSortOrder.value = order
    }

    fun setArtistSortOrder(order: ArtistSortOrder) {
        _artistSortOrder.value = order
    }
}
