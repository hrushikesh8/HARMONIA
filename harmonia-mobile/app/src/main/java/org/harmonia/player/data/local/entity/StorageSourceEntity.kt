package org.harmonia.player.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persistent Room representation of a storage source.
 */
@Entity(tableName = "mobile_storage_sources")
data class StorageSourceEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,

    @ColumnInfo(name = "type")
    val type: String, // 'MEDIA_STORE_PRIMARY', 'MEDIA_STORE_VOLUME', 'SAF_DOCUMENT_TREE', 'INTERNAL_SYNC'

    @ColumnInfo(name = "uri")
    val uri: String,

    @ColumnInfo(name = "display_name")
    val displayName: String,

    @ColumnInfo(name = "is_removable")
    val isRemovable: Boolean,

    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,

    @ColumnInfo(name = "last_scanned_at")
    val lastScannedAt: Long = 0L
)
