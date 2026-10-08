package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mobile_albums",
    foreignKeys = [
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["artist_id"])
    ]
)
data class AlbumEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "artist_id")
    val artistId: String? = null,

    @ColumnInfo(name = "year")
    val year: Int? = null,

    @ColumnInfo(name = "total_tracks")
    val totalTracks: Int? = null,

    @ColumnInfo(name = "total_discs")
    val totalDiscs: Int? = null,

    @ColumnInfo(name = "mbid")
    val mbid: String? = null,

    @ColumnInfo(name = "artwork_hash")
    val artworkHash: String? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
