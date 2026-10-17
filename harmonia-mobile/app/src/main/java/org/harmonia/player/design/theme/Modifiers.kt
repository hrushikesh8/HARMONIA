package org.harmonia.player.design.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Standard minimum touch target size according to Android Accessibility Guidelines (WCAG 2.5.5 / 2.5.8).
 */
val MinimumInteractiveTouchTarget: Dp = 48.dp

/**
 * Enforces a minimum interactive bounding box of 48dp × 48dp on any clickable or interactive component.
 */
fun Modifier.minimumTouchTarget(size: Dp = MinimumInteractiveTouchTarget): Modifier = this.then(
    Modifier.sizeIn(minWidth = size, minHeight = size)
)

/**
 * Convenience modifier for defaultMinSize with 48dp bounds.
 */
fun Modifier.touchTarget48(): Modifier = this.then(
    Modifier.defaultMinSize(minWidth = MinimumInteractiveTouchTarget, minHeight = MinimumInteractiveTouchTarget)
)

/**
 * Harmonia 1dp structural hairline border modifier.
 */
fun Modifier.hairlineBorder(
    color: Color = BorderSlate,
    shape: Shape = HarmoniaShapes.medium,
    width: Dp = 1.dp
): Modifier = this.then(
    Modifier.border(width = width, color = color, shape = shape)
)
