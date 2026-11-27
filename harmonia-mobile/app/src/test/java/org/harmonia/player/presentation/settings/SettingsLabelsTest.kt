package org.harmonia.player.presentation.settings

import org.harmonia.player.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsLabelsTest {
    @Test
    fun `repeat modes use plain labels`() {
        assertEquals(listOf("Off", "All", "One song"), RepeatMode.entries.map { repeatModeLabel(it) })
    }
}
