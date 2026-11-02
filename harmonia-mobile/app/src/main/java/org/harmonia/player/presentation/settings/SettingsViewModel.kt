package org.harmonia.player.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
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
import org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator
import org.harmonia.player.domain.model.equalizer.EqualizerBand
import org.harmonia.player.domain.model.equalizer.EqualizerFailureReason
import org.harmonia.player.domain.model.equalizer.EqualizerPreset
import org.harmonia.player.domain.model.equalizer.EqualizerState
import org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource
import org.harmonia.player.domain.model.audio.SleepTimerMode
import org.harmonia.player.domain.model.audio.SleepTimerPreset
import org.harmonia.player.domain.model.audio.SleepTimerState
import org.harmonia.player.domain.scanner.MediaReconciliationEngine

enum class EqualizerUiStatus {
    UNSUPPORTED,
    UNAVAILABLE_NO_SESSION,
    CONTROL_LOST,
    DISABLED,
    ENABLED
}

data class EqualizerUiState(
    val isUserEnabled: Boolean = false,
    val status: EqualizerUiStatus = EqualizerUiStatus.UNAVAILABLE_NO_SESSION,
    val statusMessage: String = "No active playback session",
    val bandLevelRange: IntRange = -1500..1500,
    val bands: List<EqualizerBand> = emptyList(),
    val presets: List<EqualizerPreset> = emptyList(),
    val currentPresetIndex: Short = -1,
    val isInteractive: Boolean = false
)

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
    val scanErrorMessage: String? = null,
    val equalizer: EqualizerUiState = EqualizerUiState(),
    val isOnlineEnrichmentEnabled: Boolean = false,
    val isDarkTheme: Boolean = true,
    val defaultShuffle: Boolean = false,
    val defaultRepeatMode: org.harmonia.player.domain.model.RepeatMode = org.harmonia.player.domain.model.RepeatMode.OFF,
    val keepScreenOn: Boolean = false,
    val isSleepTimerActive: Boolean = false,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerTargetDurationMs: Long = 0L,
    val sleepTimerMode: SleepTimerMode? = null
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
    private val equalizerUserIntentSource: EqualizerUserIntentSource? = null,
    private val audioEqualizerCoordinator: AudioEqualizerCoordinator? = null,
    private val onlineEnrichmentService: org.harmonia.player.data.remote.MusicBrainzEnrichmentService? = null,
    private val userPreferencesRepository: org.harmonia.player.domain.repository.UserPreferencesRepository? = null,
    private val playbackPort: org.harmonia.player.domain.playback.PlaybackPort? = null,
    private val scanCheckpointRepository: org.harmonia.player.domain.repository.ScanCheckpointRepository? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val userPrefs: org.harmonia.player.domain.repository.UserPreferencesRepository
        get() = userPreferencesRepository ?: ServiceLocator.getUserPreferencesRepository()

    private val activePlaybackPort: org.harmonia.player.domain.playback.PlaybackPort?
        get() = playbackPort ?: runCatching { ServiceLocator.getPlaybackPort() }.getOrNull()

    private val checkpointRepo: org.harmonia.player.domain.repository.ScanCheckpointRepository?
        get() = scanCheckpointRepository ?: runCatching { ServiceLocator.getScanCheckpointRepository() }.getOrNull()

    private val enrichmentService: org.harmonia.player.data.remote.MusicBrainzEnrichmentService?
        get() = onlineEnrichmentService ?: runCatching { ServiceLocator.getEnrichmentService() }.getOrNull()

    private val coordinator: StorageVolumeLifecycleCoordinator?
        get() = storageVolumeLifecycleCoordinator ?: runCatching { ServiceLocator.getStorageVolumeLifecycleCoordinator() }.getOrNull()

    private val reconciliationEngine: MediaReconciliationEngine?
        get() = mediaReconciliationEngine ?: runCatching { ServiceLocator.getMediaReconciliationEngine() }.getOrNull()

    private val permissionChecker: StoragePermissionChecker?
        get() = storagePermissionChecker ?: runCatching { ServiceLocator.getStoragePermissionChecker() }.getOrNull()

    private val safDelegate: SafPermissionDelegate?
        get() = safPermissionDelegate ?: runCatching { ServiceLocator.getSafPermissionDelegate() }.getOrNull()

    private val intentSource: EqualizerUserIntentSource
        get() = equalizerUserIntentSource ?: ServiceLocator.getEqualizerUserIntentSource()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val equalizerCoordinatorFlow: StateFlow<AudioEqualizerCoordinator?> =
        if (audioEqualizerCoordinator != null) {
            MutableStateFlow(audioEqualizerCoordinator)
        } else {
            ServiceLocator.observeAudioEqualizerCoordinator()
        }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val equalizerStateFlow: Flow<EqualizerState> =
        equalizerCoordinatorFlow.flatMapLatest { coordinatorInstance: AudioEqualizerCoordinator? ->
            coordinatorInstance?.state ?: MutableStateFlow(EqualizerState.NoSession())
        }

    private val equalizerUiStateFlow: Flow<EqualizerUiState> = combine(
        intentSource.observeEqualizerEnabled(),
        equalizerStateFlow
    ) { isUserEnabled, eqState ->
        when (eqState) {
            is EqualizerState.NoSession -> {
                val failure = eqState.failureReason
                val (status, msg) = when (failure) {
                    is EqualizerFailureReason.UnsupportedHardwareOrRoute ->
                        EqualizerUiStatus.UNSUPPORTED to (failure.message ?: "Equalizer unsupported on this device or route")
                    is EqualizerFailureReason.InvalidSessionOrArgument ->
                        EqualizerUiStatus.UNSUPPORTED to (failure.message ?: "Invalid audio session")
                    is EqualizerFailureReason.NativeIllegalState ->
                        EqualizerUiStatus.UNSUPPORTED to (failure.message ?: "Equalizer native state error")
                    is EqualizerFailureReason.UnexpectedNativeError ->
                        EqualizerUiStatus.UNSUPPORTED to (failure.message ?: "Unexpected equalizer error")
                    null ->
                        EqualizerUiStatus.UNAVAILABLE_NO_SESSION to "No active playback session"
                }
                EqualizerUiState(
                    isUserEnabled = isUserEnabled,
                    status = status,
                    statusMessage = msg,
                    isInteractive = false
                )
            }
            is EqualizerState.ControlLost -> {
                EqualizerUiState(
                    isUserEnabled = isUserEnabled,
                    status = EqualizerUiStatus.CONTROL_LOST,
                    statusMessage = "Audio effect control temporarily lost to another application",
                    bandLevelRange = eqState.bandLevelRange,
                    bands = eqState.bands,
                    presets = eqState.presets,
                    isInteractive = false
                )
            }
            is EqualizerState.AvailableDisabled -> {
                EqualizerUiState(
                    isUserEnabled = isUserEnabled,
                    status = EqualizerUiStatus.DISABLED,
                    statusMessage = "Disabled",
                    bandLevelRange = eqState.bandLevelRange,
                    bands = eqState.bands,
                    presets = eqState.presets,
                    isInteractive = false
                )
            }
            is EqualizerState.EnabledWithControl -> {
                EqualizerUiState(
                    isUserEnabled = isUserEnabled,
                    status = EqualizerUiStatus.ENABLED,
                    statusMessage = "Active",
                    bandLevelRange = eqState.bandLevelRange,
                    bands = eqState.bands,
                    presets = eqState.presets,
                    currentPresetIndex = eqState.currentPresetIndex,
                    isInteractive = true
                )
            }
        }
    }

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

    private val enrichmentEnabledFlow: Flow<Boolean> =
        enrichmentService?.isEnrichmentEnabled ?: MutableStateFlow(false)

    private val scanStateFlow = combine(
        _isScanning,
        _scanStatusMessage,
        _scanErrorMessage
    ) { isScanning, statusMsg, errorMsg ->
        Triple(isScanning, statusMsg, errorMsg)
    }

    private data class UserPreferencesTuple(
        val isDark: Boolean,
        val shuffle: Boolean,
        val repeat: org.harmonia.player.domain.model.RepeatMode,
        val keepScreenOn: Boolean
    )

    private data class AuxSettingsState(
        val equalizer: EqualizerUiState,
        val isEnrichmentEnabled: Boolean,
        val prefs: UserPreferencesTuple,
        val isSleepTimerActive: Boolean,
        val sleepTimerRemainingMs: Long,
        val sleepTimerTargetDurationMs: Long,
        val sleepTimerMode: SleepTimerMode?
    )

    private val prefsFlow = combine(
        userPrefs.observeIsDarkTheme(),
        userPrefs.observeDefaultShuffleEnabled(),
        userPrefs.observeDefaultRepeatMode(),
        userPrefs.observeKeepScreenOn()
    ) { isDark, shuffle, repeat, keepOn ->
        UserPreferencesTuple(isDark, shuffle, repeat, keepOn)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val playbackTimerFlow: Flow<SleepTimerState> =
        activePlaybackPort?.sleepTimerState
            ?: MutableStateFlow(SleepTimerState())

    private val auxStateFlow = combine(
        equalizerUiStateFlow,
        enrichmentEnabledFlow,
        prefsFlow,
        playbackTimerFlow
    ) { eq, enrichmentEnabled, prefs, timerState ->
        AuxSettingsState(
            eq,
            enrichmentEnabled,
            prefs,
            timerState.isActive,
            timerState.remainingDurationMs,
            timerState.targetDurationMs,
            timerState.mode
        )
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        catalogStatsFlow,
        storageSourceRepository.observeAllSources(),
        runtimeStatesFlow,
        scanStateFlow,
        auxStateFlow
    ) { catalogStats, sources, runtimeStates, (isScanning, statusMsg, errorMsg), aux ->
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
                    val hasPerm = runtime?.hasPermission ?: (permissionChecker?.canScanAudioMedia() ?: true)
                    if (!hasPerm) "Permission required" to false else "Ready" to true
                }
                is StorageSource.MediaStoreVolume -> {
                    when {
                        runtime == null -> "Checking..." to false
                        !runtime.isMounted -> "Disconnected" to false
                        !runtime.hasPermission -> "Permission required" to false
                        else -> "Connected" to true
                    }
                }
                is StorageSource.SafDocumentTree -> {
                    when {
                        runtime == null -> "Checking..." to false
                        !runtime.hasPermission -> "Access revoked" to false
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
            scanErrorMessage = errorMsg,
            equalizer = aux.equalizer,
            isOnlineEnrichmentEnabled = aux.isEnrichmentEnabled,
            isDarkTheme = aux.prefs.isDark,
            defaultShuffle = aux.prefs.shuffle,
            defaultRepeatMode = aux.prefs.repeat,
            keepScreenOn = aux.prefs.keepScreenOn,
            isSleepTimerActive = aux.isSleepTimerActive,
            sleepTimerRemainingMs = aux.sleepTimerRemainingMs,
            sleepTimerTargetDurationMs = aux.sleepTimerTargetDurationMs,
            sleepTimerMode = aux.sleepTimerMode
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState(
            equalizer = EqualizerUiState(
                isUserEnabled = intentSource.isEqualizerEnabled()
            ),
            isDarkTheme = userPrefs.isDarkTheme(),
            defaultShuffle = userPrefs.isDefaultShuffleEnabled(),
            defaultRepeatMode = userPrefs.getDefaultRepeatMode(),
            keepScreenOn = userPrefs.isKeepScreenOn()
        )
    )

    fun setIsDarkTheme(isDark: Boolean) {
        userPrefs.setIsDarkTheme(isDark)
    }

    fun setDefaultShuffle(enabled: Boolean) {
        userPrefs.setDefaultShuffleEnabled(enabled)
        activePlaybackPort?.setShuffleMode(enabled)
    }

    fun setDefaultRepeatMode(mode: org.harmonia.player.domain.model.RepeatMode) {
        userPrefs.setDefaultRepeatMode(mode)
        activePlaybackPort?.setRepeatMode(mode)
    }

    fun setKeepScreenOn(enabled: Boolean) {
        userPrefs.setKeepScreenOn(enabled)
    }

    fun startSleepTimer(preset: SleepTimerPreset) {
        val durationMs = preset.durationMs
        if (durationMs != null) {
            activePlaybackPort?.startSleepTimer(durationMs)
        } else {
            activePlaybackPort?.startSleepTimerEndOfTrack()
        }
    }

    fun cancelSleepTimer() {
        activePlaybackPort?.cancelSleepTimer()
    }

    fun resetScanCheckpoints() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val sources = storageSourceRepository.observeAllSources().firstOrNull() ?: emptyList()
                sources.forEach { source ->
                    checkpointRepo?.resetCheckpoint(source.id)
                }
                _scanStatusMessage.value = "Checkpoints reset. Next scan will inspect all files."
                _scanErrorMessage.value = null
            } catch (t: Throwable) {
                _scanErrorMessage.value = "Failed to reset checkpoints: ${t.message}"
            }
        }
    }

    fun setOnlineEnrichmentEnabled(enabled: Boolean) {
        enrichmentService?.setEnrichmentEnabled(enabled)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        intentSource.setEqualizerEnabled(enabled)
        viewModelScope.launch {
            equalizerCoordinatorFlow.value?.setEnabled(enabled)
        }
    }

    fun setEqualizerBandLevel(band: Short, levelMb: Short) {
        val currentEq = uiState.value.equalizer
        if (currentEq.bands.isNotEmpty() && currentEq.bands.none { it.index == band }) {
            return
        }
        val clampedLevel = if (currentEq.bands.isNotEmpty()) {
            levelMb.coerceIn(
                currentEq.bandLevelRange.first.toShort(),
                currentEq.bandLevelRange.last.toShort()
            )
        } else {
            levelMb
        }
        viewModelScope.launch {
            equalizerCoordinatorFlow.value?.setBandLevel(band, clampedLevel)
        }
    }

    fun useEqualizerPreset(presetIndex: Short) {
        val currentEq = uiState.value.equalizer
        if (currentEq.presets.isNotEmpty() && currentEq.presets.none { it.index == presetIndex }) {
            return
        }
        viewModelScope.launch {
            equalizerCoordinatorFlow.value?.usePreset(presetIndex)
        }
    }


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
