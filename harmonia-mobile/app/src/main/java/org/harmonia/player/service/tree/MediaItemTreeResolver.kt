package org.harmonia.player.service.tree

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService.LibraryParams
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.harmonia.player.domain.model.Album
import org.harmonia.player.domain.model.Artist
import org.harmonia.player.domain.model.Playlist
import org.harmonia.player.domain.model.Track
import org.harmonia.player.domain.model.storage.lifecycle.StorageVolumeLifecycleCoordinator
import org.harmonia.player.domain.repository.AlbumRepository
import org.harmonia.player.domain.repository.ArtistRepository
import org.harmonia.player.domain.repository.LocalFileRepository
import org.harmonia.player.domain.repository.PlaylistRepository
import org.harmonia.player.domain.repository.SearchRepository
import org.harmonia.player.domain.repository.TrackRepository

/**
 * Resolves Media3 MediaLibraryService tree hierarchy, search, and external playback
 * for external clients (Android Auto, Automotive OS, Assistant).
 *
 * M5-B Remediated Implementation:
 * - Repository-backed browsing with SQL LIMIT/OFFSET pagination on Dispatchers.IO.
 * - Deterministic, collision-free canonical media IDs (album:{id}, artist:{id}, playlist:{id}, track:{id}).
 * - Bounded LRU search cache with invalidation support.
 * - Strict external URI and track validation enforcing offline-only storage invariants.
 * - Safe error handling preserving cancellation and surfacing supported LibraryResults.
 */
class MediaItemTreeResolver(
    private val trackRepository: TrackRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository,
    private val localFileRepository: LocalFileRepository,
    private val searchRepository: SearchRepository,
    private val storageVolumeLifecycleCoordinator: StorageVolumeLifecycleCoordinator,
    private val coroutineDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val readinessTimeoutMs: Long = DEFAULT_READINESS_TIMEOUT_MS
) : AutoCloseable {

    private val scope = CoroutineScope(coroutineDispatcher + SupervisorJob())
    private val searchCache = SearchCacheManager(maxEntries = 20)
    private val isClosed = AtomicBoolean(false)

    val isReleased: Boolean
        get() = isClosed.get()

    /**
     * Idempotently releases this resolver, cancelling its CoroutineScope and
     * stopping all long-lived catalog and storage mutation collectors.
     */
    fun release() {
        close()
    }

    override fun close() {
        if (isClosed.compareAndSet(false, true)) {
            scope.cancel()
        }
    }

    init {
        // Wire automatic cache invalidation to catalog mutations and storage volume transitions
        scope.launch(coroutineDispatcher) {
            trackRepository.observeAllTracks().collect {
                invalidateSearchCache()
            }
        }
        scope.launch(coroutineDispatcher) {
            albumRepository.observeAllAlbums().collect {
                invalidateSearchCache()
            }
        }
        scope.launch(coroutineDispatcher) {
            artistRepository.observeAllArtists().collect {
                invalidateSearchCache()
            }
        }
        scope.launch(coroutineDispatcher) {
            playlistRepository.observeAllPlaylists().collect {
                invalidateSearchCache()
            }
        }
        scope.launch(coroutineDispatcher) {
            storageVolumeLifecycleCoordinator.runtimeStates.collect {
                invalidateSearchCache()
            }
        }
    }

    companion object {
        private const val TAG = "MediaItemTreeResolver"

        const val ROOT_ID = "root"
        const val ROOT_ALBUMS_ID = "root_albums"
        const val ROOT_ARTISTS_ID = "root_artists"
        const val ROOT_PLAYLISTS_ID = "root_playlists"
        const val ROOT_TRACKS_ID = "root_tracks"

        const val ALBUM_PREFIX = "album:"
        const val ARTIST_PREFIX = "artist:"
        const val PLAYLIST_PREFIX = "playlist:"
        const val TRACK_PREFIX = "track:"

        const val DEFAULT_PAGE_SIZE = 50
        const val MAX_PAGE_SIZE = 50
        const val MAX_SEARCH_RESULTS_PER_QUERY = 200
        const val DEFAULT_READINESS_TIMEOUT_MS = 5000L
    }

    private val rootItem: MediaItem by lazy {
        buildCategoryNode(
            id = ROOT_ID,
            title = "Harmonia",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            isGrid = false
        )
    }

    private val albumsCategoryItem: MediaItem by lazy {
        buildCategoryNode(
            id = ROOT_ALBUMS_ID,
            title = "Albums",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
            isGrid = true
        )
    }

    private val artistsCategoryItem: MediaItem by lazy {
        buildCategoryNode(
            id = ROOT_ARTISTS_ID,
            title = "Artists",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
            isGrid = true
        )
    }

    private val playlistsCategoryItem: MediaItem by lazy {
        buildCategoryNode(
            id = ROOT_PLAYLISTS_ID,
            title = "Playlists",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
            isGrid = false
        )
    }

    private val tracksCategoryItem: MediaItem by lazy {
        buildCategoryNode(
            id = ROOT_TRACKS_ID,
            title = "Tracks",
            mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
            isGrid = false
        )
    }

    private val categoryNodes: List<MediaItem> by lazy {
        listOf(
            albumsCategoryItem,
            artistsCategoryItem,
            playlistsCategoryItem,
            tracksCategoryItem
        )
    }

    fun getRoot(
        browser: MediaSession.ControllerInfo? = null,
        params: LibraryParams? = null
    ): ListenableFuture<LibraryResult<MediaItem>> {
        return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
    }

    fun getChildren(
        parentId: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return launchFuture {
            try {
                when {
                    parentId == ROOT_ID -> {
                        val paged = paginateList(categoryNodes, page, pageSize)
                        LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
                    }
                    parentId == ROOT_ALBUMS_ID -> {
                        val pagination = calculatePagination(page, pageSize)
                        if (pagination == null) {
                            LibraryResult.ofItemList(ImmutableList.of(), params)
                        } else {
                            val (limit, offset) = pagination
                            val albums = albumRepository.getAlbumsPaged(limit, offset)
                            val mediaItems = albums.map { albumToMediaItem(it) }
                            LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                        }
                    }
                    parentId == ROOT_ARTISTS_ID -> {
                        val pagination = calculatePagination(page, pageSize)
                        if (pagination == null) {
                            LibraryResult.ofItemList(ImmutableList.of(), params)
                        } else {
                            val (limit, offset) = pagination
                            val artists = artistRepository.getArtistsPaged(limit, offset)
                            val mediaItems = artists.map { artistToMediaItem(it) }
                            LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                        }
                    }
                    parentId == ROOT_PLAYLISTS_ID -> {
                        val pagination = calculatePagination(page, pageSize)
                        if (pagination == null) {
                            LibraryResult.ofItemList(ImmutableList.of(), params)
                        } else {
                            val (limit, offset) = pagination
                            val playlists = playlistRepository.getPlaylistsPaged(limit, offset)
                            val mediaItems = playlists.map { playlistToMediaItem(it) }
                            LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                        }
                    }
                    parentId == ROOT_TRACKS_ID -> {
                        val pagination = calculatePagination(page, pageSize)
                        if (pagination == null) {
                            LibraryResult.ofItemList(ImmutableList.of(), params)
                        } else {
                            val (limit, offset) = pagination
                            val tracks = trackRepository.getTracksPaged(limit, offset)
                            val mediaItems = tracks.map { trackToMediaItem(it) }
                            LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                        }
                    }
                    parentId.startsWith(ALBUM_PREFIX) -> {
                        val albumId = parentId.removePrefix(ALBUM_PREFIX)
                        val album = albumRepository.getAlbumById(albumId)
                        if (album == null) {
                            LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                        } else {
                            val pagination = calculatePagination(page, pageSize)
                            if (pagination == null) {
                                LibraryResult.ofItemList(ImmutableList.of(), params)
                            } else {
                                val (limit, offset) = pagination
                                val tracks = trackRepository.getTracksByAlbumPaged(albumId, limit, offset)
                                val mediaItems = tracks.map { trackToMediaItem(it) }
                                LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                            }
                        }
                    }
                    parentId.startsWith(ARTIST_PREFIX) -> {
                        val artistId = parentId.removePrefix(ARTIST_PREFIX)
                        val artist = artistRepository.getArtistById(artistId)
                        if (artist == null) {
                            LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                        } else {
                            val pagination = calculatePagination(page, pageSize)
                            if (pagination == null) {
                                LibraryResult.ofItemList(ImmutableList.of(), params)
                            } else {
                                val (limit, offset) = pagination
                                val tracks = trackRepository.getTracksByArtistPaged(artistId, limit, offset)
                                val mediaItems = tracks.map { trackToMediaItem(it) }
                                LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                            }
                        }
                    }
                    parentId.startsWith(PLAYLIST_PREFIX) -> {
                        val playlistId = parentId.removePrefix(PLAYLIST_PREFIX)
                        val playlist = playlistRepository.getPlaylistById(playlistId)
                        if (playlist == null) {
                            LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                        } else {
                            val pagination = calculatePagination(page, pageSize)
                            if (pagination == null) {
                                LibraryResult.ofItemList(ImmutableList.of(), params)
                            } else {
                                val (limit, offset) = pagination
                                val items = playlistRepository.getPlaylistItemsPaged(playlistId, limit, offset)
                                val tracks = items.mapNotNull { it.track }
                                val mediaItems = tracks.map { trackToMediaItem(it) }
                                LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), params)
                            }
                        }
                    }
                    else -> {
                        // Leaf item (track:{id}) or malformed parent ID
                        LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving children for parentId: $parentId", e)
                LibraryResult.ofError(SessionResult.RESULT_ERROR_UNKNOWN)
            }
        }
    }

    fun getItem(mediaId: String): ListenableFuture<LibraryResult<MediaItem>> {
        return launchFuture {
            try {
                when {
                    mediaId == ROOT_ID -> LibraryResult.ofItem(rootItem, null)
                    mediaId == ROOT_ALBUMS_ID -> LibraryResult.ofItem(albumsCategoryItem, null)
                    mediaId == ROOT_ARTISTS_ID -> LibraryResult.ofItem(artistsCategoryItem, null)
                    mediaId == ROOT_PLAYLISTS_ID -> LibraryResult.ofItem(playlistsCategoryItem, null)
                    mediaId == ROOT_TRACKS_ID -> LibraryResult.ofItem(tracksCategoryItem, null)
                    mediaId.startsWith(ALBUM_PREFIX) -> {
                        val albumId = mediaId.removePrefix(ALBUM_PREFIX)
                        val album = albumRepository.getAlbumById(albumId)
                        if (album != null) LibraryResult.ofItem(albumToMediaItem(album), null)
                        else LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                    }
                    mediaId.startsWith(ARTIST_PREFIX) -> {
                        val artistId = mediaId.removePrefix(ARTIST_PREFIX)
                        val artist = artistRepository.getArtistById(artistId)
                        if (artist != null) LibraryResult.ofItem(artistToMediaItem(artist), null)
                        else LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                    }
                    mediaId.startsWith(PLAYLIST_PREFIX) -> {
                        val playlistId = mediaId.removePrefix(PLAYLIST_PREFIX)
                        val playlist = playlistRepository.getPlaylistById(playlistId)
                        if (playlist != null) LibraryResult.ofItem(playlistToMediaItem(playlist), null)
                        else LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                    }
                    mediaId.startsWith(TRACK_PREFIX) -> {
                        val trackId = mediaId.removePrefix(TRACK_PREFIX)
                        val track = trackRepository.getTrackById(trackId)
                        if (track != null) LibraryResult.ofItem(trackToMediaItem(track), null)
                        else LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                    }
                    else -> LibraryResult.ofError(SessionResult.RESULT_ERROR_BAD_VALUE)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving item for mediaId: $mediaId", e)
                LibraryResult.ofError(SessionResult.RESULT_ERROR_UNKNOWN)
            }
        }
    }

    /**
     * Normalizes search query keys by trimming leading and trailing whitespace.
     * Note: Case folding is intentionally omitted to avoid incorrectly sharing cached results
     * for Unicode queries whose case-folding equivalence is not guaranteed by SQLite's default LIKE.
     */
    private fun normalizeSearchQuery(query: String): String = query.trim()

    fun search(
        session: MediaLibrarySession?,
        browser: MediaSession.ControllerInfo?,
        query: String,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<Void>> {
        val normalizedKey = normalizeSearchQuery(query)
        if (normalizedKey.isEmpty()) {
            searchCache.putIfGenerationMatches(searchCache.currentGeneration(), normalizedKey, emptyList())
            if (session != null && browser != null) {
                session.notifySearchResultChanged(browser, query, 0, params)
            }
            return Futures.immediateFuture(LibraryResult.ofVoid(params))
        }

        return launchFuture {
            try {
                val genAtStart = searchCache.currentGeneration()
                val tracks = searchRepository.searchTracks(normalizedKey).first().map { trackToMediaItem(it) }
                val albums = searchRepository.searchAlbums(normalizedKey).first().map { albumToMediaItem(it) }
                val artists = searchRepository.searchArtists(normalizedKey).first().map { artistToMediaItem(it) }
                val playlists = searchRepository.searchPlaylists(normalizedKey).first().map { playlistToMediaItem(it) }
                val combined = (tracks + albums + artists + playlists).take(MAX_SEARCH_RESULTS_PER_QUERY)

                // Atomic conditional put: only populates cache if generation has not changed
                searchCache.putIfGenerationMatches(genAtStart, normalizedKey, combined)

                if (session != null && browser != null) {
                    session.notifySearchResultChanged(browser, query, combined.size, params)
                }
                LibraryResult.ofVoid(params)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Error executing search for query: $query", e)
                LibraryResult.ofError(SessionResult.RESULT_ERROR_UNKNOWN)
            }
        }
    }

    fun getSearchResult(
        query: String,
        page: Int,
        pageSize: Int,
        params: LibraryParams?
    ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
        return launchFuture {
            try {
                val normalizedKey = normalizeSearchQuery(query)
                val cached = searchCache.get(normalizedKey)
                val items = if (cached != null) {
                    cached
                } else {
                    if (normalizedKey.isEmpty()) {
                        emptyList()
                    } else {
                        val genAtStart = searchCache.currentGeneration()
                        val tracks = searchRepository.searchTracks(normalizedKey).first().map { trackToMediaItem(it) }
                        val albums = searchRepository.searchAlbums(normalizedKey).first().map { albumToMediaItem(it) }
                        val artists = searchRepository.searchArtists(normalizedKey).first().map { artistToMediaItem(it) }
                        val playlists = searchRepository.searchPlaylists(normalizedKey).first().map { playlistToMediaItem(it) }
                        val combined = (tracks + albums + artists + playlists).take(MAX_SEARCH_RESULTS_PER_QUERY)
                        searchCache.putIfGenerationMatches(genAtStart, normalizedKey, combined)
                        combined
                    }
                }
                val paged = paginateList(items, page, pageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(paged), params)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Error retrieving search results for query: $query", e)
                LibraryResult.ofError(SessionResult.RESULT_ERROR_UNKNOWN)
            }
        }
    }

    fun invalidateSearchCache() {
        searchCache.invalidate()
    }

    fun resolvePlayableMediaItems(
        mediaItems: List<MediaItem>
    ): ListenableFuture<List<MediaItem>> {
        return launchFuture {
            // Await initial storage readiness to avoid rejecting valid tracks before initial revalidation completes.
            // A bounded wait prevents indefinite hangs if monitoring was not started or initialization stalled.
            val isReady = try {
                withTimeoutOrNull(readinessTimeoutMs) {
                    storageVolumeLifecycleCoordinator.awaitInitialReadiness()
                } ?: false
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                false
            }
            if (!isReady) {
                Log.w(TAG, "Storage coordinator failed initial readiness check (or timed out) — failing closed")
                return@launchFuture emptyList()
            }

            val resolved = mutableListOf<MediaItem>()
            for (item in mediaItems) {
                val mediaId = item.mediaId
                when {
                    mediaId.startsWith(TRACK_PREFIX) -> {
                        val trackId = mediaId.removePrefix(TRACK_PREFIX)
                        val playable = resolveTrackToPlayableMediaItem(trackId)
                        if (playable != null) resolved.add(playable)
                    }
                    mediaId.startsWith(ALBUM_PREFIX) -> {
                        val albumId = mediaId.removePrefix(ALBUM_PREFIX)
                        val tracks = trackRepository.getTracksByAlbum(albumId)
                        for (track in tracks) {
                            val playable = resolveTrackToPlayableMediaItem(track.id, track)
                            if (playable != null) resolved.add(playable)
                        }
                    }
                    mediaId.startsWith(ARTIST_PREFIX) -> {
                        val artistId = mediaId.removePrefix(ARTIST_PREFIX)
                        val tracks = trackRepository.getTracksByArtist(artistId)
                        for (track in tracks) {
                            val playable = resolveTrackToPlayableMediaItem(track.id, track)
                            if (playable != null) resolved.add(playable)
                        }
                    }
                    mediaId.startsWith(PLAYLIST_PREFIX) -> {
                        val playlistId = mediaId.removePrefix(PLAYLIST_PREFIX)
                        val items = playlistRepository.observePlaylistItems(playlistId).first()
                        for (pi in items) {
                            val playable = resolveTrackToPlayableMediaItem(pi.trackId, pi.track)
                            if (playable != null) resolved.add(playable)
                        }
                    }
                    else -> {
                        val rawUri = (item.requestMetadata.mediaUri ?: item.localConfiguration?.uri)?.toString()
                            ?: if (item.mediaId.startsWith("content://", ignoreCase = true) || item.mediaId.startsWith("file://", ignoreCase = true) || item.mediaId.startsWith("http://", ignoreCase = true) || item.mediaId.startsWith("https://", ignoreCase = true)) item.mediaId else null

                        if (rawUri != null && (rawUri.startsWith("content://", ignoreCase = true) || rawUri.startsWith("file://", ignoreCase = true))) {
                            val localFile = localFileRepository.getLocalFileByUri(rawUri)
                            if (localFile != null) {
                                val playable = resolveTrackToPlayableMediaItem(localFile.trackId)
                                if (playable != null) resolved.add(playable)
                            }
                        }
                    }
                }
            }
            resolved
        }
    }

    private suspend fun resolveTrackToPlayableMediaItem(
        trackId: String,
        preloadedTrack: Track? = null
    ): MediaItem? {
        val track = preloadedTrack ?: trackRepository.getTrackById(trackId) ?: return null
        if (!track.isAvailableOffline) return null

        val localFile = track.localFile
            ?: localFileRepository.observeLocalFilesForTrack(trackId).firstOrNull()?.firstOrNull()
            ?: return null

        if (!localFile.isVerified) return null

        // Storage volume availability check - strictly fail closed
        val sourceId = localFile.sourceId ?: return null
        val runtimeState = storageVolumeLifecycleCoordinator.runtimeStates.value[sourceId]
        if (runtimeState == null || !runtimeState.isAvailable || !runtimeState.isMounted || !runtimeState.hasPermission) {
            return null
        }
        if (!storageVolumeLifecycleCoordinator.isSourceAvailable(sourceId)) {
            return null
        }

        val rawUri = localFile.fileUri
        if (rawUri.startsWith("http://", ignoreCase = true) || rawUri.startsWith("https://", ignoreCase = true)) {
            return null
        }

        val fileUri = try { Uri.parse(rawUri) } catch (e: Exception) { null }
        val artworkUri = track.artworkHash?.let {
            try { Uri.parse("content://org.harmonia.player.artwork/$it") } catch (e: Exception) { null }
        }

        val builder = MediaItem.Builder()
            .setMediaId("$TRACK_PREFIX${track.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName)
                    .setAlbumTitle(track.albumTitle)
                    .setTrackNumber(track.trackNumber)
                    .setDiscNumber(track.discNumber)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply {
                        if (artworkUri != null) setArtworkUri(artworkUri)
                    }
                    .build()
            )

        if (fileUri != null) {
            builder.setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(fileUri)
                    .build()
            )
            builder.setUri(fileUri)
        }

        return builder.build()
    }

    private fun calculatePagination(page: Int, pageSize: Int): Pair<Int, Int>? {
        if (page < 0) return null
        val boundedPageSize = if (pageSize <= 0) DEFAULT_PAGE_SIZE else pageSize.coerceAtMost(MAX_PAGE_SIZE)
        val offsetLong = page.toLong() * boundedPageSize
        if (offsetLong < 0L || offsetLong > Int.MAX_VALUE) return null
        return Pair(boundedPageSize, offsetLong.toInt())
    }

    private fun <T> paginateList(items: List<T>, page: Int, pageSize: Int): List<T> {
        val pagination = calculatePagination(page, pageSize) ?: return emptyList()
        val (limit, offset) = pagination
        if (offset >= items.size) return emptyList()
        val toIndex = (offset + limit).coerceAtMost(items.size)
        return items.subList(offset, toIndex)
    }

    private fun albumToMediaItem(album: Album): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        val artworkUri = album.artworkHash?.let {
            Uri.parse("content://org.harmonia.player.artwork/$it")
        }
        return MediaItem.Builder()
            .setMediaId("$ALBUM_PREFIX${album.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(album.title)
                    .setArtist(album.artistName)
                    .setIsBrowsable(true)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .setArtworkUri(artworkUri)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun artistToMediaItem(artist: Artist): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        return MediaItem.Builder()
            .setMediaId("$ARTIST_PREFIX${artist.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(artist.name)
                    .setIsBrowsable(true)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun playlistToMediaItem(playlist: Playlist): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        val artworkUri = playlist.artworkHash?.let {
            Uri.parse("content://org.harmonia.player.artwork/$it")
        }
        return MediaItem.Builder()
            .setMediaId("$PLAYLIST_PREFIX${playlist.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playlist.name)
                    .setSubtitle("${playlist.trackCount} tracks")
                    .setIsBrowsable(true)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setArtworkUri(artworkUri)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun trackToMediaItem(track: Track): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        val artworkUri = track.artworkHash?.let {
            Uri.parse("content://org.harmonia.player.artwork/$it")
        }
        return MediaItem.Builder()
            .setMediaId("$TRACK_PREFIX${track.id}")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artistName)
                    .setAlbumTitle(track.albumTitle)
                    .setTrackNumber(track.trackNumber)
                    .setDiscNumber(track.discNumber)
                    .setIsBrowsable(false)
                    .setIsPlayable(track.isAvailableOffline)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(artworkUri)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun buildCategoryNode(
        id: String,
        title: String,
        mediaType: Int,
        isGrid: Boolean
    ): MediaItem {
        val extras = Bundle().apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                if (isGrid) {
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                } else {
                    MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
                }
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun <T> launchFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        if (isClosed.get()) {
            future.cancel(false)
            return future
        }
        val job = scope.launch(coroutineDispatcher) {
            try {
                future.set(block())
            } catch (ce: CancellationException) {
                future.cancel(false)
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause is CancellationException && !future.isDone) {
                future.cancel(false)
            }
        }
        future.addListener({
            if (future.isCancelled) {
                job.cancel()
            }
        }, { r -> r.run() })
        return future
    }

    /**
     * Thread-safe, bounded LRU search cache with generation-tracked atomic invalidation and insertion.
     *
     * Concurrency Invariant:
     * A single monitor lock (`synchronized(this)`) serializes all cache reads, generation checks,
     * conditional insertions, and cache clearing. The monitor is held strictly for memory access
     * and is NEVER held across asynchronous repository queries or coroutine suspension.
     * This guarantees that in-flight searches started prior to an invalidation cannot repopulate
     * the cleared cache, eliminating race windows between check and insertion.
     *
     * Consistency Contract:
     * - An in-flight search started prior to invalidation completes and returns its point-in-time
     *   query results to that immediate caller.
     * - Because its captured generation does not match the incremented generation, atomic
     *   `putIfGenerationMatches` rejects insertion and the cache remains clean.
     * - Any subsequent search is guaranteed to miss the cache and fetch fresh repository results.
     */
    private class SearchCacheManager(private val maxEntries: Int = 20) {
        private var generation: Long = 0L
        private val map = object : LinkedHashMap<String, List<MediaItem>>(maxEntries, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<MediaItem>>?): Boolean {
                return size > maxEntries
            }
        }

        @Synchronized
        fun currentGeneration(): Long = generation

        @Synchronized
        fun get(key: String): List<MediaItem>? = map[key]

        @Synchronized
        fun putIfGenerationMatches(expectedGeneration: Long, key: String, value: List<MediaItem>): Boolean {
            return if (generation == expectedGeneration) {
                map[key] = value
                true
            } else {
                false
            }
        }

        @Synchronized
        fun invalidate() {
            generation++
            map.clear()
        }

        @Synchronized
        fun size(): Int = map.size
    }
}
