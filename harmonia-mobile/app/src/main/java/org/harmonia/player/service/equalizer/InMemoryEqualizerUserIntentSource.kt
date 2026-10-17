package org.harmonia.player.service.equalizer

import org.harmonia.player.domain.model.equalizer.EqualizerUserIntentSource

/**
 * In-memory implementation of [EqualizerUserIntentSource] used in C2 before persistent
 * storage is implemented in C3.
 *
 * Master preference defaults to false as required by architecture invariants.
 */
class InMemoryEqualizerUserIntentSource(
    @Volatile private var isEnabled: Boolean = false
) : EqualizerUserIntentSource {

    private val flow = kotlinx.coroutines.flow.MutableStateFlow(isEnabled)

    override fun isEqualizerEnabled(): Boolean = isEnabled

    override fun setEqualizerEnabled(enabled: Boolean) {
        isEnabled = enabled
        flow.value = enabled
    }

    override fun observeEqualizerEnabled(): kotlinx.coroutines.flow.Flow<Boolean> = flow
}
