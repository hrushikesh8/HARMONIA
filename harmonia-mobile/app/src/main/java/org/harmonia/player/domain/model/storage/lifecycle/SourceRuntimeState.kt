package org.harmonia.player.domain.model.storage.lifecycle

/**
 * Platform runtime status of a StorageSource.
 * Decoupled from user configuration (isEnabled) and file existence (availabilityStatus).
 */
data class SourceRuntimeState(
    val sourceId: String,
    val isAvailable: Boolean,
    val isMounted: Boolean,
    val hasPermission: Boolean,
    val lastVerifiedTimestamp: Long = System.currentTimeMillis()
)
