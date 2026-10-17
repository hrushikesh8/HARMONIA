package org.harmonia.player

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.entity.FavoriteEntity
import org.harmonia.player.data.local.entity.RatingEntity
import org.harmonia.player.data.local.entity.TrackEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomMigrationV4ToV5AndPersonalizationDaoTest {

    private lateinit var context: Context
    private val testDbName = "migration_v4_v5_test.db"

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
    fun migration_4_to_5_preserves_existing_data_and_creates_personalization_tables_and_indexes() {
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(4) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Create minimal schema v4 tables
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `mobile_tracks` (
                            `id` TEXT NOT NULL,
                            `album_id` TEXT,
                            `artist_id` TEXT,
                            `title` TEXT NOT NULL,
                            `track_number` INTEGER,
                            `disc_number` INTEGER NOT NULL DEFAULT 1,
                            `duration_sec` REAL NOT NULL,
                            `year` INTEGER,
                            `genre` TEXT,
                            `mbid` TEXT,
                            `integrated_lufs` REAL,
                            `true_peak_dbtp` REAL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            `is_provisional` INTEGER NOT NULL DEFAULT 1,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `mobile_local_files` (
                            `id` TEXT NOT NULL,
                            `track_id` TEXT NOT NULL,
                            `source_id` TEXT,
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
                            `verified_at` INTEGER,
                            `added_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`track_id`) REFERENCES `mobile_tracks`(`id`) ON DELETE CASCADE
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

        // Seed v4 data
        val now = 1700000000000L
        rawDb.execSQL("""
            INSERT INTO `mobile_tracks` (`id`, `title`, `duration_sec`, `created_at`, `updated_at`, `is_provisional`)
            VALUES ('trk_1', 'Track One', 180.0, $now, $now, 0),
                   ('trk_2', 'Track Two', 240.0, $now, $now, 0)
        """.trimIndent())

        rawDb.execSQL("""
            INSERT INTO `mobile_local_files` (`id`, `track_id`, `source_id`, `storage_type`, `file_uri`, `file_size`, `file_hash`, `format`, `availability_status`, `added_at`)
            VALUES ('file_1', 'trk_1', 'source_internal', 'INTERNAL_SYNC', 'file:///data/1.flac', 1000, 'hash1', 'flac', 'AVAILABLE', $now)
        """.trimIndent())

        rawDb.execSQL("""
            INSERT INTO `mobile_playlists` (`id`, `name`, `created_at`, `updated_at`)
            VALUES ('pl_1', 'My Playlist', $now, $now)
        """.trimIndent())

        // Execute MIGRATION_4_5
        HarmoniaDatabase.MIGRATION_4_5.migrate(rawDb)

        // Verify existing v4 data preserved
        val trackCursor = rawDb.query("SELECT COUNT(*) FROM `mobile_tracks`")
        assertTrue(trackCursor.moveToFirst())
        assertEquals(2, trackCursor.getInt(0))
        trackCursor.close()

        val fileCursor = rawDb.query("SELECT source_id FROM `mobile_local_files` WHERE id = 'file_1'")
        assertTrue(fileCursor.moveToFirst())
        assertEquals("source_internal", fileCursor.getString(0))
        fileCursor.close()

        val playlistCursor = rawDb.query("SELECT name FROM `mobile_playlists` WHERE id = 'pl_1'")
        assertTrue(playlistCursor.moveToFirst())
        assertEquals("My Playlist", playlistCursor.getString(0))
        playlistCursor.close()

        // Verify mobile_favorites created and accepts valid data
        rawDb.execSQL("INSERT INTO `mobile_favorites` (`track_id`, `client_updated_at`) VALUES ('trk_1', $now)")
        val favCursor = rawDb.query("SELECT track_id, client_updated_at FROM `mobile_favorites` WHERE track_id = 'trk_1'")
        assertTrue(favCursor.moveToFirst())
        assertEquals("trk_1", favCursor.getString(0))
        assertEquals(now, favCursor.getLong(1))
        favCursor.close()

        // Verify mobile_ratings created and accepts valid 1-5 rating
        rawDb.execSQL("INSERT INTO `mobile_ratings` (`track_id`, `rating`, `client_updated_at`) VALUES ('trk_1', 5, $now)")
        val ratCursor = rawDb.query("SELECT rating FROM `mobile_ratings` WHERE track_id = 'trk_1'")
        assertTrue(ratCursor.moveToFirst())
        assertEquals(5, ratCursor.getInt(0))
        ratCursor.close()

        // Verify CHECK constraint (rating >= 1 AND rating <= 5) in SQLite
        try {
            rawDb.execSQL("INSERT INTO `mobile_ratings` (`track_id`, `rating`, `client_updated_at`) VALUES ('trk_2', 6, $now)")
            fail("Expected SQLiteConstraintException when rating > 5")
        } catch (e: SQLiteConstraintException) {
            // Expected
        }

        try {
            rawDb.execSQL("INSERT INTO `mobile_ratings` (`track_id`, `rating`, `client_updated_at`) VALUES ('trk_2', 0, $now)")
            fail("Expected SQLiteConstraintException when rating < 1")
        } catch (e: SQLiteConstraintException) {
            // Expected
        }

        rawDb.close()
        helper.close()
    }

    @Test
    fun cascade_deletion_purges_personalization_when_track_is_deleted() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            HarmoniaDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val trackDao = db.trackDao()
            val personalizationDao = db.personalizationDao()
            val now = System.currentTimeMillis()

            trackDao.upsertTrack(
                TrackEntity(
                    id = "trk_cascade",
                    title = "Cascade Test",
                    durationSec = 200.0,
                    createdAt = now,
                    updatedAt = now
                )
            )

            personalizationDao.upsertFavorite(FavoriteEntity("trk_cascade", now))
            personalizationDao.upsertRating(RatingEntity("trk_cascade", 4, now))

            assertEquals(1, personalizationDao.getFavoriteCount())
            assertEquals(1, personalizationDao.getRatingCount())

            // Delete track from canonical track catalog
            trackDao.deleteTrackById("trk_cascade")

            // Foreign key CASCADE should remove personal annotations
            assertEquals(0, personalizationDao.getFavoriteCount())
            assertEquals(0, personalizationDao.getRatingCount())
            assertNull(personalizationDao.getFavorite("trk_cascade"))
            assertNull(personalizationDao.getRating("trk_cascade"))
        } finally {
            db.close()
        }
    }

    @Test
    fun personalization_dao_idempotent_set_favorite_and_atomic_toggle() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            HarmoniaDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val trackDao = db.trackDao()
            val personalizationDao = db.personalizationDao()
            val now = System.currentTimeMillis()

            trackDao.upsertTrack(
                TrackEntity(
                    id = "trk_fav_ops",
                    title = "Favorite Ops",
                    durationSec = 150.0,
                    createdAt = now,
                    updatedAt = now
                )
            )

            // Idempotent setFavorite(true)
            personalizationDao.setFavorite("trk_fav_ops", true)
            assertTrue(personalizationDao.isFavorite("trk_fav_ops"))
            val fav1 = personalizationDao.getFavorite("trk_fav_ops")
            assertNotNull(fav1)

            // Repeated setFavorite(true) remains favorite
            personalizationDao.setFavorite("trk_fav_ops", true)
            assertTrue(personalizationDao.isFavorite("trk_fav_ops"))

            // Idempotent setFavorite(false) removes row
            personalizationDao.setFavorite("trk_fav_ops", false)
            assertFalse(personalizationDao.isFavorite("trk_fav_ops"))
            assertNull(personalizationDao.getFavorite("trk_fav_ops"))

            // Repeated setFavorite(false) remains no-op
            personalizationDao.setFavorite("trk_fav_ops", false)
            assertFalse(personalizationDao.isFavorite("trk_fav_ops"))

            // Atomic toggle: false -> true
            val toggled1 = personalizationDao.toggleFavorite("trk_fav_ops")
            assertTrue(toggled1)
            assertTrue(personalizationDao.isFavorite("trk_fav_ops"))

            // Atomic toggle: true -> false
            val toggled2 = personalizationDao.toggleFavorite("trk_fav_ops")
            assertFalse(toggled2)
            assertFalse(personalizationDao.isFavorite("trk_fav_ops"))
        } finally {
            db.close()
        }
    }

    @Test
    fun personalization_dao_rating_mutation_and_deterministic_ordering() = runBlocking {
        val db = androidx.room.Room.inMemoryDatabaseBuilder(
            context,
            HarmoniaDatabase::class.java
        ).allowMainThreadQueries().build()

        try {
            val trackDao = db.trackDao()
            val personalizationDao = db.personalizationDao()
            val now = System.currentTimeMillis()

            val tracks = listOf(
                TrackEntity(id = "trk_a", title = "Alpha", durationSec = 100.0, createdAt = now, updatedAt = now),
                TrackEntity(id = "trk_b", title = "Beta", durationSec = 200.0, createdAt = now, updatedAt = now),
                TrackEntity(id = "trk_c", title = "Gamma", durationSec = 300.0, createdAt = now, updatedAt = now)
            )
            trackDao.upsertTracks(tracks)

            // Test setRating with values 1..5
            personalizationDao.setRating("trk_a", 3)
            personalizationDao.setRating("trk_b", 5)
            personalizationDao.setRating("trk_c", 4)

            assertEquals(3, personalizationDao.getRatingCount())
            assertEquals(3, personalizationDao.getRating("trk_a")?.rating)
            assertEquals(5, personalizationDao.getRating("trk_b")?.rating)
            assertEquals(4, personalizationDao.getRating("trk_c")?.rating)

            // Clear rating for trk_a
            personalizationDao.setRating("trk_a", null)
            assertNull(personalizationDao.getRating("trk_a"))
            assertEquals(2, personalizationDao.getRatingCount())

            // Verify observeRatedTracks ordering (rating DESC)
            val ratedTracks = personalizationDao.observeRatedTracks().first()
            assertEquals(2, ratedTracks.size)
            assertEquals("trk_b", ratedTracks[0].track.id) // 5 stars
            assertEquals("trk_c", ratedTracks[1].track.id) // 4 stars

            // Test favorites deterministic ordering (client_updated_at DESC)
            personalizationDao.upsertFavorite(FavoriteEntity("trk_a", 1000L))
            personalizationDao.upsertFavorite(FavoriteEntity("trk_b", 3000L))
            personalizationDao.upsertFavorite(FavoriteEntity("trk_c", 2000L))

            val favoriteTracks = personalizationDao.observeFavoriteTracks().first()
            assertEquals(3, favoriteTracks.size)
            assertEquals("trk_b", favoriteTracks[0].track.id) // 3000L
            assertEquals("trk_c", favoriteTracks[1].track.id) // 2000L
            assertEquals("trk_a", favoriteTracks[2].track.id) // 1000L
        } finally {
            db.close()
        }
    }
}
