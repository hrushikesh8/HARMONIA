package org.harmonia.player.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.util.TrackDisplayUtils
import java.util.Locale

enum class SearchFilter(val label: String) {
    ALL("All"),
    TRACKS("Tracks"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    PLAYLISTS("Playlists")
}

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(),
    onTrackClick: (Track) -> Unit = {},
    onAlbumClick: (Album) -> Unit = {},
    onArtistClick: (Artist) -> Unit = {},
    onPlaylistClick: (Playlist) -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    var selectedFilter by remember { mutableStateOf(SearchFilter.ALL) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard)
    ) {
        Spacer(modifier = Modifier.height(spacing.standard))

        // Title Header
        Text(
            text = "SEARCH",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontSize = 26.sp
            ),
            color = colors.textPrimary
        )
        Text(
            text = "LOCAL CATALOG INDEX",
            style = MaterialTheme.typography.labelSmall,
            color = colors.primarySignal
        )

        Spacer(modifier = Modifier.height(spacing.standard))

        // Search Input Field with Clear Button
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search input field" },
            placeholder = { Text("Search songs, artists, albums, files...", color = colors.textSecondary) },
            leadingIcon = {
                Icon(
                    imageVector = HarmoniaIcons.Search,
                    contentDescription = null,
                    tint = if (query.isNotEmpty()) colors.primarySignal else colors.textSecondary
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { viewModel.clearQuery() },
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "Clear search query" }
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Close,
                            contentDescription = "Clear search query",
                            tint = colors.textSecondary
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primarySignal,
                unfocusedBorderColor = colors.borderStructural,
                focusedContainerColor = colors.surfaceElevated,
                unfocusedContainerColor = colors.surfaceElevated,
                cursorColor = colors.primarySignal,
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary
            )
        )

        Spacer(modifier = Modifier.height(spacing.small))

        // Search Category Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(spacing.xSmall)
        ) {
            SearchFilter.entries.forEach { filter ->
                val isSelected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) colors.primarySignal else colors.surfaceElevated)
                        .hairlineBorder(
                            color = if (isSelected) colors.primarySignal else colors.borderStructural,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedFilter = filter }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) colors.backgroundRoot else colors.textSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.standard))

        // Body Content State Handling
        if (query.isBlank()) {
            EmptyPromptState()
        } else if (uiState.isEmpty) {
            NoResultsState(query = query)
        } else {
            SearchResultsList(
                uiState = uiState,
                filter = selectedFilter,
                onTrackClick = onTrackClick,
                onAlbumClick = onAlbumClick,
                onArtistClick = onArtistClick,
                onPlaylistClick = onPlaylistClick
            )
        }
    }
}

@Composable
private fun EmptyPromptState() {
    val colors = HarmoniaThemeTokens.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = HarmoniaIcons.Search,
                contentDescription = null,
                tint = colors.primarySignal.copy(alpha = 0.6f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Search Local Music Catalog",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Instant search across offline tracks, albums, artists, playlists, and file names",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
    val colors = HarmoniaThemeTokens.colors
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = HarmoniaIcons.MusicNote,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No matches for \"$query\"",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Check spelling or search by title, artist, album, or file extension",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    uiState: SearchUiState,
    filter: SearchFilter,
    onTrackClick: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit
) {
    val spacing = HarmoniaThemeTokens.spacing

    val showTracks = filter == SearchFilter.ALL || filter == SearchFilter.TRACKS
    val showAlbums = filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS
    val showArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS
    val showPlaylists = filter == SearchFilter.ALL || filter == SearchFilter.PLAYLISTS

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold),
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        // Tracks Category
        if (showTracks && uiState.matchingTracks.isNotEmpty()) {
            item(key = "header_tracks") {
                CategoryHeader(title = "TRACKS", count = uiState.matchingTracks.size)
            }
            items(uiState.matchingTracks, key = { "trk_${it.id}" }) { track ->
                TrackResultRow(track = track, onClick = { onTrackClick(track) })
            }
        }

        // Albums Category
        if (showAlbums && uiState.matchingAlbums.isNotEmpty()) {
            item(key = "header_albums") {
                CategoryHeader(title = "ALBUMS", count = uiState.matchingAlbums.size)
            }
            items(uiState.matchingAlbums, key = { "alb_${it.id}" }) { album ->
                AlbumResultRow(album = album, onClick = { onAlbumClick(album) })
            }
        }

        // Artists Category
        if (showArtists && uiState.matchingArtists.isNotEmpty()) {
            item(key = "header_artists") {
                CategoryHeader(title = "ARTISTS", count = uiState.matchingArtists.size)
            }
            items(uiState.matchingArtists, key = { "art_${it.id}" }) { artist ->
                ArtistResultRow(artist = artist, onClick = { onArtistClick(artist) })
            }
        }

        // Playlists Category
        if (showPlaylists && uiState.matchingPlaylists.isNotEmpty()) {
            item(key = "header_playlists") {
                CategoryHeader(title = "PLAYLISTS", count = uiState.matchingPlaylists.size)
            }
            items(uiState.matchingPlaylists, key = { "pl_${it.id}" }) { playlist ->
                PlaylistResultRow(playlist = playlist, onClick = { onPlaylistClick(playlist) })
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String, count: Int) {
    val colors = HarmoniaThemeTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            ),
            color = colors.primarySignal
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun TrackResultRow(
    track: Track,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(10.dp)
    val displayTitle = TrackDisplayUtils.formatDisplayTitle(track)
    val subtitle = listOfNotNull(track.artistName, track.albumTitle).joinToString(" • ")
        .ifEmpty { "Local Audio" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(colors.borderStructural, cardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play $displayTitle by ${track.artistName ?: "Unknown"}" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = track.localFile?.fileHash,
                size = 42.dp,
                cornerRadius = 8.dp,
                contentDescription = "$displayTitle artwork"
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    track.localFile?.format?.let { fmt ->
                        Text(
                            text = fmt.uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = colors.secondarySignal,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = subtitle,
                        color = colors.textSecondary,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(spacing.small))
            Text(
                text = formatDuration(track.durationSec),
                color = colors.textSecondary,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun AlbumResultRow(
    album: Album,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(10.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(colors.borderStructural, cardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Album ${album.title} by ${album.artistName ?: "Unknown"}" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = album.artworkHash,
                size = 42.dp,
                cornerRadius = 8.dp,
                placeholderIcon = HarmoniaIcons.Album,
                contentDescription = "${album.title} artwork"
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOfNotNull(album.artistName, album.year?.toString()).joinToString(" • ")
                Text(
                    text = subtitle.ifEmpty { "Album" },
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ArtistResultRow(
    artist: Artist,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(10.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(colors.borderStructural, cardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Artist ${artist.name}" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Person,
                    contentDescription = null,
                    tint = colors.primarySignal,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Artist",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
                )
            }
        }
    }
}

@Composable
private fun PlaylistResultRow(
    playlist: Playlist,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(10.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(colors.borderStructural, cardShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Playlist ${playlist.name}" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = playlist.artworkHash,
                size = 42.dp,
                cornerRadius = 8.dp,
                placeholderIcon = HarmoniaIcons.Playlists,
                contentDescription = "${playlist.name} artwork"
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackCount} tracks",
                    color = colors.secondarySignal,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp)
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
