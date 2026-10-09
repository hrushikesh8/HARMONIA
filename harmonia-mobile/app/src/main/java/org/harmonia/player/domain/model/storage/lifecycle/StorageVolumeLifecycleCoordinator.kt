package org.harmonia.player.domain.model.storage.lifecycle

import kotlinx.coroutines.flow.StateFlow
import org.harmonia.player.domain.model.storage.StorageSource

/**
 * Coordinates physical storage volume hotplug and SAF tree permission lifecycles.
 * Maintains runtime availability without corrupting user configuration or file records.
 */
interface StorageVolumeLifecycleCoordinator {
    val runtimeStates: StateFlow<Map<String, SourceRuntimeState>>

    fun isSourceAvailable(sourceId: String): Boolean

    fun onVolumeMounted(volumeId: String)

    fun onVolumeUnmounted(volumeId: String)

    fun onSafPermissionRevoked(treeUri: String)

    fun onSafPermissionGranted(treeUri: String)

    suspend fun revalidateAllSources(): Map<String, SourceRuntimeState>

    suspend fun revalidateSource(source: StorageSource): SourceRuntimeState

    fun startMonitoring()

    fun stopMonitoring()
}
