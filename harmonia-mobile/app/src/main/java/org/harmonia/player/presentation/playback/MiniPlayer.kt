package org.harmonia.player.presentation.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.BorderSlate
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.design.theme.StudioSlate
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Track

/**
 * Compact persistent mini-player docked above the bottom navigation bar.
 */
@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    currentTrack: Track?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (currentTrack == null && playbackState.currentTrackId == null) {
        return
    }

    val title = currentTrack?.title ?: "Unknown Track"
    val subtitle = listOfNotNull(currentTrack?.artistName, currentTrack?.albumTitle)
        .joinToString(" • ").ifEmpty { "Harmonia Offline Player" }

    val progress = if (playbackState.durationMs > 0) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(AcousticCharcoal)
            .border(1.dp, BorderSlate, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .clickable(onClick = onClick)
    ) {
        // Track Progress Bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp),
            color = AcousticTeal,
            trackColor = StudioSlate
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Art / Placeholder
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(StudioSlate),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "♪",
                    style = MaterialTheme.typography.titleMedium,
                    color = AcousticTeal
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Track metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PureSignalWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MutedSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Transport controls
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (playbackState.isPlaying) HarmoniaIcons.Pause else HarmoniaIcons.Play,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = PureSignalWhite,
                    modifier = Modifier.size(24.dp)
                )
            }

            IconButton(onClick = onNext) {
                Icon(
                    imageVector = HarmoniaIcons.SkipNext,
                    contentDescription = "Next Track",
                    tint = PureSignalWhite,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
