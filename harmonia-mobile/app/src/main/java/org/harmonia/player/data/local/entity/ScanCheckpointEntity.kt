package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persistent Room representation of a storage source scan checkpoint.
 * Linked to StorageSourceEntity with ON DELETE CASCADE.
 */
@Entity(
    tableName = "mobile_scan_checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = StorageSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["source_id"])
    ]
)
data class ScanCheckpointEntity(
    @PrimaryKey
    @ColumnInfo(name = "source_id")
    val sourceId: String,

    @ColumnInfo(name = "volume_name")
    val volumeName: String,

    @ColumnInfo(name = "last_generation")
    val lastGeneration: Long,

    @ColumnInfo(name = "last_scan_timestamp")
    val lastScanTimestamp: Long,

    @ColumnInfo(name = "scanned_file_count")
    val scannedFileCount: Int,

    @ColumnInfo(name = "status")
    val status: String // 'IDLE', 'SCANNING', 'COMPLETED', 'FAILED'
)
