package org.harmonia.player.domain.model.storage.permission

/**
 * Platform-neutral representation of storage permission state.
 */
enum class StoragePermissionState {
    GRANTED,
    DENIED,
    SETTINGS_REQUIRED
}

/**
 * Domain port for checking audio media access permissions without coupling
 * to Android Activity or framework APIs.
 */
interface StoragePermissionChecker {
    fun getPermissionState(): StoragePermissionState
    fun canScanAudioMedia(): Boolean
}
