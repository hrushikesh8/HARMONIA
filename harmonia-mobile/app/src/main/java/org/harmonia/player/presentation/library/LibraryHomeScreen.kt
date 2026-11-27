package org.harmonia.player.presentation.library

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.AlbumGridMinCellWidth
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.playlists.PlaylistsViewModel

/** Where a row on the Library landing page leads. */
sealed interface LibraryDestination {
    data object Playlists : LibraryDestination
    data class Category(val tab: LibraryTab) : LibraryDestination
}

private data class LibraryEntry(
    val label: String,
    val icon: ImageVector,
    val count: Int?,
    val destination: LibraryDestination
)

private const val RECENTLY_ADDED_ALBUMS = 12

/**
 * Library landing page (Apple Music style): a short list of categories, then the albums added
 * most recently. Tapping a row opens that category as its own page.
 *
 * Until the library has songs, this shows the regular library screen instead, which owns the
 * permission request, scanning and empty states.
 */
@Composable
fun LibraryHomeScreen(
    onOpen: (LibraryDestination) -> Unit,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(),
    personalizationViewModel: PersonalizationViewModel = viewModel(),
    playlistsViewModel: PlaylistsViewModel = viewModel(),
    onTrackClick: (List<Track>, Int) -> Unit = { _, _ -> },
    onTrackMenu: ((Track) -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val favoriteTracks by personalizationViewModel.favoriteTracks.collectAsState()
    val playlistsState by playlistsViewModel.uiState.collectAsState()

    if (uiState.tracks.isEmpty()) {
        LibraryScreen(
            modifier = modifier,
            viewModel = viewModel,
            personalizationViewModel = personalizationViewModel,
            onTrackClick = onTrackClick,
            onTrackMenu = onTrackMenu,
            onAlbumClick = onAlbumClick,
            onProfileClick = onProfileClick
        )
        return
    }

    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    val entries = listOf(
        LibraryEntry("Playlists", HarmoniaIcons.Playlists, playlistsState.playlists.size, LibraryDestination.Playlists),
        LibraryEntry("Artists", HarmoniaIcons.Person, uiState.artists.size, LibraryDestination.Category(LibraryTab.ARTISTS)),
        LibraryEntry("Albums", HarmoniaIcons.Album, uiState.albums.size, LibraryDestination.Category(LibraryTab.ALBUMS)),
        LibraryEntry("Songs", HarmoniaIcons.MusicNote, uiState.tracks.size, LibraryDestination.Category(LibraryTab.TRACKS)),
        LibraryEntry("Folders", HarmoniaIcons.Folder, null, LibraryDestination.Category(LibraryTab.FOLDERS)),
        LibraryEntry("Favorites", HarmoniaIcons.Favorite, favoriteTracks.size, LibraryDestination.Category(LibraryTab.FAVORITES))
    )
    val recentAlbums = remember(uiState.albums) {
        uiState.albums.sortedByDescending { it.createdAt }.take(RECENTLY_ADDED_ALBUMS)
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = AlbumGridMinCellWidth),
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot),
        contentPadding = PaddingValues(
            start = spacing.standard,
            end = spacing.standard,
            top = spacing.standard,
            bottom = spacing.bottomScaffold
        ),
        horizontalArrangement = Arrangement.spacedBy(spacing.standard),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = spacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.displayMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (onProfileClick != null) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceContainer)
                            .clickable(onClick = onProfileClick)
                            .semantics { contentDescription = "Open Profile and Settings" },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Person,
                            contentDescription = null,
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        items(entries.size, span = { GridItemSpan(maxLineSpan) }) { index ->
            LibraryEntryRow(
                entry = entries[index],
                showDivider = index < entries.lastIndex,
                onClick = { onOpen(entries[index].destination) }
            )
        }

        if (recentAlbums.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Recently added",
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = spacing.large, bottom = spacing.medium)
                )
            }
            items(recentAlbums, key = { it.id }) { album ->
                RecentAlbumCard(album = album, onClick = { onAlbumClick(album) })
            }
        }
    }
}

@Composable
private fun LibraryEntryRow(
    entry: LibraryEntry,
    showDivider: Boolean,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = entry.icon,
                contentDescription = null,
                tint = colors.primarySignal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(spacing.standard))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (entry.count != null) {
                Text(
                    text = entry.count.toString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.width(spacing.small))
            }
            Icon(
                imageVector = HarmoniaIcons.ChevronRight,
                contentDescription = null,
                tint = colors.textDisabled,
                modifier = Modifier.size(20.dp)
            )
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .padding(start = 40.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderStructural)
            )
        }
    }
}

@Composable
private fun RecentAlbumCard(album: Album, onClick: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 20.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
    ) {
        HarmoniaArtwork(
            artworkHash = album.artworkHash,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            size = 180.dp,
            cornerRadius = 10.dp,
            contentDescription = "${album.title} artwork"
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = album.artistName ?: "Unknown artist",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
