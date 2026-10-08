package org.harmonia.player.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import java.util.Locale

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Title Header
        Text(
            text = "SEARCH",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontSize = 26.sp
            ),
            color = PureSignalWhite
        )
        Text(
            text = "LOCAL CATALOG INDEX",
            style = MaterialTheme.typography.labelSmall,
            color = AcousticTeal
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search Input Field with Clear Button
        OutlinedTextField(
            value = query,
            onValueChange = { viewModel.onQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Search input field" },
            placeholder = { Text("Search tracks, albums, artists, playlists...", color = MutedSlate) },
            leadingIcon = {
                Icon(
                    imageVector = HarmoniaIcons.Search,
                    contentDescription = null,
                    tint = AcousticTeal
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
                            tint = MutedSlate
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AcousticTeal,
                unfocusedBorderColor = BorderSlate,
                focusedContainerColor = AcousticCharcoal,
                unfocusedContainerColor = AcousticCharcoal,
                cursorColor = AcousticTeal,
                focusedTextColor = PureSignalWhite,
                unfocusedTextColor = PureSignalWhite
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Body Content State Handling
        if (query.isBlank()) {
            EmptyPromptState()
        } else if (uiState.isEmpty) {
            NoResultsState(query = query)
        } else {
            SearchResultsList(
                uiState = uiState,
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
                tint = MutedSlate,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Type to search local catalog",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PureSignalWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Search tracks, albums, artists, and playlists across your local library",
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun NoResultsState(query: String) {
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
                tint = MutedSlate,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No matches found for \"$query\"",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PureSignalWhite,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Check for typos or try searching by title, artist, or album name",
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun SearchResultsList(
    uiState: SearchUiState,
    onTrackClick: (Track) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onPlaylistClick: (Playlist) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Tracks Category
        if (uiState.matchingTracks.isNotEmpty()) {
            item(key = "header_tracks") {
                CategoryHeader(title = "TRACKS", count = uiState.matchingTracks.size)
            }
            items(uiState.matchingTracks, key = { "trk_${it.id}" }) { track ->
                TrackResultRow(track = track, onClick = { onTrackClick(track) })
            }
        }

        // Albums Category
        if (uiState.matchingAlbums.isNotEmpty()) {
            item(key = "header_albums") {
                CategoryHeader(title = "ALBUMS", count = uiState.matchingAlbums.size)
            }
            items(uiState.matchingAlbums, key = { "alb_${it.id}" }) { album ->
                AlbumResultRow(album = album, onClick = { onAlbumClick(album) })
            }
        }

        // Artists Category
        if (uiState.matchingArtists.isNotEmpty()) {
            item(key = "header_artists") {
                CategoryHeader(title = "ARTISTS", count = uiState.matchingArtists.size)
            }
            items(uiState.matchingArtists, key = { "art_${it.id}" }) { artist ->
                ArtistResultRow(artist = artist, onClick = { onArtistClick(artist) })
            }
        }

        // Playlists Category
        if (uiState.matchingPlaylists.isNotEmpty()) {
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
    Text(
        text = "$title ($count)",
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = AcousticTeal,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun TrackResultRow(
    track: Track,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play ${track.title} by ${track.artistName ?: "Unknown"}" },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = track.localFile?.fileHash,
                size = 44.dp,
                cornerRadius = 6.dp,
                contentDescription = "${track.title} artwork"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = PureSignalWhite,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artistName ?: "Unknown Artist"} • ${track.albumTitle ?: "Unknown Album"}",
                    color = MutedSlate,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = formatDuration(track.durationSec),
                color = MutedSlate,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Album ${album.title} by ${album.artistName ?: "Unknown"}" },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = album.artworkHash,
                size = 44.dp,
                cornerRadius = 6.dp,
                placeholderIcon = HarmoniaIcons.Album,
                contentDescription = "${album.title} artwork"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = album.title,
                    color = PureSignalWhite,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = listOfNotNull(album.artistName, album.year?.toString()).joinToString(" • ")
                Text(
                    text = subtitle.ifEmpty { "Album" },
                    color = MutedSlate,
                    style = MaterialTheme.typography.bodySmall,
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Artist ${artist.name}" },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = artist.artworkHash,
                size = 44.dp,
                cornerRadius = 22.dp, // circular avatar
                placeholderIcon = HarmoniaIcons.Person,
                contentDescription = "${artist.name} avatar"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    color = PureSignalWhite,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Artist",
                    color = MutedSlate,
                    style = MaterialTheme.typography.bodySmall
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Playlist ${playlist.name}" },
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = playlist.artworkHash,
                size = 44.dp,
                cornerRadius = 6.dp,
                placeholderIcon = HarmoniaIcons.Playlists,
                contentDescription = "${playlist.name} artwork"
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = PureSignalWhite,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = playlist.description ?: "Playlist",
                    color = MutedSlate,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatDuration(durationSec: Double): String {
    val totalSeconds = durationSec.toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
