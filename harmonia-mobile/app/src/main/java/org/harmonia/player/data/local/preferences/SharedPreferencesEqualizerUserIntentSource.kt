package org.harmonia.player.data.local.preferences

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
 * 2. Only the master enable preference is persisted. Custom frequency profiles and DSP configurations
 *    are explicitly excluded from persistence.
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

    companion object {
        const val PREFERENCES_NAME = "harmonia_equalizer_preferences"
        const val KEY_EQUALIZER_ENABLED = "is_equalizer_enabled"
        const val DEFAULT_EQUALIZER_ENABLED = false
    }
}
