package org.harmonia.player.domain.model.scanner

/**
 * Deterministic reconciliation state for an individual media item.
 */
enum class ReconciliationItemState {
    NEW,
    CHANGED,
    UNCHANGED,
    MOVED,
    MISSING,
    FAILED
}
