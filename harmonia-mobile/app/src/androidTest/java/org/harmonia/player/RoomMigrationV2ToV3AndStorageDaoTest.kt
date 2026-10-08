package org.harmonia.player

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.OfflineScanCheckpointRepository
import org.harmonia.player.data.local.OfflineStorageSourceRepository
import org.harmonia.player.data.local.dao.ScanCheckpointDao
import org.harmonia.player.data.local.dao.StorageSourceDao
import org.harmonia.player.data.local.entity.ScanCheckpointEntity
import org.harmonia.player.data.local.entity.StorageSourceEntity
import org.harmonia.player.domain.model.storage.ScanCheckpoint
import org.harmonia.player.domain.model.storage.ScanStatus
import org.harmonia.player.domain.model.storage.StorageSource
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
class RoomMigrationV2ToV3AndStorageDaoTest {

    private lateinit var context: Context
    private lateinit var db: HarmoniaDatabase
    private lateinit var storageSourceDao: StorageSourceDao
    private lateinit var scanCheckpointDao: ScanCheckpointDao
    private lateinit var storageSourceRepository: OfflineStorageSourceRepository
    private lateinit var scanCheckpointRepository: OfflineScanCheckpointRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = HarmoniaDatabase.buildInMemory(context)
        storageSourceDao = db.storageSourceDao()
        scanCheckpointDao = db.scanCheckpointDao()
        storageSourceRepository = OfflineStorageSourceRepository(storageSourceDao)
        scanCheckpointRepository = OfflineScanCheckpointRepository(scanCheckpointDao)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun migration_2_to_3_preserves_existing_data_and_adds_storage_tables_and_columns() {
        val dbFile = File(context.filesDir, "migration_v2_v3_test.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.name)
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create v2 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `mobile_tracks` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `album_id` TEXT,
                            `artist_name` TEXT,
                            `album_title` TEXT,
                            `track_number` INTEGER,
                            `disc_number` INTEGER NOT NULL DEFAULT 1,
                            `duration` REAL NOT NULL,
                            `loudness` REAL,
                            `date_added` INTEGER NOT NULL,
                            `last_updated` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

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
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val rawDb = helper.writableDatabase

        // Insert v2 records
        rawDb.execSQL("INSERT INTO `mobile_tracks` VALUES ('track-v2', 'Karma Police', 'album-1', 'Radiohead', 'OK Computer', 6, 1, 261.0, -12.0, 1000, 1000)")
        rawDb.execSQL("INSERT INTO `mobile_playlists` VALUES ('pl-v2', 'Favorites', 'Best songs', 'hash-1', 2000, 2000)")

        // Apply MIGRATION_2_3
        HarmoniaDatabase.MIGRATION_2_3.migrate(rawDb)

        // 1. Verify v2 data survives
        val trackCursor = rawDb.query("SELECT id, title, is_provisional FROM `mobile_tracks` WHERE id = 'track-v2'")
        assertTrue(trackCursor.moveToFirst())
        assertEquals("Karma Police", trackCursor.getString(1))
        assertEquals(1, trackCursor.getInt(2)) // Default is_provisional = 1
        trackCursor.close()

        val playlistCursor = rawDb.query("SELECT name FROM `mobile_playlists` WHERE id = 'pl-v2'")
        assertTrue(playlistCursor.moveToFirst())
        assertEquals("Favorites", playlistCursor.getString(0))
        playlistCursor.close()

        // 2. Verify mobile_storage_sources table accepts rows
        rawDb.execSQL("INSERT INTO `mobile_storage_sources` VALUES ('src-test', 'MEDIA_STORE_PRIMARY', 'uri', 'Internal Storage', 0, 1, 3000)")
        val sourceCursor = rawDb.query("SELECT display_name, is_enabled FROM `mobile_storage_sources` WHERE id = 'src-test'")
        assertTrue(sourceCursor.moveToFirst())
        assertEquals("Internal Storage", sourceCursor.getString(0))
        assertEquals(1, sourceCursor.getInt(1))
        sourceCursor.close()

        // 3. Verify mobile_scan_checkpoints table accepts rows with foreign key
        rawDb.execSQL("INSERT INTO `mobile_scan_checkpoints` VALUES ('src-test', 'primary', 100, 4000, 50, 'COMPLETED')")
        val cpCursor = rawDb.query("SELECT last_generation, status FROM `mobile_scan_checkpoints` WHERE source_id = 'src-test'")
        assertTrue(cpCursor.moveToFirst())
        assertEquals(100L, cpCursor.getLong(0))
        assertEquals("COMPLETED", cpCursor.getString(1))
        cpCursor.close()

        rawDb.close()
        dbFile.delete()
    }

    @Test
    fun storage_source_dao_crud_operations() = runBlocking {
        val source1 = StorageSourceEntity(
            id = "src-1",
            type = "MEDIA_STORE_PRIMARY",
            uri = "content://media/external/audio/media",
            displayName = "Internal Audio",
            isRemovable = false,
            isEnabled = true,
            lastScannedAt = 1000L
        )
        val source2 = StorageSourceEntity(
            id = "src-2",
            type = "SAF_DOCUMENT_TREE",
            uri = "content://tree/uri",
            displayName = "SD Card Music",
            isRemovable = true,
            isEnabled = false,
            lastScannedAt = 2000L
        )

        storageSourceDao.upsertSources(listOf(source1, source2))

        val all = storageSourceDao.observeAllSources().first()
        assertEquals(2, all.size)

        val enabled = storageSourceDao.observeEnabledSources().first()
        assertEquals(1, enabled.size)
        assertEquals("src-1", enabled[0].id)

        // Toggle enabled
        storageSourceDao.setSourceEnabled("src-2", true)
        val enabledAfter = storageSourceDao.observeEnabledSources().first()
        assertEquals(2, enabledAfter.size)

        // Delete
        storageSourceDao.deleteSourceById("src-1")
        assertNull(storageSourceDao.getSourceById("src-1"))
        assertEquals(1, storageSourceDao.observeAllSources().first().size)
    }

    @Test
    fun scan_checkpoint_cascades_on_storage_source_deletion() = runBlocking {
        val source = StorageSourceEntity(
            id = "src-cascade",
            type = "MEDIA_STORE_PRIMARY",
            uri = "uri",
            displayName = "Internal",
            isRemovable = false,
            isEnabled = true,
            lastScannedAt = 0L
        )
        storageSourceDao.upsertSource(source)

        val checkpoint = ScanCheckpointEntity(
            sourceId = "src-cascade",
            volumeName = "primary",
            lastGeneration = 500L,
            lastScanTimestamp = 10000L,
            scannedFileCount = 120,
            status = "COMPLETED"
        )
        scanCheckpointDao.upsertCheckpoint(checkpoint)

        assertNotNull(scanCheckpointDao.getCheckpoint("src-cascade"))

        // Delete parent source
        storageSourceDao.deleteSourceById("src-cascade")

        // Foreign key cascade should remove checkpoint
        assertNull(scanCheckpointDao.getCheckpoint("src-cascade"))
    }

    @Test
    fun offline_storage_source_repository_room_integration() = runBlocking {
        val saf = StorageSource.SafDocumentTree(
            id = "saf-room-test",
            treeUri = "content://saf/tree",
            displayName = "Custom FLAC Folder",
            isPersisted = true,
            isAvailable = true
        )

        storageSourceRepository.saveSource(saf)

        val retrieved = storageSourceRepository.getSourceById("saf-room-test")
        assertNotNull(retrieved)
        assertTrue(retrieved is StorageSource.SafDocumentTree)
        assertEquals("Custom FLAC Folder", retrieved?.displayName)

        val all = storageSourceRepository.observeAllSources().first()
        assertEquals(1, all.size)

        storageSourceRepository.deleteSource("saf-room-test")
        assertNull(storageSourceRepository.getSourceById("saf-room-test"))
    }

    @Test
    fun offline_scan_checkpoint_repository_room_integration() = runBlocking {
        // Create source first for foreign key
        val source = StorageSource.MediaStorePrimary(id = "src-cp-test")
        storageSourceRepository.saveSource(source)

        val cp = ScanCheckpoint(
            sourceId = "src-cp-test",
            volumeName = "external_primary",
            lastGeneration = 999L,
            lastScanTimestamp = 8888L,
            scannedFileCount = 200,
            status = ScanStatus.COMPLETED
        )

        scanCheckpointRepository.saveCheckpoint(cp)

        val retrieved = scanCheckpointRepository.getCheckpoint("src-cp-test")
        assertNotNull(retrieved)
        assertEquals(999L, retrieved?.lastGeneration)
        assertEquals(ScanStatus.COMPLETED, retrieved?.status)

        scanCheckpointRepository.resetCheckpoint("src-cp-test")
        assertNull(scanCheckpointRepository.getCheckpoint("src-cp-test"))
    }
}
