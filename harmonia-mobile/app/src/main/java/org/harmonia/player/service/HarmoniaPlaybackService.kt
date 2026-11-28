package org.harmonia.player.service

import android.os.Looper
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import org.harmonia.player.presentation.MainActivity
import org.harmonia.player.service.tree.MediaItemTreeResolver

/**
 * Background MediaLibraryService powering native Android and Android Auto media playback.
 * Survives Activity lifecycle changes and hosts the authoritative ExoPlayer instance.
 */
class HarmoniaPlaybackService : MediaLibraryService() {

    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var treeResolver: MediaItemTreeResolver
    private var audioEffectManager: org.harmonia.player.service.equalizer.DefaultAudioEffectManager? = null
    private var equalizerBridge: org.harmonia.player.service.equalizer.PlaybackServiceEqualizerBridge? = null
    private var playbackOptionsBinder: org.harmonia.player.service.audio.PlaybackOptionsBinder? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Ensure ServiceLocator dependencies are available
        org.harmonia.player.core.di.ServiceLocator.initialize(this)

        // Ensure storage volume lifecycle monitoring is active before incoming playback/browse requests
        org.harmonia.player.core.di.ServiceLocator.getStorageVolumeLifecycleCoordinator().startMonitoring()

        treeResolver = MediaItemTreeResolver(
            trackRepository = org.harmonia.player.core.di.ServiceLocator.getTrackRepository(),
            albumRepository = org.harmonia.player.core.di.ServiceLocator.getAlbumRepository(),
            artistRepository = org.harmonia.player.core.di.ServiceLocator.getArtistRepository(),
            playlistRepository = org.harmonia.player.core.di.ServiceLocator.getPlaylistRepository(),
            localFileRepository = org.harmonia.player.core.di.ServiceLocator.getLocalFileRepository(),
            searchRepository = org.harmonia.player.core.di.ServiceLocator.getSearchRepository(),
            storageVolumeLifecycleCoordinator = org.harmonia.player.core.di.ServiceLocator.getStorageVolumeLifecycleCoordinator()
        )

        // Configure audio attributes for music playback with automatic audio focus handling
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Gain stage for volume levelling and fades between songs (task 4.5). It is added in front
        // of ExoPlayer's own processors, so speed changes keep working.
        val gainProcessor = org.harmonia.player.service.audio.GainAudioProcessor()
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink =
                androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(gainProcessor))
                    .build()
        }

        // Create authoritative ExoPlayer instance
        val exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        // Keep "Play next" and "Add to queue" in order while shuffle is on.
        exoPlayer.setShuffleOrder(
            PlayNextShuffleOrder(length = 0) {
                if (Looper.myLooper() == exoPlayer.applicationLooper) exoPlayer.currentMediaItemIndex else C.INDEX_UNSET
            }
        )

        player = exoPlayer

        playbackOptionsBinder = org.harmonia.player.service.audio.PlaybackOptionsBinder(
            player = exoPlayer,
            gain = gainProcessor,
            preferences = org.harmonia.player.core.di.ServiceLocator.getUserPreferencesRepository()
        ).also { it.start() }

        // Initialize Audio Effect Manager and bridge to authoritative ExoPlayer audio session
        val equalizerFactory = org.harmonia.player.service.equalizer.AndroidPlatformEqualizerFactory()
        val userIntentSource = org.harmonia.player.core.di.ServiceLocator.getEqualizerUserIntentSource()
        val effectManager = org.harmonia.player.service.equalizer.DefaultAudioEffectManager(
            platformEqualizerFactory = equalizerFactory,
            userIntentSource = userIntentSource
        )
        audioEffectManager = effectManager
        org.harmonia.player.core.di.ServiceLocator.setAudioEqualizerCoordinator(effectManager)

        val bridge = org.harmonia.player.service.equalizer.PlaybackServiceEqualizerBridge(
            audioEffectManager = effectManager
        )
        equalizerBridge = bridge
        bridge.attachToPlayer(exoPlayer)

        // PendingIntent to launch the app UI when tapping the media notification
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create MediaLibrarySession connected to the player and media tree resolver
        mediaLibrarySession = MediaLibrarySession.Builder(
            this,
            exoPlayer,
            HarmoniaLibrarySessionCallback(treeResolver)
        )
            .setSessionActivity(sessionActivityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        playbackOptionsBinder?.release()
        playbackOptionsBinder = null
        if (::treeResolver.isInitialized) {
            treeResolver.release()
        }
        equalizerBridge?.detachAndRelease(player)
        equalizerBridge = null
        audioEffectManager?.let { manager ->
            org.harmonia.player.core.di.ServiceLocator.clearAudioEqualizerCoordinator(manager)
        }
        audioEffectManager = null


        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        player = null
        super.onDestroy()
    }

    private class HarmoniaLibrarySessionCallback(
        private val treeResolver: MediaItemTreeResolver
    ) : MediaLibrarySession.Callback {

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return treeResolver.getRoot(browser, params)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return treeResolver.getChildren(parentId, page, pageSize, params)
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return treeResolver.getItem(mediaId)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            return treeResolver.search(session, browser, query, params)
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return treeResolver.getSearchResult(query, page, pageSize, params)
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            return treeResolver.resolvePlayableMediaItems(mediaItems)
        }
    }
}
