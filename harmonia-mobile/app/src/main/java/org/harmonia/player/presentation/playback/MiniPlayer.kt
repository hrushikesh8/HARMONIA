package org.harmonia.player.presentation.playback

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.personalization.FavoriteButton
import kotlin.math.abs

/**
 * Floating mini player above the tab bar: artwork, title/artist, favorite, previous,
 * play/pause and next, with a thin lavender progress line along the bottom of the card.
 * Swipe left/right to skip tracks, swipe up to open Now Playing.
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
    onToggleFavorite: (() -> Unit)? = null,
    onPrevious: (() -> Unit)? = null
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

    val miniPlayerShape = RoundedCornerShape(14.dp)

    val swipeThresholdPx = with(LocalDensity.current) { 72.dp.toPx() }
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)
    val latestOnClick by rememberUpdatedState(onClick)
    val contentOffsetX by animateFloatAsState(
        targetValue = if (isDragging) dragX else 0f,
        animationSpec = if (isDragging) snap() else spring(),
        label = "miniPlayerSwipe"
    )

    // Floating card above the tab bar (Apple Music / Spotify style).
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = spacing.small)
            .padding(bottom = spacing.small)
            .shadow(if (colors.isDark) 0.dp else 10.dp, miniPlayerShape)
            .clip(miniPlayerShape)
            .background(if (colors.isDark) colors.surfaceContainer else colors.surfaceElevated)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragX = 0f
                        dragY = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragX += amount.x
                        dragY += amount.y
                    },
                    onDragEnd = {
                        isDragging = false
                        val horizontal = abs(dragX) > abs(dragY)
                        when {
                            horizontal && dragX <= -swipeThresholdPx -> latestOnNext()
                            horizontal && dragX >= swipeThresholdPx -> latestOnPrevious?.invoke()
                            !horizontal && dragY <= -swipeThresholdPx -> latestOnClick()
                        }
                    },
                    onDragCancel = { isDragging = false }
                )
            }
            .clickable(onClick = onClick)
            .semantics {
                customActions = listOfNotNull(
                    CustomAccessibilityAction("Next track") { onNext(); true },
                    onPrevious?.let { previous -> CustomAccessibilityAction("Previous track") { previous(); true } }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spacing.small, end = 2.dp, top = spacing.small, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Art thumbnail (40dp) via sandboxed HarmoniaArtwork
            HarmoniaArtwork(
                artworkHash = currentTrack?.artworkHash ?: currentTrack?.localFile?.fileHash,
                size = 44.dp,
                cornerRadius = 8.dp,
                contentDescription = "Artwork for $title"
            )

            Spacer(modifier = Modifier.width(spacing.medium))

            // Track metadata
            Column(
                modifier = Modifier
                    .weight(1f)
                    .graphicsLayer {
                        translationX = contentOffsetX
                        alpha = 1f - (abs(contentOffsetX) / (swipeThresholdPx * 3)).coerceIn(0f, 0.6f)
                    }
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
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

            if (onPrevious != null) {
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.touchTarget48()
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Play/Pause: plain icon, the accent stays on the progress line
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.touchTarget48()
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) HarmoniaIcons.Pause else HarmoniaIcons.Play,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = colors.textPrimary,
                    modifier = Modifier.size(30.dp)
                )
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

        // Progress line along the bottom edge of the card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.medium)
                .height(2.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(colors.textPrimary.copy(alpha = 0.12f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(2.dp)
                    .background(colors.primarySignal)
            )
        }
    }
}
