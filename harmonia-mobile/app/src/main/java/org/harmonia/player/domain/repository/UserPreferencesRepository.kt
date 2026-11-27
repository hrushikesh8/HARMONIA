package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.ThemeMode

/**
 * Pure domain repository interface for persistent user-configurable application preferences.
 * Manages UI theme, playback defaults, and display behavior.
 */
interface UserPreferencesRepository {
    fun getThemeMode(): ThemeMode
    fun setThemeMode(mode: ThemeMode)
    fun observeThemeMode(): Flow<ThemeMode>

    fun isDefaultShuffleEnabled(): Boolean
    fun setDefaultShuffleEnabled(enabled: Boolean)
    fun observeDefaultShuffleEnabled(): Flow<Boolean>

    fun getDefaultRepeatMode(): RepeatMode
    fun setDefaultRepeatMode(mode: RepeatMode)
    fun observeDefaultRepeatMode(): Flow<RepeatMode>

    /** Saved sort order for a library category (e.g. "TRACKS" -> "TITLE_ASC"), or null if never set. */
    fun getLibrarySort(category: String): String?
    fun setLibrarySort(category: String, sortOrder: String)

    fun isKeepScreenOn(): Boolean
    fun setKeepScreenOn(enabled: Boolean)
    fun observeKeepScreenOn(): Flow<Boolean>
}
