package org.harmonia.player.presentation.playlists

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.presentation.components.HarmoniaArtwork

@Composable
fun PlaylistsScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = viewModel(),
    onPlaylistClick: (Playlist) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<Playlist?>(null) }
    var playlistToDelete by remember { mutableStateOf<Playlist?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard)
    ) {
        Spacer(modifier = Modifier.height(spacing.standard))

        // Header: back, title (same as the other Library pages)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "Back to Library" }
                ) {
                    Icon(imageVector = HarmoniaIcons.ArrowBack, contentDescription = null, tint = colors.textPrimary)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Playlists", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                Text(text = "Made by you", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
            }
        }

        Spacer(modifier = Modifier.height(spacing.small))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primarySignal)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
            ) {
                // Apple Music style: "New Playlist…" is the first row of the list.
                item(key = "new_playlist") {
                    NewPlaylistRow(onClick = { showCreateDialog = true })
                }
                if (uiState.playlists.isEmpty()) {
                    item(key = "empty") {
                        Column(modifier = Modifier.padding(vertical = spacing.large)) {
                            Text(text = "No playlists yet", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                            Text(
                                text = "Create one to keep your favorite songs together.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
                items(uiState.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        coverHash = uiState.covers[playlist.id],
                        onClick = { onPlaylistClick(playlist) },
                        onRenameClick = { playlistToRename = playlist },
                        onDeleteClick = { playlistToDelete = playlist }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, desc ->
                viewModel.createPlaylist(name, desc)
                showCreateDialog = false
            }
        )
    }

    if (playlistToRename != null) {
        RenamePlaylistDialog(
            currentName = playlistToRename!!.name,
            onDismiss = { playlistToRename = null },
            onConfirm = { newName ->
                viewModel.renamePlaylist(playlistToRename!!.id, newName)
                playlistToRename = null
            }
        )
    }

    if (playlistToDelete != null) {
        DeletePlaylistDialog(
            playlistName = playlistToDelete!!.name,
            onDismiss = { playlistToDelete = null },
            onConfirm = {
                viewModel.deletePlaylist(playlistToDelete!!.id)
                playlistToDelete = null
            }
        )
    }
}

/** First row of the list, like Apple Music's "New Playlist…". */
@Composable
private fun NewPlaylistRow(onClick: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "Create new playlist"
            }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = HarmoniaIcons.PlaylistAdd, contentDescription = null, tint = colors.primarySignal, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.width(spacing.medium))
        Text(text = "New Playlist\u2026", style = MaterialTheme.typography.bodyLarge, color = colors.primarySignal)
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    coverHash: String?,
    onClick: () -> Unit,
    onRenameClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    var showMenu by remember { mutableStateOf(false) }
    val songs = "${playlist.trackCount} ${if (playlist.trackCount == 1) "song" else "songs"}"
    val subtitle = if (playlist.description.isNullOrBlank()) songs else "$songs \u00B7 ${playlist.description}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Playlist ${playlist.name}, $songs" }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HarmoniaArtwork(
            artworkHash = coverHash,
            size = 56.dp,
            cornerRadius = 6.dp,
            placeholderIcon = HarmoniaIcons.Playlists,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
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
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier
                    .touchTarget48()
                    .semantics { contentDescription = "Options for ${playlist.name}" }
            ) {
                Icon(imageVector = HarmoniaIcons.MoreVert, contentDescription = null, tint = colors.textSecondary)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                containerColor = colors.surfaceElevated
            ) {
                DropdownMenuItem(
                    text = { Text("Rename", color = colors.textPrimary) },
                    leadingIcon = { Icon(HarmoniaIcons.Edit, contentDescription = null, tint = colors.primarySignal) },
                    onClick = {
                        showMenu = false
                        onRenameClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = colors.statusError) },
                    leadingIcon = { Icon(HarmoniaIcons.Delete, contentDescription = null, tint = colors.statusError) },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String?) -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Create New Playlist", color = colors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primarySignal,
                        unfocusedBorderColor = colors.borderStructural,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (Optional)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primarySignal,
                        unfocusedBorderColor = colors.borderStructural,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name, description.ifBlank { null }) },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primarySignal,
                    contentColor = colors.backgroundRoot
                )
            ) {
                Text("Create", fontWeight = FontWeight.Bold)
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
