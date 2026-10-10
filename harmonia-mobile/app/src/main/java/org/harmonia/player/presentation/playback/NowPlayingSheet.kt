package org.harmonia.player.presentation.playback

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import org.harmonia.player.design.theme.AcousticCharcoal
import org.harmonia.player.design.theme.AcousticTeal
import org.harmonia.player.design.theme.BorderSlate
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.MutedSlate
import org.harmonia.player.design.theme.ObsidianSpace
import org.harmonia.player.design.theme.PureSignalWhite
import org.harmonia.player.design.theme.StudioSlate
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import java.util.Locale

/**
 * Now Playing Modal Bottom Sheet featuring full transport controls, seek bar, and queue sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(
    playbackState: PlaybackState,
    currentTrack: Track?,
    queue: List<QueueItem>,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onQueueItemClick: (Int) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showQueue by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderPosition by remember { mutableFloatStateOf(0f) }

    val totalDurationMs = playbackState.durationMs
    val currentPositionMs = if (isUserSeeking) {
        (seekSliderPosition * totalDurationMs).toLong()
    } else {
        playbackState.currentPositionMs
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = ObsidianSpace,
        contentColor = PureSignalWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (showQueue) "PLAYING QUEUE (${queue.size})" else "NOW PLAYING",
                    style = MaterialTheme.typography.labelMedium.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = AcousticTeal
                )
                IconButton(onClick = { showQueue = !showQueue }) {
                    Icon(
                        imageVector = HarmoniaIcons.QueueMusic,
                        contentDescription = if (showQueue) "Show Now Playing" else "Show Queue",
                        tint = if (showQueue) AcousticTeal else MutedSlate
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (showQueue) {
                // Queue View
                QueueView(
                    queue = queue,
                    activeTrackId = playbackState.currentTrackId,
                    onItemClick = onQueueItemClick,
                    modifier = Modifier.height(380.dp)
                )
            } else {
                // Large Artwork
                HarmoniaArtwork(
                    artworkHash = currentTrack?.artworkHash,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f),
                    size = 280.dp,
                    cornerRadius = 20.dp,
                    contentDescription = currentTrack?.let { "${it.title} Artwork" } ?: "Album Artwork"
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Track Title and Artist
                Text(
                    text = currentTrack?.title ?: "No Track Playing",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    ),
                    color = PureSignalWhite,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(currentTrack?.artistName, currentTrack?.albumTitle).joinToString(" • ")
                        .ifEmpty { "Harmonia Native Audio" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSlate,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Storage Disconnection / Playback Error Banner
                if (playbackState.errorMessage != null) {
                    val errorMsg = playbackState.errorMessage
                    val isStorageDisconnected = errorMsg == PlaybackState.STORAGE_DISCONNECTED_MESSAGE
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(StudioSlate)
                            .semantics {
                                contentDescription = if (isStorageDisconnected) {
                                    PlaybackState.STORAGE_DISCONNECTED_MESSAGE
                                } else {
                                    errorMsg
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Settings,
                            contentDescription = null,
                            tint = AcousticTeal,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isStorageDisconnected) "Storage Disconnected" else "Playback Error",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = PureSignalWhite
                            )
                            Text(
                                text = if (isStorageDisconnected) {
                                    "The storage volume for this track is unavailable. Reconnect storage to resume playback."
                                } else {
                                    errorMsg
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MutedSlate
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Seek Progress Bar
                val sliderValue = if (totalDurationMs > 0) {
                    if (isUserSeeking) seekSliderPosition else (playbackState.currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Slider(
                    value = sliderValue,
                    onValueChange = {
                        isUserSeeking = true
                        seekSliderPosition = it
                    },
                    onValueChangeFinished = {
                        isUserSeeking = false
                        onSeekTo((seekSliderPosition * totalDurationMs).toLong())
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = AcousticTeal,
                        activeTrackColor = AcousticTeal,
                        inactiveTrackColor = StudioSlate
                    )
                )

                // Time indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSlate
                    )
                    Text(
                        text = formatTime(totalDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedSlate
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Full Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(onClick = onToggleShuffle) {
                        Text(
                            text = "SHF",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (playbackState.shuffleMode) AcousticTeal else MutedSlate
                        )
                    }

                    // Previous
                    IconButton(onClick = onPrevious) {
                        Icon(
                            imageVector = HarmoniaIcons.SkipPrevious,
                            contentDescription = "Previous Track",
                            tint = PureSignalWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Play/Pause Big Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(AcousticTeal)
                            .clickable(onClick = onPlayPause),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) HarmoniaIcons.Pause else HarmoniaIcons.Play,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = ObsidianSpace,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Next
                    IconButton(onClick = onNext) {
                        Icon(
                            imageVector = HarmoniaIcons.SkipNext,
                            contentDescription = "Next Track",
                            tint = PureSignalWhite,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // Repeat
                    IconButton(onClick = onCycleRepeat) {
                        Text(
                            text = when (playbackState.repeatMode) {
                                RepeatMode.OFF -> "RPT"
                                RepeatMode.ALL -> "ALL"
                                RepeatMode.ONE -> "ONE"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (playbackState.repeatMode != RepeatMode.OFF) AcousticTeal else MutedSlate
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueView(
    queue: List<QueueItem>,
    activeTrackId: String?,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Queue is empty", color = MutedSlate)
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(queue, key = { idx, item -> "${item.trackId}_$idx" }) { idx, item ->
                val isCurrent = item.trackId == activeTrackId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) StudioSlate else AcousticCharcoal)
                        .clickable { onItemClick(idx) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${idx + 1}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent) AcousticTeal else MutedSlate,
                        modifier = Modifier.width(28.dp)
                    )
                    HarmoniaArtwork(
                        artworkHash = item.track?.artworkHash,
                        size = 36.dp,
                        cornerRadius = 6.dp,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.track?.title ?: item.trackId,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isCurrent) AcousticTeal else PureSignalWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.track?.artistName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedSlate,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
