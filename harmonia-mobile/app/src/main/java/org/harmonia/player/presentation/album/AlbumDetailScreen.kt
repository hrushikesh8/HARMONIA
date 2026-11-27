package org.harmonia.player.presentation.album

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.components.TrackMenuButton
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.util.TrackDisplayUtils
import java.util.Locale

/**
 * Album page (Apple Music layout): centred artwork, title and artist, Play / Shuffle,
 * then the songs grouped by disc, with a summary line at the end.
 */
@Composable
fun AlbumDetailScreen(
    albumId: String,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = viewModel(),
    personalizationViewModel: PersonalizationViewModel = viewModel(),
    currentPlayingTrackId: String? = null,
    isPlaying: Boolean = false,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    onPlayTrack: (List<Track>, Int) -> Unit = { _, _ -> },
    onShuffleAll: (List<Track>) -> Unit = {},
    onTrackMenu: ((Track) -> Unit)? = null
) {
    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val favoriteTracks by personalizationViewModel.favoriteTracks.collectAsState()
    val favoriteTrackIds = remember(favoriteTracks) { favoriteTracks.map { it.id }.toSet() }

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
            AlbumMessage(
                title = "Couldn't load this album",
                body = "Something went wrong reading your library. Try again in a moment.",
                actionLabel = "Try again",
                onAction = viewModel::retry,
                onBack = onBack,
                modifier = modifier
            )
            return
        }
        uiState.album == null -> {
            AlbumMessage(
                title = "Album not found",
                body = "It may have been moved or removed from this phone.",
                actionLabel = "Go back",
                onAction = onBack,
                onBack = onBack,
                modifier = modifier
            )
            return
        }
    }
    val album: Album = uiState.album ?: return

    val tracks = uiState.tracks
    val discs = uiState.discs

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
    ) {
        item {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(start = spacing.xSmall, top = spacing.xSmall)
                    .touchTarget48()
                    .semantics { contentDescription = "Navigate back" }
            ) {
                Icon(imageVector = HarmoniaIcons.ArrowBack, contentDescription = null, tint = colors.textPrimary)
            }
        }

        item { AlbumHeader(album = album, onArtistClick = onArtistClick) }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.standard, vertical = spacing.large),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                AlbumActionButton(
                    label = "Play",
                    icon = HarmoniaIcons.Play,
                    filled = true,
                    enabled = tracks.isNotEmpty(),
                    contentDescription = "Play album",
                    onClick = { onPlayTrack(tracks, 0) },
                    modifier = Modifier.weight(1f)
                )
                AlbumActionButton(
                    label = "Shuffle",
                    icon = HarmoniaIcons.Shuffle,
                    filled = false,
                    enabled = tracks.isNotEmpty(),
                    contentDescription = "Shuffle album tracks",
                    onClick = { onShuffleAll(tracks) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (tracks.isEmpty()) {
            item {
                Text(
                    text = "No songs from this album are on this phone right now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(spacing.xLarge)
                )
            }
        }

        discs.forEach { disc ->
            if (disc.discNumber != null) {
                item(key = "disc_${disc.discNumber}") {
                    Text(
                        text = "Disc ${disc.discNumber}",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textSecondary,
                        modifier = Modifier
                            .padding(horizontal = spacing.standard)
                            .padding(top = spacing.medium, bottom = spacing.xSmall)
                            .semantics { heading() }
                    )
                }
            }
            items(disc.tracks, key = { it.id }) { track ->
                val index = tracks.indexOf(track)
                AlbumTrackRow(
                    track = track,
                    albumArtist = album.artistName,
                    isActive = track.id == currentPlayingTrackId,
                    isPlaying = isPlaying,
                    isFavorite = track.id in favoriteTrackIds,
                    onClick = { onPlayTrack(tracks, index) },
                    onToggleFavorite = { personalizationViewModel.toggleFavorite(track.id) },
                    onMenu = onTrackMenu?.let { menu -> { menu(track) } }
                )
            }
        }

        if (tracks.isNotEmpty()) {
            item {
                AlbumFooter(album = album, tracks = tracks, totalDurationSec = uiState.totalDurationSec)
            }
        }
    }
}

@Composable
private fun AlbumHeader(album: Album, onArtistClick: (String) -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.standard),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth * 0.68f, 300.dp)
            Box(
                modifier = Modifier
                    .size(side)
                    .shadow(20.dp, RoundedCornerShape(12.dp), clip = false)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceElevated)
            ) {
                HarmoniaArtwork(
                    artworkHash = album.artworkHash,
                    modifier = Modifier.fillMaxSize(),
                    size = side,
                    cornerRadius = 12.dp,
                    contentDescription = "${album.title} artwork"
                )
            }
        }
        Spacer(modifier = Modifier.height(spacing.large))
        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(modifier = Modifier.height(4.dp))
        val artistName = album.artistName ?: "Unknown artist"
        Text(
            text = artistName,
            style = MaterialTheme.typography.titleMedium,
            color = if (album.artistId != null) colors.primarySignal else colors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = album.artistId != null) { album.artistId?.let(onArtistClick) }
                .padding(horizontal = 6.dp, vertical = 2.dp)
                .semantics { contentDescription = "Artist: $artistName" }
        )
        album.year?.takeIf { it > 0 }?.let { year ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun AlbumActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val onFilled = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .height(48.dp)
            .semantics { this.contentDescription = contentDescription },
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) colors.primarySignal else colors.surfaceContainer,
            contentColor = if (filled) onFilled else colors.primarySignal
        )
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun AlbumTrackRow(
    track: Track,
    albumArtist: String?,
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
    // Only show the artist under the title when it differs from the album artist (features, compilations).
    val trackArtist = track.artistName?.takeIf { it.isNotBlank() && !it.equals(albumArtist, ignoreCase = true) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isActive) colors.primarySignal.copy(alpha = 0.08f) else colors.backgroundRoot)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(start = spacing.standard, end = spacing.xSmall)
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.width(32.dp), contentAlignment = Alignment.CenterStart) {
            if (isActive && isPlaying) {
                Icon(
                    imageVector = HarmoniaIcons.Play,
                    contentDescription = "Playing",
                    modifier = Modifier.size(16.dp),
                    tint = colors.primarySignal
                )
            } else {
                Text(
                    text = track.trackNumber?.toString() ?: "–",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isActive) colors.primarySignal else colors.textSecondary
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isActive) colors.primarySignal else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (trackArtist != null) {
                Text(
                    text = trackArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = formatTrackDuration(track.durationSec),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary
        )
        FavoriteButton(isFavorite = isFavorite, onToggleFavorite = onToggleFavorite)
        if (onMenu != null) {
            TrackMenuButton(trackTitle = title, onClick = onMenu)
        }
    }
}

@Composable
private fun AlbumFooter(album: Album, tracks: List<Track>, totalDurationSec: Double) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val songs = "${tracks.size} ${if (tracks.size == 1) "song" else "songs"}"
    val formats = tracks.mapNotNull { it.localFile?.format?.uppercase(Locale.ROOT)?.takeIf(String::isNotBlank) }.distinct()
    Column(modifier = Modifier.padding(horizontal = spacing.standard, vertical = spacing.large)) {
        Text(
            text = "$songs, ${formatAlbumDuration(totalDurationSec)}",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary
        )
        if (formats.isNotEmpty()) {
            Text(
                text = formats.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        album.year?.takeIf { it > 0 }?.let { year ->
            Text(
                text = "Released $year",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun AlbumMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
    ) {
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
                imageVector = HarmoniaIcons.Album,
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

private fun formatTrackDuration(durationSec: Double): String {
    val total = durationSec.toInt()
    return String.format(Locale.US, "%d:%02d", total / 60, total % 60)
}
