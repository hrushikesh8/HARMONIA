package org.harmonia.player.presentation.carmode

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Track
import org.harmonia.player.presentation.components.HarmoniaArtwork
import org.harmonia.player.presentation.personalization.PersonalizationViewModel
import org.harmonia.player.presentation.playback.PlaybackViewModel
import org.harmonia.player.presentation.util.TrackDisplayUtils
import java.util.Locale

/**
 * Car Mode: big, glanceable controls. Portrait stacks artwork over the controls; landscape (dash
 * mounts) puts the artwork on the left and everything else on the right. Includes a thick
 * progress bar you can drag and a large favorite button.
 */
@Composable
fun CarModeScreen(
    playbackViewModel: PlaybackViewModel,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    personalizationViewModel: PersonalizationViewModel = viewModel()
) {
    val playerState by playbackViewModel.uiState.collectAsState()
    val personalization by personalizationViewModel.uiState.collectAsState()
    val colors = HarmoniaThemeTokens.colors
    val spacing = HarmoniaThemeTokens.spacing

    val track = playerState.currentTrack
    val playback = playerState.playbackState
    val controls = CarControls(
        isPlaying = playback.isPlaying,
        onPrevious = { playbackViewModel.previous() },
        onPlayPause = { if (playback.isPlaying) playbackViewModel.pause() else playbackViewModel.play() },
        onNext = { playbackViewModel.next() }
    )
    val isFavorite = personalization.isFavorite
    val onToggleFavorite = { personalizationViewModel.toggleFavorite() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.backgroundRoot)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(spacing.standard)
    ) {
        val availableWidth = maxWidth
        val availableHeight = maxHeight
        if (availableWidth > availableHeight) {
            // Landscape: artwork left, details and controls right.
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(spacing.large)) {
                CarArtwork(track, Modifier.fillMaxHeight().aspectRatio(1f), availableHeight)
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    CarHeader(onExit = onExit, compact = true)
                    Spacer(modifier = Modifier.height(spacing.small))
                    CarTitleRow(track, isFavorite, onToggleFavorite, titleSizeLarge = false)
                    CarProgress(playback, onSeek = playbackViewModel::seekTo)
                    Spacer(modifier = Modifier.weight(1f))
                    CarControlsRow(controls, sideSize = 64.dp, mainSize = 80.dp)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                CarHeader(onExit = onExit, compact = false)
                Spacer(modifier = Modifier.height(spacing.large))
                val artSide = minOf(availableWidth, availableHeight * 0.42f)
                CarArtwork(track, Modifier.size(artSide), artSide)
                Spacer(modifier = Modifier.height(spacing.large))
                CarTitleRow(track, isFavorite, onToggleFavorite, titleSizeLarge = true)
                CarProgress(playback, onSeek = playbackViewModel::seekTo)
                Spacer(modifier = Modifier.weight(1f))
                CarControlsRow(controls, sideSize = 72.dp, mainSize = 96.dp)
                Spacer(modifier = Modifier.height(spacing.medium))
            }
        }
    }
}

private class CarControls(
    val isPlaying: Boolean,
    val onPrevious: () -> Unit,
    val onPlayPause: () -> Unit,
    val onNext: () -> Unit
)

@Composable
private fun CarHeader(onExit: () -> Unit, compact: Boolean) {
    val colors = HarmoniaThemeTokens.colors
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = HarmoniaIcons.DirectionsCar,
            contentDescription = null,
            tint = colors.primarySignal,
            modifier = Modifier.size(if (compact) 22.dp else 28.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Car Mode",
            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleLarge,
            color = if (compact) colors.textSecondary else colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Exit",
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(colors.surfaceContainer)
                .clickable(onClick = onExit)
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .semantics {
                    role = Role.Button
                    contentDescription = "Exit Car Mode"
                }
        )
    }
}

@Composable
private fun CarArtwork(track: Track?, modifier: Modifier, size: Dp) {
    val colors = HarmoniaThemeTokens.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
    ) {
        HarmoniaArtwork(
            artworkHash = track?.artworkHash,
            modifier = Modifier.fillMaxSize(),
            size = size,
            cornerRadius = 16.dp,
            contentDescription = track?.let { "${it.title} artwork" }
        )
    }
}

@Composable
private fun CarTitleRow(track: Track?, isFavorite: Boolean, onToggleFavorite: () -> Unit, titleSizeLarge: Boolean) {
    val colors = HarmoniaThemeTokens.colors
    val title = track?.let { TrackDisplayUtils.formatDisplayTitle(it) } ?: "Nothing playing"
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = if (titleSizeLarge) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track?.artistName ?: "Pick something to play",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (track != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceContainer)
                    .clickable(onClick = onToggleFavorite)
                    .semantics {
                        role = Role.Button
                        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites"
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFavorite) HarmoniaIcons.Favorite else HarmoniaIcons.FavoriteBorder,
                    contentDescription = null,
                    tint = colors.primarySignal,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

/** Thick progress bar with a big handle; drag to seek. Shows elapsed and remaining time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarProgress(playback: PlaybackState, onSeek: (Long) -> Unit) {
    val colors = HarmoniaThemeTokens.colors
    val duration = playback.durationMs
    var isDragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val fraction = when {
        duration <= 0 -> 0f
        isDragging -> dragValue
        else -> (playback.currentPositionMs.toFloat() / duration).coerceIn(0f, 1f)
    }
    val position = (fraction * duration).toLong()
    val sliderColors = SliderDefaults.colors(
        thumbColor = colors.textPrimary,
        activeTrackColor = colors.primarySignal,
        inactiveTrackColor = colors.surfaceContainer
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Slider(
            value = fraction,
            onValueChange = {
                isDragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                isDragging = false
                if (duration > 0) onSeek((dragValue * duration).toLong())
            },
            enabled = duration > 0,
            colors = sliderColors,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.textPrimary)
                )
            },
            track = { state ->
                SliderDefaults.Track(
                    sliderState = state,
                    colors = sliderColors,
                    modifier = Modifier.height(8.dp),
                    thumbTrackGapSize = 0.dp,
                    drawStopIndicator = null
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = "Song progress" }
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = formatCarTime(position),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "-" + formatCarTime((duration - position).coerceAtLeast(0)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun CarControlsRow(controls: CarControls, sideSize: Dp, mainSize: Dp) {
    val colors = HarmoniaThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CarRoundButton(HarmoniaIcons.SkipPrevious, "Previous Track", sideSize, colors.surfaceContainer, colors.textPrimary, controls.onPrevious)
        CarRoundButton(
            icon = if (controls.isPlaying) HarmoniaIcons.Pause else HarmoniaIcons.Play,
            description = if (controls.isPlaying) "Pause" else "Play",
            size = mainSize,
            background = colors.primarySignal,
            tint = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated,
            onClick = controls.onPlayPause
        )
        CarRoundButton(HarmoniaIcons.SkipNext, "Next Track", sideSize, colors.surfaceContainer, colors.textPrimary, controls.onNext)
    }
}

@Composable
private fun CarRoundButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    background: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = description
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.46f))
    }
}

internal fun formatCarTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return String.format(Locale.US, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}
