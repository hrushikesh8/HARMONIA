package org.harmonia.player.domain.model.scanner

/**
 * Scan cycle lifecycle states.
 */
enum class ScanProgressStatus {
    IDLE,
    DISCOVERING,
    PROCESSING,
    COMPLETED,
    PARTIAL,
    FAILED,
    PERMISSION_REQUIRED
}

/**
 * Progress and diagnostic reporting model for a media source scan cycle.
 */
data class ScanProgress(
    val sourceId: String,
    val status: ScanProgressStatus = ScanProgressStatus.IDLE,
    val discoveredCount: Int = 0,
    val processedCount: Int = 0,
    val importedCount: Int = 0,
    val updatedCount: Int = 0,
    val unchangedCount: Int = 0,
    val movedCount: Int = 0,
    val unavailableCount: Int = 0,
    val failedCount: Int = 0,
    val errorMessage: String? = null
)
