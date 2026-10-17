package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent representation of a user star rating (1 to 5) for a track.
 * Row presence indicates a rated track.
 * Unrated/cleared tracks have no row.
 * Enforces integer 1..5 scale constraint.
 * Kept separate from canonical track metadata per ADR-002 and ADR-021.
 */
@Entity(
    tableName = "mobile_ratings",
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
        Index(value = ["rating", "client_updated_at"])
    ]
)
data class RatingEntity(
    @PrimaryKey
    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "rating")
    val rating: Int,

    @ColumnInfo(name = "client_updated_at")
    val clientUpdatedAt: Long
) {
    init {
        require(rating in 1..5) { "Rating must be between 1 and 5 stars inclusive, got: $rating" }
    }
}
