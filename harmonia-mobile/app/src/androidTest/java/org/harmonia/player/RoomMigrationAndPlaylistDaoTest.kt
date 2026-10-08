package org.harmonia.player

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.harmonia.player.data.local.HarmoniaDatabase
import org.harmonia.player.data.local.dao.PlaylistDao
import org.harmonia.player.data.local.dao.TrackDao
import org.harmonia.player.data.local.entity.PlaylistEntity
import org.harmonia.player.data.local.entity.PlaylistItemEntity
import org.harmonia.player.data.local.entity.TrackEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RoomMigrationAndPlaylistDaoTest {

    private lateinit var context: Context
    private lateinit var db: HarmoniaDatabase
    private lateinit var playlistDao: PlaylistDao
    private lateinit var trackDao: TrackDao
    private lateinit var albumDao: org.harmonia.player.data.local.dao.AlbumDao
    private lateinit var artistDao: org.harmonia.player.data.local.dao.ArtistDao
    private lateinit var searchRepository: org.harmonia.player.data.local.OfflineSearchRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = HarmoniaDatabase.buildInMemory(context)
        playlistDao = db.playlistDao()
        trackDao = db.trackDao()
        albumDao = db.albumDao()
        artistDao = db.artistDao()
        searchRepository = org.harmonia.player.data.local.OfflineSearchRepository(
            trackDao = trackDao,
            albumDao = albumDao,
            artistDao = artistDao,
            playlistDao = playlistDao
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun playlist_crud_operations_persist_and_update() = runBlocking {
        val playlist = PlaylistEntity(
            id = "pl-test-1",
            name = "Acoustic Sessions",
            description = "Unplugged and acoustic tracks",
            artworkHash = "hash-acoustic",
            createdAt = 1000L,
            updatedAt = 1000L
        )

        // Insert
        playlistDao.insertPlaylist(playlist)
        val retrieved = playlistDao.getPlaylistById("pl-test-1")
        assertNotNull(retrieved)
        assertEquals("Acoustic Sessions", retrieved?.name)
        assertEquals("Unplugged and acoustic tracks", retrieved?.description)

        // Update
        val updated = playlist.copy(name = "Acoustic & Live", updatedAt = 2000L)
        playlistDao.updatePlaylist(updated)
        val afterUpdate = playlistDao.getPlaylistById("pl-test-1")
        assertEquals("Acoustic & Live", afterUpdate?.name)
        assertEquals(2000L, afterUpdate?.updatedAt)

        // Delete
        playlistDao.deletePlaylist("pl-test-1")
        assertNull(playlistDao.getPlaylistById("pl-test-1"))
    }

    @Test
    fun playlist_items_maintain_deterministic_position_ordering() = runBlocking {
        val playlist = PlaylistEntity(id = "pl-order", name = "Ordering", createdAt = 0L, updatedAt = 0L)
        playlistDao.insertPlaylist(playlist)

        // Insert tracks to satisfy foreign key
        trackDao.upsertTrack(TrackEntity(id = "trk-0", title = "Zero", durationSec = 100.0, createdAt = 0L, updatedAt = 0L))
        trackDao.upsertTrack(TrackEntity(id = "trk-1", title = "One", durationSec = 100.0, createdAt = 0L, updatedAt = 0L))
        trackDao.upsertTrack(TrackEntity(id = "trk-2", title = "Two", durationSec = 100.0, createdAt = 0L, updatedAt = 0L))

        // Insert out of position order
        playlistDao.insertItem(PlaylistItemEntity(id = "item-2", playlistId = "pl-order", trackId = "trk-2", position = 2, addedAt = 10L))
        playlistDao.insertItem(PlaylistItemEntity(id = "item-0", playlistId = "pl-order", trackId = "trk-0", position = 0, addedAt = 10L))
        playlistDao.insertItem(PlaylistItemEntity(id = "item-1", playlistId = "pl-order", trackId = "trk-1", position = 1, addedAt = 10L))

        val ordered = playlistDao.getItemsForPlaylist("pl-order")
        assertEquals(3, ordered.size)
        assertEquals(0, ordered[0].position)
        assertEquals("trk-0", ordered[0].trackId)
        assertEquals(1, ordered[1].position)
        assertEquals("trk-1", ordered[1].trackId)
        assertEquals(2, ordered[2].position)
        assertEquals("trk-2", ordered[2].trackId)
    }

    @Test
    fun unique_constraint_rejects_duplicate_positions() = runBlocking {
        val playlist = PlaylistEntity(id = "pl-unique", name = "Unique", createdAt = 0L, updatedAt = 0L)
        playlistDao.insertPlaylist(playlist)

        trackDao.upsertTrack(TrackEntity(id = "trk-a", title = "A", durationSec = 100.0, createdAt = 0L, updatedAt = 0L))
        trackDao.upsertTrack(TrackEntity(id = "trk-b", title = "B", durationSec = 100.0, createdAt = 0L, updatedAt = 0L))

        playlistDao.insertItem(PlaylistItemEntity(id = "item-a", playlistId = "pl-unique", trackId = "trk-a", position = 0, addedAt = 10L))

        try {
            // Attempt inserting duplicate position 0 for the same playlist
            playlistDao.insertItem(PlaylistItemEntity(id = "item-b", playlistId = "pl-unique", trackId = "trk-b", position = 0, addedAt = 20L))
            fail("Expected SQLiteConstraintException for duplicate (playlistId, position)")
        } catch (e: SQLiteConstraintException) {
            // Expected unique constraint violation
            assertTrue(e.message?.contains("UNIQUE") == true || e.message?.contains("constraint") == true)
        }
    }

    @Test
    fun deleting_playlist_cascades_and_removes_playlist_items() = runBlocking {
        val playlist = PlaylistEntity(id = "pl-cascade", name = "Cascade Test", createdAt = 0L, updatedAt = 0L)
        playlistDao.insertPlaylist(playlist)

        trackDao.upsertTrack(TrackEntity(id = "trk-cas", title = "Cascade Track", durationSec = 100.0, createdAt = 0L, updatedAt = 0L))
        playlistDao.insertItem(PlaylistItemEntity(id = "item-cas", playlistId = "pl-cascade", trackId = "trk-cas", position = 0, addedAt = 10L))

        assertEquals(1, playlistDao.getItemsForPlaylist("pl-cascade").size)

        // Delete playlist
        playlistDao.deletePlaylist("pl-cascade")

        // Items must be cleared via CASCADE
        assertEquals(0, playlistDao.getItemsForPlaylist("pl-cascade").size)
    }

    @Test
    fun migration_1_to_2_preserves_m1_m2_data_and_creates_playlist_tables() {
        val dbFile = File(context.cacheDir, "test_migration_v1.db")
        if (dbFile.exists()) dbFile.delete()

        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbFile.absolutePath)
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // M1 / M2 Schema version 1 DDL
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `mobile_tracks` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `album_id` TEXT,
                            `artist_name` TEXT,
                            `album_title` TEXT,
                            `track_number` INTEGER,
                            `disc_number` INTEGER,
                            `duration_sec` REAL NOT NULL,
                            `integrated_lufs` REAL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `mobile_albums` (
                            `id` TEXT NOT NULL,
                            `title` TEXT NOT NULL,
                            `artist_name` TEXT,
                            `year` INTEGER,
                            `artwork_hash` TEXT,
                            `track_count` INTEGER NOT NULL,
                            `created_at` INTEGER NOT NULL,
                            `updated_at` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `listening_events` (
                            `id` TEXT NOT NULL,
                            `event_type` TEXT NOT NULL,
                            `track_id` TEXT NOT NULL,
                            `local_file_id` TEXT,
                            `position_ms` INTEGER NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `is_synced` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `playback_state` (
                            `id` INTEGER NOT NULL,
                            `current_track_id` TEXT,
                            `position_ms` INTEGER NOT NULL,
                            `is_playing` INTEGER NOT NULL,
                            `queue_index` INTEGER NOT NULL,
                            `playback_speed` REAL NOT NULL,
                            `last_updated` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val helper = FrameworkSQLiteOpenHelperFactory().create(config)
        val rawDb = helper.writableDatabase

        // Insert M1 / M2 data into v1 database
        rawDb.execSQL("INSERT INTO `mobile_tracks` VALUES ('track-m1', 'Teardrop', 'album-m1', 'Massive Attack', 'Mezzanine', 3, 1, 330.0, -14.2, 1000, 1000)")
        rawDb.execSQL("INSERT INTO `mobile_albums` VALUES ('album-m1', 'Mezzanine', 'Massive Attack', 1998, 'art-hash-1', 11, 1000, 1000)")
        rawDb.execSQL("INSERT INTO `listening_events` VALUES ('evt-m2', 'STARTED', 'track-m1', 'file-1', 0, 5000, 0)")
        rawDb.execSQL("INSERT INTO `playback_state` VALUES (1, 'track-m1', 45000, 1, 0, 1.0, 6000)")

        // Apply MIGRATION_1_2
        HarmoniaDatabase.MIGRATION_1_2.migrate(rawDb)

        // Verify M1 and M2 data survives migration
        val trackCursor = rawDb.query("SELECT id, title, artist_name FROM `mobile_tracks` WHERE id = 'track-m1'")
        assertTrue(trackCursor.moveToFirst())
        assertEquals("Teardrop", trackCursor.getString(1))
        assertEquals("Massive Attack", trackCursor.getString(2))
        trackCursor.close()

        val albumCursor = rawDb.query("SELECT id, title, year FROM `mobile_albums` WHERE id = 'album-m1'")
        assertTrue(albumCursor.moveToFirst())
        assertEquals("Mezzanine", albumCursor.getString(1))
        assertEquals(1998, albumCursor.getInt(2))
        albumCursor.close()

        val eventCursor = rawDb.query("SELECT id, event_type, track_id FROM `listening_events` WHERE id = 'evt-m2'")
        assertTrue(eventCursor.moveToFirst())
        assertEquals("STARTED", eventCursor.getString(1))
        assertEquals("track-m1", eventCursor.getString(2))
        eventCursor.close()

        val stateCursor = rawDb.query("SELECT current_track_id, position_ms FROM `playback_state` WHERE id = 1")
        assertTrue(stateCursor.moveToFirst())
        assertEquals("track-m1", stateCursor.getString(0))
        assertEquals(45000L, stateCursor.getLong(1))
        stateCursor.close()

        // Verify newly created playlist tables accept insertions
        rawDb.execSQL("INSERT INTO `mobile_playlists` VALUES ('mig-pl', 'Post Migration Playlist', 'Verified', 'hash-m3', 7000, 7000)")
        rawDb.execSQL("INSERT INTO `mobile_playlist_items` VALUES ('mig-item', 'mig-pl', 'track-m1', 0, 7000)")

        val playlistCursor = rawDb.query("SELECT name FROM `mobile_playlists` WHERE id = 'mig-pl'")
        assertTrue(playlistCursor.moveToFirst())
        assertEquals("Post Migration Playlist", playlistCursor.getString(0))
        playlistCursor.close()

        val itemCursor = rawDb.query("SELECT track_id, position FROM `mobile_playlist_items` WHERE id = 'mig-item'")
        assertTrue(itemCursor.moveToFirst())
        assertEquals("track-m1", itemCursor.getString(0))
        assertEquals(0, itemCursor.getInt(1))
        itemCursor.close()

        rawDb.close()
        dbFile.delete()
    }

    @Test
    fun track_dao_search_matches_title_artist_and_album_case_insensitively() = runBlocking {
        artistDao.upsertArtist(org.harmonia.player.data.local.entity.ArtistEntity(id = "art-rh", name = "Radiohead", sortName = "Radiohead", createdAt = 100L, updatedAt = 100L))
        artistDao.upsertArtist(org.harmonia.player.data.local.entity.ArtistEntity(id = "art-ma", name = "Massive Attack", sortName = "Massive Attack", createdAt = 200L, updatedAt = 200L))
        albumDao.upsertAlbum(org.harmonia.player.data.local.entity.AlbumEntity(id = "alb-ok", title = "OK Computer", artistId = "art-rh", createdAt = 100L, updatedAt = 100L))
        albumDao.upsertAlbum(org.harmonia.player.data.local.entity.AlbumEntity(id = "alb-mez", title = "Mezzanine", artistId = "art-ma", createdAt = 200L, updatedAt = 200L))

        trackDao.upsertTrack(TrackEntity(id = "trk-s1", title = "Paranoid Android", artistId = "art-rh", albumId = "alb-ok", durationSec = 380.0, createdAt = 100L, updatedAt = 100L))
        trackDao.upsertTrack(TrackEntity(id = "trk-s2", title = "Karma Police", artistId = "art-rh", albumId = "alb-ok", durationSec = 260.0, createdAt = 200L, updatedAt = 200L))
        trackDao.upsertTrack(TrackEntity(id = "trk-s3", title = "Teardrop", artistId = "art-ma", albumId = "alb-mez", durationSec = 330.0, createdAt = 300L, updatedAt = 300L))

        // Search by title (partial, case-insensitive)
        val titleMatch = trackDao.searchTracks("paranoid").first()
        assertEquals(1, titleMatch.size)
        assertEquals("trk-s1", titleMatch[0].track.id)

        // Search by artist (partial, uppercase)
        val artistMatch = trackDao.searchTracks("RADIOHEAD").first()
        assertEquals(2, artistMatch.size)

        // Search by album
        val albumMatch = trackDao.searchTracks("mezzanine").first()
        assertEquals(1, albumMatch.size)
        assertEquals("trk-s3", albumMatch[0].track.id)
    }

    @Test
    fun album_dao_search_matches_title_and_artist_case_insensitively() = runBlocking {
        artistDao.upsertArtist(org.harmonia.player.data.local.entity.ArtistEntity(id = "art-s1", name = "Radiohead", sortName = "Radiohead", createdAt = 100L, updatedAt = 100L))
        artistDao.upsertArtist(org.harmonia.player.data.local.entity.ArtistEntity(id = "art-s2", name = "Massive Attack", sortName = "Massive Attack", createdAt = 200L, updatedAt = 200L))
        albumDao.upsertAlbum(org.harmonia.player.data.local.entity.AlbumEntity(id = "alb-s1", title = "OK Computer", artistId = "art-s1", createdAt = 100L, updatedAt = 100L))
        albumDao.upsertAlbum(org.harmonia.player.data.local.entity.AlbumEntity(id = "alb-s2", title = "Mezzanine", artistId = "art-s2", createdAt = 200L, updatedAt = 200L))

        val matchTitle = albumDao.searchAlbums("computer").first()
        assertEquals(1, matchTitle.size)
        assertEquals("alb-s1", matchTitle[0].id)

        val matchArtist = albumDao.searchAlbums("massive").first()
        assertEquals(1, matchArtist.size)
        assertEquals("alb-s2", matchArtist[0].id)
    }

    @Test
    fun artist_dao_search_matches_name_and_sort_name_case_insensitively() = runBlocking {
        artistDao.upsertArtist(org.harmonia.player.data.local.entity.ArtistEntity(id = "art-s1", name = "The Beatles", sortName = "Beatles, The", createdAt = 100L, updatedAt = 100L))
        artistDao.upsertArtist(org.harmonia.player.data.local.entity.ArtistEntity(id = "art-s2", name = "Pink Floyd", sortName = "Pink Floyd", createdAt = 200L, updatedAt = 200L))

        // Match by display name
        val matchName = artistDao.searchArtists("floyd").first()
        assertEquals(1, matchName.size)
        assertEquals("art-s2", matchName[0].id)

        // Match by sort name
        val matchSort = artistDao.searchArtists("beatles").first()
        assertEquals(1, matchSort.size)
        assertEquals("art-s1", matchSort[0].id)
    }

    @Test
    fun playlist_dao_search_matches_name_and_description_case_insensitively() = runBlocking {
        playlistDao.insertPlaylist(PlaylistEntity(id = "pl-s1", name = "Morning Focus", description = "Ambient instrumental focus", createdAt = 100L, updatedAt = 100L))
        playlistDao.insertPlaylist(PlaylistEntity(id = "pl-s2", name = "Night Drive", description = "Synthwave and electronic", createdAt = 200L, updatedAt = 200L))

        val matchName = playlistDao.searchPlaylists("focus").first()
        assertEquals(1, matchName.size)
        assertEquals("pl-s1", matchName[0].id)

        val matchDesc = playlistDao.searchPlaylists("synthwave").first()
        assertEquals(1, matchDesc.size)
        assertEquals("pl-s2", matchDesc[0].id)
    }

    @Test
    fun offline_search_repository_executes_categorized_queries_against_room() = runBlocking {
        artistDao.upsertArtist(org.harmonia.player.data.local.entity.ArtistEntity(id = "art-c1", name = "Acoustic Project", sortName = "Acoustic Project", createdAt = 100L, updatedAt = 100L))
        albumDao.upsertAlbum(org.harmonia.player.data.local.entity.AlbumEntity(id = "alb-c1", title = "Acoustic Vibe", artistId = "art-c1", createdAt = 100L, updatedAt = 100L))
        trackDao.upsertTrack(TrackEntity(id = "trk-c1", title = "Acoustic Sun", artistId = "art-c1", albumId = "alb-c1", durationSec = 200.0, createdAt = 100L, updatedAt = 100L))
        playlistDao.insertPlaylist(PlaylistEntity(id = "pl-c1", name = "Acoustic Collection", description = "Peaceful", createdAt = 100L, updatedAt = 100L))

        val tracks = searchRepository.searchTracks("acoustic").first()
        val albums = searchRepository.searchAlbums("acoustic").first()
        val artists = searchRepository.searchArtists("acoustic").first()
        val playlists = searchRepository.searchPlaylists("acoustic").first()

        assertEquals(1, tracks.size)
        assertEquals("Acoustic Sun", tracks[0].title)
        assertEquals(1, albums.size)
        assertEquals("Acoustic Vibe", albums[0].title)
        assertEquals(1, artists.size)
        assertEquals("Acoustic Project", artists[0].name)
        assertEquals(1, playlists.size)
        assertEquals("Acoustic Collection", playlists[0].name)
    }
}
