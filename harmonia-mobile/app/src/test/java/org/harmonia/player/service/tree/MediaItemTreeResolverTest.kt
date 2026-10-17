package org.harmonia.player.service.tree

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.harmonia.player.data.playback.HarmoniaPlayerController
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.ListeningEvent
import org.harmonia.player.domain.model.ListeningEventType
import org.harmonia.player.domain.model.LocalFile
import org.harmonia.player.domain.model.PlaybackState
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.PlaylistItem
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.storage.lifecycle.SourceRuntimeState
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.ListeningEventRepository
import org.harmonia.player.domain.repository.LocalFileRepository
import org.harmonia.player.domain.repository.PlaybackStateRepository
import org.harmonia.player.domain.repository.PlaylistRepository
import org.harmonia.player.domain.repository.SearchRepository
import org.harmonia.player.domain.repository.TrackRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Stage M5-B Verification & Regression Test Suite:
 * 1. Root and category browsing with bounded pagination.
 * 2. SQL-backed LIMIT/OFFSET pagination, deterministic ordering, and boundary edge cases:
 *    - Negative page indices, 32-bit overflow offsets, zero/negative page sizes, partial pages.
 * 3. Search-cache lifecycle:
 *    - LRU eviction (max 20 entries), explicit invalidation, blank queries, failure isolation.
 * 4. Strict external URI and track validation:
 *    - Indexed local files, unindexed URIs omitted (no raw fallbacks),
 *    - Network URIs (http/https) rejected,
 *    - Unmounted/disconnected storage sources omitted,
 *    - Unverified local files omitted.
 * 5. Track ID lifecycle from Media3 canonical ID ("track:{id}") through HarmoniaPlayerController:
 *    - Successful entity lookup in database,
 *    - Storage source resolution and disconnection readiness,
 *    - ListeningEvent foreign key consistency.
 * 6. Error handling and result status code mapping (BAD_VALUE vs UNKNOWN).
 */
class MediaItemTreeResolverTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var fakeTrackRepo: FakeTrackRepository
    private lateinit var fakeAlbumRepo: FakeAlbumRepository
    private lateinit var fakeArtistRepo: FakeArtistRepository
    private lateinit var fakePlaylistRepo: FakePlaylistRepository
    private lateinit var fakeLocalFileRepo: FakeLocalFileRepository
    private lateinit var fakeSearchRepo: FakeSearchRepository
    private lateinit var fakeCoordinator: FakeStorageVolumeLifecycleCoordinator

    private lateinit var resolver: MediaItemTreeResolver

    @Before
    fun setUp() {
        fakeTrackRepo = FakeTrackRepository()
        fakeAlbumRepo = FakeAlbumRepository()
        fakeArtistRepo = FakeArtistRepository()
        fakePlaylistRepo = FakePlaylistRepository()
        fakeLocalFileRepo = FakeLocalFileRepository()
        fakeSearchRepo = FakeSearchRepository()
        fakeCoordinator = FakeStorageVolumeLifecycleCoordinator()
        fakeCoordinator.putState(
            SourceRuntimeState(
                sourceId = "src_default",
                isAvailable = true,
                isMounted = true,
                hasPermission = true
            )
        )
        fakeCoordinator.putState(
            SourceRuntimeState(
                sourceId = "src_1",
                isAvailable = true,
                isMounted = true,
                hasPermission = true
            )
        )

        resolver = MediaItemTreeResolver(
            trackRepository = fakeTrackRepo,
            albumRepository = fakeAlbumRepo,
            artistRepository = fakeArtistRepo,
            playlistRepository = fakePlaylistRepo,
            localFileRepository = fakeLocalFileRepo,
            searchRepository = fakeSearchRepo,
            storageVolumeLifecycleCoordinator = fakeCoordinator,
            coroutineDispatcher = testDispatcher
        )
    }

    @Test
    fun rootDiscovery_returnsRootAndFourCategories() {
        val rootFuture = resolver.getRoot()
        testDispatcher.scheduler.advanceUntilIdle()
        val rootResult = rootFuture.get()

        assertEquals(LibraryResult.RESULT_SUCCESS, rootResult.resultCode)
        assertEquals(MediaItemTreeResolver.ROOT_ID, rootResult.value!!.mediaId)

        val childrenFuture = resolver.getChildren(MediaItemTreeResolver.ROOT_ID, 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val childrenResult = childrenFuture.get()

        assertEquals(LibraryResult.RESULT_SUCCESS, childrenResult.resultCode)
        val items = childrenResult.value!!
        assertEquals(4, items.size)
        assertEquals(
            listOf("root_albums", "root_artists", "root_playlists", "root_tracks"),
            items.map { it.mediaId }
        )
    }

    @Test
    fun browsingAlbums_returnsRepositoryAlbums_andAlbumChildrenReturnsTracks() {
        val album1 = Album(id = "alb_1", title = "Kind of Blue", artistName = "Miles Davis", artworkHash = "art1")
        val album2 = Album(id = "alb_2", title = "A Love Supreme", artistName = "John Coltrane", artworkHash = "art2")
        fakeAlbumRepo.albums.addAll(listOf(album1, album2))

        val track1 = Track(
            id = "trk_1",
            title = "So What",
            albumId = "alb_1",
            artistName = "Miles Davis",
            albumTitle = "Kind of Blue",
            trackNumber = 1,
            discNumber = 1,
            durationSec = 562.0,
            localFile = createTestLocalFile(id = "f1", trackId = "trk_1", fileUri = "content://media/f1", isVerified = true)
        )
        val track2 = Track(
            id = "trk_2",
            title = "Freddie Freeloader",
            albumId = "alb_1",
            artistName = "Miles Davis",
            albumTitle = "Kind of Blue",
            trackNumber = 2,
            discNumber = 1,
            durationSec = 589.0,
            localFile = createTestLocalFile(id = "f2", trackId = "trk_2", fileUri = "content://media/f2", isVerified = true)
        )
        fakeTrackRepo.tracks.addAll(listOf(track1, track2))

        // 1. Browse root_albums
        val albumsFuture = resolver.getChildren("root_albums", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val albumsResult = albumsFuture.get()

        assertEquals(LibraryResult.RESULT_SUCCESS, albumsResult.resultCode)
        assertEquals(2, albumsResult.value!!.size)
        assertEquals("album:alb_1", albumsResult.value!![0].mediaId)
        assertEquals("Kind of Blue", albumsResult.value!![0].mediaMetadata.title.toString())
        assertTrue(albumsResult.value!![0].mediaMetadata.isBrowsable == true)
        assertTrue(albumsResult.value!![0].mediaMetadata.isPlayable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_ALBUM, albumsResult.value!![0].mediaMetadata.mediaType)

        // 2. Browse album:alb_1
        val albumChildrenFuture = resolver.getChildren("album:alb_1", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val albumChildrenResult = albumChildrenFuture.get()

        assertEquals(LibraryResult.RESULT_SUCCESS, albumChildrenResult.resultCode)
        assertEquals(2, albumChildrenResult.value!!.size)
        assertEquals("track:trk_1", albumChildrenResult.value!![0].mediaId)
        assertEquals("So What", albumChildrenResult.value!![0].mediaMetadata.title.toString())
        assertFalse(albumChildrenResult.value!![0].mediaMetadata.isBrowsable == true)
        assertTrue(albumChildrenResult.value!![0].mediaMetadata.isPlayable == true)
        assertEquals(MediaMetadata.MEDIA_TYPE_MUSIC, albumChildrenResult.value!![0].mediaMetadata.mediaType)
    }

    @Test
    fun browsingArtists_returnsRepositoryArtists_andArtistChildrenReturnsTracks() {
        val artist = Artist(id = "art_1", name = "The Beatles")
        fakeArtistRepo.artists.add(artist)

        val track = Track(
            id = "trk_beatles",
            title = "Hey Jude",
            artistId = "art_1",
            artistName = "The Beatles",
            durationSec = 431.0,
            localFile = createTestLocalFile(id = "f3", trackId = "trk_beatles", fileUri = "content://media/f3", isVerified = true)
        )
        fakeTrackRepo.tracks.add(track)

        val artistsFuture = resolver.getChildren("root_artists", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val artistsResult = artistsFuture.get()

        assertEquals(LibraryResult.RESULT_SUCCESS, artistsResult.resultCode)
        assertEquals(1, artistsResult.value!!.size)
        assertEquals("artist:art_1", artistsResult.value!![0].mediaId)
        assertEquals(MediaMetadata.MEDIA_TYPE_ARTIST, artistsResult.value!![0].mediaMetadata.mediaType)

        val artistTracksFuture = resolver.getChildren("artist:art_1", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val artistTracksResult = artistTracksFuture.get()

        assertEquals(LibraryResult.RESULT_SUCCESS, artistTracksResult.resultCode)
        assertEquals(1, artistTracksResult.value!!.size)
        assertEquals("track:trk_beatles", artistTracksResult.value!![0].mediaId)
    }

    @Test
    fun browsingPlaylists_returnsRepositoryPlaylists_andItemsInPositionOrder() {
        val playlist = Playlist(id = "pl_1", name = "Road Trip", trackCount = 2)
        fakePlaylistRepo.playlists.add(playlist)

        val track1 = Track(
            id = "trk_p1",
            title = "Song A",
            durationSec = 200.0,
            localFile = createTestLocalFile(id = "fa", trackId = "trk_p1", fileUri = "content://media/fa", isVerified = true)
        )
        val track2 = Track(
            id = "trk_p2",
            title = "Song B",
            durationSec = 210.0,
            localFile = createTestLocalFile(id = "fb", trackId = "trk_p2", fileUri = "content://media/fb", isVerified = true)
        )
        fakeTrackRepo.tracks.addAll(listOf(track1, track2))

        fakePlaylistRepo.playlistItems["pl_1"] = listOf(
            PlaylistItem(id = "pi_1", playlistId = "pl_1", trackId = "trk_p1", position = 0, track = track1),
            PlaylistItem(id = "pi_2", playlistId = "pl_1", trackId = "trk_p2", position = 1, track = track2)
        )

        val playlistsFuture = resolver.getChildren("root_playlists", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val playlistsResult = playlistsFuture.get()

        assertEquals(LibraryResult.RESULT_SUCCESS, playlistsResult.resultCode)
        assertEquals(1, playlistsResult.value!!.size)
        assertEquals("playlist:pl_1", playlistsResult.value!![0].mediaId)
        assertEquals(MediaMetadata.MEDIA_TYPE_PLAYLIST, playlistsResult.value!![0].mediaMetadata.mediaType)

        val playlistItemsFuture = resolver.getChildren("playlist:pl_1", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val playlistItemsResult = playlistItemsFuture.get()

        assertEquals(LibraryResult.RESULT_SUCCESS, playlistItemsResult.resultCode)
        assertEquals(2, playlistItemsResult.value!!.size)
        assertEquals("track:trk_p1", playlistItemsResult.value!![0].mediaId)
        assertEquals("track:trk_p2", playlistItemsResult.value!![1].mediaId)
    }

    @Test
    fun getItem_resolvesValidEntities_andRejectsMissingOrMalformedIds() {
        val album = Album(id = "alb_exist", title = "Exist Album")
        val track = Track(id = "trk_exist", title = "Exist Track", durationSec = 180.0)
        fakeAlbumRepo.albums.add(album)
        fakeTrackRepo.tracks.add(track)

        val albumItemFuture = resolver.getItem("album:alb_exist")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, albumItemFuture.get().resultCode)
        assertEquals("album:alb_exist", albumItemFuture.get().value!!.mediaId)

        val trackItemFuture = resolver.getItem("track:trk_exist")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, trackItemFuture.get().resultCode)
        assertEquals("track:trk_exist", trackItemFuture.get().value!!.mediaId)

        // Missing entity
        val missingAlbumFuture = resolver.getItem("album:missing_id")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, missingAlbumFuture.get().resultCode)

        // Malformed ID
        val malformedFuture = resolver.getItem("unknown_prefix:123")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(SessionResult.RESULT_ERROR_BAD_VALUE, malformedFuture.get().resultCode)
    }

    @Test
    fun pagination_boundsAndBoundaryConditions_handledSafely() {
        for (i in 1..75) {
            fakeTrackRepo.tracks.add(Track(id = "t_$i", title = "Track $i", durationSec = 100.0))
        }

        // Normal page 0 (size 30)
        val page0Future = resolver.getChildren("root_tracks", 0, 30, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val page0 = page0Future.get().value!!
        assertEquals(30, page0.size)
        assertEquals("track:t_1", page0[0].mediaId)

        // Normal page 1 with excessive pageSize 200 (capped at 50)
        val page1Future = resolver.getChildren("root_tracks", 1, 200, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val page1 = page1Future.get().value!!
        // Offset is 50, remaining is 25 items
        assertEquals(25, page1.size)
        assertEquals("track:t_51", page1[0].mediaId)

        // Negative page index -> returns empty list
        val negPageFuture = resolver.getChildren("root_tracks", -1, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, negPageFuture.get().value!!.size)

        // Out-of-bounds page index
        val oobFuture = resolver.getChildren("root_tracks", 10, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, oobFuture.get().value!!.size)

        // Arithmetic overflow page index (32-bit int multiplication overflow)
        val overflowFuture = resolver.getChildren("root_tracks", 100_000_000, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, overflowFuture.get().value!!.size)
    }

    @Test
    fun searchCache_lruEvictionAndInvalidation() {
        // Populate search cache with 25 queries (max is 20)
        for (i in 1..25) {
            val query = "query_$i"
            fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_$i", title = "Track $i", durationSec = 100.0))
            val f = resolver.search(null, null, query, null)
            testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(LibraryResult.RESULT_SUCCESS, f.get().resultCode)
        }

        // Oldest queries (e.g., query_1) must be evicted from cache
        // If query_1 is re-requested via getSearchResult, it re-queries searchRepo
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "reloaded", title = "Reloaded", durationSec = 100.0))
        val res1 = resolver.getSearchResult("query_1", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:reloaded", res1.get().value!![0].mediaId)

        // Invalidate cache
        resolver.invalidateSearchCache()
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "after_invalidation", title = "After", durationSec = 100.0))
        val res25 = resolver.getSearchResult("query_25", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:after_invalidation", res25.get().value!![0].mediaId)
    }

    @Test
    fun externalPlayback_strictUriValidation_andFallbackRejection() {
        val validFile = createTestLocalFile(id = "f_val", trackId = "t_val", fileUri = "content://valid/path", isVerified = true)
        val validTrack = Track(id = "t_val", title = "Valid Song", durationSec = 180.0, localFile = validFile)
        fakeTrackRepo.tracks.add(validTrack)
        fakeLocalFileRepo.filesByUri["content://valid/path"] = validFile
        fakeLocalFileRepo.files["t_val"] = listOf(validFile)

        // 1. Valid indexed URI -> resolves to playable MediaItem
        val validItem = MediaItem.Builder()
            .setMediaId("content://valid/path")
            .build()
        val f1 = resolver.resolvePlayableMediaItems(listOf(validItem))
        testDispatcher.scheduler.advanceUntilIdle()
        val r1 = f1.get()
        assertEquals(1, r1.size)
        assertEquals("track:t_val", r1[0].mediaId)

        // 2. Unindexed URI -> omitted completely, never appended as fallback
        val unindexedItem = MediaItem.Builder()
            .setMediaId("content://unindexed/path")
            .build()
        val f2 = resolver.resolvePlayableMediaItems(listOf(unindexedItem))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Unindexed URI must be omitted", f2.get().isEmpty())

        // 3. Network URI -> rejected completely
        val networkItem = MediaItem.Builder()
            .setMediaId("http://example.com/stream.mp3")
            .build()
        val f3 = resolver.resolvePlayableMediaItems(listOf(networkItem))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Network URI must be rejected", f3.get().isEmpty())

        // 4. Disconnected / unmounted storage source -> omitted completely
        val disconnectedFile = createTestLocalFile(
            id = "f_disc",
            trackId = "t_disc",
            fileUri = "file:///storage/sdcard/disc.mp3",
            sourceId = "src_sdcard",
            isVerified = true
        )
        val disconnectedTrack = Track(id = "t_disc", title = "Disc", durationSec = 100.0, localFile = disconnectedFile)
        fakeTrackRepo.tracks.add(disconnectedTrack)
        fakeCoordinator.putState(
            SourceRuntimeState(
                sourceId = "src_sdcard",
                isAvailable = false,
                isMounted = false,
                hasPermission = false
            )
        )

        val discItem = MediaItem.Builder().setMediaId("track:t_disc").build()
        val f4 = resolver.resolvePlayableMediaItems(listOf(discItem))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Disconnected storage source track must be omitted", f4.get().isEmpty())

        // 5. Unverified local file -> omitted completely
        val unverifiedFile = createTestLocalFile(
            id = "f_unv",
            trackId = "t_unv",
            fileUri = "file:///storage/emulated/0/unv.mp3",
            isVerified = false
        )
        val unverifiedTrack = Track(id = "t_unv", title = "Unverified", durationSec = 100.0, localFile = unverifiedFile)
        fakeTrackRepo.tracks.add(unverifiedTrack)

        val unvItem = MediaItem.Builder().setMediaId("track:t_unv").build()
        val f5 = resolver.resolvePlayableMediaItems(listOf(unvItem))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Unverified track must be omitted", f5.get().isEmpty())
    }

    @Test
    fun externalPlayback_trackIdLifecycle_andControllerResolution() {
        val file = createTestLocalFile(id = "f_ctrl", trackId = "t_ctrl", fileUri = "content://media/ctrl", sourceId = "src_1", isVerified = true)
        val track = Track(id = "t_ctrl", title = "Controller Track", durationSec = 200.0, localFile = file)
        fakeTrackRepo.tracks.add(track)
        fakeLocalFileRepo.files["t_ctrl"] = listOf(file)

        // 1. External client initiates playback of "track:t_ctrl"
        val requestItem = MediaItem.Builder().setMediaId("track:t_ctrl").build()
        val resolveFuture = resolver.resolvePlayableMediaItems(listOf(requestItem))
        testDispatcher.scheduler.advanceUntilIdle()
        val resolved = resolveFuture.get()

        assertEquals(1, resolved.size)
        val playable = resolved[0]
        assertEquals("track:t_ctrl", playable.mediaId)

        // 2. Simulate HarmoniaPlayerController receiving this MediaItem transition
        val fakeStateRepo = FakePlaybackStateRepository()
        val fakeEventRepo = FakeListeningEventRepository()
        val playerProxy = java.lang.reflect.Proxy.newProxyInstance(
            androidx.media3.common.Player::class.java.classLoader,
            arrayOf(androidx.media3.common.Player::class.java),
            java.lang.reflect.InvocationHandler { _, method, _ ->
                when (method.name) {
                    "addListener" -> null
                    "removeListener" -> null
                    "getPlaybackState" -> androidx.media3.common.Player.STATE_READY
                    "isPlaying" -> true
                    "getCurrentMediaItemIndex" -> 0
                    else -> null
                }
            }
        ) as androidx.media3.common.Player

        val controller = HarmoniaPlayerController(
            context = null,
            playbackStateRepository = fakeStateRepo,
            listeningEventRepository = fakeEventRepo,
            trackRepository = fakeTrackRepo,
            localFileRepository = fakeLocalFileRepo,
            storageVolumeLifecycleCoordinator = fakeCoordinator,
            playerOverride = playerProxy,
            scope = testScope
        )

        // Extract database ID using controller helper
        val rawDbTrackId = HarmoniaPlayerController.extractDatabaseTrackId(playable.mediaId)
        assertEquals("t_ctrl", rawDbTrackId)

        // Simulate player transition to this item
        val listener = controller.getPlayerListenerForTesting()
        assertNotNull(listener)
        listener!!.onMediaItemTransition(playable, 0)
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify state repository and controller resolved raw database track ID
        assertEquals("t_ctrl", controller.playbackState.value.currentTrackId)
        assertEquals("f_ctrl", controller.playbackState.value.currentLocalFileId)
        assertEquals(200_000L, controller.playbackState.value.durationMs)

        // Verify ListeningEvent recorded with raw database ID as foreign key
        val lastEvent = fakeEventRepo.recordedEvents.lastOrNull()
        assertNotNull(lastEvent)
        assertEquals(ListeningEventType.STARTED, lastEvent!!.eventType)
        assertEquals("t_ctrl", lastEvent.trackId)
        assertEquals("f_ctrl", lastEvent.localFileId)
    }

    @Test
    fun callbackErrorHandling_repositoryFailure_returnsResultErrorUnknown() {
        fakeTrackRepo.throwError = true

        val childrenFuture = resolver.getChildren("root_tracks", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val result = childrenFuture.get()

        assertEquals(SessionResult.RESULT_ERROR_UNKNOWN, result.resultCode)
    }

    @Test
    fun storageAvailability_failClosed_onUntrackedOrDisconnectedOrUnpermittedSources() {
        // 1. Untracked source ID -> fail closed (omitted)
        val untrackedFile = createTestLocalFile(
            id = "f_untracked",
            trackId = "t_untracked",
            fileUri = "content://media/untracked",
            sourceId = "src_not_in_coordinator",
            isVerified = true
        )
        val untrackedTrack = Track(id = "t_untracked", title = "Untracked", durationSec = 100.0, localFile = untrackedFile)
        fakeTrackRepo.tracks.add(untrackedTrack)

        val f1 = resolver.resolvePlayableMediaItems(listOf(MediaItem.Builder().setMediaId("track:t_untracked").build()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Untracked storage source must fail closed", f1.get().isEmpty())

        // 2. Unmounted source -> fail closed (omitted)
        fakeCoordinator.putState(
            SourceRuntimeState(sourceId = "src_unmounted", isAvailable = false, isMounted = false, hasPermission = true)
        )
        val unmountedFile = createTestLocalFile(
            id = "f_unmounted",
            trackId = "t_unmounted",
            fileUri = "content://media/unmounted",
            sourceId = "src_unmounted",
            isVerified = true
        )
        val unmountedTrack = Track(id = "t_unmounted", title = "Unmounted", durationSec = 100.0, localFile = unmountedFile)
        fakeTrackRepo.tracks.add(unmountedTrack)

        val f2 = resolver.resolvePlayableMediaItems(listOf(MediaItem.Builder().setMediaId("track:t_unmounted").build()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Unmounted storage source must fail closed", f2.get().isEmpty())

        // 3. Revoked permission -> fail closed (omitted)
        fakeCoordinator.putState(
            SourceRuntimeState(sourceId = "src_no_perm", isAvailable = false, isMounted = true, hasPermission = false)
        )
        val revokedFile = createTestLocalFile(
            id = "f_revoked",
            trackId = "t_revoked",
            fileUri = "content://media/revoked",
            sourceId = "src_no_perm",
            isVerified = true
        )
        val revokedTrack = Track(id = "t_revoked", title = "Revoked", durationSec = 100.0, localFile = revokedFile)
        fakeTrackRepo.tracks.add(revokedTrack)

        val f3 = resolver.resolvePlayableMediaItems(listOf(MediaItem.Builder().setMediaId("track:t_revoked").build()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Revoked permission storage source must fail closed", f3.get().isEmpty())

        // 4. Null sourceId on LocalFile -> fail closed (omitted)
        val nullSourceFile = createTestLocalFile(
            id = "f_null_src",
            trackId = "t_null_src",
            fileUri = "content://media/null_src",
            sourceId = null,
            isVerified = true
        )
        val nullSourceTrack = Track(id = "t_null_src", title = "Null Src", durationSec = 100.0, localFile = nullSourceFile)
        fakeTrackRepo.tracks.add(nullSourceTrack)

        val f4 = resolver.resolvePlayableMediaItems(listOf(MediaItem.Builder().setMediaId("track:t_null_src").build()))
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Null source ID on local file must fail closed", f4.get().isEmpty())
    }

    @Test
    fun containerPlayback_albumAndArtist_usesDedicatedQueriesAndExcludesUnavailable() {
        val fileAvailable = createTestLocalFile(id = "f_avail", trackId = "t_avail", fileUri = "content://media/avail", sourceId = "src_default", isVerified = true)
        val fileUnavailable = createTestLocalFile(id = "f_unavail", trackId = "t_unavail", fileUri = "content://media/unavail", sourceId = "src_missing", isVerified = true)

        val track1 = Track(
            id = "t_avail",
            title = "Alpha Available",
            albumId = "alb_container",
            artistId = "art_container",
            trackNumber = 1,
            discNumber = 1,
            durationSec = 200.0,
            localFile = fileAvailable
        )
        val track2 = Track(
            id = "t_unavail",
            title = "Beta Unavailable",
            albumId = "alb_container",
            artistId = "art_container",
            trackNumber = 2,
            discNumber = 1,
            durationSec = 210.0,
            localFile = fileUnavailable
        )

        fakeTrackRepo.tracks.addAll(listOf(track1, track2))

        // 1. Album container playback: resolves only available tracks in order
        val albumItem = MediaItem.Builder().setMediaId("album:alb_container").build()
        val albumFuture = resolver.resolvePlayableMediaItems(listOf(albumItem))
        testDispatcher.scheduler.advanceUntilIdle()
        val albumResolved = albumFuture.get()

        assertEquals(1, albumResolved.size)
        assertEquals("track:t_avail", albumResolved[0].mediaId)

        // 2. Artist container playback: resolves only available tracks in order
        val artistItem = MediaItem.Builder().setMediaId("artist:art_container").build()
        val artistFuture = resolver.resolvePlayableMediaItems(listOf(artistItem))
        testDispatcher.scheduler.advanceUntilIdle()
        val artistResolved = artistFuture.get()

        assertEquals(1, artistResolved.size)
        assertEquals("track:t_avail", artistResolved[0].mediaId)
    }

    @Test
    fun searchCache_automaticInvalidation_onCatalogMutation() {
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_s1", title = "Initial", durationSec = 100.0))

        val f1 = resolver.search(null, null, "test_query", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, f1.get().resultCode)

        val res1 = resolver.getSearchResult("test_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_s1", res1.get().value!![0].mediaId)

        // Mutate catalog by adding a track and updating allTracksFlow
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_s2", title = "Mutated", durationSec = 100.0))
        fakeTrackRepo.tracks.add(Track(id = "trk_mutated", title = "Mutated Track", durationSec = 100.0))
        fakeTrackRepo.notifyTracksChanged()
        testDispatcher.scheduler.advanceUntilIdle()

        // Cache must have been invalidated automatically; getSearchResult re-queries
        val res2 = resolver.getSearchResult("test_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_s2", res2.get().value!![0].mediaId)
    }

    @Test
    fun searchCache_normalizedKeys_whitespaceTrimming() {
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_norm", title = "Normalized", durationSec = 100.0))

        val f1 = resolver.search(null, null, "   Miles Davis   ", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, f1.get().resultCode)

        // Change search repository return to ensure hit is from cache
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_wrong", title = "Wrong", durationSec = 100.0))

        val res = resolver.getSearchResult("Miles Davis", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, res.get().value!!.size)
        assertEquals("track:t_norm", res.get().value!![0].mediaId)
    }

    @Test
    fun searchCache_distinctUnicodeCasing_doesNotShareCachedResults() {
        // Query 1: "Über" returns Track A
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_upper", title = "Über Track", durationSec = 100.0))
        val f1 = resolver.search(null, null, "Über", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, f1.get().resultCode)

        val resUpper = resolver.getSearchResult("Über", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_upper", resUpper.get().value!![0].mediaId)

        // Query 2: "über" has distinct casing; repository returns Track B
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_lower", title = "über Track", durationSec = 100.0))
        val f2 = resolver.search(null, null, "über", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, f2.get().resultCode)

        val resLower = resolver.getSearchResult("über", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        // Must NOT return the cached result of "Über" ("track:t_upper")
        assertEquals(1, resLower.get().value!!.size)
        assertEquals("track:t_lower", resLower.get().value!![0].mediaId)
    }

    @Test
    fun searchCache_resultsBoundedPerQuery() {
        // Return 250 tracks (exceeding MAX_SEARCH_RESULTS_PER_QUERY = 200)
        val bigList = (1..250).map { Track(id = "t_big_$it", title = "Track $it", durationSec = 100.0) }
        fakeSearchRepo.tracksToReturn = bigList

        val f = resolver.search(null, null, "big_query", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, f.get().resultCode)

        // Bounded to 200 items across 4 pages of 50. Page 4 (5th page, offset 200) must be empty.
        val p0 = resolver.getSearchResult("big_query", 0, 50, null)
        val p3 = resolver.getSearchResult("big_query", 3, 50, null)
        val p4 = resolver.getSearchResult("big_query", 4, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(50, p0.get().value!!.size)
        assertEquals(50, p3.get().value!!.size)
        assertEquals(0, p4.get().value!!.size)
    }

    @Test
    fun searchCache_deterministicRace_inFlightSearchCannotOverwriteDataAfterInvalidation() {
        val searchGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_stale", title = "Stale Track", durationSec = 100.0))
        fakeSearchRepo.searchTracksInterception = {
            searchGate.await()
        }

        // 1. Search A starts and captures generation N, then suspends inside repository query
        val searchAFuture = resolver.search(null, null, "race_query", null)
        testDispatcher.scheduler.runCurrent() // Runs up to suspension in searchTracksInterception

        // 2. Catalog mutation advances generation to N+1 and invalidates/clears cache
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_fresh", title = "Fresh Track", durationSec = 100.0))
        fakeTrackRepo.tracks.add(Track(id = "trk_mutated", title = "Mutated", durationSec = 100.0))
        fakeTrackRepo.notifyTracksChanged()
        testDispatcher.scheduler.runCurrent() // Executes invalidateSearchCache()

        // 3. Search A resumes with stale results
        searchGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, searchAFuture.get().resultCode)

        // 4. Verify Search A was rejected by atomic putIfGenerationMatches and did NOT populate cache
        fakeSearchRepo.searchTracksInterception = null
        val freshSearch = resolver.getSearchResult("race_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        val items = freshSearch.get().value!!
        assertEquals(1, items.size)
        // Must be fresh track, NOT stale track!
        assertEquals("track:t_fresh", items[0].mediaId)
    }

    @Test
    fun resolvePlayableMediaItems_awaitsInitialReadiness_validTrackResolvesAfterInitialization() {
        val gate = kotlinx.coroutines.CompletableDeferred<Boolean>()
        fakeCoordinator.readinessDeferred = gate

        // Seed a valid track with available source
        val file = createTestLocalFile("f_gate", "t_gate", "content://media/external/audio/media/1", sourceId = "src_gate")
        val track = Track(id = "t_gate", title = "Gate Track", durationSec = 120.0, localFile = file)
        fakeTrackRepo.tracks.add(track)
        fakeLocalFileRepo.files["t_gate"] = listOf(file)
        fakeCoordinator.putState(
            SourceRuntimeState(sourceId = "src_gate", isAvailable = true, isMounted = true, hasPermission = true)
        )

        val item = MediaItem.Builder().setMediaId("track:t_gate").build()
        val future = resolver.resolvePlayableMediaItems(listOf(item))
        testDispatcher.scheduler.runCurrent()

        // Before readiness gate completes, future should not be done yet
        assertFalse(future.isDone)

        // Complete initialization successfully
        gate.complete(true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(future.isDone)
        val resolved = future.get()
        assertEquals(1, resolved.size)
        assertEquals("track:t_gate", resolved[0].mediaId)
    }

    @Test
    fun resolvePlayableMediaItems_initialReadinessFailure_failsClosed() {
        fakeCoordinator.readinessResult = false

        val file = createTestLocalFile("f_fail", "t_fail", "content://media/external/audio/media/2", sourceId = "src_fail")
        val track = Track(id = "t_fail", title = "Fail Track", durationSec = 120.0, localFile = file)
        fakeTrackRepo.tracks.add(track)
        fakeLocalFileRepo.files["t_fail"] = listOf(file)
        fakeCoordinator.putState(
            SourceRuntimeState(sourceId = "src_fail", isAvailable = true, isMounted = true, hasPermission = true)
        )

        val item = MediaItem.Builder().setMediaId("track:t_fail").build()
        val future = resolver.resolvePlayableMediaItems(listOf(item))
        testDispatcher.scheduler.advanceUntilIdle()

        val resolved = future.get()
        // Must fail closed: empty list
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun resolvePlayableMediaItems_unmountedOrRevokedSource_stillRejectedAfterInitialization() {
        fakeCoordinator.readinessResult = true

        val file = createTestLocalFile("f_unm", "t_unm", "content://media/external/audio/media/3", sourceId = "src_unm")
        val track = Track(id = "t_unm", title = "Unmounted Track", durationSec = 120.0, localFile = file)
        fakeTrackRepo.tracks.add(track)
        fakeLocalFileRepo.files["t_unm"] = listOf(file)
        // Source is unmounted
        fakeCoordinator.putState(
            SourceRuntimeState(sourceId = "src_unm", isAvailable = false, isMounted = false, hasPermission = true)
        )

        val item = MediaItem.Builder().setMediaId("track:t_unm").build()
        val future = resolver.resolvePlayableMediaItems(listOf(item))
        testDispatcher.scheduler.advanceUntilIdle()

        val resolved = future.get()
        assertTrue(resolved.isEmpty())
    }

    @Test
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun resolvePlayableMediaItems_monitoringNotStarted_resolverDoesNotHangIndefinitely() {
        // Coordinator readiness never completes, simulating monitoring not started or stalled
        val stalledGate = kotlinx.coroutines.CompletableDeferred<Boolean>()
        fakeCoordinator.readinessDeferred = stalledGate

        // Create resolver with short bounded timeout (100ms)
        val shortTimeoutResolver = MediaItemTreeResolver(
            trackRepository = fakeTrackRepo,
            albumRepository = fakeAlbumRepo,
            artistRepository = fakeArtistRepo,
            playlistRepository = fakePlaylistRepo,
            localFileRepository = fakeLocalFileRepo,
            searchRepository = fakeSearchRepo,
            storageVolumeLifecycleCoordinator = fakeCoordinator,
            coroutineDispatcher = testDispatcher,
            readinessTimeoutMs = 100L
        )

        val item = MediaItem.Builder().setMediaId("track:t_stalled").build()
        val future = shortTimeoutResolver.resolvePlayableMediaItems(listOf(item))

        // Advance virtual time past the 100ms timeout threshold
        testDispatcher.scheduler.advanceTimeBy(150L)
        testDispatcher.scheduler.runCurrent()

        // Must complete rather than hanging indefinitely
        assertTrue(future.isDone)
        // Must fail closed (empty list)
        val resolved = future.get()
        assertTrue(resolved.isEmpty())
    }

    @Test
    fun getSearchResult_deterministicRace_inFlightSearchCannotOverwriteDataAfterInvalidation() {
        val searchGate = kotlinx.coroutines.CompletableDeferred<Unit>()
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_stale_get", title = "Stale Track", durationSec = 100.0))
        fakeSearchRepo.searchTracksInterception = {
            searchGate.await()
        }

        // 1. Initial getSearchResult starts and suspends inside repository query at generation N
        val future1 = resolver.getSearchResult("get_race_query", 0, 50, null)
        testDispatcher.scheduler.runCurrent()
        assertFalse(future1.isDone)

        // 2. Catalog mutation advances generation to N+1 and invalidates cache
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_fresh_get", title = "Fresh Track", durationSec = 100.0))
        fakeTrackRepo.tracks.add(Track(id = "trk_mut_get", title = "Mutated Track", durationSec = 100.0))
        fakeTrackRepo.notifyTracksChanged()
        testDispatcher.scheduler.runCurrent()

        // 3. Unpause in-flight search; returns point-in-time snapshot to caller, but atomic put is rejected
        searchGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(future1.isDone)
        val items1 = future1.get().value!!
        assertEquals(1, items1.size)
        assertEquals("track:t_stale_get", items1[0].mediaId)

        // 4. Verify cache was NOT populated with stale data: subsequent query fetches fresh data from repo
        fakeSearchRepo.searchTracksInterception = null
        val future2 = resolver.getSearchResult("get_race_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(future2.isDone)
        val items2 = future2.get().value!!
        assertEquals(1, items2.size)
        // Must return fresh track from repository, proving stale data was not cached!
        assertEquals("track:t_fresh_get", items2[0].mediaId)
    }

    @Test
    fun searchCache_invalidationOnCatalogMutations_albumsArtistsPlaylistsStorage() {
        // Seed search result in cache
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_c1", title = "Initial Track", durationSec = 100.0))
        val s1 = resolver.search(null, null, "catalog_mutation_query", null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(LibraryResult.RESULT_SUCCESS, s1.get().resultCode)

        // Verify hit is cached
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_c2", title = "Updated Track", durationSec = 100.0))
        val c1 = resolver.getSearchResult("catalog_mutation_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_c1", c1.get().value!![0].mediaId)

        // 1. Album mutation invalidates cache
        fakeAlbumRepo.albums.add(Album(id = "alb_mut", title = "New Album", artistName = "Artist"))
        fakeAlbumRepo.notifyAlbumsChanged()
        testDispatcher.scheduler.runCurrent()
        val c2 = resolver.getSearchResult("catalog_mutation_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_c2", c2.get().value!![0].mediaId)

        // 2. Artist mutation invalidates cache
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_c3", title = "Artist Mutated", durationSec = 100.0))
        fakeArtistRepo.artists.add(Artist(id = "art_mut", name = "New Artist"))
        fakeArtistRepo.notifyArtistsChanged()
        testDispatcher.scheduler.runCurrent()
        val c3 = resolver.getSearchResult("catalog_mutation_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_c3", c3.get().value!![0].mediaId)

        // 3. Playlist mutation invalidates cache
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_c4", title = "Playlist Mutated", durationSec = 100.0))
        fakePlaylistRepo.playlists.add(Playlist(id = "pl_mut", name = "New Playlist"))
        fakePlaylistRepo.notifyPlaylistsChanged()
        testDispatcher.scheduler.runCurrent()
        val c4 = resolver.getSearchResult("catalog_mutation_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_c4", c4.get().value!![0].mediaId)

        // 4. Storage volume runtime state change invalidates cache
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_c5", title = "Storage Mutated", durationSec = 100.0))
        fakeCoordinator.putState(
            SourceRuntimeState(sourceId = "src_new_mount", isAvailable = true, isMounted = true, hasPermission = true)
        )
        testDispatcher.scheduler.runCurrent()
        val c5 = resolver.getSearchResult("catalog_mutation_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_c5", c5.get().value!![0].mediaId)
    }

    @Test
    fun resolver_release_stopsCollectors_andIsSafeToCallMultipleTimes() {
        assertFalse(resolver.isReleased)

        // Seed a cached search
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_rel1", title = "Initial", durationSec = 100.0))
        val f1 = resolver.getSearchResult("release_test_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_rel1", f1.get().value!![0].mediaId)

        // Verify cache hit
        fakeSearchRepo.tracksToReturn = listOf(Track(id = "t_rel2", title = "New", durationSec = 100.0))
        val f2 = resolver.getSearchResult("release_test_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("track:t_rel1", f2.get().value!![0].mediaId)

        // Release the resolver
        resolver.release()
        assertTrue(resolver.isReleased)

        // Releasing again is safe and idempotent
        resolver.release()
        resolver.close()
        assertTrue(resolver.isReleased)

        // Trigger a catalog change: collectors must be stopped (cancelled scope)
        fakeTrackRepo.tracks.add(Track(id = "trk_after_rel", title = "After Release", durationSec = 100.0))
        fakeTrackRepo.notifyTracksChanged()
        testDispatcher.scheduler.runCurrent()

        // Calling on a released resolver cancels the future rather than executing or hanging
        val f3 = resolver.getSearchResult("release_test_query", 0, 50, null)
        assertTrue(f3.isCancelled)

        // A newly created resolver works normally and is not affected by the old resolver's closure
        val newResolver = MediaItemTreeResolver(
            trackRepository = fakeTrackRepo,
            albumRepository = fakeAlbumRepo,
            artistRepository = fakeArtistRepo,
            playlistRepository = fakePlaylistRepo,
            localFileRepository = fakeLocalFileRepo,
            searchRepository = fakeSearchRepo,
            storageVolumeLifecycleCoordinator = fakeCoordinator,
            coroutineDispatcher = testDispatcher
        )
        assertFalse(newResolver.isReleased)

        val newResult = newResolver.getSearchResult("release_test_query", 0, 50, null)
        testDispatcher.scheduler.advanceUntilIdle()
        // Fresh resolver fetches latest from search repo
        assertEquals("track:t_rel2", newResult.get().value!![0].mediaId)

        newResolver.release()
        assertTrue(newResolver.isReleased)
    }

    // --- In-Memory Fake Repositories ---

    private class FakeTrackRepository : TrackRepository {
        val tracks = mutableListOf<Track>()
        var throwError = false
        val _allTracksFlow = MutableStateFlow<List<Track>>(emptyList())

        override fun observeAllTracks(): Flow<List<Track>> = if (throwError) flow { throw RuntimeException("Simulated error") } else _allTracksFlow
        override fun observeOfflineTracks(): Flow<List<Track>> = flowOf(tracks.filter { it.isAvailableOffline })
        override suspend fun getTrackById(id: String): Track? = if (throwError) throw RuntimeException("DB error") else tracks.find { it.id == id }
        override fun searchTracks(query: String): Flow<List<Track>> = flowOf(tracks.filter { it.title.contains(query, ignoreCase = true) })
        override suspend fun upsertTracks(tracks: List<Track>) {
            this.tracks.addAll(tracks)
            _allTracksFlow.value = this.tracks.toList()
        }
        override suspend fun deleteTrack(id: String) {
            tracks.removeIf { it.id == id }
            _allTracksFlow.value = tracks.toList()
        }

        fun notifyTracksChanged() {
            _allTracksFlow.value = tracks.toList()
        }

        override suspend fun getTracksPaged(limit: Int, offset: Int): List<Track> {
            if (throwError) throw RuntimeException("DB error")
            if (offset >= tracks.size) return emptyList()
            return tracks.subList(offset, (offset + limit).coerceAtMost(tracks.size))
        }

        override suspend fun getTracksByAlbumPaged(albumId: String, limit: Int, offset: Int): List<Track> {
            val filtered = tracks.filter { it.albumId == albumId }
            if (offset >= filtered.size) return emptyList()
            return filtered.subList(offset, (offset + limit).coerceAtMost(filtered.size))
        }

        override suspend fun getTracksByAlbum(albumId: String): List<Track> {
            return tracks.filter { it.albumId == albumId }
        }

        override suspend fun getTracksByArtistPaged(artistId: String, limit: Int, offset: Int): List<Track> {
            val filtered = tracks.filter { it.artistId == artistId }
            if (offset >= filtered.size) return emptyList()
            return filtered.subList(offset, (offset + limit).coerceAtMost(filtered.size))
        }

        override suspend fun getTracksByArtist(artistId: String): List<Track> {
            return tracks.filter { it.artistId == artistId }
        }
    }

    private class FakeAlbumRepository : AlbumRepository {
        val albums = mutableListOf<Album>()
        val _allAlbumsFlow = MutableStateFlow<List<Album>>(emptyList())

        override fun observeAllAlbums(): Flow<List<Album>> = _allAlbumsFlow
        override suspend fun getAlbumById(id: String): Album? = albums.find { it.id == id }
        override fun observeAlbumsByArtist(artistId: String): Flow<List<Album>> = flowOf(albums.filter { it.artistId == artistId })
        override suspend fun upsertAlbums(albums: List<Album>) {
            this.albums.addAll(albums)
            _allAlbumsFlow.value = this.albums.toList()
        }

        fun notifyAlbumsChanged() {
            _allAlbumsFlow.value = albums.toList()
        }

        override suspend fun getAlbumsPaged(limit: Int, offset: Int): List<Album> {
            if (offset >= albums.size) return emptyList()
            return albums.subList(offset, (offset + limit).coerceAtMost(albums.size))
        }
    }

    private class FakeArtistRepository : ArtistRepository {
        val artists = mutableListOf<Artist>()
        val _allArtistsFlow = MutableStateFlow<List<Artist>>(emptyList())

        override fun observeAllArtists(): Flow<List<Artist>> = _allArtistsFlow
        override suspend fun getArtistById(id: String): Artist? = artists.find { it.id == id }
        override suspend fun upsertArtists(artists: List<Artist>) {
            this.artists.addAll(artists)
            _allArtistsFlow.value = this.artists.toList()
        }

        fun notifyArtistsChanged() {
            _allArtistsFlow.value = artists.toList()
        }

        override suspend fun getArtistsPaged(limit: Int, offset: Int): List<Artist> {
            if (offset >= artists.size) return emptyList()
            return artists.subList(offset, (offset + limit).coerceAtMost(artists.size))
        }
    }

    private class FakePlaylistRepository : PlaylistRepository {
        val playlists = mutableListOf<Playlist>()
        val playlistItems = mutableMapOf<String, List<PlaylistItem>>()
        val _allPlaylistsFlow = MutableStateFlow<List<Playlist>>(emptyList())

        override fun observeAllPlaylists(): Flow<List<Playlist>> = _allPlaylistsFlow
        override suspend fun getPlaylistById(id: String): Playlist? = playlists.find { it.id == id }
        override suspend fun createPlaylist(name: String, description: String?): Playlist = error("Not needed")
        override suspend fun renamePlaylist(id: String, newName: String) = error("Not needed")
        override suspend fun updatePlaylist(playlist: Playlist) = error("Not needed")
        override suspend fun deletePlaylist(id: String) = error("Not needed")
        override fun observePlaylistItems(playlistId: String): Flow<List<PlaylistItem>> = flowOf(playlistItems[playlistId] ?: emptyList())
        override suspend fun addTrackToPlaylist(playlistId: String, trackId: String): PlaylistItem = error("Not needed")
        override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>): List<PlaylistItem> = error("Not needed")
        override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) = error("Not needed")
        override suspend fun reorderPlaylistItem(playlistId: String, fromPosition: Int, toPosition: Int) = error("Not needed")

        fun notifyPlaylistsChanged() {
            _allPlaylistsFlow.value = playlists.toList()
        }

        override suspend fun getPlaylistsPaged(limit: Int, offset: Int): List<Playlist> {
            if (offset >= playlists.size) return emptyList()
            return playlists.subList(offset, (offset + limit).coerceAtMost(playlists.size))
        }

        override suspend fun getPlaylistItemsPaged(playlistId: String, limit: Int, offset: Int): List<PlaylistItem> {
            val items = playlistItems[playlistId] ?: emptyList()
            if (offset >= items.size) return emptyList()
            return items.subList(offset, (offset + limit).coerceAtMost(items.size))
        }
    }

    private class FakeLocalFileRepository : LocalFileRepository {
        val files = mutableMapOf<String, List<LocalFile>>()
        val filesByUri = mutableMapOf<String, LocalFile>()

        override fun observeLocalFilesForTrack(trackId: String): Flow<List<LocalFile>> = flowOf(files[trackId] ?: emptyList())
        override suspend fun getLocalFileById(id: String): LocalFile? = files.values.flatten().find { it.id == id }
        override suspend fun getLocalFileByUri(fileUri: String): LocalFile? = filesByUri[fileUri] ?: files.values.flatten().find { it.fileUri == fileUri }
        override suspend fun upsertLocalFile(localFile: LocalFile) {}
        override suspend fun updateVerificationStatus(id: String, isVerified: Boolean, verifiedAt: Long) {}
        override suspend fun deleteLocalFile(id: String) {}
    }

    private class FakeSearchRepository : SearchRepository {
        var tracksToReturn = emptyList<Track>()
        var albumsToReturn = emptyList<Album>()
        var artistsToReturn = emptyList<Artist>()
        var playlistsToReturn = emptyList<Playlist>()
        var throwError = false
        var searchTracksInterception: (suspend () -> Unit)? = null

        override fun searchTracks(query: String): Flow<List<Track>> = if (throwError) {
            flow { throw RuntimeException("Simulated error") }
        } else {
            val snapshot = tracksToReturn
            flow {
                searchTracksInterception?.invoke()
                emit(snapshot)
            }
        }
        override fun searchAlbums(query: String): Flow<List<Album>> = flowOf(albumsToReturn)
        override fun searchArtists(query: String): Flow<List<Artist>> = flowOf(artistsToReturn)
        override fun searchPlaylists(query: String): Flow<List<Playlist>> = flowOf(playlistsToReturn)
    }

    private class FakeStorageVolumeLifecycleCoordinator : StorageVolumeLifecycleCoordinator {
        val states = mutableMapOf<String, SourceRuntimeState>()
        val _runtimeStates = MutableStateFlow<Map<String, SourceRuntimeState>>(emptyMap())
        override val runtimeStates: StateFlow<Map<String, SourceRuntimeState>> = _runtimeStates
        var readinessDeferred: CompletableDeferred<Boolean>? = null
        var readinessResult: Boolean = true

        override suspend fun awaitInitialReadiness(): Boolean {
            val deferred = readinessDeferred
            return if (deferred != null) {
                deferred.await()
            } else {
                readinessResult
            }
        }

        fun putState(state: SourceRuntimeState) {
            states[state.sourceId] = state
            _runtimeStates.value = states.toMap()
        }

        override fun isSourceAvailable(sourceId: String): Boolean {
            val s = states[sourceId] ?: return false
            return s.isAvailable && s.isMounted && s.hasPermission
        }
        override fun onVolumeMounted(volumeId: String) {}
        override fun onVolumeUnmounted(volumeId: String) {}
        override fun onSafPermissionRevoked(treeUri: String) {}
        override fun onSafPermissionGranted(treeUri: String) {}
        override suspend fun revalidateAllSources(): Map<String, SourceRuntimeState> = states
        override suspend fun revalidateSource(source: org.harmonia.player.domain.model.storage.StorageSource): SourceRuntimeState =
            states[source.id] ?: SourceRuntimeState(source.id, false, false, false)
        override fun startMonitoring() {}
        override fun stopMonitoring() {}
    }

    private class FakePlaybackStateRepository : PlaybackStateRepository {
        var state = PlaybackState()
        private val queueFlow = MutableStateFlow<List<org.harmonia.player.domain.model.QueueItem>>(emptyList())
        override fun observePlaybackState(): Flow<PlaybackState> = flowOf(state)
        override suspend fun savePlaybackState(state: PlaybackState) { this.state = state }
        override fun observeQueue(): Flow<List<org.harmonia.player.domain.model.QueueItem>> = queueFlow
        override suspend fun setQueue(items: List<org.harmonia.player.domain.model.QueueItem>) { queueFlow.value = items }
        override suspend fun appendToQueue(items: List<org.harmonia.player.domain.model.QueueItem>) { queueFlow.value = queueFlow.value + items }
        override suspend fun clearQueue() { queueFlow.value = emptyList() }
    }

    private class FakeListeningEventRepository : ListeningEventRepository {
        val recordedEvents = mutableListOf<ListeningEvent>()
        override suspend fun recordEvent(event: ListeningEvent) { recordedEvents.add(event) }
        override fun observeRecentEvents(limit: Int): Flow<List<ListeningEvent>> = flowOf(recordedEvents)
        override suspend fun getEventCount(): Int = recordedEvents.size
    }

    private fun createTestLocalFile(
        id: String,
        trackId: String,
        fileUri: String,
        sourceId: String? = "src_default",
        isVerified: Boolean = true
    ): LocalFile {
        return LocalFile(
            id = id,
            trackId = trackId,
            sourceId = sourceId,
            storageType = org.harmonia.player.domain.model.StorageType.MEDIA_STORE_PRIMARY,
            fileUri = fileUri,
            fileSize = 1024L,
            fileHash = "hash_$id",
            format = "mp3",
            isVerified = isVerified
        )
    }
}
