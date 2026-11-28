package org.harmonia.player.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.harmonia.player.design.theme.HarmoniaThemeTokens
import java.text.Normalizer

/** Letters shown on the A–Z bar; "#" covers numbers and symbols. */
val ALPHABET_INDEX: List<Char> = ('A'..'Z').toList() + '#'

/** The index letter for a title: its first letter (accents removed), or '#' for anything else. */
fun indexLetterFor(text: String?): Char {
    // Must match how the list is sorted (full title), so no "The …" special case.
    val trimmed = text?.trim().orEmpty()
    val first = trimmed.firstOrNull() ?: return '#'
    val plain = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
        .firstOrNull()?.uppercaseChar() ?: return '#'
    return if (plain in 'A'..'Z') plain else '#'
}

/**
 * For a list already sorted by [keys], the first position of each letter. Numbers and symbols sort
 * before letters in most sorts, so '#' maps to wherever its first item is.
 */
fun alphabetPositions(keys: List<String?>): Map<Char, Int> {
    val positions = LinkedHashMap<Char, Int>()
    keys.forEachIndexed { index, key ->
        positions.putIfAbsent(indexLetterFor(key), index)
    }
    return positions
}

/**
 * Where to jump for [letter]: its first item, or the first item of the next letter that has one
 * (so tapping an empty letter still moves you to the right area), or the last letter before it.
 */
fun positionForLetter(letter: Char, positions: Map<Char, Int>): Int? {
    positions[letter]?.let { return it }
    val order = ALPHABET_INDEX
    val at = order.indexOf(letter).takeIf { it >= 0 } ?: return null
    for (i in at + 1 until order.size) positions[order[i]]?.let { return it }
    for (i in at - 1 downTo 0) positions[order[i]]?.let { return it }
    return null
}

/** Show the bar only for alphabetical sorts with items under at least two letters. */
fun shouldShowAlphabetBar(positions: Map<Char, Int>): Boolean = positions.size >= 2

/**
 * The A–Z strip on the right edge of a long list (like Apple Music). Tap or drag over a letter to
 * jump there; a bubble shows the letter under your finger.
 */
@Composable
fun AlphabetScroller(
    positions: Map<Char, Int>,
    onJumpTo: suspend (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HarmoniaThemeTokens.colors
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var heightPx by remember { mutableStateOf(1) }
    var activeLetter by remember { mutableStateOf<Char?>(null) }
    var activeY by remember { mutableStateOf(0f) }

    fun select(y: Float) {
        val slot = (y / heightPx * ALPHABET_INDEX.size).toInt().coerceIn(0, ALPHABET_INDEX.lastIndex)
        val letter = ALPHABET_INDEX[slot]
        activeY = y.coerceIn(0f, heightPx.toFloat())
        if (letter != activeLetter) {
            activeLetter = letter
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            positionForLetter(letter, positions)?.let { target -> scope.launch { onJumpTo(target) } }
        }
    }

    Box(modifier = modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(22.dp)
                .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
                .pointerInput(positions) {
                    detectTapGestures(
                        onPress = { offset ->
                            select(offset.y)
                            tryAwaitRelease()
                            activeLetter = null
                        }
                    )
                }
                .pointerInput(positions) {
                    detectVerticalDragGestures(
                        onDragStart = { select(it.y) },
                        onDragEnd = { activeLetter = null },
                        onDragCancel = { activeLetter = null },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            select(change.position.y)
                        }
                    )
                }
                .semantics {
                    contentDescription = "Jump to letter"
                    customActions = positions.keys.map { letter ->
                        CustomAccessibilityAction("Jump to $letter") {
                            positionForLetter(letter, positions)?.let { target -> scope.launch { onJumpTo(target) } }
                            true
                        }
                    }
                },
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ALPHABET_INDEX.forEach { letter ->
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                    color = if (letter in positions) colors.primarySignal else colors.textDisabled,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Bubble showing the letter under the finger.
        activeLetter?.let { letter ->
            Box(
                modifier = Modifier
                    .offset { IntOffset(x = -64.dp.roundToPx(), y = (activeY - 24.dp.toPx()).toInt()) }
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.primarySignal),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = letter.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (colors.isDark) colors.backgroundRoot else colors.surfaceElevated,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
