package org.harmonia.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.harmonia.player.data.local.dao.AlbumDao
import org.harmonia.player.data.local.dao.ArtistDao
import org.harmonia.player.data.local.dao.ListeningEventDao
import org.harmonia.player.data.local.dao.LocalFileDao
import org.harmonia.player.data.local.dao.PlaybackStateDao
import org.harmonia.player.data.local.dao.TrackDao
import org.harmonia.player.data.local.entity.AlbumEntity
import org.harmonia.player.data.local.entity.ArtistEntity
import org.harmonia.player.data.local.entity.ListeningEventEntity
import org.harmonia.player.data.local.entity.LocalFileEntity
import org.harmonia.player.data.local.entity.PlaybackStateEntity
import org.harmonia.player.data.local.entity.QueueItemEntity
import org.harmonia.player.data.local.entity.TrackEntity

/**
 * Primary persistent Room database for Harmonia Mobile.
 * Configured with SQLite WAL (Write-Ahead Logging) mode per ADR-021.
 */
@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        LocalFileEntity::class,
        PlaybackStateEntity::class,
        QueueItemEntity::class,
        ListeningEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HarmoniaDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun localFileDao(): LocalFileDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun listeningEventDao(): ListeningEventDao

    companion object {
        const val DATABASE_NAME = "harmonia_library.db"

        @Volatile
        private var INSTANCE: HarmoniaDatabase? = null

        fun getInstance(context: Context): HarmoniaDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): HarmoniaDatabase {
            return Room.databaseBuilder(
                context,
                HarmoniaDatabase::class.java,
                DATABASE_NAME
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * In-memory database builder for test suites.
         */
        fun buildInMemory(context: Context): HarmoniaDatabase {
            return Room.inMemoryDatabaseBuilder(
                context,
                HarmoniaDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
