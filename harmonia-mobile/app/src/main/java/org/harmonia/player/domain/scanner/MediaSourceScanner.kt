package org.harmonia.player.domain.scanner

import org.harmonia.player.domain.model.scanner.DiscoveredMedia
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.storage.SourceType
import org.harmonia.player.domain.model.storage.StorageSource

/**
 * Provider-neutral boundary for discovering media candidate items on storage sources.
 */
interface MediaSourceScanner {
    val supportedSourceType: SourceType

    /**
     * Discovers audio media items within the given storage source.
     * Respects ScanMode (incremental vs full) where supported by the provider.
     */
    suspend fun discoverMedia(
        source: StorageSource,
        mode: ScanMode = ScanMode.INCREMENTAL,
        lastGeneration: Long = 0L
    ): List<DiscoveredMedia>
}
