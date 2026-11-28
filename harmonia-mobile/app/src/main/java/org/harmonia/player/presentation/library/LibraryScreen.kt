package org.harmonia.player.presentation.library

import org.harmonia.player.presentation.components.SongSwipeActions
import org.harmonia.player.presentation.components.shouldShowAlphabetBar
import org.harmonia.player.presentation.components.alphabetPositions
import org.harmonia.player.presentation.components.AlphabetScroller
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.material3.TextButton
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.harmonia.player.data.local.MediaFolderResolver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.LocalFileAvailability
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.storage.permission.StoragePermissionState
import org.harmonia.player.presentation.components.AlbumGridMinCellWidth
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.components.TrackMenuButton
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.util.TrackDisplayUtils
import org.harmonia.player.presentation.components.AlbumGridItem
import org.harmonia.player.presentation.components.ArtistListRow
import org.harmonia.player.presentation.components.CollectionListRow
import org.harmonia.player.presentation.components.SongListRow
import java.util.Locale

enum class FavoriteSortOrder {
    RECENTLY_FAVORITED,
    TITLE_ASC,
    ARTIST_ASC
}

/**
 * Pure domain representation of a local music directory.
 */
data class MusicFolder(
    val fullPath: String,
    val folderName: String,
    val tracks: List<Track>
)

/**
 * Harmonia Blue Edition — Polished Library Screen
 *
 * Implements:
 * - Sub-tabs: Tracks, Albums, Artists, Folders, Favorites with smooth scrollable tab row.
 * - Format chips built from the formats actually in the library, with live counts.
 * - Discoverable inline search and multi-criteria sorting.
 * - High-density, information-rich track rows with verified 48dp touch targets.
 * - Folder browsing grouped by device storage directories.
 * - Robust permission and error states.
 */
@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
    personalizationViewModel: PersonalizationViewModel = viewModel(),
    onTrackClick: (List<Track>, Int) -> Unit = { _, _ -> },
    onTrackMenu: ((Track) -> Unit)? = null,
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onRequestPermission: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    category: LibraryTab? = null,
    onBack: (() -> Unit)? = null,
    onPlayNext: (List<Track>) -> Unit = {},
    onAddToQueue: (List<Track>) -> Unit = {},
    onAddToPlaylist: (List<Track>) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    // When opened from the Library landing list, show just this one category (no tab row).
    LaunchedEffect(category) { category?.let { viewModel.selectTab(it) } }
    val activeTab = category ?: uiState.selectedTab
    val favoriteTracks by personalizationViewModel.favoriteTracks.collectAsState()

    var showSortMenu by remember { mutableStateOf(false) }
    var favoriteSortOrder by rememberSaveable { mutableStateOf(FavoriteSortOrder.RECENTLY_FAVORITED) }
    // Song ids picked in Select mode; null when not selecting (task 4.4).
    var selectedIds by remember(activeTab) { mutableStateOf<Set<String>?>(null) }
    BackHandler(enabled = selectedIds != null) { selectedIds = null }
    var selectedFormatFilter by rememberSaveable { mutableStateOf(ALL_FORMATS) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

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

    // Format chips come from the songs on screen (all songs, or favorites).
    val formatSource = if (activeTab == LibraryTab.FAVORITES) sortedFavoriteTracks else uiState.tracks
    val formatOptions = remember(formatSource) { formatFilterOptions(formatSource) }
    val activeFormat = effectiveFormatFilter(selectedFormatFilter, formatOptions)

    // Filtered track lists based on format pills & search query
    val filteredTracks = remember(uiState.tracks, activeFormat, searchQuery) {
        uiState.tracks.filter { track ->
            val matchesFormat = track.matchesFormat(activeFormat)
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase(Locale.ROOT)
                track.title.lowercase(Locale.ROOT).contains(q) ||
                        (track.artistName?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (track.albumTitle?.lowercase(Locale.ROOT)?.contains(q) == true)
            }
            matchesFormat && matchesSearch
        }
    }

    val filteredFavoriteTracks = remember(sortedFavoriteTracks, activeFormat, searchQuery) {
        sortedFavoriteTracks.filter { track ->
            val matchesFormat = track.matchesFormat(activeFormat)
            val matchesSearch = if (searchQuery.isBlank()) true else {
                val q = searchQuery.trim().lowercase(Locale.ROOT)
                track.title.lowercase(Locale.ROOT).contains(q) ||
                        (track.artistName?.lowercase(Locale.ROOT)?.contains(q) == true) ||
                        (track.albumTitle?.lowercase(Locale.ROOT)?.contains(q) == true)
            }
            matchesFormat && matchesSearch
        }
    }

    val filteredAlbums = remember(uiState.albums, searchQuery) {
        if (searchQuery.isBlank()) uiState.albums else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            uiState.albums.filter {
                it.title.lowercase(Locale.ROOT).contains(q) ||
                        (it.artistName?.lowercase(Locale.ROOT)?.contains(q) == true)
            }
        }
    }

    val filteredArtists = remember(uiState.artists, searchQuery) {
        if (searchQuery.isBlank()) uiState.artists else {
            val q = searchQuery.trim().lowercase(Locale.ROOT)
            uiState.artists.filter { it.name.lowercase(Locale.ROOT).contains(q) }
        }
    }

    // MediaStore URIs carry no folder, so look folders up only while the Folders tab is open
    val folderLookupContext = LocalContext.current.applicationContext
    val isFoldersTab = activeTab == LibraryTab.FOLDERS
    val trackUris = remember(uiState.tracks) { uiState.tracks.mapNotNull { it.localFile?.fileUri } }
    val resolvedFolders by produceState(initialValue = emptyMap<String, String>(), trackUris, isFoldersTab) {
        if (isFoldersTab) {
            value = withContext(Dispatchers.IO) {
                MediaFolderResolver(folderLookupContext).resolveFolders(trackUris)
            }
        }
    }

    // Group tracks by local storage folders
    val musicFolders = remember(uiState.tracks, searchQuery, resolvedFolders) {
        val folderMap = mutableMapOf<String, MutableList<Track>>()
        uiState.tracks.forEach { track ->
            val folderPath = folderPathFor(track.localFile?.fileUri, resolvedFolders)
            folderMap.getOrPut(folderPath) { mutableListOf() }.add(track)
        }

        folderMap.map { (path, tracksInFolder) ->
            MusicFolder(fullPath = path, folderName = folderDisplayName(path), tracks = tracksInFolder)
        }.filter {
            if (searchQuery.isBlank()) true else {
                it.folderName.contains(searchQuery.trim(), ignoreCase = true) ||
                        it.fullPath.contains(searchQuery.trim(), ignoreCase = true)
            }
        }.sortedBy { it.folderName.lowercase(Locale.ROOT) }
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

        // ── 1. Top App Bar: Title + Search + Sort + Profile ──────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (category != null) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onBack != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics { contentDescription = "Back to Library" }
                        ) {
                            Icon(
                                imageVector = HarmoniaIcons.ArrowBack,
                                contentDescription = null,
                                tint = colors.textPrimary
                            )
                        }
                    }
                    Text(
                        text = libraryCategoryTitle(category),
                        style = MaterialTheme.typography.headlineMedium,
                        color = colors.textPrimary,
                        maxLines = 1
                    )
                }
            } else {
                Column {
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            fontSize = 24.sp
                        ),
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Your music",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.primarySignal
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.xSmall)
            ) {
                // Inline Search Toggle Button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isSearchExpanded) colors.primarySignal else colors.surfaceContainer)
                        .hairlineBorder(colors.borderStructural, CircleShape)
                        .clickable {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) searchQuery = ""
                        }
                        .touchTarget48(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Search,
                        contentDescription = "Search in library",
                        tint = if (isSearchExpanded) colors.backgroundRoot else colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Sort Order Menu Button
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainer)
                            .hairlineBorder(colors.borderStructural, CircleShape)
                            .clickable { showSortMenu = true }
                            .touchTarget48()
                            .semantics { contentDescription = "Sort library items" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Sort,
                            contentDescription = "Sort",
                            tint = colors.primarySignal,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false },
                        modifier = Modifier
                            .background(colors.surfaceElevated)
                            .hairlineBorder(colors.borderStructural, RoundedCornerShape(8.dp), 1.dp)
                    ) {
                        when (activeTab) {
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
                            LibraryTab.FOLDERS -> {
                                DropdownMenuItem(
                                    text = { Text("Folder Name (A-Z)", color = colors.textPrimary) },
                                    onClick = { showSortMenu = false }
                                )
                            }
                        }
                    }
                }

                // Profile Avatar Button
                if (onProfileClick != null && category == null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainer)
                            .hairlineBorder(colors.borderStructural, CircleShape)
                            .clickable(onClick = onProfileClick)
                            .touchTarget48()
                            .semantics { contentDescription = "Open Profile and Settings" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Person,
                            contentDescription = "Profile",
                            tint = colors.primarySignal,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ── 2. Animated Inline Search Bar ────────────────────────────────────
        AnimatedVisibility(
            visible = isSearchExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(spacing.small))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Filter ${libraryCategoryTitle(activeTab).lowercase()}\u2026",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = HarmoniaIcons.Search,
                            contentDescription = null,
                            tint = colors.primarySignal,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = HarmoniaIcons.Close,
                                    contentDescription = "Clear search",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfaceContainer,
                        unfocusedContainerColor = colors.surfaceContainer,
                        focusedBorderColor = colors.primarySignal,
                        unfocusedBorderColor = colors.borderStructural,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            }
        }

        val isSongList = activeTab == LibraryTab.TRACKS || activeTab == LibraryTab.FAVORITES
        val currentSelection = selectedIds
        if (currentSelection != null && isSongList) {
            val visibleSongs = if (activeTab == LibraryTab.TRACKS) filteredTracks else filteredFavoriteTracks
            val picked = visibleSongs.filter { it.id in currentSelection }
            SelectionBar(
                selectedCount = picked.size,
                allSelected = visibleSongs.isNotEmpty() && picked.size == visibleSongs.size,
                inFavorites = activeTab == LibraryTab.FAVORITES,
                onSelectAll = { selectedIds = visibleSongs.map { it.id }.toSet() },
                onClearAll = { selectedIds = emptySet() },
                onDone = { selectedIds = null },
                onPlay = { onTrackClick(picked, 0); selectedIds = null },
                onPlayNext = { onPlayNext(picked); selectedIds = null },
                onAddToQueue = { onAddToQueue(picked); selectedIds = null },
                onAddToPlaylist = { onAddToPlaylist(picked); selectedIds = null },
                onFavorite = {
                    val makeFavorite = activeTab != LibraryTab.FAVORITES
                    picked.forEach { personalizationViewModel.setFavorite(makeFavorite, it.id) }
                    selectedIds = null
                }
            )
        } else if (category != null && activeTab != LibraryTab.FOLDERS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                modifier = Modifier.padding(top = spacing.small)
            ) {
                Text(
                    text = "Sorted by ${sortLabel(activeTab, uiState, favoriteSortOrder)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.primarySignal,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { showSortMenu = true }
                        .background(colors.surfaceContainer)
                        .padding(horizontal = spacing.medium, vertical = 6.dp)
                        .semantics { contentDescription = "Change sort order" }
                )
                if (isSongList) {
                    Text(
                        text = "Select",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { selectedIds = emptySet() }
                            .background(colors.surfaceContainer)
                            .padding(horizontal = spacing.medium, vertical = 6.dp)
                            .semantics {
                                role = Role.Button
                                contentDescription = "Select songs"
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.medium))

        // ── 3. Content States (Loading, Permission, Empty, Active) ───────────
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
                        errorMessage = friendlyScanError(uiState.scanErrorMessage),
                        onRetry = { viewModel.retryScan() }
                    )
                }
                else -> {
                    EmptyLibraryState(message = "No songs found")
                }
            }
        } else {
            // Status Banners
            if (uiState.isScanning) {
                ScanningBanner()
                Spacer(modifier = Modifier.height(spacing.small))
            } else if (uiState.isScanFailed) {
                ScanErrorBanner(
                    errorMessage = friendlyScanError(uiState.scanErrorMessage),
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

            // ── 4. Sub-Tabs: Tracks, Albums, Artists, Folders, Favorites ─────
            val tabs = listOf(
                LibraryTab.TRACKS to "Tracks (${uiState.tracks.size})",
                LibraryTab.ALBUMS to "Albums (${uiState.albums.size})",
                LibraryTab.ARTISTS to "Artists (${uiState.artists.size})",
                LibraryTab.FOLDERS to "Folders (${musicFolders.size})",
                LibraryTab.FAVORITES to "Favorites (${favoriteTracks.size})"
            )
            val selectedIndex = tabs.indexOfFirst { it.first == uiState.selectedTab }.coerceAtLeast(0)

            if (category == null) ScrollableTabRow(
                selectedTabIndex = selectedIndex,
                containerColor = colors.backgroundRoot,
                contentColor = colors.primarySignal,
                edgePadding = 0.dp,
                divider = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.borderStructural)
                    )
                },
                indicator = { tabPositions ->
                    if (selectedIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                            color = colors.primarySignal,
                            height = 3.dp
                        )
                    }
                }
            ) {
                tabs.forEach { (tab, title) ->
                    Tab(
                        selected = uiState.selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (uiState.selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        },
                        selectedContentColor = colors.primarySignal,
                        unselectedContentColor = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.small))

            // ── 5. Audio Format Filter Pills (Active for Tracks & Favorites) ─
            if ((activeTab == LibraryTab.TRACKS || activeTab == LibraryTab.FAVORITES) && formatOptions.isNotEmpty()) {
                FormatFilterPillsRow(
                    options = formatOptions,
                    selected = activeFormat,
                    onSelect = { selectedFormatFilter = it }
                )
                Spacer(modifier = Modifier.height(spacing.small))
            }

            // ── 6. Tab Content Switching ─────────────────────────────────────
            when (activeTab) {
                LibraryTab.TRACKS -> TracksTabContent(
                    tracks = filteredTracks,
                    favoriteTrackIds = favoriteTrackIds,
                    onToggleFavorite = { track -> personalizationViewModel.toggleFavorite(track.id) },
                    unmountedSourceIds = uiState.unmountedSourceIds,
                    disabledSourceIds = uiState.disabledSourceIds,
                    onTrackClick = onTrackClick,
                    onTrackMenu = onTrackMenu,
                    indexKeyOf = when (uiState.trackSortOrder) {
                        TrackSortOrder.TITLE_ASC -> { track: Track -> track.title }
                        TrackSortOrder.ARTIST_ASC -> { track: Track -> track.artistName }
                        else -> null
                    },
                    selectedIds = selectedIds,
                    onToggleSelect = { track -> selectedIds = selectedIds?.toggled(track.id) },
                    onPlayNextSong = { track -> onPlayNext(listOf(track)) }
                )
                LibraryTab.FAVORITES -> FavoritesTabContent(
                    tracks = filteredFavoriteTracks,
                    onToggleFavorite = { track -> personalizationViewModel.toggleFavorite(track.id) },
                    onBrowseTracks = { viewModel.selectTab(LibraryTab.TRACKS) },
                    unmountedSourceIds = uiState.unmountedSourceIds,
                    disabledSourceIds = uiState.disabledSourceIds,
                    onTrackClick = onTrackClick,
                    onTrackMenu = onTrackMenu,
                    indexKeyOf = when (favoriteSortOrder) {
                        FavoriteSortOrder.TITLE_ASC -> { track: Track -> track.title }
                        FavoriteSortOrder.ARTIST_ASC -> { track: Track -> track.artistName }
                        else -> null
                    },
                    selectedIds = selectedIds,
                    onToggleSelect = { track -> selectedIds = selectedIds?.toggled(track.id) },
                    onPlayNextSong = { track -> onPlayNext(listOf(track)) }
                )
                LibraryTab.ALBUMS -> AlbumsTabContent(
                    albums = filteredAlbums,
                    onAlbumClick = onAlbumClick,
                    indexKeyOf = when (uiState.albumSortOrder) {
                        AlbumSortOrder.TITLE_ASC -> { album: Album -> album.title }
                        AlbumSortOrder.ARTIST_ASC -> { album: Album -> album.artistName }
                        else -> null
                    }
                )
                LibraryTab.ARTISTS -> ArtistsTabContent(
                    artists = filteredArtists,
                    onArtistClick = onArtistClick,
                    indexKeyOf = if (uiState.artistSortOrder == ArtistSortOrder.NAME_ASC) { artist: Artist -> artist.name } else null
                )
                LibraryTab.FOLDERS -> FoldersTabContent(
                    folders = musicFolders,
                    onTrackClick = onTrackClick,
                    onTrackMenu = onTrackMenu
                )
            }
        }
    }
}

/** Format chips (All, MP3, FLAC...) built from the library; see [formatFilterOptions]. */
@Composable
private fun FormatFilterPillsRow(
    options: List<FormatFilterOption>,
    selected: String,
    onSelect: (String) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors

    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(options, key = { it.key }) { option ->
            val isSelected = option.key == selected
            Text(
                text = "${option.label}  ${option.count}",
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    isSelected && colors.isDark -> colors.backgroundRoot
                    isSelected -> colors.surfaceElevated
                    else -> colors.textPrimary
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) colors.primarySignal else colors.surfaceContainer)
                    .clickable { onSelect(option.key) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (option.key == ALL_FORMATS) {
                            "Show all formats, ${option.count} songs"
                        } else {
                            "Show only ${option.label}, ${option.count} songs"
                        }
                        this.selected = isSelected
                    }
            )
        }
    }
}

internal fun Set<String>.toggled(id: String): Set<String> = if (id in this) this - id else this + id

private fun badgeFor(track: Track, unmountedSourceIds: Set<String>, disabledSourceIds: Set<String>): String? {
    val sourceId = track.localFile?.sourceId
    return when {
        sourceId != null && disabledSourceIds.contains(sourceId) -> "Source Disabled"
        sourceId != null && unmountedSourceIds.contains(sourceId) -> "Storage Unmounted"
        track.availability == LocalFileAvailability.UNAVAILABLE -> "File Unavailable"
        else -> null
    }
}

/** Room on the right for the A–Z bar, and room at the bottom so it clears the mini player. */
private val AlphabetBarWidth = 22.dp
private val AlphabetBarBottomInset = 88.dp

@Composable
internal fun TracksTabContent(
    tracks: List<Track>,
    favoriteTrackIds: Set<String> = emptySet(),
    onToggleFavorite: ((Track) -> Unit)? = null,
    unmountedSourceIds: Set<String> = emptySet(),
    disabledSourceIds: Set<String> = emptySet(),
    onTrackClick: (List<Track>, Int) -> Unit,
    onTrackMenu: ((Track) -> Unit)? = null,
    indexKeyOf: ((Track) -> String?)? = null,
    selectedIds: Set<String>? = null,
    onToggleSelect: (Track) -> Unit = {},
    onPlayNextSong: ((Track) -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No matching songs",
                style = MaterialTheme.typography.bodyMedium,
                color = HarmoniaThemeTokens.colors.textSecondary
            )
        }
    } else {
        SongList(
            tracks = tracks,
            isFavorite = { favoriteTrackIds.contains(it.id) },
            onToggleFavorite = onToggleFavorite,
            unmountedSourceIds = unmountedSourceIds,
            disabledSourceIds = disabledSourceIds,
            onTrackClick = onTrackClick,
            onTrackMenu = onTrackMenu,
            indexKeyOf = indexKeyOf,
            selectedIds = selectedIds,
            onToggleSelect = onToggleSelect,
            onPlayNextSong = onPlayNextSong
        )
    }
}

@Composable
internal fun FavoritesTabContent(
    tracks: List<Track>,
    onToggleFavorite: (Track) -> Unit,
    onBrowseTracks: () -> Unit,
    unmountedSourceIds: Set<String> = emptySet(),
    disabledSourceIds: Set<String> = emptySet(),
    onTrackClick: (List<Track>, Int) -> Unit,
    onTrackMenu: ((Track) -> Unit)? = null,
    indexKeyOf: ((Track) -> String?)? = null,
    selectedIds: Set<String>? = null,
    onToggleSelect: (Track) -> Unit = {},
    onPlayNextSong: ((Track) -> Unit)? = null
) {
    if (tracks.isEmpty()) {
        EmptyFavoritesState(onBrowseTracks = onBrowseTracks)
    } else {
        SongList(
            tracks = tracks,
            isFavorite = { true },
            onToggleFavorite = onToggleFavorite,
            unmountedSourceIds = unmountedSourceIds,
            disabledSourceIds = disabledSourceIds,
            onTrackClick = onTrackClick,
            onTrackMenu = onTrackMenu,
            indexKeyOf = indexKeyOf,
            selectedIds = selectedIds,
            onToggleSelect = onToggleSelect,
            onPlayNextSong = onPlayNextSong
        )
    }
}

/** Song list with the A–Z bar (when sorted alphabetically) and Select mode (task 4.4). */
@Composable
private fun SongList(
    tracks: List<Track>,
    isFavorite: (Track) -> Boolean,
    onToggleFavorite: ((Track) -> Unit)?,
    unmountedSourceIds: Set<String>,
    disabledSourceIds: Set<String>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onTrackMenu: ((Track) -> Unit)?,
    indexKeyOf: ((Track) -> String?)?,
    selectedIds: Set<String>?,
    onToggleSelect: (Track) -> Unit,
    onPlayNextSong: ((Track) -> Unit)? = null
) {
    val listState = rememberLazyListState()
    val positions = remember(tracks, indexKeyOf) {
        indexKeyOf?.let { key -> alphabetPositions(tracks.map(key)) } ?: emptyMap()
    }
    val showBar = selectedIds == null && shouldShowAlphabetBar(positions)

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = if (showBar) AlphabetBarWidth else 0.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            itemsIndexed(tracks, key = { _, track -> track.id }) { index, track ->
                val badge = badgeFor(track, unmountedSourceIds, disabledSourceIds)
                SongSwipeActions(
                    onPlayNext = onPlayNextSong?.let { action -> { action(track) } },
                    enabled = selectedIds == null
                ) {
                SongListRow(
                    track = track,
                    onClick = {
                        if (selectedIds != null) onToggleSelect(track) else onTrackClick(tracks, index)
                    },
                    statusText = badge,
                    isStatusError = badge == "File Unavailable",
                    isFavorite = isFavorite(track),
                    onToggleFavorite = onToggleFavorite?.let { action -> { action(track) } },
                    onMenuClick = onTrackMenu?.let { menu -> { menu(track) } },
                    selected = selectedIds?.contains(track.id)
                )
                }
            }
        }
        if (showBar) {
            AlphabetScroller(
                positions = positions,
                onJumpTo = { listState.scrollToItem(it) },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(bottom = AlphabetBarBottomInset)
            )
        }
    }
}

/** Shown instead of "Sorted by" while picking songs: count, select all, and what to do with them. */
@Composable
private fun SelectionBar(
    selectedCount: Int,
    allSelected: Boolean,
    inFavorites: Boolean,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onDone: () -> Unit,
    onPlay: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onFavorite: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val enabled = selectedCount > 0
    Column(modifier = Modifier.padding(top = spacing.small)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when (selectedCount) {
                    0 -> "Select songs"
                    1 -> "1 song selected"
                    else -> "$selectedCount songs selected"
                },
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            TextButton(onClick = if (allSelected) onClearAll else onSelectAll) {
                Text(if (allSelected) "Clear" else "Select all", color = colors.primarySignal)
            }
            TextButton(onClick = onDone) {
                Text("Done", color = colors.primarySignal, fontWeight = FontWeight.Bold)
            }
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            item { SelectionAction("Play", HarmoniaIcons.Play, enabled, onPlay) }
            item { SelectionAction("Play next", HarmoniaIcons.PlayNext, enabled, onPlayNext) }
            item { SelectionAction("Add to queue", HarmoniaIcons.AddToQueue, enabled, onAddToQueue) }
            item { SelectionAction("Add to playlist", HarmoniaIcons.PlaylistAdd, enabled, onAddToPlaylist) }
            item {
                SelectionAction(
                    label = if (inFavorites) "Remove from favorites" else "Add to favorites",
                    icon = if (inFavorites) HarmoniaIcons.FavoriteBorder else HarmoniaIcons.Favorite,
                    enabled = enabled,
                    onClick = onFavorite
                )
            }
        }
    }
}

@Composable
private fun SelectionAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    val tint = if (enabled) colors.textPrimary else colors.textDisabled
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainer)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) { role = Role.Button }
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = if (enabled) colors.primarySignal else tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}

@Composable
private fun AlbumsTabContent(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit,
    indexKeyOf: ((Album) -> String?)? = null
) {
    val spacing = HarmoniaThemeTokens.spacing

    if (albums.isEmpty()) {
        EmptyLibraryState(message = "No albums yet")
    } else {
        val gridState = rememberLazyGridState()
        val positions = remember(albums, indexKeyOf) {
            indexKeyOf?.let { key -> alphabetPositions(albums.map(key)) } ?: emptyMap()
        }
        val showBar = shouldShowAlphabetBar(positions)
        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = AlbumGridMinCellWidth),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = if (showBar) AlphabetBarWidth else 0.dp),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                verticalArrangement = Arrangement.spacedBy(spacing.medium),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumGridItem(album = album, onClick = { onAlbumClick(album) })
                }
            }
            if (showBar) {
                AlphabetScroller(
                    positions = positions,
                    onJumpTo = { gridState.scrollToItem(it) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = AlphabetBarBottomInset)
                )
            }
        }
    }
}

@Composable
private fun ArtistsTabContent(
    artists: List<Artist>,
    onArtistClick: (Artist) -> Unit,
    indexKeyOf: ((Artist) -> String?)? = null
) {
    if (artists.isEmpty()) {
        EmptyLibraryState(message = "No artists yet")
    } else {
        val listState = rememberLazyListState()
        val positions = remember(artists, indexKeyOf) {
            indexKeyOf?.let { key -> alphabetPositions(artists.map(key)) } ?: emptyMap()
        }
        val showBar = shouldShowAlphabetBar(positions)
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = if (showBar) AlphabetBarWidth else 0.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(artists, key = { it.id }) { artist ->
                    ArtistListRow(artist = artist, onClick = { onArtistClick(artist) })
                }
            }
            if (showBar) {
                AlphabetScroller(
                    positions = positions,
                    onJumpTo = { listState.scrollToItem(it) },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(bottom = AlphabetBarBottomInset)
                )
            }
        }
    }
}

@Composable
private fun FoldersTabContent(
    folders: List<MusicFolder>,
    onTrackClick: (List<Track>, Int) -> Unit,
    onTrackMenu: ((Track) -> Unit)? = null
) {
    val spacing = HarmoniaThemeTokens.spacing

    if (folders.isEmpty()) {
        EmptyLibraryState(message = "No music folders found")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(folders, key = { it.fullPath }) { folder ->
                var isFolderExpanded by remember { mutableStateOf(false) }

                Column {
                    CollectionListRow(
                        title = folder.folderName,
                        subtitle = "${folder.tracks.size} ${if (folder.tracks.size == 1) "song" else "songs"} · ${folder.fullPath}",
                        artworkHash = null,
                        placeholderIcon = HarmoniaIcons.Folder,
                        description = "Folder ${folder.folderName}, ${folder.tracks.size} songs. " +
                            if (isFolderExpanded) "Tap to hide songs" else "Tap to show songs",
                        onClick = { isFolderExpanded = !isFolderExpanded },
                        showChevron = false
                    )
                    AnimatedVisibility(visible = isFolderExpanded) {
                        Column(modifier = Modifier.padding(start = spacing.large)) {
                            folder.tracks.forEachIndexed { idx, track ->
                                SongListRow(
                                    track = track,
                                    onClick = { onTrackClick(folder.tracks, idx) },
                                    onMenuClick = onTrackMenu?.let { menu -> { menu(track) } }
                                )
                            }
                        }
                    }
                }
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
    onClick: () -> Unit,
    onMenuClick: (() -> Unit)? = null
) {
    SongListRow(
        track = track,
        onClick = onClick,
        statusText = badgeText,
        isStatusError = badgeText == "File Unavailable",
        isFavorite = isFavorite ?: false,
        onToggleFavorite = onToggleFavorite,
        onMenuClick = onMenuClick
    )
}

/**
 * Full-screen message used for the Library's empty, permission and error states: a round icon,
 * a short title, one friendly line and (optionally) one button. No card or border.
 */
@Composable
private fun LibraryMessage(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    actionLabel: String? = null,
    actionDescription: String? = null,
    onAction: (() -> Unit)? = null,
    iconTint: androidx.compose.ui.graphics.Color = HarmoniaThemeTokens.colors.primarySignal
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = spacing.large, vertical = spacing.xLarge)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
            }
            Spacer(modifier = Modifier.height(spacing.standard))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(spacing.large))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primarySignal,
                        contentColor = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated
                    ),
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .height(48.dp)
                        .semantics { if (actionDescription != null) contentDescription = actionDescription }
                ) {
                    Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
                }
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
    if (permissionState == StoragePermissionState.SETTINGS_REQUIRED) {
        LibraryMessage(
            icon = HarmoniaIcons.Settings,
            title = "Turn on music access in Settings",
            body = "Harmonia can't see your songs because access is turned off. In Settings, open Permissions, " +
                "then Music and audio, and choose Allow.",
            actionLabel = "Open Settings",
            actionDescription = "Open system settings",
            onAction = onOpenSettings
        )
    } else {
        LibraryMessage(
            icon = HarmoniaIcons.MusicNote,
            title = "Let Harmonia find your music",
            body = "Harmonia plays the songs saved on this phone. Allow access to your music and they'll show up here. " +
                "Nothing leaves your phone.",
            actionLabel = "Allow access",
            actionDescription = "Grant audio permission",
            onAction = onRequestPermission
        )
    }
}

@Composable
private fun ScanningState() {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(spacing.large)) {
            CircularProgressIndicator(color = colors.primarySignal, modifier = Modifier.size(40.dp))
            Spacer(modifier = Modifier.height(spacing.standard))
            Text(text = "Finding your music", style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = "Looking through the songs on your phone. This takes a moment the first time.",
                style = MaterialTheme.typography.bodyMedium,
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
    LibraryMessage(
        icon = HarmoniaIcons.Info,
        title = "Couldn't look for songs",
        body = errorMessage,
        actionLabel = "Try again",
        actionDescription = "Retry scan",
        onAction = onRetry,
        iconTint = HarmoniaThemeTokens.colors.statusError
    )
}

@Composable
private fun EmptyLibraryState(message: String) {
    LibraryMessage(
        icon = HarmoniaIcons.MusicNote,
        title = message,
        body = "Copy music to your phone (for example into the Music folder), or add a folder in Settings, " +
            "and it will show up here."
    )
}

@Composable
internal fun EmptyFavoritesState(
    onBrowseTracks: () -> Unit
) {
    LibraryMessage(
        icon = HarmoniaIcons.Favorite,
        title = "No favorites yet",
        body = "Tap the heart on any song and it will show up here.",
        actionLabel = "Browse songs",
        actionDescription = "Browse all tracks in library",
        onAction = onBrowseTracks
    )
}

/** Thin status line above the list (no border), with an optional small action. */
@Composable
private fun LibraryStatusLine(
    text: String,
    textColor: androidx.compose.ui.graphics.Color,
    showProgress: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceContainer)
            .padding(horizontal = spacing.medium, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showProgress) {
            CircularProgressIndicator(color = colors.primarySignal, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(spacing.small))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(text = actionLabel, color = colors.primarySignal, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ScanningBanner() {
    LibraryStatusLine(
        text = "Updating your library\u2026",
        textColor = HarmoniaThemeTokens.colors.textSecondary,
        showProgress = true
    )
}

@Composable
private fun ScanErrorBanner(
    errorMessage: String,
    onRetry: () -> Unit
) {
    LibraryStatusLine(
        text = errorMessage,
        textColor = HarmoniaThemeTokens.colors.statusError,
        actionLabel = "Try again",
        onAction = onRetry
    )
}

@Composable
private fun PermissionWarningBanner(
    permissionState: StoragePermissionState,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val isSettingsRequired = permissionState == StoragePermissionState.SETTINGS_REQUIRED
    LibraryStatusLine(
        text = "Allow access to your music so new songs show up",
        textColor = HarmoniaThemeTokens.colors.statusWarning,
        actionLabel = if (isSettingsRequired) "Settings" else "Allow",
        onAction = if (isSettingsRequired) onOpenSettings else onRequestPermission
    )
}

/** Scan problems in plain words; the technical message stays in the view model for debugging. */
internal fun friendlyScanError(raw: String?): String {
    val text = raw.orEmpty().lowercase(Locale.ROOT)
    return when {
        "file(s) failed" in text || "failed during" in text ->
            "Some songs couldn't be read. The rest of your library is fine."
        "permission" in text -> "Harmonia needs access to your music to look for songs."
        else -> "Something went wrong while looking for songs."
    }
}

/** User-facing name of a library category (Apple Music says "Songs", not "Tracks"). */
internal fun libraryCategoryTitle(tab: LibraryTab): String = when (tab) {
    LibraryTab.TRACKS -> "Songs"
    LibraryTab.ALBUMS -> "Albums"
    LibraryTab.ARTISTS -> "Artists"
    LibraryTab.FOLDERS -> "Folders"
    LibraryTab.FAVORITES -> "Favorites"
}

/** Label for the current sort order of [tab], shown as "Sorted by …". */
internal fun sortLabel(tab: LibraryTab, state: LibraryUiState, favoriteSortOrder: FavoriteSortOrder): String = when (tab) {
    LibraryTab.TRACKS -> when (state.trackSortOrder) {
        TrackSortOrder.TITLE_ASC -> "Title (A-Z)"
        TrackSortOrder.TITLE_DESC -> "Title (Z-A)"
        TrackSortOrder.ARTIST_ASC -> "Artist"
        TrackSortOrder.DATE_ADDED_DESC -> "Recently added"
        TrackSortOrder.DURATION_DESC -> "Duration"
    }
    LibraryTab.ALBUMS -> when (state.albumSortOrder) {
        AlbumSortOrder.TITLE_ASC -> "Album title"
        AlbumSortOrder.ARTIST_ASC -> "Artist"
        AlbumSortOrder.YEAR_DESC -> "Release year"
    }
    LibraryTab.ARTISTS -> when (state.artistSortOrder) {
        ArtistSortOrder.NAME_ASC -> "Name (A-Z)"
        ArtistSortOrder.NAME_DESC -> "Name (Z-A)"
    }
    LibraryTab.FAVORITES -> when (favoriteSortOrder) {
        FavoriteSortOrder.RECENTLY_FAVORITED -> "Recently favorited"
        FavoriteSortOrder.TITLE_ASC -> "Title (A-Z)"
        FavoriteSortOrder.ARTIST_ASC -> "Artist"
    }
    LibraryTab.FOLDERS -> "Folder name"
}
