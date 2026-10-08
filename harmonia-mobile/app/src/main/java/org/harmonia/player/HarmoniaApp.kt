package org.harmonia.player

import android.app.Application
import org.harmonia.player.core.di.ServiceLocator

/**
 * Harmonia Mobile Application entrypoint.
 * Initializes the Room persistence layer and lightweight Service Locator.
 */
class HarmoniaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.initialize(this)
    }
}
