package org.harmonia.player.data.local.preferences

import org.harmonia.player.domain.model.equalizer.EqualizerLayout
import org.harmonia.player.domain.model.equalizer.EqualizerPersistenceCodec
import org.harmonia.player.domain.model.equalizer.UserEqualizerPreset
import android.content.SharedPreferences
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource

/**
 * Concrete implementation of [EqualizerUserIntentSource] backed by Android [SharedPreferences].
 *
 * Invariants:
 * 1. Default enable state is strictly false.
 * 2. The master enable preference is persisted, plus (since the UI revamp, task 3.3) the device's
 *    last reported band layout, your band levels keyed by centre frequency, and your own presets.
 *    Levels only re-apply to bands with the same frequency, so a different output device is safe.
 * 3. Reactive flow updates via [observeEqualizerEnabled] unregister preferences listeners upon cancellation.
 */
class SharedPreferencesEqualizerUserIntentSource(
    private val sharedPreferences: SharedPreferences
) : EqualizerUserIntentSource {

    override fun isEqualizerEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_EQUALIZER_ENABLED, DEFAULT_EQUALIZER_ENABLED)
    }

    override fun setEqualizerEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_EQUALIZER_ENABLED, enabled)
            .apply()
    }

    override fun observeEqualizerEnabled(): Flow<Boolean> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_EQUALIZER_ENABLED || key == null) {
                trySend(isEqualizerEnabled())
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(isEqualizerEnabled())
        awaitClose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }.distinctUntilChanged()

    override fun getLastKnownLayout(): EqualizerLayout? =
        EqualizerPersistenceCodec.decodeLayout(sharedPreferences.getString(KEY_LAYOUT, null))

    override fun setLastKnownLayout(layout: EqualizerLayout) {
        sharedPreferences.edit().putString(KEY_LAYOUT, EqualizerPersistenceCodec.encodeLayout(layout)).apply()
    }

    override fun getCustomBandLevels(): Map<Int, Short> =
        EqualizerPersistenceCodec.decodeLevels(sharedPreferences.getString(KEY_BAND_LEVELS, null))

    override fun setCustomBandLevels(levels: Map<Int, Short>) {
        sharedPreferences.edit().putString(KEY_BAND_LEVELS, EqualizerPersistenceCodec.encodeLevels(levels)).apply()
    }

    override fun getUserPresets(): List<UserEqualizerPreset> =
        EqualizerPersistenceCodec.decodePresets(sharedPreferences.getString(KEY_USER_PRESETS, null))

    override fun setUserPresets(presets: List<UserEqualizerPreset>) {
        sharedPreferences.edit().putString(KEY_USER_PRESETS, EqualizerPersistenceCodec.encodePresets(presets)).apply()
    }

    companion object {
        const val PREFERENCES_NAME = "harmonia_equalizer_preferences"
        const val KEY_LAYOUT = "last_known_layout"
        const val KEY_BAND_LEVELS = "custom_band_levels"
        const val KEY_USER_PRESETS = "user_presets"
        const val KEY_EQUALIZER_ENABLED = "is_equalizer_enabled"
        const val DEFAULT_EQUALIZER_ENABLED = false
    }
}
