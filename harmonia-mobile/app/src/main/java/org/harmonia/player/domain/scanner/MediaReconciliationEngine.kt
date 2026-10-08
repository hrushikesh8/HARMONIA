package org.harmonia.player.domain.scanner

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgress
import org.harmonia.player.domain.model.storage.StorageSource

/**
 * Domain engine orchestrating media source discovery, freshness inspection,
 * metadata extraction, content-hash identity reconciliation, and checkpoint advancement.
 */
interface MediaReconciliationEngine {

    /**
     * Scans and reconciles the given storage source into the local library.
     * Emits progress updates throughout discovery and ingestion.
     */
    fun scanSource(
        source: StorageSource,
        mode: ScanMode = ScanMode.INCREMENTAL
    ): Flow<ScanProgress>
}
