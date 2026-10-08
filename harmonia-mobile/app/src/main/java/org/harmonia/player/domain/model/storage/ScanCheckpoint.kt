package org.harmonia.player.domain.model.storage

/**
 * Status of a media source scan cycle.
 */
enum class ScanStatus {
    IDLE,
    SCANNING,
    COMPLETED,
    FAILED
}

/**
 * Pure domain model representing the persisted progress and checkpoint of a storage source.
 * Used for incremental change detection via generation / timestamp counters.
 */
data class ScanCheckpoint(
    val sourceId: String,
    val volumeName: String,
    val lastGeneration: Long = 0L,
    val lastScanTimestamp: Long = 0L,
    val scannedFileCount: Int = 0,
    val status: ScanStatus = ScanStatus.IDLE
)
