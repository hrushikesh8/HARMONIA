package org.harmonia.player.presentation.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.personalization.FavoriteButton

/**
 * Compact persistent mini-player docked above the bottom navigation bar.
 * Adheres to the Obsidian Acoustic Instrument design system (40dp artwork, 2dp progress, 48dp touch targets).
 */
@Composable
fun MiniPlayer(
    playbackState: PlaybackState,
    currentTrack: Track?,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null
) {
    if (currentTrack == null && playbackState.currentTrackId == null) {
        return
    }

    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    val title = remember(currentTrack) {
        currentTrack?.let { org.harmonia.player.presentation.util.TrackDisplayUtils.formatDisplayTitle(it) } ?: "Unknown Track"
    }
    val subtitle = listOfNotNull(currentTrack?.artistName, currentTrack?.albumTitle)
        .joinToString(" • ").ifEmpty { "Harmonia Local Audio" }

    val progress = if (playbackState.durationMs > 0) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val miniPlayerShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(miniPlayerShape)
            .background(colors.surfaceElevated)
            .hairlineBorder(color = colors.borderStructural, shape = miniPlayerShape, width = 1.dp)
            .clickable(onClick = onClick)
    ) {
        // Track Progress Hairline Indicator (2dp)
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp),
            color = colors.primarySignal,
            trackColor = colors.surfaceContainer
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.standard, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Art thumbnail (40dp) via sandboxed HarmoniaArtwork
            HarmoniaArtwork(
                artworkHash = currentTrack?.artworkHash,
                size = 40.dp,
                cornerRadius = 8.dp,
                contentDescription = "Artwork for $title"
            )

            Spacer(modifier = Modifier.width(spacing.medium))

            // Track metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Transport controls (strict 48dp accessible touch targets)
            if (onToggleFavorite != null) {
                FavoriteButton(
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    enabled = currentTrack != null
                )
            }

            // Play/Pause button with Spotify Green accent
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.touchTarget48()
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(colors.primarySignal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) HarmoniaIcons.Pause else HarmoniaIcons.Play,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        tint = colors.backgroundRoot,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.touchTarget48()
            ) {
                Icon(
                    imageVector = HarmoniaIcons.SkipNext,
                    contentDescription = "Next Track",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
