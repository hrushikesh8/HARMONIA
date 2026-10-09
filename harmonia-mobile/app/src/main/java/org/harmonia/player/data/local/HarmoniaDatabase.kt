package org.harmonia.player.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.harmonia.player.data.local.dao.AlbumDao
import org.harmonia.player.data.local.dao.ArtistDao
import org.harmonia.player.data.local.dao.ListeningEventDao
import org.harmonia.player.data.local.dao.LocalFileDao
import org.harmonia.player.data.local.dao.PlaybackStateDao
import org.harmonia.player.data.local.dao.PlaylistDao
import org.harmonia.player.data.local.dao.ScanCheckpointDao
import org.harmonia.player.data.local.dao.StorageSourceDao
import org.harmonia.player.data.local.dao.TrackDao
import org.harmonia.player.data.local.entity.AlbumEntity
import org.harmonia.player.data.local.entity.ArtistEntity
import org.harmonia.player.data.local.entity.ListeningEventEntity
import org.harmonia.player.data.local.entity.LocalFileEntity
import org.harmonia.player.data.local.entity.PlaybackStateEntity
import org.harmonia.player.data.local.entity.PlaylistEntity
import org.harmonia.player.data.local.entity.PlaylistItemEntity
import org.harmonia.player.data.local.entity.QueueItemEntity
import org.harmonia.player.data.local.entity.ScanCheckpointEntity
import org.harmonia.player.data.local.entity.StorageSourceEntity
import org.harmonia.player.data.local.entity.TrackEntity

/**
 * Primary persistent Room database for Harmonia Mobile.
 * Configured with SQLite WAL (Write-Ahead Logging) mode per ADR-021.
 * Schema v2 introduced local playlist management (`mobile_playlists`, `mobile_playlist_items`).
 * Schema v3 introduces storage sources and scan checkpoints (`mobile_storage_sources`, `mobile_scan_checkpoints`).
 * Schema v4 introduces explicit source association for local files (`source_id` on `mobile_local_files`).
 */
@Database(
    entities = [
        ArtistEntity::class,
        AlbumEntity::class,
        TrackEntity::class,
        LocalFileEntity::class,
        PlaybackStateEntity::class,
        QueueItemEntity::class,
        ListeningEventEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        StorageSourceEntity::class,
        ScanCheckpointEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class HarmoniaDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun localFileDao(): LocalFileDao
    abstract fun playbackStateDao(): PlaybackStateDao
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun listeningEventDao(): ListeningEventDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun storageSourceDao(): StorageSourceDao
    abstract fun scanCheckpointDao(): ScanCheckpointDao

    companion object {
        const val DATABASE_NAME = "harmonia_library.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mobile_playlists` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `description` TEXT,
                        `artwork_hash` TEXT,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mobile_playlist_items` (
                        `id` TEXT NOT NULL,
                        `playlist_id` TEXT NOT NULL,
                        `track_id` TEXT NOT NULL,
                        `position` INTEGER NOT NULL,
                        `added_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`playlist_id`) REFERENCES `mobile_playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`track_id`) REFERENCES `mobile_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobile_playlist_items_playlist_id` ON `mobile_playlist_items` (`playlist_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobile_playlist_items_track_id` ON `mobile_playlist_items` (`track_id`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mobile_playlist_items_playlist_id_position` ON `mobile_playlist_items` (`playlist_id`, `position`)")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mobile_storage_sources` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `uri` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `is_removable` INTEGER NOT NULL,
                        `is_enabled` INTEGER NOT NULL,
                        `last_scanned_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `mobile_scan_checkpoints` (
                        `source_id` TEXT NOT NULL,
                        `volume_name` TEXT NOT NULL,
                        `last_generation` INTEGER NOT NULL,
                        `last_scan_timestamp` INTEGER NOT NULL,
                        `scanned_file_count` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        PRIMARY KEY(`source_id`),
                        FOREIGN KEY(`source_id`) REFERENCES `mobile_storage_sources`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())

                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobile_scan_checkpoints_source_id` ON `mobile_scan_checkpoints` (`source_id`)")

                db.execSQL("ALTER TABLE `mobile_tracks` ADD COLUMN `is_provisional` INTEGER NOT NULL DEFAULT 1")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `mobile_local_files` ADD COLUMN `source_id` TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobile_local_files_source_id` ON `mobile_local_files` (`source_id`)")
            }
        }

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration(false)
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
