package org.harmonia.player.core.di

import android.content.Context
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.OfflineLocalFileRepository
import org.harmonia.player.data.local.OfflinePlaybackStateRepository
import org.harmonia.player.data.local.OfflineTrackRepository
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

    fun initialize(context: Context) {
        if (database == null) {
            database = HarmoniaDatabase.getInstance(context)
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

    fun reset() {
        database?.close()
        database = null
        trackRepositoryInstance = null
        localFileRepositoryInstance = null
        playbackStateRepositoryInstance = null
    }
}
