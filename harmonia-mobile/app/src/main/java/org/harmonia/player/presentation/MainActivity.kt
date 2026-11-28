package org.harmonia.player.presentation

import android.os.Bundle
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.presentation.album.AlbumDetailScreen
import org.harmonia.player.presentation.artist.ArtistDetailScreen
import org.harmonia.player.presentation.carmode.CarModeScreen
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.AddToPlaylistDialog
import org.harmonia.player.presentation.components.TrackActionsSheet
import org.harmonia.player.presentation.home.HomeScreen
import org.harmonia.player.presentation.library.LibraryDestination
import org.harmonia.player.presentation.library.LibraryHomeScreen
import org.harmonia.player.presentation.library.LibraryScreen
import org.harmonia.player.presentation.library.LibraryTab
import org.harmonia.player.presentation.library.LibraryViewModel
import org.harmonia.player.presentation.navigation.HarmoniaNavDestination
import org.harmonia.player.presentation.playback.MiniPlayer
import org.harmonia.player.presentation.playback.NowPlayingSheet
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.playlists.PlaylistDetailScreen
import org.harmonia.player.presentation.playlists.PlaylistsScreen
import org.harmonia.player.presentation.playlists.PlaylistsViewModel
import org.harmonia.player.presentation.profile.ProfileHubScreen
import androidx.compose.runtime.DisposableEffect
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.presentation.search.SearchBrowseTarget
import org.harmonia.player.presentation.search.SearchScreen
import org.harmonia.player.presentation.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userPrefs = remember { ServiceLocator.getUserPreferencesRepository() }
            val themeMode by userPrefs.observeThemeMode().collectAsState(initial = userPrefs.getThemeMode())
            val isDarkTheme = themeMode.resolveDark(androidx.compose.foundation.isSystemInDarkTheme())
            val keepScreenOn by userPrefs.observeKeepScreenOn().collectAsState(initial = userPrefs.isKeepScreenOn())

            DisposableEffect(keepScreenOn) {
                if (keepScreenOn) {
                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
                onDispose {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            HarmoniaTheme(darkTheme = isDarkTheme) {
                MainAppContainer()
            }

            // Start the playback service only after the first frame is on screen, so building
            // the audio player doesn't fight app launch for the main thread.
            LaunchedEffect(Unit) {
                withFrameNanos { }
                ServiceLocator.getPlaybackPort().ensureConnected()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

/**
 * Bottom tabs. Each one is its own nested navigation graph, so it keeps its own back stack
 * (Apple Music style): open an album in Library, switch to Search and back, the album is still there.
 */
enum class NavigationTab(
    val title: String,
    val icon: ImageVector,
    val graph: HarmoniaNavDestination,
    val destination: HarmoniaNavDestination
) {
    HOME("Home", HarmoniaIcons.Home, HarmoniaNavDestination.HomeTab, HarmoniaNavDestination.Home),
    SEARCH("Search", HarmoniaIcons.Search, HarmoniaNavDestination.SearchTab, HarmoniaNavDestination.Search),
    LIBRARY("Library", HarmoniaIcons.Library, HarmoniaNavDestination.LibraryTab, HarmoniaNavDestination.Library)
}

/** The tab a back stack belongs to: the newest entry that sits inside one of the tab graphs. */
private fun tabFor(backStack: List<NavBackStackEntry>): NavigationTab {
    for (entry in backStack.asReversed()) {
        val tab = NavigationTab.entries.firstOrNull { tab ->
            entry.destination.hierarchy.any { it.hasRoute(tab.graph::class) }
        }
        if (tab != null) return tab
    }
    return NavigationTab.HOME
}

private const val NAV_FADE_MS = 180

@Composable
fun MainAppContainer(
    playbackViewModel: PlaybackViewModel = viewModel(),
    personalizationViewModel: PersonalizationViewModel = viewModel(),
    playlistsViewModel: PlaylistsViewModel = viewModel(),
    libraryViewModel: LibraryViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStack.collectAsState()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentDestination = currentEntry?.destination
    val selectedTab = tabFor(backStack)
    val isCarMode = currentDestination?.hasRoute(HarmoniaNavDestination.CarMode::class) == true

    var showNowPlayingSheet by rememberSaveable { mutableStateOf(false) }
    var menuTrack by remember { mutableStateOf<Track?>(null) }
    var playlistPickerTracks by remember { mutableStateOf<List<Track>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    fun showMessage(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    /** A message with an Undo button; [onUndo] runs only if the button is tapped. */
    fun showUndo(message: String, onUndo: () -> Unit) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = "Undo",
                duration = SnackbarDuration.Long
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }

    fun selectTab(tab: NavigationTab) {
        if (tab == selectedTab) {
            // Re-tapping the open tab goes back to its first screen.
            navController.popBackStack(tab.destination, inclusive = false)
            return
        }
        navController.navigate(tab.graph) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Jump to the Library tab's first screen, then open [destination] on top of it. */
    fun openInLibrary(destination: HarmoniaNavDestination) {
        selectTab(NavigationTab.LIBRARY)
        navController.popBackStack(HarmoniaNavDestination.Library, inclusive = false)
        navController.navigate(destination)
    }

    fun openLibraryTab(tab: LibraryTab) {
        libraryViewModel.selectTab(tab)
        openInLibrary(HarmoniaNavDestination.LibraryCategory(tab.name))
    }

    fun navigateToAlbum(albumId: String) {
        val entry = navController.currentBackStackEntry
        if (entry?.destination?.hasRoute(HarmoniaNavDestination.AlbumDetail::class) == true &&
            entry.toRoute<HarmoniaNavDestination.AlbumDetail>().albumId == albumId
        ) return
        navController.navigate(HarmoniaNavDestination.AlbumDetail(albumId))
    }

    fun navigateToArtist(artistId: String) {
        val entry = navController.currentBackStackEntry
        if (entry?.destination?.hasRoute(HarmoniaNavDestination.ArtistDetail::class) == true &&
            entry.toRoute<HarmoniaNavDestination.ArtistDetail>().artistId == artistId
        ) return
        navController.navigate(HarmoniaNavDestination.ArtistDetail(artistId))
    }

    fun openPlaylist(playlistId: String) {
        navController.navigate(HarmoniaNavDestination.PlaylistDetail(playlistId))
    }

    val onTrackMenu: (Track) -> Unit = { track -> menuTrack = track }

    val playerUiState by playbackViewModel.uiState.collectAsState()
    val personalizationUiState by personalizationViewModel.uiState.collectAsState()
    val playlistsUiState by playlistsViewModel.uiState.collectAsState()

    val colors = HarmoniaThemeTokens.colors

    // Now Playing is an overlay, not a route; everything else uses the navigation back stack.
    BackHandler(enabled = showNowPlayingSheet) { showNowPlayingSheet = false }

    val playTracks: (List<Track>, Int) -> Unit = { tracks, index -> playbackViewModel.playTrackList(tracks, index) }
    val shuffleTracks: (List<Track>) -> Unit = { tracks -> playbackViewModel.playTrackList(tracks.shuffled(), 0) }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (!isCarMode) {
                NavigationBar(
                    containerColor = colors.surfaceElevated,
                    contentColor = colors.textPrimary
                ) {
                    NavigationTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectTab(tab) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title
                                )
                            },
                            label = {
                                Text(text = tab.title, maxLines = 1)
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = colors.primarySignal,
                                selectedTextColor = colors.primarySignal,
                                indicatorColor = Color.Transparent,
                                unselectedIconColor = colors.textSecondary,
                                unselectedTextColor = colors.textSecondary
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.backgroundRoot)
                .padding(innerPadding)
        ) {
            NavHost(
                navController = navController,
                startDestination = HarmoniaNavDestination.HomeTab,
                modifier = Modifier.fillMaxSize(),
                enterTransition = { fadeIn(tween(NAV_FADE_MS)) },
                exitTransition = { fadeOut(tween(NAV_FADE_MS)) },
                popEnterTransition = { fadeIn(tween(NAV_FADE_MS)) },
                popExitTransition = { fadeOut(tween(NAV_FADE_MS)) }
            ) {
                navigation<HarmoniaNavDestination.HomeTab>(startDestination = HarmoniaNavDestination.Home) {
                    composable<HarmoniaNavDestination.Home> {
                        HomeScreen(
                            playbackState = playerUiState.playbackState,
                            currentTrack = playerUiState.currentTrack,
                            onNavigateToLibrary = { selectTab(NavigationTab.LIBRARY) },
                            onNavigateToPlaylists = { openInLibrary(HarmoniaNavDestination.Playlists) },
                            onViewAllAlbums = { openLibraryTab(LibraryTab.ALBUMS) },
                            onViewAllArtists = { openLibraryTab(LibraryTab.ARTISTS) },
                            onPlaylistClick = { playlist -> openPlaylist(playlist.id) },
                            onTrackClick = playTracks,
                            onTrackMenu = onTrackMenu,
                            onOpenNowPlaying = { showNowPlayingSheet = true },
                            onAlbumClick = { album -> navigateToAlbum(album.id) },
                            onArtistClick = { artist -> navigateToArtist(artist.id) },
                            onResumeClick = {
                                if (playerUiState.playbackState.isPlaying) {
                                    playbackViewModel.pause()
                                } else if (playerUiState.currentTrack != null) {
                                    if (playerUiState.queue.isNotEmpty()) {
                                        playbackViewModel.play()
                                    } else {
                                        playbackViewModel.playTrackList(listOf(playerUiState.currentTrack!!), 0)
                                    }
                                } else {
                                    playbackViewModel.play()
                                }
                            },
                            onShuffleAllClick = { playbackViewModel.shuffleAll() },
                            onProfileClick = { navController.navigate(HarmoniaNavDestination.Profile) },
                            onSearchClick = { selectTab(NavigationTab.SEARCH) }
                        )
                    }
                }
                navigation<HarmoniaNavDestination.SearchTab>(startDestination = HarmoniaNavDestination.Search) {
                    composable<HarmoniaNavDestination.Search> {
                        SearchScreen(
                            onTrackClick = playTracks,
                            onTrackMenu = onTrackMenu,
                            onAlbumClick = { album -> navigateToAlbum(album.id) },
                            onArtistClick = { artist -> navigateToArtist(artist.id) },
                            onPlaylistClick = { playlist -> openPlaylist(playlist.id) },
                            onBrowse = { target ->
                                navController.navigate(
                                    when (target) {
                                        SearchBrowseTarget.SONGS -> HarmoniaNavDestination.LibraryCategory(LibraryTab.TRACKS.name)
                                        SearchBrowseTarget.ALBUMS -> HarmoniaNavDestination.LibraryCategory(LibraryTab.ALBUMS.name)
                                        SearchBrowseTarget.ARTISTS -> HarmoniaNavDestination.LibraryCategory(LibraryTab.ARTISTS.name)
                                        SearchBrowseTarget.FAVORITES -> HarmoniaNavDestination.LibraryCategory(LibraryTab.FAVORITES.name)
                                        SearchBrowseTarget.PLAYLISTS -> HarmoniaNavDestination.Playlists
                                        SearchBrowseTarget.GENRES -> HarmoniaNavDestination.Genres
                                        SearchBrowseTarget.YEARS -> HarmoniaNavDestination.Years
                                    }
                                )
                            }
                        )
                    }
                }
                navigation<HarmoniaNavDestination.LibraryTab>(startDestination = HarmoniaNavDestination.Library) {
                    composable<HarmoniaNavDestination.Library> {
                        LibraryHomeScreen(
                            onOpen = { destination ->
                                when (destination) {
                                    LibraryDestination.Playlists ->
                                        navController.navigate(HarmoniaNavDestination.Playlists)
                                    LibraryDestination.Genres ->
                                        navController.navigate(HarmoniaNavDestination.Genres)
                                    LibraryDestination.Years ->
                                        navController.navigate(HarmoniaNavDestination.Years)
                                    is LibraryDestination.Category ->
                                        navController.navigate(HarmoniaNavDestination.LibraryCategory(destination.tab.name))
                                }
                            },
                            onAlbumClick = { album -> navigateToAlbum(album.id) },
                            viewModel = libraryViewModel,
                            personalizationViewModel = personalizationViewModel,
                            playlistsViewModel = playlistsViewModel,
                            onTrackClick = playTracks,
                            onTrackMenu = onTrackMenu,
                            onProfileClick = { navController.navigate(HarmoniaNavDestination.Profile) }
                        )
                    }
                }

                composable<HarmoniaNavDestination.LibraryCategory> { entry ->
                    val tab = entry.toRoute<HarmoniaNavDestination.LibraryCategory>().tab
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        personalizationViewModel = personalizationViewModel,
                        onTrackClick = playTracks,
                        onTrackMenu = onTrackMenu,
                        onAlbumClick = { album -> navigateToAlbum(album.id) },
                        onArtistClick = { artist -> navigateToArtist(artist.id) },
                        category = LibraryTab.entries.firstOrNull { it.name == tab } ?: LibraryTab.TRACKS,
                        onBack = { navController.popBackStack() },
                        onPlayNext = { tracks ->
                            playbackViewModel.playNext(tracks)
                            showMessage(if (tracks.size == 1) "Playing next" else "${tracks.size} songs playing next")
                        },
                        onAddToQueue = { tracks ->
                            playbackViewModel.addToQueue(tracks)
                            showMessage(if (tracks.size == 1) "Added to queue" else "${tracks.size} songs added to queue")
                        },
                        onAddToPlaylist = { tracks -> playlistPickerTracks = tracks }
                    )
                }
                composable<HarmoniaNavDestination.Playlists> {
                    PlaylistsScreen(
                        onPlaylistClick = { playlist -> openPlaylist(playlist.id) },
                        onBack = { navController.popBackStack() }
                    )
                }

                composable<HarmoniaNavDestination.Genres> {
                    org.harmonia.player.presentation.browse.GenresScreen(
                        libraryViewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        onGenreClick = { name -> navController.navigate(HarmoniaNavDestination.GenreDetail(name)) }
                    )
                }
                composable<HarmoniaNavDestination.GenreDetail> { entry ->
                    org.harmonia.player.presentation.browse.GenreDetailScreen(
                        genreName = entry.toRoute<HarmoniaNavDestination.GenreDetail>().name,
                        libraryViewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        onPlay = playTracks,
                        onShuffle = shuffleTracks,
                        onTrackMenu = onTrackMenu,
                        onAlbumClick = { album -> navigateToAlbum(album.id) }
                    )
                }
                composable<HarmoniaNavDestination.Years> {
                    org.harmonia.player.presentation.browse.YearsScreen(
                        libraryViewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        onDecadeClick = { decade -> navController.navigate(HarmoniaNavDestination.DecadeDetail(decade)) }
                    )
                }
                composable<HarmoniaNavDestination.DecadeDetail> { entry ->
                    org.harmonia.player.presentation.browse.DecadeScreen(
                        decade = entry.toRoute<HarmoniaNavDestination.DecadeDetail>().decade,
                        libraryViewModel = libraryViewModel,
                        onBack = { navController.popBackStack() },
                        onPlay = playTracks,
                        onShuffle = shuffleTracks,
                        onTrackMenu = onTrackMenu
                    )
                }
                composable<HarmoniaNavDestination.AlbumDetail> { entry ->
                    AlbumDetailScreen(
                        albumId = entry.toRoute<HarmoniaNavDestination.AlbumDetail>().albumId,
                        currentPlayingTrackId = playerUiState.currentTrack?.id,
                        isPlaying = playerUiState.playbackState.isPlaying,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = playTracks,
                        onShuffleAll = shuffleTracks,
                        onArtistClick = { artistId -> navigateToArtist(artistId) },
                        onTrackMenu = onTrackMenu
                    )
                }
                composable<HarmoniaNavDestination.ArtistDetail> { entry ->
                    ArtistDetailScreen(
                        artistId = entry.toRoute<HarmoniaNavDestination.ArtistDetail>().artistId,
                        currentPlayingTrackId = playerUiState.currentTrack?.id,
                        isPlaying = playerUiState.playbackState.isPlaying,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = playTracks,
                        onShuffleAll = shuffleTracks,
                        onAlbumClick = { albumId -> navigateToAlbum(albumId) },
                        onTrackMenu = onTrackMenu
                    )
                }
                composable<HarmoniaNavDestination.PlaylistDetail> { entry ->
                    PlaylistDetailScreen(
                        playlistId = entry.toRoute<HarmoniaNavDestination.PlaylistDetail>().playlistId,
                        onBack = { navController.popBackStack() },
                        onPlay = playTracks,
                        onShuffle = shuffleTracks,
                        onTrackMenu = onTrackMenu,
                        onRemoved = { message, undo -> showUndo(message, undo) }
                    )
                }
                composable<HarmoniaNavDestination.Profile> {
                    ProfileHubScreen(
                        onBack = { navController.popBackStack() },
                        onOpenLikes = { openLibraryTab(LibraryTab.FAVORITES) },
                        onOpenCarMode = { navController.navigate(HarmoniaNavDestination.CarMode) },
                        onOpenSettings = { navController.navigate(HarmoniaNavDestination.Settings) },
                        onPlayTracks = playTracks,
                        onArtistClick = { artistId -> navigateToArtist(artistId) }
                    )
                }
                composable<HarmoniaNavDestination.Settings> {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCarMode = { navController.navigate(HarmoniaNavDestination.CarMode) }
                    )
                }
                composable<HarmoniaNavDestination.CarMode> {
                    CarModeScreen(
                        playbackViewModel = playbackViewModel,
                        onExit = { navController.popBackStack() },
                        personalizationViewModel = personalizationViewModel
                    )
                }
            }

            // Docked MiniPlayer above navigation (hidden during CarMode)
            if (!isCarMode && playerUiState.currentTrack != null) {
                MiniPlayer(
                    playbackState = playerUiState.playbackState,
                    currentTrack = playerUiState.currentTrack,
                    onPlayPause = {
                        if (playerUiState.playbackState.isPlaying) {
                            playbackViewModel.pause()
                        } else {
                            playbackViewModel.play()
                        }
                    },
                    onNext = { playbackViewModel.next() },
                    onClick = { showNowPlayingSheet = true },
                    onPrevious = { playbackViewModel.previous() },
                    isFavorite = personalizationUiState.isFavorite,
                    onToggleFavorite = { personalizationViewModel.toggleFavorite() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

        }
    }

            // Full-screen Now Playing, above the tab bar
            AnimatedVisibility(
                visible = showNowPlayingSheet && playerUiState.currentTrack != null,
                enter = slideInVertically(tween(320)) { it },
                exit = slideOutVertically(tween(260)) { it }
            ) {
                NowPlayingSheet(
                    playbackState = playerUiState.playbackState,
                    currentTrack = playerUiState.currentTrack,
                    queue = playerUiState.queue,
                    onPlayPause = {
                        if (playerUiState.playbackState.isPlaying) {
                            playbackViewModel.pause()
                        } else {
                            playbackViewModel.play()
                        }
                    },
                    onNext = { playbackViewModel.next() },
                    onPrevious = { playbackViewModel.previous() },
                    onSeekTo = { posMs -> playbackViewModel.seekTo(posMs) },
                    onQueueItemClick = { idx -> playbackViewModel.jumpToQueueIndex(idx) },
                    onToggleShuffle = { playbackViewModel.toggleShuffle() },
                    onCycleRepeat = { playbackViewModel.cycleRepeat() },
                    sleepTimerState = playerUiState.sleepTimerState,
                    onStartSleepTimer = { preset -> playbackViewModel.startSleepTimer(preset) },
                    onCancelSleepTimer = { playbackViewModel.cancelSleepTimer() },
                    onDismiss = { showNowPlayingSheet = false },
                    isFavorite = personalizationUiState.isFavorite,
                    rating = personalizationUiState.rating,
                    onToggleFavorite = { personalizationViewModel.toggleFavorite() },
                    onSetRating = { r -> personalizationViewModel.setRating(r) },
                    onClearRating = { personalizationViewModel.clearRating() },
                    onAlbumClick = { albumId ->
                        showNowPlayingSheet = false
                        navigateToAlbum(albumId)
                    },
                    onArtistClick = { artistId ->
                        showNowPlayingSheet = false
                        navigateToArtist(artistId)
                    },
                    onMoveQueueItem = { from, to -> playbackViewModel.moveQueueItem(from, to) },
                    onRemoveQueueItem = { idx ->
                        val removed = playerUiState.queue.getOrNull(idx)?.track
                        playbackViewModel.removeQueueItem(idx)
                        if (removed != null) {
                            val title = org.harmonia.player.presentation.util.TrackDisplayUtils.formatDisplayTitle(removed)
                            showUndo("Removed \"$title\" from the queue") {
                                playbackViewModel.restoreQueueItem(idx, removed)
                            }
                        }
                    },
                    onOpenTrackMenu = playerUiState.currentTrack?.let { track -> { menuTrack = track } }
                )
            }

            menuTrack?.let { track ->
                TrackActionsSheet(
                    track = track,
                    onDismiss = { menuTrack = null },
                    onPlayNext = {
                        playbackViewModel.playNext(track)
                        showMessage("Playing next")
                    },
                    onAddToQueue = {
                        playbackViewModel.addToQueue(track)
                        showMessage("Added to queue")
                    },
                    onAddToPlaylist = { playlistPickerTracks = listOf(track) },
                    onGoToAlbum = track.albumId?.let { albumId ->
                        {
                            showNowPlayingSheet = false
                            navigateToAlbum(albumId)
                        }
                    },
                    onGoToArtist = track.artistId?.let { artistId ->
                        {
                            showNowPlayingSheet = false
                            navigateToArtist(artistId)
                        }
                    }
                )
            }

            playlistPickerTracks?.let { tracks ->
                AddToPlaylistDialog(
                    tracks = tracks,
                    playlists = playlistsUiState.playlists,
                    onSelectPlaylist = { playlist ->
                        playlistsViewModel.addTracksToPlaylist(playlist.id, tracks.map { it.id })
                        playlistPickerTracks = null
                        showMessage("Added to ${playlist.name}")
                    },
                    onCreatePlaylist = { name ->
                        playlistsViewModel.createPlaylistWithTracks(name, tracks.map { it.id })
                        playlistPickerTracks = null
                        showMessage("Added to ${name.trim()}")
                    },
                    onDismiss = { playlistPickerTracks = null }
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(
                        bottom = when {
                            showNowPlayingSheet -> 24.dp
                            isCarMode -> 8.dp
                            playerUiState.currentTrack != null -> 164.dp
                            else -> 88.dp
                        }
                    )
            ) { data ->
                // Harmonia colours instead of Material's white bar; Undo in lavender.
                val themeColors = HarmoniaThemeTokens.colors
                Snackbar(
                    snackbarData = data,
                    modifier = Modifier.padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(14.dp),
                    containerColor = themeColors.surfaceElevated,
                    contentColor = themeColors.textPrimary,
                    actionColor = themeColors.primarySignal
                )
            }
    }
}
