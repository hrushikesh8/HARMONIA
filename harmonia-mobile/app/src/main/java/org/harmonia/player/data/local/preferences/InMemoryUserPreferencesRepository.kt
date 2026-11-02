package org.harmonia.player.data.local.preferences

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.repository.UserPreferencesRepository

/**
 * Pure in-memory implementation of [UserPreferencesRepository] for unit tests and fallbacks.
 */
class InMemoryUserPreferencesRepository(
    initialDarkTheme: Boolean = true,
    initialShuffle: Boolean = false,
    initialRepeatMode: RepeatMode = RepeatMode.OFF,
    initialKeepScreenOn: Boolean = false
) : UserPreferencesRepository {

    private val _darkTheme = MutableStateFlow(initialDarkTheme)
    private val _shuffle = MutableStateFlow(initialShuffle)
    private val _repeatMode = MutableStateFlow(initialRepeatMode)
    private val _keepScreenOn = MutableStateFlow(initialKeepScreenOn)

    override fun isDarkTheme(): Boolean = _darkTheme.value
    override fun setIsDarkTheme(isDark: Boolean) { _darkTheme.value = isDark }
    override fun observeIsDarkTheme(): Flow<Boolean> = _darkTheme.asStateFlow()

    override fun isDefaultShuffleEnabled(): Boolean = _shuffle.value
    override fun setDefaultShuffleEnabled(enabled: Boolean) { _shuffle.value = enabled }
    override fun observeDefaultShuffleEnabled(): Flow<Boolean> = _shuffle.asStateFlow()

    override fun getDefaultRepeatMode(): RepeatMode = _repeatMode.value
    override fun setDefaultRepeatMode(mode: RepeatMode) { _repeatMode.value = mode }
    override fun observeDefaultRepeatMode(): Flow<RepeatMode> = _repeatMode.asStateFlow()

    override fun isKeepScreenOn(): Boolean = _keepScreenOn.value
    override fun setKeepScreenOn(enabled: Boolean) { _keepScreenOn.value = enabled }
    override fun observeKeepScreenOn(): Flow<Boolean> = _keepScreenOn.asStateFlow()
}
