package org.harmonia.player.presentation.playlists

import androidx.compose.foundation.background
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

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBack: () -> Unit,
    onPlay: (tracks: List<Track>, startIndex: Int) -> Unit,
    onShuffle: (tracks: List<Track>) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistDetailViewModel = viewModel(factory = PlaylistDetailViewModel.Factory(playlistId))
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
    val heroArtworkHash = playlist?.artworkHash
        ?: items.firstOrNull { it.track?.artworkHash != null }?.track?.artworkHash

    val validTracks = remember(items) { items.mapNotNull { it.track } }
    val totalSeconds = remember(items) {
        items.mapNotNull { it.track?.durationSec }.sum()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard)
    ) {
        Spacer(modifier = Modifier.height(spacing.standard))

        // Top Navigation & Action Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .touchTarget48()
                    .semantics { contentDescription = "Navigate Back" }
            ) {
                Icon(
                    imageVector = HarmoniaIcons.SkipPrevious,
                    contentDescription = "Navigate Back",
                    tint = colors.textPrimary
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Playlist Options" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.MoreVert,
                        contentDescription = "Playlist Options",
                        tint = colors.textPrimary
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier
                        .background(colors.surfaceElevated)
                        .hairlineBorder(colors.borderStructural, RoundedCornerShape(8.dp))
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename Playlist", color = colors.textPrimary) },
                        leadingIcon = { Icon(HarmoniaIcons.Edit, contentDescription = null, tint = colors.primarySignal) },
                        onClick = {
                            showMenu = false
                            showRenameDialog = true
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete Playlist", color = colors.statusError) },
                        leadingIcon = { Icon(HarmoniaIcons.Delete, contentDescription = null, tint = colors.statusError) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }

        if (uiState.isLoading && playlist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primarySignal)
            }
            return
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
        ) {
            // Hero Section
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    HarmoniaArtwork(
                        artworkHash = heroArtworkHash,
                        modifier = Modifier
                            .size(160.dp)
                            .aspectRatio(1f),
                        size = 160.dp,
                        cornerRadius = 16.dp,
                        placeholderIcon = HarmoniaIcons.Playlists,
                        contentDescription = "${playlist?.name ?: "Playlist"} cover"
                    )

                    Spacer(modifier = Modifier.height(spacing.medium))

                    Text(
                        text = playlist?.name ?: "Playlist",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        ),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    val subtitle = buildString {
                        append("${items.size} ${if (items.size == 1) "track" else "tracks"}")
                        if (totalSeconds > 0) {
                            append(" • ${formatMinutes(totalSeconds)}")
                        }
                        if (!playlist?.description.isNullOrBlank()) {
                            append(" • ${playlist.description}")
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )

                    Spacer(modifier = Modifier.height(spacing.large))

                    // Transport & Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { if (validTracks.isNotEmpty()) onPlay(validTracks, 0) },
                            enabled = validTracks.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .touchTarget48()
                                .semantics { contentDescription = "Play Playlist" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primarySignal,
                                contentColor = colors.backgroundRoot
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(HarmoniaIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Play", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { if (validTracks.isNotEmpty()) onShuffle(validTracks) },
                            enabled = validTracks.isNotEmpty(),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .touchTarget48()
                                .semantics { contentDescription = "Shuffle Playlist" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.surfaceElevated,
                                contentColor = colors.textPrimary
                            ),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(HarmoniaIcons.Shuffle, contentDescription = null, tint = colors.primarySignal, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Shuffle", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showAddTracksDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .touchTarget48()
                                .semantics { contentDescription = "Add tracks to playlist" },
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(colors.borderStructural)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.primarySignal)
                        ) {
                            Text("+ Add", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))
            }

            // Playlist Track List
            if (items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("This playlist has no tracks yet", color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { showAddTracksDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primarySignal,
                                    contentColor = colors.backgroundRoot
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.touchTarget48()
                            ) {
                                Text("Add Tracks", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                    PlaylistItemRow(
                        item = item,
                        index = index,
                        canMoveUp = index > 0,
                        canMoveDown = index < items.size - 1,
                        onClick = {
                            val trackIndex = validTracks.indexOfFirst { it.id == item.trackId }
                            if (trackIndex >= 0) {
                                onPlay(validTracks, trackIndex)
                            }
                        },
                        onMoveUp = { viewModel.moveTrackUp(index) },
                        onMoveDown = { viewModel.moveTrackDown(index) },
                        onRemove = { viewModel.removeTrack(item.trackId) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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

@Composable
private fun PlaylistItemRow(
    item: PlaylistItem,
    index: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(10.dp)
    val displayTitle = item.track?.let { TrackDisplayUtils.formatDisplayTitle(it) } ?: item.trackId

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(colors.borderStructural, cardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play track $displayTitle" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = colors.primarySignal,
                modifier = Modifier.width(24.dp)
            )

            HarmoniaArtwork(
                artworkHash = item.track?.artworkHash,
                size = 40.dp,
                cornerRadius = 6.dp,
                contentDescription = null
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.track?.artistName ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Move Up button
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier
                    .size(36.dp)
                    .touchTarget48()
                    .semantics { contentDescription = "Move track up" }
            ) {
                Icon(
                    imageVector = HarmoniaIcons.ArrowUp,
                    contentDescription = "Move track up",
                    tint = if (canMoveUp) colors.textPrimary else colors.textDisabled,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Move Down button
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier
                    .size(36.dp)
                    .touchTarget48()
                    .semantics { contentDescription = "Move track down" }
            ) {
                Icon(
                    imageVector = HarmoniaIcons.ArrowDown,
                    contentDescription = "Move track down",
                    tint = if (canMoveDown) colors.textPrimary else colors.textDisabled,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Remove button
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(36.dp)
                    .touchTarget48()
                    .semantics { contentDescription = "Remove track from playlist" }
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Close,
                    contentDescription = "Remove track from playlist",
                    tint = colors.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
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

private fun formatMinutes(seconds: Double): String {
    val mins = (seconds / 60).toInt()
    return "$mins min"
}
