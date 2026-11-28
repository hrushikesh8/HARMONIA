package org.harmonia.player.presentation.playlists

import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import org.harmonia.player.presentation.components.DragAutoScroll
import org.harmonia.player.presentation.components.TrackMenuButton
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.PlaylistItem
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.util.TrackDisplayUtils
import java.util.Locale

/**
 * Playlist page (Apple Music layout): artwork collage, name, Play / Shuffle, then the songs.
 * Drag the handle to reorder (committed once on release), swipe a song away to remove it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onPlay: (tracks: List<Track>, startIndex: Int) -> Unit,
    onShuffle: (tracks: List<Track>) -> Unit,
    onTrackMenu: ((Track) -> Unit)? = null,
    /** Shows "Removed …" with an Undo button; the second argument puts the song back. */
    onRemoved: (message: String, undo: () -> Unit) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = viewModel(
        key = "playlist_detail_$playlistId",
        factory = PlaylistDetailViewModel.Factory(playlistId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddTracksDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val playlist = uiState.playlist
    val items = uiState.items
    val validTracks = remember(items) { items.mapNotNull { it.track } }
    val totalSeconds = remember(items) { items.mapNotNull { it.track?.durationSec }.sum() }
    val collage = remember(items, playlist?.artworkHash) { collageArtwork(playlist?.artworkHash, items) }

    // Local order while dragging; reset whenever the saved order changes.
    var workingItems by remember(items) { mutableStateOf(items) }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    fun dragBy(deltaY: Float) {
        val id = draggingId ?: return
        dragOffset += deltaY
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == id } ?: return
        val draggedCenter = dragged.offset + dragOffset + dragged.size / 2f
        val target = visible.firstOrNull { other ->
            other.key != id && (other.key as? String)?.let { key -> workingItems.any { it.id == key } } == true &&
                draggedCenter >= other.offset && draggedCenter <= other.offset + other.size
        } ?: return
        val from = workingItems.indexOfFirst { it.id == id }
        val to = workingItems.indexOfFirst { it.id == target.key }
        if (from < 0 || to < 0) return
        workingItems = workingItems.toMutableList().apply { add(to, removeAt(from)) }
        dragOffset += (dragged.offset - target.offset)
    }

    fun endDrag() {
        val id = draggingId ?: return
        val to = workingItems.indexOfFirst { it.id == id }
        if (dragStartIndex >= 0 && to >= 0 && to != dragStartIndex) {
            viewModel.moveTrack(dragStartIndex, to)
        }
        draggingId = null
        dragStartIndex = -1
        dragOffset = 0f
    }

    // Scroll the list when a dragged song reaches the top or bottom edge.
    DragAutoScroll(
        listState = listState,
        draggingKey = draggingId,
        dragOffset = { dragOffset },
        onScrolled = { dragBy(it) }
    )

    if (uiState.isLoading && playlist == null) {
        Box(
            modifier = modifier.fillMaxSize().background(colors.backgroundRoot),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = colors.primarySignal)
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
    ) {
        // Top bar
        item(key = "top_bar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.xSmall, vertical = spacing.xSmall),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "Navigate Back" }
                ) {
                    Icon(imageVector = HarmoniaIcons.ArrowBack, contentDescription = null, tint = colors.textPrimary)
                }
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.semantics { contentDescription = "Playlist Options" }
                    ) {
                        Icon(imageVector = HarmoniaIcons.MoreVert, contentDescription = null, tint = colors.textPrimary)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(colors.surfaceElevated)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add songs", color = colors.textPrimary) },
                            leadingIcon = { Icon(HarmoniaIcons.PlaylistAdd, contentDescription = null, tint = colors.primarySignal) },
                            onClick = {
                                showMenu = false
                                showAddTracksDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename playlist", color = colors.textPrimary) },
                            leadingIcon = { Icon(HarmoniaIcons.Edit, contentDescription = null, tint = colors.primarySignal) },
                            onClick = {
                                showMenu = false
                                showRenameDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete playlist", color = colors.statusError) },
                            leadingIcon = { Icon(HarmoniaIcons.Delete, contentDescription = null, tint = colors.statusError) },
                            onClick = {
                                showMenu = false
                                showDeleteDialog = true
                            }
                        )
                    }
                }
            }
        }

        // Header: collage, name, details, actions
        item(key = "header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.standard),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val side = minOf(maxWidth * 0.68f, 280.dp)
                    PlaylistCollage(
                        artworkHashes = collage,
                        side = side,
                        contentDescription = "${playlist?.name ?: "Playlist"} cover"
                    )
                }
                Spacer(modifier = Modifier.height(spacing.large))
                Text(
                    text = playlist?.name ?: "Playlist",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!playlist?.description.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = playlist?.description.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playlistSummary(items.size, totalSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(spacing.large))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    Button(
                        onClick = { onPlay(validTracks, 0) },
                        enabled = validTracks.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Play Playlist" },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primarySignal,
                            contentColor = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated
                        )
                    ) {
                        Icon(HarmoniaIcons.Play, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Play", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = { onShuffle(validTracks) },
                        enabled = validTracks.isNotEmpty(),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .semantics { contentDescription = "Shuffle Playlist" },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceContainer,
                            contentColor = colors.primarySignal
                        )
                    ) {
                        Icon(HarmoniaIcons.Shuffle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Shuffle", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (items.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.medium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Drag ≡ to reorder · swipe a song to remove it",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Add songs",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primarySignal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAddTracksDialog = true }
                                .padding(horizontal = spacing.small, vertical = spacing.small)
                                .semantics { contentDescription = "Add tracks to playlist" }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(spacing.small))
            }
        }

        if (items.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "This playlist is empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Add songs from your library to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Button(
                        onClick = { showAddTracksDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.primarySignal,
                            contentColor = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.semantics { contentDescription = "Add tracks to playlist" }
                    ) {
                        Text("Add songs", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        } else {
            itemsIndexed(workingItems, key = { _, item -> item.id }) { index, item ->
                val isDragging = item.id == draggingId
                val dismissState = rememberSwipeToDismissBoxState()
                Box(
                    modifier = if (isDragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                    } else {
                        Modifier.animateItem()
                    }
                ) {
                    SwipeToDismissBox(
                        state = dismissState,
                        gesturesEnabled = draggingId == null,
                        onDismiss = {
                            val title = item.track?.let { TrackDisplayUtils.formatDisplayTitle(it) } ?: "Song"
                            viewModel.removeTrack(item.trackId)
                            onRemoved("Removed \"$title\"") { viewModel.restoreTrack(item.trackId, index) }
                        },
                        backgroundContent = {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(colors.statusError.copy(alpha = 0.2f))
                                    .padding(horizontal = spacing.standard),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(HarmoniaIcons.Delete, contentDescription = null, tint = colors.statusError)
                                Icon(HarmoniaIcons.Delete, contentDescription = null, tint = colors.statusError)
                            }
                        }
                    ) {
                        PlaylistSongRow(
                            item = item,
                            isDragging = isDragging,
                            onMoveUp = if (index > 0) ({ viewModel.moveTrack(index, index - 1) }) else null,
                            onMoveDown = if (index < workingItems.lastIndex) ({ viewModel.moveTrack(index, index + 1) }) else null,
                            onClick = {
                                val trackIndex = validTracks.indexOfFirst { it.id == item.trackId }
                                if (trackIndex >= 0) onPlay(validTracks, trackIndex)
                            },
                            onMenu = item.track?.let { track -> onTrackMenu?.let { menu -> { menu(track) } } },
                            dragHandleModifier = Modifier.pointerInput(item.id) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingId = item.id
                                        dragStartIndex = workingItems.indexOfFirst { it.id == item.id }
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragBy(amount.y)
                                    },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { endDrag() }
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog && playlist != null) {
        RenamePlaylistDialog(
            currentName = playlist.name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                viewModel.renamePlaylist(newName)
                showRenameDialog = false
            }
        )
    }

    if (showDeleteDialog && playlist != null) {
        DeletePlaylistDialog(
            playlistName = playlist.name,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                viewModel.deletePlaylist(onDeleted = onBack)
                showDeleteDialog = false
            }
        )
    }

    if (showAddTracksDialog) {
        AddTracksDialog(
            availableTracks = uiState.availableLibraryTracks,
            existingTrackIds = items.map { it.trackId }.toSet(),
            onDismiss = { showAddTracksDialog = false },
            onAdd = { selectedIds ->
                viewModel.addTracks(selectedIds)
                showAddTracksDialog = false
            }
        )
    }
}

/**
 * Cover artwork for a playlist: its own cover if set, otherwise up to four different album
 * covers from its songs (shown as a 2x2 collage when there are four).
 */
internal fun collageArtwork(playlistArtwork: String?, items: List<PlaylistItem>): List<String> {
    if (playlistArtwork != null) return listOf(playlistArtwork)
    val distinct = items.mapNotNull { it.track?.artworkHash }.distinct()
    return if (distinct.size >= 4) distinct.take(4) else distinct.take(1)
}

/** "48 min", "1 hr 12 min", or "57 sec" for very short playlists. */
internal fun formatPlaylistDuration(totalSeconds: Double): String {
    val minutes = (totalSeconds / 60).toInt()
    return when {
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min"
        minutes > 0 -> "$minutes min"
        else -> "${totalSeconds.toInt()} sec"
    }
}

internal fun playlistSummary(songCount: Int, totalSeconds: Double): String {
    val songs = "$songCount ${if (songCount == 1) "song" else "songs"}"
    return if (totalSeconds > 0) "$songs, ${formatPlaylistDuration(totalSeconds)}" else songs
}

@Composable
private fun PlaylistCollage(artworkHashes: List<String>, side: Dp, contentDescription: String) {
    val colors = HarmoniaThemeTokens.colors
    Box(
        modifier = Modifier
            .size(side)
            .shadow(20.dp, RoundedCornerShape(12.dp), clip = false)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceElevated)
            .semantics { this.contentDescription = contentDescription }
    ) {
        if (artworkHashes.size >= 4) {
            Column {
                for (row in 0 until 2) {
                    Row {
                        for (col in 0 until 2) {
                            HarmoniaArtwork(
                                artworkHash = artworkHashes[row * 2 + col],
                                size = side / 2,
                                cornerRadius = 0.dp
                            )
                        }
                    }
                }
            }
        } else {
            HarmoniaArtwork(
                artworkHash = artworkHashes.firstOrNull(),
                modifier = Modifier.fillMaxSize(),
                size = side,
                cornerRadius = 0.dp,
                placeholderIcon = HarmoniaIcons.Playlists
            )
        }
    }
}

@Composable
private fun PlaylistSongRow(
    item: PlaylistItem,
    isDragging: Boolean,
    onClick: () -> Unit,
    onMenu: (() -> Unit)?,
    dragHandleModifier: Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val track = item.track
    val title = track?.let { TrackDisplayUtils.formatDisplayTitle(it) } ?: "Unavailable song"
    val subtitle = track?.let { listOfNotNull(it.artistName, formatSongDuration(it.durationSec)).joinToString(" · ") }
        ?: "Not on this phone right now"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Opaque so the swipe-to-remove background never shows through.
            .background(if (isDragging) colors.surfaceContainer else colors.backgroundRoot)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(start = spacing.standard, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HarmoniaArtwork(
            artworkHash = track?.artworkHash,
            size = 48.dp,
            cornerRadius = 6.dp,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (track != null) colors.textPrimary else colors.textDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onMenu != null) {
            TrackMenuButton(trackTitle = title, onClick = onMenu)
        }
        Box(
            modifier = dragHandleModifier
                .size(48.dp)
                .semantics {
                    contentDescription = "Drag to reorder $title"
                    // Screen readers can't drag, so reordering is also offered as actions.
                    customActions = listOfNotNull(
                        onMoveUp?.let { move -> CustomAccessibilityAction("Move up") { move(); true } },
                        onMoveDown?.let { move -> CustomAccessibilityAction("Move down") { move(); true } }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = HarmoniaIcons.DragHandle,
                contentDescription = null,
                tint = colors.textSecondary
            )
        }
    }
}

private fun formatSongDuration(durationSec: Double): String {
    val total = durationSec.toInt()
    return String.format(java.util.Locale.US, "%d:%02d", total / 60, total % 60)
}

@Composable
internal fun RenamePlaylistDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (newName: String) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Playlist", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.semantics { contentDescription = "Playlist Name Field" },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primarySignal,
                    unfocusedBorderColor = colors.borderStructural,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                )
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.semantics { contentDescription = "Confirm Rename" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primarySignal,
                    contentColor = colors.backgroundRoot
                )
            ) {
                Text("Rename", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surfaceElevated
    )
}

@Composable
internal fun DeletePlaylistDialog(
    playlistName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Playlist", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "Are you sure you want to delete \"$playlistName\"? This removes the playlist only. None of your music files or tracks will be deleted.",
                color = colors.textSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.statusError,
                    contentColor = colors.textPrimary
                )
            ) {
                Text("Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surfaceElevated
    )
}

@Composable
private fun AddTracksDialog(
    availableTracks: List<Track>,
    existingTrackIds: Set<String>,
    onDismiss: () -> Unit,
    onAdd: (selectedTrackIds: List<String>) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    var searchQuery by remember { mutableStateOf("") }
    val selectedIds = remember { mutableStateListOf<String>() }

    val filteredTracks = remember(availableTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            availableTracks
        } else {
            availableTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                    (it.artistName?.contains(searchQuery, ignoreCase = true) == true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tracks to Playlist", color = colors.textPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.height(380.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search catalog") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primarySignal,
                        unfocusedBorderColor = colors.borderStructural,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredTracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No tracks found", color = colors.textSecondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredTracks, key = { it.id }) { track ->
                            val isSelected = selectedIds.contains(track.id)
                            val isAlreadyInPlaylist = existingTrackIds.contains(track.id)
                            val displayTitle = TrackDisplayUtils.formatDisplayTitle(track)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) colors.surfaceContainer else colors.surfaceElevated)
                                    .clickable {
                                        if (isSelected) {
                                            selectedIds.remove(track.id)
                                        } else {
                                            selectedIds.add(track.id)
                                        }
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedIds.add(track.id) else selectedIds.remove(track.id)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = colors.primarySignal,
                                        checkmarkColor = colors.backgroundRoot
                                    )
                                )

                                Spacer(modifier = Modifier.width(6.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayTitle,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = colors.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = listOfNotNull(track.artistName, if (isAlreadyInPlaylist) "(In playlist)" else null)
                                            .joinToString(" • "),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isAlreadyInPlaylist) colors.primarySignal else colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primarySignal,
                    contentColor = colors.backgroundRoot
                )
            ) {
                Text("Add (${selectedIds.size})", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = colors.textSecondary)
            }
        },
        containerColor = colors.surfaceElevated
    )
}
