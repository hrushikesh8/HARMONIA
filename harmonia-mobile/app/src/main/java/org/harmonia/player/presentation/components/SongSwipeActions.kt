package org.harmonia.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.harmonia.player.design.theme.HarmoniaIcons
import org.harmonia.player.design.theme.HarmoniaThemeTokens

/** Which swipe does what in song lists (agreed with Sagar and Hrushikesh). */
enum class SongSwipe {
    /** Right to left: the song plays after the current one. Every song list. */
    PLAY_NEXT,

    /** Left to right: the song is removed (with Undo). Playlists only. */
    REMOVE;

    companion object {
        /** Maps the direction a row was swiped to its action. */
        fun of(direction: SwipeToDismissBoxValue): SongSwipe? = when (direction) {
            SwipeToDismissBoxValue.EndToStart -> PLAY_NEXT
            SwipeToDismissBoxValue.StartToEnd -> REMOVE
            SwipeToDismissBoxValue.Settled -> null
        }
    }
}

/**
 * Wraps a song row with swipe actions:
 * - swipe **right to left** → [onPlayNext]; the row springs back and stays in the list;
 * - swipe **left to right** → [onRemove] (only offered when it isn't null, i.e. in playlists).
 *
 * A coloured hint shows behind the row while swiping, the phone ticks when the action fires, and
 * both actions are also offered to screen readers.
 */
@Composable
fun SongSwipeActions(
    onPlayNext: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onRemove: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    if (onPlayNext == null && onRemove == null) {
        content()
        return
    }
    val colors = HarmoniaThemeTokens.colors
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val playNext by rememberUpdatedState(onPlayNext)
    val remove by rememberUpdatedState(onRemove)
    val state = rememberSwipeToDismissBoxState(positionalThreshold = { width -> width * 0.3f })

    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOfNotNull(
                playNext?.let { action -> CustomAccessibilityAction("Play next") { action(); true } },
                remove?.let { action -> CustomAccessibilityAction("Remove from playlist") { action(); true } }
            )
        },
        enableDismissFromStartToEnd = onRemove != null,
        enableDismissFromEndToStart = onPlayNext != null,
        gesturesEnabled = enabled,
        onDismiss = { direction ->
            when (SongSwipe.of(direction)) {
                SongSwipe.PLAY_NEXT -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    playNext?.invoke()
                    // The song stays in the list: slide the row back.
                    scope.launch { state.reset() }
                }
                SongSwipe.REMOVE -> {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    remove?.invoke()
                }
                null -> Unit
            }
        },
        backgroundContent = {
            when (SongSwipe.of(state.dismissDirection)) {
                SongSwipe.PLAY_NEXT -> SwipeHint(
                    label = "Play next",
                    icon = HarmoniaIcons.PlayNext,
                    tint = colors.primarySignal,
                    background = colors.primarySignal.copy(alpha = 0.18f),
                    alignEnd = true
                )
                SongSwipe.REMOVE -> SwipeHint(
                    label = "Remove",
                    icon = HarmoniaIcons.Delete,
                    tint = colors.statusError,
                    background = colors.statusError.copy(alpha = 0.18f),
                    alignEnd = false
                )
                null -> Unit
            }
        }
    ) {
        content()
    }
}

@Composable
private fun SwipeHint(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    background: androidx.compose.ui.graphics.Color,
    alignEnd: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start
    ) {
        if (alignEnd) {
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        } else {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}
