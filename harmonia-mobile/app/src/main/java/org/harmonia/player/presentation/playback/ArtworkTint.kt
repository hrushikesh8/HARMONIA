package org.harmonia.player.presentation.playback

import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.harmonia.player.data.local.ArtworkProvider
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/*
 * Now Playing background tinted from the album art (task 4.1).
 *
 * The cover is shrunk to 64px, its pixels are grouped by hue, and the most common colourful hue
 * wins (grey, near-black and near-white pixels are ignored so a white border or black background
 * doesn't decide the colour). That colour is then turned into a background that keeps text
 * readable: dark and muted in dark mode, pale in light mode.
 */

/** Hue 0-360, saturation and lightness 0-1. */
internal data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

internal fun argbToHsl(argb: Int): Hsl {
    val r = ((argb shr 16) and 0xFF) / 255f
    val g = ((argb shr 8) and 0xFF) / 255f
    val b = (argb and 0xFF) / 255f
    val maxC = max(r, max(g, b))
    val minC = min(r, min(g, b))
    val l = (maxC + minC) / 2f
    val d = maxC - minC
    if (d == 0f) return Hsl(0f, 0f, l)
    val s = d / (1f - abs(2f * l - 1f))
    val h = when (maxC) {
        r -> 60f * (((g - b) / d) % 6f)
        g -> 60f * ((b - r) / d + 2f)
        else -> 60f * ((r - g) / d + 4f)
    }
    return Hsl((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
}

internal fun hslToArgb(hsl: Hsl): Int {
    val c = (1f - abs(2f * hsl.lightness - 1f)) * hsl.saturation
    val x = c * (1f - abs((hsl.hue / 60f) % 2f - 1f))
    val m = hsl.lightness - c / 2f
    val (r, g, b) = when ((hsl.hue / 60f).toInt().coerceIn(0, 5)) {
        0 -> Triple(c, x, 0f)
        1 -> Triple(x, c, 0f)
        2 -> Triple(0f, c, x)
        3 -> Triple(0f, x, c)
        4 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    fun channel(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f + 0.5f).toInt()
    return (0xFF shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
}

private const val HUE_BUCKETS = 12

/**
 * The cover's main colour as HSL, or null when there is nothing colourful to go on (an all-grey
 * or black-and-white cover), in which case Now Playing keeps its plain background.
 */
internal fun dominantArtworkHsl(pixels: IntArray): Hsl? {
    val weight = FloatArray(HUE_BUCKETS)
    val hueX = FloatArray(HUE_BUCKETS)
    val hueY = FloatArray(HUE_BUCKETS)
    val satSum = FloatArray(HUE_BUCKETS)
    val count = IntArray(HUE_BUCKETS)
    var colourful = 0
    for (p in pixels) {
        if ((p ushr 24) < 128) continue // mostly transparent
        val hsl = argbToHsl(p)
        if (hsl.saturation < 0.18f || hsl.lightness < 0.08f || hsl.lightness > 0.92f) continue
        colourful++
        val bucket = ((hsl.hue / 360f) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
        // Vivid pixels count for more than dull ones.
        val w = hsl.saturation
        weight[bucket] += w
        val rad = Math.toRadians(hsl.hue.toDouble())
        hueX[bucket] += (Math.cos(rad) * w).toFloat()
        hueY[bucket] += (Math.sin(rad) * w).toFloat()
        satSum[bucket] += hsl.saturation
        count[bucket]++
    }
    // Ignore a few stray colourful pixels in an otherwise grey cover.
    if (colourful < max(8, pixels.size / 50)) return null
    val best = weight.indices.maxByOrNull { weight[it] } ?: return null
    if (count[best] == 0) return null
    val hue = ((Math.toDegrees(Math.atan2(hueY[best].toDouble(), hueX[best].toDouble())) + 360.0) % 360.0).toFloat()
    return Hsl(hue, satSum[best] / count[best], 0.5f)
}

/** A background colour from the cover's hue that keeps white (dark mode) or black (light mode) text readable. */
internal fun tintBackgroundArgb(dominant: Hsl, isDark: Boolean): Int =
    if (isDark) {
        hslToArgb(Hsl(dominant.hue, (dominant.saturation * 0.7f).coerceIn(0.25f, 0.55f), 0.22f))
    } else {
        hslToArgb(Hsl(dominant.hue, (dominant.saturation * 0.6f).coerceIn(0.2f, 0.45f), 0.88f))
    }

private const val TINT_SAMPLE_PX = 64

/** Computed colours per artwork, so going back to a song doesn't recompute it. */
private val tintCache = LruCache<String, Any>(64)
private val NO_TINT = Any()

/**
 * The background tint for [artworkHash], animated when the song changes; [fallback] when the song
 * has no art or the art has no colour.
 */
@Composable
internal fun rememberArtworkTint(artworkHash: String?, isDark: Boolean, fallback: Color): State<Color> {
    val context = LocalContext.current
    val provider = remember(context) { ArtworkProvider.getInstance(context) }
    val target = remember { mutableStateOf(fallback) }

    LaunchedEffect(artworkHash, isDark, fallback) {
        val key = artworkHash?.takeIf { it.isNotBlank() }
        if (key == null) {
            target.value = fallback
            return@LaunchedEffect
        }
        val cached = tintCache.get(key)
        val dominant = if (cached != null) {
            cached as? Hsl
        } else {
            withContext(Dispatchers.Default) {
                val bitmap = withContext(Dispatchers.IO) { provider.loadBitmap(key, TINT_SAMPLE_PX) }
                bitmap?.takeIf { !it.isRecycled }?.let { bmp ->
                    val pixels = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(pixels, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    dominantArtworkHsl(pixels)
                }
            }.also { tintCache.put(key, it ?: NO_TINT) }
        }
        target.value = dominant?.let { Color(tintBackgroundArgb(it, isDark)) } ?: fallback
    }

    return animateColorAsState(target.value, animationSpec = tween(durationMillis = 600), label = "artworkTint")
}
