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
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.BorderSlate
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.ObsidianSpace
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.design.theme.StudioSlate
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.presentation.components.HarmoniaArtwork
import java.util.Locale

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
    onTrackClick: (List<Track>, Int) -> Unit = { _, _ -> },
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onRequestPermission: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

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
                    color = PureSignalWhite
                )
                Text(
                    text = "LOCAL MEDIA CATALOG",
                    style = MaterialTheme.typography.labelSmall,
                    color = AcousticTeal
                )
            }

            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Sort library items" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Sort,
                        contentDescription = "Sort",
                        tint = AcousticTeal
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false },
                    modifier = Modifier.background(AcousticCharcoal)
                ) {
                    when (uiState.selectedTab) {
                        LibraryTab.TRACKS -> {
                            DropdownMenuItem(
                                text = { Text("Title (A-Z)", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.TITLE_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Title (Z-A)", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.TITLE_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.ARTIST_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Recently Added", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.DATE_ADDED_DESC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Duration", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setTrackSortOrder(TrackSortOrder.DURATION_DESC)
                                    showSortMenu = false
                                }
                            )
                        }
                        LibraryTab.ALBUMS -> {
                            DropdownMenuItem(
                                text = { Text("Album Title", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setAlbumSortOrder(AlbumSortOrder.TITLE_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Artist", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setAlbumSortOrder(AlbumSortOrder.ARTIST_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Release Year", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setAlbumSortOrder(AlbumSortOrder.YEAR_DESC)
                                    showSortMenu = false
                                }
                            )
                        }
                        LibraryTab.ARTISTS -> {
                            DropdownMenuItem(
                                text = { Text("Name (A-Z)", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setArtistSortOrder(ArtistSortOrder.NAME_ASC)
                                    showSortMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Name (Z-A)", color = PureSignalWhite) },
                                onClick = {
                                    viewModel.setArtistSortOrder(ArtistSortOrder.NAME_DESC)
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AcousticTeal)
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
                Spacer(modifier = Modifier.height(8.dp))
            } else if (uiState.isScanFailed) {
                ScanErrorBanner(
                    errorMessage = uiState.scanErrorMessage ?: "Scan failed",
                    onRetry = { viewModel.retryScan() }
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (uiState.isPermissionRequired) {
                PermissionWarningBanner(
                    permissionState = uiState.permissionState,
                    onRequestPermission = actualRequestPermission,
                    onOpenSettings = actualOpenSettings
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tabs: Tracks, Albums, Artists
            androidx.compose.material3.SecondaryTabRow(
                selectedTabIndex = uiState.selectedTab.ordinal,
                containerColor = ObsidianSpace,
                contentColor = PureSignalWhite,
                divider = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderSlate)
                    )
                }
            ) {
                Tab(
                    selected = uiState.selectedTab == LibraryTab.TRACKS,
                    onClick = { viewModel.selectTab(LibraryTab.TRACKS) },
                    text = { Text("Tracks (${uiState.tracks.size})", fontWeight = FontWeight.Bold) },
                    selectedContentColor = AcousticTeal,
                    unselectedContentColor = MutedSlate
                )
                Tab(
                    selected = uiState.selectedTab == LibraryTab.ALBUMS,
                    onClick = { viewModel.selectTab(LibraryTab.ALBUMS) },
                    text = { Text("Albums (${uiState.albums.size})", fontWeight = FontWeight.Bold) },
                    selectedContentColor = AcousticTeal,
                    unselectedContentColor = MutedSlate
                )
                Tab(
                    selected = uiState.selectedTab == LibraryTab.ARTISTS,
                    onClick = { viewModel.selectTab(LibraryTab.ARTISTS) },
                    text = { Text("Artists (${uiState.artists.size})", fontWeight = FontWeight.Bold) },
                    selectedContentColor = AcousticTeal,
                    unselectedContentColor = MutedSlate
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (uiState.selectedTab) {
                LibraryTab.TRACKS -> TracksTabContent(
                    tracks = uiState.tracks,
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = HarmoniaIcons.MusicNote,
                    contentDescription = null,
                    tint = AcousticTeal,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                val isSettingsRequired = permissionState == StoragePermissionState.SETTINGS_REQUIRED
                Text(
                    text = if (isSettingsRequired) "Permission Required in Settings" else "Audio Permission Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureSignalWhite,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isSettingsRequired) {
                        "Audio permission was permanently denied. Please enable audio storage access in system settings to discover local music."
                    } else {
                        "Harmonia is an offline-first music player that needs access to your device audio files to discover and organize your local library."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = if (isSettingsRequired) onOpenSettings else onRequestPermission,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AcousticTeal,
                        contentColor = ObsidianSpace
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.semantics {
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            CircularProgressIndicator(
                color = AcousticTeal,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Scanning Local Audio",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PureSignalWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Discovering audio files from device storage...",
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Scan Failed",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = PureSignalWhite
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AcousticTeal,
                        contentColor = ObsidianSpace
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.semantics {
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
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = HarmoniaIcons.MusicNote,
                contentDescription = null,
                tint = MutedSlate,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PureSignalWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No supported audio tracks were found on this device. Add music files to your storage to start listening.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ScanningBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(AcousticCharcoal, RoundedCornerShape(8.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = AcousticTeal,
            modifier = Modifier.size(16.dp),
            strokeWidth = 2.dp
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Updating music library...",
            style = MaterialTheme.typography.bodySmall,
            color = PureSignalWhite
        )
    }
}

@Composable
private fun ScanErrorBanner(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(AcousticCharcoal, RoundedCornerShape(8.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodySmall,
            color = PureSignalWhite,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = AcousticTeal,
                contentColor = ObsidianSpace
            ),
            shape = RoundedCornerShape(6.dp),
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
    val isSettingsRequired = permissionState == StoragePermissionState.SETTINGS_REQUIRED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(AcousticCharcoal, RoundedCornerShape(8.dp))
            .border(1.dp, BorderSlate, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Audio permission required to scan new files",
            style = MaterialTheme.typography.bodySmall,
            color = PureSignalWhite,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = if (isSettingsRequired) onOpenSettings else onRequestPermission,
            colors = ButtonDefaults.buttonColors(
                containerColor = AcousticTeal,
                contentColor = ObsidianSpace
            ),
            shape = RoundedCornerShape(6.dp),
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
    unmountedSourceIds: Set<String> = emptySet(),
    disabledSourceIds: Set<String> = emptySet(),
    onTrackClick: (List<Track>, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
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
                onClick = { onTrackClick(tracks, index) }
            )
        }
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    if (albums.isEmpty()) {
        EmptyLibraryState(message = "No albums found in library")
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
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
    if (artists.isEmpty()) {
        EmptyLibraryState(message = "No artists found in library")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
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
    onClick: () -> Unit
) {
    val isDimmed = badgeText != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (badgeText != null) {
                    "Play ${track.title} by ${track.artistName ?: "Unknown"} ($badgeText)"
                } else {
                    "Play ${track.title} by ${track.artistName ?: "Unknown"}"
                }
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = track.localFile?.fileHash,
                size = 48.dp,
                cornerRadius = 8.dp,
                contentDescription = "${track.title} artwork",
                modifier = if (isDimmed) Modifier.graphicsLayer(alpha = 0.5f) else Modifier
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isDimmed) PureSignalWhite.copy(alpha = 0.5f) else PureSignalWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(StudioSlate, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = MutedSlate
                            )
                        }
                    }
                }
                Text(
                    text = listOfNotNull(track.artistName, track.albumTitle).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDimmed) MutedSlate.copy(alpha = 0.6f) else MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatDuration(track.durationSec),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isDimmed) PureSignalWhite.copy(alpha = 0.5f) else PureSignalWhite
                )
                track.trackNumber?.let { num ->
                    Text(
                        text = "#$num",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSlate
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumGridCard(
    album: Album,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Album ${album.title} by ${album.artistName ?: "Unknown"}" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
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

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PureSignalWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = album.artistName ?: "Unknown Artist",
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            album.year?.let { yr ->
                Text(
                    text = yr.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = AcousticTeal
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Artist ${artist.name}" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(StudioSlate),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Person,
                    contentDescription = null,
                    tint = AcousticTeal,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = PureSignalWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Local Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSlate
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
