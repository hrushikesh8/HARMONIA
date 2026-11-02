package org.harmonia.player.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaTheme
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.presentation.album.AlbumDetailScreen
import org.harmonia.player.presentation.artist.ArtistDetailScreen
import org.harmonia.player.presentation.carmode.CarModeScreen
import org.harmonia.player.presentation.home.HomeScreen
import org.harmonia.player.presentation.library.LibraryScreen
import org.harmonia.player.presentation.navigation.HarmoniaNavDestination
import org.harmonia.player.presentation.playback.MiniPlayer
import org.harmonia.player.presentation.playback.NowPlayingSheet
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.playlists.PlaylistDetailScreen
import org.harmonia.player.presentation.playlists.PlaylistsScreen
import org.harmonia.player.presentation.profile.ProfileHubScreen
import androidx.compose.runtime.DisposableEffect
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.presentation.search.SearchScreen
import org.harmonia.player.presentation.settings.SettingsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val userPrefs = remember { ServiceLocator.getUserPreferencesRepository() }
            val isDarkTheme by userPrefs.observeIsDarkTheme().collectAsState(initial = userPrefs.isDarkTheme())
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

/**
 * 4 primary music destinations per Blue Edition profile/navigation redesign.
 * Settings has moved to the Profile/Menu destination.
 */
enum class NavigationTab(
    val title: String,
    val icon: ImageVector,
    val destination: HarmoniaNavDestination
) {
    HOME("Home", HarmoniaIcons.Home, HarmoniaNavDestination.Home),
    SEARCH("Search", HarmoniaIcons.Search, HarmoniaNavDestination.Search),
    LIBRARY("Library", HarmoniaIcons.Library, HarmoniaNavDestination.Library),
    PLAYLISTS("Playlists", HarmoniaIcons.Playlists, HarmoniaNavDestination.Playlists)
}

@Composable
fun MainAppContainer(
    playbackViewModel: PlaybackViewModel = viewModel(),
    personalizationViewModel: PersonalizationViewModel = viewModel()
) {
    var selectedTab by rememberSaveable { mutableStateOf(NavigationTab.HOME) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<String?>(null) }
    var detailStack by rememberSaveable { mutableStateOf(listOf<String>()) }
    var showProfileHub by rememberSaveable { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showCarMode by rememberSaveable { mutableStateOf(false) }
    var showNowPlayingSheet by remember { mutableStateOf(false) }

    val currentDetail = detailStack.lastOrNull()
    val selectedAlbumId = if (currentDetail?.startsWith("album:") == true) currentDetail.removePrefix("album:") else null
    val selectedArtistId = if (currentDetail?.startsWith("artist:") == true) currentDetail.removePrefix("artist:") else null

    fun navigateToAlbum(albumId: String) {
        val entry = "album:$albumId"
        if (detailStack.lastOrNull() != entry) {
            detailStack = detailStack + entry
        }
    }

    fun navigateToArtist(artistId: String) {
        val entry = "artist:$artistId"
        if (detailStack.lastOrNull() != entry) {
            detailStack = detailStack + entry
        }
    }

    fun popDetail() {
        if (detailStack.isNotEmpty()) {
            detailStack = detailStack.dropLast(1)
        }
    }

    val playerUiState by playbackViewModel.uiState.collectAsState()
    val personalizationUiState by personalizationViewModel.uiState.collectAsState()

    val colors = HarmoniaThemeTokens.colors

    val canGoBack = showNowPlayingSheet || showCarMode || showSettings || showProfileHub || detailStack.isNotEmpty() || selectedPlaylistId != null || selectedTab != NavigationTab.HOME

    // Back handling for hierarchal views and modals
    BackHandler(
        enabled = canGoBack
    ) {
        when {
            showNowPlayingSheet -> showNowPlayingSheet = false
            showCarMode -> showCarMode = false
            showSettings -> showSettings = false
            showProfileHub -> showProfileHub = false
            detailStack.isNotEmpty() -> popDetail()
            selectedPlaylistId != null -> selectedPlaylistId = null
            selectedTab != NavigationTab.HOME -> selectedTab = NavigationTab.HOME
        }
    }

    val isTopLevelDestination = !showCarMode && !showProfileHub && !showSettings &&
            detailStack.isEmpty() && selectedPlaylistId == null

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isTopLevelDestination) {
                NavigationBar(
                    containerColor = colors.surfaceElevated,
                    contentColor = colors.textPrimary
                ) {
                    NavigationTab.entries.forEach { tab ->
                        val isSelected = selectedTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                detailStack = emptyList()
                                if (tab == NavigationTab.PLAYLISTS && selectedTab == NavigationTab.PLAYLISTS) {
                                    selectedPlaylistId = null
                                }
                                selectedTab = tab
                            },
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
                                indicatorColor = colors.surfaceContainer,
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
            when {
                showCarMode -> {
                    CarModeScreen(
                        playbackViewModel = playbackViewModel,
                        onExit = { showCarMode = false }
                    )
                }
                showSettings -> {
                    SettingsScreen(
                        onBack = { showSettings = false },
                        onOpenCarMode = { showCarMode = true }
                    )
                }
                showProfileHub -> {
                    ProfileHubScreen(
                        onBack = { showProfileHub = false },
                        onOpenLikes = {
                            showProfileHub = false
                            detailStack = emptyList()
                            selectedPlaylistId = null
                            selectedTab = NavigationTab.LIBRARY
                        },
                        onOpenCarMode = {
                            showCarMode = true
                        },
                        onOpenSettings = {
                            showSettings = true
                        }
                    )
                }
                selectedAlbumId != null -> {
                    AlbumDetailScreen(
                        albumId = selectedAlbumId,
                        currentPlayingTrackId = playerUiState.currentTrack?.id,
                        isPlaying = playerUiState.playbackState.isPlaying,
                        onBack = { popDetail() },
                        onPlayTrack = { tracks, clickedIndex ->
                            playbackViewModel.playTrackList(tracks, clickedIndex)
                        },
                        onShuffleAll = { tracks ->
                            playbackViewModel.playTrackList(tracks.shuffled(), 0)
                        },
                        onArtistClick = { artistId ->
                            navigateToArtist(artistId)
                        }
                    )
                }
                selectedArtistId != null -> {
                    ArtistDetailScreen(
                        artistId = selectedArtistId,
                        currentPlayingTrackId = playerUiState.currentTrack?.id,
                        isPlaying = playerUiState.playbackState.isPlaying,
                        onBack = { popDetail() },
                        onPlayTrack = { tracks, clickedIndex ->
                            playbackViewModel.playTrackList(tracks, clickedIndex)
                        },
                        onShuffleAll = { tracks ->
                            playbackViewModel.playTrackList(tracks.shuffled(), 0)
                        },
                        onAlbumClick = { albumId ->
                            navigateToAlbum(albumId)
                        }
                    )
                }
                else -> {
                    when (selectedTab) {
                        NavigationTab.HOME -> HomeScreen(
                            playbackState = playerUiState.playbackState,
                            currentTrack = playerUiState.currentTrack,
                            onNavigateToLibrary = { selectedTab = NavigationTab.LIBRARY },
                            onNavigateToPlaylists = {
                                selectedPlaylistId = null
                                selectedTab = NavigationTab.PLAYLISTS
                            },
                            onTrackClick = { track ->
                                playbackViewModel.playTrackList(listOf(track), 0)
                            },
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
                            onProfileClick = { showProfileHub = true },
                            onSearchClick = { selectedTab = NavigationTab.SEARCH }
                        )
                        NavigationTab.SEARCH -> SearchScreen(
                            onTrackClick = { track ->
                                playbackViewModel.playTrackList(listOf(track), 0)
                            },
                            onAlbumClick = { album -> navigateToAlbum(album.id) },
                            onArtistClick = { artist -> navigateToArtist(artist.id) },
                            onPlaylistClick = { playlist ->
                                selectedPlaylistId = playlist.id
                            }
                        )
                        NavigationTab.LIBRARY -> LibraryScreen(
                            personalizationViewModel = personalizationViewModel,
                            onTrackClick = { tracks, clickedIndex ->
                                playbackViewModel.playTrackList(tracks, clickedIndex)
                            },
                            onAlbumClick = { album -> navigateToAlbum(album.id) },
                            onArtistClick = { artist -> navigateToArtist(artist.id) },
                            onProfileClick = { showProfileHub = true }
                        )
                        NavigationTab.PLAYLISTS -> {
                            if (selectedPlaylistId != null) {
                                PlaylistDetailScreen(
                                    playlistId = selectedPlaylistId!!,
                                    onBack = { selectedPlaylistId = null },
                                    onPlay = { tracks, startIndex ->
                                        playbackViewModel.playTrackList(tracks, startIndex)
                                    },
                                    onShuffle = { tracks ->
                                        playbackViewModel.playTrackList(tracks.shuffled(), 0)
                                    }
                                )
                            } else {
                                PlaylistsScreen(
                                    onPlaylistClick = { playlist ->
                                        selectedPlaylistId = playlist.id
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Docked MiniPlayer above navigation (hidden during CarMode)
            if (!showCarMode && playerUiState.currentTrack != null) {
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
                    isFavorite = personalizationUiState.isFavorite,
                    onToggleFavorite = { personalizationViewModel.toggleFavorite() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            // Now Playing Sheet
            if (showNowPlayingSheet) {
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
                    }
                )
            }
        }
    }
}
