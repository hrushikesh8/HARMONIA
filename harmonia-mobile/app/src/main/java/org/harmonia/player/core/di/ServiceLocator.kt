package org.harmonia.player.core.di

import android.content.Context
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.OfflineListeningEventRepository
import org.harmonia.player.data.local.OfflineLocalFileRepository
import org.harmonia.player.data.local.OfflinePlaybackStateRepository
import org.harmonia.player.data.local.OfflineTrackRepository
import org.harmonia.player.domain.playback.PlaybackPort
import org.harmonia.player.domain.repository.ListeningEventRepository
import org.harmonia.player.domain.repository.LocalFileRepository
import org.harmonia.player.domain.repository.PlaybackStateRepository
import org.harmonia.player.domain.repository.TrackRepository

/**
 * Lightweight, explicit Service Locator for Harmonia Mobile.
 * Avoids reflection overhead while providing clean dependency injection.
 */
object ServiceLocator {

    private var database: HarmoniaDatabase? = null

    @Volatile
    private var trackRepositoryInstance: TrackRepository? = null

    @Volatile
    private var localFileRepositoryInstance: LocalFileRepository? = null

    @Volatile
    private var playbackStateRepositoryInstance: PlaybackStateRepository? = null

    @Volatile
    private var listeningEventRepositoryInstance: ListeningEventRepository? = null

    @Volatile
    private var playlistRepositoryInstance: org.harmonia.player.domain.repository.PlaylistRepository? = null

    @Volatile
    private var albumRepositoryInstance: org.harmonia.player.domain.repository.AlbumRepository? = null

    @Volatile
    private var artistRepositoryInstance: org.harmonia.player.domain.repository.ArtistRepository? = null

    @Volatile
    private var searchRepositoryInstance: org.harmonia.player.domain.repository.SearchRepository? = null

    @Volatile
    private var storageSourceRepositoryInstance: org.harmonia.player.domain.repository.StorageSourceRepository? = null

    @Volatile
    private var scanCheckpointRepositoryInstance: org.harmonia.player.domain.repository.ScanCheckpointRepository? = null

    @Volatile
    private var personalizationRepositoryInstance: org.harmonia.player.domain.repository.PersonalizationRepository? = null

    @Volatile
    private var storagePermissionCheckerInstance: org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker? = null

    @Volatile
    private var audioMetadataReaderInstance: org.harmonia.player.domain.metadata.AudioMetadataReader? = null

    @Volatile
    private var mediaReconciliationEngineInstance: org.harmonia.player.domain.scanner.MediaReconciliationEngine? = null

    @Volatile
    private var storageVolumeLifecycleCoordinatorInstance: org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator? = null

    @Volatile
    private var playbackPortInstance: PlaybackPort? = null

    @Volatile
    private var safPermissionDelegateInstance: org.harmonia.player.domain.model.storage.permission.SafPermissionDelegate? = null

    @Volatile
    private var equalizerUserIntentSourceInstance: org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource? = null

    private val audioEqualizerCoordinatorFlow = kotlinx.coroutines.flow.MutableStateFlow<org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator?>(null)

    fun initialize(context: Context) {
        if (equalizerUserIntentSourceInstance == null) {
            val prefs = context.applicationContext.getSharedPreferences(
                org.harmonia.player.data.local.preferences.SharedPreferencesEqualizerUserIntentSource.PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            equalizerUserIntentSourceInstance = org.harmonia.player.data.local.preferences.SharedPreferencesEqualizerUserIntentSource(prefs)
        }

        if (database == null) {

            database = HarmoniaDatabase.getInstance(context)
        }
        if (storagePermissionCheckerInstance == null) {
            storagePermissionCheckerInstance = org.harmonia.player.data.local.permission.AndroidStoragePermissionChecker(context.applicationContext)
        }
        if (audioMetadataReaderInstance == null) {
            audioMetadataReaderInstance = org.harmonia.player.data.local.metadata.AndroidAudioMetadataReader(context.applicationContext)
        }
        if (mediaReconciliationEngineInstance == null) {
            val permChecker = storagePermissionCheckerInstance ?: org.harmonia.player.data.local.permission.AndroidStoragePermissionChecker(context.applicationContext)
            val metaReader = audioMetadataReaderInstance ?: org.harmonia.player.data.local.metadata.AndroidAudioMetadataReader(context.applicationContext)
            val db = database ?: HarmoniaDatabase.getInstance(context)
            val msScanner = org.harmonia.player.data.local.scanner.MediaStoreScanner(context.applicationContext, permChecker)
            val safScanner = org.harmonia.player.data.local.scanner.SafTreeScanner(context.applicationContext, permChecker)
            mediaReconciliationEngineInstance = org.harmonia.player.data.local.scanner.DefaultMediaReconciliationEngine(
                context = context.applicationContext,
                database = db,
                metadataReader = metaReader,
                permissionChecker = permChecker,
                mediaStoreScanner = msScanner,
                safTreeScanner = safScanner
            )
        }
        if (storageVolumeLifecycleCoordinatorInstance == null) {
            val db = database ?: HarmoniaDatabase.getInstance(context)
            val reconEngine = mediaReconciliationEngineInstance ?: error("MediaReconciliationEngine missing")
            val platformBridge = org.harmonia.player.data.local.storage.lifecycle.AndroidStoragePlatformBridge(context.applicationContext)
            storageVolumeLifecycleCoordinatorInstance = org.harmonia.player.data.local.storage.lifecycle.DefaultStorageVolumeLifecycleCoordinator(
                storageSourceDao = db.storageSourceDao(),
                reconciliationEngine = reconEngine,
                platformBridge = platformBridge
            )
        }
        if (safPermissionDelegateInstance == null) {
            safPermissionDelegateInstance = org.harmonia.player.data.local.permission.AndroidSafPermissionDelegate(context.applicationContext)
        }
    }

    /**
     * For unit and integration tests to inject an in-memory database or mocks.
     */
    fun setDatabaseForTesting(testDatabase: HarmoniaDatabase) {
        database = testDatabase
        trackRepositoryInstance = null
        localFileRepositoryInstance = null
        playbackStateRepositoryInstance = null
        listeningEventRepositoryInstance = null
        playlistRepositoryInstance = null
        albumRepositoryInstance = null
        artistRepositoryInstance = null
        searchRepositoryInstance = null
        storageSourceRepositoryInstance = null
        scanCheckpointRepositoryInstance = null
        personalizationRepositoryInstance = null
        playbackPortInstance = null
    }

    fun setStoragePermissionCheckerForTesting(checker: org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker) {
        storagePermissionCheckerInstance = checker
    }

    fun getDatabase(): HarmoniaDatabase {
        return database ?: error("ServiceLocator has not been initialized with Context!")
    }

    fun getTrackRepository(): TrackRepository {
        return trackRepositoryInstance ?: synchronized(this) {
            trackRepositoryInstance ?: OfflineTrackRepository(getDatabase().trackDao()).also {
                trackRepositoryInstance = it
            }
        }
    }

    fun getLocalFileRepository(): LocalFileRepository {
        return localFileRepositoryInstance ?: synchronized(this) {
            localFileRepositoryInstance ?: OfflineLocalFileRepository(getDatabase().localFileDao()).also {
                localFileRepositoryInstance = it
            }
        }
    }

    fun getPlaybackStateRepository(): PlaybackStateRepository {
        return playbackStateRepositoryInstance ?: synchronized(this) {
            playbackStateRepositoryInstance ?: OfflinePlaybackStateRepository(getDatabase().playbackStateDao()).also {
                playbackStateRepositoryInstance = it
            }
        }
    }

    fun getListeningEventRepository(): ListeningEventRepository {
        return listeningEventRepositoryInstance ?: synchronized(this) {
            listeningEventRepositoryInstance ?: OfflineListeningEventRepository(getDatabase().listeningEventDao()).also {
                listeningEventRepositoryInstance = it
            }
        }
    }

    fun getPlaylistRepository(): org.harmonia.player.domain.repository.PlaylistRepository {
        return playlistRepositoryInstance ?: synchronized(this) {
            playlistRepositoryInstance ?: org.harmonia.player.data.local.OfflinePlaylistRepository(
                getDatabase().playlistDao(),
                getDatabase().trackDao()
            ).also { playlistRepositoryInstance = it }
        }
    }

    fun getAlbumRepository(): org.harmonia.player.domain.repository.AlbumRepository {
        return albumRepositoryInstance ?: synchronized(this) {
            albumRepositoryInstance ?: org.harmonia.player.data.local.OfflineAlbumRepository(
                getDatabase().albumDao(),
                getDatabase().artistDao()
            ).also { albumRepositoryInstance = it }
        }
    }

    fun getArtistRepository(): org.harmonia.player.domain.repository.ArtistRepository {
        return artistRepositoryInstance ?: synchronized(this) {
            artistRepositoryInstance ?: org.harmonia.player.data.local.OfflineArtistRepository(
                getDatabase().artistDao()
            ).also { artistRepositoryInstance = it }
        }
    }

    fun getSearchRepository(): org.harmonia.player.domain.repository.SearchRepository {
        return searchRepositoryInstance ?: synchronized(this) {
            searchRepositoryInstance ?: org.harmonia.player.data.local.OfflineSearchRepository(
                getDatabase().trackDao(),
                getDatabase().albumDao(),
                getDatabase().artistDao(),
                getDatabase().playlistDao()
            ).also { searchRepositoryInstance = it }
        }
    }

    fun getStorageSourceRepository(): org.harmonia.player.domain.repository.StorageSourceRepository {
        return storageSourceRepositoryInstance ?: synchronized(this) {
            storageSourceRepositoryInstance ?: org.harmonia.player.data.local.OfflineStorageSourceRepository(
                getDatabase().storageSourceDao()
            ).also { storageSourceRepositoryInstance = it }
        }
    }

    fun getScanCheckpointRepository(): org.harmonia.player.domain.repository.ScanCheckpointRepository {
        return scanCheckpointRepositoryInstance ?: synchronized(this) {
            scanCheckpointRepositoryInstance ?: org.harmonia.player.data.local.OfflineScanCheckpointRepository(
                getDatabase().scanCheckpointDao()
            ).also { scanCheckpointRepositoryInstance = it }
        }
    }

    fun getPersonalizationDao(): org.harmonia.player.data.local.dao.PersonalizationDao {
        return getDatabase().personalizationDao()
    }

    fun getPersonalizationRepository(): org.harmonia.player.domain.repository.PersonalizationRepository {
        return personalizationRepositoryInstance ?: synchronized(this) {
            personalizationRepositoryInstance ?: org.harmonia.player.data.local.OfflinePersonalizationRepository(
                personalizationDao = getDatabase().personalizationDao(),
                trackDao = getDatabase().trackDao()
            ).also { personalizationRepositoryInstance = it }
        }
    }

    fun setPersonalizationRepositoryForTesting(repository: org.harmonia.player.domain.repository.PersonalizationRepository?) {
        personalizationRepositoryInstance = repository
    }

    fun getStoragePermissionChecker(): org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker {
        return storagePermissionCheckerInstance ?: error("StoragePermissionChecker has not been initialized in ServiceLocator!")
    }

    fun getAudioMetadataReader(): org.harmonia.player.domain.metadata.AudioMetadataReader {
        return audioMetadataReaderInstance ?: error("AudioMetadataReader has not been initialized in ServiceLocator!")
    }

    fun setAudioMetadataReaderForTesting(reader: org.harmonia.player.domain.metadata.AudioMetadataReader) {
        audioMetadataReaderInstance = reader
    }

    fun getMediaReconciliationEngine(): org.harmonia.player.domain.scanner.MediaReconciliationEngine {
        return mediaReconciliationEngineInstance ?: error("MediaReconciliationEngine has not been initialized in ServiceLocator!")
    }

    fun setMediaReconciliationEngineForTesting(engine: org.harmonia.player.domain.scanner.MediaReconciliationEngine) {
        mediaReconciliationEngineInstance = engine
    }

    fun getStorageVolumeLifecycleCoordinator(): org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator {
        return storageVolumeLifecycleCoordinatorInstance ?: error("StorageVolumeLifecycleCoordinator has not been initialized in ServiceLocator!")
    }

    fun setStorageVolumeLifecycleCoordinatorForTesting(coordinator: org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator) {
        storageVolumeLifecycleCoordinatorInstance = coordinator
    }

    fun getPlaybackPort(): PlaybackPort {
        return playbackPortInstance ?: error("PlaybackPort has not been registered in ServiceLocator!")
    }

    fun setPlaybackPort(port: PlaybackPort) {
        playbackPortInstance = port
    }

    fun getSafPermissionDelegate(): org.harmonia.player.domain.model.storage.permission.SafPermissionDelegate {
        return safPermissionDelegateInstance ?: error("SafPermissionDelegate has not been initialized in ServiceLocator!")
    }

    fun setSafPermissionDelegateForTesting(delegate: org.harmonia.player.domain.model.storage.permission.SafPermissionDelegate) {
        safPermissionDelegateInstance = delegate
    }

    fun getEqualizerUserIntentSource(): org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource {
        return equalizerUserIntentSourceInstance ?: synchronized(this) {
            equalizerUserIntentSourceInstance ?: org.harmonia.player.service.equalizer.InMemoryEqualizerUserIntentSource().also {
                equalizerUserIntentSourceInstance = it
            }
        }
    }

    fun setEqualizerUserIntentSourceForTesting(source: org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource) {
        equalizerUserIntentSourceInstance = source
    }

    fun getAudioEqualizerCoordinator(): org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator? {
        return audioEqualizerCoordinatorFlow.value
    }

    fun observeAudioEqualizerCoordinator(): kotlinx.coroutines.flow.StateFlow<org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator?> {
        return audioEqualizerCoordinatorFlow
    }

    fun setAudioEqualizerCoordinator(coordinator: org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator?) {
        audioEqualizerCoordinatorFlow.value = coordinator
    }

    fun clearAudioEqualizerCoordinator(coordinator: org.harmonia.player.domain.model.equalizer.AudioEqualizerCoordinator): Boolean {
        return audioEqualizerCoordinatorFlow.compareAndSet(coordinator, null)
    }

    fun reset() {
        database?.close()
        database = null
        trackRepositoryInstance = null
        localFileRepositoryInstance = null
        playbackStateRepositoryInstance = null
        listeningEventRepositoryInstance = null
        playlistRepositoryInstance = null
        albumRepositoryInstance = null
        artistRepositoryInstance = null
        searchRepositoryInstance = null
        storageSourceRepositoryInstance = null
        scanCheckpointRepositoryInstance = null
        personalizationRepositoryInstance = null
        storagePermissionCheckerInstance = null
        audioMetadataReaderInstance = null
        mediaReconciliationEngineInstance = null
        storageVolumeLifecycleCoordinatorInstance = null
        playbackPortInstance = null
        safPermissionDelegateInstance = null
        equalizerUserIntentSourceInstance = null
        audioEqualizerCoordinatorFlow.value = null
    }
}
