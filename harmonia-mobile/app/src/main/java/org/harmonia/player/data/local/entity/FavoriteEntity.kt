package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent representation of an active user favorite for a track.
 * Row presence indicates an active favorite.
 * Deletion represents unfavoriting.
 * Kept separate from canonical track metadata per ADR-002 and ADR-021.
 */
@Entity(
    tableName = "mobile_favorites",
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["track_id"]),
        Index(value = ["client_updated_at"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "client_updated_at")
    val clientUpdatedAt: Long
)
