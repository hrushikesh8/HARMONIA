package org.harmonia.player.presentation.components

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/**
 * How far to scroll this frame while an item is dragged: negative near the top edge, positive near
 * the bottom, zero in between. Speed grows the deeper the item goes into the [edge] zone.
 */
internal fun dragAutoScrollSpeed(
    itemTop: Float,
    itemBottom: Float,
    viewportStart: Float,
    viewportEnd: Float,
    edge: Float,
    maxSpeed: Float
): Float {
    if (edge <= 0f) return 0f
    val intoTop = viewportStart + edge - itemTop
    val intoBottom = itemBottom - (viewportEnd - edge)
    return when {
        intoTop > 0f && intoBottom > 0f -> 0f // item taller than the list: nowhere to go
        intoTop > 0f -> -maxSpeed * (intoTop / edge).coerceAtMost(1f)
        intoBottom > 0f -> maxSpeed * (intoBottom / edge).coerceAtMost(1f)
        else -> 0f
    }
}

/**
 * While [draggingKey] is set, scrolls [listState] when the dragged item reaches the top or bottom
 * edge, so a song can be moved further than one screen. [onScrolled] gets the distance scrolled;
 * pass it to the drag handler so the item stays under the finger and swaps with its neighbours.
 */
@Composable
fun DragAutoScroll(
    listState: LazyListState,
    draggingKey: Any?,
    dragOffset: () -> Float,
    onScrolled: (Float) -> Unit
) {
    val density = LocalDensity.current
    val edgePx = with(density) { 72.dp.toPx() }
    val maxStepPx = with(density) { 12.dp.toPx() }
    val currentOffset = rememberUpdatedState(dragOffset)
    val currentOnScrolled = rememberUpdatedState(onScrolled)

    LaunchedEffect(draggingKey, listState) {
        val key = draggingKey ?: return@LaunchedEffect
        while (isActive) {
            withFrameNanos { }
            val info = listState.layoutInfo
            val item = info.visibleItemsInfo.firstOrNull { it.key == key } ?: continue
            val top = item.offset + currentOffset.value()
            val step = dragAutoScrollSpeed(
                itemTop = top,
                itemBottom = top + item.size,
                viewportStart = info.viewportStartOffset.toFloat(),
                viewportEnd = info.viewportEndOffset.toFloat(),
                edge = edgePx,
                maxSpeed = maxStepPx
            )
            if (step != 0f) {
                val scrolled = listState.scrollBy(step)
                if (scrolled != 0f) currentOnScrolled.value(scrolled)
            }
        }
    }
}
