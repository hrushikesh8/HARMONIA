package org.harmonia.player.presentation.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.components.TrackMenuButton
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.util.TrackDisplayUtils
import java.util.Locale

/**
 * Artist page (Apple Music layout): artwork header that fades into the page with the name and a
 * play button, then Top songs, Albums and Appears on.
 */
@Composable
fun ArtistDetailScreen(
    artistId: String,
    modifier: Modifier = Modifier,
    viewModel: ArtistDetailViewModel = viewModel(),
    personalizationViewModel: PersonalizationViewModel = viewModel(),
    currentPlayingTrackId: String? = null,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onAlbumClick: (String) -> Unit = {},
    onPlayTrack: (List<Track>, Int) -> Unit = { _, _ -> },
    onShuffleAll: (List<Track>) -> Unit = {},
    onTrackMenu: ((Track) -> Unit)? = null
) {
    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val favoriteTracks by personalizationViewModel.favoriteTracks.collectAsState()
    val favoriteTrackIds = remember(favoriteTracks) { favoriteTracks.map { it.id }.toSet() }
    var showAllSongs by rememberSaveable(artistId) { mutableStateOf(false) }

    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    when {
        uiState.isLoading -> {
            Box(
                modifier = modifier.fillMaxSize().background(colors.backgroundRoot),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = colors.primarySignal)
            }
            return
        }
        uiState.errorMessage != null -> {
            ArtistMessage(
                title = "Couldn't load this artist",
                body = "Something went wrong reading your library. Try again in a moment.",
                actionLabel = "Try again",
                onAction = viewModel::retry,
                onBack = onBack,
                modifier = modifier
            )
            return
        }
        uiState.artist == null -> {
            ArtistMessage(
                title = "Artist not found",
                body = "Their songs may have been moved or deleted from this phone.",
                actionLabel = "Go back",
                onAction = onBack,
                onBack = onBack,
                modifier = modifier
            )
            return
        }
    }
    val artist: Artist = uiState.artist ?: return
    val tracks = uiState.tracks
    val songList = if (showAllSongs) tracks else uiState.topSongs
    val heroArtwork = artist.artworkHash ?: uiState.albums.firstOrNull { it.artworkHash != null }?.artworkHash

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
    ) {
        item {
            ArtistHeader(
                artist = artist,
                artworkHash = heroArtwork,
                canPlay = tracks.isNotEmpty(),
                onBack = onBack,
                onPlay = { onPlayTrack(tracks, 0) }
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.standard),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artistSummary(uiState.albums.size, tracks.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (tracks.isNotEmpty()) {
                    Button(
                        onClick = { onShuffleAll(tracks) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceContainer,
                            contentColor = colors.primarySignal
                        ),
                        modifier = Modifier.semantics { contentDescription = "Shuffle artist songs" }
                    ) {
                        Icon(imageVector = HarmoniaIcons.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Shuffle", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Text(
                    text = "No songs by this artist are on this phone right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(spacing.xLarge)
                )
            }
        } else {
            item {
                SectionTitle(if (showAllSongs) "All songs" else "Top songs")
            }
            itemsIndexed(songList, key = { _, track -> "song_${track.id}" }) { index, track ->
                ArtistSongRow(
                    rank = index + 1,
                    track = track,
                    isActive = track.id == currentPlayingTrackId,
                    isPlaying = isPlaying,
                    isFavorite = track.id in favoriteTrackIds,
                    onClick = { onPlayTrack(songList, index) },
                    onToggleFavorite = { personalizationViewModel.toggleFavorite(track.id) },
                    onMenu = onTrackMenu?.let { menu -> { menu(track) } }
                )
            }
            if (tracks.size > uiState.topSongs.size) {
                item {
                    Text(
                        text = if (showAllSongs) "Show top songs" else "Show all ${tracks.size} songs",
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primarySignal,
                        modifier = Modifier
                            .padding(horizontal = spacing.small, vertical = spacing.xSmall)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showAllSongs = !showAllSongs }
                            .padding(horizontal = spacing.small, vertical = spacing.small)
                    )
                }
            }
        }

        if (uiState.albums.isNotEmpty()) {
            item { SectionTitle("Albums") }
            item { AlbumRow(albums = uiState.albums, onAlbumClick = onAlbumClick) }
        }

        if (uiState.appearsOn.isNotEmpty()) {
            item { SectionTitle("Appears on") }
            item { AlbumRow(albums = uiState.appearsOn, onAlbumClick = onAlbumClick, showArtist = true) }
        }
    }
}

internal fun artistSummary(albumCount: Int, songCount: Int): String {
    val albums = "$albumCount ${if (albumCount == 1) "album" else "albums"}"
    val songs = "$songCount ${if (songCount == 1) "song" else "songs"}"
    return if (albumCount > 0) "$albums · $songs" else songs
}

@Composable
private fun ArtistHeader(
    artist: Artist,
    artworkHash: String?,
    canPlay: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
    ) {
        HarmoniaArtwork(
            artworkHash = artworkHash,
            modifier = Modifier.fillMaxSize(),
            size = 400.dp,
            cornerRadius = 0.dp,
            contentDescription = null
        )
        // Fade the artwork into the page so the name stays readable on any cover.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Transparent,
                        1f to colors.backgroundRoot
                    )
                )
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(spacing.small)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .semantics { contentDescription = "Navigate back" }
        ) {
            Icon(imageVector = HarmoniaIcons.ArrowBack, contentDescription = null, tint = Color.White)
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = spacing.standard, vertical = spacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.displayMedium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            if (canPlay) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.primarySignal)
                        .clickable(onClick = onPlay)
                        .semantics {
                            role = Role.Button
                            contentDescription = "Play ${artist.name}"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Play,
                        contentDescription = null,
                        tint = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        color = colors.textPrimary,
        modifier = Modifier
            .padding(horizontal = spacing.standard)
            .padding(top = spacing.large, bottom = spacing.small)
            .semantics { heading() }
    )
}

@Composable
private fun ArtistSongRow(
    rank: Int,
    track: Track,
    isActive: Boolean,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMenu: (() -> Unit)?
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val title = TrackDisplayUtils.formatDisplayTitle(track)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) colors.primarySignal.copy(alpha = 0.08f) else colors.backgroundRoot)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(start = spacing.standard, end = spacing.xSmall, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (isActive) colors.primarySignal else colors.textSecondary,
            modifier = Modifier.width(28.dp)
        )
        HarmoniaArtwork(
            artworkHash = track.artworkHash,
            size = 48.dp,
            cornerRadius = 6.dp,
            contentDescription = null
        )
        Spacer(modifier = Modifier.width(spacing.medium))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) colors.primarySignal else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(track.albumTitle, formatSongDuration(track.durationSec)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (isActive && isPlaying) {
            Icon(
                imageVector = HarmoniaIcons.Play,
                contentDescription = "Playing",
                tint = colors.primarySignal,
                modifier = Modifier.size(16.dp)
            )
        }
        FavoriteButton(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
        if (onMenu != null) {
            TrackMenuButton(trackTitle = title, onClick = onMenu)
        }
    }
}

@Composable
private fun AlbumRow(albums: List<Album>, onAlbumClick: (String) -> Unit, showArtist: Boolean = false) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    LazyRow(
        contentPadding = PaddingValues(horizontal = spacing.standard),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .width(150.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAlbumClick(album.id) }
                    .semantics(mergeDescendants = true) { role = Role.Button }
            ) {
                HarmoniaArtwork(
                    artworkHash = album.artworkHash,
                    size = 150.dp,
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
                val subtitle = if (showArtist) album.artistName else album.year?.takeIf { it > 0 }?.toString()
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Box(modifier = modifier.fillMaxSize().background(colors.backgroundRoot)) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .padding(spacing.xSmall)
                .semantics { contentDescription = "Navigate back" }
        ) {
            Icon(imageVector = HarmoniaIcons.ArrowBack, contentDescription = null, tint = colors.textPrimary)
        }
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(spacing.xLarge),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = HarmoniaIcons.Person,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = colors.textDisabled
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Spacer(modifier = Modifier.height(spacing.small))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.large))
            Button(
                onClick = onAction,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primarySignal,
                    contentColor = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated
                )
            ) {
                Text(text = actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun formatSongDuration(durationSec: Double): String {
    val total = durationSec.toInt()
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
