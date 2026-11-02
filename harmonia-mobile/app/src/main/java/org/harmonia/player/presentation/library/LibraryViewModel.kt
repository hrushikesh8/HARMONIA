package org.harmonia.player.presentation.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.StorageSourceRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.domain.scanner.MediaReconciliationEngine

enum class LibraryTab {
    TRACKS,
    ALBUMS,
    ARTISTS,
    FOLDERS,
    FAVORITES
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
    val isLoading: Boolean = false,
    val permissionState: StoragePermissionState = StoragePermissionState.GRANTED,
    val isScanning: Boolean = false,
    val scanErrorMessage: String? = null,
    val hasCompletedInitialScan: Boolean = false,
    val unmountedSourceIds: Set<String> = emptySet(),
    val disabledSourceIds: Set<String> = emptySet()
) {
    val isPermissionRequired: Boolean
        get() = permissionState != StoragePermissionState.GRANTED

    val isScanFailed: Boolean
        get() = scanErrorMessage != null && !isScanning

    val isLibraryEmpty: Boolean
        get() = tracks.isEmpty() && !isLoading && !isScanning && !isPermissionRequired && !isScanFailed
}

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

private data class ScanStatus(
    val permissionState: StoragePermissionState,
    val isScanning: Boolean,
    val scanErrorMessage: String?,
    val hasCompletedInitialScan: Boolean
)

private data class SourceAvailability(
    val unmountedIds: Set<String>,
    val disabledIds: Set<String>
)

class LibraryViewModel(
    private val trackRepository: TrackRepository = ServiceLocator.getTrackRepository(),
    private val albumRepository: AlbumRepository = ServiceLocator.getAlbumRepository(),
    private val artistRepository: ArtistRepository = ServiceLocator.getArtistRepository(),
    private val storagePermissionChecker: StoragePermissionChecker? = null,
    private val mediaReconciliationEngine: MediaReconciliationEngine? = null,
    private val storageSourceRepository: StorageSourceRepository? = null,
    private val storageVolumeLifecycleCoordinator: StorageVolumeLifecycleCoordinator? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val permissionChecker: StoragePermissionChecker?
        get() = storagePermissionChecker ?: runCatching { ServiceLocator.getStoragePermissionChecker() }.getOrNull()

    private val reconciliationEngine: MediaReconciliationEngine?
        get() = mediaReconciliationEngine ?: runCatching { ServiceLocator.getMediaReconciliationEngine() }.getOrNull()

    private val sourceRepository: StorageSourceRepository?
        get() = storageSourceRepository ?: runCatching { ServiceLocator.getStorageSourceRepository() }.getOrNull()

    private val coordinator: StorageVolumeLifecycleCoordinator?
        get() = storageVolumeLifecycleCoordinator ?: runCatching { ServiceLocator.getStorageVolumeLifecycleCoordinator() }.getOrNull()

    private val _selectedTab = MutableStateFlow(LibraryTab.TRACKS)
    private val _trackSortOrder = MutableStateFlow(TrackSortOrder.TITLE_ASC)
    private val _albumSortOrder = MutableStateFlow(AlbumSortOrder.TITLE_ASC)
    private val _artistSortOrder = MutableStateFlow(ArtistSortOrder.NAME_ASC)

    private val _permissionState = MutableStateFlow(
        storagePermissionChecker?.getPermissionState() ?: runCatching {
            ServiceLocator.getStoragePermissionChecker().getPermissionState()
        }.getOrDefault(StoragePermissionState.GRANTED)
    )
    private val _isScanning = MutableStateFlow(false)
    private val _scanErrorMessage = MutableStateFlow<String?>(null)
    private val _hasCompletedInitialScan = MutableStateFlow(false)

    private var scanJob: Job? = null

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

    private val scanStatusFlow = combine(
        _permissionState,
        _isScanning,
        _scanErrorMessage,
        _hasCompletedInitialScan
    ) { perm, scanning, error, completed ->
        ScanStatus(perm, scanning, error, completed)
    }

    private val runtimeStatesFlow: Flow<Map<String, SourceRuntimeState>> =
        coordinator?.runtimeStates ?: flowOf(emptyMap())

    private val storageSourcesFlow: Flow<List<StorageSource>> =
        sourceRepository?.observeAllSources() ?: flowOf(emptyList())

    private val sourceAvailabilityFlow: Flow<SourceAvailability> = combine(
        storageSourcesFlow,
        runtimeStatesFlow
    ) { sources, runtimeStates ->
        // Deliberately disabled by user configuration
        val disabled = sources.filter { !it.isAvailable }.map { it.id }.toSet()
        val sourcesById = sources.associateBy { it.id }

        // Physical unmount strictly when isMounted == false (excluding disabled sources and isolating permission loss)
        val unmounted = runtimeStates.filter { (id, state) ->
            if (id in disabled) return@filter false
            val source = sourcesById[id]
            if (source != null && !source.isRemovable) return@filter false
            !state.isMounted && state.hasPermission
        }.keys

        SourceAvailability(unmounted, disabled)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        catalogFlow,
        sortFlow,
        scanStatusFlow,
        sourceAvailabilityFlow
    ) { catalog, sort, scan, availability ->
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
            isLoading = false,
            permissionState = scan.permissionState,
            isScanning = scan.isScanning,
            scanErrorMessage = scan.scanErrorMessage,
            hasCompletedInitialScan = scan.hasCompletedInitialScan,
            unmountedSourceIds = availability.unmountedIds,
            disabledSourceIds = availability.disabledIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LibraryUiState(isLoading = true)
    )

    init {
        val initialPerm = permissionChecker?.getPermissionState() ?: StoragePermissionState.GRANTED
        _permissionState.value = initialPerm
        if (initialPerm == StoragePermissionState.GRANTED) {
            triggerScan()
        }
    }

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

    fun onPermissionResult(granted: Boolean, canRequestAgain: Boolean = true) {
        if (granted) {
            _permissionState.value = StoragePermissionState.GRANTED
            _scanErrorMessage.value = null
            triggerScan()
        } else {
            _permissionState.value = if (canRequestAgain) {
                StoragePermissionState.DENIED
            } else {
                StoragePermissionState.SETTINGS_REQUIRED
            }
        }
    }

    fun refreshPermissionState() {
        val current = permissionChecker?.getPermissionState() ?: return
        val previous = _permissionState.value
        _permissionState.value = current
        if (current == StoragePermissionState.GRANTED && previous != StoragePermissionState.GRANTED) {
            _scanErrorMessage.value = null
            triggerScan()
        }
    }

    fun retryScan() {
        _scanErrorMessage.value = null
        triggerScan()
    }

    fun triggerScan() {
        if (_permissionState.value != StoragePermissionState.GRANTED) {
            return
        }
        if (scanJob?.isActive == true) {
            return
        }
        val engine = reconciliationEngine ?: return
        val repo = sourceRepository ?: return

        scanJob = viewModelScope.launch(ioDispatcher) {
            _isScanning.value = true
            _scanErrorMessage.value = null
            try {
                var primarySource = repo.getSourceById(StorageSource.MediaStorePrimary.ID)
                if (primarySource == null) {
                    val newSource = StorageSource.MediaStorePrimary()
                    repo.saveSource(newSource)
                    primarySource = newSource
                }

                if (primarySource.isAvailable) {
                    engine.scanSource(primarySource, ScanMode.INCREMENTAL).collect { progress ->
                        when (progress.status) {
                            ScanProgressStatus.FAILED -> {
                                _scanErrorMessage.value = progress.errorMessage ?: "Scan failed"
                            }
                            ScanProgressStatus.PERMISSION_REQUIRED -> {
                                _permissionState.value = StoragePermissionState.DENIED
                                _scanErrorMessage.value = progress.errorMessage ?: "Storage permission required"
                            }
                            else -> {
                                // Scanning or Completed
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                _scanErrorMessage.value = t.message ?: "Failed to scan media library"
            } finally {
                _isScanning.value = false
                _hasCompletedInitialScan.value = true
            }
        }
    }
}
