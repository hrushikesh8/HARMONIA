package org.harmonia.player.presentation.artist

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.style.TextAlign
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
    onShuffleAll: (List<Track>) -> Unit = {}
) {
    LaunchedEffect(artistId) {
        viewModel.loadArtist(artistId)
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

    val artist = uiState.artist
    if (artist == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.backgroundRoot)
                .padding(spacing.standard),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = HarmoniaIcons.Person,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = colors.textSecondary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(spacing.medium))
                Text(
                    text = "Artist not found",
                    style = MaterialTheme.typography.titleLarge,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = "This artist may have no remaining tracks in your local library.",
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

    val albums = uiState.albums
    val tracks = uiState.tracks
    val totalMins = (uiState.totalDurationSec / 60).toInt()
    val statsList = mutableListOf<String>()
    statsList.add("${albums.size} ${if (albums.size == 1) "album" else "albums"}")
    statsList.add("${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}")
    if (totalMins > 0) {
        statsList.add("${totalMins} min")
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
                        text = "ARTIST",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = colors.primarySignal
                    )
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Hero Profile Header
        item {
            Spacer(modifier = Modifier.height(spacing.medium))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .clip(CircleShape)
                        .hairlineBorder(colors.primarySignal.copy(alpha = 0.6f), CircleShape, 2.dp)
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    HarmoniaArtwork(
                        artworkHash = artist.artworkHash,
                        size = 104.dp,
                        cornerRadius = 52.dp,
                        placeholderIcon = HarmoniaIcons.Person,
                        contentDescription = artist.name
                    )
                }

                Spacer(modifier = Modifier.height(spacing.medium))

                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    ),
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(spacing.xSmall))

                Text(
                    text = statsList.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                Spacer(modifier = Modifier.height(spacing.large))

                // Play All & Shuffle Buttons
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
                            .semantics { contentDescription = "Play all artist tracks" },
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
                            text = "Play All",
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
                            .semantics { contentDescription = "Shuffle all artist tracks" },
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
        }

        // Albums Section (Discography)
        if (albums.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ALBUMS (${albums.size})",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                        color = colors.primarySignal
                    )
                }

                Spacer(modifier = Modifier.height(spacing.small))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium),
                    contentPadding = PaddingValues(vertical = spacing.xSmall)
                ) {
                    items(albums, key = { it.id }) { album ->
                        Column(
                            modifier = Modifier
                                .width(128.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onAlbumClick(album.id) }
                                .semantics { contentDescription = "Album: ${album.title}" }
                        ) {
                            Card(
                                modifier = Modifier
                                    .size(128.dp)
                                    .hairlineBorder(colors.borderStructural, RoundedCornerShape(10.dp)),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
                            ) {
                                HarmoniaArtwork(
                                    artworkHash = album.artworkHash,
                                    size = 128.dp,
                                    cornerRadius = 10.dp,
                                    contentDescription = album.title
                                )
                            }
                            Spacer(modifier = Modifier.height(spacing.xSmall))
                            Text(
                                text = album.title,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            album.year?.let {
                                if (it > 0) {
                                    Text(
                                        text = it.toString(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                        color = colors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))
            }
        }

        // Tracks by This Artist Section
        item {
            Text(
                text = "TRACKS BY THIS ARTIST (${tracks.size})",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = colors.primarySignal,
                modifier = Modifier.padding(bottom = spacing.small)
            )
        }

        if (tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = spacing.xLarge),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks found for this artist",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary
                    )
                }
            }
        }

        itemsIndexed(tracks) { index, track ->
            val isFavorite = favoriteTrackIds.contains(track.id)
            val isThisTrackActive = currentPlayingTrackId != null && currentPlayingTrackId == track.id
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
                // Active speaker or artwork thumbnail
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    HarmoniaArtwork(
                        artworkHash = track.artworkHash,
                        size = 44.dp,
                        cornerRadius = 8.dp,
                        contentDescription = track.title
                    )
                    if (isThisTrackActive && isPlaying) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.backgroundRoot.copy(alpha = 0.65f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = HarmoniaIcons.Play,
                                contentDescription = "Playing",
                                modifier = Modifier.size(20.dp),
                                tint = colors.primarySignal
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(spacing.medium))

                // Title + Album / Format subtitle
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
                        Text(
                            text = track.albumTitle ?: (track.artistName ?: "Local Audio"),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (formatStr != null) {
                            Spacer(modifier = Modifier.width(4.dp))
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
