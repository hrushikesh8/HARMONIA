package org.harmonia.player.data.local.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.model.ThemeMode
import org.harmonia.player.domain.repository.UserPreferencesRepository

/**
 * Pure in-memory implementation of [UserPreferencesRepository] for unit tests and fallbacks.
 */
class InMemoryUserPreferencesRepository(
    initialThemeMode: ThemeMode = ThemeMode.DARK,
    initialShuffle: Boolean = false,
    initialRepeatMode: RepeatMode = RepeatMode.OFF,
    initialKeepScreenOn: Boolean = false
) : UserPreferencesRepository {

    private val _themeMode = MutableStateFlow(initialThemeMode)
    private val _shuffle = MutableStateFlow(initialShuffle)
    private val _repeatMode = MutableStateFlow(initialRepeatMode)
    private val _keepScreenOn = MutableStateFlow(initialKeepScreenOn)

    override fun getThemeMode(): ThemeMode = _themeMode.value
    override fun setThemeMode(mode: ThemeMode) { _themeMode.value = mode }
    override fun observeThemeMode(): Flow<ThemeMode> = _themeMode.asStateFlow()

    override fun isDefaultShuffleEnabled(): Boolean = _shuffle.value
    override fun setDefaultShuffleEnabled(enabled: Boolean) { _shuffle.value = enabled }
    override fun observeDefaultShuffleEnabled(): Flow<Boolean> = _shuffle.asStateFlow()

    override fun getDefaultRepeatMode(): RepeatMode = _repeatMode.value
    override fun setDefaultRepeatMode(mode: RepeatMode) { _repeatMode.value = mode }
    override fun observeDefaultRepeatMode(): Flow<RepeatMode> = _repeatMode.asStateFlow()

    private val librarySorts = mutableMapOf<String, String>()
    override fun getLibrarySort(category: String): String? = librarySorts[category]
    override fun setLibrarySort(category: String, sortOrder: String) { librarySorts[category] = sortOrder }

    override fun isKeepScreenOn(): Boolean = _keepScreenOn.value
    override fun setKeepScreenOn(enabled: Boolean) { _keepScreenOn.value = enabled }
    override fun observeKeepScreenOn(): Flow<Boolean> = _keepScreenOn.asStateFlow()
}
