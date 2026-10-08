package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mobile_playback_state")
data class PlaybackStateEntity(
    @PrimaryKey
    @ColumnInfo(name = "singleton_id")
    val singletonId: Int = 1,

    @ColumnInfo(name = "current_track_id")
    val currentTrackId: String? = null,

    @ColumnInfo(name = "current_position_ms", defaultValue = "0")
    val currentPositionMs: Long = 0L,

    @ColumnInfo(name = "is_playing", defaultValue = "0")
    val isPlaying: Boolean = false,

    @ColumnInfo(name = "shuffle_mode", defaultValue = "0")
    val shuffleMode: Boolean = false,

    @ColumnInfo(name = "repeat_mode", defaultValue = "0")
    val repeatMode: Int = 0, // 0: Off, 1: All, 2: One

    @ColumnInfo(name = "volume_scalar", defaultValue = "1.0")
    val volumeScalar: Float = 1.0f,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
