package org.harmonia.player

import android.app.Application
import org.harmonia.player.core.di.ServiceLocator
import org.harmonia.player.data.playback.HarmoniaPlayerController

class HarmoniaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)

        // Initialize authoritative native Media3 playback controller
        val playerController = HarmoniaPlayerController(
            context = this,
            playbackStateRepository = ServiceLocator.getPlaybackStateRepository(),
            listeningEventRepository = ServiceLocator.getListeningEventRepository(),
            trackRepository = ServiceLocator.getTrackRepository(),
            localFileRepository = ServiceLocator.getLocalFileRepository()
        )
        ServiceLocator.setPlaybackPort(playerController)
    }
}
