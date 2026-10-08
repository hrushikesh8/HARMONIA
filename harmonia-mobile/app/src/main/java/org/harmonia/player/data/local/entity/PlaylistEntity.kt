package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.harmonia.player.domain.model.Playlist

@Entity(tableName = "mobile_playlists")
data class PlaylistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "artwork_hash")
    val artworkHash: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toDomain(trackCount: Int = 0): Playlist {
        return Playlist(
            id = id,
            name = name,
            description = description,
            artworkHash = artworkHash,
            createdAt = createdAt,
            updatedAt = updatedAt,
            trackCount = trackCount
        )
    }

    companion object {
        fun fromDomain(domain: Playlist): PlaylistEntity {
            return PlaylistEntity(
                id = domain.id,
                name = domain.name,
                description = domain.description,
                artworkHash = domain.artworkHash,
                createdAt = domain.createdAt,
                updatedAt = domain.updatedAt
            )
        }
    }
}
