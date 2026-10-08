package org.harmonia.player.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.harmonia.player.presentation.MainActivity

/**
 * Background MediaSessionService powering native Android media playback.
 * Survives Activity lifecycle changes and hosts the authoritative ExoPlayer instance.
 */
class HarmoniaPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    @androidx.annotation.OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // Configure audio attributes for music playback with automatic audio focus handling
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Create authoritative ExoPlayer instance
        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player = exoPlayer

        // PendingIntent to launch the app UI when tapping the media notification
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Create MediaSession connected to the player
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(HarmoniaSessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }

    private class HarmoniaSessionCallback : MediaSession.Callback {
        // Standard session callback handling commands from lock screen, notification, and Bluetooth/headset
    }
}
