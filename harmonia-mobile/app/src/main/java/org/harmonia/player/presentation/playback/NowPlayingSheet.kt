package org.harmonia.player.presentation.playback

import androidx.compose.foundation.background
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.launch
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.design.theme.hairlineBorder
import org.harmonia.player.design.theme.touchTarget48
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.QueueItem
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.audio.SleepTimerMode
import org.harmonia.player.domain.model.audio.SleepTimerPreset
import org.harmonia.player.domain.model.audio.SleepTimerState
import org.harmonia.player.presentation.components.DragAutoScroll
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.personalization.RatingDialog
import java.util.Locale

/**
 * Full-screen Now Playing (Apple Music layout, Harmonia theme): big artwork, title and favorite,
 * slim scrubber with quality badge, transport controls, phone volume, and sleep timer / rating /
 * queue actions. Swipe down or tap the chevron to close; the queue replaces the artwork in place.
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
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onStartSleepTimer: (SleepTimerPreset) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onDismiss: () -> Unit,
    isFavorite: Boolean = false,
    rating: Int? = null,
    onToggleFavorite: () -> Unit = {},
    onSetRating: (Int) -> Unit = {},
    onClearRating: () -> Unit = {},
    onAlbumClick: ((String) -> Unit)? = null,
    onArtistClick: ((String) -> Unit)? = null,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (index: Int) -> Unit = {},
    onOpenTrackMenu: (() -> Unit)? = null
) {
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }

    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderPosition by remember { mutableFloatStateOf(0f) }

    val totalDurationMs = playbackState.durationMs
    val currentPositionMs = if (isUserSeeking) {
        (seekSliderPosition * totalDurationMs).toLong()
    } else {
        playbackState.currentPositionMs
    }

    if (showSleepTimerDialog) {
        SleepTimerDialog(
            sleepTimerState = sleepTimerState,
            onSelectPreset = onStartSleepTimer,
            onCancelTimer = onCancelSleepTimer,
            onDismiss = { showSleepTimerDialog = false }
        )
    }

    if (showRatingDialog && currentTrack != null) {
        RatingDialog(
            currentRating = rating,
            onRatingSelected = { newRating ->
                onSetRating(newRating)
            },
            onClearRating = {
                onClearRating()
                showRatingDialog = false
            },
            onDismissRequest = { showRatingDialog = false },
            trackTitle = currentTrack.title,
            artistName = currentTrack.artistName
        )
    }

    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val volume = rememberMediaVolume()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()

    // Swipe down anywhere outside a scrolling list to close (Apple Music style).
    val dragOffset = remember { Animatable(0f) }
    val dismissThresholdPx = with(density) { 140.dp.toPx() }

    // Artwork eases back slightly while paused.
    val artworkScale by animateFloatAsState(
        targetValue = if (playbackState.isPlaying || currentTrack == null) 1f else 0.88f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "artworkScale"
    )

    val displayTitle = remember(currentTrack) {
        currentTrack?.let { org.harmonia.player.presentation.util.TrackDisplayUtils.formatDisplayTitle(it) } ?: "Nothing playing"
    }
    val formatPillText = remember(currentTrack) { currentTrack?.let { formatBadgeText(it) } }

    // Background tinted from the album art, fading into the normal background (task 4.1).
    val artworkTint by rememberArtworkTint(
        artworkHash = currentTrack?.artworkHash,
        isDark = colors.isDark,
        fallback = colors.backgroundRoot
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = dragOffset.value.coerceAtLeast(0f) }
            .background(colors.backgroundRoot)
            .background(Brush.verticalGradient(0f to artworkTint, 0.75f to colors.backgroundRoot))
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (dragOffset.value > dismissThresholdPx) {
                                onDismiss()
                            } else {
                                dragOffset.animateTo(0f, spring(stiffness = 500f))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch { dragOffset.animateTo(0f) }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            dragOffset.snapTo((dragOffset.value + dragAmount).coerceAtLeast(0f))
                        }
                    }
                )
            }
            .semantics { paneTitle = "Now Playing" }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.standard),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Grabber
            Box(
                modifier = Modifier
                    .padding(top = spacing.small)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(colors.textDisabled)
            )

            // Header: close, "Playing from", more
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .touchTarget48()
                        .semantics { contentDescription = "Close Now Playing" }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.KeyboardArrowDown,
                        contentDescription = null,
                        tint = colors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (showQueue) "Up next" else "Playing from",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textSecondary
                    )
                    Text(
                        text = if (showQueue) {
                            "${queue.size} ${if (queue.size == 1) "song" else "songs"} in queue"
                        } else {
                            currentTrack?.albumTitle?.takeIf { it.isNotBlank() } ?: "Your library"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (onOpenTrackMenu != null) {
                    IconButton(
                        onClick = onOpenTrackMenu,
                        modifier = Modifier
                            .touchTarget48()
                            .semantics { contentDescription = "More options for current track" }
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.MoreVert,
                            contentDescription = null,
                            tint = colors.textPrimary
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            if (showQueue) {
                // Compact header for the song that's playing
                if (currentTrack != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HarmoniaArtwork(
                            artworkHash = currentTrack.artworkHash,
                            modifier = Modifier.size(56.dp),
                            size = 56.dp,
                            cornerRadius = 8.dp,
                            contentDescription = "${currentTrack.title} artwork"
                        )
                        Spacer(modifier = Modifier.width(spacing.medium))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentTrack.artistName ?: "Unknown artist",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        FavoriteButton(
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            enabled = true
                        )
                    }
                    Spacer(modifier = Modifier.height(spacing.medium))
                }
                val activeIndex = playbackState.activeQueueIndex
                    .takeIf { it in queue.indices }
                    ?: queue.indexOfFirst { it.trackId == playbackState.currentTrackId }
                QueueView(
                    queue = queue,
                    activeIndex = activeIndex,
                    isPlaying = playbackState.isPlaying,
                    onItemClick = onQueueItemClick,
                    onMove = onMoveQueueItem,
                    onRemove = onRemoveQueueItem,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                // Big artwork, sized to whatever space the controls leave
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    val side = minOf(maxWidth, maxHeight)
                    Box(
                        modifier = Modifier
                            .size(side)
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                            }
                            .shadow(24.dp, RoundedCornerShape(14.dp), clip = false)
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.surfaceElevated)
                    ) {
                        HarmoniaArtwork(
                            artworkHash = currentTrack?.artworkHash,
                            modifier = Modifier.fillMaxSize(),
                            size = side,
                            cornerRadius = 14.dp,
                            contentDescription = currentTrack?.let { "${it.title} Artwork" } ?: "Album Artwork"
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.large))

                // Title, artist and favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val artistClickable = onArtistClick != null && currentTrack?.artistId != null
                        Text(
                            text = currentTrack?.artistName?.takeIf { it.isNotBlank() } ?: "Unknown artist",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable(enabled = artistClickable) {
                                currentTrack?.artistId?.let { onArtistClick?.invoke(it) }
                            }
                        )
                    }
                    FavoriteButton(
                        isFavorite = isFavorite,
                        onToggleFavorite = onToggleFavorite,
                        enabled = currentTrack != null
                    )
                }

                // Storage disconnected / playback error
                if (playbackState.errorMessage != null) {
                    val errorMsg = playbackState.errorMessage
                    val isStorageDisconnected = errorMsg == PlaybackState.STORAGE_DISCONNECTED_MESSAGE
                    Spacer(modifier = Modifier.height(spacing.small))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceContainer)
                            .semantics {
                                contentDescription = if (isStorageDisconnected) PlaybackState.STORAGE_DISCONNECTED_MESSAGE else errorMsg
                            }
                            .padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Info,
                            contentDescription = null,
                            tint = if (isStorageDisconnected) colors.statusWarning else colors.statusError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Text(
                            text = if (isStorageDisconnected) {
                                "This song's storage isn't connected. Reconnect it to keep listening."
                            } else {
                                "This song couldn't be played."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Scrubber
            val sliderValue = if (totalDurationMs > 0) {
                if (isUserSeeking) seekSliderPosition else (playbackState.currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            HarmoniaThinSlider(
                value = sliderValue,
                onValueChange = {
                    isUserSeeking = true
                    seekSliderPosition = it
                },
                onValueChangeFinished = {
                    isUserSeeking = false
                    onSeekTo((seekSliderPosition * totalDurationMs).toLong())
                },
                contentDescription = "Playback seek slider"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    modifier = Modifier.weight(1f)
                )
                if (formatPillText != null) {
                    Text(
                        text = formatPillText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.technicalAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
                Text(
                    text = "-" + formatTime((totalDurationMs - currentPositionMs).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(spacing.medium))

            // Transport controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .touchTarget48()
                        .semantics {
                            role = Role.Button
                            contentDescription = getShuffleAccessibilityLabel(playbackState.shuffleMode)
                        }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.Shuffle,
                        contentDescription = null,
                        tint = if (playbackState.shuffleMode) colors.primarySignal else colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.primarySignal)
                        .clickable(onClick = onPlayPause)
                        .semantics {
                            role = Role.Button
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play"
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) HarmoniaIcons.Pause else HarmoniaIcons.Play,
                        contentDescription = null,
                        tint = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.SkipNext,
                        contentDescription = "Next Track",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .touchTarget48()
                        .semantics {
                            role = Role.Button
                            contentDescription = getRepeatAccessibilityLabel(playbackState.repeatMode)
                        }
                ) {
                    Icon(
                        imageVector = when (playbackState.repeatMode) {
                            RepeatMode.ONE -> HarmoniaIcons.RepeatOne
                            else -> HarmoniaIcons.Repeat
                        },
                        contentDescription = null,
                        tint = if (playbackState.repeatMode != RepeatMode.OFF) colors.primarySignal else colors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Volume (phone media volume, in sync with the hardware keys)
            if (volume.isAvailable) {
                Spacer(modifier = Modifier.height(spacing.small))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.VolumeDown,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    HarmoniaThinSlider(
                        value = volume.fraction,
                        onValueChange = { volume.setFraction(it) },
                        contentDescription = "Volume",
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = spacing.small),
                        secondary = true
                    )
                    Icon(
                        imageVector = HarmoniaIcons.VolumeUp,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Bottom actions: sleep timer, rating, queue
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showSleepTimerDialog = true },
                        modifier = Modifier
                            .touchTarget48()
                            .semantics {
                                contentDescription = if (sleepTimerState.isActive) {
                                    val modeDesc = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) "End of track" else formatRemainingTimer(sleepTimerState.remainingDurationMs)
                                    "Sleep timer active, $modeDesc remaining"
                                } else {
                                    "Set sleep timer"
                                }
                            }
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Timer,
                            contentDescription = null,
                            tint = if (sleepTimerState.isActive) colors.primarySignal else colors.textSecondary
                        )
                    }
                    if (sleepTimerState.isActive) {
                        Text(
                            text = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) "End of song" else formatRemainingTimer(sleepTimerState.remainingDurationMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primarySignal
                        )
                    }
                }
                IconButton(
                    onClick = { showRatingDialog = true },
                    enabled = currentTrack != null,
                    modifier = Modifier
                        .touchTarget48()
                        .semantics {
                            contentDescription = if (rating != null) "Rating: $rating of 5 stars" else "Rate track"
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = if (rating != null) HarmoniaIcons.Star else HarmoniaIcons.StarBorder,
                            contentDescription = null,
                            tint = if (rating != null) colors.primarySignal else colors.textSecondary
                        )
                        if (rating != null) {
                            Text(
                                text = "$rating",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primarySignal
                            )
                        }
                    }
                }
                IconButton(
                    onClick = { showQueue = !showQueue },
                    modifier = Modifier
                        .touchTarget48()
                        .clip(CircleShape)
                        .background(if (showQueue) colors.surfaceContainer else Color.Transparent)
                        .semantics {
                            contentDescription = if (showQueue) "Show Now Playing" else "Show Queue"
                        }
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.QueueMusic,
                        contentDescription = null,
                        tint = if (showQueue) colors.primarySignal else colors.textSecondary
                    )
                }
            }
        }
    }
}

/** Short quality badge under the scrubber, e.g. "FLAC 24-bit / 96 kHz", "MP3 320 kbps". */
internal fun formatBadgeText(track: Track): String? {
    val file = track.localFile ?: return null
    val fmt = file.format.ifBlank { file.fileUri.substringAfterLast('.', "") }.uppercase(Locale.ROOT)
    return when {
        file.bitDepth != null && file.sampleRate != null -> {
            val khz = file.sampleRate / 1000.0
            val khzStr = if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", khz)
            "$fmt ${file.bitDepth}-bit / $khzStr"
        }
        file.isLossless -> "$fmt Lossless"
        file.bitrate != null -> "$fmt ${file.bitrate} kbps"
        fmt.isNotEmpty() -> fmt
        else -> null
    }
}

/**
 * Slim Apple-style slider with a full touch height.
 * - [secondary] = false (song progress): 4dp lavender track that thickens to 8dp while dragged.
 * - [secondary] = true (volume): quieter 3dp grey track and a smaller thumb, so the eye goes to the
 *   song progress first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HarmoniaThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onValueChangeFinished: (() -> Unit)? = null,
    secondary: Boolean = false
) {
    val colors = HarmoniaThemeTokens.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val active = isDragged || isPressed
    val trackHeight by animateDpAsState(
        targetValue = when {
            secondary -> if (active) 5.dp else 3.dp
            else -> if (active) 8.dp else 4.dp
        },
        label = "sliderTrack"
    )
    val thumbSize by animateDpAsState(
        targetValue = when {
            secondary -> if (active) 14.dp else 10.dp
            else -> if (active) 18.dp else 14.dp
        },
        label = "sliderThumb"
    )
    val fill = if (secondary) colors.textSecondary else colors.primarySignal
    val thumbColor = if (secondary) colors.textSecondary else colors.textPrimary
    val sliderColors = SliderDefaults.colors(
        thumbColor = thumbColor,
        activeTrackColor = fill,
        inactiveTrackColor = colors.surfaceContainer
    )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        interactionSource = interactionSource,
        modifier = modifier
            .height(36.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = sliderColors,
        thumb = {
            Box(
                modifier = Modifier
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        },
        track = { state ->
            SliderDefaults.Track(
                sliderState = state,
                colors = sliderColors,
                modifier = Modifier.height(trackHeight),
                thumbTrackGapSize = 0.dp,
                drawStopIndicator = null
            )
        }
    )
}

private data class QueueEntry(val key: String, val item: QueueItem)

/**
 * Editable play queue: tap to jump, drag the handle to reorder, swipe to remove.
 * Reordering happens locally while dragging and is committed once on release.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueView(
    queue: List<QueueItem>,
    activeIndex: Int,
    isPlaying: Boolean,
    onItemClick: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    if (queue.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = HarmoniaIcons.QueueMusic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = colors.textSecondary.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(spacing.small))
                Text(
                    text = "No upcoming tracks in queue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        }
        return
    }

    val entries = remember(queue) { queue.mapIndexed { idx, item -> QueueEntry("${item.trackId}#$idx", item) } }
    var workingEntries by remember(entries) { mutableStateOf(entries) }
    val activeKey = entries.getOrNull(activeIndex)?.key
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var dragStartIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        if (activeIndex > 0) listState.scrollToItem(activeIndex)
    }

    fun dragBy(deltaY: Float) {
        val key = draggingKey ?: return
        dragOffset += deltaY
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == key } ?: return
        val draggedCenter = dragged.offset + dragOffset + dragged.size / 2f
        val target = visible.firstOrNull { other ->
            other.key != key && draggedCenter >= other.offset && draggedCenter <= other.offset + other.size
        } ?: return
        val from = workingEntries.indexOfFirst { it.key == key }
        if (from < 0 || target.index !in workingEntries.indices) return
        workingEntries = workingEntries.toMutableList().apply { add(target.index, removeAt(from)) }
        dragOffset += (dragged.offset - target.offset)
    }

    fun endDrag() {
        val key = draggingKey ?: return
        val to = workingEntries.indexOfFirst { it.key == key }
        if (dragStartIndex >= 0 && to >= 0 && to != dragStartIndex) {
            onMove(dragStartIndex, to)
        }
        draggingKey = null
        dragStartIndex = -1
        dragOffset = 0f
    }

    // Scroll the queue when a dragged song reaches the top or bottom edge.
    DragAutoScroll(
        listState = listState,
        draggingKey = draggingKey,
        dragOffset = { dragOffset },
        onScrolled = { dragBy(it) }
    )

    val upNextCount = (queue.size - activeIndex - 1).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "$upNextCount up next \u2022 drag \u2261 to reorder, swipe to remove",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            modifier = Modifier.padding(bottom = spacing.small)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(workingEntries, key = { _, entry -> entry.key }) { idx, entry ->
                val isCurrent = entry.key == activeKey
                val isDragging = entry.key == draggingKey
                val dismissState = rememberSwipeToDismissBoxState()

                Box(
                    modifier = if (isDragging) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer { translationY = dragOffset }
                    } else {
                        Modifier.animateItem()
                    }
                ) {
                    SwipeToDismissBox(
                        state = dismissState,
                        gesturesEnabled = !isCurrent && draggingKey == null,
                        onDismiss = {
                            val index = queue.indexOf(entry.item)
                            if (index >= 0) onRemove(index)
                        },
                        backgroundContent = {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.statusError.copy(alpha = 0.2f))
                                    .padding(horizontal = spacing.medium),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(HarmoniaIcons.Delete, contentDescription = null, tint = colors.statusError)
                                Icon(HarmoniaIcons.Delete, contentDescription = null, tint = colors.statusError)
                            }
                        }
                    ) {
                        QueueRow(
                            item = entry.item,
                            position = idx,
                            isCurrent = isCurrent,
                            isPlaying = isPlaying,
                            isDragging = isDragging,
                            onClick = {
                                val index = queue.indexOf(entry.item)
                                if (index >= 0) onItemClick(index)
                            },
                            dragHandleModifier = Modifier.pointerInput(entry.key) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingKey = entry.key
                                        dragStartIndex = workingEntries.indexOfFirst { it.key == entry.key }
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragBy(amount.y)
                                    },
                                    onDragEnd = { endDrag() },
                                    onDragCancel = { endDrag() }
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: QueueItem,
    position: Int,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean,
    onClick: () -> Unit,
    dragHandleModifier: Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing
    val track = item.track
    val title = track?.let { org.harmonia.player.presentation.util.TrackDisplayUtils.formatDisplayTitle(it) } ?: item.trackId
    val artist = track?.artistName ?: "Unknown Artist"
    val durationMs = ((track?.durationSec ?: 0.0) * 1000).toLong()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            // Opaque base so the swipe-to-remove background never shows through tinted rows
            .background(colors.surfaceElevated)
            .background(
                when {
                    isDragging -> colors.surfaceContainer
                    isCurrent -> colors.primarySignal.copy(alpha = 0.12f)
                    else -> colors.surfaceElevated
                }
            )
            .hairlineBorder(if (isCurrent) colors.primarySignal.copy(alpha = 0.5f) else colors.borderStructural, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = if (isCurrent) "Now playing: $title" else "Play $title from queue" }
            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.width(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isCurrent && isPlaying) {
                Icon(
                    imageVector = HarmoniaIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = colors.primarySignal
                )
            } else {
                Text(
                    text = "${position + 1}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isCurrent) colors.primarySignal else colors.textSecondary
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        HarmoniaArtwork(
            artworkHash = track?.artworkHash ?: track?.localFile?.fileHash,
            size = 38.dp,
            cornerRadius = 6.dp,
            contentDescription = null
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (isCurrent) colors.primarySignal else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (durationMs > 0) {
            Spacer(modifier = Modifier.width(spacing.small))
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary
            )
        }

        Box(
            modifier = dragHandleModifier
                .size(48.dp)
                .semantics { contentDescription = "Drag to reorder $title" },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = HarmoniaIcons.DragHandle,
                contentDescription = null,
                tint = colors.textSecondary
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

private fun formatRemainingTimer(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return if (minutes >= 60) {
        val hours = minutes / 60
        val remMin = minutes % 60
        String.format(Locale.US, "%d:%02d:%02d", hours, remMin, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

@Composable
private fun SleepTimerDialog(
    sleepTimerState: SleepTimerState,
    onSelectPreset: (SleepTimerPreset) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = HarmoniaThemeTokens.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surfaceElevated,
        titleContentColor = colors.textPrimary,
        textContentColor = colors.textSecondary,
        title = {
            Text(
                text = "SLEEP TIMER",
                style = MaterialTheme.typography.labelMedium.copy(
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = colors.primarySignal
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (sleepTimerState.isActive) {
                    val remainingText = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) {
                        "Active: End of current track"
                    } else {
                        "Active: ${formatRemainingTimer(sleepTimerState.remainingDurationMs)} remaining"
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = remainingText,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.primarySignal
                        )
                    }
                }

                SleepTimerPreset.entries.forEach { preset ->
                    val isSelected = sleepTimerState.isActive && (
                        (preset == SleepTimerPreset.END_OF_TRACK && sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) ||
                        (preset != SleepTimerPreset.END_OF_TRACK && sleepTimerState.mode == SleepTimerMode.DURATION && sleepTimerState.targetDurationMs == preset.durationMs)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) colors.surfaceContainer else colors.surfaceElevated)
                            .border(1.dp, if (isSelected) colors.primarySignal else colors.borderStructural, RoundedCornerShape(8.dp))
                            .clickable {
                                onSelectPreset(preset)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isSelected) colors.primarySignal else colors.textPrimary
                        )
                        if (isSelected) {
                            Text(
                                text = "Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.primarySignal
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (sleepTimerState.isActive) {
                TextButton(
                    onClick = {
                        onCancelTimer()
                        onDismiss()
                    }
                ) {
                    Text(text = "Turn Off Timer", color = colors.textPrimary)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Close", color = colors.textSecondary)
            }
        }
    )
}

internal fun getShuffleAccessibilityLabel(shuffleMode: Boolean): String =
    if (shuffleMode) "Shuffle on" else "Shuffle off"

internal fun getRepeatAccessibilityLabel(repeatMode: RepeatMode): String =
    when (repeatMode) {
        RepeatMode.OFF -> "Repeat off"
        RepeatMode.ALL -> "Repeat all"
        RepeatMode.ONE -> "Repeat one"
    }
