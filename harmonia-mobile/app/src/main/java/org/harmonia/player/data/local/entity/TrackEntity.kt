package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mobile_tracks",
    foreignKeys = [
        ForeignKey(
            entity = AlbumEntity::class,
            parentColumns = ["id"],
            childColumns = ["album_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = ArtistEntity::class,
            parentColumns = ["id"],
            childColumns = ["artist_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["album_id"]),
        Index(value = ["artist_id"]),
        Index(value = ["title"])
    ]
)
data class TrackEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "album_id")
    val albumId: String? = null,

    @ColumnInfo(name = "artist_id")
    val artistId: String? = null,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "track_number")
    val trackNumber: Int? = null,

    @ColumnInfo(name = "disc_number", defaultValue = "1")
    val discNumber: Int = 1,

    @ColumnInfo(name = "duration_sec")
    val durationSec: Double,

    @ColumnInfo(name = "year")
    val year: Int? = null,

    @ColumnInfo(name = "genre")
    val genre: String? = null,

    @ColumnInfo(name = "mbid")
    val mbid: String? = null,

    @ColumnInfo(name = "integrated_lufs")
    val integratedLufs: Double? = null,

    @ColumnInfo(name = "true_peak_dbtp")
    val truePeakDbtp: Double? = null,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long
)
