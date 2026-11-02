package org.harmonia.player.presentation.carmode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.harmonia.player.presentation.util.TrackDisplayUtils

@Composable
fun CarModeScreen(
    playbackViewModel: PlaybackViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by playbackViewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    val currentTrack = playerState.currentTrack
    val isPlaying = playerState.playbackState.isPlaying
    val title = currentTrack?.let { TrackDisplayUtils.formatDisplayTitle(it) } ?: "No Track Playing"
    val artist = currentTrack?.artistName ?: "Harmonia Car Mode"

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .padding(spacing.standard)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Header with Exit Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = HarmoniaIcons.DirectionsCar,
                        contentDescription = null,
                        tint = colors.primarySignal,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.small))
                    Text(
                        text = "CAR MODE",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        ),
                        color = colors.textPrimary
                    )
                }

                Button(
                    onClick = onExit,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceElevated,
                        contentColor = colors.textPrimary
                    ),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.semantics { contentDescription = "Exit Car Mode" }
                ) {
                    Text("Exit", fontWeight = FontWeight.Bold)
                }
            }

            // Central Massive Artwork
            Card(
                modifier = Modifier
                    .size(200.dp)
                    .hairlineBorder(colors.borderStructural, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated)
            ) {
                HarmoniaArtwork(
                    artworkHash = currentTrack?.artworkHash,
                    size = 200.dp,
                    cornerRadius = 16.dp,
                    contentDescription = title
                )
            }

            // Distraction-free Large Typography
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.medium)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(spacing.xSmall))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = colors.secondarySignal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            // Oversized In-Vehicle Controls (72dp and 88dp touch targets)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Previous
                IconButton(
                    onClick = { playbackViewModel.previous() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .semantics { contentDescription = "Previous Track" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.SkipPrevious,
                        contentDescription = "Previous",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Play / Pause
                IconButton(
                    onClick = {
                        if (isPlaying) playbackViewModel.pause() else playbackViewModel.play()
                    },
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(colors.primarySignal)
                        .semantics { contentDescription = if (isPlaying) "Pause" else "Play" }
                ) {
                    Icon(
                        imageVector = if (isPlaying) HarmoniaIcons.Pause else HarmoniaIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = colors.backgroundRoot,
                        modifier = Modifier.size(44.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = { playbackViewModel.next() },
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.surfaceElevated)
                        .semantics { contentDescription = "Next Track" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.SkipNext,
                        contentDescription = "Next",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))
        }
    }
}
