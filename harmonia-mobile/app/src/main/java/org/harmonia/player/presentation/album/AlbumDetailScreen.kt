package org.harmonia.player.presentation.album

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.util.TrackDisplayUtils
import java.util.Locale

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
    onShuffleAll: (List<Track>) -> Unit = {}
) {
    LaunchedEffect(albumId) {
        viewModel.loadAlbum(albumId)
    }

    val uiState by viewModel.uiState.collectAsState()
    val favoriteTracks by personalizationViewModel.favoriteTracks.collectAsState()
    val favoriteTrackIds = remember(favoriteTracks) { favoriteTracks.map { it.id }.toSet() }

    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.backgroundRoot),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = colors.primarySignal)
        }
        return
    }

    val album = uiState.album
    if (album == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.backgroundRoot)
                .padding(spacing.standard),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = HarmoniaIcons.Album,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colors.textSecondary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    text = "Album not found",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = "This album may have been moved or removed from storage.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Spacer(modifier = Modifier.height(spacing.large))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(containerColor = colors.primarySignal)
                ) {
                    Text("Go Back", color = colors.backgroundRoot, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val tracks = uiState.tracks
    val totalMinutes = (uiState.totalDurationSec / 60).toInt()
    val totalSeconds = (uiState.totalDurationSec % 60).toInt()
    val durationText = when {
        totalMinutes >= 60 -> {
            val hours = totalMinutes / 60
            val remainingMins = totalMinutes % 60
            "${hours} hr ${remainingMins} min"
        }
        totalMinutes > 0 -> "${totalMinutes} min ${totalSeconds} sec"
        else -> "${totalSeconds} sec"
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
    ) {
        // Navigation Back Bar
        item {
            Spacer(modifier = Modifier.height(spacing.small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Navigate back" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.ArrowBack,
                        contentDescription = "Back",
                        tint = colors.textPrimary
                    )
                }
                Spacer(modifier = Modifier.width(spacing.small))
                Column {
                    Text(
                        text = "ALBUM",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = colors.primarySignal
                    )
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Hero Artwork & Metadata Header
        item {
            Spacer(modifier = Modifier.height(spacing.medium))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(
                    modifier = Modifier
                        .size(136.dp)
                        .hairlineBorder(colors.borderStructural, RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
                ) {
                    HarmoniaArtwork(
                        artworkHash = album.artworkHash,
                        size = 136.dp,
                        cornerRadius = 12.dp,
                        contentDescription = album.title
                    )
                }

                Spacer(modifier = Modifier.width(spacing.medium))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val artistName = album.artistName ?: "Unknown Artist"
                    Spacer(modifier = Modifier.height(spacing.xSmall))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = album.artistId != null) {
                                album.artistId?.let { onArtistClick(it) }
                            }
                            .semantics { contentDescription = "Artist: $artistName" }
                    ) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = if (album.artistId != null) colors.secondarySignal else colors.textSecondary
                        )
                        if (album.artistId != null) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = HarmoniaIcons.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colors.secondarySignal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.xSmall))
                    val metaParts = mutableListOf<String>()
                    album.year?.let { if (it > 0) metaParts.add(it.toString()) }
                    metaParts.add("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}")
                    if (uiState.totalDurationSec > 0) {
                        metaParts.add(durationText)
                    }

                    Text(
                        text = metaParts.joinToString(" • "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))

            // Action Buttons: Play and Shuffle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(spacing.medium)
            ) {
                Button(
                    onClick = {
                        if (tracks.isNotEmpty()) {
                            onPlayTrack(tracks, 0)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .semantics { contentDescription = "Play album" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primarySignal,
                        contentColor = colors.backgroundRoot
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Play,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.backgroundRoot
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(
                        text = "Play",
                        fontWeight = FontWeight.Bold,
                        color = colors.backgroundRoot
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (tracks.isNotEmpty()) {
                            onShuffleAll(tracks.shuffled())
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .hairlineBorder(colors.borderStructural, RoundedCornerShape(24.dp))
                        .semantics { contentDescription = "Shuffle album tracks" },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = colors.surfaceElevated,
                        contentColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Shuffle,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = colors.primarySignal
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(
                        text = "Shuffle",
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.large))
        }

        // Empty Tracks State
        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks found in this album",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        // Ordered Track List
        itemsIndexed(tracks) { index, track ->
            val isFavorite = favoriteTrackIds.contains(track.id)
            val isThisTrackActive = currentPlayingTrackId != null && currentPlayingTrackId == track.id
            val trackNumberDisplay = track.trackNumber?.toString() ?: "${index + 1}"
            val cleanTitle = TrackDisplayUtils.formatDisplayTitle(track)
            val formatStr = track.localFile?.format?.uppercase(Locale.ROOT)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isThisTrackActive) colors.primarySignal.copy(alpha = 0.08f) else colors.backgroundRoot)
                    .clickable { onPlayTrack(tracks, index) }
                    .padding(vertical = spacing.small, horizontal = spacing.xSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Track Number or Active Playing Indicator
                Box(
                    modifier = Modifier.width(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isThisTrackActive && isPlaying) {
                        Icon(
                            imageVector = HarmoniaIcons.Play,
                            contentDescription = "Playing",
                            modifier = Modifier.size(16.dp),
                            tint = colors.primarySignal
                        )
                    } else {
                        Text(
                            text = trackNumberDisplay,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = if (isThisTrackActive) colors.primarySignal else colors.textSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.width(spacing.small))

                // Title + Subtitle Metadata
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = cleanTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isThisTrackActive) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (isThisTrackActive) colors.primarySignal else colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        track.artistName?.let { artist ->
                            Text(
                                text = artist,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (formatStr != null) {
                            if (track.artistName != null) {
                                Text(
                                    text = " • ",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textSecondary
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(colors.surfaceElevated)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = formatStr,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold
                                    ),
                                    color = colors.primarySignal
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(spacing.small))

                // Duration Text
                val trackMin = (track.durationSec / 60).toInt()
                val trackSec = (track.durationSec % 60).toInt()
                Text(
                    text = String.format(Locale.US, "%d:%02d", trackMin, trackSec),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.width(spacing.small))

                // Favorite Toggle (48dp touch target)
                FavoriteButton(
                    isFavorite = isFavorite,
                    onToggleFavorite = { personalizationViewModel.toggleFavorite(track.id) }
                )
            }
        }
    }
}
