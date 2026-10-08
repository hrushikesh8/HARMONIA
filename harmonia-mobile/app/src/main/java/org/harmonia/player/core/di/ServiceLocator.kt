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
    private var storagePermissionCheckerInstance: org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker? = null

    @Volatile
    private var playbackPortInstance: PlaybackPort? = null

    fun initialize(context: Context) {
        if (database == null) {
            database = HarmoniaDatabase.getInstance(context)
        }
        if (storagePermissionCheckerInstance == null) {
            storagePermissionCheckerInstance = org.harmonia.player.data.local.permission.AndroidStoragePermissionChecker(context.applicationContext)
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

    fun getStoragePermissionChecker(): org.harmonia.player.domain.model.storage.permission.StoragePermissionChecker {
        return storagePermissionCheckerInstance ?: error("StoragePermissionChecker has not been initialized in ServiceLocator!")
    }

    fun getPlaybackPort(): PlaybackPort {
        return playbackPortInstance ?: error("PlaybackPort has not been registered in ServiceLocator!")
    }

    fun setPlaybackPort(port: PlaybackPort) {
        playbackPortInstance = port
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
        storagePermissionCheckerInstance = null
        playbackPortInstance = null
    }
}
