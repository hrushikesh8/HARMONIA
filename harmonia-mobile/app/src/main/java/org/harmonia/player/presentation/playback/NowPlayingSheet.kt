package org.harmonia.player.presentation.playback

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.personalization.FavoriteButton
import org.harmonia.player.presentation.personalization.RatingDialog
import java.util.Locale

/**
 * Now Playing Modal Bottom Sheet featuring full transport controls, seek bar, queue view, and sleep timer.
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.backgroundRoot,
        contentColor = colors.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.xLarge),
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
                    color = colors.primarySignal
                )
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
                            text = if (sleepTimerState.mode == SleepTimerMode.END_OF_TRACK) "End" else formatRemainingTimer(sleepTimerState.remainingDurationMs),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = colors.primarySignal,
                            modifier = Modifier.padding(end = 4.dp)
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
                                tint = colors.textSecondary
                            )
                        }
                    }
                    IconButton(
                        onClick = { showQueue = !showQueue },
                        modifier = Modifier
                            .touchTarget48()
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

            Spacer(modifier = Modifier.height(spacing.standard))

            if (showQueue) {
                // Queue View
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
                        .height(420.dp)
                )

                Spacer(modifier = Modifier.height(spacing.medium))

                // Compact current track info row
                if (currentTrack != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surfaceElevated)
                            .padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Now Playing: ",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primarySignal
                        )
                        Text(
                            text = currentTrack.title,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                // Large Responsive Artwork with Hairline Border
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .sizeIn(maxWidth = 300.dp, maxHeight = 300.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .hairlineBorder(colors.borderStructural, RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    HarmoniaArtwork(
                        artworkHash = currentTrack?.artworkHash,
                        modifier = Modifier.fillMaxSize(),
                        size = 280.dp,
                        cornerRadius = 16.dp,
                        contentDescription = currentTrack?.let { "${it.title} Artwork" } ?: "Album Artwork"
                    )
                }

                Spacer(modifier = Modifier.height(spacing.large))

                // Track Title, Artist, and Personalization Actions (Favorite & Rating)
                val displayTitle = remember(currentTrack) {
                    currentTrack?.let { org.harmonia.player.presentation.util.TrackDisplayUtils.formatDisplayTitle(it) } ?: "No Track Playing"
                }
                val formatPillText = remember(currentTrack) {
                    currentTrack?.localFile?.let { file ->
                        val fmt = file.format.ifBlank {
                            file.fileUri.substringAfterLast('.', "")
                        }.uppercase(Locale.ROOT)
                        when {
                            file.bitDepth != null && file.sampleRate != null -> {
                                val khz = file.sampleRate / 1000.0
                                val khzStr = if (khz % 1.0 == 0.0) "${khz.toInt()} kHz" else String.format(Locale.US, "%.1f kHz", khz)
                                "$fmt • ${file.bitDepth}-BIT / $khzStr"
                            }
                            file.isLossless -> "$fmt • LOSSLESS"
                            file.bitrate != null -> "$fmt • ${file.bitrate} KBPS"
                            fmt.isNotEmpty() -> fmt
                            else -> null
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!currentTrack?.artistName.isNullOrBlank()) {
                                Text(
                                    text = currentTrack.artistName!!,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = if (onArtistClick != null && currentTrack.artistId != null) colors.secondarySignal else colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = onArtistClick != null && currentTrack.artistId != null) {
                                        currentTrack.artistId?.let { onArtistClick?.invoke(it) }
                                    }
                                )
                            }
                            if (!currentTrack?.artistName.isNullOrBlank() && !currentTrack?.albumTitle.isNullOrBlank()) {
                                Text(
                                    text = " • ",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                            if (!currentTrack?.albumTitle.isNullOrBlank()) {
                                Text(
                                    text = currentTrack.albumTitle!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (onAlbumClick != null && currentTrack.albumId != null) colors.secondarySignal else colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable(enabled = onAlbumClick != null && currentTrack.albumId != null) {
                                        currentTrack.albumId?.let { onAlbumClick?.invoke(it) }
                                    }
                                )
                            }
                            if (currentTrack?.artistName.isNullOrBlank() && currentTrack?.albumTitle.isNullOrBlank()) {
                                Text(
                                    text = "Harmonia Local Audio",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textSecondary
                                )
                            }
                        }
                        if (formatPillText != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.surfaceContainer)
                                    .hairlineBorder(colors.borderStructural, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = formatPillText,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = colors.primarySignal
                                )
                            }
                        }
                    }

                    // Personalization Controls: Star Rating trigger & Favorite Button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { showRatingDialog = true },
                            enabled = currentTrack != null,
                            modifier = Modifier
                                .touchTarget48()
                                .semantics {
                                    contentDescription = if (rating != null) {
                                        "Rating: $rating of 5 stars"
                                    } else {
                                        "Rate track"
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = if (rating != null) HarmoniaIcons.Star else HarmoniaIcons.StarBorder,
                                    contentDescription = null,
                                    tint = if (rating != null) colors.statusWarning else colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                if (rating != null) {
                                    Text(
                                        text = "$rating",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = colors.statusWarning
                                    )
                                }
                            }
                        }

                        FavoriteButton(
                            isFavorite = isFavorite,
                            onToggleFavorite = onToggleFavorite,
                            enabled = currentTrack != null
                        )
                    }
                }

                // Storage Disconnection / Playback Error Banner
                if (playbackState.errorMessage != null) {
                    val errorMsg = playbackState.errorMessage
                    val isStorageDisconnected = errorMsg == PlaybackState.STORAGE_DISCONNECTED_MESSAGE
                    val errorCardShape = RoundedCornerShape(8.dp)
                    Spacer(modifier = Modifier.height(spacing.medium))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(errorCardShape)
                            .background(colors.surfaceContainer)
                            .hairlineBorder(color = if (isStorageDisconnected) colors.statusWarning else colors.statusError, shape = errorCardShape)
                            .semantics {
                                contentDescription = if (isStorageDisconnected) {
                                    PlaybackState.STORAGE_DISCONNECTED_MESSAGE
                                } else {
                                    errorMsg
                                }
                            }
                            .padding(horizontal = spacing.medium, vertical = spacing.small),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = HarmoniaIcons.Settings,
                            contentDescription = null,
                            tint = if (isStorageDisconnected) colors.statusWarning else colors.statusError,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.small))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isStorageDisconnected) "Storage Disconnected" else "Playback Error",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = colors.textPrimary
                            )
                            Text(
                                text = if (isStorageDisconnected) {
                                    "The storage volume for this track is unavailable. Reconnect storage to resume playback."
                                } else {
                                    errorMsg
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(spacing.standard))

            // Seek Progress Bar (always accessible)
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
                modifier = Modifier
                    .fillMaxWidth()
                    .touchTarget48()
                    .semantics {
                        contentDescription = "Playback seek slider"
                    },
                colors = SliderDefaults.colors(
                    thumbColor = colors.primarySignal,
                    activeTrackColor = colors.primarySignal,
                    inactiveTrackColor = colors.surfaceContainer
                )
            )

            // Time indicators (Tabular alignment)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(currentPositionMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
                Text(
                    text = formatTime(totalDurationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary
                )
            }

            Spacer(modifier = Modifier.height(spacing.large))

            // Full Transport Controls (all with strict 48dp minimum bounds)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Shuffle Toggle
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

                // Previous Track
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier.touchTarget48()
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.SkipPrevious,
                        contentDescription = "Previous Track",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play/Pause Big Button (64dp circular focal point in electric blue)
                Box(
                    modifier = Modifier
                        .size(64.dp)
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
                        tint = colors.backgroundRoot,
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next Track
                IconButton(
                    onClick = onNext,
                    modifier = Modifier.touchTarget48()
                ) {
                    Icon(
                        imageVector = HarmoniaIcons.SkipNext,
                        contentDescription = "Next Track",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Repeat Mode Toggle
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
        }
    }
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
