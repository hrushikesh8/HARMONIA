package org.harmonia.player.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.scanner.ScanProgressStatus
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.harmonia.player.domain.model.storage.permission.SafPermissionDelegate
import org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker
import org.harmonia.player.domain.repository.StorageSourceRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.harmonia.player.domain.scanner.MediaReconciliationEngine

data class StorageSourceItemUiState(
    val id: String,
    val displayName: String,
    val typeDescription: String,
    val isEnabled: Boolean,
    val runtimeStatusDescription: String,
    val isRuntimeAvailable: Boolean,
    val isRemovable: Boolean,
    val indexedTrackCount: Int = 0
)

data class SettingsUiState(
    val totalTracks: Int = 0,
    val offlineTracks: Int = 0,
    val appVersion: String = "0.1.0-alpha",
    val walModeEnabled: Boolean = true,
    val storageUsageEstimateMb: Double = 0.0,
    val storageSources: List<StorageSourceItemUiState> = emptyList(),
    val isScanning: Boolean = false,
    val scanStatusMessage: String? = null,
    val scanErrorMessage: String? = null
)

private data class CatalogStats(
    val totalTracks: Int,
    val offlineTracks: Int,
    val storageUsageEstimateMb: Double,
    val trackCountsBySourceId: Map<String, Int>
)

class SettingsViewModel(
    trackRepository: TrackRepository = ServiceLocator.getTrackRepository(),
    private val storageSourceRepository: StorageSourceRepository = ServiceLocator.getStorageSourceRepository(),
    private val storageVolumeLifecycleCoordinator: StorageVolumeLifecycleCoordinator? = null,
    private val mediaReconciliationEngine: MediaReconciliationEngine? = null,
    private val storagePermissionChecker: StoragePermissionChecker? = null,
    private val safPermissionDelegate: SafPermissionDelegate? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val coordinator: StorageVolumeLifecycleCoordinator?
        get() = storageVolumeLifecycleCoordinator ?: runCatching { ServiceLocator.getStorageVolumeLifecycleCoordinator() }.getOrNull()

    private val reconciliationEngine: MediaReconciliationEngine?
        get() = mediaReconciliationEngine ?: runCatching { ServiceLocator.getMediaReconciliationEngine() }.getOrNull()

    private val permissionChecker: StoragePermissionChecker?
        get() = storagePermissionChecker ?: runCatching { ServiceLocator.getStoragePermissionChecker() }.getOrNull()

    private val safDelegate: SafPermissionDelegate?
        get() = safPermissionDelegate ?: runCatching { ServiceLocator.getSafPermissionDelegate() }.getOrNull()

    private val _isScanning = MutableStateFlow(false)
    private val _scanStatusMessage = MutableStateFlow<String?>(null)
    private val _scanErrorMessage = MutableStateFlow<String?>(null)

    private var scanJob: Job? = null

    private val catalogStatsFlow = combine(
        trackRepository.observeAllTracks(),
        trackRepository.observeOfflineTracks()
    ) { allTracks, offlineTracks ->
        val totalSize = offlineTracks.sumOf { it.localFile?.fileSize ?: 0L }
        val countsBySource = allTracks
            .mapNotNull { it.localFile?.sourceId }
            .groupingBy { it }
            .eachCount()
        CatalogStats(
            totalTracks = allTracks.size,
            offlineTracks = offlineTracks.size,
            storageUsageEstimateMb = totalSize / (1024.0 * 1024.0),
            trackCountsBySourceId = countsBySource
        )
    }

    private val runtimeStatesFlow: StateFlow<Map<String, SourceRuntimeState>> =
        coordinator?.runtimeStates ?: MutableStateFlow(emptyMap())

    private val scanStateFlow = combine(
        _isScanning,
        _scanStatusMessage,
        _scanErrorMessage
    ) { isScanning, statusMsg, errorMsg ->
        Triple(isScanning, statusMsg, errorMsg)
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        catalogStatsFlow,
        storageSourceRepository.observeAllSources(),
        runtimeStatesFlow,
        scanStateFlow
    ) { catalogStats, sources, runtimeStates, (isScanning, statusMsg, errorMsg) ->
        val sourceItems = sources.map { source ->
            val runtime = runtimeStates[source.id]
            val trackCount = catalogStats.trackCountsBySourceId[source.id] ?: 0
            val typeDesc = when (source) {
                is StorageSource.MediaStorePrimary -> "Internal MediaStore"
                is StorageSource.MediaStoreVolume -> "Removable Volume"
                is StorageSource.SafDocumentTree -> "SAF Folder Tree"
                is StorageSource.InternalSyncStorage -> "App Synced Storage"
            }

            val (runtimeDesc, isAvailable) = when (source) {
                is StorageSource.MediaStorePrimary -> {
                    val hasPerm = runtime?.hasPermission ?: true
                    if (!hasPerm) "Permission required" to false else "Ready" to true
                }
                is StorageSource.MediaStoreVolume -> {
                    when {
                        runtime?.isMounted == false -> "Disconnected" to false
                        runtime?.hasPermission == false -> "Permission required" to false
                        else -> "Connected" to true
                    }
                }
                is StorageSource.SafDocumentTree -> {
                    when {
                        runtime?.hasPermission == false -> "Access revoked" to false
                        else -> "Accessible" to true
                    }
                }
                is StorageSource.InternalSyncStorage -> "Ready" to true
            }

            StorageSourceItemUiState(
                id = source.id,
                displayName = source.displayName,
                typeDescription = typeDesc,
                isEnabled = source.isAvailable, // maps to Room is_enabled
                runtimeStatusDescription = runtimeDesc,
                isRuntimeAvailable = isAvailable,
                isRemovable = source.isRemovable,
                indexedTrackCount = trackCount
            )
        }

        SettingsUiState(
            totalTracks = catalogStats.totalTracks,
            offlineTracks = catalogStats.offlineTracks,
            appVersion = "0.1.0-alpha",
            walModeEnabled = true,
            storageUsageEstimateMb = catalogStats.storageUsageEstimateMb,
            storageSources = sourceItems,
            isScanning = isScanning,
            scanStatusMessage = statusMsg,
            scanErrorMessage = errorMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    fun toggleSource(sourceId: String, isEnabled: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            try {
                storageSourceRepository.setSourceEnabled(sourceId, isEnabled)
            } catch (t: Throwable) {
                _scanErrorMessage.value = "Failed to update source: ${t.message}"
            }
        }
    }

    fun registerSafFolder(treeUri: String, displayName: String) {
        if (treeUri.isBlank()) return
        viewModelScope.launch(ioDispatcher) {
            val normalized = StorageSource.SafDocumentTree.normalizeTreeUri(treeUri)
            val hadPreExistingGrant = safDelegate?.hasPersistedReadPermission(normalized) ?: false
            val canonicalId = StorageSource.SafDocumentTree.deriveCanonicalId(treeUri)
            val isAlreadyRegistered = storageSourceRepository.getSourceById(canonicalId) != null

            try {
                // Acquire persistent read permission before registering source
                safDelegate?.takePersistableReadPermission(normalized)

                val safSource = StorageSource.SafDocumentTree(
                    treeUri = normalized,
                    displayName = displayName.ifBlank { "Folder Tree" },
                    isPersisted = true,
                    isRemovable = false,
                    isAvailable = true
                )
                storageSourceRepository.saveSource(safSource)
                coordinator?.onSafPermissionGranted(normalized)
                _scanStatusMessage.value = "Folder registered: ${safSource.displayName}"
                _scanErrorMessage.value = null
            } catch (ce: kotlinx.coroutines.CancellationException) {
                if (!hadPreExistingGrant && !isAlreadyRegistered) {
                    try {
                        safDelegate?.releasePersistableReadPermission(normalized)
                    } catch (rollbackError: Throwable) {
                        ce.addSuppressed(rollbackError)
                    }
                }
                throw ce
            } catch (t: Throwable) {
                // Roll back newly acquired permission if source registration failed
                if (!hadPreExistingGrant && !isAlreadyRegistered) {
                    try {
                        safDelegate?.releasePersistableReadPermission(normalized)
                    } catch (rollbackError: Throwable) {
                        t.addSuppressed(rollbackError)
                    }
                }
                _scanErrorMessage.value = "Failed to register folder: ${t.message}"
            }
        }
    }

    fun scanLibrary() {
        if (scanJob?.isActive == true) {
            return
        }
        val engine = reconciliationEngine ?: run {
            _scanErrorMessage.value = "Reconciliation engine unavailable"
            return
        }

        scanJob = viewModelScope.launch(ioDispatcher) {
            _isScanning.value = true
            _scanErrorMessage.value = null
            _scanStatusMessage.value = "Starting library scan..."
            try {
                val enabledSources = storageSourceRepository.observeEnabledSources().firstOrNull() ?: emptyList()
                if (enabledSources.isEmpty()) {
                    _scanStatusMessage.value = "No enabled storage sources to scan."
                    return@launch
                }

                var totalImported = 0
                var totalUpdated = 0
                var totalUnchanged = 0
                var totalMoved = 0
                var totalFailed = 0
                var sourcesScanned = 0

                for (source in enabledSources) {
                    val isAvailable = coordinator?.isSourceAvailable(source.id) ?: true
                    if (!isAvailable) {
                        continue // Skip disconnected or inaccessible sources
                    }

                    _scanStatusMessage.value = "Scanning ${source.displayName}..."
                    sourcesScanned++

                    engine.scanSource(source, ScanMode.INCREMENTAL).collect { progress ->
                        when (progress.status) {
                            ScanProgressStatus.FAILED -> {
                                _scanErrorMessage.value = "Scan failed on ${source.displayName}: ${progress.errorMessage}"
                            }
                            ScanProgressStatus.COMPLETED -> {
                                totalImported += progress.importedCount
                                totalUpdated += progress.updatedCount
                                totalUnchanged += progress.unchangedCount
                                totalMoved += progress.movedCount
                            }
                            ScanProgressStatus.PARTIAL -> {
                                totalImported += progress.importedCount
                                totalUpdated += progress.updatedCount
                                totalUnchanged += progress.unchangedCount
                                totalMoved += progress.movedCount
                                totalFailed += progress.failedCount
                            }
                            else -> {
                                // Discovering or Processing
                            }
                        }
                    }
                }

                if (_scanErrorMessage.value == null) {
                    _scanStatusMessage.value = if (sourcesScanned == 0) {
                        "No accessible sources available to scan."
                    } else {
                        "Scan complete: $totalImported new, $totalUpdated updated, $totalUnchanged unchanged"
                    }
                }
            } catch (t: Throwable) {
                _scanErrorMessage.value = t.message ?: "Manual scan encountered an unexpected error"
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun clearMessages() {
        _scanStatusMessage.value = null
        _scanErrorMessage.value = null
    }
}
