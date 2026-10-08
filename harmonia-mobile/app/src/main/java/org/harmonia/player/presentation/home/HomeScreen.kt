package org.harmonia.player.presentation.home

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
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.BorderSlate
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.ObsidianSpace
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.design.theme.StudioSlate
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

    if (uiState.isLoading) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(ObsidianSpace),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = AcousticTeal)
        }
        return
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianSpace)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            HeaderSection()
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Quick Actions
        item {
            QuickActionsSection(
                resumeTrack = uiState.resumeTrack,
                hasTracks = uiState.totalTrackCount > 0,
                onResume = onResumeClick,
                onShuffleAll = onShuffleAllClick
            )
            Spacer(modifier = Modifier.height(20.dp))
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
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Recently Played
        if (uiState.recentlyPlayed.isNotEmpty()) {
            item {
                SectionTitle(title = "RECENTLY PLAYED")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.recentlyPlayed, key = { "recent_${it.id}" }) { track ->
                        HorizontalTrackCard(track = track, onClick = { onTrackClick(track) })
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }

        // Recently Added
        if (uiState.recentlyAdded.isNotEmpty()) {
            item {
                SectionTitle(title = "RECENTLY ADDED")
                Spacer(modifier = Modifier.height(12.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.recentlyAdded, key = { "added_${it.id}" }) { track ->
                        HorizontalTrackCard(track = track, onClick = { onTrackClick(track) })
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
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
    Column {
        Text(
            text = "HARMONIA",
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                fontSize = 28.sp
            ),
            color = PureSignalWhite
        )
        Text(
            text = "USER-OWNED MUSIC PLAYER",
            style = MaterialTheme.typography.labelSmall,
            color = AcousticTeal
        )
    }
}

@Composable
private fun QuickActionsSection(
    resumeTrack: Track?,
    hasTracks: Boolean,
    onResume: () -> Unit,
    onShuffleAll: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (resumeTrack != null) {
            Button(
                onClick = onResume,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = "Resume playback of ${resumeTrack.title}" },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AcousticTeal,
                    contentColor = ObsidianSpace
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Resume", fontWeight = FontWeight.Bold)
            }
        }

        if (hasTracks) {
            OutlinedButton(
                onClick = onShuffleAll,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .semantics { contentDescription = "Shuffle all library tracks" },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = PureSignalWhite
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(BorderSlate)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = HarmoniaIcons.Play,
                    contentDescription = null,
                    tint = AcousticTeal,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ShortcutCard(
            title = "Albums",
            count = albumCount,
            icon = HarmoniaIcons.Album,
            onClick = onNavigateToLibrary,
            modifier = Modifier.weight(1f)
        )
        ShortcutCard(
            title = "Artists",
            count = artistCount,
            icon = HarmoniaIcons.Person,
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
    Card(
        modifier = modifier
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$title, $count items" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AcousticTeal,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = PureSignalWhite
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MutedSlate
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = PureSignalWhite
    )
}

@Composable
private fun HorizontalTrackCard(
    track: Track,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Play ${track.title} by ${track.artistName ?: "Unknown"}" },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            HarmoniaArtwork(
                artworkHash = track.localFile?.fileHash,
                size = 120.dp,
                cornerRadius = 8.dp,
                contentDescription = "${track.title} artwork"
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = track.title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                color = PureSignalWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = track.artistName ?: "Unknown Artist",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSlate,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyHomeState(onNavigateToLibrary: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderSlate, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AcousticCharcoal)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = HarmoniaIcons.MusicNote,
                contentDescription = null,
                tint = AcousticTeal,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Welcome to Harmonia",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PureSignalWhite
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your local music catalog is empty. Scan an offline music directory to get started.",
                style = MaterialTheme.typography.bodySmall,
                color = MutedSlate,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNavigateToLibrary,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AcousticTeal,
                    contentColor = ObsidianSpace
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Open Library", fontWeight = FontWeight.Bold)
            }
        }
    }
}
