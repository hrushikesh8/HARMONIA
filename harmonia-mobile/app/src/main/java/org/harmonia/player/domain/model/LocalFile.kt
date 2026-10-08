package org.harmonia.player.domain.model

/**
 * Storage category for local media files on Android.
 */
enum class StorageType {
    INTERNAL_SYNC,
    SAF_IMPORTED,
    EXTERNAL_SD
}

/**
 * Pure domain representation of a physical bitstream file on the mobile device.
 * Decoupled from Track per ADR-025.
 */
data class LocalFile(
    val id: String,
    val trackId: String,
    val storageType: StorageType,
    val fileUri: String,
    val fileSize: Long,
    val fileHash: String,
    val format: String,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val channels: Int? = null,
    val isLossless: Boolean = false,
    val isVerified: Boolean = false,
    val verifiedAt: Long = 0L,
    val addedAt: Long = System.currentTimeMillis()
)
