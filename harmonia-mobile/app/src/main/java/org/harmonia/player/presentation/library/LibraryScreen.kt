package org.harmonia.player.presentation.library

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import java.util.Locale

enum class FavoriteSortOrder {
    RECENTLY_FAVORITED,
    TITLE_ASC,
    ARTIST_ASC
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
    personalizationViewModel: PersonalizationViewModel = viewModel(),
    onTrackClick: (List<Track>, Int) -> Unit = { _, _ -> },
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onRequestPermission: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoriteTracks by personalizationViewModel.favoriteTracks.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }
    var favoriteSortOrder by rememberSaveable { mutableStateOf(FavoriteSortOrder.RECENTLY_FAVORITED) }

    val favoriteTrackIds = remember(favoriteTracks) { favoriteTracks.map { it.id }.toSet() }
    val sortedFavoriteTracks = remember(favoriteTracks, favoriteSortOrder) {
        when (favoriteSortOrder) {
            FavoriteSortOrder.RECENTLY_FAVORITED -> favoriteTracks
            FavoriteSortOrder.TITLE_ASC -> favoriteTracks.sortedWith(
                compareBy<Track> { it.title.lowercase(Locale.ROOT) }
                    .thenBy { it.artistName?.lowercase(Locale.ROOT) ?: "" }
                    .thenBy { it.id }
            )
            FavoriteSortOrder.ARTIST_ASC -> favoriteTracks.sortedWith(
                compareBy<Track> { it.artistName?.lowercase(Locale.ROOT) ?: "" }
                    .thenBy { it.title.lowercase(Locale.ROOT) }
                    .thenBy { it.id }
            )
        }
    }

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val activity = context as? Activity
        val requiredPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val canRequestAgain = if (activity != null) {
            ActivityCompat.shouldShowRequestPermissionRationale(activity, requiredPerm)
        } else {
            true
        }
        viewModel.onPermissionResult(isGranted, canRequestAgain)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val actualRequestPermission = onRequestPermission ?: {
        val requiredPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(requiredPerm)
    }

    val actualOpenSettings = onOpenSettings ?: {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard)
    ) {
        Spacer(modifier = Modifier.height(spacing.standard))

        // Header Row: Title + Sort Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "LIBRARY",
                    style = MaterialTheme.typography.displayLarge.copy(
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                        fontSize = 26.sp
                    ),
                    color = colors.textPrimary
                )
                Text(
                    text = "LOCAL MEDIA CATALOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primarySignal
                )
            }

            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Sort library items" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Sort,
                        contentDescription = "Sort",
                        tint = colors.primarySignal
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier
                        .background(colors.surfaceElevated)
                        .hairlineBorder(colors.borderStructural, RoundedCornerShape(8.dp), 1.dp)
                ) {
                    when (uiState.selectedTab) {
                        LibraryTab.TRACKS -> {
                            DropdownMenuItem(
                                text = { Text("Title (A-Z)", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.TITLE_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Title (Z-A)", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.TITLE_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.ARTIST_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recently Added", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.DATE_ADDED_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duration", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.DURATION_DESC)
                                    showSortMenu = false
                                }
                            )
                        }
                        LibraryTab.ALBUMS -> {
                            DropdownMenuItem(
                                text = { Text("Album Title", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setAlbumSortOrder(AlbumSortOrder.TITLE_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setAlbumSortOrder(AlbumSortOrder.ARTIST_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Release Year", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setAlbumSortOrder(AlbumSortOrder.YEAR_DESC)
                                    showSortMenu = false
                                }
                            )
                        }
                        LibraryTab.ARTISTS -> {
                            DropdownMenuItem(
                                text = { Text("Name (A-Z)", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setArtistSortOrder(ArtistSortOrder.NAME_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (Z-A)", color = colors.textPrimary) },
                                onClick = {
                                    viewModel.setArtistSortOrder(ArtistSortOrder.NAME_DESC)
                                    showSortMenu = false
                                }
                            )
                        }
                        LibraryTab.FAVORITES -> {
                            DropdownMenuItem(
                                text = { Text("Recently Favorited", color = colors.textPrimary) },
                                onClick = {
                                    favoriteSortOrder = FavoriteSortOrder.RECENTLY_FAVORITED
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Title (A-Z)", color = colors.textPrimary) },
                                onClick = {
                                    favoriteSortOrder = FavoriteSortOrder.TITLE_ASC
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist", color = colors.textPrimary) },
                                onClick = {
                                    favoriteSortOrder = FavoriteSortOrder.ARTIST_ASC
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primarySignal)
            }
        } else if (uiState.tracks.isEmpty()) {
            when {
                uiState.isPermissionRequired -> {
                    PermissionRequiredState(
                        permissionState = uiState.permissionState,
                        onRequestPermission = actualRequestPermission,
                        onOpenSettings = actualOpenSettings
                    )
                }
                uiState.isScanning -> {
                    ScanningState()
                }
                uiState.isScanFailed -> {
                    ScanFailedState(
                        errorMessage = uiState.scanErrorMessage ?: "Media reconciliation scan failed",
                        onRetry = { viewModel.retryScan() }
                    )
                }
                else -> {
                    EmptyLibraryState(message = "No audio files found")
                }
            }
        } else {
            // In-flight status banner when library is populated
            if (uiState.isScanning) {
                ScanningBanner()
                Spacer(modifier = Modifier.height(spacing.small))
            } else if (uiState.isScanFailed) {
                ScanErrorBanner(
                    errorMessage = uiState.scanErrorMessage ?: "Scan failed",
                    onRetry = { viewModel.retryScan() }
                )
                Spacer(modifier = Modifier.height(spacing.small))
            } else if (uiState.isPermissionRequired) {
                PermissionWarningBanner(
                    permissionState = uiState.permissionState,
                    onRequestPermission = actualRequestPermission,
                    onOpenSettings = actualOpenSettings
                )
                Spacer(modifier = Modifier.height(spacing.small))
            }

            // Tabs: Tracks, Favorites, Albums, Artists
            val tabs = listOf(
                LibraryTab.TRACKS to "Tracks (${uiState.tracks.size})",
                LibraryTab.FAVORITES to "Favorites (${favoriteTracks.size})",
                LibraryTab.ALBUMS to "Albums (${uiState.albums.size})",
                LibraryTab.ARTISTS to "Artists (${uiState.artists.size})"
            )
            val selectedIndex = tabs.indexOfFirst { it.first == uiState.selectedTab }.coerceAtLeast(0)

            androidx.compose.material3.SecondaryTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = colors.backgroundRoot,
                contentColor = colors.textPrimary,
                divider = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.borderStructural)
                    )
                }
            ) {
                tabs.forEach { (tab, title) ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(title, fontWeight = FontWeight.Bold) },
                        selectedContentColor = colors.primarySignal,
                        unselectedContentColor = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            when (uiState.selectedTab) {
                LibraryTab.TRACKS -> TracksTabContent(
                    tracks = uiState.tracks,
                    favoriteTrackIds = favoriteTrackIds,
                    onToggleFavorite = { track -> personalizationViewModel.toggleFavorite(track.id) },
                    unmountedSourceIds = uiState.unmountedSourceIds,
                    disabledSourceIds = uiState.disabledSourceIds,
                    onTrackClick = onTrackClick
                )
                LibraryTab.FAVORITES -> FavoritesTabContent(
                    tracks = sortedFavoriteTracks,
                    onToggleFavorite = { track -> personalizationViewModel.toggleFavorite(track.id) },
                    onBrowseTracks = { viewModel.selectTab(LibraryTab.TRACKS) },
                    unmountedSourceIds = uiState.unmountedSourceIds,
                    disabledSourceIds = uiState.disabledSourceIds,
                    onTrackClick = onTrackClick
                )
                LibraryTab.ALBUMS -> AlbumsTabContent(
                    albums = uiState.albums,
                    onAlbumClick = onAlbumClick
                )
                LibraryTab.ARTISTS -> ArtistsTabContent(
                    artists = uiState.artists,
                    onArtistClick = onArtistClick
                )
            }
        }
    }
}

@Composable
private fun PermissionRequiredState(
    permissionState: StoragePermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = HarmoniaIcons.MusicNote,
                    contentDescription = null,
                    tint = colors.primarySignal,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                val isSettingsRequired = permissionState == StoragePermissionState.SETTINGS_REQUIRED
                Text(
                    text = if (isSettingsRequired) "Permission Required in Settings" else "Audio Permission Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = if (isSettingsRequired) {
                        "Audio permission was permanently denied. Please enable audio storage access in system settings to discover local music."
                    } else {
                        "Harmonia is an offline-first music player that needs access to your device audio files to discover and organize your local library."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = if (isSettingsRequired) onOpenSettings else onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primarySignal,
                        contentColor = colors.backgroundRoot
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .touchTarget48()
                        .semantics {
                            contentDescription = if (isSettingsRequired) "Open system settings" else "Grant audio permission"
                        }
                ) {
                    Text(
                        text = if (isSettingsRequired) "Open Settings" else "Grant Permission",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun ScanningState() {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(spacing.large)
        ) {
            CircularProgressIndicator(
                color = colors.primarySignal,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(spacing.standard))
            Text(
                text = "Scanning Local Audio",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = "Discovering audio files from device storage...",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanFailedState(
    errorMessage: String,
    onRetry: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Close,
                    contentDescription = null,
                    tint = colors.statusError,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    text = "Scan Failed",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primarySignal,
                        contentColor = colors.backgroundRoot
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .touchTarget48()
                        .semantics {
                            contentDescription = "Retry scan"
                        }
                ) {
                    Text("Retry Scan", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(message: String) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(spacing.large)
        ) {
            Icon(
                imageVector = HarmoniaIcons.MusicNote,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.xSmall))
            Text(
                text = "No supported audio tracks were found on this device. Add music files to your storage to start listening.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanningBanner() {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val bannerShape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xSmall)
            .background(colors.surfaceElevated, bannerShape)
            .hairlineBorder(color = colors.borderStructural, shape = bannerShape, width = 1.dp)
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = colors.primarySignal,
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(spacing.small))
        Text(
            text = "Updating music library...",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun ScanErrorBanner(
    errorMessage: String,
    onRetry: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val bannerShape = RoundedCornerShape(8.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xSmall)
            .background(colors.surfaceElevated, bannerShape)
            .hairlineBorder(color = colors.statusError.copy(alpha = 0.5f), shape = bannerShape, width = 1.dp)
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = colors.statusError,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(spacing.small))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primarySignal,
                contentColor = colors.backgroundRoot
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.touchTarget48(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("Retry", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionWarningBanner(
    permissionState: StoragePermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val bannerShape = RoundedCornerShape(8.dp)
    val isSettingsRequired = permissionState == StoragePermissionState.SETTINGS_REQUIRED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xSmall)
            .background(colors.surfaceElevated, bannerShape)
            .hairlineBorder(color = colors.statusWarning.copy(alpha = 0.5f), shape = bannerShape, width = 1.dp)
            .padding(horizontal = spacing.medium, vertical = spacing.small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Audio permission required to scan new files",
            style = MaterialTheme.typography.bodySmall,
            color = colors.statusWarning,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(spacing.small))
        Button(
            onClick = if (isSettingsRequired) onOpenSettings else onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.primarySignal,
                contentColor = colors.backgroundRoot
            ),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier.touchTarget48(),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (isSettingsRequired) "Settings" else "Grant",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun TracksTabContent(
    tracks: List<Track>,
    favoriteTrackIds: Set<String> = emptySet(),
    onToggleFavorite: ((Track) -> Unit)? = null,
    unmountedSourceIds: Set<String> = emptySet(),
    disabledSourceIds: Set<String> = emptySet(),
    onTrackClick: (List<Track>, Int) -> Unit
) {
    val spacing = HarmoniaThemeTokens.spacing

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
    ) {
        itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
            val sourceId = track.localFile?.sourceId
            val isUnmounted = sourceId != null && unmountedSourceIds.contains(sourceId)
            val isDisabled = sourceId != null && disabledSourceIds.contains(sourceId)
            val isUnavailable = track.availability == LocalFileAvailability.UNAVAILABLE

            val badgeText: String? = when {
                isDisabled -> "Source Disabled"
                isUnmounted -> "Storage Unmounted"
                isUnavailable -> "File Unavailable"
                else -> null
            }

            TrackRowCard(
                track = track,
                badgeText = badgeText,
                isFavorite = favoriteTrackIds.contains(track.id),
                onToggleFavorite = onToggleFavorite?.let { action -> { action(track) } },
                onClick = { onTrackClick(tracks, index) }
            )
        }
    }
}

@Composable
internal fun FavoritesTabContent(
    tracks: List<Track>,
    onToggleFavorite: (Track) -> Unit,
    onBrowseTracks: () -> Unit,
    unmountedSourceIds: Set<String> = emptySet(),
    disabledSourceIds: Set<String> = emptySet(),
    onTrackClick: (List<Track>, Int) -> Unit
) {
    val spacing = HarmoniaThemeTokens.spacing

    if (tracks.isEmpty()) {
        EmptyFavoritesState(onBrowseTracks = onBrowseTracks)
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
        ) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                val sourceId = track.localFile?.sourceId
                val isUnmounted = sourceId != null && unmountedSourceIds.contains(sourceId)
                val isDisabled = sourceId != null && disabledSourceIds.contains(sourceId)
                val isUnavailable = track.availability == LocalFileAvailability.UNAVAILABLE

                val badgeText: String? = when {
                    isDisabled -> "Source Disabled"
                    isUnmounted -> "Storage Unmounted"
                    isUnavailable -> "File Unavailable"
                    else -> null
                }

                TrackRowCard(
                    track = track,
                    badgeText = badgeText,
                    isFavorite = true,
                    onToggleFavorite = { onToggleFavorite(track) },
                    onClick = { onTrackClick(tracks, index) }
                )
            }
        }
    }
}

@Composable
internal fun EmptyFavoritesState(
    onBrowseTracks: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp),
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(spacing.large),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = HarmoniaIcons.FavoriteBorder,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    text = "No Favorite Tracks",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = "Tracks marked as favorites in your library or Now Playing will appear here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onBrowseTracks,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primarySignal,
                        contentColor = colors.backgroundRoot
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .touchTarget48()
                        .semantics {
                            contentDescription = "Browse all tracks in library"
                        }
                ) {
                    Text("Browse Tracks", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    val spacing = HarmoniaThemeTokens.spacing

    if (albums.isEmpty()) {
        EmptyLibraryState(message = "No albums found in library")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacing.medium),
            verticalArrangement = Arrangement.spacedBy(spacing.medium),
            contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumGridCard(album = album, onClick = { onAlbumClick(album) })
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit
) {
    val spacing = HarmoniaThemeTokens.spacing

    if (artists.isEmpty()) {
        EmptyLibraryState(message = "No artists found in library")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(spacing.small),
            contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
        ) {
            items(artists, key = { it.id }) { artist ->
                ArtistRowCard(artist = artist, onClick = { onArtistClick(artist) })
            }
        }
    }
}

@Composable
internal fun TrackRowCard(
    track: Track,
    badgeText: String? = null,
    isFavorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(12.dp)
    val isDimmed = badgeText != null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (badgeText != null) {
                    "Play ${track.title} by ${track.artistName ?: "Unknown"} ($badgeText)"
                } else {
                    "Play ${track.title} by ${track.artistName ?: "Unknown"}"
                }
            },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = track.localFile?.fileHash,
                size = 48.dp,
                cornerRadius = 8.dp,
                contentDescription = "${track.title} artwork",
                modifier = if (isDimmed) Modifier.graphicsLayer(alpha = 0.5f) else Modifier
            )

            Spacer(modifier = Modifier.width(spacing.medium))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isDimmed) colors.textPrimary.copy(alpha = 0.5f) else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(spacing.xSmall))
                        val badgeColor = if (badgeText == "File Unavailable") colors.statusError else colors.statusWarning
                        Box(
                            modifier = Modifier
                                .background(colors.surfaceContainer, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = badgeColor
                            )
                        }
                    }
                }
                Text(
                    text = listOfNotNull(track.artistName, track.albumTitle).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDimmed) colors.textSecondary.copy(alpha = 0.6f) else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(spacing.small))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDuration(track.durationSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDimmed) colors.textPrimary.copy(alpha = 0.5f) else colors.textPrimary
                )
                track.trackNumber?.let { num ->
                    Text(
                        text = "#$num",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
            }

            if (onToggleFavorite != null) {
                Spacer(modifier = Modifier.width(spacing.xSmall))
                FavoriteButton(
                    isFavorite = isFavorite ?: false,
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.touchTarget48()
                )
            }
        }
    }
}

@Composable
private fun AlbumGridCard(
    album: Album,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Album ${album.title} by ${album.artistName ?: "Unknown"}" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            HarmoniaArtwork(
                artworkHash = album.artworkHash,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                size = 140.dp,
                cornerRadius = 8.dp,
                placeholderIcon = HarmoniaIcons.Album,
                contentDescription = "${album.title} cover"
            )

            Spacer(modifier = Modifier.height(spacing.small))

            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = album.artistName ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            album.year?.let { yr ->
                Text(
                    text = yr.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primarySignal
                )
            }
        }
    }
}

@Composable
private fun ArtistRowCard(
    artist: Artist,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Artist ${artist.name}" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Person,
                    contentDescription = null,
                    tint = colors.primarySignal,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(spacing.standard))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Local Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

private fun formatDuration(durationSec: Double): String {
    val totalSec = durationSec.toInt()
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
