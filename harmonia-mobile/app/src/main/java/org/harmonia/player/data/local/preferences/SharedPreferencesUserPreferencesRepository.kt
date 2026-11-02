package org.harmonia.player.data.local.preferences

import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.harmonia.player.domain.model.RepeatMode
import org.harmonia.player.domain.repository.UserPreferencesRepository

/**
 * Android [SharedPreferences] backed implementation of [UserPreferencesRepository].
 * Guarantees reactive updates and persistent configuration across application relaunches.
 */
class SharedPreferencesUserPreferencesRepository(
    private val sharedPreferences: SharedPreferences
) : UserPreferencesRepository {

    override fun isDarkTheme(): Boolean {
        return sharedPreferences.getBoolean(KEY_DARK_THEME, DEFAULT_DARK_THEME)
    }

    override fun setIsDarkTheme(isDark: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DARK_THEME, isDark).apply()
    }

    override fun observeIsDarkTheme(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DARK_THEME || key == null) {
                trySend(isDarkTheme())
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(isDarkTheme())
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    override fun isDefaultShuffleEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DEFAULT_SHUFFLE, DEFAULT_SHUFFLE)
    }

    override fun setDefaultShuffleEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_DEFAULT_SHUFFLE, enabled).apply()
    }

    override fun observeDefaultShuffleEnabled(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DEFAULT_SHUFFLE || key == null) {
                trySend(isDefaultShuffleEnabled())
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(isDefaultShuffleEnabled())
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    override fun getDefaultRepeatMode(): RepeatMode {
        val raw = sharedPreferences.getString(KEY_DEFAULT_REPEAT_MODE, RepeatMode.OFF.name) ?: RepeatMode.OFF.name
        return try {
            RepeatMode.valueOf(raw)
        } catch (_: Exception) {
            RepeatMode.OFF
        }
    }

    override fun setDefaultRepeatMode(mode: RepeatMode) {
        sharedPreferences.edit().putString(KEY_DEFAULT_REPEAT_MODE, mode.name).apply()
    }

    override fun observeDefaultRepeatMode(): Flow<RepeatMode> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DEFAULT_REPEAT_MODE || key == null) {
                trySend(getDefaultRepeatMode())
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(getDefaultRepeatMode())
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    override fun isKeepScreenOn(): Boolean {
        return sharedPreferences.getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON)
    }

    override fun setKeepScreenOn(enabled: Boolean) {
        sharedPreferences.edit().putBoolean(KEY_KEEP_SCREEN_ON, enabled).apply()
    }

    override fun observeKeepScreenOn(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_KEEP_SCREEN_ON || key == null) {
                trySend(isKeepScreenOn())
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(isKeepScreenOn())
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    companion object {
        const val PREFERENCES_NAME = "harmonia_user_preferences"
        const val KEY_DARK_THEME = "key_dark_theme"
        const val KEY_DEFAULT_SHUFFLE = "key_default_shuffle"
        const val KEY_DEFAULT_REPEAT_MODE = "key_default_repeat_mode"
        const val KEY_KEEP_SCREEN_ON = "key_keep_screen_on"

        const val DEFAULT_DARK_THEME = true
        const val DEFAULT_SHUFFLE = false
        const val DEFAULT_KEEP_SCREEN_ON = false
    }
}
