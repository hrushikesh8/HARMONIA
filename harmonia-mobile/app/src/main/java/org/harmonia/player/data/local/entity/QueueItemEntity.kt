package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mobile_queue_items",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"])
    ]
)
data class QueueItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "original_index")
    val originalIndex: Int? = null,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
