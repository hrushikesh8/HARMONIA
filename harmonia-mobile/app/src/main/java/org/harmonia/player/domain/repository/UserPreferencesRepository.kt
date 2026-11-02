package org.harmonia.player.domain.repository

import kotlinx.coroutines.flow.Flow
import org.harmonia.player.domain.model.RepeatMode

/**
 * Pure domain repository interface for persistent user-configurable application preferences.
 * Manages UI theme, playback defaults, and display behavior.
 */
interface UserPreferencesRepository {
    fun isDarkTheme(): Boolean
    fun setIsDarkTheme(isDark: Boolean)
    fun observeIsDarkTheme(): Flow<Boolean>

    fun isDefaultShuffleEnabled(): Boolean
    fun setDefaultShuffleEnabled(enabled: Boolean)
    fun observeDefaultShuffleEnabled(): Flow<Boolean>

    fun getDefaultRepeatMode(): RepeatMode
    fun setDefaultRepeatMode(mode: RepeatMode)
    fun observeDefaultRepeatMode(): Flow<RepeatMode>

    fun isKeepScreenOn(): Boolean
    fun setKeepScreenOn(enabled: Boolean)
    fun observeKeepScreenOn(): Flow<Boolean>
}
