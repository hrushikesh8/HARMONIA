package org.harmonia.player.domain.model.scanner

/**
 * Scanning strategy mode for storage sources.
 */
enum class ScanMode {
    /**
     * Incremental scan: checks generation / timestamp counters and only ingests
     * new or modified files. Does not flag missing files.
     */
    INCREMENTAL,

    /**
     * Full reconciliation: enumerates complete source inventory. Ingests new/modified
     * files and marks missing files as MISSING without deleting records.
     */
    FULL_RECONCILIATION
}
