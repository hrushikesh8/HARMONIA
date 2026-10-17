package org.harmonia.player.presentation.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(),
    onNavigateToLibrary: () -> Unit = {},
    onNavigateToPlaylists: () -> Unit = {},
    onTrackClick: (Track) -> Unit = {},
    onResumeClick: () -> Unit = {},
    onShuffleAllClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(horizontal = spacing.standard),
        contentPadding = PaddingValues(bottom = spacing.bottomScaffold)
    ) {
        item {
            Spacer(modifier = Modifier.height(spacing.standard))
            HeaderSection()
            Spacer(modifier = Modifier.height(spacing.standard))
        }

        // Resume Section (Active or Meaningful Empty State)
        if (uiState.totalTrackCount > 0) {
            item {
                if (uiState.resumeTrack != null) {
                    ResumeCard(
                        track = uiState.resumeTrack!!,
                        onResume = onResumeClick
                    )
                } else {
                    NoResumeCard()
                }
                Spacer(modifier = Modifier.height(spacing.standard))
            }
        }

        // Quick Actions
        item {
            QuickActionsSection(
                resumeTrack = uiState.resumeTrack,
                hasTracks = uiState.totalTrackCount > 0,
                onResume = onResumeClick,
                onShuffleAll = onShuffleAllClick
            )
            Spacer(modifier = Modifier.height(spacing.large))
        }

        // Category Shortcuts
        item {
            CategoryShortcutsSection(
                albumCount = uiState.albums.size,
                artistCount = uiState.artists.size,
                playlistCount = uiState.playlists.size,
                onNavigateToLibrary = onNavigateToLibrary,
                onNavigateToPlaylists = onNavigateToPlaylists
            )
            Spacer(modifier = Modifier.height(spacing.large))
        }

        // Recently Played (Factual ListeningEvent history)
        if (uiState.recentlyPlayed.isNotEmpty()) {
            item {
                SectionTitle(title = "RECENTLY PLAYED")
                Spacer(modifier = Modifier.height(spacing.medium))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    items(uiState.recentlyPlayed, key = { "recent_${it.id}" }) { track ->
                        HorizontalTrackCard(track = track, onClick = { onTrackClick(track) })
                    }
                }
                Spacer(modifier = Modifier.height(spacing.large))
            }
        }

        // Recently Added (Authoritative track createdAt metadata)
        if (uiState.recentlyAdded.isNotEmpty()) {
            item {
                SectionTitle(title = "RECENTLY ADDED")
                Spacer(modifier = Modifier.height(spacing.medium))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(spacing.medium)
                ) {
                    items(uiState.recentlyAdded, key = { "added_${it.id}" }) { track ->
                        HorizontalTrackCard(track = track, onClick = { onTrackClick(track) })
                    }
                }
                Spacer(modifier = Modifier.height(spacing.large))
            }
        }

        // Empty Library State
        if (uiState.totalTrackCount == 0) {
            item {
                EmptyHomeState(onNavigateToLibrary = onNavigateToLibrary)
            }
        }
    }
}

@Composable
private fun HeaderSection() {
    val colors = HarmoniaThemeTokens.colors
    Column {
        Text(
            text = "HARMONIA",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontSize = 28.sp
            ),
            color = colors.textPrimary
        )
        Text(
            text = "USER-OWNED MUSIC PLAYER",
            style = MaterialTheme.typography.labelSmall,
            color = colors.primarySignal
        )
    }
}

@Composable
private fun ResumeCard(
    track: Track,
    onResume: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.medium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HarmoniaArtwork(
                artworkHash = track.localFile?.fileHash,
                size = 48.dp,
                cornerRadius = 8.dp,
                contentDescription = "${track.title} artwork"
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "RESUME PLAYBACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primarySignal,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = track.title,
                    color = colors.textPrimary,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artistName ?: "Unknown Artist",
                    color = colors.textSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(spacing.small))
            Button(
                onClick = onResume,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primarySignal,
                    contentColor = colors.backgroundRoot
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .touchTarget48()
                    .semantics { contentDescription = "Resume playback of ${track.title}" }
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Resume", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun NoResumeCard() {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.standard),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = HarmoniaIcons.MusicNote,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(spacing.medium))
            Column {
                Text(
                    text = "No Active Playback",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary
                )
                Text(
                    text = "Select any track from your library to start listening",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    resumeTrack: Track?,
    hasTracks: Boolean,
    onResume: () -> Unit,
    onShuffleAll: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium)
    ) {
        if (resumeTrack != null) {
            Button(
                onClick = onResume,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .touchTarget48()
                    .semantics { contentDescription = "Resume playback of ${resumeTrack.title}" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primarySignal,
                    contentColor = colors.backgroundRoot
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text("Resume", fontWeight = FontWeight.Bold)
            }
        }

        if (hasTracks) {
            OutlinedButton(
                onClick = onShuffleAll,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .touchTarget48()
                    .semantics { contentDescription = "Shuffle all library tracks" },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = colors.textPrimary
                ),
                border = BorderStroke(1.dp, colors.borderStructural),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Play,
                    contentDescription = null,
                    tint = colors.primarySignal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(spacing.small))
                Text("Shuffle All", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CategoryShortcutsSection(
    albumCount: Int,
    artistCount: Int,
    playlistCount: Int,
    onNavigateToLibrary: () -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    val spacing = HarmoniaThemeTokens.spacing

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        ShortcutCard(
            title = "Artists",
            count = artistCount,
            icon = HarmoniaIcons.Person,
            onClick = onNavigateToLibrary,
            modifier = Modifier.weight(1f)
        )
        ShortcutCard(
            title = "Albums",
            count = albumCount,
            icon = HarmoniaIcons.Album,
            onClick = onNavigateToLibrary,
            modifier = Modifier.weight(1f)
        )
        ShortcutCard(
            title = "Playlists",
            count = playlistCount,
            icon = HarmoniaIcons.Playlists,
            onClick = onNavigateToPlaylists,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ShortcutCard(
    title: String,
    count: Int,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = modifier
            .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Navigate to $title, $count available" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(spacing.medium),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.primarySignal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(spacing.xSmall))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    val colors = HarmoniaThemeTokens.colors
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = colors.textPrimary
    )
}

@Composable
private fun HorizontalTrackCard(
    track: Track,
    onClick: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = Modifier
            .width(140.dp)
            .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play ${track.title} by ${track.artistName ?: "Unknown"}" },
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            HarmoniaArtwork(
                artworkHash = track.localFile?.fileHash,
                size = 120.dp,
                cornerRadius = 8.dp,
                contentDescription = "${track.title} artwork"
            )

            Spacer(modifier = Modifier.height(spacing.small))

            Text(
                text = track.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = track.artistName ?: "Unknown Artist",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHomeState(onNavigateToLibrary: () -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val cardShape = RoundedCornerShape(16.dp)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(color = colors.borderStructural, shape = cardShape, width = 1.dp),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.large),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = HarmoniaIcons.MusicNote,
                contentDescription = null,
                tint = colors.primarySignal,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(spacing.medium))
            Text(
                text = "Welcome to Harmonia",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(spacing.xSmall))
            Text(
                text = "Your local music catalog is empty. Scan an offline music directory to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(spacing.standard))
            Button(
                onClick = onNavigateToLibrary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.primarySignal,
                    contentColor = colors.backgroundRoot
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .touchTarget48()
            ) {
                Text("Open Library", fontWeight = FontWeight.Bold)
            }
        }
    }
}
