package org.harmonia.player.domain.scanner

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates concurrent media reconciliation scans across ViewModels and background workers.
 * Prevents overlapping reconciliation scans of the same storage source ID while allowing
 * distinct storage sources to scan concurrently.
 */
class SourceScanCoordinator {

    private val sourceLocks = ConcurrentHashMap<String, Mutex>()

    fun getLockForSource(sourceId: String): Mutex =
        sourceLocks.computeIfAbsent(sourceId) { Mutex() }

    fun isSourceScanning(sourceId: String): Boolean =
        sourceLocks[sourceId]?.isLocked == true

    suspend fun <T> withSourceLock(sourceId: String, action: suspend () -> T): T {
        return getLockForSource(sourceId).withLock {
            action()
        }
    }
}
