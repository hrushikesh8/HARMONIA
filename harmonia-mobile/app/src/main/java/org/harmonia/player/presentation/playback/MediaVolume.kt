package org.harmonia.player.presentation.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * The phone's media volume (STREAM_MUSIC), the same volume the hardware buttons change.
 * [fraction] is 0..1 and stays in sync when the user presses the volume keys.
 */
@Stable
class MediaVolume internal constructor(
    private val audioManager: AudioManager?,
    private val minLevel: Int,
    private val maxLevel: Int,
    initialLevel: Int
) {
    var level by mutableIntStateOf(initialLevel)
        internal set

    val fraction: Float
        get() = if (maxLevel <= minLevel) 0f else (level - minLevel).toFloat() / (maxLevel - minLevel)

    val isAvailable: Boolean get() = audioManager != null && maxLevel > minLevel

    fun setFraction(value: Float) {
        val manager = audioManager ?: return
        val target = levelForFraction(value, minLevel, maxLevel)
        if (target == level) return
        try {
            manager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            level = target
        } catch (_: SecurityException) {
            // Volume changes can be blocked (e.g. Do Not Disturb policy); keep the reported level.
        }
    }

    internal fun refresh() {
        audioManager?.let { level = it.getStreamVolume(AudioManager.STREAM_MUSIC) }
    }

    companion object {
        /** Maps a 0..1 slider value to the nearest whole volume step. */
        internal fun levelForFraction(value: Float, minLevel: Int, maxLevel: Int): Int =
            (minLevel + value.coerceIn(0f, 1f) * (maxLevel - minLevel)).roundToInt()
    }
}

private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"

@Composable
fun rememberMediaVolume(): MediaVolume {
    val context = LocalContext.current
    val volume = remember(context) {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        val min = if (manager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            manager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
        val max = manager?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 0
        MediaVolume(manager, min, max, manager?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0)
    }
    DisposableEffect(context, volume) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) = volume.refresh()
        }
        // Sent by the system when any stream volume changes (hardware keys, other apps).
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(VOLUME_CHANGED_ACTION),
            ContextCompat.RECEIVER_EXPORTED
        )
        volume.refresh()
        onDispose { context.unregisterReceiver(receiver) }
    }
    return volume
}
