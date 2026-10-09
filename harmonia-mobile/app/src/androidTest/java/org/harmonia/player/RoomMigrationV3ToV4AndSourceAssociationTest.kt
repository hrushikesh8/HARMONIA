package org.harmonia.player

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.dao.LocalFileDao
import org.harmonia.player.data.local.entity.LocalFileEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomMigrationV3ToV4AndSourceAssociationTest {

    private lateinit var context: Context
    private val testDbName = "migration_v3_v4_test.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val dbFile = File(context.filesDir, testDbName)
        if (dbFile.exists()) dbFile.delete()
        val dbFileWal = File(context.filesDir, "$testDbName-wal")
        if (dbFileWal.exists()) dbFileWal.delete()
        val dbFileShm = File(context.filesDir, "$testDbName-shm")
        if (dbFileShm.exists()) dbFileShm.delete()
    }

    @After
    fun tearDown() {
        val dbFile = File(context.filesDir, testDbName)
        if (dbFile.exists()) dbFile.delete()
    }

    @Test
    fun migration_3_to_4_adds_source_id_and_preserves_unresolved_legacy_records() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(3) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create minimal schema v3 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `mobile_tracks` (
                            `id` TEXT NOT NULL,
                            `album_id` TEXT,
                            `artist_id` TEXT,
                            `title` TEXT NOT NULL,
                            `track_number` INTEGER,
                            `disc_number` INTEGER NOT NULL,
                            `duration_sec` REAL NOT NULL,
                            `year` INTEGER,
                            `genre` TEXT,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `is_provisional` INTEGER NOT NULL DEFAULT 1,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    // Schema v3 mobile_local_files WITHOUT source_id
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `mobile_local_files` (
                            `id` TEXT NOT NULL,
                            `track_id` TEXT NOT NULL,
                            `storage_type` TEXT NOT NULL,
                            `file_uri` TEXT NOT NULL,
                            `file_size` INTEGER NOT NULL,
                            `file_hash` TEXT NOT NULL,
                            `format` TEXT NOT NULL,
                            `bitrate` INTEGER,
                            `sample_rate` INTEGER,
                            `bit_depth` INTEGER,
                            `channels` INTEGER,
                            `is_lossless` INTEGER NOT NULL DEFAULT 0,
                            `availability_status` TEXT NOT NULL,
                            `verified_at` INTEGER NOT NULL,
                            `added_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`track_id`) REFERENCES `mobile_tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_mobile_local_files_track_id` ON `mobile_local_files` (`track_id`)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_mobile_local_files_file_uri` ON `mobile_local_files` (`file_uri`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val rawDb = helper.writableDatabase

        // Insert pre-existing tracks and legacy local files
        rawDb.execSQL("""
            INSERT INTO `mobile_tracks` (`id`, `title`, `disc_number`, `duration_sec`, `created_at`, `updated_at`, `is_provisional`)
            VALUES ('trk_1', 'Legacy Track 1', 1, 210.5, 1700000000, 1700000000, 0),
                   ('trk_2', 'Legacy Track 2', 1, 185.0, 1700000000, 1700000000, 0)
        """.trimIndent())

        rawDb.execSQL("""
            INSERT INTO `mobile_local_files` (`id`, `track_id`, `storage_type`, `file_uri`, `file_size`, `file_hash`, `format`, `availability_status`, `verified_at`, `added_at`)
            VALUES ('file_legacy_1', 'trk_1', 'MEDIA_STORE_PRIMARY', 'content://media/external/audio/1', 4096000, 'hash_legacy_1', 'mp3', 'AVAILABLE', 1700000000, 1700000000),
                   ('file_legacy_unresolved', 'trk_2', 'SAF_IMPORTED', 'content://tree/unknown/doc/2', 8192000, 'hash_legacy_2', 'flac', 'AVAILABLE', 1700000000, 1700000000)
        """.trimIndent())

        // Apply MIGRATION_3_4
        HarmoniaDatabase.MIGRATION_3_4.migrate(rawDb)

        // 1. Verify schema: source_id column exists
        val cursor = rawDb.query("SELECT * FROM `mobile_local_files` WHERE `id` = 'file_legacy_1'")
        assertTrue("Cursor should contain records", cursor.moveToFirst())
        val sourceIdColIdx = cursor.getColumnIndex("source_id")
        assertTrue("source_id column must exist after migration", sourceIdColIdx >= 0)

        // 2. Invariant: Legacy rows are explicitly preserved with source_id == null (unresolved)
        assertNull("Pre-existing rows must not have fabricated source associations", cursor.getString(sourceIdColIdx))
        assertEquals("trk_1", cursor.getString(cursor.getColumnIndexOrThrow("track_id")))
        assertEquals("content://media/external/audio/1", cursor.getString(cursor.getColumnIndexOrThrow("file_uri")))
        assertEquals("hash_legacy_1", cursor.getString(cursor.getColumnIndexOrThrow("file_hash")))
        cursor.close()

        // 3. Verify index exists in sqlite_master
        val indexCursor = rawDb.query("SELECT sql FROM sqlite_master WHERE type = 'index' AND name = 'index_mobile_local_files_source_id'")
        assertTrue("index_mobile_local_files_source_id must exist", indexCursor.moveToFirst())
        indexCursor.close()

        // 4. Verify SQLite queries with source_id
        val unresolvedCursor = rawDb.query("SELECT id FROM `mobile_local_files` WHERE `source_id` IS NULL")
        assertEquals(2, unresolvedCursor.count)
        unresolvedCursor.close()

        rawDb.execSQL("""
            INSERT INTO `mobile_local_files` (`id`, `track_id`, `source_id`, `storage_type`, `file_uri`, `file_size`, `file_hash`, `format`, `availability_status`, `verified_at`, `added_at`)
            VALUES ('file_new_sd', 'trk_1', 'mediastore_vol_sandisk', 'MEDIA_STORE_VOLUME', 'content://media/external_sandisk/audio/42', 1048576, 'hash_new_sd', 'flac', 'AVAILABLE', 1700000000, 1700000000)
        """.trimIndent())

        val sdCursor = rawDb.query("SELECT id FROM `mobile_local_files` WHERE `source_id` = 'mediastore_vol_sandisk'")
        assertTrue(sdCursor.moveToFirst())
        assertEquals("file_new_sd", sdCursor.getString(0))
        sdCursor.close()

        rawDb.close()
        helper.close()
    }

    @Test
    fun v4_dao_handles_source_id_queries_and_unresolved_legacy_records() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            HarmoniaDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val trackDao = db.trackDao()
            val localFileDao = db.localFileDao()

            val now = System.currentTimeMillis()
            trackDao.upsertTrack(
                org.harmonia.player.data.local.entity.TrackEntity(
                    id = "trk_legacy",
                    title = "Legacy Track",
                    durationSec = 200.0,
                    createdAt = now,
                    updatedAt = now
                )
            )

            // Legacy file with null source_id
            localFileDao.upsertLocalFile(
                LocalFileEntity(
                    id = "file_legacy",
                    trackId = "trk_legacy",
                    sourceId = null,
                    storageType = "MEDIA_STORE_PRIMARY",
                    fileUri = "content://media/external/audio/legacy",
                    fileSize = 4096L,
                    fileHash = "hash_legacy",
                    format = "mp3",
                    availabilityStatus = "AVAILABLE",
                    addedAt = now
                )
            )

            // New file with explicit source association
            localFileDao.upsertLocalFile(
                LocalFileEntity(
                    id = "file_associated",
                    trackId = "trk_legacy",
                    sourceId = "mediastore_vol_sandisk",
                    storageType = "MEDIA_STORE_VOLUME",
                    fileUri = "content://media/external_sandisk/audio/new",
                    fileSize = 8192L,
                    fileHash = "hash_new",
                    format = "flac",
                    availabilityStatus = "AVAILABLE",
                    addedAt = now
                )
            )

            // Verify unresolved query
            val unresolved = localFileDao.getLocalFilesWithUnresolvedSource()
            assertEquals(1, unresolved.size)
            assertEquals("file_legacy", unresolved.first().id)
            assertNull(unresolved.first().sourceId)

            // Verify source-scoped query
            val sandiskFiles = localFileDao.getLocalFilesBySourceId("mediastore_vol_sandisk")
            assertEquals(1, sandiskFiles.size)
            assertEquals("file_associated", sandiskFiles.first().id)
            assertEquals("mediastore_vol_sandisk", sandiskFiles.first().sourceId)

            // Verify explicit source resolution
            localFileDao.updateSourceId("file_legacy", "mediastore_primary")
            val updatedUnresolved = localFileDao.getLocalFilesWithUnresolvedSource()
            assertTrue(updatedUnresolved.isEmpty())

            val resolvedFile = localFileDao.getLocalFileById("file_legacy")
            assertNotNull(resolvedFile)
            assertEquals("mediastore_primary", resolvedFile?.sourceId)
        } finally {
            db.close()
        }
    }
}
