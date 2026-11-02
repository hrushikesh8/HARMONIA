package org.harmonia.player.data.local.storage.lifecycle

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.harmonia.player.data.local.dao.StorageSourceDao
import org.harmonia.player.data.local.entity.StorageSourceEntity
import org.harmonia.player.domain.model.scanner.ScanMode
import org.harmonia.player.domain.model.storage.StorageSource
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.harmonia.player.domain.scanner.MediaReconciliationEngine

/**
 * Concrete implementation of StorageVolumeLifecycleCoordinator.
 * Manages runtime availability of storage sources across hotplug and permission events.
 *
 * Strict Invariants:
 * 1. Volume unmount/eject never mutates StorageSourceEntity.isEnabled (user configuration).
 * 2. Volume unmount/eject never mutates LocalFileEntity records to MISSING (file existence).
 * 3. Never deletes TrackEntity, LocalFileEntity, playlist items, or listening history on eject.
 * 4. Idempotent under duplicate or out-of-order events.
 * 5. Uses bounded readiness checks rather than arbitrary sleep delays before reconciliation.
 */
class DefaultStorageVolumeLifecycleCoordinator(
    private val storageSourceDao: StorageSourceDao,
    private val reconciliationEngine: MediaReconciliationEngine,
    private val platformBridge: StoragePlatformBridge,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
) : StorageVolumeLifecycleCoordinator {

    private val _runtimeStates = MutableStateFlow<Map<String, SourceRuntimeState>>(emptyMap())
    override val runtimeStates: StateFlow<Map<String, SourceRuntimeState>> = _runtimeStates.asStateFlow()

    private val lock = Any()
    private var platformSubscription: AutoCloseable? = null
    private val initialReadinessDeferred = CompletableDeferred<Boolean>()
    private var initialRevalidationJob: Job? = null

    override fun startMonitoring() {
        synchronized(lock) {
            if (platformSubscription != null) return
            platformSubscription = platformBridge.registerVolumeEventListener(object : VolumeEventListener {
                override fun onVolumeMounted(volumeId: String) {
                    this@DefaultStorageVolumeLifecycleCoordinator.onVolumeMounted(volumeId)
                }

                override fun onVolumeUnmounted(volumeId: String) {
                    this@DefaultStorageVolumeLifecycleCoordinator.onVolumeUnmounted(volumeId)
                }
            })
            if (initialRevalidationJob == null) {
                triggerInitialRevalidationLocked()
            } else {
                // Restarting after stopMonitoring(): refresh storage state to reflect transitions while stopped
                scope.launch {
                    try {
                        revalidateAllSources()
                    } catch (e: Exception) {
                        // Keep fail-closed runtime state
                    }
                }
            }
        }
    }

    private fun triggerInitialRevalidationLocked() {
        if (initialRevalidationJob == null) {
            initialRevalidationJob = scope.launch {
                try {
                    revalidateAllSources()
                    initialReadinessDeferred.complete(true)
                } catch (ce: CancellationException) {
                    initialReadinessDeferred.complete(false)
                    throw ce
                } catch (e: Exception) {
                    initialReadinessDeferred.complete(false)
                }
            }
        }
    }

    override suspend fun awaitInitialReadiness(): Boolean {
        synchronized(lock) {
            if (!initialReadinessDeferred.isCompleted && platformSubscription == null) {
                startMonitoring()
            }
        }
        return try {
            initialReadinessDeferred.await()
        } catch (ce: CancellationException) {
            throw ce
        }
    }

    override fun stopMonitoring() {
        synchronized(lock) {
            platformSubscription?.close()
            platformSubscription = null
        }
    }

    override fun isSourceAvailable(sourceId: String): Boolean {
        val state = _runtimeStates.value[sourceId]
        if (state != null) {
            return state.isAvailable && state.isMounted && state.hasPermission
        }
        // If initial revalidation has not completed yet or primary storage is being resolved,
        // primary internal storage on Android is physically available by default.
        if (sourceId == StorageSource.MediaStorePrimary.ID) {
            return true
        }
        return false
    }

    override fun onVolumeUnmounted(volumeId: String) {
        scope.launch {
            val sources = storageSourceDao.observeAllSources().firstOrNull() ?: emptyList()
            val matching = sources.filter { matchesVolume(it, volumeId) }

            val updated = _runtimeStates.value.toMutableMap()
            for (source in matching) {
                val current = updated[source.id]
                // Idempotent: if already marked unmounted, avoid duplicate processing
                if (current != null && !current.isMounted && !current.isAvailable) {
                    continue
                }
                updated[source.id] = SourceRuntimeState(
                    sourceId = source.id,
                    isAvailable = false,
                    isMounted = false,
                    hasPermission = current?.hasPermission ?: true,
                    lastVerifiedTimestamp = System.currentTimeMillis()
                )
            }
            _runtimeStates.value = updated
        }
    }

    override fun onVolumeMounted(volumeId: String) {
        scope.launch {
            val sources = storageSourceDao.observeAllSources().firstOrNull() ?: emptyList()
            val matching = sources.filter { matchesVolume(it, volumeId) }

            val updated = _runtimeStates.value.toMutableMap()
            for (source in matching) {
                val current = updated[source.id]
                // Idempotent guard
                if (current != null && current.isMounted && current.isAvailable) {
                    continue
                }

                // Verify readiness via platform bridge before attempting scan
                val isReady = platformBridge.checkVolumeReadiness(source)
                if (isReady) {
                    updated[source.id] = SourceRuntimeState(
                        sourceId = source.id,
                        isAvailable = true,
                        isMounted = true,
                        hasPermission = true,
                        lastVerifiedTimestamp = System.currentTimeMillis()
                    )

                    // Reconcile ONLY if the source is configured as user-enabled
                    if (source.isEnabled) {
                        launch {
                            val domainSource = source.toDomain()
                            reconciliationEngine.scanSource(domainSource, ScanMode.INCREMENTAL).collect()
                        }
                    }
                }
            }
            _runtimeStates.value = updated
        }
    }

    override fun onSafPermissionRevoked(treeUri: String) {
        scope.launch {
            val sources = storageSourceDao.observeAllSources().firstOrNull() ?: emptyList()
            val matching = sources.filter { matchesSafSource(it, treeUri) }

            val updated = _runtimeStates.value.toMutableMap()
            for (source in matching) {
                updated[source.id] = SourceRuntimeState(
                    sourceId = source.id,
                    isAvailable = false,
                    isMounted = false,
                    hasPermission = false,
                    lastVerifiedTimestamp = System.currentTimeMillis()
                )
            }
            _runtimeStates.value = updated
        }
    }

    override fun onSafPermissionGranted(treeUri: String) {
        scope.launch {
            val sources = storageSourceDao.observeAllSources().firstOrNull() ?: emptyList()
            val matching = sources.filter { matchesSafSource(it, treeUri) }

            val updated = _runtimeStates.value.toMutableMap()
            for (source in matching) {
                updated[source.id] = SourceRuntimeState(
                    sourceId = source.id,
                    isAvailable = true,
                    isMounted = true,
                    hasPermission = true,
                    lastVerifiedTimestamp = System.currentTimeMillis()
                )
            }
            _runtimeStates.value = updated
        }
    }

    override suspend fun revalidateAllSources(): Map<String, SourceRuntimeState> {
        var sources = storageSourceDao.observeAllSources().firstOrNull() ?: emptyList()
        if (sources.isEmpty()) {
            try {
                storageSourceDao.upsertSource(
                    StorageSourceEntity(
                        id = StorageSource.MediaStorePrimary.ID,
                        type = "MEDIA_STORE_PRIMARY",
                        uri = "content://media/external/audio/media",
                        displayName = "Internal Audio Storage",
                        isRemovable = false,
                        isEnabled = true,
                        lastScannedAt = 0L
                    )
                )
                sources = storageSourceDao.observeAllSources().firstOrNull() ?: sources
            } catch (_: Exception) {}
        }
        val states = mutableMapOf<String, SourceRuntimeState>()

        for (sourceEntity in sources) {
            val state = revalidateSource(sourceEntity.toDomain())
            states[sourceEntity.id] = state
        }

        _runtimeStates.value = states
        return states
    }

    override suspend fun revalidateSource(source: StorageSource): SourceRuntimeState {
        return when (source) {
            is StorageSource.MediaStorePrimary -> SourceRuntimeState(
                sourceId = source.id,
                isAvailable = true,
                isMounted = true,
                hasPermission = true
            )
            is StorageSource.MediaStoreVolume -> {
                val isMounted = platformBridge.isPlatformVolumeMounted(source.volumeUuid)
                SourceRuntimeState(
                    sourceId = source.id,
                    isAvailable = isMounted,
                    isMounted = isMounted,
                    hasPermission = true
                )
            }
            is StorageSource.SafDocumentTree -> {
                val hasPermission = platformBridge.isSafPermissionGranted(source.treeUri)
                SourceRuntimeState(
                    sourceId = source.id,
                    isAvailable = hasPermission,
                    isMounted = hasPermission,
                    hasPermission = hasPermission
                )
            }
            is StorageSource.InternalSyncStorage -> SourceRuntimeState(
                sourceId = source.id,
                isAvailable = true,
                isMounted = true,
                hasPermission = true
            )
        }
    }

    private fun matchesSafSource(source: StorageSourceEntity, treeUri: String): Boolean {
        if (source.type != "SAF_DOCUMENT_TREE") return false
        val cleanInput = treeUri.trim()
        if (cleanInput.isBlank()) return false
        val normalizedInput = StorageSource.SafDocumentTree.normalizeTreeUri(cleanInput)
        val normalizedSourceUri = StorageSource.SafDocumentTree.normalizeTreeUri(source.uri)
        val canonicalId = StorageSource.SafDocumentTree.deriveCanonicalId(cleanInput)

        return source.uri == cleanInput ||
               normalizedSourceUri == normalizedInput ||
               source.id == canonicalId ||
               source.id == cleanInput
    }

    private fun matchesVolume(source: StorageSourceEntity, eventVolumeId: String): Boolean {
        val cleanEventId = eventVolumeId.trim()
        if (cleanEventId.isBlank()) return false

        if (source.type == "MEDIA_STORE_PRIMARY") {
            val normalized = cleanEventId.lowercase()
            return normalized == "primary" ||
                   normalized == StorageSource.MediaStorePrimary.ID ||
                   normalized == source.id.lowercase()
        }

        if (source.type == "MEDIA_STORE_VOLUME") {
            val sourceUuid = StorageSource.MediaStoreVolume.extractVolumeUuid(source.uri, source.id)
            if (sourceUuid == null) {
                // Persisted source is missing authoritative UUID: ambiguous, do not match
                return false
            }

            val cleanEventUuid = StorageSource.MediaStoreVolume.extractVolumeUuid(cleanEventId, cleanEventId)
                ?: cleanEventId.lowercase().removePrefix("mediastore_vol_").takeIf { StorageSource.MediaStoreVolume.isValidVolumeUuid(it) }

            if (cleanEventUuid == null) {
                return false
            }

            // Match strictly on authoritative normalized UUID. NEVER match solely by volumeName!
            return sourceUuid == cleanEventUuid
        }

        return false
    }

    private fun StorageSourceEntity.toDomain(): StorageSource {
        return when (type) {
            "MEDIA_STORE_PRIMARY" -> StorageSource.MediaStorePrimary(
                id = id,
                displayName = displayName,
                isRemovable = isRemovable,
                isAvailable = isEnabled
            )
            "MEDIA_STORE_VOLUME" -> {
                val extractedUuid = StorageSource.MediaStoreVolume.extractVolumeUuid(uri, id) ?: id
                val volumeName = if (uri.contains(":") && !uri.startsWith("content:", ignoreCase = true) && !uri.startsWith("file:", ignoreCase = true)) {
                    uri.substringBefore(":").trim().ifBlank { displayName }
                } else {
                    displayName.ifBlank { extractedUuid }
                }
                StorageSource.MediaStoreVolume(
                    id = id,
                    volumeName = volumeName,
                    volumeUuid = extractedUuid,
                    displayName = displayName,
                    isRemovable = isRemovable,
                    isAvailable = isEnabled
                )
            }
            "SAF_DOCUMENT_TREE" -> StorageSource.SafDocumentTree(
                id = id,
                treeUri = uri,
                displayName = displayName,
                isPersisted = true,
                isRemovable = isRemovable,
                isAvailable = isEnabled
            )
            "INTERNAL_SYNC" -> StorageSource.InternalSyncStorage(
                id = id,
                rootPath = uri,
                displayName = displayName,
                isRemovable = isRemovable,
                isAvailable = isEnabled
            )
            else -> StorageSource.MediaStorePrimary(
                id = id,
                displayName = displayName,
                isRemovable = isRemovable,
                isAvailable = isEnabled
            )
        }
    }
}
