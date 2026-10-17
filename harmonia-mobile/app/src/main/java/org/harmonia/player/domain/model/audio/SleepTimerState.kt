package org.harmonia.player.domain.model.audio

/**
 * Operating mode for the sleep timer.
 */
enum class SleepTimerMode {
    DURATION,
    END_OF_TRACK
}

/**
 * Standard sleep timer duration presets.
 */
enum class SleepTimerPreset(val durationMs: Long?, val label: String) {
    MINUTES_15(15 * 60 * 1000L, "15 minutes"),
    MINUTES_30(30 * 60 * 1000L, "30 minutes"),
    MINUTES_45(45 * 60 * 1000L, "45 minutes"),
    MINUTES_60(60 * 60 * 1000L, "60 minutes"),
    END_OF_TRACK(null, "End of track")
}

/**
 * Monotonic time source abstraction to decouple sleep timer countdowns from wall-clock time
 * and enable deterministic time-advancement in unit tests.
 */
interface MonotonicClock {
    fun elapsedRealtimeMs(): Long

    companion object {
        val DEFAULT: MonotonicClock = object : MonotonicClock {
            override fun elapsedRealtimeMs(): Long {
                return try {
                    android.os.SystemClock.elapsedRealtime()
                } catch (_: Throwable) {
                    System.nanoTime() / 1_000_000L
                }
            }
        }
    }
}

/**
 * Observable state model representing the active or inactive sleep timer.
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingDurationMs: Long = 0L,
    val targetDurationMs: Long = 0L,
    val mode: SleepTimerMode = SleepTimerMode.DURATION,
    val baselineVolume: Float = 1.0f
)
