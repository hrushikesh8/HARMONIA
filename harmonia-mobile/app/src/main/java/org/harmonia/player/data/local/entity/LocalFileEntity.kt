package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mobile_local_files",
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
        Index(value = ["file_uri"], unique = true),
        Index(value = ["source_id"])
    ]
)
data class LocalFileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "track_id")
    val trackId: String,

    @ColumnInfo(name = "source_id")
    val sourceId: String? = null,

    @ColumnInfo(name = "storage_type")
    val storageType: String, // 'INTERNAL_SYNC', 'SAF_IMPORTED', 'EXTERNAL_SD'

    @ColumnInfo(name = "file_uri")
    val fileUri: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long,

    @ColumnInfo(name = "file_hash")
    val fileHash: String,

    @ColumnInfo(name = "format")
    val format: String,

    @ColumnInfo(name = "bitrate")
    val bitrate: Int? = null,

    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int? = null,

    @ColumnInfo(name = "bit_depth")
    val bitDepth: Int? = null,

    @ColumnInfo(name = "channels")
    val channels: Int? = null,

    @ColumnInfo(name = "is_lossless", defaultValue = "0")
    val isLossless: Boolean = false,

    @ColumnInfo(name = "availability_status")
    val availabilityStatus: String, // 'AVAILABLE', 'MISSING', 'CORRUPT', 'UNVERIFIED'

    @ColumnInfo(name = "verified_at")
    val verifiedAt: Long = 0L,

    @ColumnInfo(name = "added_at")
    val addedAt: Long
)
