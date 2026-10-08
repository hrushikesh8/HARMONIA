package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.harmonia.player.domain.model.PlaylistItem

@Entity(
    tableName = "mobile_playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlist_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["track_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["playlist_id"]),
        Index(value = ["track_id"]),
        Index(value = ["playlist_id", "position"], unique = true)
    ]
)
data class PlaylistItemEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "playlist_id")
    val playlistId: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "position")
    val position: Int,

    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): PlaylistItem {
        return PlaylistItem(
            id = id,
            playlistId = playlistId,
            trackId = trackId,
            position = position,
            addedAt = addedAt
        )
    }

    companion object {
        fun fromDomain(domain: PlaylistItem): PlaylistItemEntity {
            return PlaylistItemEntity(
                id = domain.id,
                playlistId = domain.playlistId,
                trackId = domain.trackId,
                position = domain.position,
                addedAt = domain.addedAt
            )
        }
    }
}
